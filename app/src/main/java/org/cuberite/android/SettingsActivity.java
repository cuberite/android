package org.cuberite.android;

import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import com.google.android.material.snackbar.Snackbar;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.ini4j.Config;
import org.ini4j.Ini;

import java.io.File;
import java.io.IOException;

import static org.cuberite.android.MainActivity.PRIVATE_DIR;
import static org.cuberite.android.MainActivity.PUBLIC_DIR;
import static org.cuberite.android.MainActivity.getPreferredABI;

public class SettingsActivity extends AppCompatActivity {

    private final static int PICK_FILE_BINARY = 1;
    private final static int PICK_FILE_SERVER = 2;

    private SharedPreferences preferences;

    public static void initializeSettings(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(MainActivity.PACKAGE_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();

        if(!preferences.contains("externalStoragePermission")) {
            // TODO: only ask once for permisssions
        }

        if (!preferences.contains("cuberiteLocation") && preferences.getBoolean("externalStoragePermission", false)) {
            editor.putString("cuberiteLocation", PUBLIC_DIR + "/cuberite-server");
        } else if (!preferences.contains("cuberiteLocation") && preferences.contains("externalStoragePermission")) {
            editor.putString("cuberiteLocation", PRIVATE_DIR + "/cuberite-server");
        }
        if (!preferences.contains("executableName"))
            editor.putString("executableName", "Cuberite");
        if (!preferences.contains("downloadHost"))
            editor.putString("downloadHost", "https://builds.cuberite.org/job/cuberite/job/master/job/android/job/release/lastSuccessfulBuild/artifact/android/Server/");
        editor.apply();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // Ini4j config
        Config config = Config.getGlobal();
        config.setEscape(false);
        config.setStrictOperator(true);

        final Context context = this;

        preferences = getSharedPreferences(MainActivity.PACKAGE_NAME, MODE_PRIVATE);

        // Install options

        Button updateBinary = (Button) findViewById(R.id.installUpdateBinary);
        updateOnButtonClick(this, updateBinary, State.NEED_DOWNLOAD_BINARY);

        Button updateServer = (Button) findViewById(R.id.installUpdateServer);
        updateOnButtonClick(this, updateServer, State.NEED_DOWNLOAD_SERVER);

        String abi = String.format(getString(R.string.settings_install_manually_abi), getPreferredABI());
        ((TextView) findViewById(R.id.installABI)).setText(abi);

        Button installBinary = (Button) findViewById(R.id.installBinary);
        installSelectFileOnButtonClick(installBinary, PICK_FILE_BINARY);

        Button installServer = (Button) findViewById(R.id.installServer);
        installSelectFileOnButtonClick(installServer, PICK_FILE_SERVER);

        Button installNoSha = (Button) findViewById(R.id.installNoShaButton);
        installNoSha.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(context, InstallService.class);
                intent.setAction("installNoCheck");
                intent.putExtra("downloadHost", preferences.getString("downloadHost", ""));
                intent.putExtra("state", State.NEED_DOWNLOAD_BOTH.toString());
                intent.putExtra("executableName", preferences.getString("executableName", ""));
                intent.putExtra("targetDirectory", preferences.getString("cuberiteLocation", ""));
                intent.putExtra("receiver", new DownloadReceiver(context, new Handler()));
                LocalBroadcastManager.getInstance(context).registerReceiver(new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        LocalBroadcastManager.getInstance(context).unregisterReceiver(this);
                        String error = intent.getStringExtra("error");
                        if (error != null) {
                            Snackbar.make(findViewById(R.id.activity_main), getString(R.string.status_download_error) + error, Snackbar.LENGTH_LONG).show();
                        }
                    }
                }, new IntentFilter("InstallService.callback"));

