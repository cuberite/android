package org.cuberite.android

import android.Manifest
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.core.content.edit
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import org.cuberite.android.extension.isExternalStorageGranted
import org.cuberite.android.ui.console.navigation.navigateToConsole
import org.cuberite.android.ui.control.navigation.navigateToControl
import org.cuberite.android.ui.navigation.CuberiteNavGraph
import org.cuberite.android.ui.navigation.TopLevelNavigation
import org.cuberite.android.ui.navigation.TopLevelNavigation.Console
import org.cuberite.android.ui.navigation.TopLevelNavigation.Control
import org.cuberite.android.ui.navigation.TopLevelNavigation.Settings
import org.cuberite.android.ui.settings.navigation.navigateToSettings
import org.cuberite.android.ui.theme.CuberiteTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            CuberiteTheme {
                Cuberite()
            }
        }
    }
}

@Composable
fun Cuberite() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val permissionLauncher = rememberLauncherForActivityResult(RequestPermission()) { isGranted ->
        MainApplication.preferences.edit {
            if (isGranted) {
                putString("cuberiteLocation", "${MainApplication.publicDir}/cuberite-server")
            } else {
                putString("cuberiteLocation", "${MainApplication.privateDir}/cuberite-server")
            }
        }
    }

    var showPermissionPopup by remember { mutableStateOf(false) }

    PermissionCheckSideEffect(
        preferences = MainApplication.preferences,
        onRequest = { showPermissionPopup = true },
        onPause = { showPermissionPopup = false },
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                TopLevelNavigation.entries.forEach { navigation ->
                    val selected = remember(currentDestination?.route) {
                        currentDestination?.route == navigation.route
                    }
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            when (navigation) {
                                Control -> navController.navigateToControl()
                                Console -> navController.navigateToConsole()
                                Settings -> navController.navigateToSettings()
                            }
                        },
                        icon = {
                            Icon(
                                painter = painterResource(navigation.iconRes),
                                contentDescription = null,
                            )
                        },
                        label = {
                            Text(text = stringResource(navigation.labelRes))
                        },
                    )
                }
            }
        },
    ) { innerPadding ->
        CuberiteNavGraph(
            navController = navController,
            modifier = Modifier.padding(innerPadding),
        )
    }
    if (showPermissionPopup) {
        PermissionPopup(
            onDismiss = { showPermissionPopup = false },
            onConfirm = {
                permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            },
        )
    }
}

@Composable
private fun PermissionPopup(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(R.string.ok))
            }
        },
        title = {
            Text(text = stringResource(R.string.status_permissions_needed))
        },
        text = {
            Text(text = stringResource(R.string.message_externalstorage_permission))
        }
    )
}

@Composable
private fun PermissionCheckSideEffect(
    preferences: SharedPreferences,
    onRequest: () -> Unit,
    onPause: () -> Unit,
) {
    val context = LocalContext.current
    val location = remember {
        preferences.getString("cuberiteLocation", "")
    }
    LifecycleResumeEffect(true) {
        if (!context.isExternalStorageGranted) {
            if (location!!.isEmpty() || location.startsWith(MainApplication.publicDir)) {
                onRequest()
            }
        } else if (location!!.isEmpty() || location.startsWith(MainApplication.privateDir)) {
            preferences.edit {
                putString("cuberiteLocation", "${MainApplication.publicDir}/cuberite-server")
            }
        }
        onPauseOrDispose {
            onPause()
        }
    }
}
