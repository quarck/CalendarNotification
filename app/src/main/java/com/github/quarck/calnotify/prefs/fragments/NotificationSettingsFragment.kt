package com.github.quarck.calnotify.prefs.fragments

import android.content.SharedPreferences
import android.os.Bundle
import android.preference.PreferenceFragment
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.utils.vibratorService

class NotificationSettingsFragment: PreferenceFragment(), SharedPreferences.OnSharedPreferenceChangeListener {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.notification_preferences)
    }

    @Suppress("DEPRECATION")
    override fun onResume() {
        super.onResume();
        preferenceScreen.sharedPreferences.registerOnSharedPreferenceChangeListener(this);
    }

    @Suppress("DEPRECATION")
    override fun onPause() {
        super.onPause();
        preferenceScreen.sharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        if (key != null && key == Settings.VIBRATION_PATTERN_KEY) {
            val newPattern = Settings(activity).vibrationPattern
            activity.vibratorService.vibrate(newPattern, -1);
        }
    }
}
