package org.cuberite.android.fragments;

import android.app.Activity;
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

import org.cuberite.android.BuildConfig;
import org.cuberite.android.MainActivity;
import org.cuberite.android.helpers.ProgressReceiver;
import org.cuberite.android.services.CuberiteService;
import org.cuberite.android.services.InstallService;
import org.cuberite.android.R;
import org.cuberite.android.State;
import org.ini4j.Config;
import org.ini4j.Ini;

import java.io.File;
import java.io.IOException;

import static android.app.Activity.RESULT_OK;
import static android.content.Context.MODE_PRIVATE;
import static androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
import static androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO;
import static androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES;
import static androidx.appcompat.app.AppCompatDelegate.create;
import static org.cuberite.android.MainActivity.PACKAGE_NAME;
import static org.cuberite.android.MainActivity.PRIVATE_DIR;
import static org.cuberite.android.MainActivity.PUBLIC_DIR;

public class SettingsFragment extends PreferenceFragmentCompat {
    // Logging tag
    private String LOG = "Cuberite/Settings";

    public final static int PICK_FILE_BINARY = 1;
    private final static int PICK_FILE_SERVER = 2;

    @Override
    public void onCreatePreferences(Bundle bundle, String s) {
        addPreferencesFromResource(R.xml.preferences);

        final SharedPreferences preferences = getActivity().getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE);
        final File cuberiteDir = new File(preferences.getString("cuberiteLocation", ""));

        // Ini4j config
        Config config = Config.getGlobal();
        config.setEscape(false);
        config.setStrictOperator(true);

        // Initialize theme picker and update the state of it
        ListPreference theme = findPreference("theme");
        theme.setDialogTitle(getString(R.string.settings_theme_choose));
        theme.setEntries(new CharSequence[]{
                getString(R.string.settings_theme_light),
                getString(R.string.settings_theme_dark),
                getString(R.string.settings_theme_auto)
        });
        theme.setEntryValues(new CharSequence[]{"light", "dark", "auto"});

        int getCurrentTheme = preferences.getInt("defaultTheme", MODE_NIGHT_FOLLOW_SYSTEM);

        if (getCurrentTheme == MODE_NIGHT_NO) {
            theme.setValue("light");
        } else if (getCurrentTheme == MODE_NIGHT_YES) {
            theme.setValue("dark");
        } else {
            theme.setValue("auto");
        }

