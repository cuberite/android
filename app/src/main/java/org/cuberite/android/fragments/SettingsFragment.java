package org.cuberite.android.fragments;

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

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.appcompat.app.AlertDialog;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.cuberite.android.ProgressReceiver;
import org.cuberite.android.services.CuberiteService;
import org.cuberite.android.services.InstallService;
import org.cuberite.android.PathUtils;
import org.cuberite.android.R;
import org.cuberite.android.State;
import org.cuberite.android.Tags;
import org.ini4j.Config;
import org.ini4j.Ini;

import java.io.File;
import java.io.IOException;

import static android.content.Context.MODE_PRIVATE;
import static org.cuberite.android.MainActivity.PACKAGE_NAME;
import static org.cuberite.android.MainActivity.PRIVATE_DIR;
import static org.cuberite.android.MainActivity.PUBLIC_DIR;

public class SettingsFragment extends Fragment {
    private final static int PICK_FILE_BINARY = 1;
    private final static int PICK_FILE_SERVER = 2;

    private SharedPreferences preferences;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, null);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        // Ini4j config
        Config config = Config.getGlobal();
        config.setEscape(false);
        config.setStrictOperator(true);

        preferences = getActivity().getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE);

        // Install options

        Button updateBinary = view.findViewById(R.id.installUpdateBinary);
        updateOnButtonClick(getContext(), updateBinary, State.NEED_DOWNLOAD_BINARY);

        Button updateServer = view.findViewById(R.id.installUpdateServer);
        updateOnButtonClick(getContext(), updateServer, State.NEED_DOWNLOAD_SERVER);

        String abi = String.format(getString(R.string.settings_install_manually_abi), InstallService.getPreferredABI());
        ((TextView) view.findViewById(R.id.installABI)).setText(abi);

        Button installBinary = view.findViewById(R.id.installBinary);
        installSelectFileOnButtonClick(installBinary, PICK_FILE_BINARY);

        Button installServer = view.findViewById(R.id.installServer);
        installSelectFileOnButtonClick(installServer, PICK_FILE_SERVER);

        Button installNoSha = view.findViewById(R.id.installNoShaButton);
        installNoSha.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (ControlFragment.isServiceRunning(CuberiteService.class, getContext())) {
                    Snackbar.make(view.findViewById(R.id.fragment_container), getString(R.string.status_update_binary_error), Snackbar.LENGTH_LONG).show();
                    return;
                }
                Intent intent = new Intent(getContext(), InstallService.class);
                intent.setAction("installNoCheck");
                intent.putExtra("downloadHost", preferences.getString("downloadHost", ""));
                intent.putExtra("state", State.NEED_DOWNLOAD_BOTH.toString());
                intent.putExtra("executableName", preferences.getString("executableName", ""));
                intent.putExtra("targetDirectory", preferences.getString("cuberiteLocation", ""));
                intent.putExtra("receiver", new ProgressReceiver(getContext(), new Handler()));
                LocalBroadcastManager.getInstance(getContext()).registerReceiver(new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        LocalBroadcastManager.getInstance(context).unregisterReceiver(this);
                        String error = intent.getStringExtra("error");
                        if (error != null) {
                            Snackbar.make(getActivity().findViewById(R.id.fragment_container), getString(R.string.status_download_error) + " " + error, Snackbar.LENGTH_LONG).show();
                        }
                    }
                }, new IntentFilter("InstallService.callback"));

                getActivity().startService(intent);
            }
        });

        // Webadmin
        final Button webadminOpen = view.findViewById(R.id.webadminOpen);
        webadminOpen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!ControlFragment.isServiceRunning(CuberiteService.class, getContext())) {
                    Snackbar.make(getActivity().findViewById(R.id.fragment_container), getString(R.string.settings_webadmin_not_running), Snackbar.LENGTH_LONG).show();
                    return;
                }
                try {
                    File cuberiteDir = new File(preferences.getString("cuberiteLocation", ""));
                    final File webadminFile = new File(cuberiteDir.getAbsolutePath() + "/webadmin.ini");

                    Ini ini;
                    if(!webadminFile.exists()) {
                        ini = new Ini();
                        ini.put("WebAdmin", "Ports", 8080);
                    } else {
                        ini = new Ini(webadminFile);
                    }

                    final String ip = CuberiteService.getIpAddress(getContext());
                    final String port = ini.get("WebAdmin", "Ports");
                    final String url = "http://" + ip + ":" + port;

                    Log.d(Tags.SETTINGS_ACTIVITY, "Opening Webadmin on " + url);
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://" + ip + ":" + port));
                    startActivity(browserIntent);
                } catch (IOException e) {
                    Log.e(Tags.SETTINGS_ACTIVITY, "Something went wrong while opening the ini file", e);
                    Snackbar.make(view.findViewById(R.id.container), getString(R.string.settings_webadmin_error), Snackbar.LENGTH_LONG).show();
                }
            }
        });

        final Button webadminLogin = view.findViewById(R.id.webadminLogin);
        webadminLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                File cuberiteDir = new File(preferences.getString("cuberiteLocation", ""));
                final File webadminFile = new File(cuberiteDir.getAbsolutePath() + "/webadmin.ini");
                if(!cuberiteDir.exists()) {
                    cuberiteNotInstalled(getContext());
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

                        final View layout = getLayoutInflater().inflate(R.layout.dialog_webadmin_credentials, null);
                        ((EditText) layout.findViewById(R.id.webadminUsername)).setText(username);
                        ((EditText) layout.findViewById(R.id.webadminPassword)).setText(password);

                        AlertDialog.Builder builder = new AlertDialog.Builder(getContext())
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
                                            Snackbar.make(getActivity().findViewById(R.id.fragment_container), getString(R.string.settings_webadmin_success), Snackbar.LENGTH_LONG).show();
                                        } catch(IOException e) {
                                            Log.e(Tags.SETTINGS_ACTIVITY, "Something went wrong while saving the ini file", e);
                                            Snackbar.make(getActivity().findViewById(R.id.fragment_container), getString(R.string.settings_webadmin_error), Snackbar.LENGTH_LONG).show();
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
                        Snackbar.make(view.findViewById(R.id.container), getString(R.string.settings_webadmin_error), Snackbar.LENGTH_LONG).show();
                    }
                }
            }
        });

        Button toggleAuthentication = view.findViewById(R.id.troubleshootingAuthenticationToggle);
        toggleAuthentication.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                File cuberiteDir = new File(preferences.getString("cuberiteLocation", ""));
                final File settingsFile = new File(cuberiteDir.getAbsolutePath() + "/settings.ini");
                if(!cuberiteDir.exists()) {
                    cuberiteNotInstalled(getContext());
                } else if(!settingsFile.exists()) {
                    // TODO: prompt to start cuberite once
                } else {
                    try {
                        Ini ini = new Ini(settingsFile);
                        int enabled = Integer.parseInt(ini.get("Authentication", "Authenticate"));
                        int newenabled = enabled ^ 1; // XOR: 0 ^ 1 => 1, 1 ^ 1 => 0
                        ini.put("Authentication", "Authenticate", newenabled);
                        ini.store(settingsFile);
                        Snackbar.make(view.findViewById(R.id.container), String.format(getString(R.string.settings_authentication_toggle_success), getString((newenabled == 1 ? R.string.enabled : R.string.disabled))), Snackbar.LENGTH_LONG).show();
                    } catch(IOException e) {
                        Log.e(Tags.SETTINGS_ACTIVITY, "Something went wrong while opening the ini file", e);
                        Snackbar.make(view.findViewById(R.id.container), getString(R.string.settings_authentication_toggle_error), Snackbar.LENGTH_LONG).show();
                    }
                }
            }
        });

        TextView infoDebug = view.findViewById(R.id.infoDebugInfo);
        String infos = "Running on Android " + Build.VERSION.RELEASE + " (API Level " + Build.VERSION.SDK_INT + ")\n" +
                "Using ABI " + InstallService.getPreferredABI() + "\n" +
                "Private directory: " + PRIVATE_DIR + "\n" +
                "Public directory: " + PUBLIC_DIR + "\n" +
                "Storage location: " + preferences.getString("cuberiteLocation", "") + "\n" +
                "Will download from: " + preferences.getString("downloadHost", "");

        infoDebug.setText(infos);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Ini4j config
        Config config = Config.getGlobal();
        config.setEscape(false);
        config.setStrictOperator(true);

        preferences = this.getActivity().getSharedPreferences(PACKAGE_NAME, this.getActivity().MODE_PRIVATE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == getActivity().RESULT_OK && data != null) {
            Uri selectedFile = data.getData();
            String path = PathUtils.getPath(getContext(), selectedFile);

            switch (requestCode) {
                case PICK_FILE_BINARY: {
                    Intent intent = new Intent(getContext(), InstallService.class);
                    intent.setAction("unzip");
                    intent.putExtra("file", path);
                    intent.putExtra("targetLocation", PRIVATE_DIR);
                    intent.putExtra("receiver", new ProgressReceiver(getContext(), new Handler()));
                    getActivity().startService(intent);
                    break;
                }
                case PICK_FILE_SERVER: {
                    Intent intent = new Intent(getContext(), InstallService.class);
                    intent.setAction("unzip");
                    intent.putExtra("file", path);
                    intent.putExtra("targetLocation", preferences.getString("cuberiteLocation", ""));
                    intent.putExtra("receiver", new ProgressReceiver(getContext(), new Handler()));
                    getActivity().startService(intent);
                    break;
                }
            }
        }
    }

    private void installSelectFileOnButtonClick(Button button, final int code) {
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (code == PICK_FILE_BINARY &&
                    ControlFragment.isServiceRunning(CuberiteService.class, getContext())) {
                    Snackbar.make(getActivity().findViewById(R.id.fragment_container), getString(R.string.status_update_binary_error), Snackbar.LENGTH_LONG).show();
                    return;
                }
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("*/*");
                try {
                    startActivityForResult(intent, code);
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(getContext(), getString(R.string.status_missing_filemanager), Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void updateOnButtonClick(final Context context, Button button, final State state) {
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (state == State.NEED_DOWNLOAD_BINARY &&
                        ControlFragment.isServiceRunning(CuberiteService.class, getContext())) {
                    Snackbar.make(getActivity().findViewById(R.id.fragment_container), getString(R.string.status_update_binary_error), Snackbar.LENGTH_LONG).show();
                    return;
                }
                Intent intent = new Intent(context, InstallService.class);
                intent.setAction("install");
                intent.putExtra("downloadHost", preferences.getString("downloadHost", ""));
                intent.putExtra("state", state.toString());
                intent.putExtra("executableName", preferences.getString("executableName", ""));
                intent.putExtra("targetDirectory", preferences.getString("cuberiteLocation", ""));
                intent.putExtra("receiver", new ProgressReceiver(context, new Handler()));

                LocalBroadcastManager.getInstance(context).registerReceiver(new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        LocalBroadcastManager.getInstance(context).unregisterReceiver(this);
                        String error = intent.getStringExtra("error");
                        if(error != null) {
                            Snackbar.make(getActivity().findViewById(R.id.fragment_container), getString(R.string.status_download_error) + " " + error, Snackbar.LENGTH_LONG).show();
                        }
                    }
                }, new IntentFilter("InstallService.callback"));
                getActivity().startService(intent);
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
                        intent.putExtra("receiver", new ProgressReceiver(context, new Handler()));

                        LocalBroadcastManager.getInstance(context).registerReceiver(new BroadcastReceiver() {
                            @Override
                            public void onReceive(Context context, Intent intent) {
                                LocalBroadcastManager.getInstance(context).unregisterReceiver(this);
                                String error = intent.getStringExtra("error");
                                if(error != null) {
                                    Snackbar.make(getActivity().findViewById(R.id.fragment_container), getString(R.string.status_download_error) + " " + error, Snackbar.LENGTH_LONG).show();
                                }
                            }
                        }, new IntentFilter("InstallService.callback"));
                        getActivity().startService(intent);
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
