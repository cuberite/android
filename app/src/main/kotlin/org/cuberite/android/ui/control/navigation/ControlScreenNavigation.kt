package org.cuberite.android.ui.control.navigation

import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import org.cuberite.android.ui.control.ControlScreen

const val CONTROL_ROUTE = "control"

fun NavController.navigateToControl(navOptions: NavOptions? = null) {
    navigate(CONTROL_ROUTE, navOptions)
}

fun NavGraphBuilder.control() {
    composable(CONTROL_ROUTE) {
        ControlScreen(viewModel = viewModel())
    }
}