package org.cuberite.android.fragments;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
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

import com.google.android.material.color.MaterialColors;
import com.google.android.material.textfield.TextInputLayout;

import org.cuberite.android.R;
import org.cuberite.android.helpers.CuberiteHelper;

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
        inputLine.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                String command = inputLine.getText().toString();
                sendExecuteCommand(command);
                inputLine.setText("");
                // return true makes sure the keyboard doesn't close
                return true;
            }
            return false;
        });

        final TextInputLayout textInputLayout = view.findViewById(R.id.inputWrapper);
        textInputLayout.setEndIconOnClickListener(v -> {
            String command = inputLine.getText().toString();
            sendExecuteCommand(command);
            inputLine.setText("");
        });
    }

    private void sendExecuteCommand(String command) {
        if (!command.isEmpty()
                && CuberiteHelper.isCuberiteRunning(requireActivity())) {
            // Logging tag
            String LOG = "Cuberite/Console";

            Log.d(LOG, "Executing " + command);
            Intent intent = new Intent("executeCommand");
            intent.putExtra("message", command);
            LocalBroadcastManager.getInstance(requireContext()).sendBroadcast(intent);
        }
    }

    // Broadcast receivers
    private final BroadcastReceiver updateLog = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final ScrollView scrollView = (ScrollView) logView.getParent();
            boolean shouldScroll = (logView.getBottom() - (scrollView.getHeight() + scrollView.getScrollY())) <= 0;
            String output = CuberiteHelper.getConsoleOutput();
            SpannableStringBuilder formattedOutput = new SpannableStringBuilder();

            for (String line : output.split("\\n")) {
                if (line.isEmpty()) {
                    continue;
                }

                if (formattedOutput.length() > 0) {
                    // Line break
                    formattedOutput.append("\n");
                }

                int color = -1;

                if (line.toLowerCase().startsWith("log: ")) {
                    line = line.replaceFirst("(?i)log: ", "");
                }
                else if (line.toLowerCase().startsWith("info: ")) {
                    line = line.replaceFirst("(?i)info: ", "");
                    color = R.attr.colorTertiary;
                }
                else if (line.toLowerCase().startsWith("warning: ")) {
                    line = line.replaceFirst("(?i)warning: ", "");
                    color = R.attr.colorError;
                }
                else if (line.toLowerCase().startsWith("error: ")) {
                    line = line.replaceFirst("(?i)error: ", "");
                    color = R.attr.colorOnErrorContainer;
                }

                SpannableStringBuilder logLine = new SpannableStringBuilder(line);

                if (color >= 0) {
                    int start = 0;
                    int end = logLine.length();
                    color = MaterialColors.getColor(requireContext(), color, Color.BLACK);

                    logLine.setSpan(new ForegroundColorSpan(color), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                formattedOutput.append(logLine);
            }
            logView.setText(formattedOutput);

            if (shouldScroll) {
                scrollView.post(() -> {
                    scrollView.fullScroll(ScrollView.FOCUS_DOWN);
                    inputLine.requestFocus();
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
