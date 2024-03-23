package org.cuberite.android.services

import android.app.IntentService
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.PowerManager.WakeLock
import android.os.ResultReceiver
import android.util.Log
import androidx.lifecycle.MutableLiveData
import org.cuberite.android.R
import org.cuberite.android.helpers.CuberiteHelper
import org.cuberite.android.helpers.StateHelper
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
    // Logging tag
    private val log = "Cuberite/InstallService"
    private var receiver: ResultReceiver? = null

    // Wakelock
    private fun acquireWakelock(): WakeLock {
        Log.d(log, "Acquiring wakeLock")
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        val wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, javaClass.getName())
        wakeLock.acquire(300000) // 5 min timeout
        return wakeLock
    }

    // Download verification
    private fun downloadVerify(url: String, targetLocation: File, retryCount: Int): String? {
        val zipFileError = download(url, targetLocation)
        zipFileError?.let {
            return zipFileError
        }

        // Verifying file
        val shaError = download("$url.sha1", File("$targetLocation.sha1"))
        shaError?.let {
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
                Log.d(log, "SHA-1 check didn't pass")
                return if (retryCount > 0) {
                    // Retry if verification failed
                    downloadVerify(url, targetLocation, retryCount - 1)
                } else getString(R.string.status_shasum_error)
            }
            Log.d(log, "SHA-1 check passed successfully with checksum $generatedSha")
        } catch (e: FileNotFoundException) {
            Log.e(log, "Something went wrong while generating checksum", e)
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
        Log.d(log, "Started downloading $stringUrl")
        Log.d(log, "Downloading to $targetLocation")

        try {
            connection.setConnectTimeout(10000) // 10 secs
            connection.connect()
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                val error = "Server returned HTTP " + connection.getResponseCode() + " " + connection.getResponseMessage()
                Log.e(log, error)
                result = error
            } else {
                inputStream = connection.inputStream
                outputStream = FileOutputStream(targetLocation)
                val length = connection.getContentLength()
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
                Log.d(log, "Finished downloading")
            }
        } catch (e: Exception) {
            result = e.message
            Log.e(log, "An error occurred when downloading a zip", e)
        } finally {
            try {
                inputStream?.let {
                    inputStream.close()
                }
                outputStream?.let {
                    outputStream.close()
                }
            } catch (ignored: IOException) {
            }
            connection.disconnect()
        }
        result?.let {
            receiver!!.send(ProgressReceiver.PROGRESS_END, null)
        }
        Log.d(log, "Releasing wakeLock")
        wakeLock.release()
        return result
    }

    // Unzip
    private fun unzip(fileUri: Uri?, targetLocation: File): String {
        var result = getString(R.string.status_install_success)
        Log.i(log, "Unzipping $fileUri to $targetLocation")
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
            Log.e(log, "An error occurred while installing Cuberite", e)
        }
        receiver!!.send(ProgressReceiver.PROGRESS_END, null)
        Log.d(log, "Releasing wakeLock")
        wakeLock.release()
        return result
    }

    @Throws(IOException::class)
    private fun unzipStream(fileUri: Uri?, targetLocation: File) {
        val inputStream = contentResolver.openInputStream(fileUri!!)
        val zipInputStream = ZipInputStream(inputStream)
        var zipEntry: ZipEntry
        while (zipInputStream.getNextEntry().also { zipEntry = it } != null) {
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
            Log.e(log, "Something went wrong while creating the .nomedia file", e)
        }
    }

    // Service handler
    @Deprecated("Deprecated in Java")
    override fun onHandleIntent(intent: Intent?) {
        val state = intent!!.serializable("state") as StateHelper.State?
        var result: String?
        if ((state == StateHelper.State.NEED_DOWNLOAD_BINARY || state == StateHelper.State.NEED_DOWNLOAD_BOTH || state == StateHelper.State.PICK_FILE_BINARY)
                && CuberiteHelper.isCuberiteRunning(applicationContext)) {
            result = getString(R.string.status_update_binary_error)
        } else if ("unzip" == intent.action) {
            val uri = intent.parcelable("uri") as Uri?
            val targetFolder = File(
                    if (state == StateHelper.State.PICK_FILE_BINARY) this.filesDir.absolutePath else intent.getStringExtra("targetFolder")!!
            )
            receiver = intent.parcelable("receiver")
            result = unzip(uri, targetFolder)
        } else {
            val downloadHost = intent.getStringExtra("downloadHost")
            val abi = CuberiteHelper.preferredABI
            val targetFileName = (if (state == StateHelper.State.NEED_DOWNLOAD_BINARY || state == StateHelper.State.NEED_DOWNLOAD_BOTH) abi else "server") + ".zip"
            val downloadUrl = downloadHost + targetFileName
            val tempZip = File(this.cacheDir, targetFileName) // Zip files are temporary
            val targetFolder = File(
                    if (state == StateHelper.State.NEED_DOWNLOAD_BINARY || state == StateHelper.State.NEED_DOWNLOAD_BOTH) this.filesDir.absolutePath else intent.getStringExtra("targetFolder")!!
            )
            receiver = intent.parcelable("receiver")

            // Download
            Log.i(log, "Downloading $state")
            val retryCount = 1
            result = downloadVerify(downloadUrl, tempZip, retryCount)
            if (result == null) {
                result = unzip(Uri.fromFile(tempZip), targetFolder)
                if (!tempZip.delete()) {
                    Log.w(log, "Failed to delete downloaded zip file")
                }
            }
            if (state == StateHelper.State.NEED_DOWNLOAD_BOTH) {
                intent.putExtra("state", StateHelper.State.NEED_DOWNLOAD_SERVER)
                onHandleIntent(intent)
            }
        }
        stopSelf()
        Handler(Looper.getMainLooper()).post { endedLiveData.setValue(result) }
    }

    companion object {
        val endedLiveData = MutableLiveData<String?>()
    }
}