        theme.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                int theme;
                if (newValue.equals("light")) {
                    theme = MODE_NIGHT_NO;
                } else if (newValue.equals("dark")) {
                    theme = MODE_NIGHT_YES;
                } else {
                    theme = MODE_NIGHT_FOLLOW_SYSTEM;
                }
                AppCompatDelegate.setDefaultNightMode(theme);
                SharedPreferences.Editor editor = preferences.edit();
                editor.putInt("defaultTheme", theme);
                editor.apply();
                return true;
            }
        });

        // Webadmin
        final File webadminFile = new File(cuberiteDir.getAbsolutePath() + "/webadmin.ini");

        String url = null;

        if (cuberiteDir.exists()) {
            try {
                final Ini ini = createWebadminIni(webadminFile);
                final String ip = CuberiteService.getIpAddress(getContext());
                int port;
                try {
                    port = Integer.parseInt(ini.get("WebAdmin", "Ports"));
                } catch (NumberFormatException e) {
                    ini.put("WebAdmin", "Ports", 8080);
                    ini.store(webadminFile);
                    port = Integer.parseInt(ini.get("WebAdmin", "Ports"));
                }
                url = "http://" + ip + ":" + port;

                Preference webadminDescription = findPreference("webadminDescription");
                webadminDescription.setSummary(webadminDescription.getSummary() + "\n\n" + "URL: " + url);
            } catch (IOException e) {
                Log.e(LOG, "Something went wrong while opening the ini file", e);
            }
        }

        final String urlInner = url;

        Preference webadminOpen = findPreference("webadminOpen");
        webadminOpen.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                if (!CuberiteService.isCuberiteRunning(getActivity())) {
                    Snackbar.make(getActivity().findViewById(R.id.fragment_container), getString(R.string.settings_webadmin_not_running), Snackbar.LENGTH_LONG)
                            .setAnchorView(MainActivity.navigation)
                            .show();
                } else if (urlInner != null) {
                    Log.d(LOG, "Opening Webadmin on " + urlInner);
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(urlInner));
                    startActivity(browserIntent);
                }
                return true;
            }
        });

        Preference webadminLogin = findPreference("webadminLogin");
        webadminLogin.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                if (!cuberiteDir.exists()) {
                    showWebadminInfoPopup();
                } else {
                    try {
                        final Ini ini = createWebadminIni(webadminFile);
                        ini.put("WebAdmin", "Enabled", 1);

                        showWebadminCredentialPopup(webadminFile, ini);
                    } catch(IOException e) {
                        Log.e(LOG, "Something went wrong while opening the ini file", e);
                        MainActivity.showSnackBar(getActivity(), getString(R.string.settings_webadmin_error));
                    }
                }
                return true;
            }
        });

        // Install options
        Preference updateBinary = findPreference("installUpdateBinary");
        updateBinary.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                String action = "install";
                installCuberiteDownload(getActivity(), action, State.NEED_DOWNLOAD_BINARY);
                return true;
            }
        });

        Preference updateServer = findPreference("installUpdateServer");
        updateServer.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                String action = "install";
                installCuberiteDownload(getActivity(), action, State.NEED_DOWNLOAD_SERVER);
                return true;
            }
        });

        String abi = String.format(getString(R.string.settings_install_manually_abi), InstallService.getPreferredABI());
        Preference setABIText = findPreference("abiText");
        setABIText.setSummary(setABIText.getSummary() + "\n\n" + abi);

        Preference installNoSha = findPreference("installNoShaButton");
        installNoSha.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                String action = "installNoCheck";
                installCuberiteDownload(getActivity(), action, State.NEED_DOWNLOAD_BOTH);
                return true;
            }
        });

        Preference installBinary = findPreference("installBinary");
        installBinary.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                installCuberiteLocal(PICK_FILE_BINARY);
                return true;
            }
        });

        Preference installServer = findPreference("installServer");
        installServer.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                installCuberiteLocal(PICK_FILE_SERVER);
                return true;
            }
        });

        // Authentication
        final SwitchPreference toggleAuthentication = findPreference("troubleshootingAuthenticationToggle");
        final File settingsFile = new File(cuberiteDir.getAbsolutePath() + "/settings.ini");

        updateAuthenticationToggle(settingsFile, toggleAuthentication);

        toggleAuthentication.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                try {
                    final Ini ini = new Ini(settingsFile);
                    int newEnabled;

                    try {
                        final int enabled = Integer.parseInt(ini.get("Authentication", "Authenticate"));
                        newEnabled = enabled ^ 1; // XOR: 0 ^ 1 => 1, 1 ^ 1 => 0
                    } catch (NumberFormatException e) {
                        newEnabled = 1;
                    }

                    boolean newEnabledBool = newEnabled != 0;
                    toggleAuthentication.setChecked(newEnabledBool);

                    ini.put("Authentication", "Authenticate", newEnabled);
                    ini.store(settingsFile);
                    MainActivity.showSnackBar(getActivity(), String.format(getString(R.string.settings_authentication_toggle_success), getString(newEnabled == 1 ? R.string.enabled : R.string.disabled)));
                } catch(IOException e) {
                    Log.e(LOG, "Something went wrong while opening the ini file", e);
                    MainActivity.showSnackBar(getActivity(), getString(R.string.settings_authentication_toggle_error));
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
                        "IP: " + CuberiteService.getIpAddress(getContext()) + "\n" +
                        "Private directory: " + PRIVATE_DIR + "\n" +
                        "Public directory: " + PUBLIC_DIR + "\n" +
                        "Storage location: " + preferences.getString("cuberiteLocation", "") + "\n" +
                        "Download URL: " + preferences.getString("downloadHost", "");
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
                        getString(R.string.ini4j_license_description) + "\n\n";
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
        version.setSummary(String.format(getString(R.string.settings_info_version), BuildConfig.VERSION_NAME));
        version.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://download.cuberite.org/android"));
                startActivity(browserIntent);
                return true;
            }
        });
    }

    public static void installCuberiteDownload(final Activity activity, String action, State state) {
        SharedPreferences preferences = activity.getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE);

        Intent intent = new Intent(activity, InstallService.class);
        intent.setAction(action);
        intent.putExtra("downloadHost", preferences.getString("downloadHost", ""));
        intent.putExtra("state", state.toString());
        intent.putExtra("executableName", preferences.getString("executableName", ""));
        intent.putExtra("targetDirectory", preferences.getString("cuberiteLocation", ""));
        intent.putExtra("receiver", new ProgressReceiver(activity, new Handler()));

        LocalBroadcastManager.getInstance(activity).registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                LocalBroadcastManager.getInstance(context).unregisterReceiver(this);
                String result = intent.getStringExtra("result");
                MainActivity.showSnackBar(activity, result);
            }
        }, new IntentFilter("InstallService.callback"));
        activity.startService(intent);
    }

    private void installCuberiteLocal(int code) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            // Only show file sources that support loading content from Uri
            intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        }

        intent.setType("*/*");
        try {
            startActivityForResult(intent, code);
        } catch (ActivityNotFoundException e) {
            MainActivity.showSnackBar(getActivity(), getString(R.string.status_missing_filemanager));
        }
    }

    private Ini createWebadminIni(final File webadminFile) throws IOException {
        Ini ini;
        if (!webadminFile.exists()) {
            ini = new Ini();
            ini.put("WebAdmin", "Ports", 8080);
            ini.put("WebAdmin", "Enabled", 1);
            ini.store(webadminFile);
        } else {
            ini = new Ini(webadminFile);
        }

        return ini;
    }

    private void showWebadminInfoPopup() {
        final AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setTitle(R.string.settings_webadmin_not_installed_title)
                .setMessage(R.string.settings_webadmin_not_installed)
                .setPositiveButton(R.string.do_install_cuberite, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int i) {
                        String action = "install";
                        installCuberiteDownload(getActivity(), action, State.NEED_DOWNLOAD_BOTH);
                    }
                }).setNegativeButton(R.string.cancel, null)
                .create();
        dialog.show();
    }

    private void showWebadminCredentialPopup(final File webadminFile, final Ini ini) {
        String username = "";
        String password = "";

        for (String sectionName : ini.keySet()) {
            if (sectionName.startsWith("User:")) {
                username = sectionName.substring(5);
                password = ini.get(sectionName, "Password");
            }
        }
        final String oldUsername = username;

        final View layout = getLayoutInflater().inflate(R.layout.dialog_webadmin_credentials, null);
        ((EditText) layout.findViewById(R.id.webadminUsername)).setText(username);
        ((EditText) layout.findViewById(R.id.webadminPassword)).setText(password);

        final AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setView(layout)
                .setTitle(R.string.settings_webadmin_login)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        String newUsername = ((EditText) layout.findViewById(R.id.webadminUsername)).getText().toString();
                        String newPassword = ((EditText) layout.findViewById(R.id.webadminPassword)).getText().toString();

                        ini.remove("User:" + oldUsername);
                        ini.put("User:" + newUsername, "Password", newPassword);

                        try {
                            ini.store(webadminFile);
                            MainActivity.showSnackBar(getActivity(), getString(R.string.settings_webadmin_success));
                        } catch(IOException e) {
                            Log.e(LOG, "Something went wrong while saving the ini file", e);
                            MainActivity.showSnackBar(getActivity(),getString(R.string.settings_webadmin_error));
                        }
                    }
                })
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                })
                .create();
        dialog.show();
    }

    private void updateAuthenticationToggle(File settingsFile, SwitchPreference toggle) {
        try {
            final Ini ini = new Ini(settingsFile);

            try {
                final int enabled = Integer.parseInt(ini.get("Authentication", "Authenticate"));

                if (enabled == 0) {
                    toggle.setChecked(false);
                }
            } catch (NumberFormatException e) {
                ini.put("Authentication", "Authenticate", 1);
                ini.store(settingsFile);
                toggle.setChecked(true);
            }
        } catch(IOException e) {
            Log.e(LOG, "Settings.ini doesn't exist, disabling authentication toggle");
            toggle.setShouldDisableView(true);
            toggle.setEnabled(false);
            toggle.setChecked(true);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        SharedPreferences preferences = getActivity().getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE);

        if (resultCode == RESULT_OK
                && data != null) {
            Uri selectedFileUri = data.getData();

            Intent intent = new Intent(getContext(), InstallService.class);
            intent.setAction("unzip");
            intent.putExtra("uri", selectedFileUri.toString());
            intent.putExtra("state", Integer.toString(requestCode));
            intent.putExtra("targetLocation", preferences.getString("cuberiteLocation", ""));
            intent.putExtra("receiver", new ProgressReceiver(getContext(), new Handler()));

            LocalBroadcastManager.getInstance(getContext()).registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    LocalBroadcastManager.getInstance(context).unregisterReceiver(this);
                    String result = intent.getStringExtra("result");
                    MainActivity.showSnackBar(getActivity(), result);
                }
            }, new IntentFilter("InstallService.callback"));

            getActivity().startService(intent);
        }
    }
}
