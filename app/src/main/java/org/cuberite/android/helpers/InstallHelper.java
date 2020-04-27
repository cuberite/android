package org.cuberite.android.helpers;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;

import org.cuberite.android.helpers.StateHelper.State;
import org.cuberite.android.receivers.ProgressReceiver;
import org.cuberite.android.services.InstallService;

public class InstallHelper {
    public static String getDownloadHost() {
        return "https://download.cuberite.org/androidbinaries/";
    }

    public static void installCuberiteDownload(final Activity activity, State state) {
        SharedPreferences preferences = activity.getSharedPreferences(activity.getPackageName(), Context.MODE_PRIVATE);

        Intent intent = new Intent(activity, InstallService.class)
                .setAction("download")
                .putExtra("downloadHost", getDownloadHost())
                .putExtra("state", state)
                .putExtra("targetFolder", preferences.getString("cuberiteLocation", ""))
                .putExtra("receiver", new ProgressReceiver(activity, new Handler()));

        activity.startService(intent);
    }

    public static void installCuberiteLocal(Activity activity, State state, Intent data) {
        SharedPreferences preferences = activity.getSharedPreferences(activity.getPackageName(), Context.MODE_PRIVATE);

        if (data != null) {
            Uri selectedFileUri = data.getData();

            Intent intent = new Intent(activity, InstallService.class)
                    .setAction("unzip")
                    .putExtra("uri", selectedFileUri.toString())
                    .putExtra("state", state)
                    .putExtra("targetFolder", preferences.getString("cuberiteLocation", ""))
                    .putExtra("receiver", new ProgressReceiver(activity, new Handler()));

            activity.startService(intent);
        }
    }
}
