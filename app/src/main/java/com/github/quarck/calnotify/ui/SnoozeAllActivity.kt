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

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.support.design.widget.FloatingActionButton
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.text.format.DateUtils
import android.view.View
import android.widget.*
import com.github.quarck.calnotify.app.*
import com.github.quarck.calnotify.calendar.*
import com.github.quarck.calnotify.dismissedeventsstorage.EventDismissType
import com.github.quarck.calnotify.eventsstorage.EventsStorage
//import com.github.quarck.calnotify.logs.Logger
import com.github.quarck.calnotify.maps.MapsIntents
import com.github.quarck.calnotify.quiethours.QuietHoursManager
import com.github.quarck.calnotify.textutils.EventFormatter
import com.github.quarck.calnotify.textutils.EventFormatterInterface
import com.github.quarck.calnotify.utils.*
import java.util.*
import com.github.quarck.calnotify.*
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.permissions.PermissionsManager
import android.content.res.ColorStateList
import android.support.v4.content.ContextCompat
import android.text.method.ScrollingMovementMethod


open class SnoozeAllActivity : AppCompatActivity() {

    var state = ViewEventActivityState()

    lateinit var snoozePresets: LongArray

    lateinit var settings: Settings

    lateinit var formatter: EventFormatterInterface

    val calendarReloadManager: CalendarReloadManagerInterface = CalendarReloadManager
    val calendarProvider: CalendarProviderInterface = CalendarProvider

    var snoozeAllIsChange = false

    var snoozeFromMainActivity = false

    val snoozePresetControlIds = intArrayOf(
            R.id.snooze_view_snooze_present1,
            R.id.snooze_view_snooze_present2,
            R.id.snooze_view_snooze_present3,
            R.id.snooze_view_snooze_present4,
            R.id.snooze_view_snooze_present5,
            R.id.snooze_view_snooze_present6
    )

    val snoozePresentQuietTimeReminderControlIds = intArrayOf(
            R.id.snooze_view_snooze_present1_quiet_time_notice,
            R.id.snooze_view_snooze_present2_quiet_time_notice,
            R.id.snooze_view_snooze_present3_quiet_time_notice,
            R.id.snooze_view_snooze_present4_quiet_time_notice,
            R.id.snooze_view_snooze_present5_quiet_time_notice,
            R.id.snooze_view_snooze_present6_quiet_time_notice
    )

    var baselineIds = intArrayOf(
            R.id.snooze_view_snooze_present1_quiet_time_notice_baseline,
            R.id.snooze_view_snooze_present2_quiet_time_notice_baseline,
            R.id.snooze_view_snooze_present3_quiet_time_notice_baseline,
            R.id.snooze_view_snooze_present4_quiet_time_notice_baseline,
            R.id.snooze_view_snooze_present5_quiet_time_notice_baseline,
            R.id.snooze_view_snooze_present6_quiet_time_notice_baseline
    )

    private val undoManager by lazy { UndoManager }

    // These dialog controls moved here so saveInstanceState could store current time selection
    var customSnooze_TimeIntervalPickerController: TimeIntervalPickerController? = null
    var snoozeUntil_DatePicker: DatePicker? = null
    var snoozeUntil_TimePicker: TimePicker? = null


    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        if (savedInstanceState != null)
            state = ViewEventActivityState.fromBundle(savedInstanceState)

        setContentView(R.layout.activity_snooze_all)

        val currentTime = System.currentTimeMillis()

        settings = Settings(this)
        formatter = EventFormatter(this)

        snoozeAllIsChange = intent.getBooleanExtra(Consts.INTENT_SNOOZE_ALL_IS_CHANGE, false)

        snoozeFromMainActivity = intent.getBooleanExtra(Consts.INTENT_SNOOZE_FROM_MAIN_ACTIVITY, false)

