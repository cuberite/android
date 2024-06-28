package org.cuberite.android.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import org.cuberite.android.R
import org.cuberite.android.ui.console.navigation.CONSOLE_ROUTE
import org.cuberite.android.ui.console.navigation.console
import org.cuberite.android.ui.control.navigation.CONTROL_ROUTE
import org.cuberite.android.ui.control.navigation.control
import org.cuberite.android.ui.settings.navigation.SETTINGS_ROUTE
import org.cuberite.android.ui.settings.navigation.settings

@Composable
fun CuberiteNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        modifier = modifier,
        navController = navController,
        startDestination = CONTROL_ROUTE,
    ) {
        control()
        console()
        settings()
    }
}

enum class TopLevelNavigation(
    @DrawableRes val iconRes: Int,
    @StringRes val labelRes: Int,
    val route: String
) {
    Control(R.drawable.ic_control, R.string.do_open_control, CONTROL_ROUTE),
    Console(R.drawable.ic_console, R.string.do_open_log, CONSOLE_ROUTE),
    Settings(R.drawable.ic_settings, R.string.do_open_settings, SETTINGS_ROUTE),
}
