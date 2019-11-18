package org.cuberite.android;

import android.app.IntentService;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.core.app.NotificationCompat;

import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.util.Log;

import java.io.File;
import java.io.OutputStream;
import java.util.NoSuchElementException;
import java.util.Scanner;

public class CuberiteService extends IntentService {

    private String log = "";

    public CuberiteService() {
        super("CuberiteService");
    }

    private void addLog(String string) {
        String logLine = "";
        String[] text = string.split("\\n");
        for (String line : text) {
            String curText = TextUtils.htmlEncode(line);
            if (curText.toLowerCase().startsWith("log: ")) {
                curText = curText.replaceFirst("(?i)log: ", "");
            } else if (curText.toLowerCase().startsWith("info:")) {
                curText = curText.replaceFirst("(?i)info: ", "");
                curText = "<font color= \"#FFA500\">" + curText + "</font>";
            } else if (curText.toLowerCase().startsWith("warning: ")) {
                curText = curText.replaceFirst("(?i)warning: ", "");
                curText = "<font color= \"#FF0000\">" + curText + "</font>";
            } else if (curText.toLowerCase().startsWith("error: ")) {
                curText = curText.replaceFirst("(?i)error: ", "");
                curText = "<font color=\"#8B0000\">" + curText + "</font>";
            }

            if (log.isEmpty()) {
                logLine = curText;
            } else {
                logLine += "<br>" + curText;
            }
        }
        log += logLine;
        Intent intent = new Intent("addLog");
        intent.putExtra("message", logLine);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void showStartupError() {
        Intent intent = new Intent("showStartupError");
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private String getIpAddress() {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        int ip = wifiInfo.getIpAddress();

        if (ip == 0) {
            return "127.0.0.1";
        } else {
            return Formatter.formatIpAddress(ip);
        }
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.d(Tags.SERVICE, "Starting service...");
        final String stopCommand = intent.getStringExtra("stopcommand");
        final String ip = getIpAddress();
        final String binary = intent.getStringExtra("binary");
        final String location = intent.getStringExtra("location");
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
            addLog("Info: Cuberite is starting...");
            Log.d(Tags.SERVICE, "Starting process...");
            final Process process = processBuilder.start();

            // Open STDIN for the inputLine
            final OutputStream cuberiteSTDIN = process.getOutputStream();

            // Logging thread. This thread will check cuberite's stdout (and stderr), color it and append it to the logView. This thread will wait only for next lines coming. if stdout is closed, this thread will exit
            new Thread(new Runnable() {
                @Override
                public void run() {
                    Log.d(Tags.SERVICE, "Starting logging thread...");
                    final long logTimeStart = System.currentTimeMillis();

                    Scanner processScanner = new Scanner(process.getInputStream());
                    String line;
                    try {
                        while ((line = processScanner.nextLine()) != null) {
                            Log.i(Tags.PROCESS, line);
                            addLog(line);
                        }
                    } catch (NoSuchElementException e) {
                        // Do nothing. Workaround for issues in older Android versions.
                    }
                    processScanner.close();

                    final long logTimeEnd = System.currentTimeMillis();
                    if ((logTimeEnd - logTimeStart) < 500) {
                        showStartupError();
                    }
                }
            }).start();

            // Update notification IP if network changes
            BroadcastReceiver updateIp = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    final String action = intent.getAction();
                    if (action.equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
                        NetworkInfo info = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);

                        if (info.getState() == NetworkInfo.State.CONNECTED ||
                        info.getState() == NetworkInfo.State.DISCONNECTED) {
                            Log.d(Tags.SERVICE, "Updating notification IP due to network change");
                            NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                            final String ip = getIpAddress();
                            notification.setContentText(ip);
                            notificationManager.notify(1, notification.build());
                        }
                    }
                }
            };

            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
            registerReceiver(updateIp, intentFilter);

            // Communication with the activity
            BroadcastReceiver getLog = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    Intent sendIntent = new Intent("fullLog");
                    sendIntent.putExtra("message", log);
                    LocalBroadcastManager.getInstance(context).sendBroadcast(sendIntent);
                }
            };
            BroadcastReceiver stop = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    try {
                        cuberiteSTDIN.write((stopCommand + "\n").getBytes());
                        cuberiteSTDIN.flush();
                    } catch (Exception e) {
                        Log.e(Tags.SERVICE, "An error occurred when writing " + stopCommand + " to the STDIN", e);
                    }
                }
            };
            BroadcastReceiver kill = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    process.destroy();
                }
            };
            BroadcastReceiver executeLine = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String line = intent.getStringExtra("message");
                    try {
                        cuberiteSTDIN.write((line + "\n").getBytes());
                        cuberiteSTDIN.flush();
                    } catch (Exception e) {
                        Log.e(Tags.SERVICE, "An error occurred when writing " + line + " to the STDIN", e);}
                }
            };

            LocalBroadcastManager.getInstance(this).registerReceiver(getLog, new IntentFilter("getLog"));
            LocalBroadcastManager.getInstance(this).registerReceiver(stop, new IntentFilter("stop"));
            LocalBroadcastManager.getInstance(this).registerReceiver(kill, new IntentFilter("kill"));
            LocalBroadcastManager.getInstance(this).registerReceiver(executeLine, new IntentFilter("executeLine"));

            // Wait for the process to end. Logic waits here until cuberite has stopped. Everything after that is cleanup for the next run
            process.waitFor();

            unregisterReceiver(updateIp);

            LocalBroadcastManager.getInstance(this).unregisterReceiver(getLog);
            LocalBroadcastManager.getInstance(this).unregisterReceiver(stop);
            LocalBroadcastManager.getInstance(this).unregisterReceiver(kill);
            LocalBroadcastManager.getInstance(this).unregisterReceiver(executeLine);
            cuberiteSTDIN.close();
            LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent("callback"));
            stopSelf();
        } catch (Exception e) {
            Log.wtf(Tags.SERVICE, "An error occurred when starting Cuberite", e);
            showStartupError();
            LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent("callback"));
        }
    }
}
