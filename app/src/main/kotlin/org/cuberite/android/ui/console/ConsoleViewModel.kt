package org.cuberite.android.ui.console

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.cuberite.android.services.CuberiteService

class ConsoleViewModel : ViewModel() {

    var command: String by mutableStateOf("")
        private set

    val logs: StateFlow<List<String>> = CuberiteService.updateLogLiveData
        .asFlow()
        .map { it.toString().lines() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    fun updateCommand(value: String) {
        command = value
    }

    fun invokeCommand() {
        viewModelScope.launch {
            if (CuberiteService.isRunning && command.isNotEmpty()) {
                Log.d(LOG, "Executing $command")
                CuberiteService.executeCommand(command)
                command = ""
            }
        }
    }

    private companion object {
        const val LOG = "Cuberite/Console"
    }
}
