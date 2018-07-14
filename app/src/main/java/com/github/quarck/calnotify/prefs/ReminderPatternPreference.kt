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
import android.widget.*
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.Settings
//import com.github.quarck.calnotify.logs.Logger
import com.github.quarck.calnotify.ui.TimeIntervalPickerController
import com.github.quarck.calnotify.utils.find
import com.github.quarck.calnotify.utils.findOrThrow

class ReminderPatternPreference(val context: Context, val settings: Settings, val inflater: LayoutInflater)
    : AdapterView.OnItemSelectedListener
{
    val SecondsIndex = 0
    val MinutesIndex = 1
    val HoursIndex = 2
    val DaysIndex = 3

    //internal var timeValueSeconds = 0
    internal var reminderPatternMillis = settings.remindersIntervalMillisPattern
    internal var simpleIntervalMode = true

    internal lateinit var view: View
    internal var maxIntervalMilliseconds = 0L
    internal lateinit var numberPicker: NumberPicker
    internal lateinit var timeUnitsSpinners: Spinner
    internal lateinit var checkboxCustomPattern: CheckBox
    internal lateinit var editTextCustomPattern: EditText
    internal lateinit var layoutSimpleInterval: LinearLayout
    internal lateinit var layoutCustomPattern: LinearLayout

    fun create(): Dialog {

        val builder = AlertDialog.Builder(context)

        // Inflate and set the layout for the dialog
        // Pass null as the parent view because its going in the dialog layout
        val rootView: View = inflater.inflate(R.layout.dialog_reminder_interval_configuration, null)

        onBindDialogView(rootView)

        builder.setView(rootView)

        // Add action buttons
        builder.setPositiveButton(android.R.string.ok, DialogInterface.OnClickListener {
            _, _ ->
            onDialogClosed(true)
            // sign in the user ...
        })

        builder.setNegativeButton(android.R.string.cancel, DialogInterface.OnClickListener {
            _, _ ->
            onDialogClosed(false)
            //this@LoginDialogFragment.getDialog().cancel()
        })

        return builder.create()
    }

    fun onBindDialogView(view: View) {

        numberPicker = view.findOrThrow<NumberPicker>(R.id.numberPickerTimeInterval)
        timeUnitsSpinners = view.findOrThrow<Spinner>(R.id.spinnerTimeIntervalUnit)
        checkboxCustomPattern = view.findOrThrow<CheckBox>(R.id.checkbox_custom_reminder_pattern)
        editTextCustomPattern = view.findOrThrow<EditText>(R.id.edittext_custom_reminder_pattern)

        layoutSimpleInterval = view.findOrThrow<LinearLayout>(R.id.layout_reminder_interval_simple)
        layoutCustomPattern = view.findOrThrow<LinearLayout>(R.id.layout_reminder_interval_custom)

        timeUnitsSpinners.adapter =
                ArrayAdapter(
                        view.context,
                        android.R.layout.simple_list_item_1,
                        view.context.resources.getStringArray(R.array.time_units_plurals_with_seconds)
                )

        timeUnitsSpinners.onItemSelectedListener = this

        timeUnitsSpinners.setSelection(MinutesIndex)

        timeUnitsSpinners.onItemSelectedListener = this

        numberPicker.minValue = 1
        numberPicker.maxValue = 100

        simpleIntervalMode = reminderPatternMillis.size == 1
        checkboxCustomPattern.isChecked = !simpleIntervalMode
        updateLayout()

        checkboxCustomPattern.setOnClickListener {
            _ ->
            simpleIntervalMode = !checkboxCustomPattern.isChecked
            updateLayout()
        }
    }

    private fun updateLayout() {

        if (simpleIntervalMode) {
            simpleIntervalSeconds = (reminderPatternMillis[0] / 1000L).toInt()

            layoutSimpleInterval.visibility = View.VISIBLE
            layoutCustomPattern.visibility = View.GONE
        }
        else {
            editTextCustomPattern.setText(PreferenceUtils.formatPattern(reminderPatternMillis))

            layoutSimpleInterval.visibility = View.GONE
            layoutCustomPattern.visibility = View.VISIBLE
        }
    }

    fun clearFocus() {
        numberPicker.clearFocus()
        timeUnitsSpinners.clearFocus()
    }

    fun onDialogClosed(positiveResult: Boolean) {

        if (positiveResult) {
            clearFocus()

            if (simpleIntervalMode) {
                var simpleIntervalMillis = simpleIntervalSeconds * 1000L

                if (simpleIntervalMillis == 0L) {
                    simpleIntervalMillis = 60 * 1000L
                    val msg = context.resources.getString(R.string.invalid_reminder_interval)
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
                }

                reminderPatternMillis = longArrayOf(simpleIntervalMillis)

                settings.remindersIntervalMillisPattern = reminderPatternMillis
            }
            else {
                val text = editTextCustomPattern.text.toString()
                val pattern = PreferenceUtils.parseSnoozePresets(text)

                if (pattern != null && pattern.size >= 1) {
                    reminderPatternMillis =
                            pattern.map { Math.max(it, Consts.MIN_REMINDER_INTERVAL_SECONDS*1000L) }
                                    .toLongArray()
                    settings.remindersIntervalMillisPattern = reminderPatternMillis// some kind of 'cleanup' from user input
                }
                else {
                    Toast.makeText(context, R.string.error_cannot_parse_pattern, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {

        if (maxIntervalMilliseconds == 0L) {
            if (position == SecondsIndex) {
                numberPicker.minValue = Consts.MIN_REMINDER_INTERVAL_SECONDS
                numberPicker.maxValue = 60
            } else {
                numberPicker.minValue = 1
                numberPicker.maxValue = 100
            }
            return
        }

        val maxValue =
                when (timeUnitsSpinners.selectedItemPosition) {
                    SecondsIndex ->
                        maxIntervalMilliseconds / 1000L
                    MinutesIndex ->
                        maxIntervalMilliseconds / Consts.MINUTE_IN_MILLISECONDS
                    HoursIndex ->
                        maxIntervalMilliseconds / Consts.HOUR_IN_MILLISECONDS
                    DaysIndex ->
                        maxIntervalMilliseconds / Consts.DAY_IN_MILLISECONDS
                    else ->
                        throw Exception("Unknown time unit")
                }

        numberPicker.maxValue = Math.min(maxValue.toInt(), 100)
    }

    override fun onNothingSelected(parent: AdapterView<*>) {

    }


    private var simpleIntervalSeconds: Int
        get() {
            clearFocus()

            val number = numberPicker.value

            val multiplier =
                    when (timeUnitsSpinners.selectedItemPosition) {
                        SecondsIndex ->
                            1
                        MinutesIndex ->
                            60
                        HoursIndex ->
                            60 * 60
                        DaysIndex ->
                            24 * 60 * 60
                        else ->
                            throw Exception("Unknown time unit")
                    }

            return (number * multiplier).toInt()
        }
        set(value) {

            var number = value
            var units = SecondsIndex

            if ((number % 60) == 0) {
                units = MinutesIndex
                number /= 60 // to minutes
            }

            if ((number % 60) == 0) {
                units = HoursIndex
                number /= 60 // to hours
            }

            if ((number % 24) == 0) {
                units = DaysIndex
                number /= 24 // to days
            }

            timeUnitsSpinners.setSelection(units)
            numberPicker.value = number.toInt()
        }

    companion object {
        private const val LOG_TAG = "TimePickerPreference"
    }
}