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
import android.widget.TextView
import android.widget.TimePicker
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.Settings
//import com.github.quarck.calnotify.logs.Logger
import com.github.quarck.calnotify.utils.find
import java.text.DateFormat
import java.util.*

class TimeOfDayPreference(context: Context, attrs: AttributeSet) : DialogPreference(context, attrs) {

    internal var timeValue = Pair(0, 0) // hr, min

    // UI representation
    internal lateinit var picker: TimePicker

    internal var isTwentyFourHour: Boolean = true

    internal var widgetView: TextView? = null

    init {
        val settings = Settings(getContext())

        dialogLayoutResource =
                if (settings.haloLightTimePicker)
                    R.layout.dialog_time_of_day_halo_light
                else
                    R.layout.dialog_time_of_day

        widgetLayoutResource = R.layout.dialog_time_of_day_widget

        setPositiveButtonText(android.R.string.ok)
        setNegativeButtonText(android.R.string.cancel)

        dialogIcon = null
        isTwentyFourHour = android.text.format.DateFormat.is24HourFormat(context)//  context.is24HoursClock()
    }

    override fun onBindView(view: View) {
        super.onBindView(view)

        widgetView = view.find<TextView?>(R.id.dialog_time_of_day_widget)

        updateWidgetView()
    }

    @Suppress("DEPRECATION")
    override fun onBindDialogView(view: View) {
        super.onBindDialogView(view)

        picker = view.find<TimePicker>(R.id.time_picker_pref_time_of_day)

        picker.setIs24HourView(isTwentyFourHour)
        picker.currentHour = timeValue.component1()
        picker.currentMinute = timeValue.component2()

        updateWidgetView()
    }

    override fun onClick() {
        super.onClick()
        picker.clearFocus()
    }

    @Suppress("DEPRECATION")
    override fun onDialogClosed(positiveResult: Boolean) {

        // When the user selects "OK", persist the new value
        if (positiveResult) {
            picker.clearFocus()

            timeValue = Pair(picker.currentHour, picker.currentMinute)
            persistInt(PreferenceUtils.packTime(timeValue))
            updateWidgetView()
        }
    }

    @Suppress("DEPRECATION")
    fun formatTime(time: Pair<Int, Int>): String {
        val timeFormatter = DateFormat.getTimeInstance(DateFormat.SHORT)
        val date = Date(1, 1, 1, time.component1(), time.component2(), 0)
        return timeFormatter.format(date)
    }

    fun updateWidgetView() {

        val wv = widgetView
        if (wv != null)
            wv.text = formatTime(timeValue)
    }

    override fun onSetInitialValue(restorePersistedValue: Boolean, defaultValue: Any?) {

        var timeValueInt: Int = 0

        if (restorePersistedValue) {
            // Restore existing state
            timeValueInt = this.getPersistedInt(0)

        }
        else if (defaultValue != null && defaultValue is Int) {
            // Set default state from the XML attribute
            timeValueInt = defaultValue
            persistInt(defaultValue)
        }

        timeValue = PreferenceUtils.unpackTime(timeValueInt)
    }

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any {
        return a.getInteger(index, 0)
    }

    companion object {
        private const val LOG_TAG = "TimeOfDayPreference"
    }
}
