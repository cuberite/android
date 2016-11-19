package org.cuberite.android;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.PowerManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.support.design.widget.Snackbar;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends AppCompatActivity {
    // Helper functions
    public void unzip(final String file, String _targetLocation, final InstallState installState, String message) {
        Log.d(TAG, "Unzipping " + file + " to " + _targetLocation);
        Log.v(TAG, "Acquiring wakeLock");
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        final PowerManager.WakeLock wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getClass().getName());
        wakeLock.acquire();

        ProgressDialog progress = new ProgressDialog(context);
        progress.setMax(100);
        progress.setTitle(getString(R.string.status_downloading_cuberite));
        progress.setMessage(message);
        progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progress.setCancelable(false);
        progress.setCanceledOnTouchOutside(false);
        progress.show();

        UnzipThread unzipThread = new UnzipThread(file, _targetLocation, progress, wakeLock, new UnzipThread.OnThreadEndListener() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Log.v(TAG, "Finished unzipping");
                        if (installState == InstallState.DOWNLOAD_BOTH) {
                            doInstall(InstallState.DOWNLOAD_BINARY);
                        } else {
                            doWeNeedToInstall();
                        }
                        Log.v(TAG, "Deleting file");
                        File zipFile = new File(file);
                        if(!zipFile.delete())
                            Log.w(TAG, "Couldn't delete file: " + file);
                    }
                });
            }
        });
        unzipThread.start();
    }
    private InstallState getInstallState() {
        InstallState installState = null;
        boolean hasBinary = false;
        boolean hasServer = false;
        if (new File(PRIVATE_DIR + "/" + preferences.getString("executableName", "")).exists())
            hasBinary = true;
        if (new File(preferences.getString("cuberiteLocation", "")).exists())
            hasServer = true;

        if (hasBinary && hasServer)
            installState = InstallState.OK;
        if (!hasServer && !hasBinary)
            installState = InstallState.DOWNLOAD_BOTH;
        if (!hasServer)
            installState = InstallState.DOWNLOAD_SERVER;
        if (!hasBinary)
            installState = InstallState.DOWNLOAD_BINARY;

        Log.v(TAG, "Getting InstallState: " + installState.toString());

        return installState;
    }
    private String getPreferredABI() {
        String abi;
        if(Build.VERSION.SDK_INT > 20)
            abi = Build.SUPPORTED_ABIS[0];
        else
            abi = Build.CPU_ABI;

        Log.v(TAG, "Getting preferred ABI: " + abi);

        return abi;
    }

    private void animateColorChange(final View view, int colorFrom, int colorTo, int duration) {
        Log.v(TAG, "Changing color from " + Integer.toHexString(colorFrom) + " to " + Integer.toHexString(colorTo));

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

    protected void doInstall(final InstallState installState) {
        Log.v(TAG, "Installing " + installState.toString());
        final String downloadedServerFileName = "server.zip";
        final String downloadedBinaryFileName = preferences.getString("executableName", "") + ".zip";
        final ProgressDialog progress = new ProgressDialog(context);
        progress.setMax(100);
        progress.setTitle(getString(R.string.status_downloading_cuberite));
        progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);

        // Download Task
        class DownloadTask extends AsyncTask<String, Integer, String> {
            private Context context;
            private PowerManager.WakeLock wakeLock;

            DownloadTask(Context context) {
                this.context = context;
            }

            @Override
            protected String doInBackground(String... urlAndTarget) {
                InputStream inputStream = null;
                OutputStream outputStream = null;
                HttpURLConnection connection = null;
                try {
                    URL url = new URL(urlAndTarget[0]);
                    String targetLocation = urlAndTarget[1];
                    connection = (HttpURLConnection) url.openConnection();
                    connection.connect();
                    if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                        String error = "Server returned HTTP " + connection.getResponseCode() + " " + connection.getResponseMessage();
                        Log.e(TAG, error);
                        return error;

                    }

                    int length = connection.getContentLength();
                    inputStream = connection.getInputStream();
                    outputStream = new FileOutputStream(PRIVATE_DIR + "/" + targetLocation);

                    byte data[] = new byte[4096];
                    long total = 0;
                    int count;
                    while ((count = inputStream.read(data)) != -1) {
                        if (isCancelled()) {
                            inputStream.close();
                            return null;
                        }
                        total += count;
                        if (length > 0) // only if total length is known
                            publishProgress((int) total, length);
                        outputStream.write(data, 0, count);
                    }
                    Log.v(TAG, "Finished downloading");
                } catch (Exception e) {
                    return e.toString();
                } finally {
                    try {
                        if (outputStream != null)
                            outputStream.close();
                        if (inputStream != null)
                            inputStream.close();
                    } catch (IOException ignored) {
                    }

                    if (connection != null)
                        connection.disconnect();
                }
                return null;
            }

            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                Log.v(TAG, "Acquiring wakeLock");
                PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getClass().getName());
                wakeLock.acquire();

                progress.show();
            }

            @Override
            protected void onProgressUpdate(Integer... currentProgress) {
                super.onProgressUpdate(currentProgress);
                progress.setIndeterminate(false);
                progress.setMax(currentProgress[1]); // second argument is file length
                progress.setProgress(currentProgress[0]); // first argument is already downloaded size
            }

            @Override
            protected void onPostExecute(String result) {
                Log.v(TAG, "Releasing wakeLock");
                wakeLock.release();
                progress.dismiss();

                if (result != null) {
                    Snackbar.make(findViewById(R.id.activity_main), getString(R.string.status_download_error) + result, Snackbar.LENGTH_LONG).show();
                    cancel(true);
                }
            }
        }

        if (installState == InstallState.DOWNLOAD_SERVER || installState == InstallState.DOWNLOAD_BOTH) {
            progress.setMessage(getString(R.string.status_downloading_server));
            final DownloadTask downloadTask = new DownloadTask(context);
            downloadTask.execute(preferences.getString("downloadServerLink", ""), downloadedServerFileName);
            progress.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    downloadTask.cancel(true);
                    downloadTask.wakeLock.release(); // onPostExecute() isn't called when cancelling
                }
            });

            progress.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    if (downloadTask.isCancelled()) {
                        File downloadedFile = new File(PRIVATE_DIR + "/" + downloadedServerFileName);
                        boolean deleted = downloadedFile.delete();
                        if (!deleted)
                            Snackbar.make(findViewById(R.id.activity_main), getString(R.string.status_delete_file_error), Snackbar.LENGTH_SHORT).show();
                    } else {
                        // Unzipping file
                        unzip(PRIVATE_DIR + "/" + downloadedServerFileName, preferences.getString("cuberiteLocation", ""), installState, getString(R.string.status_unpacking_server));
                    }
                    doWeNeedToInstall();
                }
            });
        }

        if (installState == InstallState.DOWNLOAD_BINARY) {
            progress.setMessage(getString(R.string.status_downloading_binary));
            final DownloadTask downloadTask = new DownloadTask(context);
            downloadTask.execute(preferences.getString("downloadBinaryLink", ""), downloadedBinaryFileName);
            progress.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    downloadTask.cancel(true);
                    downloadTask.wakeLock.release(); // onPostExecute() isn't called when cancelling
                }
            });
            progress.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    if (downloadTask.isCancelled()) {
                        File downloadedFile = new File(PRIVATE_DIR + "/" + downloadedBinaryFileName);
                        boolean deleted = downloadedFile.delete();
                        if (!deleted)
                            Snackbar.make(findViewById(R.id.activity_main), getString(R.string.status_delete_file_error), Snackbar.LENGTH_SHORT).show();
                    } else {
                        unzip(PRIVATE_DIR + "/" + downloadedBinaryFileName, PRIVATE_DIR, installState, getString(R.string.status_unpacking_binary));
                    }
                    doWeNeedToInstall();
                }
            });
        }
    }

    protected void doStart() {
        Log.v(TAG, "Starting cuberite");
        checkPermissions();
        isCuberiteRunning = true;

        cuberiteTask = new CuberiteTask(logView, preferences.getString("cuberiteLocation", ""), PRIVATE_DIR + "/" + preferences.getString("executableName", ""), new CuberiteTask.OnEndListener() {
            @Override
            public void run() {
                Log.v(TAG, "Cuberite exited");
                isCuberiteRunning = false;
                doWeNeedToInstall(); // Sets the start button color correctly
            }
        });

        int colorTo = ContextCompat.getColor(context, R.color.warning);
        animateColorChange(mainButton, mainButtonColor, colorTo, 500);
        mainButton.setText(getText(R.string.do_stop_cuberite));
        mainButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                doStop();
            }
        });
        logView.setText("");
        cuberiteTask.execute();
    }

    protected void doStop() {
        Log.v(TAG, "Stopping Cuberite");
        cuberiteTask.stop();
        int colorTo = ContextCompat.getColor(context, R.color.danger);
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
        if(cuberiteTask != null) {
            Log.v(TAG, "Killing Cuberite");
            cuberiteTask.kill();
        } else {
            Log.w(TAG, "cuberiteTask is not null, thus not killing");
        }
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
        InputMethodManager inputMethodManager = (InputMethodManager) context.getSystemService(Activity.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(MainActivity.this.getCurrentFocus().getWindowToken(), 0);
    }

    private void doWeNeedToInstall() {
        checkPermissions();
        final InstallState installState = getInstallState();
        if (installState != InstallState.OK) {
            int colorTo = ContextCompat.getColor(context, R.color.primary);
            animateColorChange(mainButton, mainButtonColor, colorTo, 500);
            mainButton.setText(getText(R.string.do_install_cuberite));
            mainButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    doInstall(installState);
                }
            });
        } else {
            int colorTo = ContextCompat.getColor(context, R.color.success);
            animateColorChange(mainButton, mainButtonColor, colorTo, 500);
            mainButton.setText(getText(R.string.do_start_cuberite));
            mainButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    doStart();
                }
            });
        }
    }

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle(getString(R.string.status_permissions_needed));
            builder.setMessage(R.string.message_externalstorage_permission);
            builder.setPositiveButton(R.string.do_ok, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    Log.v(TAG, "Requesting permissions for external storage");
                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION);
                }
            });
            builder.create().show();
        } else if(preferences.getString("cuberiteLocation", "").startsWith(PRIVATE_DIR)){
            Log.v(TAG, "Cuberite has permissions for external storage, but is still in the private dir");
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
    private CuberiteTask cuberiteTask;
    private EditText inputLine;
    private Button executeLine;
    private Button openLogButton;
    private boolean isCuberiteRunning = false;
    private boolean hasPressedBackBefore = false;
    final int REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION = 1;
    static final String TAG = "Cuberite";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        context = this;
        mainButton = (Button) findViewById(R.id.mainButton);
        mainButtonColor = ContextCompat.getColor(context, R.color.main);
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
                    if(!line.isEmpty() && isCuberiteRunning) {
                        Log.v(TAG, "Executing " + line);
                        cuberiteTask.executeLine(line);
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
                if(!line.isEmpty() && isCuberiteRunning) {
                    Log.v(TAG, "Executing " + line);
                    cuberiteTask.executeLine(line);
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

        // PACKAGE_NAME: org.cuberite.android
        // PRIVATE_DIR: /data/data/org.cuberite.android/files
        // On most devices
        PACKAGE_NAME = getApplicationContext().getPackageName();
        PRIVATE_DIR = getFilesDir().getAbsolutePath();
        PUBLIC_DIR = Environment.getExternalStorageDirectory().getAbsolutePath();

        // Initialize variables
        preferences = getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE);

        // Only runs on first run.
        // Sets initial values for settings
        SharedPreferences.Editor editor = preferences.edit();
        if (!preferences.contains("cuberiteLocation")) {
            checkPermissions();
            editor.putString("cuberiteLocation", PUBLIC_DIR + "/cuberite-server");
        }
        if (!preferences.contains("executableName"))
            editor.putString("executableName", "Cuberite");
        if (!preferences.contains("downloadServerLink"))
            editor.putString("downloadServerLink", "https://ultramc.org/cuberite-server.zip");
        if (!preferences.contains("downloadBinaryLink"))
            editor.putString("downloadBinaryLink", "https://ultramc.org/cuberite-binary-" + getPreferredABI() + ".zip");
        editor.apply();

        doWeNeedToInstall();

        // Print some useful information
        String infos = "Running on Android " + Build.VERSION.RELEASE + " (API Level " + Build.VERSION.SDK_INT + ")\n" +
                "Using ABI " + getPreferredABI() + "\n" +
                "Private directory: " + PRIVATE_DIR + "\n" +
                "Public directory: " + PUBLIC_DIR + "\n" +
                "Storage location: " + preferences.getString("cuberiteLocation", "") + "\n" +
                "Will download server from: " + preferences.getString("downloadServerLink", "") + "\n" +
                "Will download binary from: " + preferences.getString("downloadBinaryLink", "");
        statusTextView.append(infos);
        Log.d(TAG, infos);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.v(TAG, "Got permissions, using public directory");
                    preferences.edit().putString("cuberiteLocation", PUBLIC_DIR + "/cuberite-server").apply();
                } else {
                    Log.v(TAG, "Permissions denied, boo, using private directory");
                    preferences.edit().putString("cuberiteLocation", PRIVATE_DIR + "/cuberite-server").apply();
                }
                doWeNeedToInstall();
                return;
            }
        }
    }

    @Override
    public void onBackPressed() {
        // super.onBackPressed(); Back button should not act like it normally does

        // hasPressedBackBefore is by default false, becomes true when this method is called the first time and becomes false when the method is called the second time within 5 seconds
        hasPressedBackBefore = !hasPressedBackBefore;
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                hasPressedBackBefore = false;
            }
        }, 5000);

        if(logLayout.getVisibility() == View.VISIBLE) {
            hideLogLayout();
            hasPressedBackBefore = false;
        } else if (isCuberiteRunning) {
            if(!hasPressedBackBefore)
                doStop();
            else
                Snackbar.make(findViewById(R.id.activity_main), getString(R.string.press_back_twice_started), Snackbar.LENGTH_LONG).show();
        } else if (!isCuberiteRunning) {
            if(!hasPressedBackBefore)
                finish();
            else
                Snackbar.make(findViewById(R.id.activity_main), getString(R.string.press_back_twice_stopped), Snackbar.LENGTH_LONG).show();
        }
    }

}