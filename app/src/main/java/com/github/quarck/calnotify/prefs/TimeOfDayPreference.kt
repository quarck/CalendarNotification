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
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.res.TypedArray
import android.preference.DialogPreference
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.TimePicker
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.Settings
//import com.github.quarck.calnotify.logs.Logger
import com.github.quarck.calnotify.utils.find
import com.github.quarck.calnotify.utils.findOrThrow
import java.text.DateFormat
import java.util.*

// val context: Context, val settings: Settings, var inflater: LayoutInflater

class TimeOfDayPreference(
        val context: Context,
        var inflater:
        LayoutInflater,
        var timeValue: Pair<Int, Int>,
        val onNewValue: (Pair<Int, Int>) -> Unit) {

    // UI representation
    internal lateinit var picker: TimePicker

    internal var isTwentyFourHour: Boolean = true

    fun create(): Dialog {

        val builder = AlertDialog.Builder(context)

        isTwentyFourHour = android.text.format.DateFormat.is24HourFormat(context)//  context.is24HoursClock()

        // Inflate and set the layout for the dialog
        // Pass null as the parent view because its going in the dialog layout
        val rootView: View = inflater.inflate(R.layout.dialog_time_of_day, null)

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
        picker = view.findOrThrow<TimePicker>(R.id.time_picker_pref_time_of_day)

        picker.setIs24HourView(isTwentyFourHour)
        picker.hour = timeValue.component1()
        picker.minute = timeValue.component2()
    }

//    override fun onClick() {
//        super.onClick()
//        picker.clearFocus()
//    }

    fun onDialogClosed(positiveResult: Boolean) {

        // When the user selects "OK", persist the new value
        if (positiveResult) {
            picker.clearFocus()
            timeValue = Pair(picker.hour, picker.minute)
            onNewValue(timeValue)
        }
    }

    companion object {
        private const val LOG_TAG = "TimeOfDayPreference"
    }
}
