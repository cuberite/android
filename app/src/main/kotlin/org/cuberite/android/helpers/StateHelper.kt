package org.cuberite.android.helpers

import android.content.Context
import android.util.Log
import java.io.File

object StateHelper {
    fun isCuberiteInstalled(context: Context): Boolean {
        val state = getState(context)
        return state != State.NEED_DOWNLOAD_BINARY && state != State.NEED_DOWNLOAD_SERVER && state != State.NEED_DOWNLOAD_BOTH
    }

    fun getState(context: Context): State {
        // Logging tag
        val log = "Cuberite/State"
        val preferences = context.getSharedPreferences(context.packageName, Context.MODE_PRIVATE)
        var hasBinary = false
        var hasServer = false
        if (File(context.filesDir.absolutePath + "/" + CuberiteHelper.EXECUTABLE_NAME).exists()) {
            hasBinary = true
        }
        if (File(preferences.getString("cuberiteLocation", "")!!).exists()) {
            hasServer = true
        }

        // Update state
        var state = State.READY
        if (CuberiteHelper.isCuberiteRunning(context)) {
            state = State.RUNNING
        } else if (!hasBinary && !hasServer) {
            state = State.NEED_DOWNLOAD_BOTH
        } else if (!hasBinary) {
            state = State.NEED_DOWNLOAD_BINARY
        } else if (!hasServer) {
            state = State.NEED_DOWNLOAD_SERVER
        }
        Log.d(log, "Getting State: $state")
        return state
    }

    enum class State {
        NEED_DOWNLOAD_SERVER,
        NEED_DOWNLOAD_BINARY,
        NEED_DOWNLOAD_BOTH,
        PICK_FILE_BINARY,
        PICK_FILE_SERVER,
        RUNNING,
        READY
    }
}
