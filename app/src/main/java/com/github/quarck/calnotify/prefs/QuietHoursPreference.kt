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
import android.provider.Settings
import android.util.AttributeSet
import android.view.View
import android.widget.CheckBox
import android.widget.TextView
import android.widget.TimePicker
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.logs.Logger
import com.github.quarck.calnotify.utils.find
import com.github.quarck.calnotify.utils.is24HoursClock
import java.text.DateFormat
import java.util.*

class QuietHoursPreference(context: Context, attrs: AttributeSet) : DialogPreference(context, attrs) {

    // Source of truth
    internal var isEnabled = false
    internal var timeValueFrom = Pair(0, 0) // hr, min
    internal var timeValueTo = Pair(0, 0) // hr, min

    // UI representation
    internal lateinit var pickerFrom: TimePicker
    internal lateinit var pickerTo: TimePicker
    internal lateinit var labelFrom: TextView
    internal lateinit var labelTo: TextView
    internal lateinit var cbEnabled: CheckBox

    internal var isTwentyFourHour: Boolean = true

    init {
        dialogLayoutResource = R.layout.dialog_quiet_hours

        setPositiveButtonText(android.R.string.ok)
        setNegativeButtonText(android.R.string.cancel)

        dialogIcon = null

        isTwentyFourHour = context.is24HoursClock()
    }

    override fun onBindView(view: View) {
        super.onBindView(view)
        updateSummaryView()
    }

    override fun onBindDialogView(view: View) {
        super.onBindDialogView(view)

        pickerFrom = view.find<TimePicker>(R.id.time_picker_quiet_time_from)
        pickerTo = view.find<TimePicker>(R.id.time_picker_quiet_time_to)
        labelFrom = view.find<TextView>(R.id.text_view_quiet_time_from_label)
        labelTo = view.find<TextView>(R.id.text_view_quiet_time_to_label)
        cbEnabled = view.find<CheckBox>(R.id.check_box_enable_quiet_time)

        pickerFrom.setIs24HourView(isTwentyFourHour)
        pickerFrom.currentHour = timeValueFrom.component1()
        pickerFrom.currentMinute = timeValueFrom.component2()

        pickerTo.setIs24HourView(isTwentyFourHour)
        pickerTo.currentHour = timeValueTo.component1()
        pickerTo.currentMinute = timeValueTo.component2()

        updateDialogView();

        view.find<CheckBox>(R.id.check_box_enable_quiet_time).setOnClickListener { v -> onEnableClick(v) }

        updateSummaryView()
    }

    override fun onDialogClosed(positiveResult: Boolean) {

        // When the user selects "OK", persist the new value
        if (positiveResult) {
            timeValueFrom = Pair(pickerFrom.currentHour, pickerFrom.currentMinute)
            timeValueTo = Pair(pickerTo.currentHour, pickerTo.currentMinute)
            isEnabled = cbEnabled.isChecked
            persistInt(PreferenceUtils.packQuietHours(isEnabled, timeValueFrom, timeValueTo))
            updateSummaryView()
        }
    }


    private fun onEnableClick(v: View?) {
        if (v != null && v is CheckBox) {
            isEnabled = v.isChecked
            updateDialogView();
        }
    }

    fun formatTime(time: Pair<Int, Int>): String {
        var timeFormatter = DateFormat.getTimeInstance(DateFormat.SHORT)
        var date = Date(1, 1, 1, time.component1(), time.component2(), 0)
        return timeFormatter.format(date)
    }


    fun updateSummaryView() {
        if (isEnabled)
            this.summary = String.format(
                    context.resources.getString(R.string.quiet_hours_pref_summary_format),
                    formatTime(timeValueFrom),
                    formatTime(timeValueTo))
        else
            this.summary = context.resources.getString(R.string.quiet_hours_pref_disabled)
    }

    fun updateDialogView() {
        cbEnabled.isChecked = isEnabled
        pickerFrom.isEnabled = isEnabled
        pickerTo.isEnabled = isEnabled
        labelFrom.isEnabled = isEnabled
        labelTo.isEnabled = isEnabled
    }

    override fun onSetInitialValue(restorePersistedValue: Boolean, defaultValue: Any?) {

        var timeValue: Int = 0

        if (restorePersistedValue) {
            // Restore existing state
            timeValue = this.getPersistedInt(0)

        } else if (defaultValue != null && defaultValue is Int) {
            // Set default state from the XML attribute
            timeValue = defaultValue
            persistInt(defaultValue)
        }

        isEnabled = PreferenceUtils.unpackQuietHoursIsEnabled(timeValue)
        timeValueFrom = PreferenceUtils.unpackQuietHoursFrom(timeValue)
        timeValueTo = PreferenceUtils.unpackQuietHoursTo(timeValue)
    }

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any {
        return a.getInteger(index, 0)
    }

    companion object {
        var logger = Logger("QuietHoursPreference");
    }
}
