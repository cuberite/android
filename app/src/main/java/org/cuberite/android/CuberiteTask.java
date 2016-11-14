package org.cuberite.android;

import android.os.AsyncTask;
import android.text.Html;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedWriter;
import java.io.File;
import java.io.OutputStreamWriter;
import java.util.Scanner;

public class CuberiteTask extends AsyncTask<String, String, Integer> {

    private TextView textView;
    private Process process;
    private ProcessBuilder processBuilder;
    private Scanner processScanner;
    private BufferedWriter cuberiteSTDIN;
    private String cuberiteLocation;
    private String binaryLocation;
    private String stopCommand;
    private OnEndListener onEndListener;

    CuberiteTask(TextView textView, String cuberiteLocation, String binaryLocation, OnEndListener onEndListener) {
        this.textView = textView;
        this.cuberiteLocation = cuberiteLocation;
        this.binaryLocation = binaryLocation;
        this.onEndListener = onEndListener;
        stopCommand = "stop";
    }

    protected void executeLine(String line) {
        try {
            cuberiteSTDIN.write(line + "\n");
            cuberiteSTDIN.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected void stop() {
        try {
            cuberiteSTDIN.write(stopCommand + "\n");
            cuberiteSTDIN.flush();
            cuberiteSTDIN.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected void kill() {
        process.destroy();
    }

    @Override
    protected Integer doInBackground(String... params) {
        try {
            process = processBuilder.start();

            // Logging thread. This thread will check cuberite's stdout (and stderr), color it and append it to the logView. This thread will wait only for next lines coming. if stdout is closed, this thread will exit
            new Thread(new Runnable() {
                @Override
                public void run() {
                    processScanner = new Scanner(process.getInputStream());
                    while (processScanner.hasNextLine()) {
                        String line = processScanner.nextLine();
                        publishProgress(line);
                        System.out.println(line);
                    }
                    processScanner.close();
                }
            }).start();

            // Open STDIN for the inputLine
            cuberiteSTDIN = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));

            // Wait for the process to end. Logic waits here until cuberite has stopped. Everything after that is cleanup for the next run
            process.waitFor();
            return process.exitValue();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    protected void onPostExecute(Integer result) {
        addLogToTextView("\n" + result);
        onEndListener.run();
    }

    @Override
    protected void onPreExecute() {
        // Make sure we can execute the binary
        new File(binaryLocation).setExecutable(true, true);
        // Initiate ProcessBuilder with the command at the given location
        processBuilder = new ProcessBuilder(binaryLocation);
        processBuilder.directory(new File(cuberiteLocation).getAbsoluteFile());
        processBuilder.redirectErrorStream(true);
        addLogToTextView("Info: Cuberite is starting...");
    }

    // Adds log to the logView
    @Override
    protected void onProgressUpdate(String... values) {
        for(int i = 0; i<values.length; i++) {
            addLogToTextView(values[i]);
        }
    }

    private void addLogToTextView(String string) {
        String line = "";
        String[] text = string.split("\\n");
        for (int i = 0; i < text.length; i++) {
            String curText = Html.escapeHtml(text[i]);
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

            line += "<br>" + curText;
        }

        final ScrollView scrollView = (ScrollView) textView.getParent();
        // Only scroll down if we are already at bottom. getScrollY is how much we have scrolled, whereas getHeight is the complete height.
        final int scrollY = scrollView.getScrollY();
        final int bottomLocation = textView.getHeight() - scrollView.getHeight();

        textView.append(Html.fromHtml("\n" + line));
        scrollView.post(new Runnable() {
            @Override
            public void run() {
                if(scrollY > bottomLocation)
                    scrollView.fullScroll(ScrollView.FOCUS_DOWN);
            }
        });
    }

    interface OnEndListener {
        void run();
    }
}
