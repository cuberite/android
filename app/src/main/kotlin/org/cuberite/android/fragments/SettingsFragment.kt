package org.cuberite.android.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.material3.Surface
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import org.cuberite.android.ui.settings.SettingsScreen
import org.cuberite.android.ui.settings.SettingsViewModel
import org.cuberite.android.ui.theme.CuberiteTheme

class SettingsFragment : Fragment() {

    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                CuberiteTheme {
                    Surface {
                        SettingsScreen(viewModel)
                    }
                }
            }
        }
    }
}
