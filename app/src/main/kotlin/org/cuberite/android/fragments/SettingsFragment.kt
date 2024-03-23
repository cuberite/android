package org.cuberite.android.fragments

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.widget.EditText
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import org.cuberite.android.BuildConfig
import org.cuberite.android.R
import org.cuberite.android.helpers.CuberiteHelper
import org.cuberite.android.helpers.InstallHelper
import org.cuberite.android.helpers.StateHelper
import org.cuberite.android.services.InstallService
import org.ini4j.Config
import org.ini4j.Ini
import java.io.File
import java.io.IOException

class SettingsFragment : PreferenceFragmentCompat() {
    // Logging tag
    private val log = "Cuberite/Settings"

    override fun onCreatePreferences(bundle: Bundle?, s: String?) {
        addPreferencesFromResource(R.xml.preferences)
        val preferences = requireContext().getSharedPreferences(requireContext().packageName, Context.MODE_PRIVATE)

        // Ini4j config
        val config = Config.getGlobal()
        config.isEscape = false
        config.isStrictOperator = true

        // Initialize
        initializeThemeSettings(preferences)
        initializeStartupSettings(preferences)
        initializeSDCardSettings(preferences)
        initializeWebadminSettings(preferences)
        initializeInstallSettings()
        initializeInfoSettings(preferences)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        InstallService.endedLiveData.observe(viewLifecycleOwner) { result ->
            if (result == null) {
                return@observe
            }
            Snackbar.make(requireActivity().findViewById(R.id.fragment_container), result, Snackbar.LENGTH_LONG)
                .setAnchorView(requireActivity().findViewById(R.id.bottom_navigation))
                .show()
            InstallService.endedLiveData.postValue(null)
        }
    }

    // Theme-related methods
    private fun initializeThemeSettings(preferences: SharedPreferences) {
        val getCurrentTheme = preferences.getInt("defaultTheme", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        val theme = findPreference<ListPreference>("theme")
        theme!!.dialogTitle = getString(R.string.settings_theme_choose)
        theme.entries = arrayOf<CharSequence>(
                getString(R.string.settings_theme_light),
                getString(R.string.settings_theme_dark),
                getString(R.string.settings_theme_auto)
        )
        theme.entryValues = arrayOf<CharSequence>("light", "dark", "auto")
        when (getCurrentTheme) {
            AppCompatDelegate.MODE_NIGHT_NO -> theme.setValue("light")
            AppCompatDelegate.MODE_NIGHT_YES -> theme.setValue("dark")
            else -> theme.setValue("auto")
        }
        theme.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any ->
            val newTheme = when (newValue.toString()) {
                "light" -> AppCompatDelegate.MODE_NIGHT_NO
                "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            AppCompatDelegate.setDefaultNightMode(newTheme)
            val editor = preferences.edit()
            editor.putInt("defaultTheme", newTheme)
            editor.apply()
            true
        }
    }

    // Startup-related methods
    private fun initializeStartupSettings(preferences: SharedPreferences) {
        val startupToggle = findPreference<SwitchPreferenceCompat>("startupToggle")
        startupToggle!!.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any ->
            val editor = preferences.edit()
            editor.putBoolean("startOnBoot", newValue as Boolean)
            editor.apply()
            true
        }
    }

