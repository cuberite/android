package org.cuberite.android;

import android.app.IntentService;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.PowerManager;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.support.v4.os.ResultReceiver;
import androidx.appcompat.app.AlertDialog;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.cuberite.android.MainActivity.PRIVATE_DIR;
import static org.cuberite.android.MainActivity.generateSha1;
import static org.cuberite.android.MainActivity.getPreferredABI;

public class InstallService extends IntentService {

    private ResultReceiver receiver;

    public InstallService() {
        super("InstallService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {

        receiver = intent.getParcelableExtra("receiver");

        switch (intent.getAction()) {
            case "unzip": {
                String file = intent.getStringExtra("file");
                String targetLocation = intent.getStringExtra("targetLocation");
                String unzipError = unzip(new File(file), new File(targetLocation));
                if(unzipError != null){
                    LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent("InstallService.callback").putExtra("error", unzipError));
                    return;
                }
                LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent("InstallService.callback"));
                break;
            }
            case "install": {
                String downloadHost = intent.getStringExtra("downloadHost");
                String abi = getPreferredABI();
                State state = State.valueOf(intent.getStringExtra("state"));
                String executableName = intent.getStringExtra("executableName");
                String targetDirectory = (state == State.NEED_DOWNLOAD_BINARY || state == State.NEED_DOWNLOAD_BOTH ? PRIVATE_DIR : intent.getStringExtra("targetDirectory"));

                String zipTarget = PRIVATE_DIR + "/" + (state == State.NEED_DOWNLOAD_BINARY || state == State.NEED_DOWNLOAD_BOTH ? executableName : "server") + ".zip";
                String zipUrl = downloadHost + (state == State.NEED_DOWNLOAD_BINARY || state == State.NEED_DOWNLOAD_BOTH ? abi : "server") + ".zip";

                Log.i(Tags.INSTALL_SERVICE, "Downloading " + state.toString());

                // Download
                String downloadError = downloadAndVerify(zipUrl, zipTarget);
                if (downloadError != null) {
                    LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent("InstallService.callback").putExtra("error", downloadError));
                    return;
                }

                // Unzipping file
                String unzipError = unzip(new File(zipTarget), new File(targetDirectory));
                if (!new File(zipTarget).delete())
                    Log.w(Tags.INSTALL_SERVICE, getString(R.string.status_delete_file_error));
                if(unzipError != null) {
                    LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent("InstallService.callback").putExtra("error", unzipError));
                    return;
                }

