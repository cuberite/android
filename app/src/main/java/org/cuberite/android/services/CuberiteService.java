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
import java.io.IOException;
import java.io.OutputStream;
import java.util.NoSuchElementException;
import java.util.Scanner;

public class CuberiteService extends IntentService {
    // Logging tag
    private static final String LOG = "Cuberite/ServerService";

    private NotificationCompat.Builder notification;
    private Process process;
    private OutputStream cuberiteSTDIN;

    public CuberiteService() {
        super("CuberiteService");
    }


    // Notification-related methods

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            final String channelId = "cuberiteservice";
            final CharSequence name = getString(R.string.app_name);

            final NotificationChannel channel = new NotificationChannel(
                    channelId,
                    name,
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setSound(null, null);
            channel.setVibrationPattern(new long[]{0});
            channel.enableVibration(true);

            final NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void createNotification() {
        final String channelId = "cuberiteservice";
        final int icon = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) ? R.drawable.ic_notification : R.mipmap.ic_launcher;
        final CharSequence text = getText(R.string.notification_cuberite_running);
        final String ip = CuberiteHelper.getIpAddress(getApplicationContext());

        final int flags = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) ? PendingIntent.FLAG_IMMUTABLE : 0;
        final Intent notificationIntent = new Intent(this, MainActivity.class);
        final PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, flags);

        createNotificationChannel();

        notification = new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(icon)
                .setTicker(text)
                .setContentTitle(text)
                .setContentText(ip)
                .setContentIntent(contentIntent)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE);

        startForeground(1, notification.build());
    }


    // Process-related methods

    private void startProcess() throws IOException {
        final SharedPreferences preferences = getApplicationContext().getSharedPreferences(this.getPackageName(), MODE_PRIVATE);
        final String executableName = CuberiteHelper.getExecutableName();
        final String location = preferences.getString("cuberiteLocation", "");

        // Clear previous output
        CuberiteHelper.resetConsoleOutput();

        // Make sure we can execute the binary
        new File(this.getFilesDir(), executableName).setExecutable(true, true);

        // Initiate ProcessBuilder with the command at the given location
        ProcessBuilder processBuilder = new ProcessBuilder(this.getFilesDir() + "/" + executableName, "--no-output-buffering");
        processBuilder.directory(new File(location));
        processBuilder.redirectErrorStream(true);

        CuberiteHelper.addConsoleOutput(getApplicationContext(), "Info: Cuberite is starting...");
        Log.d(LOG, "Starting process...");
        process = processBuilder.start();
        cuberiteSTDIN = process.getOutputStream();
    }

    private void updateOutput() {
        Log.d(LOG, "Starting logging...");

        final Scanner processScanner = new Scanner(process.getInputStream());
        String line;

        try {
            while ((line = processScanner.nextLine()) != null) {
                Log.i(LOG, line);
                CuberiteHelper.addConsoleOutput(getApplicationContext(), line);
            }
        } catch (NoSuchElementException e) {
            // Do nothing. Workaround for issues in older Android versions.
        }

        processScanner.close();
    }


    // Broadcast receivers

    private final BroadcastReceiver executeCommand = new BroadcastReceiver() {
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

    private final BroadcastReceiver updateIp = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(action)) {
                final NetworkInfo info = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);

                if (NetworkInfo.State.CONNECTED.equals(info.getState())
                        || NetworkInfo.State.DISCONNECTED.equals(info.getState())) {
                    Log.d(LOG, "Updating notification IP due to network change");
                    final String ip = CuberiteHelper.getIpAddress(context);
                    notification.setContentText(ip);

                    final NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                    notificationManager.notify(1, notification.build());
                }
            }
        }
    };

    private final BroadcastReceiver stop = new BroadcastReceiver() {
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

    private final BroadcastReceiver kill = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            process.destroy();
        }
    };


    // Service startup and cleanup

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.d(LOG, "Starting service...");

        try {
            // Create and show notification about Cuberite running
            createNotification();

            // Start the Cuberite process
            startProcess();

            // Update notification IP if network changes
            IntentFilter intentFilter = new IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION);
            registerReceiver(updateIp, intentFilter);

            // Communication with the activity
            LocalBroadcastManager.getInstance(this).registerReceiver(executeCommand, new IntentFilter("executeCommand"));
            LocalBroadcastManager.getInstance(this).registerReceiver(stop, new IntentFilter("stop"));
            LocalBroadcastManager.getInstance(this).registerReceiver(kill, new IntentFilter("kill"));

            // Log to console
            final long logTimeStart = System.currentTimeMillis();
            updateOutput();

            // Logic waits here until Cuberite has stopped. Everything after that is cleanup for the next run

            final long logTimeEnd = System.currentTimeMillis();
            if ((logTimeEnd - logTimeStart) < 100) {
                LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent("showStartupError"));
            }

            // Shutdown
            unregisterReceiver(updateIp);

            LocalBroadcastManager.getInstance(this).unregisterReceiver(executeCommand);
            LocalBroadcastManager.getInstance(this).unregisterReceiver(stop);
            LocalBroadcastManager.getInstance(this).unregisterReceiver(kill);

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
