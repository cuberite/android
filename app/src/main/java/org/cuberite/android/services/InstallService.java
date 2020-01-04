package org.cuberite.android.services;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.support.v4.os.ResultReceiver;
import androidx.appcompat.app.AlertDialog;
import android.util.Log;

import org.cuberite.android.helpers.ProgressReceiver;
import org.cuberite.android.R;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Scanner;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.cuberite.android.MainActivity.PRIVATE_DIR;
import static org.cuberite.android.fragments.SettingsFragment.PICK_FILE_BINARY;

public class InstallService extends IntentService {
    // Logging tag
    private static String LOG = "Cuberite/InstallService";

    private ResultReceiver receiver;

    public InstallService() {
        super("InstallService");
    }

    public static String getPreferredABI() {
        String abi;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            abi = Build.SUPPORTED_ABIS[0];
        } else {
            abi = Build.CPU_ABI;
        }

        Log.d(LOG, "Getting preferred ABI: " + abi);

        return abi;
    }

    private String generateSha1(String location) {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            InputStream input = new FileInputStream(location);
            byte[] buffer = new byte[8192];
            int len = input.read(buffer);

            while (len != -1) {
                sha1.update(buffer, 0, len);
                len = input.read(buffer);
            }
            byte[] shaSum = sha1.digest();
            char[] charset = "0123456789ABCDEF".toCharArray();
            char[] hexResult = new char[shaSum.length * 2];
            for ( int j = 0; j < shaSum.length; j++ ) {
                int v = shaSum[j] & 0xFF;
                hexResult[j * 2] = charset[v >>> 4];
                hexResult[j * 2 + 1] = charset[v & 0x0F];
            }
            return new String(hexResult).toLowerCase();
        } catch (Exception e) {
            return e.toString();
        }
    }

    private String downloadVerify(String url, String target) {
        String zipFileError = downloadNoVerify(url, target);
        if (zipFileError != null) {
            return zipFileError;
        }

        // Verifying file
        String zipSha = generateSha1(target);
        String shaError = downloadNoVerify(url + ".sha1", target + ".sha1");
        if (shaError != null) {
            return shaError;
        }

        try {
            String shaFile = new Scanner(new File(target + ".sha1")).useDelimiter("\\Z").next().split(" ", 2)[0];
            new File(target + ".sha1").delete();
            if (!shaFile.equals(zipSha)) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(getString(R.string.status_shasum_error));
                builder.setMessage(R.string.message_shasum_not_matching);
                builder.setPositiveButton(R.string.ok, null);
                builder.create().show();
                Log.d(LOG, "SHA-1 check didn't pass");
            } else {
                Log.d(LOG, "SHA-1 check passed successfully with checksum " + zipSha);
            }
        } catch (Exception e) {
            Log.e(LOG, "Something went wrong while generating checksum", e);
            return "";
        }
        return null;
    }

    private String downloadNoVerify(String stringUrl, String targetLocation) {
        Log.d(LOG, "Acquiring wakeLock");
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        final PowerManager.WakeLock wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getClass().getName());
        wakeLock.acquire();

        String result = null;

        InputStream inputStream = null;
        OutputStream outputStream = null;
        HttpURLConnection connection = null;

        Bundle bundleInit = new Bundle();
        bundleInit.putString("title", getString(R.string.status_downloading_cuberite));
        receiver.send(ProgressReceiver.PROGRESS_START, bundleInit);

        install: try {
            Log.d(LOG, "Started downloading " + stringUrl);
            Log.d(LOG, "Downloading to " + targetLocation);

            URL url = new URL(stringUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.connect();

            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                String error = "Server returned HTTP " + connection.getResponseCode() + " " + connection.getResponseMessage();
                Log.e(LOG, error);
                result = error;
                break install;
            }

            int length = connection.getContentLength();
            inputStream = connection.getInputStream();
            outputStream = new FileOutputStream(targetLocation);

            byte[] data = new byte[4096];
            long total = 0;
            int count;
            while ((count = inputStream.read(data)) != -1) {
                total += count;
                if (length > 0) { // only if total length is known
                    Bundle bundleProg = new Bundle();
                    bundleProg.putInt("progress", (int) total);
                    bundleProg.putInt("max", length);
                    receiver.send(ProgressReceiver.PROGRESS_NEWDATA, bundleProg);
                }
                outputStream.write(data, 0, count);
            }
            Log.d(LOG, "Finished downloading");
        } catch (Exception e) {
            result = getString(R.string.status_no_connection);
            Log.e(LOG, "An error occurred when downloading a zip", e);
        } finally {
            try {
                if (outputStream != null)
                    outputStream.close();
                if (inputStream != null)
                    inputStream.close();
            } catch (IOException ignored) {}

            if (connection != null)
                connection.disconnect();
        }

        receiver.send(ProgressReceiver.PROGRESS_END, null);

        Log.d(LOG, "Releasing wakeLock");
        wakeLock.release();
        return result;
    }

    private String unzip(Uri fileUri, File targetLocation) {
        Log.i(LOG, "Unzipping " + fileUri + " to " + targetLocation.getAbsolutePath());
        String result = getString(R.string.status_unzip_error);

        Log.d(LOG, "Acquiring wakeLock");

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        final PowerManager.WakeLock wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getClass().getName());
        wakeLock.acquire();

        if (!targetLocation.exists()) {
            targetLocation.mkdir();
        }

        Bundle bundleInit = new Bundle();
        bundleInit.putString("title", getString(R.string.status_installing_cuberite));
        receiver.send(ProgressReceiver.PROGRESS_START_INDETERMINATE, bundleInit);

        try {
            InputStream inputStream = getContentResolver().openInputStream(fileUri);
            ZipInputStream zipInputStream = new ZipInputStream(inputStream);
            ZipEntry zipEntry;

            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                if (zipEntry.isDirectory()) {
                    new File(targetLocation.getAbsolutePath() + "/" + zipEntry.getName()).mkdir();
                } else {
                    FileOutputStream outputStream = new FileOutputStream(targetLocation.getAbsolutePath() + "/" + zipEntry.getName());
                    BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(outputStream);
                    byte[] buffer = new byte[1024];
                    int read;
                    while ((read = zipInputStream.read(buffer)) != -1) {
                        bufferedOutputStream.write(buffer, 0, read);
                    }
                    zipInputStream.closeEntry();
                    bufferedOutputStream.close();
                    outputStream.close();
                }

            }
            zipInputStream.close();
            result = getString(R.string.status_install_success);
        } catch (IOException e) {
            Log.e(LOG, "An error occurred while installing Cuberite", e);
        }
        receiver.send(ProgressReceiver.PROGRESS_END, null);

        Log.d(LOG, "Releasing wakeLock");
        wakeLock.release();

        return result;
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        receiver = intent.getParcelableExtra("receiver");
        String state = intent.getStringExtra("state");
        String result;

        if ((state.equals("NEED_DOWNLOAD_BINARY") ||
                state.equals("NEED_DOWNLOAD_BOTH") ||
                state.equals(Integer.toString(PICK_FILE_BINARY))) &&
                CuberiteService.isCuberiteRunning(getBaseContext())) {
            result = getString(R.string.status_update_binary_error);
            LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent("InstallService.callback").putExtra("result", result));
        } else if (intent.getAction().equals("unzip")) {
            Uri uri = Uri.parse(intent.getStringExtra("uri"));
            Log.d(LOG, intent.getStringExtra("targetLocation"));
            String targetLocation = (state.equals(Integer.toString(PICK_FILE_BINARY)) ? PRIVATE_DIR : intent.getStringExtra("targetLocation"));
            Log.d(LOG, targetLocation);
            result = unzip(uri, new File(targetLocation));
            LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent("InstallService.callback").putExtra("result", result));
        } else {
            String downloadHost = intent.getStringExtra("downloadHost");
            String abi = getPreferredABI();
            String executableName = intent.getStringExtra("executableName");
            String targetDirectory = (state.equals("NEED_DOWNLOAD_BINARY") || state.equals("NEED_DOWNLOAD_BOTH") ? PRIVATE_DIR : intent.getStringExtra("targetDirectory"));

            String zipTarget = PRIVATE_DIR + "/" + (state.equals("NEED_DOWNLOAD_BINARY") || state.equals("NEED_DOWNLOAD_BOTH") ? executableName : "server") + ".zip";
            String zipUrl = downloadHost + (state.equals("NEED_DOWNLOAD_BINARY") || state.equals("NEED_DOWNLOAD_BOTH") ? abi : "server") + ".zip";

            Log.i(LOG, "Downloading " + state);

            // Download
            if (intent.getAction().equals("install")) {
                result = downloadVerify(zipUrl, zipTarget);
            } else {
                result = downloadNoVerify(zipUrl, zipTarget);
            }

            if (result == null) {
                result = unzip(Uri.fromFile(new File(zipTarget)), new File(targetDirectory));

                if (!new File(zipTarget).delete()) {
                    Log.w(LOG, getString(R.string.status_delete_file_error));
                }
            }

            if (state.equals("NEED_DOWNLOAD_BOTH")) {
                intent.putExtra("state", "NEED_DOWNLOAD_SERVER");
                onHandleIntent(intent);
            } else {
                LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent("InstallService.callback").putExtra("result", result));
            }
        }
        stopSelf();
    }
}