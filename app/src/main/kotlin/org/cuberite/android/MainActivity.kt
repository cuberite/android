package org.cuberite.android

import android.Manifest
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.MutableLiveData
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.elevation.SurfaceColors
import com.google.android.material.navigation.NavigationBarView
import org.cuberite.android.fragments.ConsoleFragment
import org.cuberite.android.fragments.ControlFragment
import org.cuberite.android.fragments.SettingsFragment

class MainActivity : AppCompatActivity(), NavigationBarView.OnItemSelectedListener {
    private var permissionPopup: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.container)
        if (savedInstanceState == null) {
            loadFragment(ControlFragment())
        }

        // Set colors
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.statusBarColor = SurfaceColors.SURFACE_0.getColor(this)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            window.navigationBarColor = SurfaceColors.SURFACE_2.getColor(this)
        }

        // Set navigation bar listener
        val navigation: BottomNavigationView = findViewById(R.id.bottom_navigation)
        navigation.setOnItemSelectedListener(this)
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        var fragment: Fragment? = null
        when (item.itemId) {
            R.id.item_control -> {
                fragment = ControlFragment()
            }
            R.id.item_console -> {
                fragment = ConsoleFragment()
            }
            R.id.item_settings -> {
                fragment = SettingsFragment()
            }
        }
        return loadFragment(fragment)
    }

    private fun loadFragment(fragment: Fragment?): Boolean {
        if (fragment != null) {
            supportFragmentManager
                    .beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .commit()
            return true
        }
        return false
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
        if (isGranted) {
            Log.i(LOG, "Got permissions, using public directory")
            MainApplication.preferences.edit().putString("cuberiteLocation", "${MainApplication.publicDir}/cuberite-server").apply()
        } else {
            Log.i(LOG, "Permissions denied, boo, using private directory")
            MainApplication.preferences.edit().putString("cuberiteLocation", "${MainApplication.privateDir}/cuberite-server").apply()
        }
    }

    private fun showPermissionPopup() {
        permissionPopup = MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.status_permissions_needed))
                .setMessage(R.string.message_externalstorage_permission)
                .setCancelable(false)
                .setPositiveButton(R.string.ok) { _: DialogInterface?, _: Int ->
                    Log.d(LOG, "Requesting permissions for external storage")
                    permissionPopup = null
                    requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
                .create()
        permissionPopup!!.show()
    }

    private fun checkPermissions() {
        val location = MainApplication.preferences.getString("cuberiteLocation", "")
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            // User is running Android 6 or above, show permission popup on first run
            // or if user granted permission and later denied it
            if (location!!.isEmpty() || location.startsWith(MainApplication.publicDir)) {
                showPermissionPopup()
            }
        } else if (location!!.isEmpty() || location.startsWith(MainApplication.privateDir)) {
            val editor = MainApplication.preferences.edit()
            editor.putString("cuberiteLocation", "${MainApplication.publicDir}/cuberite-server")
            editor.apply()
        }
    }

    override fun onPause() {
        super.onPause()
        if (permissionPopup != null) {
            permissionPopup!!.dismiss()
            permissionPopup = null
        }
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
    }

    companion object {
        private const val LOG = "Cuberite/MainActivity"
        val executeCommandLiveData = MutableLiveData<String?>()
        val killCuberiteLiveData = MutableLiveData<Boolean>()
    }
}
