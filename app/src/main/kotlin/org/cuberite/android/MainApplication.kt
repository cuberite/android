package org.cuberite.android

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Build.VERSION_CODES.TIRAMISU
import android.os.Environment
import android.os.Parcelable
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.color.DynamicColors
import java.io.Serializable

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Initialize settings
        preferences = getSharedPreferences(packageName, MODE_PRIVATE)
        privateDir = filesDir.absolutePath
        publicDir = Environment.getExternalStorageDirectory().absolutePath

        // Application theme
        AppCompatDelegate.setDefaultNightMode(preferences.getInt("defaultTheme", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM))
        DynamicColors.applyToActivitiesIfAvailable(this)

        // Notification channel
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val channelId = "cuberiteservice"
        val name = getString(R.string.app_name)
        val channel = NotificationChannel(
                channelId,
                name,
                NotificationManager.IMPORTANCE_HIGH
        )
        channel.setSound(null, null)
        channel.setVibrationPattern(longArrayOf(0))
        channel.enableVibration(true)
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        lateinit var preferences: SharedPreferences
        lateinit var privateDir: String
        lateinit var publicDir: String
    }
}

inline fun <reified T : Parcelable> Intent.parcelable(key: String): T? = when {
    Build.VERSION.SDK_INT >= TIRAMISU -> getParcelableExtra(key, T::class.java)
    else -> @Suppress("deprecation") getParcelableExtra(key) as? T
}

inline fun <reified T : Serializable> Intent.serializable(key: String): T? = when {
    Build.VERSION.SDK_INT >= TIRAMISU -> getSerializableExtra(key, T::class.java)
    else -> @Suppress("deprecation") getSerializableExtra(key) as? T
}