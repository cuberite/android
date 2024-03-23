package org.cuberite.android.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.cuberite.android.MainApplication
import org.cuberite.android.services.CuberiteService
import org.cuberite.android.services.InstallService

class StartupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_BOOT_COMPLETED == intent.action) {
            if (MainApplication.preferences.getBoolean("startOnBoot", false)
                    && InstallService.isInstalled) {
                CuberiteService.start(context)
            }
        }
    }
}
