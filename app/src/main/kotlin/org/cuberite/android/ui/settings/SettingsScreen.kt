package org.cuberite.android.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import kotlinx.coroutines.launch
import org.cuberite.android.BuildConfig
import org.cuberite.android.R
import org.cuberite.android.extension.isSDAvailable
import org.cuberite.android.services.InstallService
import org.cuberite.android.services.InstallService.State.PICK_FILE_BINARY
import org.cuberite.android.services.InstallService.State.PICK_FILE_SERVER
import org.cuberite.android.ui.settings.components.Category
import org.cuberite.android.ui.settings.components.CategoryScope
import org.cuberite.android.ui.settings.components.DialogItem
import org.cuberite.android.ui.settings.components.Footer
import org.cuberite.android.ui.settings.components.SettingDialog
import org.cuberite.android.ui.settings.components.SwitchItem
import org.cuberite.android.ui.settings.components.Themes
import org.cuberite.android.ui.settings.components.rememberCategory

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        launch {
            viewModel.snackbarMessage.collect {
                snackbarHost.showSnackbar(it)
            }
        }
        launch {
            viewModel.snackbarStringRes.collect {
                snackbarHost.showSnackbar(context.getString(it))
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHost) }
    ) { paddingValues ->
        paddingValues
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
        ) {
            val isSDAvailable = remember { context.isSDAvailable }
            BasicSettings(
                onEditTheme = viewModel::showDialog,
                currentTheme = viewModel.theme,
                startOnStartup = viewModel.isStartOnBoot,
                onStartupCheck = viewModel::setStartUp,
            ) {
                if (isSDAvailable) {
                    SwitchItem(
                        title = stringResource(R.string.settings_sd_card),
                        description = stringResource(R.string.settings_sd_card_explanation),
                        isChecked = viewModel.isUsingSdCard,
                        onCheckedChange = { viewModel.setSdCard(context, it) },
                    )
                }
            }
            HorizontalDivider()
            WebAdmin(
                webAdminUrl = viewModel.webAdminUrl,
                onOpenWebAdmin = { viewModel.openWebAdmin(uriHandler) },
                onOpenWebAdminLogin = viewModel::showDialog,
                username = viewModel.username,
                password = viewModel.password,
            )
            HorizontalDivider()
            UpdateCuberite {
                viewModel.updateCuberite(context)
            }
            HorizontalDivider()
            LocalInstall { state, uri ->
                viewModel.installLocal(context, uri, state)
            }
            HorizontalDivider()
            About(
                onClick = viewModel::showDialog,
                onVersionClick = { uriHandler.openUri(DownloadCuberite) }
            )
        }
        if (viewModel.dialogType != null) {
            SettingDialog(
                dialogType = requireNotNull(viewModel.dialogType),
                onSelectTheme = viewModel::setNewTheme,
                onSetLogin = viewModel::setWebAdminLogin,
                onDismiss = viewModel::hideDialog
            )
        }
    }
}

@Composable
private fun BasicSettings(
    onEditTheme: (DialogType) -> Unit,
    currentTheme: Themes,
    startOnStartup: Boolean,
    onStartupCheck: (Boolean) -> Unit,
    extras: @Composable CategoryScope.() -> Unit,
) {
    val settings = rememberCategory(
        title = stringResource(R.string.title_activity_settings),
        icon = Icons.Rounded.Settings,
    )
    Category(data = settings) {
        DialogItem(
            title = stringResource(R.string.settings_theme),
            description = stringResource(currentTheme.stringRes),
            onClick = { onEditTheme(DialogType.Theme(currentTheme)) },
        )
        SwitchItem(
            title = stringResource(R.string.settings_startup_toggle),
            description = stringResource(R.string.settings_startup_explanation),
            isChecked = startOnStartup,
            onCheckedChange = onStartupCheck,
        )
        extras()
    }
}

@Composable
private fun WebAdmin(
    username: String,
    password: String,
    webAdminUrl: String?,
    onOpenWebAdmin: () -> Unit,
    onOpenWebAdminLogin: (DialogType) -> Unit,
) {
    val webAdmin = rememberCategory(
        title = stringResource(R.string.settings_webadmin_heading),
        icon = ImageVector.vectorResource(R.drawable.ic_webadmin),
        description = """${stringResource(R.string.settings_webadmin_explanation)}
            
URL:$webAdminUrl""".trimMargin()
    )
    Category(data = webAdmin) {
        DialogItem(
            title = stringResource(R.string.settings_webadmin_login),
            onClick = { onOpenWebAdminLogin(DialogType.WebAdminLogin(username, password)) },
        )
        DialogItem(
            title = stringResource(R.string.settings_webadmin_open),
            onClick = onOpenWebAdmin,
        )
    }
}

@Composable
private fun UpdateCuberite(
    onClick: () -> Unit,
) {
    val updateCuberite = rememberCategory(
        title = stringResource(R.string.settings_install_update),
        description = stringResource(R.string.settings_install_update_explanation),
        icon = Icons.Rounded.CloudDownload,
    )
    Category(data = updateCuberite) {
        DialogItem(
            title = stringResource(R.string.settings_install_update),
            onClick = onClick,
        )
    }
}

@Composable
private fun LocalInstall(
    onResult: (state: InstallService.State, uri: Uri?) -> Unit,
) {

    val installLocal = rememberCategory(
        title = stringResource(R.string.settings_install_manually),
        description = stringResource(R.string.settings_install_manually_explanation),
        icon = Icons.Rounded.Archive,
    )
    val binaryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? -> onResult(PICK_FILE_BINARY, uri) }
    )
    val serverPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? -> onResult(PICK_FILE_SERVER, uri) }
    )
    Category(data = installLocal) {
        DialogItem(
            title = stringResource(R.string.settings_install_select_binary),
            onClick = { binaryPicker.launch("*/*") },
        )
        DialogItem(
            title = stringResource(R.string.settings_install_select_server),
            onClick = { serverPicker.launch("*/*") },
        )
    }
}

@Composable
private fun About(
    onClick: (DialogType) -> Unit,
    onVersionClick: () -> Unit,
) {
    val about = rememberCategory(title = stringResource(R.string.settings_info_heading))
    Category(data = about) {
        DialogItem(
            title = stringResource(R.string.settings_info_debug),
            onClick = { onClick(DialogType.Info.Debug) },
        )
        DialogItem(
            title = stringResource(R.string.settings_info_libraries),
            onClick = { onClick(DialogType.Info.Debug) },
        )
        Footer(
            modifier = Modifier.clickable(onClick = onVersionClick),
            text = stringResource(R.string.settings_info_version, BuildConfig.VERSION_NAME)
        )
    }
}

private const val DownloadCuberite = "https://download.cuberite.org/android"
