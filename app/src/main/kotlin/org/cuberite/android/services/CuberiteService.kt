package org.cuberite.android.services

import android.app.IntentService
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.NetworkInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import org.cuberite.android.MainActivity
import org.cuberite.android.MainApplication
import org.cuberite.android.R
import org.cuberite.android.parcelable
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Scanner


class CuberiteService : IntentService("CuberiteService") {
    private var consoleOutput = StringBuilder()
    private lateinit var notification: NotificationCompat.Builder
    private lateinit var process: Process
    private lateinit var cuberiteSTDIN: OutputStream

    // Notification-related methods
    private fun createNotification() {
        val channelId = "cuberiteservice"
        val icon = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) R.drawable.ic_notification else R.mipmap.ic_launcher
        val text = getText(R.string.notification_cuberite_running)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val notificationIntent = Intent(this, MainActivity::class.java)
        val contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, flags)
        notification = NotificationCompat.Builder(this, channelId)
                .setSmallIcon(icon)
                .setTicker(text)
                .setContentTitle(text)
                .setContentText(ipAddress)
                .setContentIntent(contentIntent)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        startForeground(1, notification.build())
    }

    // Process-related methods
    @Throws(IOException::class)
    private fun startProcess() {
        val location = MainApplication.preferences.getString("cuberiteLocation", "")

        // Clear previous output
        consoleOutput = StringBuilder()
        consoleOutput.append("Info: Cuberite is starting...").append("\n")
        Handler(Looper.getMainLooper()).post { updateLogLiveData.postValue(consoleOutput) }

        // Make sure we can execute the binary
        File(filesDir, EXECUTABLE_NAME).setExecutable(true, true)

        // Initiate ProcessBuilder with the command at the given location
        val processBuilder = ProcessBuilder("$filesDir/$EXECUTABLE_NAME", "--no-output-buffering")
        processBuilder.directory(File(location!!))
        processBuilder.redirectErrorStream(true)
        Log.d(LOG, "Starting process...")
        process = processBuilder.start()
        cuberiteSTDIN = process.outputStream
    }

    private fun updateOutput() {
        Log.d(LOG, "Starting logging...")
        val processScanner = Scanner(process.inputStream)
        var line: String?
        try {
            while (processScanner.nextLine().also { line = it } != null) {
                Log.i(LOG, line!!)
                consoleOutput.append(line).append("\n")
                Handler(Looper.getMainLooper()).post { updateLogLiveData.postValue(consoleOutput) }
            }
        } catch (e: NoSuchElementException) {
            // Do nothing. Workaround for issues in older Android versions.
        }
        processScanner.close()
    }

    // Broadcast receivers
    private val updateIp: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (WifiManager.NETWORK_STATE_CHANGED_ACTION == action) {
                val info = intent.parcelable(WifiManager.EXTRA_NETWORK_INFO) as NetworkInfo?
                if (NetworkInfo.State.CONNECTED == info!!.state || NetworkInfo.State.DISCONNECTED == info.state) {
                    Log.d(LOG, "Updating notification IP due to network change")
                    notification.setContentText(ipAddress)
                    val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.notify(1, notification.build())
                }
            }
        }
    }

    // Service startup and cleanup
    @Deprecated("Deprecated in Java")
    override fun onHandleIntent(intent: Intent?) {
        isRunning = true
        Log.d(LOG, "Starting service...")
        try {
            // Create and show notification about Cuberite running
            createNotification()

            // Start the Cuberite process
            startProcess()

            // Update notification IP if network changes
            val intentFilter = IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION)
            registerReceiver(updateIp, intentFilter)

            // Communication with the activity
            val executeObserver = Observer<String?> { command ->
                if (command == null) {
                    return@Observer
                }
                try {
                    cuberiteSTDIN.write((command + "\n").toByteArray())
                    cuberiteSTDIN.flush()
                } catch (e: Exception) {
                    Log.e(LOG, "An error occurred when writing $command to the STDIN", e)
                }
                MainActivity.executeCommandLiveData.postValue(null)
            }
            val killObserver = Observer<Boolean> { kill ->
                if (!kill) {
                    return@Observer
                }
                process.destroy()
                MainActivity.killCuberiteLiveData.postValue(false)
            }
            Handler(Looper.getMainLooper()).post {
                MainActivity.executeCommandLiveData.observeForever(executeObserver)
                MainActivity.killCuberiteLiveData.observeForever(killObserver)
            }

            // Log to console
            val logTimeStart = System.currentTimeMillis()
            updateOutput()

            // Logic waits here until Cuberite has stopped. Everything after that is cleanup for the next run
            val logTimeEnd = System.currentTimeMillis()
            if (logTimeEnd - logTimeStart < 100) {
                Handler(Looper.getMainLooper()).post {
                    startupErrorLiveData.postValue(true)
                }
            }

            // Shutdown
            unregisterReceiver(updateIp)
            Handler(Looper.getMainLooper()).post {
                MainActivity.executeCommandLiveData.removeObserver(executeObserver)
                MainActivity.killCuberiteLiveData.removeObserver(killObserver)
            }
            cuberiteSTDIN.close()
        } catch (e: Exception) {
            Log.e(LOG, "An error occurred when starting Cuberite", e)

            // Send error to user
            Handler(Looper.getMainLooper()).post { startupErrorLiveData.postValue(true) }
        }
        stopSelf()
        Handler(Looper.getMainLooper()).post { endedLiveData.postValue(true) }
    }

    @Deprecated("Deprecated in Java")
    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
    }

    companion object {
        private const val LOG = "Cuberite/ServerService"
        const val EXECUTABLE_NAME = "Cuberite"
        var isRunning = false
        val endedLiveData = MutableLiveData<Boolean>()
        val startupErrorLiveData = MutableLiveData<Boolean>()
        val updateLogLiveData = MutableLiveData<StringBuilder>()

        val ipAddress: String
            get() {
                try {
                    for (networkInterface in NetworkInterface.getNetworkInterfaces()) {
                        for (inetAddress in networkInterface.getInetAddresses()) {
                            if (!inetAddress.isLoopbackAddress && inetAddress is Inet4Address) {
                                return inetAddress.hostAddress!!
                            }
                        }
                    }
                } catch (_: Exception) {}
                return "127.0.0.1"
            }

        val preferredABI: String
            get() {
                val abi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    Build.SUPPORTED_ABIS[0]
                } else {
                    @Suppress("deprecation") Build.CPU_ABI
                }
                Log.d(LOG, "Getting preferred ABI: $abi")
                return abi
            }

        fun start(context: Context) {
            Log.d(LOG, "Starting Cuberite")
            val serviceIntent = Intent(context, CuberiteService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }

        fun stop() {
            Log.d(LOG, "Stopping Cuberite")
            MainActivity.executeCommandLiveData.postValue("stop")
        }

        fun kill() {
            MainActivity.killCuberiteLiveData.postValue(true)
        }
    }
}
