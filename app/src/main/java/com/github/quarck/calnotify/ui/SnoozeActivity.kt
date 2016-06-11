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

import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.format.DateUtils
import android.view.View
import android.widget.*
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.app.SnoozeResult
import com.github.quarck.calnotify.app.SnoozeType
import com.github.quarck.calnotify.calendar.CalendarIntents
import com.github.quarck.calnotify.calendar.CalendarProvider
import com.github.quarck.calnotify.eventsstorage.EventAlertRecord
import com.github.quarck.calnotify.eventsstorage.EventsStorage
import com.github.quarck.calnotify.eventsstorage.displayedStartTime
import com.github.quarck.calnotify.eventsstorage.formatTime
import com.github.quarck.calnotify.logs.Logger
import com.github.quarck.calnotify.maps.MapsIntents
import com.github.quarck.calnotify.quiethours.QuietHoursManager
import com.github.quarck.calnotify.utils.adjustCalendarColor
import com.github.quarck.calnotify.utils.find
import java.util.*

class SnoozeActivity : Activity() {
    var event: EventAlertRecord? = null

    lateinit var snoozePresets: LongArray

    lateinit var settings: Settings

    var snoozeAllIsChange = false

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

    var baselineIds = intArrayOf (
        R.id.snooze_view_snooze_present1_quiet_time_notice_baseline,
        R.id.snooze_view_snooze_present2_quiet_time_notice_baseline,
        R.id.snooze_view_snooze_present3_quiet_time_notice_baseline,
        R.id.snooze_view_snooze_present4_quiet_time_notice_baseline,
        R.id.snooze_view_snooze_present5_quiet_time_notice_baseline,
        R.id.snooze_view_snooze_present6_quiet_time_notice_baseline
    )

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_snooze)

        val currentTime = System.currentTimeMillis()

        settings = Settings(this)

        // Populate event details
        // val notificationId = intent.getIntExtra(Consts.INTENT_NOTIFICATION_ID_KEY, -1)
        val eventId = intent.getLongExtra(Consts.INTENT_EVENT_ID_KEY, -1)
        val instanceStartTime = intent.getLongExtra(Consts.INTENT_INSTANCE_START_TIME_KEY, -1L)

        snoozeAllIsChange = intent.getBooleanExtra(Consts.INTENT_SNOOZE_ALL_IS_CHANGE, false)

        val isSnoozeAll = (eventId == -1L)

        // load event if it is not a "snooze all"
        //var event: EventRecord? = null
        if (!isSnoozeAll)
            event = EventsStorage(this).use { it.getEvent(eventId, instanceStartTime) }

        val ev = event // so we can check it only once for null

        snoozePresets = settings.snoozePresets

        // remove "MM minutes before event" snooze presents for "Snooze All"
        // and when event time has passed already
        if (isSnoozeAll || ev != null && ev.displayedStartTime < currentTime)
            snoozePresets = snoozePresets.filter{ it > 0L }.toLongArray()

        val isQuiet =
            QuietHoursManager.isInsideQuietPeriod(
                settings,
                snoozePresets.map{ it -> currentTime + it }.toLongArray() )

        // Populate snooze controls
        for ((idx, id) in snoozePresetControlIds.withIndex()) {
            val snoozeLable = find<TextView>(id);
            val quietTimeNotice = find<TextView>(snoozePresentQuietTimeReminderControlIds[idx])
            val quietTimeNoticeBaseline = find<TextView>(baselineIds[idx])

            if (idx < snoozePresets.size) {
                snoozeLable.text = formatPreset(snoozePresets[idx])
                snoozeLable.visibility = View.VISIBLE;
                quietTimeNoticeBaseline.visibility = View.VISIBLE

                if (isQuiet[idx])
                    quietTimeNotice.visibility = View.VISIBLE
                else
                    quietTimeNotice.visibility = View.GONE
            } else {
                snoozeLable.visibility = View.GONE;
                quietTimeNotice.visibility = View.GONE
                quietTimeNoticeBaseline.visibility = View.GONE
            }
        }

        // need to hide these guys
        val showCustomSnoozeVisibility =  if (settings.showCustomSnoozeAndUntil) View.VISIBLE else View.GONE
        find<TextView>(R.id.snooze_view_snooze_custom).visibility = showCustomSnoozeVisibility
        val snoozeCustom = find<TextView?>(R.id.snooze_view_snooze_until)
        if (snoozeCustom != null)
            snoozeCustom.visibility = showCustomSnoozeVisibility

        if (!isSnoozeAll && ev != null) {

            val title =
                    if (ev.title == "")
                        this.resources.getString(R.string.empty_title)
                    else
                        ev.title;

            val (date, time) = ev.formatTime(this);

            val location = ev.location;
            if (location != "") {
                find<View>(R.id.snooze_view_location_layout).visibility = View.VISIBLE;
                val locationView = find<TextView>(R.id.snooze_view_location)
                locationView.text = location;
                locationView.setOnClickListener { MapsIntents.openLocation(this, event?.location ?: "") }
            }

            find<TextView>(R.id.snooze_view_title).text = title;

            val dateView = find<TextView>(R.id.snooze_view_event_date)
            val timeView = find<TextView>(R.id.snooze_view_event_time)

            dateView.text = date;
            timeView.text = time;

            val onClick =
                View.OnClickListener {
                    CalendarIntents.viewCalendarEvent(this, eventId, ev.instanceStartTime, ev.instanceEndTime)
                }

            dateView.setOnClickListener(onClick)
            timeView.setOnClickListener(onClick)


            var color: Int = ev.color.adjustCalendarColor();
            if (color == 0)
                color = resources.getColor(R.color.primary)
            find<RelativeLayout>(R.id.snooze_view_event_details_layout).background = ColorDrawable(color)

            if (!ev.isRepeating)
                find<RelativeLayout>(R.id.snooze_reschedule_layout).visibility = View.VISIBLE

        } else if (isSnoozeAll) {

            find<TextView>(R.id.snooze_view_title).text = this.resources.getString(R.string.all_events);
            find<RelativeLayout>(R.id.layout_snooze_time).visibility = View.GONE
            find<View>(R.id.view_snooze_divider).visibility = View.GONE
            find<TextView>(R.id.snooze_view_event_date).text = ""
            find<TextView>(R.id.snooze_view_event_time).text = ""
            find<TextView>(R.id.snooze_snooze_for).text = resources.getString(R.string.snooze_events_for)
        }
    }

    private fun formatPreset(preset: Long): String {
        val num: Long
        val unit: String
        val presetSeconds = preset / 1000L;

        if (presetSeconds % Consts.DAY_IN_SECONDS == 0L) {
            num = presetSeconds / Consts.DAY_IN_SECONDS;
            unit = if (num != 1L) resources.getString(R.string.days) else resources.getString(R.string.day)
        } else if (presetSeconds % Consts.HOUR_IN_SECONDS == 0L) {
            num = presetSeconds / Consts.HOUR_IN_SECONDS;
            unit = if (num != 1L) resources.getString(R.string.hours) else resources.getString(R.string.hour)
        } else {
            num = presetSeconds / Consts.MINUTE_IN_SECONDS;
            unit = if (num != 1L) resources.getString(R.string.minutes) else resources.getString(R.string.minute)
        }

        if (num < 0) {
            val beforeEventString = resources.getString(R.string.before_event)
            return "${-num} $unit $beforeEventString"
        }

        return "$num $unit"
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    fun onButtonCancelClick(v: View?) {
        finish();
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    fun OnButtonEventDetailsClick(v: View?) {
        val ev = event
        if (ev != null)
            CalendarIntents.viewCalendarEvent(this, ev.eventId, ev.instanceStartTime, ev.instanceEndTime);
    }

    private fun dateToStr(time: Long)
        = DateUtils.formatDateTime(this, time, DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_DATE)

    private fun toastAboutSnoozeResult(res: SnoozeResult) {

        var msg = ""

        if (res.type == SnoozeType.Snoozed) {
            if (res.quietUntil != 0L) {

                val dateTime = dateToStr(res.quietUntil)
                val quietUntilFmt = resources.getString(R.string.snoozed_time_inside_quiet_hours)
                msg = String.format(quietUntilFmt, dateTime)
            } else {

                val dateTime = dateToStr(res.snoozedUntil)
                val snoozedUntil = resources.getString(R.string.snoozed_until_string)
                msg = "$snoozedUntil $dateTime"
            }
        } else if (res.type == SnoozeType.Moved) {
            val dateTime = dateToStr(res.snoozedUntil)
            val snoozedUntil = resources.getString(R.string.moved_to_string)
            msg ="$snoozedUntil $dateTime"
        }

        if (msg != "")
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    private fun snoozeEvent(snoozeDelay: Long) {
        val ev = event
        if (ev != null) {
            logger.debug("Snoozing event id ${ev.eventId}, snoozeDelay=${snoozeDelay / 1000L}")

            val result = ApplicationController.snoozeEvent(this, ev.eventId, ev.instanceStartTime, snoozeDelay);
            if (result != null)
                toastAboutSnoozeResult(result)

            finish();
        } else {
            AlertDialog.Builder(this)
                .setMessage(
                    if (snoozeAllIsChange)
                        R.string.change_all_notification
                    else
                        R.string.snooze_all_confirmation)
                .setCancelable(false)
                .setPositiveButton(android.R.string.yes) {
                    x, y ->

                    logger.debug("Snoozing (change=$snoozeAllIsChange) all events, snoozeDelay=${snoozeDelay / 1000L}")

                    val result = ApplicationController.snoozeAllEvents(this, snoozeDelay, snoozeAllIsChange);
                    if (result != null)
                        toastAboutSnoozeResult(result)
                    finish();
                }
                .setNegativeButton(R.string.cancel) {
                    x, y ->
                }
                .create()
                .show()
        }
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

    @Suppress("unused", "UNUSED_PARAMETER")
    fun OnButtonCustomSnoozeClick(v: View?) {

        val settings = Settings(this)

        val dialogView = this.layoutInflater.inflate(R.layout.dialog_interval_picker, null);

        val dialogController = TimeIntervalPickerController(dialogView, R.string.snooze_for)

        dialogController.intervalMinutes = (settings.lastCustomSnoozeIntervalMillis / Consts.MINUTE_IN_SECONDS / 1000L).toInt()

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton(R.string.snooze) {
                x: DialogInterface?, y: Int ->

                val intervalMilliseconds = dialogController.intervalMilliseconds
                settings.lastCustomSnoozeIntervalMillis = intervalMilliseconds
                snoozeEvent(intervalMilliseconds)
            }
            .setNegativeButton(R.string.cancel) {
                x: DialogInterface?, y: Int ->
            }
            .create()
            .show()
    }

    @Suppress("unused", "UNUSED_PARAMETER", "DEPRECATION")
    fun OnButtonSnoozeUntilClick(v: View?) {

        val dialogView = this.layoutInflater.inflate(R.layout.dialog_date_time_picker, null);

        val timePicker = dialogView.find<TimePicker>(R.id.timePickerCustomSnooze)
        val datePicker = dialogView.find<DatePicker>(R.id.datePickerCustomSnooze)
        timePicker.setIs24HourView(android.text.format.DateFormat.is24HourFormat(this))

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton(R.string.snooze) {
                x: DialogInterface?, y: Int ->

                datePicker.clearFocus()
                timePicker.clearFocus()

                // grab time from timePicker + date picker
                val cal = Calendar.getInstance()
                cal.set(
                    datePicker.year,
                    datePicker.month,
                    datePicker.dayOfMonth,
                    timePicker.currentHour,
                    timePicker.currentMinute,
                    0)

                val snoozeFor = cal.timeInMillis - System.currentTimeMillis() - Consts.ALARM_THRESHOULD

                if (snoozeFor > 0L) {
                    snoozeEvent(snoozeFor)
                } else {
                    // Selected time is in the past
                    AlertDialog.Builder(this)
                        .setTitle(R.string.selected_time_is_in_the_past)
                        .setNegativeButton(R.string.cancel) {
                            x: DialogInterface?, y: Int ->
                        }
                        .create()
                        .show()
                }
            }
            .setNegativeButton(R.string.cancel) {
                x: DialogInterface?, y: Int ->
            }
            .create()
            .show()
    }

    fun reschedule(addTime: Long) {

        val ev = event

        if (ev != null) {

            logger.info("Moving event ${ev.eventId} by ${addTime/1000L} seconds");

            val moved = CalendarProvider.moveEvent(this, ev, addTime)

            if (moved) {

                logger.info("snooze: Moved event ${ev.eventId} by ${addTime/1000L} seconds")
                // Dismiss
                ApplicationController.dismissEvent(this, ev.eventId, ev.instanceStartTime, ev.notificationId, enableUndo = false)

                // Show
                if (Settings(this).viewAfterEdit)
                    CalendarIntents.viewCalendarEvent(this, ev.eventId, 0L, 0L)
                else
                    toastAboutSnoozeResult(SnoozeResult(SnoozeType.Moved, ev.startTime, 0L))

                // terminate ourselves
                finish();
            } else {
                logger.info("snooze: Failed to move event ${ev.eventId} by ${addTime/1000L} seconds")
            }
        }
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    fun OnButtonRescheduleClick(v: View?) {
        if (v == null)
            return

        when (v.id) {
            R.id.snooze_view_reschedule_present1 ->
                reschedule( Consts.HOUR_IN_SECONDS * 1000L)
            R.id.snooze_view_reschedule_present2 ->
                reschedule( Consts.DAY_IN_SECONDS * 1000L)
            R.id.snooze_view_reschedule_present3 ->
                reschedule( Consts.DAY_IN_SECONDS * 7L * 1000L)
        }
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    fun OnButtonRescheduleCustomClick(v: View?) {
    }

    companion object {
        private val logger = Logger("ActivitySnooze");

        const val CUSTOM_SNOOZE_SNOOZE_FOR_IDX = 0
        const val CUSTOM_SNOOZE_SNOOZE_UNTIL_IDX = 1

    }

}
