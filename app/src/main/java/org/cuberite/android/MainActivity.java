package org.cuberite.android;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.snackbar.Snackbar;

import org.cuberite.android.fragments.ConsoleFragment;
import org.cuberite.android.fragments.ControlFragment;
import org.cuberite.android.fragments.SettingsFragment;

public class MainActivity extends AppCompatActivity implements BottomNavigationView.OnNavigationItemSelectedListener {
    // Logging tag
    private String LOG = "Cuberite/MainActivity";

    private SharedPreferences preferences;
    private AlertDialog permissionPopup;
    public static BottomNavigationView navigation;

    public static String PACKAGE_NAME;
    public static String PRIVATE_DIR;
    public static String PUBLIC_DIR;
    public static String SD_DIR;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.container);

        if (savedInstanceState == null) {
            loadFragment(new ControlFragment());
        }

        // Set navigation bar listener
        navigation = findViewById(R.id.bottom_navigation);
        navigation.setOnNavigationItemSelectedListener(this);

        // Initialize settings
        // PACKAGE_NAME: org.cuberite.android
        // PRIVATE_DIR: /data/0/org.cuberite.android/files
        // On most devices
        PACKAGE_NAME = this.getPackageName();
        PRIVATE_DIR = this.getFilesDir().getAbsolutePath();
        PUBLIC_DIR = getExternalFilesDir(null).getAbsolutePath();
        SD_DIR = PUBLIC_DIR;

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT
                && getExternalFilesDirs(null)[1] != null) {
                SD_DIR = getExternalFilesDirs(null)[1].getAbsolutePath();
            }
        } catch (ArrayIndexOutOfBoundsException ignored) {
        }

        preferences = getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();

        if (!preferences.getString("cuberiteLocation", "").equals(PUBLIC_DIR + "/server")
                && !preferences.getString("cuberiteLocation", "").equals(SD_DIR + "/server")) {
            preferences.edit().putString("cuberiteLocation", PUBLIC_DIR + "/server").apply();
        }

        AppCompatDelegate.setDefaultNightMode(preferences.getInt("defaultTheme", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM));

        editor.putString("executableName", "Cuberite");
        editor.putString("downloadHost", "https://builds.cuberite.org/job/cuberite/job/master/job/android/job/release/lastSuccessfulBuild/artifact/android/Server/");
        editor.apply();
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        Fragment fragment = null;

        switch (item.getItemId()) {
            case R.id.item_control:
                fragment = new ControlFragment();
                break;
            case R.id.item_console:
                fragment = new ConsoleFragment();
                break;
            case R.id.item_settings:
                fragment = new SettingsFragment();
                break;
        }

        return loadFragment(fragment);
    }

    private boolean loadFragment(Fragment fragment) {
        if (fragment != null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .commit();
            return true;
        }
        return false;
    }

    public static void showSnackBar(Activity activity, String message) {
        Snackbar.make(activity.findViewById(R.id.fragment_container), message, Snackbar.LENGTH_LONG)
                .setAnchorView(MainActivity.navigation)
                .show();
    }

    private BroadcastReceiver checkSD = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action.equals(Intent.ACTION_MEDIA_REMOVED)
                    || action.equals(Intent.ACTION_MEDIA_UNMOUNTED)
                    || action.equals(Intent.ACTION_MEDIA_BAD_REMOVAL)) {
                if (!preferences.getString("cuberiteLocation", "").startsWith(PUBLIC_DIR)) {
                    preferences.edit().putString("cuberiteLocation", PUBLIC_DIR + "/server").apply();
                }
            }
        }
    };

    @Override
    public void onPause() {
        super.onPause();
        if (permissionPopup != null) {
            permissionPopup.dismiss();
            permissionPopup = null;
        }
        unregisterReceiver(checkSD);
    }

    @Override
    public void onResume() {
        super.onResume();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_MEDIA_REMOVED);
        intentFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        intentFilter.addAction(Intent.ACTION_MEDIA_BAD_REMOVAL);
        intentFilter.addDataScheme("file");
        registerReceiver(checkSD, intentFilter);
    }
}
