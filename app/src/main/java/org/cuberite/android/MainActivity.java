package org.cuberite.android;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.MenuItem;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.elevation.SurfaceColors;
import com.google.android.material.snackbar.Snackbar;

import org.cuberite.android.fragments.ConsoleFragment;
import org.cuberite.android.fragments.ControlFragment;
import org.cuberite.android.fragments.SettingsFragment;

public class MainActivity extends AppCompatActivity implements BottomNavigationView.OnItemSelectedListener {
    // Logging tag
    private final String LOG = "Cuberite/MainActivity";

    private SharedPreferences preferences;
    private AlertDialog permissionPopup;
    private static BottomNavigationView navigation;

    private String PRIVATE_DIR;
    private String PUBLIC_DIR;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.container);

        if (savedInstanceState == null) {
            loadFragment(new ControlFragment());
        }

        // Set colors
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getWindow().setStatusBarColor(SurfaceColors.SURFACE_0.getColor(this));
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            getWindow().setNavigationBarColor(SurfaceColors.SURFACE_2.getColor(this));
        }

        // Set navigation bar listener
        navigation = findViewById(R.id.bottom_navigation);
        navigation.setOnItemSelectedListener(this);

        // Initialize settings
        preferences = getSharedPreferences(this.getPackageName(), MODE_PRIVATE);

        PRIVATE_DIR = this.getFilesDir().getAbsolutePath();
        PUBLIC_DIR = Environment.getExternalStorageDirectory().getAbsolutePath();
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        Fragment fragment = null;

        if (item.getItemId() == R.id.item_control) {
            fragment = new ControlFragment();
        }
        else if (item.getItemId() == R.id.item_console) {
            fragment = new ConsoleFragment();
        }
        else if (item.getItemId() == R.id.item_settings) {
            fragment = new SettingsFragment();
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

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    Log.i(LOG, "Got permissions, using public directory");
                    preferences.edit().putString("cuberiteLocation", PUBLIC_DIR + "/cuberite-server").apply();
                } else {
                    Log.i(LOG, "Permissions denied, boo, using private directory");
                    preferences.edit().putString("cuberiteLocation", PRIVATE_DIR + "/cuberite-server").apply();
                }
            });

    private void showPermissionPopup() {
        permissionPopup = new MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.status_permissions_needed))
            .setMessage(R.string.message_externalstorage_permission)
            .setCancelable(false)
            .setPositiveButton(R.string.ok, (dialog, id) -> {
                Log.d(LOG, "Requesting permissions for external storage");
                permissionPopup = null;
                requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            })
            .create();

        permissionPopup.show();
    }

    private void checkPermissions() {
        final String location = preferences.getString("cuberiteLocation", "");
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            // User is running Android 6 or above, show permission popup on first run
            // or if user granted permission and later denied it

            if (location.isEmpty() || location.startsWith(PUBLIC_DIR)) {
                showPermissionPopup();
            }
        } else if (location.isEmpty() || location.startsWith(PRIVATE_DIR)) {
            SharedPreferences.Editor editor = preferences.edit();
            editor.putString("cuberiteLocation", PUBLIC_DIR + "/cuberite-server");
            editor.apply();
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
