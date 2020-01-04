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
import org.cuberite.android.State;

import java.io.File;

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
        return inflater.inflate(R.layout.fragment_control, null);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        mainButton = view.findViewById(R.id.mainButton);
        mainButtonColor = ContextCompat.getColor(getContext(), R.color.bg);
        preferences = getActivity().getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE);
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

    private State getState() {
        State state;
        SharedPreferences preferences = getContext().getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE);
        boolean hasBinary = false;
        boolean hasServer = false;

        // Install state
        if (new File(PRIVATE_DIR + "/" + preferences.getString("executableName", "")).exists())
            hasBinary = true;
        if (new File(preferences.getString("cuberiteLocation", "")).exists())
            hasServer = true;

        if (CuberiteService.isCuberiteRunning(getActivity()))
            state = State.RUNNING;
        else if (hasBinary && hasServer)
            state = State.READY;
        else if (!hasServer && !hasBinary)
            state = State.NEED_DOWNLOAD_BOTH;
        else if (!hasServer)
            state = State.NEED_DOWNLOAD_SERVER;
        else
            state = State.NEED_DOWNLOAD_BINARY;

        Log.d(LOG, "Getting State: " + state.toString());
        return state;
    }

    private void updateControlButton() {
        final State state = getState();

        if (state == State.RUNNING) {
            int colorTo = ContextCompat.getColor(getContext(), R.color.warning);
            animateColorChange(mainButton, mainButtonColor, colorTo);
            mainButton.setText(getText(R.string.do_stop_cuberite));
            mainButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    doStop();
                }
            });
        } else if (state == State.READY) {
            int colorTo = ContextCompat.getColor(getContext(), R.color.accent);
            animateColorChange(mainButton, mainButtonColor, colorTo);
            mainButton.setText(getText(R.string.do_start_cuberite));
            mainButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    doStart();
                }
            });
        } else {
            int colorTo = ContextCompat.getColor(getContext(), R.color.accent);
            animateColorChange(mainButton, mainButtonColor, colorTo);
            mainButton.setText(getText(R.string.do_install_cuberite));
            mainButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    String action = "install";
                    SettingsFragment.installCuberiteDownload(getActivity(), action, getState());

                    LocalBroadcastManager.getInstance(getContext()).registerReceiver(new BroadcastReceiver() {
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

        getActivity().startService(serviceIntent);

        updateControlButton();
    }

    private void doStop() {
        Log.d(LOG, "Stopping Cuberite");

        LocalBroadcastManager.getInstance(getContext()).sendBroadcast(new Intent("stop"));

        int colorTo = ContextCompat.getColor(getContext(), R.color.danger);
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
        LocalBroadcastManager.getInstance(getContext()).sendBroadcast(new Intent("kill"));
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
            MainActivity.showSnackBar(getActivity(), String.format(getString(R.string.status_failed_start), InstallService.getPreferredABI()));
        }
    };

    // Register/unregister receivers and update button state
    @Override
    public void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(cuberiteServiceCallback);
        LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(showStartupError);
    }

    @Override
    public void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(getContext()).registerReceiver(cuberiteServiceCallback, new IntentFilter("CuberiteService.callback"));
        LocalBroadcastManager.getInstance(getContext()).registerReceiver(showStartupError, new IntentFilter("showStartupError"));
        updateControlButton();
    }
}
