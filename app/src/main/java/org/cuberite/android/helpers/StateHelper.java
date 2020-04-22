package org.cuberite.android.helpers;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.File;

import static android.content.Context.MODE_PRIVATE;
import static org.cuberite.android.MainActivity.PACKAGE_NAME;
import static org.cuberite.android.MainActivity.PRIVATE_DIR;

public class StateHelper {
    public enum State {
        NEED_DOWNLOAD_SERVER,
        NEED_DOWNLOAD_BINARY,
        NEED_DOWNLOAD_BOTH,
        PICK_FILE_BINARY,
        PICK_FILE_SERVER,
        RUNNING,
        READY,
        STOPPING
    }

    public static State getState(Context context) {
        // Logging tag
        String LOG = "Cuberite/State";

        SharedPreferences preferences = context.getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE);
        boolean hasBinary = false;
        boolean hasServer = false;

        if (new File(PRIVATE_DIR + "/" + preferences.getString("executableName", "")).exists()) {
            hasBinary = true;
        }

        if (new File(preferences.getString("cuberiteLocation", "")).exists()) {
            hasServer = true;
        }

        // Update state
        State state = State.READY;

        if (CuberiteHelper.isCuberiteRunning(context)) {
            state = State.RUNNING;
        } else if (!hasBinary && !hasServer) {
            state = State.NEED_DOWNLOAD_BOTH;
        } else if (!hasBinary) {
            state = State.NEED_DOWNLOAD_BINARY;
        } else if (!hasServer) {
            state = State.NEED_DOWNLOAD_SERVER;
        }

        Log.d(LOG, "Getting State: " + state);
        return state;
    }
}
