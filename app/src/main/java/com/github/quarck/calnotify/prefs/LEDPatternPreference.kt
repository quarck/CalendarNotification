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
import android.graphics.drawable.ColorDrawable
import android.preference.DialogPreference
import android.util.AttributeSet
import android.view.View
import android.view.ViewParent
import android.widget.*
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.logs.Logger
import com.github.quarck.calnotify.utils.find

class LEDPatternPreference(context: Context, attrs: AttributeSet)
: DialogPreference(context, attrs), SeekBar.OnSeekBarChangeListener {

    internal var ledOnTime = 0
    internal var ledOffTime = 0

    lateinit var onTimeSeeker: SeekBar
    lateinit var offTimeSeeker: SeekBar

    lateinit var onTimeText: TextView
    lateinit var offTimeText: TextView

    lateinit var millisecondsString: String

    init {
        dialogLayoutResource = R.layout.dialog_led_pattern

        setPositiveButtonText(android.R.string.ok)
        setNegativeButtonText(android.R.string.cancel)

        dialogIcon = null
    }

    override fun onBindDialogView(view: View) {
        super.onBindDialogView(view)

        onTimeSeeker = view.find<SeekBar>(R.id.seek_bar_led_on_time)
        offTimeSeeker = view.find<SeekBar>(R.id.seek_bar_led_off_time)

        onTimeText = view.find<TextView>(R.id.text_view_led_on_time_value)
        offTimeText = view.find<TextView>(R.id.text_view_led_off_time_value)

        millisecondsString = view.resources.getString(R.string.milliseconds_suffix)

        parseSetting(this.getPersistedString(Consts.DEFAULT_LED_PATTERN))

        updateTexts()

        onTimeSeeker.progress =
            onTimeSeeker.max *
                (ledOnTime - Consts.LED_MIN_DURATION) / (Consts.LED_MAX_DURATION - Consts.LED_MIN_DURATION)

        offTimeSeeker.progress =
            offTimeSeeker.max *
                (ledOffTime - Consts.LED_MIN_DURATION) / (Consts.LED_MAX_DURATION - Consts.LED_MIN_DURATION)

        onTimeSeeker.setOnSeekBarChangeListener(this)
        offTimeSeeker.setOnSeekBarChangeListener(this)
    }

    private fun updateTexts() {
        onTimeText.text = "$ledOnTime $millisecondsString"
        offTimeText.text = "$ledOffTime $millisecondsString"
    }

    override fun onStopTrackingTouch(seekBar: SeekBar?) {
    }

    override fun onStartTrackingTouch(seekBar: SeekBar?) {
    }

    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {

        when (seekBar) {
            onTimeSeeker ->
                ledOnTime = (Consts.LED_MAX_DURATION - Consts.LED_MIN_DURATION) *
                    progress / onTimeSeeker.max + Consts.LED_MIN_DURATION

            offTimeSeeker ->
                ledOffTime = (Consts.LED_MAX_DURATION - Consts.LED_MIN_DURATION) *
                    progress / offTimeSeeker.max + Consts.LED_MIN_DURATION
        }

        ledOnTime = Math.round(ledOnTime.toFloat() / Consts.LED_DURATION_GRANULARITY).toInt() * Consts.LED_DURATION_GRANULARITY
        ledOffTime = Math.round(ledOffTime.toFloat() / Consts.LED_DURATION_GRANULARITY).toInt() * Consts.LED_DURATION_GRANULARITY

        updateTexts()
    }

    private fun capDuration(duration: Int) =
        when {
            duration < Consts.LED_MIN_DURATION ->
                Consts.LED_MIN_DURATION

            duration > Consts.LED_MAX_DURATION ->
                Consts.LED_MAX_DURATION

            else ->
                duration
        }

    override fun onDialogClosed(positiveResult: Boolean) {
        if (positiveResult) {
            ledOnTime = capDuration(ledOnTime)
            ledOffTime = capDuration(ledOffTime)
            persistString("$ledOnTime,$ledOffTime")
        }
    }

    private fun parseSetting(settingValue: String) {
        val (on, off) = settingValue.split(",")
        ledOnTime = on.toInt()
        ledOffTime = off.toInt()
    }

    override fun onSetInitialValue(restorePersistedValue: Boolean, defaultValue: Any?) {

        if (restorePersistedValue) {
            // Restore existing state
            parseSetting(this.getPersistedString(Consts.DEFAULT_LED_PATTERN))
        } else if (defaultValue != null && defaultValue is String) {
            // Set default state from the XML attribute
            parseSetting(defaultValue)
        }
   }

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any {
        return a.getString(index)
    }

    companion object {
        var logger = Logger("LEDPatternPreference");
    }
}
