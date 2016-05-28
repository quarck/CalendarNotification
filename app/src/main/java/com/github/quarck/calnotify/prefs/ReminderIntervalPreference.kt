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
import android.widget.TimePicker
import android.widget.Toast
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.logs.Logger
import com.github.quarck.calnotify.utils.find

class ReminderIntervalPreference(context: Context, attrs: AttributeSet) : DialogPreference(context, attrs) {
    internal var timeValue = 0

    internal lateinit var picker: TimePicker

    init {
        dialogLayoutResource = R.layout.dialog_remind_interval
        setPositiveButtonText(android.R.string.ok)
        setNegativeButtonText(android.R.string.cancel)
        dialogIcon = null
    }

    override fun onBindDialogView(view: View) {
        super.onBindDialogView(view)

        val pkr = view.find<TimePicker?>(R.id.time_picker_remind_interval)
        if (pkr != null) {
            pkr.setIs24HourView(true)
            pkr.currentHour = timeValue / 60
            pkr.currentMinute = timeValue % 60
            picker = pkr
        }
    }

    override fun onClick() {
        super.onClick()
        picker?.clearFocus()
    }

    override fun onDialogClosed(positiveResult: Boolean) {

        if (positiveResult) {

            picker.clearFocus()
            timeValue = picker.currentHour * 60 + picker.currentMinute

            if (timeValue == 0) {
                timeValue = 1
                val msg = context.resources.getString(R.string.invalid_reminder_interval)
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
            }

            persistInt(timeValue)
        }
    }

    override fun onSetInitialValue(restorePersistedValue: Boolean, defaultValue: Any?) {
        if (restorePersistedValue) {
            // Restore existing state
            timeValue = this.getPersistedInt(0)
        } else if (defaultValue != null && defaultValue is Int) {
            // Set default state from the XML attribute
            timeValue = defaultValue
            persistInt(timeValue)
        }
    }

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any {
        return a.getInteger(index, 10)
    }

    companion object {
        val logger = Logger("TimePickerPreference")
    }
}