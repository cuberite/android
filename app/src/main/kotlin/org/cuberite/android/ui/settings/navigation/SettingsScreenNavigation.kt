package org.cuberite.android.ui.settings.navigation

import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import org.cuberite.android.ui.settings.SettingsScreen

const val SETTINGS_ROUTE = "settings"

fun NavController.navigateToSettings(navOptions: NavOptions? = null) {
    navigate(SETTINGS_ROUTE, navOptions)
}

fun NavGraphBuilder.settings() {
    composable(SETTINGS_ROUTE) {
        SettingsScreen(viewModel = viewModel())
    }
}