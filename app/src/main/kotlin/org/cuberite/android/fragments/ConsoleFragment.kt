package org.cuberite.android.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.material3.Surface
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import org.cuberite.android.ui.console.ConsoleScreen
import org.cuberite.android.ui.console.ConsoleViewModel
import org.cuberite.android.ui.theme.CuberiteTheme

class ConsoleFragment : Fragment() {

    private val viewModel: ConsoleViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                CuberiteTheme {
                    Surface {
                        ConsoleScreen(viewModel)
                    }
                }
            }
        }
    }
}
