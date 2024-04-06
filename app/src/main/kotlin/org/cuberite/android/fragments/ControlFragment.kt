package org.cuberite.android.fragments

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import org.cuberite.android.R
import org.cuberite.android.services.CuberiteService
import org.cuberite.android.services.InstallService

class ControlFragment : Fragment() {
    private var mainButtonColor = 0
    private lateinit var mainButton: Button

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_control, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mainButton = view.findViewById(R.id.mainButton)
        mainButtonColor =
            MaterialColors.getColor(mainButton, com.google.android.material.R.attr.colorSurface)
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                launch {
                    CuberiteService.isRunning.collect {
                        updateControlButton()
                    }
                }
                launch {
                    InstallService.serviceResult
                        .filterNotNull()
                        .collect { result ->
                            Snackbar.make(view.parent as View, result, Snackbar.LENGTH_LONG)
                                .setAnchorView(requireActivity().findViewById(R.id.bottom_navigation))
                                .show()
                            updateControlButton()
                            InstallService.resultConsumed()
                        }
                }
                launch {
                    CuberiteService.result.collect { result ->
                        result?.onFailure {
                            Log.d(LOG, "Cuberite exited on process")
                            val message = String.format(
                                getString(R.string.status_failed_start),
                                CuberiteService.preferredABI
                            )
                            Snackbar.make(
                                requireActivity().findViewById(R.id.fragment_container),
                                message,
                                Snackbar.LENGTH_LONG
                            )
                                .setAnchorView(requireActivity().findViewById(R.id.bottom_navigation))
                                .show()
                        }
                        updateControlButton()
                    }
                }
            }
        }
        updateControlButton()
    }

    private fun animateColorChange(button: Button, colorFrom: Int, colorTo: Int) {
        Log.d(
            LOG,
            "Changing color from " + Integer.toHexString(colorFrom) + " to " + Integer.toHexString(
                colorTo
            )
        )
        val colorAnimation = ValueAnimator.ofObject(ArgbEvaluator(), colorFrom, colorTo)
        colorAnimation.setDuration(300)
        colorAnimation.addUpdateListener { animator: ValueAnimator ->
            button.setBackgroundColor(
                animator.animatedValue as Int
            )
        }
        colorAnimation.start()
        mainButtonColor = colorTo
    }

    private fun setInstallButton() {
        val colorTo =
            MaterialColors.getColor(mainButton, com.google.android.material.R.attr.colorPrimary)
        animateColorChange(mainButton, mainButtonColor, colorTo)
        mainButton.text = getText(R.string.do_install_cuberite)
        mainButton.setOnClickListener {
            InstallService.download(requireActivity())
        }
    }

    private fun setStartButton() {
        val colorTo =
            MaterialColors.getColor(mainButton, com.google.android.material.R.attr.colorPrimary)
        animateColorChange(mainButton, mainButtonColor, colorTo)
        mainButton.text = getText(R.string.do_start_cuberite)
        mainButton.setOnClickListener {
            CuberiteService.start(requireActivity())
            setStopButton()
        }
    }

    private fun setStopButton() {
        val colorTo =
            MaterialColors.getColor(mainButton, com.google.android.material.R.attr.colorTertiary)
        animateColorChange(mainButton, mainButtonColor, colorTo)
        mainButton.text = getText(R.string.do_stop_cuberite)
        mainButton.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                CuberiteService.stop()
                setKillButton()
            }
        }
    }

    private fun setKillButton() {
        val colorTo =
            MaterialColors.getColor(mainButton, com.google.android.material.R.attr.colorError)
        animateColorChange(mainButton, mainButtonColor, colorTo)
        mainButton.text = getText(R.string.do_kill_cuberite)
        mainButton.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                CuberiteService.kill()
            }
        }
    }

    private fun updateControlButton() {
        if (CuberiteService.isRunning.value) {
            setStopButton()
        } else if (InstallService.isInstalled) {
            setStartButton()
        } else {
            setInstallButton()
        }
    }

    companion object {
        private const val LOG = "Cuberite/Control"
    }
}
