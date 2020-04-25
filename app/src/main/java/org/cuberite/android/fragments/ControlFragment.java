package org.cuberite.android.fragments;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import org.cuberite.android.MainActivity;
import org.cuberite.android.helpers.CuberiteHelper;
import org.cuberite.android.helpers.InstallHelper;
import org.cuberite.android.helpers.StateHelper;
import org.cuberite.android.helpers.StateHelper.State;
import org.cuberite.android.R;

public class ControlFragment extends Fragment {
    // Logging tag
    private final String LOG = "Cuberite/Control";

    private Button mainButton;
    private int mainButtonColor;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_control, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        mainButton = view.findViewById(R.id.mainButton);
        mainButtonColor = ContextCompat.getColor(requireContext(), R.color.bg);
    }

    private void animateColorChange(final View view, int colorFrom, int colorTo) {
        Log.d(LOG, "Changing color from " + Integer.toHexString(colorFrom) + " to " + Integer.toHexString(colorTo));

        ValueAnimator colorAnimation = ValueAnimator.ofObject(new ArgbEvaluator(), colorFrom, colorTo);
        colorAnimation.setDuration(300);
        colorAnimation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animator) {
                view.setBackgroundColor((int) animator.getAnimatedValue());
            }
        });
        colorAnimation.start();
        mainButtonColor = colorTo;
    }

    private void setInstallButton(final State state) {
        int colorTo = ContextCompat.getColor(requireContext(), R.color.primary);
        animateColorChange(mainButton, mainButtonColor, colorTo);
        mainButton.setText(getText(R.string.do_install_cuberite));
        mainButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                LocalBroadcastManager.getInstance(requireContext()).registerReceiver(
                        installServiceCallback,
                        new IntentFilter("InstallService.callback")
                );

                InstallHelper.installCuberiteDownload(requireActivity(), state);
            }
        });
    }

    private void setStartButton() {
        int colorTo = ContextCompat.getColor(requireContext(), R.color.primary);
        animateColorChange(mainButton, mainButtonColor, colorTo);
        mainButton.setText(getText(R.string.do_start_cuberite));
        mainButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                LocalBroadcastManager.getInstance(requireContext()).registerReceiver(
                        showStartupError,
                        new IntentFilter("showStartupError")
                );

                CuberiteHelper.startCuberite(requireContext());
                setStopButton();
            }
        });
    }

    private void setStopButton() {
        int colorTo = ContextCompat.getColor(requireContext(), R.color.warning);
        animateColorChange(mainButton, mainButtonColor, colorTo);
        mainButton.setText(getText(R.string.do_stop_cuberite));
        mainButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                CuberiteHelper.stopCuberite(requireContext());
                setKillButton();
            }
        });
    }

    private void setKillButton() {
        int colorTo = ContextCompat.getColor(requireContext(), R.color.danger);
        animateColorChange(mainButton, mainButtonColor, colorTo);
        mainButton.setText(getText(R.string.do_kill_cuberite));
        mainButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                CuberiteHelper.killCuberite(requireContext());
            }
        });
    }

    private void updateControlButton() {
        final State state = StateHelper.getState(requireContext());

        if (state == State.RUNNING) {
            setStopButton();
        } else if (state == State.READY) {
            setStartButton();
        } else {
            setInstallButton(state);
        }
    }

    // Broadcast receivers
    private final BroadcastReceiver cuberiteServiceCallback = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateControlButton();
        }
    };

    private final BroadcastReceiver installServiceCallback = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            LocalBroadcastManager.getInstance(context).unregisterReceiver(this);
            String result = intent.getStringExtra("result");
            MainActivity.showSnackBar(requireContext(), result);
            updateControlButton();
        }
    };

    private final BroadcastReceiver showStartupError = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            LocalBroadcastManager.getInstance(context).unregisterReceiver(this);
            Log.d(LOG, "Cuberite exited on process");
            MainActivity.showSnackBar(
                    requireContext(),
                    String.format(
                            getString(R.string.status_failed_start),
                            CuberiteHelper.getPreferredABI()
                    )
            );
        }
    };

    // Register/unregister receivers and update button state
    @Override
    public void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(cuberiteServiceCallback);
    }

    @Override
    public void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(cuberiteServiceCallback, new IntentFilter("CuberiteService.callback"));
        updateControlButton();
    }
}
