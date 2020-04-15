package org.cuberite.android.helpers;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.cuberite.android.services.CuberiteService;

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
        READY
    }

    /*public static final int NEED_DOWNLOAD_SERVER = 0;
    public static final int NEED_DOWNLOAD_BINARY = 1;
    public static final int NEED_DOWNLOAD_BOTH = 2;
    public static final int PICK_FILE_BINARY = 3;
    public static final int PICK_FILE_SERVER = 4;
    public static final int RUNNING = 5;
    public static final int READY = 6;*/

    public static State getState(Context context) {
        // Logging tag
        String LOG = "Cuberite/Status";

        SharedPreferences preferences = context.getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE);
        State state;
        boolean hasBinary = false;
        boolean hasServer = false;

        // Install state
        if (new File(PRIVATE_DIR + "/" + preferences.getString("executableName", "")).exists()) {
            hasBinary = true;
        }
        if (new File(preferences.getString("cuberiteLocation", "")).exists()) {
            hasServer = true;
        }

        if (CuberiteService.isCuberiteRunning(context)) {
            state = State.RUNNING;
        } else if (hasBinary && hasServer) {
            state = State.READY;
        } else if (!hasServer && !hasBinary) {
            state = State.NEED_DOWNLOAD_BOTH;
        } else if (!hasServer) {
            state = State.NEED_DOWNLOAD_SERVER;
        } else {
            state = State.NEED_DOWNLOAD_BINARY;
        }

        Log.d(LOG, "Getting State: " + state);
        return state;
    }
}
