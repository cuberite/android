package org.cuberite.android.receivers;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;
import android.view.View;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import org.cuberite.android.R;

public class ProgressReceiver extends ResultReceiver {
    public static final int PROGRESS_START = 0;
    public static final int PROGRESS_NEW_DATA = 1;
    public static final int PROGRESS_END = 2;

    private final Context cont;

    private AlertDialog progressDialog;
    private LinearProgressIndicator progressBar;

    public ProgressReceiver(Context context, Handler handler) {
        super(handler);
        cont = context;
    }

    private void createDialog(String title) {
        final View layout = View.inflate(cont, R.layout.dialog_progress, null);
        progressBar = ((LinearProgressIndicator) layout.findViewById(R.id.progressBar));
        progressDialog = new MaterialAlertDialogBuilder(cont)
                .setTitle(title)
                .setView(layout)
                .setCancelable(false)
                .create();
    }

    @Override
    public void onReceiveResult(int resultCode, Bundle resultData) {
        super.onReceiveResult(resultCode, resultData);
        switch (resultCode) {
            case PROGRESS_START -> {
                String title = resultData.getString("title");
                if (progressDialog == null) {
                    createDialog(title);
                } else {
                    progressDialog.setTitle(title);
                }
                progressBar.setIndeterminate(true);
                progressDialog.show();
            }
            case PROGRESS_NEW_DATA -> {
                int progress = resultData.getInt("progress");
                int max = resultData.getInt("max");
                progressBar.setIndeterminate(false);
                progressBar.setProgressCompat(progress, true);
                progressBar.setMax(max);
            }
            case PROGRESS_END -> {
                progressDialog.dismiss();
                progressDialog = null;
            }
        }
    }
}
