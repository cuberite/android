package org.cuberite.android;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.color.DynamicColors;

public class MainApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        // Application theme
        final SharedPreferences preferences = getSharedPreferences(this.getPackageName(), MODE_PRIVATE);
        AppCompatDelegate.setDefaultNightMode(preferences.getInt("defaultTheme", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM));
        DynamicColors.applyToActivitiesIfAvailable(this);

        // Notification channel
        createNotificationChannel();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        final String channelId = "cuberiteservice";
        final CharSequence name = getString(R.string.app_name);

        final NotificationChannel channel = new NotificationChannel(
                channelId,
                name,
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setSound(null, null);
        channel.setVibrationPattern(new long[]{0});
        channel.enableVibration(true);

        final NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.createNotificationChannel(channel);
    }
}