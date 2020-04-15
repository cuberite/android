package org.cuberite.android.helpers;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.util.Log;

import org.cuberite.android.MainActivity;
import org.cuberite.android.helpers.StateHelper.State;
import org.cuberite.android.services.InstallService;

public class InstallHelper {
    public static String getPreferredABI() {
        // Logging tag
        final String LOG = "Cuberite/InstallHelper";

        String abi;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            abi = Build.SUPPORTED_ABIS[0];
        } else {
            abi = Build.CPU_ABI;
        }

        Log.d(LOG, "Getting preferred ABI: " + abi);

        return abi;
    }

    public static void installCuberiteDownload(final Activity activity, State state) {
        SharedPreferences preferences = activity.getSharedPreferences(MainActivity.PACKAGE_NAME, Context.MODE_PRIVATE);

        Intent intent = new Intent(activity, InstallService.class);
        intent.setAction("download");
        intent.putExtra("downloadHost", preferences.getString("downloadHost", ""));
        intent.putExtra("state", state);
        intent.putExtra("executableName", preferences.getString("executableName", ""));
        intent.putExtra("targetDirectory", preferences.getString("cuberiteLocation", ""));
        intent.putExtra("receiver", new ProgressReceiver(activity, new Handler()));

        activity.startService(intent);
    }

    public static void installCuberiteLocal(Activity activity, State state, Intent data) {
        SharedPreferences preferences = activity.getSharedPreferences(MainActivity.PACKAGE_NAME, Context.MODE_PRIVATE);

        if (data != null) {
            Uri selectedFileUri = data.getData();

            Intent intent = new Intent(activity, InstallService.class);
            intent.setAction("unzip");
            intent.putExtra("uri", selectedFileUri.toString());
            intent.putExtra("state", state);
            intent.putExtra("targetLocation", preferences.getString("cuberiteLocation", ""));
            intent.putExtra("receiver", new ProgressReceiver(activity, new Handler()));

            activity.startService(intent);
        }
    }
}
