package org.cuberite.android;

import android.app.ProgressDialog;
import android.os.PowerManager;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class UnzipThread extends Thread {
    private String file;
    private String _targetDir;
    private ProgressDialog progress;
    private PowerManager.WakeLock wakeLock;
    private OnThreadEndListener onThreadEndListener;

    public UnzipThread(String file, String _targetLocation, ProgressDialog progress, PowerManager.WakeLock wakeLock, OnThreadEndListener onThreadEndListener) {
        this.file = file;
        this._targetDir = _targetLocation;
        this.progress = progress;
        this.wakeLock = wakeLock;
        this.onThreadEndListener = onThreadEndListener;
    }

    @Override
    public void run() {
        // make sure the target directory exists
        File targetDir = new File(_targetDir);
        if(!targetDir.exists())
            targetDir.mkdir();

        try {
            FileInputStream inputStream = new FileInputStream(file);
            ZipInputStream zipInputStream = new ZipInputStream(inputStream);
            ZipEntry zipEntry;
            progress.setMax((int) new File(file).length());
            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                if (zipEntry.isDirectory()) {
                    new File(_targetDir + "/" + zipEntry.getName()).mkdir();
                } else {
                    FileOutputStream outputStream = new FileOutputStream(_targetDir + "/" + zipEntry.getName());
                    BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(outputStream);
                    byte[] buffer = new byte[1024];
                    int read;
                    while((read = zipInputStream.read(buffer)) != -1) {
                        bufferedOutputStream.write(buffer, 0, read);
                        progress.incrementProgressBy(1);
                    }
                    zipInputStream.closeEntry();
                    bufferedOutputStream.close();
                    outputStream.close();
                }

            }
            zipInputStream.close();
        } catch (IOException e) {
            System.out.println(e.toString());
        } finally {
            Log.v(MainActivity.TAG, "Releasing wakeLock");
            wakeLock.release();
            progress.dismiss();
            onThreadEndListener.run();
        }
    }

    interface OnThreadEndListener {
        void run();
    }
}
