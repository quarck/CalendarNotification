//
//   Calendar Notifications Plus  
//   Copyright (C) 2016 Sergey Parshin (s.parshin.sc@gmail.com)
//
//   This program is free software; you can redistribute it and/or modify
//   it under the terms of the GNU General Public License as published by
//   the Free Software Foundation; either version 3 of the License, or
//   (at your option) any later version.
// 
//   This program is distributed in the hope that it will be useful,
//   but WITHOUT ANY WARRANTY; without even the implied warranty of
//   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//   GNU General Public License for more details.
//
//   You should have received a copy of the GNU General Public License
//   along with this program; if not, write to the Free Software Foundation,
//   Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301  USA
//

package com.github.quarck.calnotify.ui

import android.os.Bundle
import android.preference.PreferenceActivity
import android.preference.PreferenceScreen
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.github.quarck.calnotify.R

class SettingsActivity : PreferenceActivity() {

    @Suppress("DEPRECATION")
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addPreferencesFromResource(R.xml.preferences)

        for (nestedPref in nestedPreferences) {
            val preferenceScreen = findPreference(nestedPref) as PreferenceScreen

            preferenceScreen.setOnPreferenceClickListener({
                preference ->

                preferenceScreen.dialog.actionBar.setDisplayHomeAsUpEnabled(true);

                val dialog = preferenceScreen.dialog;

                val homeBtn = dialog.findViewById(android.R.id.home);
                if (homeBtn != null) {
                    val dismissListener = View.OnClickListener { dialog.dismiss(); }

                    // Prepare yourselves for some hacky programming
                    val homeBtnContainer = homeBtn.parent;

                    // The home button is an ImageView inside a FrameLayout
                    if (homeBtnContainer is FrameLayout) {
                        val containerParent = homeBtnContainer.parent as ViewGroup

                        if (containerParent is LinearLayout) {
                            // This view also contains the title text, set the whole view as clickable
                            containerParent.setOnClickListener(dismissListener);
                        } else {
                            // Just set it on the home button
                            homeBtnContainer.setOnClickListener(dismissListener);
                        }
                    } else {
                        // The 'If all else fails' default case
                        homeBtn.setOnClickListener(dismissListener);
                    }
                }
                true
            })
        }
    }

    companion object {
        val nestedPreferences =
            listOf(
                "pref_key_notification_settings",
                "pref_key_notification_settings",
                "pref_key_snooze_prs",
                "pref_key_reminder",
                "pref_key_quiet_hours",
                "pref_key_smartwatch",
                "pref_key_misc" )
    }
}
