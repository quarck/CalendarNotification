package com.github.quarck.calnotify.prefs.fragments

import android.content.SharedPreferences
import android.os.Bundle
import android.preference.PreferenceFragment
import android.view.MenuItem
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.utils.vibratorService

class AppearanceSettingsFragment: PreferenceFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.appearance_preferences)
    }
}
