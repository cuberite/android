package org.cuberite.android.fragments;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.text.Html;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.cuberite.android.R;
import org.cuberite.android.Tags;
import org.cuberite.android.services.CuberiteService;

public class ConsoleFragment extends Fragment {
    private RelativeLayout logLayout;
    private TextView logView;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_console, null);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        //logLayout = view.findViewById(R.id.logLayout);
        logView = view.findViewById(R.id.logView);

        final EditText inputLine = view.findViewById(R.id.inputLine);
        inputLine.setOnEditorActionListener(new EditText.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                String line = inputLine.getText().toString();
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    if (!line.isEmpty() && ControlFragment.isServiceRunning(CuberiteService.class, getContext())) {
                        sendExecuteLine(line);
                        inputLine.setText("");
                    }
                    // return true makes sure the keyboard doesn't close
                    return true;
                }
                return false;
            }
        });

        ImageView sendCommandButton = view.findViewById(R.id.executeLine);
        sendCommandButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String line = inputLine.getText().toString();
                if (!line.isEmpty() && ControlFragment.isServiceRunning(CuberiteService.class, getContext())) {
                    sendExecuteLine(line);
                    inputLine.setText("");
                }
            }
        });
    }

    private void sendExecuteLine(String line) {
        Log.d(Tags.MAIN_ACTIVITY, "Executing " + line);
        Intent intent = new Intent("executeLine");
        intent.putExtra("message", line);
        LocalBroadcastManager.getInstance(getContext()).sendBroadcast(intent);
    }

    private BroadcastReceiver updateLog = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Extract data included in the Intent
            String message = CuberiteService.getLog();
            final ScrollView scrollView = (ScrollView) logView.getParent();
            // Only scroll down if we are already at bottom. getScrollY is how much we have scrolled, whereas getHeight is the complete height.
            //final int scrollY = scrollView.getScrollY();
            //final int bottomLocation = logLayout.getHeight() - scrollView.getHeight();

            logView.setText(Html.fromHtml(message));
            scrollView.post(new Runnable() {
                @Override
                public void run() {
                    //if(scrollY > bottomLocation)
                    scrollView.fullScroll(ScrollView.FOCUS_DOWN);

                }
            });
        }
    };

    @Override
    public void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(updateLog);
    }

    @Override
    public void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(getContext()).registerReceiver(updateLog, new IntentFilter("updateLog"));
        LocalBroadcastManager.getInstance(getContext()).sendBroadcast(new Intent("updateLog"));
    }
}