        val toolbar = find<Toolbar?>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)


        // remove "MM minutes before event" snooze presents for "Snooze All"
        // and when event time has passed already
        snoozePresets = settings.snoozePresets.filter { it > 0L }.toLongArray()

        val isQuiet =
                QuietHoursManager(this).isInsideQuietPeriod(
                        settings,
                        snoozePresets.map { it -> currentTime + it }.toLongArray())

        // Populate snooze controls
        for ((idx, id) in snoozePresetControlIds.withIndex()) {
            val snoozeLable = findOrThrow<TextView>(id);
            val quietTimeNotice = findOrThrow<TextView>(snoozePresentQuietTimeReminderControlIds[idx])
            val quietTimeNoticeBaseline = findOrThrow<TextView>(baselineIds[idx])

            if (idx < snoozePresets.size) {
                snoozeLable.text = formatPreset(snoozePresets[idx])
                snoozeLable.visibility = View.VISIBLE;
                quietTimeNoticeBaseline.visibility = View.VISIBLE

                if (isQuiet[idx])
                    quietTimeNotice.visibility = View.VISIBLE
                else
                    quietTimeNotice.visibility = View.GONE
            }
            else {
                snoozeLable.visibility = View.GONE;
                quietTimeNotice.visibility = View.GONE
                quietTimeNoticeBaseline.visibility = View.GONE
            }
        }

        // need to hide these guys
        val showCustomSnoozeVisibility = View.VISIBLE
        findOrThrow<TextView>(R.id.snooze_view_snooze_custom).visibility = showCustomSnoozeVisibility
        val snoozeCustom = find<TextView?>(R.id.snooze_view_snooze_until)
        if (snoozeCustom != null)
            snoozeCustom.visibility = showCustomSnoozeVisibility

        findOrThrow<TextView>(R.id.snooze_snooze_for).text =
                if (!snoozeAllIsChange)
                    this.resources.getString(R.string.snooze_all_events)
                else
                    this.resources.getString(R.string.change_all_events)

        find<ImageView?>(R.id.snooze_view_img_custom_period)?.visibility = View.VISIBLE
        find<ImageView?>(R.id.snooze_view_img_until)?.visibility = View.VISIBLE


        this.title =
                if (!snoozeAllIsChange)
                    resources.getString(R.string.snooze_all_title)
                else
                    resources.getString(R.string.change_all_title)

        restoreState(state)
    }

    private fun formatPreset(preset: Long): String {
        val num: Long
        val unit: String
        val presetSeconds = preset / 1000L;

        if (presetSeconds == 0L)
            return resources.getString(R.string.until_event_time)

        if (presetSeconds % Consts.DAY_IN_SECONDS == 0L) {
            num = presetSeconds / Consts.DAY_IN_SECONDS;
            unit =
                    if (num != 1L)
                        resources.getString(R.string.days)
                    else
                        resources.getString(R.string.day)
        }
        else if (presetSeconds % Consts.HOUR_IN_SECONDS == 0L) {
            num = presetSeconds / Consts.HOUR_IN_SECONDS;
            unit =
                    if (num != 1L)
                        resources.getString(R.string.hours)
                    else
                        resources.getString(R.string.hour)
        }
        else {
            num = presetSeconds / Consts.MINUTE_IN_SECONDS;
            unit =
                    if (num != 1L)
                        resources.getString(R.string.minutes)
                    else
                        resources.getString(R.string.minute)
        }

        if (num <= 0) {
            val beforeEventString = resources.getString(R.string.before_event)
            return "${-num} $unit $beforeEventString"
        }
        return "$num $unit"
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    fun onButtonCancelClick(v: View?) {
        finish();
    }

    private fun snoozeEvent(snoozeDelay: Long) {
        AlertDialog.Builder(this)
                .setMessage(
                        if (snoozeAllIsChange)
                            R.string.change_all_notification
                        else
                            R.string.snooze_all_confirmation)
                .setCancelable(false)
                .setPositiveButton(android.R.string.yes) {
                    _, _ ->

                    DevLog.debug(LOG_TAG, "Snoozing (change=$snoozeAllIsChange) all requests, snoozeDelay=${snoozeDelay / 1000L}")

                    val result = ApplicationController.snoozeAllEvents(this, snoozeDelay, snoozeAllIsChange, false);
                    if (result != null) {
                        result.toast(this)
                    }
                    finish()
                }
                .setNegativeButton(R.string.cancel) {
                    _, _ ->
                }
                .create()
                .show()
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    fun OnButtonSnoozeClick(v: View?) {
        if (v == null)
            return

        for ((idx, id) in snoozePresetControlIds.withIndex()) {
            if (id == v.id) {
                snoozeEvent(snoozePresets[idx]);
                break;
            }
        }
    }

    public override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        when (state.state) {
            ViewEventActivityStateCode.Normal -> {
            }
            ViewEventActivityStateCode.CustomSnoozeOpened -> {
                state.timeAMillis = customSnooze_TimeIntervalPickerController?.intervalMilliseconds ?: 0L
            }
            ViewEventActivityStateCode.SnoozeUntilOpenedDatePicker -> {

                val datePicker = snoozeUntil_DatePicker
                if (datePicker != null) {
                    datePicker.clearFocus()

                    val date = Calendar.getInstance()
                    date.set(datePicker.year, datePicker.month, datePicker.dayOfMonth, 0, 0, 0)
                    state.timeAMillis = date.timeInMillis
                    state.timeBMillis = 0L
                }
            }
            ViewEventActivityStateCode.SnoozeUntilOpenedTimePicker -> {

                val timePicker = snoozeUntil_TimePicker
                if (timePicker != null) {
                    timePicker.clearFocus()

                    val time = Calendar.getInstance()
                    time.timeInMillis = state.timeAMillis
                    time.set(Calendar.HOUR_OF_DAY, timePicker.hour)
                    time.set(Calendar.MINUTE, timePicker.minute)

                    state.timeBMillis = time.timeInMillis
                }
            }
        }
        // val intervalMilliseconds = customSnooze_TimeIntervalPickerController?.intervalMilliseconds ?: 0L
        state.toBundle(outState)
    }

    private fun restoreState(state: ViewEventActivityState) {

        when (state.state) {
            ViewEventActivityStateCode.Normal -> {

            }
            ViewEventActivityStateCode.CustomSnoozeOpened -> {
                customSnoozeShowDialog(state.timeAMillis)
            }
            ViewEventActivityStateCode.SnoozeUntilOpenedDatePicker -> {
                snoozeUntilShowDatePickerDialog(state.timeAMillis, state.timeBMillis)
            }
            ViewEventActivityStateCode.SnoozeUntilOpenedTimePicker -> {
                snoozeUntilShowTimePickerDialog(state.timeAMillis, state.timeBMillis)
            }
        }
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    fun OnButtonCustomSnoozeClick(v: View?) {
        customSnoozeShowSimplifiedDialog(persistentState.lastCustomSnoozeIntervalMillis)
    }

    fun customSnoozeShowSimplifiedDialog(initialTimeValue: Long) {

        val intervalNames: Array<String> = this.resources.getStringArray(R.array.default_snooze_intervals)
        val intervalValues = this.resources.getIntArray(R.array.default_snooze_intervals_seconds_values)

        val builder = AlertDialog.Builder(this)

        val adapter = ArrayAdapter<String>(this, R.layout.simple_list_item_medium)

        adapter.addAll(intervalNames.toMutableList())

        builder.setCancelable(true)

        builder.setAdapter(adapter) {
            _, which ->
            if (which in 0..intervalValues.size-1) {

                val intervalSeconds = intervalValues[which].toLong()
                if (intervalSeconds != -1L) {
                    snoozeEvent(intervalSeconds * 1000L)
                } else {
                    customSnoozeShowDialog(initialTimeValue)
                }
            }
        }

        builder.show()
    }

    fun customSnoozeShowDialog(initialTimeValue: Long) {

        val dialogView = this.layoutInflater.inflate(R.layout.dialog_interval_picker, null);

        val timeIntervalPicker = TimeIntervalPickerController(dialogView, R.string.snooze_for, 0, false)
        timeIntervalPicker.intervalMilliseconds = initialTimeValue

        state.state = ViewEventActivityStateCode.CustomSnoozeOpened
        customSnooze_TimeIntervalPickerController = timeIntervalPicker

        val builder = AlertDialog.Builder(this)

        builder.setView(dialogView)

        builder.setPositiveButton(R.string.snooze) {
            _: DialogInterface?, _: Int ->

            val intervalMilliseconds = timeIntervalPicker.intervalMilliseconds
            this.persistentState.lastCustomSnoozeIntervalMillis = intervalMilliseconds

            snoozeEvent(intervalMilliseconds)

            state.state = ViewEventActivityStateCode.Normal
            customSnooze_TimeIntervalPickerController = null
        }

        builder.setNegativeButton(R.string.cancel) {
            _: DialogInterface?, _: Int ->

            state.state = ViewEventActivityStateCode.Normal
            customSnooze_TimeIntervalPickerController = null
        }

        builder.create().show()
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    fun OnButtonSnoozeUntilClick(v: View?) {
        val currentlySnoozedUntil = 0L
        snoozeUntilShowDatePickerDialog(currentlySnoozedUntil, currentlySnoozedUntil)
    }

    fun inflateDatePickerDialog() = layoutInflater?.inflate(R.layout.dialog_date_picker, null)

    fun inflateTimePickerDialog() = layoutInflater?.inflate(R.layout.dialog_time_picker, null)

    @SuppressLint("NewApi")
    fun snoozeUntilShowDatePickerDialog(initialValueForDate: Long, initialValueForTime: Long) {

        val dialogDate = inflateDatePickerDialog() ?: return

        val datePicker = dialogDate.findOrThrow<DatePicker>(R.id.datePickerCustomSnooze)

        state.state = ViewEventActivityStateCode.SnoozeUntilOpenedDatePicker
        snoozeUntil_DatePicker = datePicker

        val firstDayOfWeek = Settings(this).firstDayOfWeek
        if (firstDayOfWeek != -1)
            snoozeUntil_DatePicker?.firstDayOfWeek = firstDayOfWeek

        if (initialValueForDate != 0L) {
            val cal = Calendar.getInstance()
            cal.timeInMillis = initialValueForDate

            val year = cal.get(Calendar.YEAR)
            val month = cal.get(Calendar.MONTH)
            val day = cal.get(Calendar.DAY_OF_MONTH)

            snoozeUntil_DatePicker?.updateDate(year, month, day)
        }

        val builder = AlertDialog.Builder(this)

        builder.setView(dialogDate)

        builder.setPositiveButton(R.string.next) {
            _: DialogInterface?, _: Int ->

            datePicker.clearFocus()

            val date = Calendar.getInstance()
            date.set(datePicker.year, datePicker.month, datePicker.dayOfMonth, 0, 0, 0)

            snoozeUntilShowTimePickerDialog(date.timeInMillis, initialValueForTime)
        }

        builder.setNegativeButton(R.string.cancel) {
            _: DialogInterface?, _: Int ->

            state.state = ViewEventActivityStateCode.Normal
            snoozeUntil_DatePicker = null
        }

        builder.create().show()

    }

    fun snoozeUntilShowTimePickerDialog(currentDateSelection: Long, initialTimeValue: Long) {

        val date = Calendar.getInstance()
        date.timeInMillis = currentDateSelection

        val dialogTime = inflateTimePickerDialog() ?: return

        val timePicker: TimePicker = dialogTime.findOrThrow<TimePicker>(R.id.timePickerCustomSnooze)
        timePicker.setIs24HourView(android.text.format.DateFormat.is24HourFormat(this))

        state.state = ViewEventActivityStateCode.SnoozeUntilOpenedTimePicker
        state.timeAMillis = currentDateSelection
        snoozeUntil_TimePicker = timePicker
        snoozeUntil_DatePicker = null

        if (initialTimeValue != 0L) {
            val cal = Calendar.getInstance()
            cal.timeInMillis = initialTimeValue

            timePicker.hour = cal.get(Calendar.HOUR_OF_DAY)
            timePicker.minute = cal.get(Calendar.MINUTE)
        }

        val title = dialogTime.findOrThrow<TextView>(R.id.textViewSnoozeUntilDate)
        title.text =
                String.format(
                        resources.getString(R.string.choose_time),
                        DateUtils.formatDateTime(this, date.timeInMillis, DateUtils.FORMAT_SHOW_DATE))

        val builder = AlertDialog.Builder(this)
        builder.setView(dialogTime)
        builder.setPositiveButton(R.string.snooze) {
            _: DialogInterface?, _: Int ->

            state.state = ViewEventActivityStateCode.Normal
            snoozeUntil_TimePicker = null

            timePicker.clearFocus()

            // grab time from timePicker + date picker

            date.set(Calendar.HOUR_OF_DAY, timePicker.hour)
            date.set(Calendar.MINUTE, timePicker.minute)

            val snoozeFor = date.timeInMillis - System.currentTimeMillis() + Consts.ALARM_THRESHOLD

            if (snoozeFor > 0L) {
                snoozeEvent(snoozeFor)
            }
            else {
                // Selected time is in the past
                AlertDialog.Builder(this)
                        .setTitle(R.string.selected_time_is_in_the_past)
                        .setNegativeButton(R.string.cancel) {
                            _: DialogInterface?, _: Int ->
                        }
                        .create()
                        .show()
            }

        }

        builder.setNegativeButton(R.string.cancel) {
            _: DialogInterface?, _: Int ->

            state.state = ViewEventActivityStateCode.Normal
            snoozeUntil_TimePicker = null
        }

        builder.create().show()
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    fun OnButtonRescheduleCustomClick(v: View?) {
    }

    companion object {
        private const val LOG_TAG = "ActivitySnoozeAll"
    }

}
