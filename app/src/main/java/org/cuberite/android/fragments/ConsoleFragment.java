package org.cuberite.android.fragments;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.TooltipCompat;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.cuberite.android.R;
import org.cuberite.android.services.CuberiteService;

public class ConsoleFragment extends Fragment {
    private TextView logView;
    private EditText inputLine;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_console, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        logView = view.findViewById(R.id.logView);

        inputLine = view.findViewById(R.id.inputLine);
        inputLine.setOnEditorActionListener(new EditText.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                String line = inputLine.getText().toString();
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    if (!line.isEmpty() && CuberiteService.isCuberiteRunning(requireActivity())) {
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
                if (!line.isEmpty() && CuberiteService.isCuberiteRunning(requireActivity())) {
                    sendExecuteLine(line);
                    inputLine.setText("");
                }
            }
        });
        TooltipCompat.setTooltipText(sendCommandButton, getString(R.string.do_execute_line));
    }

    private void sendExecuteLine(String line) {
        // Logging tag
        String LOG = "Cuberite/Console";

        Log.d(LOG, "Executing " + line);
        Intent intent = new Intent("executeLine");
        intent.putExtra("message", line);
        LocalBroadcastManager.getInstance(requireContext()).sendBroadcast(intent);
    }

    private BroadcastReceiver updateLog = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Extract data included in the Intent
            String message = CuberiteService.getConsoleOutput();
            final ScrollView scrollView = (ScrollView) logView.getParent();

            boolean shouldScroll = (logView.getBottom() - (scrollView.getHeight() + scrollView.getScrollY())) <= 0;
            logView.setText(Html.fromHtml(message));

            if (shouldScroll) {
                scrollView.post(new Runnable() {
                    @Override
                    public void run() {
                        scrollView.fullScroll(ScrollView.FOCUS_DOWN);
                        inputLine.requestFocus();
                    }
                });
            }
        }
    };

    @Override
    public void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(updateLog);
    }

    @Override
    public void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(updateLog, new IntentFilter("updateLog"));
        LocalBroadcastManager.getInstance(requireContext()).sendBroadcast(new Intent("updateLog"));
    }
}
