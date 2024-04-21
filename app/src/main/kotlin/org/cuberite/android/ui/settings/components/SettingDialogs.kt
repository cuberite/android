package org.cuberite.android.ui.settings.components

import android.os.Build
import android.os.Environment
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.cuberite.android.MainApplication
import org.cuberite.android.R
import org.cuberite.android.services.CuberiteService
import org.cuberite.android.services.InstallService
import org.cuberite.android.ui.settings.DialogType

@Composable
fun SettingDialog(
    dialogType: DialogType,
    onSelectTheme: (Themes) -> Unit,
    onSetLogin: (username: String, password: String) -> Unit,
    onDismiss: () -> Unit,
) {
    when (dialogType) {
        DialogType.Info.Debug -> DebugInfoDialog(onDismiss)
        DialogType.Info.License -> LicenseInfoDialog(onDismiss)

        is DialogType.WebAdminLogin -> WebAdminLoginDialog(
            oldUsername = dialogType.username,
            oldPassword = dialogType.password,
            onComplete = onSetLogin,
            onDismiss = onDismiss
        )

        is DialogType.Theme -> ThemeDialog(
            selectedTheme = dialogType.selectedTheme,
            onComplete = onSelectTheme,
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun ThemeDialog(
    selectedTheme: Themes,
    onComplete: (Themes) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .background(
                    MaterialTheme.colorScheme.surfaceContainer,
                    shape = MaterialTheme.shapes.extraLarge
                )
                .padding(24.dp),
        ) {
            Text(
                modifier = Modifier.padding(bottom = 16.dp),
                text = stringResource(R.string.settings_webadmin_login),
                style = MaterialTheme.typography.headlineSmall,
            )
            Themes.entries.forEach { theme ->
                val isSelected = remember(selectedTheme) { theme == selectedTheme }
                ThemeItem(
                    themeName = stringResource(theme.stringRes),
                    isSelected = isSelected,
                    onSelect = {
                        onComplete(theme)
                    }
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            TextButton(
                modifier = Modifier.align(Alignment.End),
                onClick = onDismiss
            ) {
                Text(text = stringResource(R.string.cancel))
            }
        }
    }
}

@Composable
private fun ThemeItem(
    themeName: String,
    isSelected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier.clickable(onClick = onSelect).fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = isSelected, onClick = onSelect)
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = themeName)
    }
}

@Composable
private fun WebAdminLoginDialog(
    oldUsername: String,
    oldPassword: String,
    onComplete: (username: String, password: String) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        val (username, onUsernameChange) = remember {
            mutableStateOf(oldUsername)
        }
        val (password, onPasswordChange) = remember {
            mutableStateOf(oldPassword)
        }
        Column(
            modifier = Modifier
                .background(
                    MaterialTheme.colorScheme.surfaceContainer,
                    shape = MaterialTheme.shapes.extraLarge
                )
                .padding(24.dp),
        ) {
            Text(
                modifier = Modifier.padding(bottom = 16.dp),
                text = stringResource(R.string.settings_webadmin_login),
                style = MaterialTheme.typography.headlineSmall,
            )
            TextField(
                value = username,
                onValueChange = onUsernameChange,
                leadingIcon = {
                    Icon(imageVector = Icons.Rounded.Person, contentDescription = null)
                },
                label = {
                    Text(text = stringResource(R.string.username))
                },
            )
            Spacer(modifier = Modifier.height(16.dp))
            TextField(
                value = password,
                onValueChange = onPasswordChange,
                leadingIcon = {
                    Icon(imageVector = Icons.Rounded.Password, contentDescription = null)
                },
                label = {
                    Text(text = stringResource(R.string.password))
                },
            )
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.align(Alignment.End),
            ) {
                TextButton(onClick = onDismiss) {
                    Text(text = stringResource(R.string.cancel))
                }
                FilledTonalButton(onClick = { onComplete(username, password) }) {
                    Text(text = stringResource(R.string.ok))
                }
            }
        }
    }
}

@Composable
private fun DebugInfoDialog(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current

    val message = remember {
        """Running on Android ${Build.VERSION.RELEASE} (API Level ${Build.VERSION.SDK_INT})
Using ABI ${CuberiteService.preferredABI}
IP: ${CuberiteService.ipAddress}
Private directory: ${context.filesDir}
Public directory: ${Environment.getExternalStorageDirectory()}
Storage location: ${MainApplication.preferences.getString("cuberiteLocation", "")}
Download URL: ${InstallService.DOWNLOAD_HOST}"""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.settings_info_debug))
        },
        text = {
            Text(text = message)
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource((R.string.ok)))
            }
        }
    )
}

@Composable
private fun LicenseInfoDialog(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current

    val message = remember {
        """${context.getString(R.string.ini4j_license)}

${context.getString(R.string.ini4j_license_description)}"""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.settings_info_libraries))
        },
        text = {
            Text(text = message)
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource((R.string.ok)))
            }
        }
    )
}

enum class Themes(@StringRes val stringRes: Int) {
    Light(R.string.settings_theme_light),
    Dark(R.string.settings_theme_dark),
    System(R.string.settings_theme_auto),
}

fun themesFromInt(systemTheme: Int): Themes = when (systemTheme) {
    AppCompatDelegate.MODE_NIGHT_NO -> Themes.Light
    AppCompatDelegate.MODE_NIGHT_YES -> Themes.Dark
    AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM -> Themes.System
    else -> error("Unknown Theme")
}

val Themes.toAndroidTheme: Int
    get() = when (this) {
        Themes.Light -> AppCompatDelegate.MODE_NIGHT_NO
        Themes.Dark -> AppCompatDelegate.MODE_NIGHT_YES
        Themes.System -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }
