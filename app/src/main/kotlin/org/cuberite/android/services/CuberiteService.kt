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
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import org.cuberite.android.MainActivity
import org.cuberite.android.R
import org.cuberite.android.helpers.CuberiteHelper
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.Scanner

class CuberiteService : IntentService("CuberiteService") {
    // Logging tag
    private val log = "Cuberite/ServerService"
    private lateinit var notification: NotificationCompat.Builder
    private lateinit var process: Process
    private lateinit var cuberiteSTDIN: OutputStream

    // Notification-related methods
    private fun createNotification() {
        val channelId = "cuberiteservice"
        val icon = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) R.drawable.ic_notification else R.mipmap.ic_launcher
        val text = getText(R.string.notification_cuberite_running)
        val ip = CuberiteHelper.getIpAddress(applicationContext)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val notificationIntent = Intent(this, MainActivity::class.java)
        val contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, flags)
        notification = NotificationCompat.Builder(this, channelId)
                .setSmallIcon(icon)
                .setTicker(text)
                .setContentTitle(text)
                .setContentText(ip)
                .setContentIntent(contentIntent)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        startForeground(1, notification.build())
    }

    // Process-related methods
    @Throws(IOException::class)
    private fun startProcess() {
        val preferences = applicationContext.getSharedPreferences(this.packageName, MODE_PRIVATE)
        val executableName = CuberiteHelper.EXECUTABLE_NAME
        val location = preferences.getString("cuberiteLocation", "")

        // Clear previous output
        CuberiteHelper.resetConsoleOutput()

        // Make sure we can execute the binary
        File(this.filesDir, executableName).setExecutable(true, true)

        // Initiate ProcessBuilder with the command at the given location
        val processBuilder = ProcessBuilder(this.filesDir.toString() + "/" + executableName, "--no-output-buffering")
        processBuilder.directory(File(location!!))
        processBuilder.redirectErrorStream(true)
        CuberiteHelper.addConsoleOutput(applicationContext, "Info: Cuberite is starting...")
        Log.d(log, "Starting process...")
        process = processBuilder.start()
        cuberiteSTDIN = process.outputStream
    }

    private fun updateOutput() {
        Log.d(log, "Starting logging...")
        val processScanner = Scanner(process.inputStream)
        var line: String?
        try {
            while (processScanner.nextLine().also { line = it } != null) {
                Log.i(log, line!!)
                CuberiteHelper.addConsoleOutput(applicationContext, line)
            }
        } catch (e: NoSuchElementException) {
            // Do nothing. Workaround for issues in older Android versions.
        }
        processScanner.close()
    }

    // Broadcast receivers
    private val executeCommand: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val command = intent.getStringExtra("message")
            try {
                cuberiteSTDIN.write((command + "\n").toByteArray())
                cuberiteSTDIN.flush()
            } catch (e: Exception) {
                Log.e(log, "An error occurred when writing $command to the STDIN", e)
            }
        }
    }
    private val updateIp: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (WifiManager.NETWORK_STATE_CHANGED_ACTION == action) {
                val info = intent.getParcelableExtra<NetworkInfo>(WifiManager.EXTRA_NETWORK_INFO)
                if (NetworkInfo.State.CONNECTED == info!!.state || NetworkInfo.State.DISCONNECTED == info.state) {
                    Log.d(log, "Updating notification IP due to network change")
                    val ip = CuberiteHelper.getIpAddress(context)
                    notification.setContentText(ip)
                    val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.notify(1, notification.build())
                }
            }
        }
    }
    private val stop: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            try {
                cuberiteSTDIN.write("stop\n".toByteArray())
                cuberiteSTDIN.flush()
            } catch (e: Exception) {
                Log.e(log, "An error occurred when writing stop to the STDIN", e)
            }
        }
    }
    private val kill: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            process.destroy()
        }
    }

    // Service startup and cleanup
    @Deprecated("Deprecated in Java")
    override fun onHandleIntent(intent: Intent?) {
        Log.d(log, "Starting service...")
        try {
            // Create and show notification about Cuberite running
            createNotification()

            // Start the Cuberite process
            startProcess()

            // Update notification IP if network changes
            val intentFilter = IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION)
            registerReceiver(updateIp, intentFilter)

            // Communication with the activity
            LocalBroadcastManager.getInstance(this).registerReceiver(executeCommand, IntentFilter("executeCommand"))
            LocalBroadcastManager.getInstance(this).registerReceiver(stop, IntentFilter("stop"))
            LocalBroadcastManager.getInstance(this).registerReceiver(kill, IntentFilter("kill"))

            // Log to console
            val logTimeStart = System.currentTimeMillis()
            updateOutput()

            // Logic waits here until Cuberite has stopped. Everything after that is cleanup for the next run
            val logTimeEnd = System.currentTimeMillis()
            if (logTimeEnd - logTimeStart < 100) {
                LocalBroadcastManager.getInstance(this).sendBroadcast(Intent("showStartupError"))
            }

            // Shutdown
            unregisterReceiver(updateIp)
            LocalBroadcastManager.getInstance(this).unregisterReceiver(executeCommand)
            LocalBroadcastManager.getInstance(this).unregisterReceiver(stop)
            LocalBroadcastManager.getInstance(this).unregisterReceiver(kill)
            cuberiteSTDIN.close()
        } catch (e: Exception) {
            Log.e(log, "An error occurred when starting Cuberite", e)

            // Send error to user
            LocalBroadcastManager.getInstance(this).sendBroadcast(Intent("showStartupError"))
        }
        stopSelf()
        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent("CuberiteService.callback"))
    }
}
