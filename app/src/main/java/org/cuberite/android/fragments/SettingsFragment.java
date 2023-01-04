package org.cuberite.android.fragments;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.EditText;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
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
        initializeSDCardSettings(preferences);
        initializeWebadminSettings(cuberiteDir);
        initializeInstallSettings();
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

        theme.setOnPreferenceChangeListener((preference, newValue) -> {
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
        });
    }


    // Startup-related methods

    private void initializeStartupSettings(final SharedPreferences preferences) {
        final SwitchPreferenceCompat startupToggle = findPreference("startupToggle");
        startupToggle.setOnPreferenceChangeListener((preference, newValue) -> {
            SharedPreferences.Editor editor = preferences.edit();
            editor.putBoolean("startOnBoot", (boolean) newValue);
            editor.apply();
            return true;
        });
    }


    // SD Card-related methods

    private void initializeSDCardSettings(final SharedPreferences preferences) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT
                && requireContext().getExternalFilesDirs(null).length > 1
                && requireContext().getExternalFilesDirs(null)[1] != null) {
            final String PUBLIC_DIR = Environment.getExternalStorageDirectory().getAbsolutePath();
            final SwitchPreferenceCompat toggleSD = findPreference("saveToSDToggle");

            Log.d(LOG, "SD Card found, showing preference");
            toggleSD.setVisible(true);

            if (preferences.getString("cuberiteLocation", "").startsWith(PUBLIC_DIR)) {
                toggleSD.setChecked(false);
            }

            toggleSD.setOnPreferenceChangeListener((preference, newValue) -> {
                if (CuberiteHelper.isCuberiteRunning(requireContext())) {
                    MainActivity.showSnackBar(
                            requireContext(),
                            getString(R.string.settings_sd_card_running)
                    );
                    return false;
                }

                String location = PUBLIC_DIR;

                if ((boolean) newValue) {
                    location = requireContext().getExternalFilesDirs(null)[1].getAbsolutePath();  // SD dir
                } else if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    location = requireContext().getFilesDir().getAbsolutePath();  // Private dir
                }

                preferences.edit().putString("cuberiteLocation", location + "/cuberite-server").apply();
                return true;
            });
        }
    }

    private final BroadcastReceiver unmountSD = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final SharedPreferences preferences = context.getSharedPreferences(context.getPackageName(), MODE_PRIVATE);
            String location = Environment.getExternalStorageDirectory().getAbsolutePath();  // Public dir

            if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                location = context.getFilesDir().getAbsolutePath();  // Private dir
            }

            preferences.edit().putString("cuberiteLocation", location + "/cuberite-server").apply();

            final SwitchPreferenceCompat toggleSD = findPreference("saveToSDToggle");
            toggleSD.setChecked(false);
            toggleSD.setVisible(false);
        }
    };


    // Webadmin-related methods

    private void initializeWebadminSettings(final File cuberiteDir) {
        final File webadminFile = new File(cuberiteDir, "webadmin.ini");

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
        webadminOpen.setOnPreferenceClickListener(preference -> {
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
        });

        Preference webadminLogin = findPreference("webadminLogin");
        webadminLogin.setOnPreferenceClickListener(preference -> {
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
                .setPositiveButton(R.string.ok, (dialog12, id) -> {
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
                })
                .setNegativeButton(R.string.cancel, (dialog1, id) -> dialog1.cancel())
                .create();
        dialog.show();
    }


    // Install-related methods

    private void initializeInstallSettings() {
        Preference updateBinary = findPreference("installUpdateBinary");
        updateBinary.setOnPreferenceClickListener(preference -> {
            InstallHelper.installCuberiteDownload(requireActivity(), State.NEED_DOWNLOAD_BINARY);
            return true;
        });

        Preference updateServer = findPreference("installUpdateServer");
        updateServer.setOnPreferenceClickListener(preference -> {
            InstallHelper.installCuberiteDownload(requireActivity(), State.NEED_DOWNLOAD_SERVER);
            return true;
        });

        String abi = String.format(getString(R.string.settings_install_manually_abi), CuberiteHelper.getPreferredABI());
        Preference setABIText = findPreference("abiText");
        setABIText.setSummary(setABIText.getSummary() + "\n\n" + abi);

        Preference installBinary = findPreference("installBinary");
        installBinary.setOnPreferenceClickListener(preference -> {
            pickFile(pickFileBinaryLauncher);
            return true;
        });

        Preference installServer = findPreference("installServer");
        installServer.setOnPreferenceClickListener(preference -> {
            pickFile(pickFileServerLauncher);
            return true;
        });
    }

    private final BroadcastReceiver installServiceCallback = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String result = intent.getStringExtra("result");
            MainActivity.showSnackBar(requireContext(), result);
        }
    };

    private final ActivityResultLauncher<String> pickFileBinaryLauncher =
        registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> InstallHelper.installCuberiteLocal(requireActivity(), State.PICK_FILE_BINARY, uri));

    private final ActivityResultLauncher<String> pickFileServerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> InstallHelper.installCuberiteLocal(requireActivity(), State.PICK_FILE_SERVER, uri));

    private void pickFile(ActivityResultLauncher<String> launcher) {
        try {
            launcher.launch("*/*");
        } catch (ActivityNotFoundException e) {
            MainActivity.showSnackBar(
                    requireContext(),
                    requireContext().getString(R.string.status_missing_filemanager)
            );
        }
    }


    // Info-related methods

    private void initializeInfoSettings(final SharedPreferences preferences) {
        Preference infoDebugInfo = findPreference("infoDebugInfo");
        infoDebugInfo.setOnPreferenceClickListener(preference -> {
            final String title = getString(R.string.settings_info_debug);
            final String message = "Running on Android " + Build.VERSION.RELEASE + " (API Level " + Build.VERSION.SDK_INT + ")\n" +
                    "Using ABI " + CuberiteHelper.getPreferredABI() + "\n" +
                    "IP: " + CuberiteHelper.getIpAddress(requireContext()) + "\n" +
                    "Private directory: " + requireContext().getFilesDir() + "\n" +
                    "Public directory: " + Environment.getExternalStorageDirectory() + "\n" +
                    "Storage location: " + preferences.getString("cuberiteLocation", "") + "\n" +
                    "Download URL: " + InstallHelper.getDownloadHost();
            showInfoPopup(title, message);
            return true;
        });

        Preference thirdPartyLicenses = findPreference("thirdPartyLicenses");
        thirdPartyLicenses.setOnPreferenceClickListener(preference -> {
            final String title = getString(R.string.settings_info_libraries);
            final String message = getString(R.string.ini4j_license) + "\n\n" +
                    getString(R.string.ini4j_license_description);
            showInfoPopup(title, message);
            return true;
        });

        Preference version = findPreference("version");
        version.setSummary(String.format(getString(R.string.settings_info_version), BuildConfig.VERSION_NAME));
        version.setOnPreferenceClickListener(preference -> {
            final Intent browserIntent = new Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://download.cuberite.org/android")
            );
            startActivity(browserIntent);
            return true;
        });
    }

    private void showInfoPopup(String title, String message) {
        final AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.ok, (dialog1, id) -> {
                    if (dialog1 != null) {
                        dialog1.dismiss();
                    }
                })
                .create();
        dialog.show();
    }


    // Listeners

    @Override
    public void onPause() {
        super.onPause();
        requireContext().unregisterReceiver(unmountSD);
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(installServiceCallback);
    }

    @Override
    public void onResume() {
        super.onResume();

        IntentFilter sdIntentFilter = new IntentFilter();
        sdIntentFilter.addAction(Intent.ACTION_MEDIA_REMOVED);
        sdIntentFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        sdIntentFilter.addAction(Intent.ACTION_MEDIA_BAD_REMOVAL);
        sdIntentFilter.addDataScheme("file");

        requireContext().registerReceiver(
                unmountSD,
                sdIntentFilter
        );

        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(
                installServiceCallback,
                new IntentFilter("InstallService.callback")
        );
    }
}
