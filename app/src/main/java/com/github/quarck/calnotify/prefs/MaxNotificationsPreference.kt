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

import android.content.Context
import android.content.res.TypedArray
import android.preference.DialogPreference
import android.util.AttributeSet
import android.view.View
import android.widget.*
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.logs.Logger
import com.github.quarck.calnotify.ui.TimeIntervalPickerController
import com.github.quarck.calnotify.utils.find
import com.github.quarck.calnotify.utils.toIntOrNull

class MaxNotificationsPreference(context: Context, attrs: AttributeSet) : DialogPreference(context, attrs) {

    internal var value = 0

    internal lateinit var picker: NumberPickerController

    init {
        dialogLayoutResource = R.layout.dialog_max_reminders
        setPositiveButtonText(android.R.string.ok)
        setNegativeButtonText(android.R.string.cancel)
        dialogIcon = null
    }

    override fun onBindDialogView(view: View) {
        super.onBindDialogView(view)

        view.find<TextView?>(R.id.textViewTimeIntervalSummary)?.text = ""

        picker = NumberPickerController(view, 1, Consts.MAX_NOTIFICATIONS)
        picker.value = value
    }

    override fun onClick() {
        super.onClick()
        picker.clearFocus()
    }

    override fun onDialogClosed(positiveResult: Boolean) {

        if (positiveResult) {
            picker.clearFocus()
            value = picker.value
            persistInt(value)
        }
    }

    override fun onSetInitialValue(restorePersistedValue: Boolean, defaultValue: Any?) {
        if (restorePersistedValue) {
            // Restore existing state
            value = this.getPersistedInt(Consts.DEFAULT_NOTIFICATIONS)
        } else if (defaultValue != null && defaultValue is Int) {
            // Set default state from the XML attribute
            value = defaultValue
            persistInt(value)
        }
    }

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any {
        return a.getInt(index, Consts.DEFAULT_NOTIFICATIONS)
    }

    companion object {
        val logger = Logger("MaxRemindersPreference")
    }
}