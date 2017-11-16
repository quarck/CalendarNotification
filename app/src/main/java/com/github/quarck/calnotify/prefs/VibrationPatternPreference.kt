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


package com.github.quarck.calnotify.prefs

import android.app.AlertDialog
import android.content.Context
import android.content.res.TypedArray
import android.preference.DialogPreference
import android.util.AttributeSet
import android.view.View
import android.widget.EditText
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.R.id
import com.github.quarck.calnotify.R.layout
import com.github.quarck.calnotify.utils.find
import com.github.quarck.calnotify.utils.findOrThrow
import com.github.quarck.calnotify.utils.vibratorService


open class VibrationPatternPreference(internal var context: Context, attrs: AttributeSet)
    : DialogPreference(context, attrs) {
    internal var patternValue: String? = null

    internal lateinit var edit: EditText

    init {
        dialogLayoutResource = layout.dialog_vibration_pattern
        setPositiveButtonText(android.R.string.ok)
        setNegativeButtonText(android.R.string.cancel)
        dialogIcon = null
    }

    open fun onPatternSelected(newPattern: LongArray) {
        context.vibratorService.vibrate(newPattern, 1);
    }

    override fun onBindDialogView(view: View) {
        super.onBindDialogView(view)

        edit = view.findOrThrow<EditText>(id.editTextVibrationPattern)
        edit.setText(patternValue)
    }

    override fun onDialogClosed(positiveResult: Boolean) {

        if (positiveResult) {
            val value = edit.text.toString()

            val pattern = PreferenceUtils.parsePattern(value)

            if (pattern != null) {
                patternValue = value
                persistString(patternValue)

                onPatternSelected(pattern)
            }
            else {
                onInvalidPattern()
            }
        }
    }

    fun onInvalidPattern() {
        val builder = AlertDialog.Builder(context)
        builder
                .setMessage(context.getString(R.string.error_cannot_parse_pattern))
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok) { _, _ -> }

        builder.create().show()
    }

    override fun onSetInitialValue(restorePersistedValue: Boolean, defaultValue: Any?) {
        if (restorePersistedValue) {
            patternValue = this.getPersistedString("0,1200") // 0,1200 - failback value
        }
        else if (defaultValue != null && defaultValue is String) {
            patternValue = defaultValue
            persistString(patternValue)
        }
    }

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any {
        return a.getString(index)
    }
}
