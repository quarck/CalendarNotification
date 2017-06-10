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
import android.widget.NumberPicker
import com.github.quarck.calnotify.R
//import com.github.quarck.calnotify.logs.Logger
import com.github.quarck.calnotify.utils.find
import com.github.quarck.calnotify.utils.toIntOrNull

class NumberPickerController(val view: View, val minValue: Int, val maxValue: Int) {

    lateinit var numberPicker: NumberPicker

    init {
        numberPicker = view.find<NumberPicker>(R.id.numberPickerMaxReminders)

        numberPicker.minValue = minValue
        numberPicker.maxValue = maxValue
    }

    fun clearFocus() {
        numberPicker.clearFocus()
    }

    var value: Int
        get() {
            clearFocus()
            return numberPicker.value
        }
        set(value) {
            numberPicker.value = value
        }
}

class MaxRemindersPreference(context: Context, attrs: AttributeSet) : DialogPreference(context, attrs) {

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

        picker = NumberPickerController(view, 0, 999)
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
            persistString("$value") // backward compatibility
        }
    }

    override fun onSetInitialValue(restorePersistedValue: Boolean, defaultValue: Any?) {
        if (restorePersistedValue) {
            // Restore existing state
            value = this.getPersistedString("").toIntOrNull() ?: 0
        }
        else if (defaultValue != null && defaultValue is String) {
            // Set default state from the XML attribute
            value = defaultValue.toIntOrNull() ?: 0
            persistString("$value")
        }
    }

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any {
        return a.getString(index)
    }

    companion object {
        private const val LOG_TAG = "MaxRemindersPreference"
    }
}