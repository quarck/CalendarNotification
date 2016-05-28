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

package com.github.quarck.calnotify.ui

import android.view.View
import android.widget.NumberPicker
import android.widget.Spinner
import android.widget.TextView
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.utils.find

class TimeIntervalPickerController(val view: View, titleId: Int?) {

    lateinit var numberPicker: NumberPicker
    lateinit var timeUnitsSpinners: Spinner

    init {
        numberPicker = view.find<NumberPicker>(R.id.numberPickerTimeInterval)
        timeUnitsSpinners = view.find<Spinner>(R.id.spinnerTimeIntervalUnit)

        val label = view.find<TextView>(R.id.textViewTimeIntervalTitle)
        if (titleId != null)
            label.setText(titleId)
        else
            label.visibility = View.GONE

        timeUnitsSpinners.setSelection(MINUTES_ID)

        numberPicker.minValue = 1
        numberPicker.maxValue = 100
    }

    fun clearFocus() {
        numberPicker.clearFocus()
        timeUnitsSpinners.clearFocus()
    }

    var intervalMinutes: Int
        get() {
            clearFocus()

            val number = numberPicker.value

            val multiplier =
                when (timeUnitsSpinners.selectedItemPosition) {
                    MINUTES_ID ->
                        1
                    HOURS_ID ->
                        60
                    DAYS_ID ->
                        24 * 60
                    else ->
                        throw Exception("Unknown time unit")
                }

            return (number * multiplier).toInt()
        }
        set(value) {
            var number = value
            var units = MINUTES_ID

            if ((number % 60) == 0) {
                units = HOURS_ID
                number /= 60 // to hours
            }

            if ((number % 24) == 0) {
                units = DAYS_ID
                number /= 24 // to days
            }

            timeUnitsSpinners.setSelection(units)
            numberPicker.value = number.toInt()

        }

    val intervalMilliseconds: Long
        get() = intervalMinutes * 60L * 1000L

    companion object {
        const val MINUTES_ID = 0
        const val HOURS_ID = 1
        const val DAYS_ID = 2
    }

}