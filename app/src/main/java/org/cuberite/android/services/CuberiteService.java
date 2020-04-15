package org.cuberite.android.services;

import android.app.IntentService;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.content.SharedPreferences;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.util.Log;

import org.cuberite.android.MainActivity;
import org.cuberite.android.R;
import org.cuberite.android.helpers.CuberiteHelper;

import java.io.File;
import java.io.OutputStream;
import java.util.NoSuchElementException;
import java.util.Scanner;

import static org.cuberite.android.MainActivity.PACKAGE_NAME;
import static org.cuberite.android.MainActivity.PRIVATE_DIR;

public class CuberiteService extends IntentService {
    // Logging tag
    private static String LOG = "Cuberite/CuberiteService";

    public CuberiteService() {
        super("CuberiteService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.d(LOG, "Starting service...");

        CuberiteHelper.resetConsoleOutput();

        final SharedPreferences preferences = getApplicationContext().getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE);
        final String ip = CuberiteHelper.getIpAddress(getApplicationContext());
        final String binary = PRIVATE_DIR + "/" + preferences.getString("executableName", "Cuberite");
        final String location = preferences.getString("cuberiteLocation", "");

        final String CHANNEL_ID = "cuberiteservice";
        int icon = R.drawable.ic_cuberite;
        CharSequence text = getText(R.string.notification_cuberite_running);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), 0);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString(R.string.app_name);
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_HIGH);
            channel.setSound(null, null);
            channel.setVibrationPattern(new long[]{ 0 });
            channel.enableVibration(true);

            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.createNotificationChannel(channel);
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            icon = R.mipmap.ic_launcher;
        }

        final NotificationCompat.Builder notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(icon)
                .setTicker(text)
                .setContentTitle(text)
                .setContentText(ip)
                .setContentIntent(contentIntent)
                .setOnlyAlertOnce(true);

        startForeground(1, notification.build());

        try {
            // Make sure we can execute the binary
            new File(binary).setExecutable(true, true);
            // Initiate ProcessBuilder with the command at the given location
            ProcessBuilder processBuilder = new ProcessBuilder(binary, "--no-output-buffering");
            processBuilder.directory(new File(location).getAbsoluteFile());
            processBuilder.redirectErrorStream(true);
            CuberiteHelper.addConsoleOutput(getApplicationContext(), "Info: Cuberite is starting...");
            Log.d(LOG, "Starting process...");
            final Process process = processBuilder.start();

            // Open STDIN for the inputLine
            final OutputStream cuberiteSTDIN = process.getOutputStream();

            // Update notification IP if network changes
            BroadcastReceiver updateIp = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    final String action = intent.getAction();
                    if (WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(action)) {
                        NetworkInfo info = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);

                        if (NetworkInfo.State.CONNECTED.equals(info.getState())
                                || NetworkInfo.State.DISCONNECTED.equals(info.getState())) {
                            Log.d(LOG, "Updating notification IP due to network change");
                            NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                            final String ip = CuberiteHelper.getIpAddress(context);
                            notification.setContentText(ip);
                            notificationManager.notify(1, notification.build());
                        }
                    }
                }
            };

            IntentFilter intentFilter = new IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION);
            registerReceiver(updateIp, intentFilter);

            // Communication with the activity
            BroadcastReceiver stop = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    try {
                        cuberiteSTDIN.write(("stop\n").getBytes());
                        cuberiteSTDIN.flush();
                    } catch (Exception e) {
                        Log.e(LOG, "An error occurred when writing stop to the STDIN", e);
                    }
                }
            };
            BroadcastReceiver kill = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    process.destroy();
                }
            };
            BroadcastReceiver executeCommand = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String command = intent.getStringExtra("message");
                    try {
                        cuberiteSTDIN.write((command + "\n").getBytes());
                        cuberiteSTDIN.flush();
                    } catch (Exception e) {
                        Log.e(LOG, "An error occurred when writing " + command + " to the STDIN", e);
                    }
                }
            };

            LocalBroadcastManager.getInstance(this).registerReceiver(stop, new IntentFilter("stop"));
            LocalBroadcastManager.getInstance(this).registerReceiver(kill, new IntentFilter("kill"));
            LocalBroadcastManager.getInstance(this).registerReceiver(executeCommand, new IntentFilter("executeCommand"));

            // Log to console
            Log.d(LOG, "Starting logging...");
            final long logTimeStart = System.currentTimeMillis();

            Scanner processScanner = new Scanner(process.getInputStream());
            String line;
            try {
                while ((line = processScanner.nextLine()) != null) {
                    Log.i(LOG, line);
                    CuberiteHelper.addConsoleOutput(getApplicationContext(), line);
                }
            } catch (NoSuchElementException e) {
                // Do nothing. Workaround for issues in older Android versions.
            }

            // Logic waits here until Cuberite has stopped. Everything after that is cleanup for the next run

            processScanner.close();

            final long logTimeEnd = System.currentTimeMillis();
            if ((logTimeEnd - logTimeStart) < 100) {
                LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent("showStartupError"));
            }

            LocalBroadcastManager.getInstance(this).unregisterReceiver(updateIp);

            LocalBroadcastManager.getInstance(this).unregisterReceiver(stop);
            LocalBroadcastManager.getInstance(this).unregisterReceiver(kill);
            LocalBroadcastManager.getInstance(this).unregisterReceiver(executeCommand);
            cuberiteSTDIN.close();
        } catch (Exception e) {
            Log.e(LOG, "An error occurred when starting Cuberite", e);

            // Send error to user
            LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent("showStartupError"));
        }

        stopSelf();
        LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent("CuberiteService.callback"));
    }
}
