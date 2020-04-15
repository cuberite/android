package org.cuberite.android;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.snackbar.Snackbar;

import org.cuberite.android.fragments.ConsoleFragment;
import org.cuberite.android.fragments.ControlFragment;
import org.cuberite.android.fragments.SettingsFragment;

public class MainActivity extends AppCompatActivity implements BottomNavigationView.OnNavigationItemSelectedListener {
    // Logging tag
    private final String LOG = "Cuberite/MainActivity";

    private SharedPreferences preferences;
    private AlertDialog permissionPopup;
    private static BottomNavigationView navigation;

    public static String PACKAGE_NAME;
    public static String PRIVATE_DIR;
    public static String PUBLIC_DIR;
    private final int REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION = 1;

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
        PUBLIC_DIR = Environment.getExternalStorageDirectory().getAbsolutePath();

        preferences = getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();

        AppCompatDelegate.setDefaultNightMode(preferences.getInt("defaultTheme", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM));

        editor.putString("downloadHost", "https://download.cuberite.org/androidbinaries/");
        editor.putString("executableName", "Cuberite");
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

    public static void showSnackBar(Context context, String message) {
        Snackbar.make(((Activity) context).findViewById(R.id.fragment_container), message, Snackbar.LENGTH_LONG)
                .setAnchorView(MainActivity.navigation)
                .show();
    }

    private void showPermissionPopup() {
        permissionPopup = new AlertDialog.Builder(this)
            .setTitle(getString(R.string.status_permissions_needed))
            .setMessage(R.string.message_externalstorage_permission)
            .setCancelable(false)
            .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    Log.d(LOG, "Requesting permissions for external storage");
                    permissionPopup = null;
                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION);
                }
            })
            .create();

        permissionPopup.show();
    }

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            // User is running Android 6 or above, show permission popup on first run
            // or if user granted permission and later denied it

            if (!preferences.getString("cuberiteLocation", "").startsWith(PRIVATE_DIR)) {
                showPermissionPopup();
            }
        } else if (!preferences.getString("cuberiteLocation", "").startsWith(PUBLIC_DIR)) {
            preferences.edit().putString("cuberiteLocation", PUBLIC_DIR + "/cuberite-server").apply();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION) {
            // If request is cancelled, the result arrays are empty.
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.i(LOG, "Got permissions, using public directory");
                preferences.edit().putString("cuberiteLocation", PUBLIC_DIR + "/cuberite-server").apply();
            } else {
                Log.i(LOG, "Permissions denied, boo, using private directory");
                preferences.edit().putString("cuberiteLocation", PRIVATE_DIR + "/cuberite-server").apply();
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (permissionPopup != null) {
            permissionPopup.dismiss();
            permissionPopup = null;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        checkPermissions();
    }
}
