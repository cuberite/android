package org.cuberite.android.fragments;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;

import com.google.android.material.snackbar.Snackbar;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import org.cuberite.android.ProgressReceiver;
import org.cuberite.android.services.CuberiteService;
import org.cuberite.android.services.InstallService;
import org.cuberite.android.R;
import org.cuberite.android.State;
import org.cuberite.android.Tags;

import java.io.File;

import static android.content.Context.MODE_PRIVATE;
import static org.cuberite.android.MainActivity.PACKAGE_NAME;
import static org.cuberite.android.MainActivity.PRIVATE_DIR;

public class ControlFragment extends Fragment {
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

        // Initialize variables
        preferences = getActivity().getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE);
    }

    // Helper functions
    public static boolean isServiceRunning(Class<?> serviceClass, Context context) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    private State getState() {
        State state;
        boolean hasBinary = false;
        boolean hasServer = false;

        // Install state
        if (new File(PRIVATE_DIR + "/" + preferences.getString("executableName", "")).exists())
            hasBinary = true;
        if (new File(preferences.getString("cuberiteLocation", "")).exists())
            hasServer = true;

        if(isServiceRunning(CuberiteService.class, getContext()))
            state = State.RUNNING;
        else if (hasBinary && hasServer)
            state = State.OK;
        else if (!hasServer && !hasBinary)
            state = State.NEED_DOWNLOAD_BOTH;
        else if (!hasServer)
            state = State.NEED_DOWNLOAD_SERVER;
        else
            state = State.NEED_DOWNLOAD_BINARY;

        Log.d(Tags.MAIN_ACTIVITY, "Getting State: " + state.toString());

        return state;
    }

    private void animateColorChange(final View view, int colorFrom, int colorTo) {
        Log.d(Tags.MAIN_ACTIVITY, "Changing color from " + Integer.toHexString(colorFrom) + " to " + Integer.toHexString(colorTo));

        ValueAnimator colorAnimation = ValueAnimator.ofObject(new ArgbEvaluator(), colorFrom, colorTo);
        colorAnimation.setDuration(300);
        colorAnimation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animator) {
                GradientDrawable bgShape = (GradientDrawable) view.getBackground();
                bgShape.setColor((int) animator.getAnimatedValue());
            }
        });
        colorAnimation.start();
        mainButtonColor = colorTo;
    }

    private void checkState() {
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
        } else if (state == State.OK) {
            int colorTo = ContextCompat.getColor(getContext(), R.color.success);
            animateColorChange(mainButton, mainButtonColor, colorTo);
            mainButton.setText(getText(R.string.do_start_cuberite));
            mainButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    doStart();
                }
            });
        } else {
            int colorTo = ContextCompat.getColor(getContext(), R.color.primary);
            animateColorChange(mainButton, mainButtonColor, colorTo);
            mainButton.setText(getText(R.string.do_install_cuberite));
            mainButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent intent = new Intent(getContext(), InstallService.class);
                    intent.setAction("install");
                    intent.putExtra("downloadHost", preferences.getString("downloadHost", ""));
                    intent.putExtra("state", state.toString());
                    intent.putExtra("executableName", preferences.getString("executableName", ""));
                    intent.putExtra("targetDirectory", preferences.getString("cuberiteLocation", ""));
                    intent.putExtra("receiver", new ProgressReceiver(getContext(), new Handler()));

                    LocalBroadcastManager.getInstance(getContext()).registerReceiver(new BroadcastReceiver() {
                        @Override
                        public void onReceive(Context context, Intent intent) {
                            LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(this);
                            String error = intent.getStringExtra("error");
                            if(error != null) {
                                Snackbar.make(getActivity().findViewById(R.id.fragment_container), getString(R.string.status_download_error) + " " + error, Snackbar.LENGTH_LONG).show();
                            }
                            checkState();
                        }
                    }, new IntentFilter("InstallService.callback"));
                    getActivity().startService(intent);
                }
            });
        }
    }

    private void doStart() {
        Log.d(Tags.MAIN_ACTIVITY, "Starting Cuberite");

        Intent serviceIntent = new Intent(getContext(), CuberiteService.class);
        serviceIntent.putExtra("location", preferences.getString("cuberiteLocation", ""));
        serviceIntent.putExtra("binary", PRIVATE_DIR + "/" + preferences.getString("executableName", ""));
        getActivity().startService(serviceIntent);

        checkState();
    }

    private void doStop() {
        Log.d(Tags.MAIN_ACTIVITY, "Stopping Cuberite");

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

    private BroadcastReceiver showStartupError = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Snackbar.make(getActivity().findViewById(R.id.fragment_container), getString(R.string.status_failed_start) + " " + InstallService.getPreferredABI(), Snackbar.LENGTH_LONG)
                    .show();
        }
    };

    private BroadcastReceiver serverStopped = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(Tags.MAIN_ACTIVITY, "Cuberite exited on process");
            checkState(); // Sets the start button color correctly
        }
    };

    @Override
    public void onPause() {
        LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(showStartupError);
        LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(serverStopped);
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(getContext()).registerReceiver(showStartupError, new IntentFilter("showStartupError"));
        LocalBroadcastManager.getInstance(getContext()).registerReceiver(serverStopped, new IntentFilter("serverStopped"));
        checkState();
    }
}
