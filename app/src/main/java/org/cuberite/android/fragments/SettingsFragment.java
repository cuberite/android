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

import androidx.appcompat.app.AppCompatDelegate;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreference;

import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import org.cuberite.android.BuildConfig;
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

public class SettingsFragment extends PreferenceFragmentCompat {
    private final static int PICK_FILE_BINARY = 1;
    private final static int PICK_FILE_SERVER = 2;

    private SharedPreferences preferences;

    @Override
    public void onCreatePreferences(Bundle bundle, String s) {
        addPreferencesFromResource(R.xml.preferences);

        // Ini4j config
        Config config = Config.getGlobal();
        config.setEscape(false);
        config.setStrictOperator(true);

        preferences = getActivity().getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE);

        // Initialize theme picker and update the state of it
        ListPreference theme = findPreference("theme");
        theme.setDialogTitle(getString(R.string.settings_theme_choose));
        theme.setEntries(new CharSequence[]{
                getString(R.string.settings_theme_light),
                getString(R.string.settings_theme_dark),
                getString(R.string.settings_theme_auto)
        });
        theme.setEntryValues(new CharSequence[]{"light", "dark", "auto"});

        int getCurrentTheme = preferences.getInt("defaultTheme", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);

        if (getCurrentTheme == AppCompatDelegate.MODE_NIGHT_NO) {
            theme.setValue("light");
        } else if (getCurrentTheme == AppCompatDelegate.MODE_NIGHT_YES) {
            theme.setValue("dark");
        } else {
            theme.setValue("auto");
        }

