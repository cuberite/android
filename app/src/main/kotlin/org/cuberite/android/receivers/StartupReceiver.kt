package org.cuberite.android.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.cuberite.android.helpers.CuberiteHelper
import org.cuberite.android.helpers.StateHelper

class StartupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_BOOT_COMPLETED == intent.action) {
            val preferences = context.getSharedPreferences(context.packageName, Context.MODE_PRIVATE)
            if (preferences.getBoolean("startOnBoot", false)
                    && StateHelper.isCuberiteInstalled(context)) {
                CuberiteHelper.startCuberite(context)
            }
        }
    }
}
