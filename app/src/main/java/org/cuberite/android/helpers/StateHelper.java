package org.cuberite.android.helpers;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.File;

import static android.content.Context.MODE_PRIVATE;

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

    public static boolean isCuberiteInstalled(Context context) {
        State state = getState(context);
        return (
                state != State.NEED_DOWNLOAD_BINARY
                && state != State.NEED_DOWNLOAD_SERVER
                && state != State.NEED_DOWNLOAD_BOTH
        );
    }

    public static State getState(Context context) {
        // Logging tag
        String LOG = "Cuberite/State";

        final SharedPreferences preferences = context.getSharedPreferences(context.getPackageName(), MODE_PRIVATE);
        boolean hasBinary = false;
        boolean hasServer = false;

        if (new File(context.getFilesDir().getAbsolutePath() + "/" + CuberiteHelper.getExecutableName()).exists()) {
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
