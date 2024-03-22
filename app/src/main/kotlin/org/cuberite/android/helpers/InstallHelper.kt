package org.cuberite.android.helpers

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import org.cuberite.android.receivers.ProgressReceiver
import org.cuberite.android.services.InstallService

object InstallHelper {
    const val DOWNLOAD_HOST = "https://download.cuberite.org/androidbinaries/"

    fun installCuberiteDownload(activity: Activity, state: StateHelper.State?) {
        val preferences = activity.getSharedPreferences(activity.packageName, Context.MODE_PRIVATE)
        val intent = Intent(activity, InstallService::class.java)
                .setAction("download")
                .putExtra("downloadHost", DOWNLOAD_HOST)
                .putExtra("state", state)
                .putExtra("targetFolder", preferences.getString("cuberiteLocation", ""))
                .putExtra("receiver", ProgressReceiver(activity, Handler(Looper.getMainLooper())))
        activity.startService(intent)
    }

    fun installCuberiteLocal(activity: Activity, state: StateHelper.State?, selectedFileUri: Uri?) {
        val preferences = activity.getSharedPreferences(activity.packageName, Context.MODE_PRIVATE)
        selectedFileUri?.let {
            val intent = Intent(activity, InstallService::class.java)
                    .setAction("unzip")
                    .putExtra("uri", selectedFileUri)
                    .putExtra("state", state)
                    .putExtra("targetFolder", preferences.getString("cuberiteLocation", ""))
                    .putExtra("receiver", ProgressReceiver(activity, Handler(Looper.getMainLooper())))
            activity.startService(intent)
        }
    }
}