                startService(intent);
            }
        });

        // Webadmin
        final Button webadminLogin = (Button) findViewById(R.id.webadminLogin);
        webadminLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                File cuberiteDir = new File(preferences.getString("cuberiteLocation", ""));
                final File webadminFile = new File(cuberiteDir.getAbsolutePath() + "/webadmin.ini");
                if(!cuberiteDir.exists()) {
                    cuberiteNotInstalled(context);
                } else {
                    try {
                        final Ini ini;
                        if(!webadminFile.exists()) {
                            ini = new Ini();
                            ini.put("WebAdmin", "Ports", 8080);
                        } else {
                            ini = new Ini(webadminFile);
                        }
                        ini.put("WebAdmin", "Enabled", 1);

                        String username = "";
                        String password = "";
                        for(String sectionName : ini.keySet()) {
                            if(sectionName.startsWith("User:")) {
                                username = sectionName.substring(5);
                                password = ini.get(sectionName, "Password");
                            }
                        }
                        final String oldUser = username;

                        final View layout = getLayoutInflater().inflate(R.layout.webadmin_layout, null);
                        ((EditText) layout.findViewById(R.id.webadminUsername)).setText(username);
                        ((EditText) layout.findViewById(R.id.webadminPassword)).setText(password);

                        AlertDialog.Builder builder = new AlertDialog.Builder(context)
                                .setView(layout)
                                .setTitle(R.string.settings_webadmin_login)
                                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int id) {
                                        String newUser = ((EditText) layout.findViewById(R.id.webadminUsername)).getText().toString();
                                        String newPass = ((EditText) layout.findViewById(R.id.webadminPassword)).getText().toString();

                                        if(newUser.equals("") || newPass.equals("")) {
                                            ini.put("WebAdmin", "Enabled", 0);
                                            ini.remove("User:" + oldUser);
                                        } else {
                                            ini.put("User:" + newUser, "Password", newPass);
                                        }
                                        try {
                                            ini.store(webadminFile);
                                            Snackbar.make(findViewById(R.id.activity_main), getString(R.string.settings_webadmin_success), Snackbar.LENGTH_LONG).show();
                                        } catch(IOException e) {
                                            Log.e(Tags.SETTINGS_ACTIVITY, "Something went wrong while saving the ini file", e);
                                            Snackbar.make(findViewById(R.id.activity_main), getString(R.string.settings_webadmin_error), Snackbar.LENGTH_LONG).show();
                                        }
                                    }
                                })
                                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        dialog.cancel();
                                    }
                                });
                        builder.create().show();
                    } catch(IOException e) {
                        Log.e(Tags.SETTINGS_ACTIVITY, "Something went wrong while opening the ini file", e);
                        Snackbar.make(findViewById(R.id.activity_main), getString(R.string.settings_webadmin_error), Snackbar.LENGTH_LONG).show();
                    }
                }
            }
        });

        Button toggleAuthentication = (Button) findViewById(R.id.troubleshootingAuthenticationToggle);
        toggleAuthentication.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                File cuberiteDir = new File(preferences.getString("cuberiteLocation", ""));
                final File settingsFile = new File(cuberiteDir.getAbsolutePath() + "/settings.ini");
                if(!cuberiteDir.exists()) {
                    cuberiteNotInstalled(context);
                } else if(!settingsFile.exists()) {
                    // TODO: prompt to start cuberite once
                } else {
                    try {
                        Ini ini = new Ini(settingsFile);
                        int enabled = ini.get("Authentication", "Authenticate", int.class);
                        int newenabled = enabled ^ 1; // XOR: 0 ^ 1 => 1, 1 ^ 1 => 0
                        ini.put("Authentication", "Authenticate", newenabled);
                        ini.store(settingsFile);
                        Snackbar.make(findViewById(R.id.activity_main), String.format(getString(R.string.settings_authentication_toggle_success), getString((newenabled == 1 ? R.string.enabled : R.string.disabled))), Snackbar.LENGTH_LONG).show();
                    } catch(IOException e) {
                        Log.e(Tags.SETTINGS_ACTIVITY, "Something went wrong while opening the ini file", e);
                        Snackbar.make(findViewById(R.id.activity_main), getString(R.string.settings_authentication_toggle_error), Snackbar.LENGTH_LONG).show();
                    }
                }
            }
        });

        TextView infoDebug = (TextView) findViewById(R.id.infoDebugInfo);
        String infos = "Running on Android " + Build.VERSION.RELEASE + " (API Level " + Build.VERSION.SDK_INT + ")\n" +
                "Using ABI " + getPreferredABI() + "\n" +
                "Private directory: " + PRIVATE_DIR + "\n" +
                "Public directory: " + PUBLIC_DIR + "\n" +
                "Storage location: " + preferences.getString("cuberiteLocation", "") + "\n" +
                "Will download from: " + preferences.getString("downloadHost", "");

        infoDebug.setText(infos);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK && data != null) {
            Uri selectedFile = data.getData();
            String path = PathUtils.getPath(this, selectedFile);

            switch (requestCode) {
                case PICK_FILE_BINARY: {
                    Intent intent = new Intent(this, InstallService.class);
                    intent.setAction("unzip");
                    intent.putExtra("file", path);
                    intent.putExtra("targetLocation", PRIVATE_DIR);
                    intent.putExtra("receiver", new DownloadReceiver(this, new Handler()));
                    startService(intent);
                    break;
                }
                case PICK_FILE_SERVER: {
                    Intent intent = new Intent(this, InstallService.class);
                    intent.setAction("unzip");
                    intent.putExtra("file", path);
                    intent.putExtra("targetLocation", preferences.getString("cuberiteLocation", ""));
                    intent.putExtra("receiver", new DownloadReceiver(this, new Handler()));
                    startService(intent);
                    break;
                }
            }
        }
    }

    private void installSelectFileOnButtonClick(Button button, final int code) {
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("*/*");
                try {
                    startActivityForResult(intent, code);
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(SettingsActivity.this, getString(R.string.status_missing_filemanager), Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void updateOnButtonClick(final Context context, Button button, final State state) {
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(context, InstallService.class);
                intent.setAction("install");
                intent.putExtra("downloadHost", preferences.getString("downloadHost", ""));
                intent.putExtra("state", state.toString());
                intent.putExtra("executableName", preferences.getString("executableName", ""));
                intent.putExtra("targetDirectory", preferences.getString("cuberiteLocation", ""));
                intent.putExtra("receiver", new DownloadReceiver(context, new Handler()));

                LocalBroadcastManager.getInstance(context).registerReceiver(new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        LocalBroadcastManager.getInstance(context).unregisterReceiver(this);
                        String error = intent.getStringExtra("error");
                        if(error != null) {
                            Snackbar.make(findViewById(R.id.activity_main), getString(R.string.status_download_error) + error, Snackbar.LENGTH_LONG).show();
                        }
                    }
                }, new IntentFilter("InstallService.callback"));
                startService(intent);
            }
        });
    }

    private void cuberiteNotInstalled(final Context context) {
        final AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.settings_webadmin_not_installed_title)
                .setMessage(R.string.settings_webadmin_not_installed)
                .setPositiveButton(R.string.do_install_cuberite, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int i) {
                        State state = null;
                        boolean hasBinary = false;
                        boolean hasServer = false;

                        // Install state
                        if (new File(PRIVATE_DIR + "/" + preferences.getString("executableName", "")).exists())
                            hasBinary = true;
                        if (new File(preferences.getString("cuberiteLocation", "")).exists())
                            hasServer = true;

                        if (!hasServer && !hasBinary)
                            state = State.NEED_DOWNLOAD_BOTH;
                        else if (!hasServer)
                            state = State.NEED_DOWNLOAD_SERVER;
                        else if (!hasBinary)
                            state = State.NEED_DOWNLOAD_BINARY;
                        Intent intent = new Intent(context, InstallService.class);
                        intent.setAction("install");
                        intent.putExtra("downloadHost", preferences.getString("downloadHost", ""));
                        intent.putExtra("state", state.toString());
                        intent.putExtra("executableName", preferences.getString("executableName", ""));
                        intent.putExtra("targetDirectory", preferences.getString("cuberiteLocation", ""));
                        intent.putExtra("receiver", new DownloadReceiver(context, new Handler()));

                        LocalBroadcastManager.getInstance(context).registerReceiver(new BroadcastReceiver() {
                            @Override
                            public void onReceive(Context context, Intent intent) {
                                LocalBroadcastManager.getInstance(context).unregisterReceiver(this);
                                String error = intent.getStringExtra("error");
                                if(error != null) {
                                    Snackbar.make(findViewById(R.id.activity_main), getString(R.string.status_download_error) + error, Snackbar.LENGTH_LONG).show();
                                }
                            }
                        }, new IntentFilter("InstallService.callback"));
                        startService(intent);
                    }
                }).setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int i) {
                        // Nothing to do
                    }
                })
                .create();
        dialog.show();
    }
}
