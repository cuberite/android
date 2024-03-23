package org.cuberite.android.fragments

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar
import org.cuberite.android.R
import org.cuberite.android.helpers.CuberiteHelper
import org.cuberite.android.helpers.InstallHelper
import org.cuberite.android.helpers.StateHelper

class ControlFragment : Fragment() {
    // Logging tag
    private val log = "Cuberite/Control"
    private var mainButtonColor = 0
    private lateinit var mainButton: Button

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_control, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        mainButton = view.findViewById(R.id.mainButton)
        mainButtonColor = MaterialColors.getColor(mainButton, com.google.android.material.R.attr.colorSurface)
    }

    private fun animateColorChange(button: Button, colorFrom: Int, colorTo: Int) {
        Log.d(log, "Changing color from " + Integer.toHexString(colorFrom) + " to " + Integer.toHexString(colorTo))
        val colorAnimation = ValueAnimator.ofObject(ArgbEvaluator(), colorFrom, colorTo)
        colorAnimation.setDuration(300)
        colorAnimation.addUpdateListener { animator: ValueAnimator -> button.setBackgroundColor(animator.getAnimatedValue() as Int) }
        colorAnimation.start()
        mainButtonColor = colorTo
    }

    private fun setInstallButton(state: StateHelper.State?) {
        val colorTo = MaterialColors.getColor(mainButton, com.google.android.material.R.attr.colorPrimary)
        animateColorChange(mainButton, mainButtonColor, colorTo)
        mainButton.text = getText(R.string.do_install_cuberite)
        mainButton.setOnClickListener {
            LocalBroadcastManager.getInstance(requireContext()).registerReceiver(
                    installServiceCallback,
                    IntentFilter("InstallService.callback")
            )
            InstallHelper.installCuberiteDownload(requireActivity(), state)
        }
    }

    private fun setStartButton() {
        val colorTo = MaterialColors.getColor(mainButton, com.google.android.material.R.attr.colorPrimary)
        animateColorChange(mainButton, mainButtonColor, colorTo)
        mainButton.text = getText(R.string.do_start_cuberite)
        mainButton.setOnClickListener {
            LocalBroadcastManager.getInstance(requireContext()).registerReceiver(
                    showStartupError,
                    IntentFilter("showStartupError")
            )
            CuberiteHelper.startCuberite(requireContext())
            setStopButton()
        }
    }

    private fun setStopButton() {
        val colorTo = MaterialColors.getColor(mainButton, com.google.android.material.R.attr.colorTertiary)
        animateColorChange(mainButton, mainButtonColor, colorTo)
        mainButton.text = getText(R.string.do_stop_cuberite)
        mainButton.setOnClickListener {
            CuberiteHelper.stopCuberite(requireContext())
            setKillButton()
        }
    }

    private fun setKillButton() {
        val colorTo = MaterialColors.getColor(mainButton, com.google.android.material.R.attr.colorError)
        animateColorChange(mainButton, mainButtonColor, colorTo)
        mainButton.text = getText(R.string.do_kill_cuberite)
        mainButton.setOnClickListener { CuberiteHelper.killCuberite(requireContext()) }
    }

    private fun updateControlButton() {
        when (val state = StateHelper.getState(requireContext())) {
            StateHelper.State.RUNNING -> {
                setStopButton()
            }
            StateHelper.State.READY -> {
                setStartButton()
            }
            else -> {
                setInstallButton(state)
            }
        }
    }

    // Broadcast receivers
    private val cuberiteServiceCallback: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            updateControlButton()
        }
    }
    private val installServiceCallback: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            LocalBroadcastManager.getInstance(context).unregisterReceiver(this)
            val result = intent.getStringExtra("result")
            Snackbar.make(requireActivity().findViewById(R.id.fragment_container), result!!, Snackbar.LENGTH_LONG)
                .setAnchorView(requireActivity().findViewById(R.id.bottom_navigation))
                .show()
            updateControlButton()
        }
    }
    private val showStartupError: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            LocalBroadcastManager.getInstance(context).unregisterReceiver(this)
            Log.d(log, "Cuberite exited on process")
            val message = String.format(
                getString(R.string.status_failed_start),
                CuberiteHelper.preferredABI
            )
            Snackbar.make(requireActivity().findViewById(R.id.fragment_container), message, Snackbar.LENGTH_LONG)
                .setAnchorView(requireActivity().findViewById(R.id.bottom_navigation))
                .show()
        }
    }

    // Register/unregister receivers and update button state
    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(cuberiteServiceCallback)
    }

    override fun onResume() {
        super.onResume()
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(cuberiteServiceCallback, IntentFilter("CuberiteService.callback"))
        updateControlButton()
    }
}
