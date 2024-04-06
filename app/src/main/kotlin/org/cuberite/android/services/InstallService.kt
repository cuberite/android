package org.cuberite.android.services

import android.app.Activity
import android.app.IntentService
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.PowerManager
import android.os.PowerManager.WakeLock
import android.os.ResultReceiver
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.cuberite.android.MainApplication
import org.cuberite.android.R
import org.cuberite.android.parcelable
import org.cuberite.android.receivers.ProgressReceiver
import org.cuberite.android.serializable
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Scanner
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class InstallService : IntentService("InstallService") {
    enum class State {
        NEED_DOWNLOAD_SERVER,
        NEED_DOWNLOAD_BINARY,
        NEED_DOWNLOAD_BOTH,
        PICK_FILE_BINARY,
        PICK_FILE_SERVER,
        DONE
    }

    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + serviceJob)

    private var receiver: ResultReceiver? = null

    // Wakelock
    private fun acquireWakelock(): WakeLock {
        Log.d(LOG, "Acquiring wakeLock")
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        val wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, this::class.simpleName)
        wakeLock.acquire(300000) // 5 min timeout
        return wakeLock
    }

    // Download verification
    private fun downloadVerify(url: String, targetLocation: File, retryCount: Int): String? {
        val zipFileError = download(url, targetLocation)
        if (zipFileError != null) {
            return zipFileError
        }

        // Verifying file
        val shaError = download("$url.sha1", File("$targetLocation.sha1"))
        if (shaError != null) {
            return shaError
        }
        try {
            val generatedSha = generateSha1(targetLocation)
            val downloadedSha = Scanner(
                File("$targetLocation.sha1")
            )
                .useDelimiter("\\Z")
                .next()
                .split(" ".toRegex(), limit = 2).toTypedArray()[0]
            File("$targetLocation.sha1").delete()
            if (downloadedSha != generatedSha) {
                Log.d(LOG, "SHA-1 check didn't pass")
                return if (retryCount > 0) {
                    // Retry if verification failed
                    downloadVerify(url, targetLocation, retryCount - 1)
                } else getString(R.string.status_shasum_error)
            }
            Log.d(LOG, "SHA-1 check passed successfully with checksum $generatedSha")
        } catch (e: FileNotFoundException) {
            Log.e(LOG, "Something went wrong while generating checksum", e)
            return getString(R.string.status_shasum_error)
        }
        return null
    }

    private fun generateSha1(targetLocation: File): String {
        return try {
            val sha1 = MessageDigest.getInstance("SHA-1")
            val input: InputStream = FileInputStream(targetLocation)
            val buffer = ByteArray(8192)
            var len = input.read(buffer)
            while (len != -1) {
                sha1.update(buffer, 0, len)
                len = input.read(buffer)
            }
            val shaSum = sha1.digest()
            val charset = "0123456789ABCDEF".toCharArray()
            val hexResult = CharArray(shaSum.size * 2)
            for (j in shaSum.indices) {
                val v = shaSum[j].toInt() and 0xFF
                hexResult[j * 2] = charset[v ushr 4]
                hexResult[j * 2 + 1] = charset[v and 0x0F]
            }
            String(hexResult).lowercase()
        } catch (e: Exception) {
            e.toString()
        }
    }

    // Download
    private fun download(stringUrl: String, targetLocation: File): String? {
        val wakeLock = acquireWakelock()
        val bundleInit = Bundle()
        val url = URL(stringUrl)
        val connection = url.openConnection() as HttpURLConnection
        var inputStream: InputStream? = null
        var outputStream: OutputStream? = null
        var result: String? = null

        bundleInit.putString("title", getString(R.string.status_downloading_cuberite))
        receiver!!.send(ProgressReceiver.PROGRESS_START, bundleInit)
        Log.d(LOG, "Started downloading $stringUrl")
        Log.d(LOG, "Downloading to $targetLocation")

        try {
            connection.setConnectTimeout(10000) // 10 secs
            connection.connect()
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                val error =
                    "Server returned HTTP " + connection.responseCode + " " + connection.responseMessage
                Log.e(LOG, error)
                result = error
            } else {
                inputStream = connection.inputStream
                outputStream = FileOutputStream(targetLocation)
                val length = connection.contentLength
                val data = ByteArray(4096)
                var total: Long = 0
                var count: Int
                while (inputStream.read(data).also { count = it } != -1) {
                    total += count.toLong()
                    if (length > 0) { // only if total length is known
                        val bundleProg = Bundle()
                        bundleProg.putInt("progress", total.toInt())
                        bundleProg.putInt("max", length)
                        receiver!!.send(ProgressReceiver.PROGRESS_NEW_DATA, bundleProg)
                    }
                    outputStream.write(data, 0, count)
                }
                Log.d(LOG, "Finished downloading")
            }
        } catch (e: Exception) {
            result = e.message
            Log.e(LOG, "An error occurred when downloading a zip", e)
        } finally {
            try {
                inputStream?.close()
                outputStream?.close()
            } catch (ignored: IOException) {
            }
            connection.disconnect()
        }
        if (result != null) {
            receiver!!.send(ProgressReceiver.PROGRESS_END, null)
        }
        Log.d(LOG, "Releasing wakeLock")
        wakeLock.release()
        return result
    }

    // Unzip
    private fun unzip(fileUri: Uri?, targetLocation: File): String {
        var result = getString(R.string.status_install_success)
        Log.i(LOG, "Unzipping $fileUri to $targetLocation")
        val wakeLock = acquireWakelock()
        if (!targetLocation.exists()) {
            targetLocation.mkdir()
        }

        // Create a .nomedia file in the server directory to prevent images from showing in gallery
        createNoMediaFile(targetLocation)
        val bundleInit = Bundle()
        bundleInit.putString("title", getString(R.string.status_installing_cuberite))
        receiver!!.send(ProgressReceiver.PROGRESS_START, bundleInit)
        try {
            unzipStream(fileUri, targetLocation)
        } catch (e: IOException) {
            result = getString(R.string.status_unzip_error)
            Log.e(LOG, "An error occurred while installing Cuberite", e)
        }
        receiver!!.send(ProgressReceiver.PROGRESS_END, null)
        Log.d(LOG, "Releasing wakeLock")
        wakeLock.release()
        return result
    }

    @Throws(IOException::class)
    private fun unzipStream(fileUri: Uri?, targetLocation: File) {
        val inputStream = contentResolver.openInputStream(fileUri!!)
        val zipInputStream = ZipInputStream(inputStream)
        var zipEntry: ZipEntry
        while (zipInputStream.nextEntry.also { zipEntry = it } != null) {
            if (zipEntry.isDirectory) {
                File(targetLocation, zipEntry.name).mkdir()
            } else {
                val outputStream = FileOutputStream(targetLocation.toString() + "/" + zipEntry.name)
                val bufferedOutputStream = BufferedOutputStream(outputStream)
                val buffer = ByteArray(1024)
                var read: Int
                while (zipInputStream.read(buffer).also { read = it } != -1) {
                    bufferedOutputStream.write(buffer, 0, read)
                }
                zipInputStream.closeEntry()
                bufferedOutputStream.close()
                outputStream.close()
            }
        }
        zipInputStream.close()
    }

    private fun createNoMediaFile(targetFolder: File) {
        val noMedia = File(targetFolder, ".nomedia")
        try {
            noMedia.createNewFile()
        } catch (e: IOException) {
            Log.e(LOG, "Something went wrong while creating the .nomedia file", e)
        }
    }

    // Service handler
    @Deprecated("Deprecated in Java")
    override fun onHandleIntent(intent: Intent?) {
        val state = intent!!.serializable("state") as State?
        var result: String?
        if ((state == State.NEED_DOWNLOAD_BINARY || state == State.NEED_DOWNLOAD_BOTH || state == State.PICK_FILE_BINARY)
            && CuberiteService.isRunning.value
        ) {
            result = getString(R.string.status_update_binary_error)
        } else if ("unzip" == intent.action) {
            val uri = intent.parcelable("uri") as Uri?
            val filePath = if (state == State.PICK_FILE_BINARY) {
                MainApplication.privateDir
            } else {
                MainApplication.preferences.getString("cuberiteLocation", "")!!
            }
            val targetFolder = File(filePath)
            receiver = intent.parcelable("receiver")
            result = unzip(uri, targetFolder)
        } else {
            val abi = CuberiteService.preferredABI
            val targetFileName =
                if (state == State.NEED_DOWNLOAD_BINARY || state == State.NEED_DOWNLOAD_BOTH) {
                    "$abi.zip"
                } else {
                    "server.zip"
                }
            val downloadUrl = DOWNLOAD_HOST + targetFileName
            val tempZip = File(cacheDir, targetFileName) // Zip files are temporary
            val filePath =
                if (state == State.NEED_DOWNLOAD_BINARY || state == State.NEED_DOWNLOAD_BOTH) {
                    MainApplication.privateDir
                } else {
                    MainApplication.preferences.getString("cuberiteLocation", "")!!
                }
            val targetFolder = File(filePath)
            receiver = intent.parcelable("receiver")

            // Download
            Log.i(LOG, "Downloading $state")
            val retryCount = 1
            result = downloadVerify(downloadUrl, tempZip, retryCount)
            if (result == null) {
                result = unzip(Uri.fromFile(tempZip), targetFolder)
                if (!tempZip.delete()) {
                    Log.w(LOG, "Failed to delete downloaded zip file")
                }
            }
            if (state == State.NEED_DOWNLOAD_BOTH) {
                intent.putExtra("state", State.NEED_DOWNLOAD_SERVER)
                onHandleIntent(intent)
            }
        }
        scope.launch {
            _serviceResult.emit(result)
        }
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancelChildren()
    }

    companion object {
        private const val LOG = "Cuberite/InstallService"
        const val DOWNLOAD_HOST = "https://download.cuberite.org/androidbinaries/"

        private val _serviceResult: MutableStateFlow<String?> = MutableStateFlow(null)
        val serviceResult: StateFlow<String?> = _serviceResult.asStateFlow()

        private val state: State
            get() {
                val hasBinary =
                    File(MainApplication.privateDir + "/" + CuberiteService.EXECUTABLE_NAME).exists()
                val hasServer =
                    File(MainApplication.preferences.getString("cuberiteLocation", "")!!).exists()
                var state: State = State.DONE

                if (!CuberiteService.isRunning.value) {
                    if (!hasBinary && !hasServer) {
                        state = State.NEED_DOWNLOAD_BOTH
                    } else if (!hasBinary) {
                        state = State.NEED_DOWNLOAD_BINARY
                    } else if (!hasServer) {
                        state = State.NEED_DOWNLOAD_SERVER
                    }
                }
                Log.d(LOG, "Getting State: $state")
                return state
            }

        val isInstalled: Boolean
            get() {
                return state == State.DONE
            }

        fun download(activity: Activity, state: State = this.state) {
            val intent = Intent(activity, InstallService::class.java)
                .setAction("download")
                .putExtra("state", state)
                .putExtra("receiver", ProgressReceiver(activity, Handler(activity.mainLooper)))
            activity.startService(intent)
        }

        fun resultConsumed() {
            _serviceResult.value = null
        }

        fun installLocal(activity: Activity, selectedFileUri: Uri?, state: State = this.state) {
            if (selectedFileUri == null) {
                return
            }
            val intent = Intent(activity, InstallService::class.java)
                .setAction("unzip")
                .putExtra("uri", selectedFileUri)
                .putExtra("state", state)
                .putExtra("receiver", ProgressReceiver(activity, Handler(activity.mainLooper)))
            activity.startService(intent)
        }
    }
}