        theme.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                int theme;
                if (newValue.equals("light")) {
                    theme = AppCompatDelegate.MODE_NIGHT_NO;
                } else if (newValue.equals("dark")) {
                    theme = AppCompatDelegate.MODE_NIGHT_YES;
                } else {
                    theme = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
                }
                AppCompatDelegate.setDefaultNightMode(theme);
                SharedPreferences.Editor editor = preferences.edit();
                editor.putInt("defaultTheme", theme);
                editor.apply();
                return true;
            }
        });

        // Webadmin
        Preference webadminOpen = findPreference("webadminOpen");
        webadminOpen.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                if (!ControlFragment.isServiceRunning(CuberiteService.class, getContext())) {
                    Snackbar.make(getActivity().findViewById(R.id.fragment_container), getString(R.string.settings_webadmin_not_running), Snackbar.LENGTH_LONG).show();
                } else {
                    try {
                        File cuberiteDir = new File(preferences.getString("cuberiteLocation", ""));
                        final File webadminFile = new File(cuberiteDir.getAbsolutePath() + "/webadmin.ini");

                        Ini ini;
                        if (!webadminFile.exists()) {
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
                        Snackbar.make(getActivity().findViewById(R.id.fragment_container), getString(R.string.settings_webadmin_error), Snackbar.LENGTH_LONG).show();
                    }
                }
                return true;
            }
        });

        Preference webadminLogin = findPreference("webadminLogin");
        webadminLogin.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
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
                        Snackbar.make(getActivity().findViewById(R.id.fragment_container), getString(R.string.settings_webadmin_error), Snackbar.LENGTH_LONG).show();
                    }
                }
                return true;
            }
        });

        // Install options
        Preference updateBinary = findPreference("installUpdateBinary");
        updateOnButtonClick(getContext(), updateBinary, State.NEED_DOWNLOAD_BINARY);

        Preference updateServer = findPreference("installUpdateServer");
        updateOnButtonClick(getContext(), updateServer, State.NEED_DOWNLOAD_SERVER);

        Preference installNoSha = findPreference("installNoShaButton");
        installNoSha.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                if (ControlFragment.isServiceRunning(CuberiteService.class, getContext())) {
                    Snackbar.make(getActivity().findViewById(R.id.fragment_container), getString(R.string.status_update_binary_error), Snackbar.LENGTH_LONG).show();
                } else {
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
                return true;
            }
        });

        String abi = String.format(getString(R.string.settings_install_manually_abi), InstallService.getPreferredABI());
        Preference setABIText = findPreference("abiText");
        setABIText.setSummary(setABIText.getSummary() + "\n\n" + abi);

        Preference installBinary = findPreference("installBinary");
        installSelectFileOnButtonClick(installBinary, PICK_FILE_BINARY);

        Preference installServer = findPreference("installServer");
        installSelectFileOnButtonClick(installServer, PICK_FILE_SERVER);

        // Authentication
        final SwitchPreference toggleAuthentication = findPreference("troubleshootingAuthenticationToggle");
        toggleAuthentication.setShouldDisableView(false);
        toggleAuthentication.setEnabled(true);

        final File cuberiteDir = new File(preferences.getString("cuberiteLocation", ""));
        final File settingsFile = new File(cuberiteDir.getAbsolutePath() + "/settings.ini");
        int enabled = 0;

        try {
            Ini ini = new Ini(settingsFile);
            enabled = Integer.parseInt(ini.get("Authentication", "Authenticate"));

            if (enabled == 1) {
                toggleAuthentication.setChecked(true);
            } else {
                toggleAuthentication.setChecked(false);
            }
        } catch(IOException e) {
            Log.e(Tags.SETTINGS_ACTIVITY, "Settings.ini doesn't exist, disabling authentication toggle");
            toggleAuthentication.setShouldDisableView(true);
            toggleAuthentication.setEnabled(false);
        }

        toggleAuthentication.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                try {
                    Ini ini = new Ini(settingsFile);
                    int enabled = Integer.parseInt(ini.get("Authentication", "Authenticate"));
                    int newenabled = enabled ^ 1; // XOR: 0 ^ 1 => 1, 1 ^ 1 => 0
                    ini.put("Authentication", "Authenticate", newenabled);
                    ini.store(settingsFile);
                    Snackbar.make(getActivity().findViewById(R.id.fragment_container), String.format(getString(R.string.settings_authentication_toggle_success), getString((newenabled == 1 ? R.string.enabled : R.string.disabled))), Snackbar.LENGTH_LONG).show();
                } catch(IOException e) {
                    Log.e(Tags.SETTINGS_ACTIVITY, "Something went wrong while opening the ini file", e);
                    Snackbar.make(getActivity().findViewById(R.id.fragment_container), getString(R.string.settings_authentication_toggle_error), Snackbar.LENGTH_LONG).show();
                }
                return true;
            }
        });

        // Info
        Preference infoDebugInfo = findPreference("infoDebugInfo");
        infoDebugInfo.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(getContext());
                dialogBuilder.setTitle(getString(R.string.settings_info_debug));
                String message = "Running on Android " + Build.VERSION.RELEASE + " (API Level " + Build.VERSION.SDK_INT + ")\n" +
                        "Using ABI " + InstallService.getPreferredABI() + "\n" +
                        "Private directory: " + PRIVATE_DIR + "\n" +
                        "Public directory: " + PUBLIC_DIR + "\n" +
                        "Storage location: " + preferences.getString("cuberiteLocation", "") + "\n" +
                        "Will download from: " + preferences.getString("downloadHost", "");
                dialogBuilder.setMessage(message);
                dialogBuilder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        if (dialog != null) {
                            dialog.dismiss();
                        }
                    }
                });

                AlertDialog dialog = dialogBuilder.create();
                dialog.show();
                return true;
            }
        });

        Preference thirdPartyLicenses = findPreference("thirdPartyLicenses");
        thirdPartyLicenses.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(getContext());
                dialogBuilder.setTitle(getString(R.string.settings_info_libraries));
                String message = getString(R.string.settings_info_libraries_explanation) + "\n\n" +
                        getString(R.string.ini4j_license) + "\n\n" +
                        getString(R.string.ini4j_license_description) + "\n\n" +
                        getString(R.string.pathutils_license) + "\n\n" +
                        getString(R.string.pathutils_license_description);
                dialogBuilder.setMessage(message);
                dialogBuilder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        if (dialog != null) {
                            dialog.dismiss();
                        }
                    }
                });

                AlertDialog dialog = dialogBuilder.create();
                dialog.show();
                return true;
            }
        });

        Preference version = findPreference("version");
        version.setTitle(String.format(getString(R.string.settings_info_version), BuildConfig.VERSION_NAME));
        version.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/cuberite/android/releases/latest"));
                startActivity(browserIntent);
                return true;
            }
        });
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

    private void installSelectFileOnButtonClick(Preference preference, final int code) {
        preference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                if (code == PICK_FILE_BINARY &&
                    ControlFragment.isServiceRunning(CuberiteService.class, getContext())) {
                    Snackbar.make(getActivity().findViewById(R.id.fragment_container), getString(R.string.status_update_binary_error), Snackbar.LENGTH_LONG).show();
                } else {
                    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.setType("*/*");
                    try {
                        startActivityForResult(intent, code);
                    } catch (ActivityNotFoundException e) {
                        Toast.makeText(getContext(), getString(R.string.status_missing_filemanager), Toast.LENGTH_LONG).show();
                    }
                }
                return true;
            }
        });
    }

    private void updateOnButtonClick(final Context context, Preference preference, final State state) {
        preference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                if (state == State.NEED_DOWNLOAD_BINARY &&
                        ControlFragment.isServiceRunning(CuberiteService.class, getContext())) {
                    Snackbar.make(getActivity().findViewById(R.id.fragment_container), getString(R.string.status_update_binary_error), Snackbar.LENGTH_LONG).show();
                } else {
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
                            if (error != null) {
                                Snackbar.make(getActivity().findViewById(R.id.fragment_container), getString(R.string.status_download_error) + " " + error, Snackbar.LENGTH_LONG).show();
                            }
                        }
                    }, new IntentFilter("InstallService.callback"));
                    getActivity().startService(intent);
                }
                return true;
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
