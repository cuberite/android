package org.cuberite.android;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.security.MessageDigest;
import java.util.Enumeration;

public class MainActivity extends AppCompatActivity {
    // TODO: maybe improve landscape mode :P

    // Helper functions
    private boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
    private State getState() {
        State state = null;
        boolean hasBinary = false;
        boolean hasServer = false;

        // Install state
        if (new File(PRIVATE_DIR + "/" + preferences.getString("executableName", "")).exists())
            hasBinary = true;
        if (new File(preferences.getString("cuberiteLocation", "")).exists())
            hasServer = true;

        if(isServiceRunning(CuberiteService.class))
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
    static String getPreferredABI() {
        String abi;
        if(Build.VERSION.SDK_INT > 20)
            abi = Build.SUPPORTED_ABIS[0];
        else
            abi = Build.CPU_ABI;

        Log.d(Tags.MAIN_ACTIVITY, "Getting preferred ABI: " + abi);

        return abi;
    }
    public String getIpAddress() {
        try {
            for (Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces(); interfaces.hasMoreElements();) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (networkInterface.getName().contains("wlan")) {
                    for (Enumeration<InetAddress> addresses = networkInterface.getInetAddresses(); addresses.hasMoreElements();) {
                        InetAddress address = addresses.nextElement();
                        if (!address.isLoopbackAddress() && (address.getAddress().length == 4)) {
                            return address.getHostAddress();
                        }
                    }
                }
            }
        } catch (SocketException e) {
            return e.toString();
        }
        return null;
    }
    public static String generateSha1(String location) {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            InputStream input = new FileInputStream(location);
            byte[] buffer = new byte[8192];
            int len = input.read(buffer);

            while (len != -1) {
                sha1.update(buffer, 0, len);
                len = input.read(buffer);
            }
            byte[] shasum = sha1.digest();
            char[] charset = "0123456789ABCDEF".toCharArray();
            char[] hexResult = new char[shasum.length * 2];
            for ( int j = 0; j < shasum.length; j++ ) {
                int v = shasum[j] & 0xFF;
                hexResult[j * 2] = charset[v >>> 4];
                hexResult[j * 2 + 1] = charset[v & 0x0F];
            }
            return new String(hexResult).toLowerCase();
        } catch (Exception e) {
            return e.toString();
        }
    }
    private void animateColorChange(final View view, int colorFrom, int colorTo, int duration) {
        Log.d(Tags.MAIN_ACTIVITY, "Changing color from " + Integer.toHexString(colorFrom) + " to " + Integer.toHexString(colorTo));

        ValueAnimator colorAnimation = ValueAnimator.ofObject(new ArgbEvaluator(), colorFrom, colorTo);
        colorAnimation.setDuration(duration);
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

    protected void doStart() {
        logView.setText("");
        Log.d(Tags.MAIN_ACTIVITY, "Starting cuberite");
        checkPermissions();

        serviceIntent = new Intent(this, CuberiteService.class);
        serviceIntent.putExtra("location", preferences.getString("cuberiteLocation", ""));
        serviceIntent.putExtra("binary", PRIVATE_DIR + "/" + preferences.getString("executableName", ""));
        serviceIntent.putExtra("stopcommand", "stop");
        serviceIntent.putExtra("ip", getIpAddress());
        BroadcastReceiver callback = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(Tags.MAIN_ACTIVITY, "Cuberite exited on process");
                LocalBroadcastManager.getInstance(context).unregisterReceiver(this);
                checkState(); // Sets the start button color correctly
            }
        };
        LocalBroadcastManager.getInstance(this).registerReceiver(callback, new IntentFilter("callback"));
        startService(serviceIntent);

        int colorTo = ContextCompat.getColor(this, R.color.warning);
        animateColorChange(mainButton, mainButtonColor, colorTo, 500);
        mainButton.setText(getText(R.string.do_stop_cuberite));
        mainButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                doStop();
            }
        });
    }

    protected void doStop() {
        Log.d(Tags.MAIN_ACTIVITY, "Stopping Cuberite");
        LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent("stop"));
        int colorTo = ContextCompat.getColor(this, R.color.danger);
        animateColorChange(mainButton, mainButtonColor, colorTo, 500);
        mainButton.setText(getText(R.string.do_kill_cuberite));
        mainButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                doKill();
            }
        });
    }

    protected void doKill() {
        LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent("kill"));
        checkState();
    }

    private void showLogLayout() {
        if (Build.VERSION.SDK_INT > 20) {
            // Animate start
            int cx = (int) openLogButton.getX() + openLogButton.getWidth() / 2;
            int cy = (int) openLogButton.getY() + openLogButton.getHeight() / 2;
            float finalRadius = (float) Math.hypot(cx, cy);

            startstopLayout.setVisibility(View.INVISIBLE);
            logLayout.setVisibility(View.VISIBLE);

            Animator anim = ViewAnimationUtils.createCircularReveal(logLayout, cx, cy, 0, finalRadius);
            anim.start();
        } else {
            startstopLayout.setVisibility(View.INVISIBLE);
            logLayout.setVisibility(View.VISIBLE);
        }

        inputLine.setEnabled(true);
        executeLine.setEnabled(true);
    }

    private void hideLogLayout() {
        inputLine.setEnabled(false);
        executeLine.setEnabled(false);
        mainButton.requestFocus();

        if(Build.VERSION.SDK_INT > 20) {
            // Animate start
            int cx = (int) openLogButton.getX() + openLogButton.getWidth() / 2;
            int cy = (int) openLogButton.getY() + openLogButton.getHeight() / 2;
            float initialRadius = (float) Math.hypot(cx, cy);

            Animator anim = ViewAnimationUtils.createCircularReveal(logLayout, cx, cy, initialRadius, 0);
            anim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    super.onAnimationEnd(animation);
                    logLayout.setVisibility(View.INVISIBLE);
                    startstopLayout.setVisibility(View.VISIBLE);
                }
            });
            anim.start();
        } else {
            logLayout.setVisibility(View.INVISIBLE);
            startstopLayout.setVisibility(View.VISIBLE);
        }
        InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Activity.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(MainActivity.this.getCurrentFocus().getWindowToken(), 0);
    }

    private void checkState() {
        checkPermissions();
        final State state = getState();
        if (state == State.RUNNING) {
            int colorTo = ContextCompat.getColor(this, R.color.warning);
            animateColorChange(mainButton, mainButtonColor, colorTo, 500);
            mainButton.setText(getText(R.string.do_stop_cuberite));
            mainButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    doStop();
                }
            });
        } else if (state == State.OK) {
            int colorTo = ContextCompat.getColor(this, R.color.success);
            animateColorChange(mainButton, mainButtonColor, colorTo, 500);
            mainButton.setText(getText(R.string.do_start_cuberite));
            mainButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    doStart();
                }
            });
        } else {
            int colorTo = ContextCompat.getColor(this, R.color.primary);
            animateColorChange(mainButton, mainButtonColor, colorTo, 500);
            mainButton.setText(getText(R.string.do_install_cuberite));
            mainButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent intent = new Intent(context, InstallService.class);
                    intent.setAction("install");
                    intent.putExtra("downloadHost", preferences.getString("downloadHost", ""));
                    intent.putExtra("state", state.toString());
                    intent.putExtra("executableName", preferences.getString("executableName", ""));
                    intent.putExtra("targetDirectory", preferences.getString("cuberiteLocation", ""));
                    intent.putExtra("receiver", new DownloadReceiver(context, new Handler()));

                    LocalBroadcastManager.getInstance(context).registerReceiver(new BroadcastReceiver() {
                        @Override
                        public void onReceive(Context context, Intent intent) {
                            LocalBroadcastManager.getInstance(context).unregisterReceiver(this);
                            String error = intent.getStringExtra("error");
                            if(error != null) {
                                Snackbar.make(findViewById(R.id.activity_main), getString(R.string.status_download_error) + error, Snackbar.LENGTH_LONG).show();
                            }
                            checkState();
                        }
                    }, new IntentFilter("InstallService.callback"));
                    startService(intent);
                }
            });
        }
    }

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(getString(R.string.status_permissions_needed));
            builder.setMessage(R.string.message_externalstorage_permission);
            builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    Log.d(Tags.MAIN_ACTIVITY, "Requesting permissions for external storage");
                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION);
                }
            });
            builder.create().show();
        } else if(preferences.getString("cuberiteLocation", "").startsWith(PRIVATE_DIR)){
            Log.d(Tags.MAIN_ACTIVITY, "Cuberite has permissions for external storage, but is still in the private dir");
        } else if(preferences.getString("cuberiteLocation", null) == null) {
            preferences.edit().putString("cuberiteLocation", "/sdcard/cuberite-server").apply();
        }
    }

    private SharedPreferences preferences;
    static String PACKAGE_NAME;
    static String PRIVATE_DIR;
    static String PUBLIC_DIR;
    private Context context;
    private Button mainButton;
    private int mainButtonColor;
    private TextView statusTextView;
    private RelativeLayout startstopLayout;
    private RelativeLayout logLayout;
    private TextView logView;
    private Intent serviceIntent;
    private EditText inputLine;
    private Button executeLine;
    private Button openLogButton;
    private Button openSettingsButton;
    final int REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        context = this;
        mainButton = (Button) findViewById(R.id.mainButton);
        mainButtonColor = ContextCompat.getColor(this, R.color.main);
        statusTextView = (TextView) findViewById(R.id.statusTextView);
        startstopLayout = (RelativeLayout) findViewById(R.id.startStopLayout);
        logLayout = (RelativeLayout) findViewById(R.id.logLayout);
        logView = (TextView) findViewById(R.id.logView);
        logView.setMovementMethod(new ScrollingMovementMethod());

        inputLine = (EditText) findViewById(R.id.inputLine);
        // Rename enter key :P
        inputLine.setImeActionLabel(getString(R.string.do_execute_line), KeyEvent.KEYCODE_ENTER);
        inputLine.setOnEditorActionListener(new EditText.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                String line = inputLine.getText().toString();
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    if(!line.isEmpty() && isServiceRunning(CuberiteService.class)) {
                        sendExecuteLine(line);
                        inputLine.setText("");
                    }
                    // return true makes sure the keyboard doesn't close
                    return true;
                }
                return false;
            }
        });

        executeLine = (Button) findViewById(R.id.executeLine);
        executeLine.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String line = inputLine.getText().toString();
                if(!line.isEmpty() && isServiceRunning(CuberiteService.class)) {
                    sendExecuteLine(line);
                    inputLine.setText("");
                }
            }
        });

        openLogButton = (Button) findViewById(R.id.openLogButton);
        openLogButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showLogLayout();
            }
        });

        openSettingsButton = (Button) findViewById(R.id.openSettingsButton);
        openSettingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(context, SettingsActivity.class);
                startActivity(intent);
            }
        });

        // PACKAGE_NAME: org.cuberite.android
        // PRIVATE_DIR: /data/data/org.cuberite.android/files
        // On most devices
        PACKAGE_NAME = getApplicationContext().getPackageName();
        PRIVATE_DIR = getFilesDir().getAbsolutePath();
        PUBLIC_DIR = Environment.getExternalStorageDirectory().getAbsolutePath();

        // Initialize variables
        preferences = getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE);

        SettingsActivity.initializeSettings(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(this).registerReceiver(fullLog, new IntentFilter("fullLog"));
        LocalBroadcastManager.getInstance(this).registerReceiver(addLog, new IntentFilter("addLog"));
        LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent("getLog"));
        checkState();
    }

    private BroadcastReceiver fullLog = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Extract data included in the Intent
            String message = intent.getStringExtra("message");
            final ScrollView scrollView = (ScrollView) logView.getParent();
            // Only scroll down if we are already at bottom. getScrollY is how much we have scrolled, whereas getHeight is the complete height.
            final int scrollY = scrollView.getScrollY();
            final int bottomLocation = logLayout.getHeight() - scrollView.getHeight();

            logView.setText(Html.fromHtml(message));
            scrollView.post(new Runnable() {
                @Override
                public void run() {
                    if(scrollY > bottomLocation)
                        scrollView.fullScroll(ScrollView.FOCUS_DOWN);
                }
            });
        }
    };

    private BroadcastReceiver addLog = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String message = intent.getStringExtra("message");
            final ScrollView scrollView = (ScrollView) logView.getParent();
            final int scrollY = scrollView.getScrollY();
            final int bottomLocation = logLayout.getHeight() - scrollView.getHeight();
            logView.append(Html.fromHtml(message));
            scrollView.post(new Runnable() {
                @Override
                public void run() {
                    if(scrollY > bottomLocation)
                        scrollView.fullScroll(ScrollView.FOCUS_DOWN);
                }
            });
        }
    };

    private void sendExecuteLine(String line) {
        Log.d(Tags.MAIN_ACTIVITY, "Executing " + line);
        Intent intent = new Intent("executeLine");
        intent.putExtra("message", line);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    @Override
    protected void onPause() {
        // Unregister since the activity is not visible
        LocalBroadcastManager.getInstance(this).unregisterReceiver(fullLog);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(addLog);
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.i(Tags.MAIN_ACTIVITY, "Got permissions, using public directory");
                    preferences.edit().putString("cuberiteLocation", PUBLIC_DIR + "/cuberite-server").apply();
                } else {
                    Log.i(Tags.MAIN_ACTIVITY, "Permissions denied, boo, using private directory");
                    preferences.edit().putString("cuberiteLocation", PRIVATE_DIR + "/cuberite-server").apply();
                }
                checkState();
            }
        }
    }

    @Override
    public void onBackPressed() {
        // super.onBackPressed(); Back button should only act like it normally does if the log isn't visible
        if(logLayout.getVisibility() == View.VISIBLE) {
            hideLogLayout();
        } else
            super.onBackPressed();
    }
}