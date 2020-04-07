package org.cuberite.android.fragments;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
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
import org.cuberite.android.services.CuberiteService;
import org.cuberite.android.services.InstallService;
import org.cuberite.android.R;
import org.cuberite.android.helpers.State;

import static android.content.Context.MODE_PRIVATE;
import static org.cuberite.android.MainActivity.PACKAGE_NAME;
import static org.cuberite.android.MainActivity.PRIVATE_DIR;

public class ControlFragment extends Fragment {
    // Logging tag
    private String LOG = "Cuberite/Control";

    private SharedPreferences preferences;
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
        preferences = requireContext().getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE);
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

    private void updateControlButton() {
        final int state = State.getState(requireContext());

        if (state == State.RUNNING) {
            int colorTo = ContextCompat.getColor(requireContext(), R.color.warning);
            animateColorChange(mainButton, mainButtonColor, colorTo);
            mainButton.setText(getText(R.string.do_stop_cuberite));
            mainButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    doStop();
                }
            });
        } else if (state == State.READY) {
            int colorTo = ContextCompat.getColor(requireContext(), R.color.accent);
            animateColorChange(mainButton, mainButtonColor, colorTo);
            mainButton.setText(getText(R.string.do_start_cuberite));
            mainButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    doStart();
                }
            });
        } else {
            int colorTo = ContextCompat.getColor(requireContext(), R.color.accent);
            animateColorChange(mainButton, mainButtonColor, colorTo);
            mainButton.setText(getText(R.string.do_install_cuberite));
            mainButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    String action = "install";
                    SettingsFragment.installCuberiteDownload(requireActivity(), action, state);

                    LocalBroadcastManager.getInstance(requireContext()).registerReceiver(new BroadcastReceiver() {
                        @Override
                        public void onReceive(Context context, Intent intent) {
                            LocalBroadcastManager.getInstance(context).unregisterReceiver(this);
                            updateControlButton();
                        }
                    }, new IntentFilter("InstallService.callback"));
                }
            });
        }
    }

    private void doStart() {
        Log.d(LOG, "Starting Cuberite");

        Intent serviceIntent = new Intent(getContext(), CuberiteService.class);
        serviceIntent.putExtra("location", preferences.getString("cuberiteLocation", ""));
        serviceIntent.putExtra("binary", PRIVATE_DIR + "/" + preferences.getString("executableName", ""));

        requireContext().startService(serviceIntent);

        updateControlButton();
    }

    private void doStop() {
        Log.d(LOG, "Stopping Cuberite");

        LocalBroadcastManager.getInstance(requireContext()).sendBroadcast(new Intent("stop"));

        int colorTo = ContextCompat.getColor(requireContext(), R.color.danger);
        animateColorChange(mainButton, mainButtonColor, colorTo);
        mainButton.setText(getText(R.string.do_kill_cuberite));
        mainButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                doKill();
            }
        });
    }

    private void doKill() {
        LocalBroadcastManager.getInstance(requireContext()).sendBroadcast(new Intent("kill"));
    }

    // Broadcast receivers
    private BroadcastReceiver cuberiteServiceCallback = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(LOG, "Cuberite exited on process");
            updateControlButton(); // Sets the start button color correctly
        }
    };

    private BroadcastReceiver showStartupError = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(LOG, "Cuberite exited on process");
            MainActivity.showSnackBar(requireActivity(), String.format(getString(R.string.status_failed_start), InstallService.getPreferredABI()));
        }
    };

    // Register/unregister receivers and update button state
    @Override
    public void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(cuberiteServiceCallback);
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(showStartupError);
    }

    @Override
    public void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(cuberiteServiceCallback, new IntentFilter("CuberiteService.callback"));
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(showStartupError, new IntentFilter("showStartupError"));
        updateControlButton();
    }
}