    // SD Card-related methods
    private fun initializeSDCardSettings(preferences: SharedPreferences) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            return
        }
        val privateDir = requireActivity().filesDir.absolutePath
        val publicDir = Environment.getExternalStorageDirectory().absolutePath
        val location = preferences.getString("cuberiteLocation", "")
        val isSDAvailable = (requireContext().getExternalFilesDirs(null).size > 1)
        val isSDEnabled = !(location!!.startsWith(publicDir) || location.startsWith(privateDir))
        val toggleSD = findPreference<SwitchPreferenceCompat>("saveToSDToggle")
        if (!(isSDAvailable || isSDEnabled)) {
            return
        }
        Log.d(log, "SD Card found or location set, showing preference")
        toggleSD!!.isVisible = true
        toggleSD.setChecked(isSDEnabled)
        toggleSD.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any ->
            if (CuberiteHelper.isCuberiteRunning(requireContext())) {
                val message = getString(R.string.settings_sd_card_running)
                Snackbar.make(requireActivity().findViewById(R.id.fragment_container), message, Snackbar.LENGTH_LONG)
                    .setAnchorView(requireActivity().findViewById(R.id.bottom_navigation))
                    .show()
                return@OnPreferenceChangeListener false
            }
            val isSDAvailableInner = (requireContext().getExternalFilesDirs(null).size > 1)
            var newLocation = publicDir
            if (newValue as Boolean && isSDAvailableInner) {
                // SD dir
                newLocation = requireContext().getExternalFilesDirs(null)[1].absolutePath
            } else {
                if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    // Private dir
                    newLocation = requireContext().filesDir.absolutePath
                }
                toggleSD.isVisible = isSDAvailableInner
            }
            val editor = preferences.edit()
            editor.putString("cuberiteLocation", "$newLocation/cuberite-server")
            editor.apply()
            true
        }
    }

    // Webadmin-related methods
    private fun initializeWebadminSettings(preferences: SharedPreferences) {
        val webadminFile = getWebadminFile(preferences)
        val url = getWebadminUrl(webadminFile)
        url?.let {
            val webadminDescription = findPreference<Preference>("webadminDescription")
            webadminDescription!!.setSummary("""
    ${webadminDescription.getSummary().toString()}
    
    URL: $url
    """.trimIndent())
        }
        val webadminOpen = findPreference<Preference>("webadminOpen")
        webadminOpen!!.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            if (!CuberiteHelper.isCuberiteRunning(requireContext())) {
                val message = getString(R.string.settings_webadmin_not_running)
                Snackbar.make(requireActivity().findViewById(R.id.fragment_container), message, Snackbar.LENGTH_LONG)
                    .setAnchorView(requireActivity().findViewById(R.id.bottom_navigation))
                    .show()
                return@OnPreferenceClickListener true
            }
            val webadminFileInner = getWebadminFile(preferences)
            val urlInner = getWebadminUrl(webadminFileInner)
            urlInner?.let {
                Log.d(log, "Opening Webadmin on $urlInner")
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(urlInner))
                startActivity(browserIntent)
            }
            true
        }
        val webadminLogin = findPreference<Preference>("webadminLogin")
        webadminLogin!!.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            try {
                val webadminFileInner = getWebadminFile(preferences)
                val ini = createWebadminIni(webadminFileInner)
                ini.put("WebAdmin", "Enabled", 1)
                showWebadminCredentialPopup(webadminFileInner, ini)
            } catch (e: IOException) {
                Log.e(log, "Something went wrong while opening the ini file", e)
                val message = getString(R.string.settings_webadmin_error)
                Snackbar.make(requireActivity().findViewById(R.id.fragment_container), message, Snackbar.LENGTH_LONG)
                    .setAnchorView(requireActivity().findViewById(R.id.bottom_navigation))
                    .show()
            }
            true
        }
    }

    @Throws(IOException::class)
    private fun createWebadminIni(webadminFile: File): Ini {
        val ini: Ini
        if (!webadminFile.exists()) {
            ini = Ini()
            ini.put("WebAdmin", "Ports", 8080)
            ini.put("WebAdmin", "Enabled", 1)
            ini.store(webadminFile)
        } else {
            ini = Ini(webadminFile)
        }
        return ini
    }

    private fun getWebadminFile(preferences: SharedPreferences): File {
        val cuberiteDir = File(preferences.getString("cuberiteLocation", "")!!)
        return File(cuberiteDir, "webadmin.ini")
    }

    private fun getWebadminUrl(webadminFile: File): String? {
        var url: String? = null
        try {
            val ini = createWebadminIni(webadminFile)
            val ip = CuberiteHelper.getIpAddress()
            val port: Int = try {
                ini["WebAdmin", "Ports"].toInt()
            } catch (e: NumberFormatException) {
                ini.put("WebAdmin", "Ports", 8080)
                ini.store(webadminFile)
                ini["WebAdmin", "Ports"].toInt()
            }
            url = "http://$ip:$port"
        } catch (e: IOException) {
            Log.e(log, "Something went wrong while opening the ini file", e)
        }
        return url
    }

    private fun showWebadminCredentialPopup(webadminFile: File, ini: Ini) {
        var username = ""
        var password = ""
        for (sectionName in ini.keys) {
            if (sectionName.startsWith("User:")) {
                username = sectionName.substring(5)
                password = ini[sectionName, "Password"]
            }
        }
        val oldUsername = username
        val layout = View.inflate(requireContext(), R.layout.dialog_webadmin_credentials, null)
        (layout.findViewById<View>(R.id.webadminUsername) as EditText).setText(username)
        (layout.findViewById<View>(R.id.webadminPassword) as EditText).setText(password)
        val dialog = MaterialAlertDialogBuilder(requireContext())
                .setView(layout)
                .setTitle(R.string.settings_webadmin_login)
                .setPositiveButton(R.string.ok) { _: DialogInterface?, _: Int ->
                    val newUsername = (layout.findViewById<View>(R.id.webadminUsername) as EditText).getText().toString()
                    val newPassword = (layout.findViewById<View>(R.id.webadminPassword) as EditText).getText().toString()
                    ini.remove("User:$oldUsername")
                    ini.put("User:$newUsername", "Password", newPassword)
                    try {
                        ini.store(webadminFile)
                        val message = getString(R.string.settings_webadmin_success)
                        Snackbar.make(requireActivity().findViewById(R.id.fragment_container), message, Snackbar.LENGTH_LONG)
                            .setAnchorView(requireActivity().findViewById(R.id.bottom_navigation))
                            .show()
                    } catch (e: IOException) {
                        Log.e(log, "Something went wrong while saving the ini file", e)
                        val message = getString(R.string.settings_webadmin_error)
                        Snackbar.make(requireActivity().findViewById(R.id.fragment_container), message, Snackbar.LENGTH_LONG)
                            .setAnchorView(requireActivity().findViewById(R.id.bottom_navigation))
                            .show()
                    }
                }
                .setNegativeButton(R.string.cancel) { dialog1: DialogInterface, _: Int -> dialog1.cancel() }
                .create()
        dialog.show()
    }

    // Install-related methods
    private fun initializeInstallSettings() {
        val updateBinary = findPreference<Preference>("installUpdateBinary")
        updateBinary!!.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            InstallHelper.installCuberiteDownload(requireActivity(), StateHelper.State.NEED_DOWNLOAD_BINARY)
            true
        }
        val updateServer = findPreference<Preference>("installUpdateServer")
        updateServer!!.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            InstallHelper.installCuberiteDownload(requireActivity(), StateHelper.State.NEED_DOWNLOAD_SERVER)
            true
        }
        val abi = String.format(getString(R.string.settings_install_manually_abi), CuberiteHelper.preferredABI)
        val setABIText = findPreference<Preference>("abiText")
        setABIText!!.setSummary("""
    ${setABIText.getSummary().toString()}
    
    $abi
    """.trimIndent())
        val installBinary = findPreference<Preference>("installBinary")
        installBinary!!.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            pickFile(pickFileBinaryLauncher)
            true
        }
        val installServer = findPreference<Preference>("installServer")
        installServer!!.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            pickFile(pickFileServerLauncher)
            true
        }
    }

    private val pickFileBinaryLauncher = registerForActivityResult<String, Uri>(ActivityResultContracts.GetContent()) { uri: Uri? -> InstallHelper.installCuberiteLocal(requireActivity(), StateHelper.State.PICK_FILE_BINARY, uri) }
    private val pickFileServerLauncher = registerForActivityResult<String, Uri>(ActivityResultContracts.GetContent()) { uri: Uri? -> InstallHelper.installCuberiteLocal(requireActivity(), StateHelper.State.PICK_FILE_SERVER, uri) }
    private fun pickFile(launcher: ActivityResultLauncher<String>) {
        try {
            launcher.launch("*/*")
        } catch (e: ActivityNotFoundException) {
            val message = getString(R.string.status_missing_filemanager)
            Snackbar.make(requireActivity().findViewById(R.id.fragment_container), message, Snackbar.LENGTH_LONG)
                .setAnchorView(requireActivity().findViewById(R.id.bottom_navigation))
                .show()
        }
    }

    // Info-related methods
    private fun initializeInfoSettings(preferences: SharedPreferences) {
        val infoDebugInfo = findPreference<Preference>("infoDebugInfo")
        infoDebugInfo!!.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            val title = getString(R.string.settings_info_debug)
            val message = """Running on Android ${Build.VERSION.RELEASE} (API Level ${Build.VERSION.SDK_INT})
Using ABI ${CuberiteHelper.preferredABI}
IP: ${CuberiteHelper.getIpAddress()}
Private directory: ${requireContext().filesDir}
Public directory: ${Environment.getExternalStorageDirectory()}
Storage location: ${preferences.getString("cuberiteLocation", "")}
Download URL: ${InstallHelper.DOWNLOAD_HOST}"""
            showInfoPopup(title, message)
            true
        }
        val thirdPartyLicenses = findPreference<Preference>("thirdPartyLicenses")
        thirdPartyLicenses!!.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            val title = getString(R.string.settings_info_libraries)
            val message = """
                ${getString(R.string.ini4j_license)}
                
                ${getString(R.string.ini4j_license_description)}
                """.trimIndent()
            showInfoPopup(title, message)
            true
        }
        val version = findPreference<Preference>("version")
        version!!.setSummary(String.format(getString(R.string.settings_info_version), BuildConfig.VERSION_NAME))
        version.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            val browserIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://download.cuberite.org/android")
            )
            startActivity(browserIntent)
            true
        }
    }

    private fun showInfoPopup(title: String, message: String) {
        MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.ok) { dialog1: DialogInterface?, _: Int -> dialog1?.dismiss() }
                .show()
    }
}
