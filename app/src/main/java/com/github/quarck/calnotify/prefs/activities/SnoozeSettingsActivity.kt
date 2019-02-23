//
//   Calendar Notifications Plus
//   Copyright (C) 2018 Sergey Parshin (s.parshin.sc@gmail.com)
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

package com.github.quarck.calnotify.prefs.activities

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.preference.PreferenceActivity
import android.preference.PreferenceFragment
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.widget.Switch
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.utils.findOrThrow
import android.content.DialogInterface
import android.view.LayoutInflater
import android.widget.EditText
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.prefs.PreferenceUtils
import com.github.quarck.calnotify.prefs.preferences


class SnoozeSettingsActivity : AppCompatActivity() {

    lateinit var settings: Settings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);

        settings = Settings(this)

        preferences(this) {

            item(R.string.snooze_presets_title,
                    R.string.snooze_presets_summary) {
                onCreateSnoozePresetsDialog().show()
            }

            switch (R.string.always_use_extenal_editor, R.string.always_use_external_editor_summary) {
                initial (settings.alwaysUseExternalEditor)
                onChange { settings.alwaysUseExternalEditor = it }
            }

            switch(R.string.view_after_edit, R.string.view_after_edit_summary) {
                initial(settings.viewAfterEdit)
                onChange { settings.viewAfterEdit = it }
            }

        }
    }

    private fun onNewValue(newText: String) {
        val presets = PreferenceUtils.parseSnoozePresets(newText)
        if (presets != null) {
            if (presets.size == 0) {
                settings.snoozePresetsRaw = Settings.DEFAULT_SNOOZE_PRESET
            }
            else {
                settings.snoozePresetsRaw =
                        newText.split(',')
                                .map { it.trim() }
                                .filter { !it.isEmpty() }
                                .joinToString(", ")
            }

            if (presets.size > Consts.MAX_SUPPORTED_PRESETS) {
                showMessage(R.string.error_too_many_presets)
            }
        }
        else {
            showMessage(R.string.error_cannot_parse_preset)
        }
    }

    private fun showMessage(id: Int) {
        val builder = AlertDialog.Builder(this)
        builder
                .setMessage(this.getString(id))
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok) { _, _ -> }

        builder.create().show()
    }

    private fun onCreateSnoozePresetsDialog(): Dialog {
        val builder = AlertDialog.Builder(this)
        // Get the layout inflater
        val inflater = this.layoutInflater

        // Inflate and set the layout for the dialog
        // Pass null as the parent view because its going in the dialog layout
        val rootView: View = inflater.inflate(R.layout.dialog_snooze_presets, null)

        val editText = rootView.findOrThrow<EditText>(R.id.edit_text_snooze_presets)
        editText.setText(settings.snoozePresetsRaw)

        builder.setView(rootView)
                .setPositiveButton(R.string.ok, DialogInterface.OnClickListener {
                    _, _ ->
                    onNewValue(editText.text.toString())
                })
                .setNegativeButton(R.string.cancel, DialogInterface.OnClickListener {
                    _, _ ->
                })
        return builder.create()
    }
}

