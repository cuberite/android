package org.cuberite.android.ui.control

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.cuberite.android.extension.Default
import org.cuberite.android.services.CuberiteService
import org.cuberite.android.services.InstallService

class ControlViewModel : ViewModel() {

    private val isActionStop: MutableStateFlow<Boolean> = MutableStateFlow(false)

    private val installServiceStream: StateFlow<String> = InstallService
        .serviceResult
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Default,
            initialValue = ""
        )

    private val serviceStream: StateFlow<Result<Unit>?> = CuberiteService
        .result
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Default,
            initialValue = null
        )

    val snackbarState = SnackbarHostState()

    val state: StateFlow<ControlAction> =
        combine(
            CuberiteService.isRunning,
            isActionStop,
            installServiceStream,
            serviceStream,
        ) { isServiceRunning, isStopped, _, _ ->
            when {
                isStopped && isServiceRunning -> {
                    ControlAction.Kill
                }

                isServiceRunning -> {
                    ControlAction.Stop(CuberiteService.ipAddress)
                }

                InstallService.isInstalled -> {
                    isActionStop.emit(false)
                    ControlAction.Start
                }

                else -> {
                    isActionStop.emit(false)
                    ControlAction.Install
                }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Default,
            initialValue = ControlAction.Start
        )

    fun onActionClick(context: Context) {
        viewModelScope.launch {
            when (state.value) {
                ControlAction.Install -> InstallService.download(context as Activity)
                ControlAction.Start -> CuberiteService.start(context as Activity)
                ControlAction.Kill -> CuberiteService.kill()
                is ControlAction.Stop -> {
                    isActionStop.emit(true)
                    CuberiteService.stop()
                }
            }
        }
    }

    init {
        viewModelScope.launch {
            launch {
                InstallService.serviceResult.collect { result ->
                    snackbarState.showSnackbar(result)
                }
            }
            launch {
                CuberiteService.result.collect { result ->
                    result?.onFailure {
                        Log.d(LOG, "Cuberite exited on process")
                        snackbarState.showSnackbar(it.message ?: "")
                    }
                }
            }
        }
    }

    private companion object {
        const val LOG = "Cuberite/Control"
    }
}

sealed interface ControlAction {

    data object Install : ControlAction

    data object Start : ControlAction

    data class Stop(val ipAddress: String) : ControlAction

    data object Kill : ControlAction

}

val ControlAction.containerColor: Color
    @Composable
    get() = when (this) {
        ControlAction.Start -> MaterialTheme.colorScheme.tertiary
        ControlAction.Install -> MaterialTheme.colorScheme.primary
        ControlAction.Kill -> MaterialTheme.colorScheme.error
        is ControlAction.Stop -> MaterialTheme.colorScheme.error
    }

val ControlAction.contentColor: Color
    @Composable
    get() = contentColorFor(backgroundColor = containerColor)
