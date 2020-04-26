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
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import org.cuberite.android.BuildConfig;
import org.cuberite.android.MainActivity;
import org.cuberite.android.R;
import org.cuberite.android.helpers.CuberiteHelper;
import org.cuberite.android.helpers.InstallHelper;
import org.cuberite.android.helpers.StateHelper;
import org.cuberite.android.helpers.StateHelper.State;
import org.ini4j.Config;
import org.ini4j.Ini;

import java.io.File;
import java.io.IOException;

import static android.content.Context.MODE_PRIVATE;
import static androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
import static androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO;
import static androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES;

public class SettingsFragment extends PreferenceFragmentCompat {
    // Logging tag
    private final String LOG = "Cuberite/Settings";

    @Override
    public void onCreatePreferences(Bundle bundle, String s) {
        addPreferencesFromResource(R.xml.preferences);

        final SharedPreferences preferences = requireContext().getSharedPreferences(requireContext().getPackageName(), MODE_PRIVATE);
        final File cuberiteDir = new File(preferences.getString("cuberiteLocation", ""));

        // Ini4j config
        Config config = Config.getGlobal();
        config.setEscape(false);
        config.setStrictOperator(true);

        // Initialize
        initializeThemeSettings(preferences);
        initializeStartupSettings(preferences);
        initializeWebadminSettings(cuberiteDir);
        initializeInstallSettings();
        initializeAuthenticationSettings(cuberiteDir);
        initializeInfoSettings(preferences);
    }



    // Theme-related methods

