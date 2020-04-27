package org.cuberite.android.services;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;

import android.support.v4.os.ResultReceiver;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.util.Log;

import org.cuberite.android.helpers.CuberiteHelper;
import org.cuberite.android.helpers.StateHelper.State;
import org.cuberite.android.receivers.ProgressReceiver;
import org.cuberite.android.R;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
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

public class InstallService extends IntentService {
    // Logging tag
    private static final String LOG = "Cuberite/InstallService";

    private ResultReceiver receiver;

    public InstallService() {
        super("InstallService");
    }


    // Wakelock

    private PowerManager.WakeLock acquireWakelock() {
        Log.d(LOG, "Acquiring wakeLock");

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        final PowerManager.WakeLock wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getClass().getName());
        wakeLock.acquire(300000);  // 5 min timeout

        return wakeLock;
    }


    // Download verification

    private String downloadVerify(String url, File targetLocation, int retryCount) {
        final String zipFileError = download(url, targetLocation);
        if (zipFileError != null) {
            return zipFileError;
        }

        // Verifying file
        final String shaError = download(url + ".sha1", new File(targetLocation.getAbsolutePath() + ".sha1"));
        if (shaError != null) {
            return shaError;
        }

        try {
            final String generatedSha = generateSha1(targetLocation);
            final String downloadedSha = new Scanner(
                    new File(targetLocation.getAbsolutePath() + ".sha1")
            )
                    .useDelimiter("\\Z")
                    .next()
                    .split(" ", 2)[0];
            new File(targetLocation.getAbsolutePath() + ".sha1").delete();

            if (!downloadedSha.equals(generatedSha)) {
                Log.d(LOG, "SHA-1 check didn't pass");

                if (retryCount > 0) {
                    // Retry if verification failed
                    return downloadVerify(url, targetLocation, retryCount - 1);
                }

                return getString(R.string.status_shasum_error);
            }

            Log.d(LOG, "SHA-1 check passed successfully with checksum " + generatedSha);
        } catch (FileNotFoundException e) {
            Log.e(LOG, "Something went wrong while generating checksum", e);
            return getString(R.string.status_shasum_error);
        }

        return null;
    }

    private String generateSha1(File targetLocation) {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            InputStream input = new FileInputStream(targetLocation);
            byte[] buffer = new byte[8192];
            int len = input.read(buffer);

            while (len != -1) {
                sha1.update(buffer, 0, len);
                len = input.read(buffer);
            }
            byte[] shaSum = sha1.digest();
            char[] charset = "0123456789ABCDEF".toCharArray();
            char[] hexResult = new char[shaSum.length * 2];
            for (int j = 0; j < shaSum.length; j++) {
                int v = shaSum[j] & 0xFF;
                hexResult[j * 2] = charset[v >>> 4];
                hexResult[j * 2 + 1] = charset[v & 0x0F];
            }
            return new String(hexResult).toLowerCase();
        } catch (Exception e) {
            return e.toString();
        }
    }


    // Download

    private String download(String stringUrl, File targetLocation) {
        final PowerManager.WakeLock wakeLock = acquireWakelock();

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
            connection.setConnectTimeout(10000); // 10 secs
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
                    receiver.send(ProgressReceiver.PROGRESS_NEW_DATA, bundleProg);
                }
                outputStream.write(data, 0, count);
            }
            Log.d(LOG, "Finished downloading");
        } catch (Exception e) {
            result = e.getMessage();
            Log.e(LOG, "An error occurred when downloading a zip", e);
        } finally {
            try {
                if (outputStream != null) {
                    outputStream.close();
                }
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (IOException ignored) {}

            if (connection != null) {
                connection.disconnect();
            }
        }

        receiver.send(ProgressReceiver.PROGRESS_END, null);

        Log.d(LOG, "Releasing wakeLock");
        wakeLock.release();
        return result;
    }


    // Unzip

    private String unzip(Uri fileUri, File targetLocation) {
        String result = getString(R.string.status_install_success);

        Log.i(LOG, "Unzipping " + fileUri + " to " + targetLocation.getAbsolutePath());

        final PowerManager.WakeLock wakeLock = acquireWakelock();

        if (!targetLocation.exists()) {
            targetLocation.mkdir();
        }

        // Create a .nomedia file in the server directory to prevent images from showing in gallery
        createNoMediaFile(targetLocation.getAbsolutePath());

        Bundle bundleInit = new Bundle();
        bundleInit.putString("title", getString(R.string.status_installing_cuberite));
        receiver.send(ProgressReceiver.PROGRESS_START_INDETERMINATE, bundleInit);

        try {
            unzipStream(fileUri, targetLocation);
        } catch (IOException e) {
            result = getString(R.string.status_unzip_error);
            Log.e(LOG, "An error occurred while installing Cuberite", e);
        }

        receiver.send(ProgressReceiver.PROGRESS_END, null);

        Log.d(LOG, "Releasing wakeLock");
        wakeLock.release();

        return result;
    }

    private void unzipStream(Uri fileUri, File targetLocation) throws IOException {
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
    }

    private void createNoMediaFile(String filePath) {
        final File noMedia = new File(filePath + "/.nomedia");
        try {
            noMedia.createNewFile();
        } catch (IOException e) {
            Log.e(LOG, "Something went wrong while creating the .nomedia file", e);
        }
    }


    // Service handler

    @Override
    protected void onHandleIntent(Intent intent) {
        State state = (State) intent.getSerializableExtra("state");
        String result;

        if (
                (state == State.NEED_DOWNLOAD_BINARY
                || state == State.NEED_DOWNLOAD_BOTH
                || state == State.PICK_FILE_BINARY)
                && CuberiteHelper.isCuberiteRunning(getApplicationContext())
        ) {
            result = getString(R.string.status_update_binary_error);
        } else if ("unzip".equals(intent.getAction())) {
            final Uri uri = Uri.parse(intent.getStringExtra("uri"));
            final File targetFolder = new File(
                    state == State.PICK_FILE_BINARY ? this.getFilesDir().getAbsolutePath() : intent.getStringExtra("targetFolder")
            );
            receiver = intent.getParcelableExtra("receiver");

            result = unzip(uri, targetFolder);
        } else {
            final String downloadHost = intent.getStringExtra("downloadHost");
            final String abi = CuberiteHelper.getPreferredABI();

            final String targetFileName = (state == State.NEED_DOWNLOAD_BINARY || state == State.NEED_DOWNLOAD_BOTH ? abi : "server") + ".zip";
            final String downloadUrl = downloadHost + targetFileName;
            final File targetZip = new File(this.getFilesDir().getAbsolutePath() + "/" + targetFileName);  // Download all zip files to internal storage
            final File targetFolder = new File(
                    state == State.NEED_DOWNLOAD_BINARY || state == State.NEED_DOWNLOAD_BOTH ? this.getFilesDir().getAbsolutePath() : intent.getStringExtra("targetFolder")
            );
            receiver = intent.getParcelableExtra("receiver");

            // Download
            Log.i(LOG, "Downloading " + state);

            final int retryCount = 1;
            result = downloadVerify(downloadUrl, targetZip, retryCount);

            if (result == null) {
                result = unzip(Uri.fromFile(targetZip), targetFolder);

                if (!targetZip.delete()) {
                    Log.w(LOG, getString(R.string.status_delete_file_error));
                }
            }

            if (state == State.NEED_DOWNLOAD_BOTH) {
                intent.putExtra("state", State.NEED_DOWNLOAD_SERVER);
                onHandleIntent(intent);
            }
        }

        stopSelf();
        LocalBroadcastManager.getInstance(this).sendBroadcast(
                new Intent("InstallService.callback")
                        .putExtra("result", result)
        );
    }
}
