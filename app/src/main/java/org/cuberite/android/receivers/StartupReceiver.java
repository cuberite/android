package org.cuberite.android.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import org.cuberite.android.helpers.CuberiteHelper;
import org.cuberite.android.helpers.StateHelper;

import static android.content.Context.MODE_PRIVATE;

public class StartupReceiver extends BroadcastReceiver {
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            final SharedPreferences preferences = context.getSharedPreferences(context.getPackageName(), MODE_PRIVATE);

            if (preferences.getBoolean("startOnBoot", false)
                    && StateHelper.isCuberiteInstalled(context)) {
                CuberiteHelper.startCuberite(context);
            }
        }
    }
}
