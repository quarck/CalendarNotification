//
//   Calendar Notifications Plus
//   Copyright (C) 2017 Sergey Parshin (s.parshin.sc@gmail.com)
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
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.res.TypedArray
import android.preference.DialogPreference
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.ui.TimeIntervalPickerController

class DefaultManualNotificationPreference(
        val context: Context,
        var inflater: LayoutInflater,
        defaultValue: Int,
        val onNewValue: (Int)->Unit
) {

    internal var timeValue = defaultValue

    internal lateinit var picker: TimeIntervalPickerController

    fun create(): Dialog {
        val builder = AlertDialog.Builder(context)

        // Inflate and set the layout for the dialog
        // Pass null as the parent view because its going in the dialog layout
        val rootView: View = inflater.inflate(R.layout.dialog_default_manual_notification, null)

        onBindDialogView(rootView)

        builder.setView(rootView)

        builder.setPositiveButton(android.R.string.ok, DialogInterface.OnClickListener {
            _, _ ->
            onDialogClosed(true)
        })

        builder.setNegativeButton(android.R.string.cancel, DialogInterface.OnClickListener {
            _, _ ->
            onDialogClosed(false)
        })

        return builder.create()

    }


    fun onBindDialogView(view: View) {
        picker = TimeIntervalPickerController(view, null, 0, false)
        picker.intervalMinutes = timeValue
    }

//    override fun onClick() {
//        super.onClick()
//        picker.clearFocus()
//    }

    fun onDialogClosed(positiveResult: Boolean) {

        if (positiveResult) {
            picker.clearFocus()

            timeValue = picker.intervalMinutes
            onNewValue(timeValue)
        }
    }

//    override fun onSetInitialValue(restorePersistedValue: Boolean, defaultValue: Any?) {
//        if (restorePersistedValue) {
//            // Restore existing state
//            timeValue = this.getPersistedInt(0)
//        }
//        else if (defaultValue != null && defaultValue is Int) {
//            // Set default state from the XML attribute
//            timeValue = defaultValue
//            persistInt(timeValue)
//        }
//    }
//
//    override fun onGetDefaultValue(a: TypedArray, index: Int): Any {
//        return a.getInteger(index, 15)
//    }
//
    companion object {
        private const val LOG_TAG = "DefaultManualNotificationPreference"
    }
}