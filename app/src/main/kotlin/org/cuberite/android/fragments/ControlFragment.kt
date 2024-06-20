package org.cuberite.android.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import org.cuberite.android.ui.control.ControlScreen
import org.cuberite.android.ui.control.ControlViewModel
import org.cuberite.android.ui.theme.CuberiteTheme

class ControlFragment : Fragment() {

    private val viewModel: ControlViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                CuberiteTheme {
                    ControlScreen(viewModel = viewModel)
                }
            }
        }
    }
}
