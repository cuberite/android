package org.cuberite.android.helpers

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import org.cuberite.android.MainActivity
import org.cuberite.android.services.CuberiteService
import java.net.Inet4Address
import java.net.NetworkInterface


object CuberiteHelper {
    // Logging tag
    private const val LOG = "Cuberite/CuberiteHelper"
    const val EXECUTABLE_NAME = "Cuberite"

    fun getIpAddress(): String {
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

    fun stopCuberite() {
        Log.d(LOG, "Stopping Cuberite")
        MainActivity.executeCommandLiveData.postValue("stop")
    }

    fun killCuberite() {
        MainActivity.killCuberiteLiveData.postValue(true)
    }
}
