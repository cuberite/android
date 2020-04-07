package org.cuberite.android.helpers;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.os.ResultReceiver;

public class ProgressReceiver extends ResultReceiver {
    public static final int PROGRESS_START_INDETERMINATE = 0;
    public static final int PROGRESS_START = 1;
    public static final int PROGRESS_NEWDATA = 2;
    public static final int PROGRESS_END = 3;

    private Context cont;

    private ProgressDialog progressDialog;

    public ProgressReceiver(Context context, Handler handler) {
        super(handler);
        cont = context;
    }
    @Override
    public void onReceiveResult(int resultCode, Bundle resultData) {
        super.onReceiveResult(resultCode, resultData);
        switch(resultCode) {
            case PROGRESS_START_INDETERMINATE: {
                String title = resultData.getString("title");
                progressDialog = new ProgressDialog(cont);
                progressDialog.setTitle(title);
                progressDialog.setMessage(title);
                progressDialog.setIndeterminate(true);
                progressDialog.show();

                progressDialog.setCanceledOnTouchOutside(false);
                progressDialog.setCancelable(false);
                break;
            }

            case PROGRESS_START: {
                String title = resultData.getString("title");
                progressDialog = new ProgressDialog(cont);
                progressDialog.setTitle(title);
                progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                progressDialog.show();

                progressDialog.setCanceledOnTouchOutside(false);
                progressDialog.setCancelable(false);
                break;
            }

            case PROGRESS_NEWDATA: {
                int progress = resultData.getInt("progress");
                int max = resultData.getInt("max");
                progressDialog.setIndeterminate(false);
                progressDialog.setProgress(progress);
                progressDialog.setMax(max);
                break;
            }

            case PROGRESS_END: {
                progressDialog.dismiss();
                break;
            }
        }
    }
}
