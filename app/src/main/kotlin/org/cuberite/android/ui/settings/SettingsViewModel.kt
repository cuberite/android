package org.cuberite.android.ui.settings

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
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
import org.cuberite.android.ui.settings.components.Themes
import org.cuberite.android.ui.settings.components.themesFromInt
import org.cuberite.android.ui.settings.components.toAndroidTheme
import org.ini4j.Ini
import java.io.File
import java.io.IOException

class SettingsViewModel : ViewModel() {

    private val preferences: SharedPreferences = MainApplication.preferences

    private val isCuberiteRunning: StateFlow<Boolean> = CuberiteService.isRunning

    private val currentTheme
        get() = preferences.getInt("defaultTheme", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)

    private val currentLocation: String
        get() = preferences.getString("cuberiteLocation", "") ?: ""

    private val webAdminFile: File
        get() = File(File(currentLocation), "webadmin.ini")

    private val isSdEnabled: Boolean
        get() = !(currentLocation.startsWith(MainApplication.publicDir)
                || currentLocation.startsWith(MainApplication.privateDir))

    private val _snackbarMessage: MutableSharedFlow<String> = MutableSharedFlow()
    val snackbarMessage: SharedFlow<String> = _snackbarMessage.asSharedFlow()

    private val _snackbarStringRes: MutableSharedFlow<Int> = MutableSharedFlow()
    val snackbarStringRes: SharedFlow<Int> = _snackbarStringRes.asSharedFlow()

    var isUsingSdCard: Boolean by mutableStateOf(isSdEnabled)
        private set

    var isStartOnBoot: Boolean by mutableStateOf(preferences.getBoolean("startOnBoot", false))
        private set

    var dialogType: DialogType? by mutableStateOf(null)
        private set

    var theme: Themes by mutableStateOf(themesFromInt(currentTheme))
        private set

    var username: String by mutableStateOf("")
        private set

    var password: String by mutableStateOf("")
        private set

    val webAdminUrl: String?
        get() {
            return try {
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
                "http://$ip:$port"
            } catch (e: IOException) {
                Log.e(LOG, "Something went wrong while opening the ini file", e)
                null
            }
        }

    fun setStartUp(value: Boolean) {
        preferences.edit {
            putBoolean("startOnBoot", value)
            isStartOnBoot = value
        }
    }

    fun setSdCard(context: Context, value: Boolean) {
        if (isCuberiteRunning.value) {
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

    fun setNewTheme(value: Themes) {
        theme = value
        preferences.edit {
            putInt("defaultTheme", value.toAndroidTheme)
        }
        AppCompatDelegate.setDefaultNightMode(theme.toAndroidTheme)
        hideDialog()
    }

    fun setWebAdminLogin(newUsername: String, newPassword: String) {
        require(webAdminFile.exists()) { "We should not have opened the dialog if this was empty" }
        val ini = createWebadminIni(webAdminFile)
        ini.remove("User:${this.username}")
        ini.put("User:$newUsername", "Password", newPassword)
        try {
            ini.store(webAdminFile)
            viewModelScope.launch {
                initWebAdmin()
                _snackbarStringRes.emit(R.string.settings_webadmin_success)
            }
        } catch (e: IOException) {
            Log.e(LOG, "Something went wrong while opening the ini file", e)
            viewModelScope.launch {
                _snackbarStringRes.emit(R.string.settings_webadmin_error)
            }
        }
        hideDialog()
    }

    fun showDialog(type: DialogType) {
        if (type is DialogType.WebAdminLogin) {
            initWebAdmin()
        }
        dialogType = type
    }

    // this is launched in `init` because other wise it will lead to recomposition
    private fun initWebAdmin() {
        try {
            val ini = createWebadminIni(webAdminFile)
            ini.put("WebAdmin", "Enabled", 1)
            for (sectionName in ini.keys) {
                if (sectionName.startsWith("User:")) {
                    username = sectionName.substring(5)
                    password = ini[sectionName, "Password"]
                }
            }
        } catch (e: IOException) {
            viewModelScope.launch {
                Log.e(LOG, "Something went wrong while opening the ini file", e)
                _snackbarStringRes.emit(R.string.settings_webadmin_error)
            }
        }
    }

    fun hideDialog() {
        dialogType = null
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
        if (!isCuberiteRunning.value) {
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
            initWebAdmin()
            InstallService.serviceResult
                .collect { result -> _snackbarMessage.emit(result) }
        }
    }

    private companion object {
        const val LOG = "Cuberite/Settings"
    }
}

sealed interface DialogType {

    data class WebAdminLogin(val username: String, val password: String) : DialogType

    data class Theme(val selectedTheme: Themes) : DialogType

    sealed interface Info : DialogType {

        data object Debug : Info

        data object License : Info

    }
}