                if(state == State.NEED_DOWNLOAD_BOTH) {
                    intent.putExtra("state", State.NEED_DOWNLOAD_SERVER.toString());
                    onHandleIntent(intent);
                } else
                    LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent("InstallService.callback"));
                break;
            }
            case "installNoCheck": {
                String downloadHost = intent.getStringExtra("downloadHost");
                String abi = getPreferredABI();
                State state = State.valueOf(intent.getStringExtra("state"));
                String executableName = intent.getStringExtra("executableName");
                String targetDirectory = (state == State.NEED_DOWNLOAD_BINARY || state == State.NEED_DOWNLOAD_BOTH ? PRIVATE_DIR : intent.getStringExtra("targetDirectory"));

                String zipTarget = PRIVATE_DIR + "/" + (state == State.NEED_DOWNLOAD_BINARY || state == State.NEED_DOWNLOAD_BOTH ? executableName : "server") + ".zip";
                String zipUrl = downloadHost + (state == State.NEED_DOWNLOAD_BINARY || state == State.NEED_DOWNLOAD_BOTH ? abi : "server") + ".zip";

                Log.i(Tags.INSTALL_SERVICE, "Downloading without checking " + state.toString());

                String zipFileError = downloadFile(zipUrl, zipTarget);
                if (zipFileError != null) {
                    LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent("InstallService.callback").putExtra("error", zipFileError));
                    return;
                }

                String unzipError = unzip(new File(zipTarget), new File(targetDirectory));
                if (!new File(zipTarget).delete())
                    Log.w(Tags.INSTALL_SERVICE, getString(R.string.status_delete_file_error));
                if(unzipError != null) {
                    LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent("InstallService.callback").putExtra("error", unzipError));
                    return;
                }

                if(state == State.NEED_DOWNLOAD_BOTH) {
                    intent.putExtra("state", State.NEED_DOWNLOAD_SERVER.toString());
                    onHandleIntent(intent);
                } else
                    LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent("InstallService.callback"));
                break;
            }
        }
    }

    private String downloadAndVerify(String url, String target) {
        String zipFileError = downloadFile(url, target);
        if(zipFileError != null)
            return zipFileError;

        // Verifying file
        String zipSha = generateSha1(target);
        String shaError = downloadFile(url + ".sha1", target + ".sha1");
        if (shaError != null)
            return shaError;

        try {
            String shaFile = new Scanner(new File(target + ".sha1")).useDelimiter("\\Z").next().split(" ", 2)[0];
            new File(target + ".sha1").delete();
            if (!shaFile.equals(zipSha)) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(getString(R.string.status_shasum_error));
                builder.setMessage(R.string.message_shasum_not_matching);
                builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        // No event
                    }
                });
                builder.create().show();
                return null;
            }
            Log.d(Tags.INSTALL_SERVICE, "SHA-1 check passed successfully with checksum " + zipSha);
        } catch (Exception e) {
            Log.e(Tags.INSTALL_SERVICE, "Something went wrong while generating checksum", e);
            return "";
        }
        return null;
    }

    private String downloadFile(String stringUrl, String targetLocation) {
        Log.d(Tags.INSTALL_SERVICE, "Acquiring wakeLock");
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        final PowerManager.WakeLock wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getClass().getName());
        wakeLock.acquire();

        receiver.send(DownloadReceiver.PROGRESS_START, null);

        String result = null;

        InputStream inputStream = null;
        OutputStream outputStream = null;
        HttpURLConnection connection = null;
        install: try {
            Log.d(Tags.INSTALL_SERVICE, "Started downloading " + stringUrl);
            URL url = new URL(stringUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.connect();
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                String error = "Server returned HTTP " + connection.getResponseCode() + " " + connection.getResponseMessage();
                Log.e(Tags.INSTALL_SERVICE, error);
                result = error;
                break install;
            }

            int length = connection.getContentLength();
            inputStream = connection.getInputStream();
            outputStream = new FileOutputStream(targetLocation);

            byte data[] = new byte[4096];
            long total = 0;
            int count;
            while ((count = inputStream.read(data)) != -1) {
                total += count;
                if (length > 0) { // only if total length is known
                    Bundle bundle = new Bundle();
                    bundle.putInt("progress", (int) total);
                    bundle.putInt("max", length);
                    receiver.send(DownloadReceiver.PROGRESS_NEWDATA, bundle);
                }
                outputStream.write(data, 0, count);
            }
            Log.d(Tags.INSTALL_SERVICE, "Finished downloading");
        } catch (Exception e) {
            result = e.getMessage();
            Log.e(Tags.INSTALL_SERVICE, "An error occurred when downloading a zip", e);
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

        Log.d(Tags.INSTALL_SERVICE, "Releasing wakeLock");
        wakeLock.release();
        receiver.send(DownloadReceiver.PROGRESS_END, null);
        return result;
    }

    private String unzip(File file, File targetLocation) {
        Log.i(Tags.INSTALL_SERVICE, "Unzipping " + file.getAbsolutePath() + " to " + targetLocation.getAbsolutePath());
        Log.d(Tags.INSTALL_SERVICE, "Acquiring wakeLock");

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        final PowerManager.WakeLock wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getClass().getName());
        wakeLock.acquire();

        receiver.send(DownloadReceiver.PROGRESS_START, null);

        if (!targetLocation.exists())
            targetLocation.mkdir();

        // Create a .nomedia file in the server directory to prevent images from showing in gallery
        createNoMediaFile(targetLocation.getAbsolutePath());

        String result = null;

        try {
            FileInputStream inputStream = new FileInputStream(file);
            ZipInputStream zipInputStream = new ZipInputStream(inputStream);
            ZipEntry zipEntry;
            int length = (int) file.length();
            int progress = 0;
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
                        Bundle bundle = new Bundle();
                        bundle.putInt("progress", ++progress);
                        bundle.putInt("max", length);
                        receiver.send(DownloadReceiver.PROGRESS_NEWDATA, bundle);
                    }
                    zipInputStream.closeEntry();
                    bufferedOutputStream.close();
                    outputStream.close();
                }

            }
            zipInputStream.close();
        } catch (IOException e) {
            Log.e(Tags.INSTALL_SERVICE, "An error occurred while installing Cuberite", e);
            result = getString(R.string.status_unzip_error);
        }
        Log.d(Tags.INSTALL_SERVICE, "Releasing wakeLock");
        wakeLock.release();
        receiver.send(DownloadReceiver.PROGRESS_END, null);
        return result;
    }

    private void createNoMediaFile(String filePath) {
        final File noMedia = new File(filePath + "/.nomedia");
        try {
            noMedia.createNewFile();
        } catch (IOException e) {
            Log.e(Tags.INSTALL_SERVICE, "Something went wrong while creating the .nomedia file", e);
        }
    }
}