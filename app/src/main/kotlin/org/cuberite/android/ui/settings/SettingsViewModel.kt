package org.cuberite.android.ui.settings

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.UriHandler
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.cuberite.android.MainApplication
import org.cuberite.android.R
import org.cuberite.android.extension.isExternalStorageGranted
import org.cuberite.android.extension.isSDAvailable
import org.cuberite.android.services.CuberiteService
import org.cuberite.android.services.InstallService
import org.ini4j.Ini
import java.io.File
import java.io.IOException

class SettingsViewModel : ViewModel() {

    private val preferences: SharedPreferences = MainApplication.preferences

    private val isRunning: StateFlow<Boolean> = CuberiteService.isRunning

    private val currentLocation: String
        get() = preferences.getString("cuberiteLocation", "") ?: ""

    private val webAdminFile: File
        get() {
            val cuberiteDir = File(currentLocation)
            return File(cuberiteDir, "webadmin.ini")
        }

    private val _snackbarMessage: MutableSharedFlow<String> = MutableSharedFlow()
    val snackbarMessage: SharedFlow<String> = _snackbarMessage.asSharedFlow()

    private val _snackbarStringRes: MutableSharedFlow<Int> = MutableSharedFlow()
    val snackbarStringRes: SharedFlow<Int> = _snackbarStringRes.asSharedFlow()

    private val isSdEnabled = !(currentLocation.startsWith(MainApplication.publicDir)
            || currentLocation.startsWith(MainApplication.privateDir))

    var isUsingSdCard: Boolean by mutableStateOf(isSdEnabled)
        private set

    var isStartOnBoot: Boolean by mutableStateOf(preferences.getBoolean("startOnBoot", false))
        private set

    val webAdminUrl: String?
        get() {
            var url: String? = null
            try {
                val currentFile = webAdminFile
                val ini = createWebadminIni(currentFile)
                val ip = CuberiteService.ipAddress
                val port: Int = try {
                    ini["WebAdmin", "Ports"].toInt()
                } catch (e: NumberFormatException) {
                    ini.put("WebAdmin", "Ports", 8080)
                    ini.store(currentFile)
                    ini["WebAdmin", "Ports"].toInt()
                }
                url = "http://$ip:$port"
            } catch (e: IOException) {
                Log.e(LOG, "Something went wrong while opening the ini file", e)
            }
            return url
        }

    fun setStartUp(value: Boolean) {
        preferences.edit {
            putBoolean("startOnBoot", value)
            isStartOnBoot = value
        }
    }

    fun setSdCard(context: Context, value: Boolean) {
        if (isRunning.value) {
            val message = R.string.settings_sd_card_running
            viewModelScope.launch {
                _snackbarStringRes.emit(message)
            }
            return
        }
        val isSDAvailableInner = context.isSDAvailable
        var newLocation = MainApplication.publicDir
        if (value && isSDAvailableInner) {
            // SD dir
            newLocation = context.getExternalFilesDirs(null)[1].absolutePath
        } else {
            if (context.isExternalStorageGranted) {
                // Private dir
                newLocation = MainApplication.privateDir
            }
        }
        isUsingSdCard = value
        preferences.edit {
            updateFileLocation(newLocation)
        }
    }

    fun updateCuberite(context: Context) {
        InstallService.download(context as Activity, InstallService.State.NEED_DOWNLOAD_BOTH)
    }

    fun installLocal(context: Context, uri: Uri?, state: InstallService.State) {
        InstallService.installLocal(
            activity = context as Activity,
            selectedFileUri = uri,
            state = state
        )
    }

    fun openWebAdmin(uriHandler: UriHandler) {
        if (!isRunning.value) {
            val message = R.string.settings_webadmin_not_running
            viewModelScope.launch {
                _snackbarStringRes.emit(message)
            }
            return
        }
        val url = webAdminUrl
        if (url != null) {
            Log.d(LOG, "Opening Webadmin on $url")
            uriHandler.openUri(url)
        }
    }

    private fun updateFileLocation(location: String) {
        preferences.edit {
            putString("cuberiteLocation", "$location/cuberite-server")
        }
    }


    @Throws(IOException::class)
    private fun createWebadminIni(webAdminFile: File): Ini {
        return if (!webAdminFile.exists()) {
            Ini().apply {
                put("WebAdmin", "Ports", 8080)
                put("WebAdmin", "Enabled", 1)
                store(webAdminFile)
            }
        } else Ini(webAdminFile)
    }

    init {
        viewModelScope.launch {
            InstallService.serviceResult
                .collect { result -> _snackbarMessage.emit(result) }
        }
    }

    private companion object {
        const val LOG = "Cuberite/Settings"
    }
}

sealed interface DialogType {

    data object WebAdminLogin : DialogType

    data object Theme : DialogType

    sealed class Info(@StringRes val title: Int) : DialogType {

        data object Debug : Info(R.string.settings_info_debug)

        data object License : Info(R.string.settings_info_libraries)

    }
}
