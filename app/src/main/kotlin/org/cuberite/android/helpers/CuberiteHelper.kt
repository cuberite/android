package org.cuberite.android.helpers

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.text.format.Formatter
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import org.cuberite.android.services.CuberiteService

object CuberiteHelper {
    // Logging tag
    private const val LOG = "Cuberite/CuberiteHelper"
    private var consoleOutput = StringBuilder()
    const val EXECUTABLE_NAME = "Cuberite"

    fun addConsoleOutput(context: Context?, string: String?) {
        consoleOutput.append(string).append("\n")
        val intent = Intent("updateLog")
        LocalBroadcastManager.getInstance(context!!).sendBroadcast(intent)
    }

    fun getConsoleOutput(): String {
        return consoleOutput.toString()
    }

    fun resetConsoleOutput() {
        consoleOutput = StringBuilder()
    }

    fun getIpAddress(context: Context): String {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val wifiInfo = wifiManager.connectionInfo
        val ip = wifiInfo.getIpAddress()
        return if (ip == 0) "127.0.0.1" else Formatter.formatIpAddress(ip)
    }

    val preferredABI: String
        get() {
            val abi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Build.SUPPORTED_ABIS[0]
            } else {
                Build.CPU_ABI
            }
            Log.d(LOG, "Getting preferred ABI: $abi")
            return abi
        }

    fun isCuberiteRunning(context: Context): Boolean {
        return (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
            .getRunningServices(Int.MAX_VALUE)
            .any { it.service.className == CuberiteService::class.qualifiedName }
    }

    fun startCuberite(context: Context) {
        Log.d(LOG, "Starting Cuberite")
        val serviceIntent = Intent(context, CuberiteService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }

    fun stopCuberite(context: Context?) {
        Log.d(LOG, "Stopping Cuberite")
        LocalBroadcastManager.getInstance(context!!).sendBroadcast(Intent("stop"))
    }

    fun killCuberite(context: Context?) {
        LocalBroadcastManager.getInstance(context!!).sendBroadcast(Intent("kill"))
    }
}
