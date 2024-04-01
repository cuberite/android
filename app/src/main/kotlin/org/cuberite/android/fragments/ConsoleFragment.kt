package org.cuberite.android.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import org.cuberite.android.MainActivity
import org.cuberite.android.services.CuberiteService
import org.cuberite.android.ui.console.ConsoleScreen
import org.cuberite.android.ui.theme.CuberiteTheme

class ConsoleFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                CuberiteTheme {
                    Surface {
                        val (command, onCommandChange) = remember {
                            mutableStateOf("")
                        }
                        val logs by CuberiteService.updateLogLiveData
                            .observeAsState(StringBuilder(""))
                        ConsoleScreen(
                            logs = logs.toString(),
                            command = command,
                            onCommandChange = onCommandChange,
                            onSendCommand = {
                                sendExecuteCommand(command)
                                onCommandChange("")
                            }
                        )
                    }
                }
            }
        }
    }

    private fun sendExecuteCommand(command: String) {
        if (command.isNotEmpty() && CuberiteService.isRunning) {
            Log.d(LOG, "Executing $command")
            MainActivity.executeCommandLiveData.postValue(command)
        }
    }

    companion object {
        private const val LOG = "Cuberite/Console"
    }
}