    private void initializeThemeSettings(final SharedPreferences preferences) {
        int getCurrentTheme = preferences.getInt("defaultTheme", MODE_NIGHT_FOLLOW_SYSTEM);

        ListPreference theme = findPreference("theme");
        theme.setDialogTitle(getString(R.string.settings_theme_choose));
        theme.setEntries(new CharSequence[]{
                getString(R.string.settings_theme_light),
                getString(R.string.settings_theme_dark),
                getString(R.string.settings_theme_auto)
        });
        theme.setEntryValues(new CharSequence[]{"light", "dark", "auto"});

        switch (getCurrentTheme) {
            case MODE_NIGHT_NO:
                theme.setValue("light");
                break;
            case MODE_NIGHT_YES:
                theme.setValue("dark");
                break;
            default:
                theme.setValue("auto");
                break;
        }

        theme.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                int newTheme;

                switch (newValue.toString()) {
                    case "light":
                        newTheme = MODE_NIGHT_NO;
                        break;
                    case "dark":
                        newTheme = MODE_NIGHT_YES;
                        break;
                    default:
                        newTheme = MODE_NIGHT_FOLLOW_SYSTEM;
                        break;
                }

                AppCompatDelegate.setDefaultNightMode(newTheme);
                SharedPreferences.Editor editor = preferences.edit();
                editor.putInt("defaultTheme", newTheme);
                editor.apply();
                return true;
            }
        });
    }


    // Startup-related methods

    private void initializeStartupSettings(final SharedPreferences preferences) {
        final boolean startOnBoot = preferences.getBoolean("startOnBoot", false);

        final SwitchPreferenceCompat startupToggle = findPreference("startupToggle");

        if (!StateHelper.isCuberiteInstalled(requireContext())) {
            startupToggle.setShouldDisableView(true);
            startupToggle.setEnabled(false);
        }

        startupToggle.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                final boolean newStartOnBoot = !startOnBoot;

                SharedPreferences.Editor editor = preferences.edit();
                editor.putBoolean("startOnBoot", newStartOnBoot);
                editor.apply();

                if (newStartOnBoot) {
                    startupToggle.setChecked(true);
                } else {
                    startupToggle.setChecked(false);
                }
                return true;
            }
        });
    }


    // Webadmin-related methods

    private void initializeWebadminSettings(final File cuberiteDir) {
        final File webadminFile = new File(cuberiteDir.getAbsolutePath() + "/webadmin.ini");

        String url = null;

        if (cuberiteDir.exists()) {
            try {
                final Ini ini = createWebadminIni(webadminFile);
                final String ip = CuberiteHelper.getIpAddress(requireContext());
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
                if (!CuberiteHelper.isCuberiteRunning(requireContext())) {
                    MainActivity.showSnackBar(
                            requireContext(),
                            getString(R.string.settings_webadmin_not_running)
                    );
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
                try {
                    final Ini ini = createWebadminIni(webadminFile);
                    ini.put("WebAdmin", "Enabled", 1);

                    showWebadminCredentialPopup(webadminFile, ini);
                } catch(IOException e) {
                    Log.e(LOG, "Something went wrong while opening the ini file", e);
                    MainActivity.showSnackBar(
                            requireContext(),
                            getString(R.string.settings_webadmin_error)
                    );
                }
                return true;
            }
        });
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

        final View layout = View.inflate(requireContext(), R.layout.dialog_webadmin_credentials, null);
        ((EditText) layout.findViewById(R.id.webadminUsername)).setText(username);
        ((EditText) layout.findViewById(R.id.webadminPassword)).setText(password);

        final AlertDialog dialog = new AlertDialog.Builder(requireContext())
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
                            MainActivity.showSnackBar(
                                    requireContext(),
                                    getString(R.string.settings_webadmin_success)
                            );
                        } catch(IOException e) {
                            Log.e(LOG, "Something went wrong while saving the ini file", e);
                            MainActivity.showSnackBar(
                                    requireContext(),
                                    getString(R.string.settings_webadmin_error)
                            );
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


    // Install-related methods

    private void initializeInstallSettings() {
        Preference updateBinary = findPreference("installUpdateBinary");
        updateBinary.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                InstallHelper.installCuberiteDownload(requireActivity(), State.NEED_DOWNLOAD_BINARY);
                return true;
            }
        });

        Preference updateServer = findPreference("installUpdateServer");
        updateServer.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                InstallHelper.installCuberiteDownload(requireActivity(), State.NEED_DOWNLOAD_SERVER);
                return true;
            }
        });

        String abi = String.format(getString(R.string.settings_install_manually_abi), CuberiteHelper.getPreferredABI());
        Preference setABIText = findPreference("abiText");
        setABIText.setSummary(setABIText.getSummary() + "\n\n" + abi);

        Preference installBinary = findPreference("installBinary");
        installBinary.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                pickFile(State.PICK_FILE_BINARY);
                return true;
            }
        });

        Preference installServer = findPreference("installServer");
        installServer.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                pickFile(State.PICK_FILE_SERVER);
                return true;
            }
        });
    }

    private final BroadcastReceiver installServiceCallback = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String result = intent.getStringExtra("result");
            MainActivity.showSnackBar(requireContext(), result);
        }
    };

    private void pickFile(StateHelper.State state) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            // Only show file sources that support loading content from Uri
            intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        }

        intent.setType("*/*");

        try {
            startActivityForResult(intent, state.ordinal());
        } catch (ActivityNotFoundException e) {
            MainActivity.showSnackBar(
                    requireContext(),
                    requireContext().getString(R.string.status_missing_filemanager)
            );
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            InstallHelper.installCuberiteLocal(requireActivity(), StateHelper.State.values()[requestCode], data);
        }
    }


    // Authentication-related methods

    private void initializeAuthenticationSettings(final File cuberiteDir) {
        final SwitchPreferenceCompat toggleAuthentication = findPreference("troubleshootingAuthenticationToggle");
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
                    MainActivity.showSnackBar(
                            requireContext(),
                            String.format(getString(R.string.settings_authentication_toggle_success), getString(newEnabled == 1 ? R.string.enabled : R.string.disabled))
                    );
                } catch (IOException e) {
                    Log.e(LOG, "Something went wrong while opening the ini file", e);
                    MainActivity.showSnackBar(
                            requireContext(),
                            getString(R.string.settings_authentication_toggle_error)
                    );
                }
                return true;
            }
        });
    }

    private void updateAuthenticationToggle(File settingsFile, SwitchPreferenceCompat toggle) {
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


    // Info-related methods

    private void initializeInfoSettings(final SharedPreferences preferences) {
        Preference infoDebugInfo = findPreference("infoDebugInfo");
        infoDebugInfo.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                final String title = getString(R.string.settings_info_debug);
                final String message = "Running on Android " + Build.VERSION.RELEASE + " (API Level " + Build.VERSION.SDK_INT + ")\n" +
                        "Using ABI " + CuberiteHelper.getPreferredABI() + "\n" +
                        "IP: " + CuberiteHelper.getIpAddress(requireContext()) + "\n" +
                        "Private directory: " + requireContext().getFilesDir().getAbsolutePath() + "\n" +
                        "Public directory: " + Environment.getExternalStorageDirectory().getAbsolutePath() + "\n" +
                        "Storage location: " + preferences.getString("cuberiteLocation", "") + "\n" +
                        "Download URL: " + preferences.getString("downloadHost", "");
                showInfoPopup(title, message);
                return true;
            }
        });

        Preference thirdPartyLicenses = findPreference("thirdPartyLicenses");
        thirdPartyLicenses.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                final String title = getString(R.string.settings_info_libraries);
                final String message = getString(R.string.ini4j_license) + "\n\n" +
                        getString(R.string.ini4j_license_description);
                showInfoPopup(title, message);
                return true;
            }
        });

        Preference version = findPreference("version");
        version.setSummary(String.format(getString(R.string.settings_info_version), BuildConfig.VERSION_NAME));
        version.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                final Intent browserIntent = new Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://download.cuberite.org/android")
                );
                startActivity(browserIntent);
                return true;
            }
        });
    }

    private void showInfoPopup(String title, String message) {
        final AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        if (dialog != null) {
                            dialog.dismiss();
                        }
                    }
                })
                .create();
        dialog.show();
    }


    // Listeners

    @Override
    public void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(installServiceCallback);
    }

    @Override
    public void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(
                installServiceCallback,
                new IntentFilter("InstallService.callback")
        );
    }
}
