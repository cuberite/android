package org.cuberite.android.ui.console.navigation

import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import org.cuberite.android.ui.console.ConsoleScreen

const val CONSOLE_ROUTE = "console"

fun NavController.navigateToConsole(navOptions: NavOptions? = null) {
    navigate(CONSOLE_ROUTE, navOptions)
}

fun NavGraphBuilder.console() {
    composable(CONSOLE_ROUTE) {
        ConsoleScreen(viewModel = viewModel())
    }
}
