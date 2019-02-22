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

package com.github.quarck.calnotify.prefs.components

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.res.TypedArray
import android.preference.DialogPreference
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.RadioButton
import android.widget.TimePicker
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.utils.find
import com.github.quarck.calnotify.utils.findOrThrow

class DefaultManualAllDayNotificationPreference(
        val context: Context,
        var inflater: LayoutInflater,
        defaultValue: Int,
        val onNewValue: (Int)->Unit
) {

    internal var settingValue = defaultValue

    internal lateinit var timePicker: TimePicker
    internal lateinit var radioButtonDayBefore: RadioButton
    internal lateinit var radioButtonDayOfEvent: RadioButton

    internal var isTwentyFourHour: Boolean = true

    fun create(): Dialog {
        val builder = AlertDialog.Builder(context)

        // Inflate and set the layout for the dialog
        // Pass null as the parent view because its going in the dialog layout
        val rootView: View = inflater.inflate(R.layout.dialog_default_manual_all_day_notification, null)

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

        timePicker = view.findOrThrow<TimePicker>(R.id.time_picker_pref_notification_time_of_day)
        radioButtonDayBefore = view.findOrThrow<RadioButton>(R.id.radio_button_day_before)
        radioButtonDayOfEvent = view.findOrThrow<RadioButton>(R.id.radio_button_day_of_event)

        isTwentyFourHour = android.text.format.DateFormat.is24HourFormat(context)//  context.is24HoursClock()
        timePicker.setIs24HourView(isTwentyFourHour)

        if (settingValue < 0) {
            // Reminder on the day before
            val hrMin = Consts.DAY_IN_MINUTES + settingValue
            timePicker.hour = hrMin / 60
            timePicker.minute = hrMin % 60

            radioButtonDayBefore.isChecked = true
            radioButtonDayOfEvent.isChecked = false
        }
        else {
            // Reminder at the day of event
            timePicker.hour = settingValue / 60
            timePicker.minute = settingValue % 60

            radioButtonDayBefore.isChecked = false
            radioButtonDayOfEvent.isChecked = true
        }
    }

//    override fun onClick() {
//        super.onClick()
//        timePicker.clearFocus()
//    }

    fun onDialogClosed(positiveResult: Boolean) {

        if (positiveResult) {
            timePicker.clearFocus()

            val isDayBefore = radioButtonDayBefore.isChecked
            val hrMin = timePicker.hour * 60 + timePicker.minute

            settingValue = if (isDayBefore) hrMin - Consts.DAY_IN_MINUTES else hrMin

            onNewValue(settingValue)
        }
    }

    companion object {
        private const val LOG_TAG = "DefaultManualAllDayNotificationPreference"
    }
}