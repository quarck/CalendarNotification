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
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.format.DateUtils
import android.view.View
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.EventsManager
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.calendar.CalendarUtils
import com.github.quarck.calnotify.eventsstorage.EventsStorage
import com.github.quarck.calnotify.eventsstorage.formatTime
import com.github.quarck.calnotify.logs.Logger
import com.github.quarck.calnotify.maps.MapsUtils
import com.github.quarck.calnotify.quiethours.QuietHoursManager
import com.github.quarck.calnotify.utils.adjustCalendarColor
import com.github.quarck.calnotify.utils.find

class ActivitySnooze : Activity() {
    var eventId: Long = -1;
    var notificationId: Int = -1;

    lateinit var snoozePresets: LongArray

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_snooze)

        snoozePresets = Settings(this).snoozePresets

        val currentTime = System.currentTimeMillis()

        val settings = Settings(this)

        // Populate snooze controls
        for ((idx, id) in snoozePresetControlIds.withIndex()) {
            var view = find<TextView>(id);
            var quietTimeNotice = find<TextView>(snoozePresentQuietTimeReminderControlIds[idx])

            if (idx < snoozePresets.size) {
                view.text = formatPreset(snoozePresets[idx])
                view.visibility = View.VISIBLE;

                if (QuietHoursManager.isQuietPeriod(settings, currentTime + snoozePresets[idx]))
                    quietTimeNotice.visibility = View.VISIBLE
                else
                    quietTimeNotice.visibility = View.GONE
            } else {
                view.visibility = View.GONE;
                quietTimeNotice.visibility = View.GONE
            }

        }

        // Populate event details
        notificationId = intent.getIntExtra(Consts.INTENT_NOTIFICATION_ID_KEY, -1)
        eventId = intent.getLongExtra(Consts.INTENT_EVENT_ID_KEY, -1)

        var event = EventsStorage(this).use { it.getEvent(eventId) }

        if (eventId != -1L && event != null) {
            var title =
                    if (event.title == "")
                        this.resources.getString(R.string.empty_title)
                    else
                        event.title;

            var (date, time) = event.formatTime(this);

            var location = event.location;
            if (location != "") {
                find<View>(R.id.snooze_view_location_layout).visibility = View.VISIBLE;
                var locationView = find<TextView>(R.id.snooze_view_location)
                locationView.text = location;
                locationView.setOnClickListener { MapsUtils.openLocation(this, event.location) }
            }

            find<TextView>(R.id.snooze_view_title).text = title;

            var dateView = find<TextView>(R.id.snooze_view_event_date)
            var timeView = find<TextView>(R.id.snooze_view_event_time)

            dateView.text = date;
            timeView.text = time;

            var onClick = View.OnClickListener { CalendarUtils.editCalendarEvent(this, eventId) }

            dateView.setOnClickListener(onClick)
            timeView.setOnClickListener(onClick)


            var color: Int = event.color.adjustCalendarColor();
            if (color == 0)
                color = resources.getColor(R.color.primary)
            find<RelativeLayout>(R.id.snooze_view_event_details_layout).background = ColorDrawable(color)

            var isRepeating = CalendarUtils.isRepeatingEvent(this, event)
            if (isRepeating != null && !isRepeating)
                find<RelativeLayout>(R.id.snooze_reschedule_layout).visibility = View.VISIBLE

        } else if (eventId == -1L) { // no eventId passed or -1 passed - it is "Snooze All"

            find<TextView>(R.id.snooze_view_title).text = this.resources.getString(R.string.all_events);
            find<RelativeLayout>(R.id.layout_snooze_time).visibility = View.GONE
            find<View>(R.id.view_snooze_divider).visibility = View.GONE
            find<TextView>(R.id.snooze_view_event_date).text = ""
            find<TextView>(R.id.snooze_view_event_time).text = ""
            find<TextView>(R.id.snooze_snooze_for).text = resources.getString(R.string.snooze_events_for)
        }
    }

    private fun formatPreset(preset: Long): String {
        var num: Long = 0
        var unit: String = ""
        var presetSeconds = preset / 1000L;

        if (presetSeconds % Consts.DAY_IN_SECONDS == 0L) {
            num = presetSeconds / Consts.DAY_IN_SECONDS;

            unit =
                    if (num > 1)
                        resources.getString(R.string.days)
                    else
                        resources.getString(R.string.day)
        } else if (presetSeconds % Consts.HOUR_IN_SECONDS == 0L) {
            num = presetSeconds / Consts.HOUR_IN_SECONDS;
            unit =
                    if (num > 1)
                        resources.getString(R.string.hours)
                    else
                        resources.getString(R.string.hour)
        } else {
            num = presetSeconds / Consts.MINUTE_IN_SECONDS;
            unit =
                    if (num > 1)
                        resources.getString(R.string.minutes)
                    else
                        resources.getString(R.string.minute)
        }

        return "$num $unit"
    }

    fun onButtonCancelClick(v: View?) {
        finish();
    }

    fun OnButtonEventDetailsClick(v: View?) {
        CalendarUtils.viewCalendarEvent(this, eventId);
    }

    private fun snoozeEvent(presetIdx: Int) {
        var snoozeDelay = snoozePresets[presetIdx];

        var quietUntil = 0L

        if (notificationId != -1 && eventId != -1L) {

            logger.debug("Snoozing event id ${eventId}, snoozeDelay=${snoozeDelay / 1000L}")

            EventsStorage(this).use {
                storage ->
                var event = storage.getEvent(eventId)
                if (event != null) {
                    EventsManager.snoozeEvent(this, event, snoozeDelay, storage, { qt -> quietUntil = qt });
                    finish();
                } else {
                    logger.error("Error: can't get event from DB");
                }
            }

        } else {

            AlertDialog.Builder(this)
                    .setMessage(R.string.snooze_all_confirmation)
                    .setCancelable(false)
                    .setPositiveButton(android.R.string.yes) {
                        x, y ->

                        logger.debug("Snoozing all events, snoozeDelay=${snoozeDelay / 1000L}")

                        EventsStorage(this).use {
                            storage ->

                            var events = storage.events

                            // Don't allow events to have exactly the same "quietUntil", so to have
                            // predicted sorting order, so add a tiny (less than 0.1s) adjust to each
                            // snoozed time

                            var snoozeAdjust = 0L

                            for (event in events) {
                                EventsManager.snoozeEvent(this, event, snoozeDelay + snoozeAdjust, storage,
                                    { qt -> quietUntil = qt });
                                ++snoozeAdjust
                            }
                        }

                        finish();
                    }
                    .setNegativeButton(R.string.cancel) {
                        x, y ->
                    }
                    .create()
                    .show()
        }

        if (quietUntil != 0L) {
            val msg =
                    String.format(resources.getString(R.string.snoozed_time_inside_quiet_hours),
                            DateUtils.formatDateTime(this, quietUntil,
                                            DateUtils.FORMAT_SHOW_TIME or
                                            DateUtils.FORMAT_SHOW_DATE))
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        }
    }

    fun OnButtonSnoozeClick(v: View?) {
        if (v == null)
            return

        for ((idx, id) in snoozePresetControlIds.withIndex()) {
            if (id == v.id) {
                snoozeEvent(idx);
                break;
            }
        }
    }

    fun reschedule(addTime: Long) {

        var event = EventsStorage(this).use { it.getEvent(eventId) }

        if (event != null) {

            logger.info("Moving event ${event.eventId} by ${addTime/1000L} seconds");

            var moved = CalendarUtils.moveEvent(this, event, addTime)
            if (moved) {
                logger.info("snooze: Moved event ${event.eventId} by ${addTime/1000L} seconds")
                // Dismiss
                EventsManager.dismissEvent(this, eventId, notificationId)

                if (Settings(this).viewAfterEdit) {
                    // Show
                    CalendarUtils.viewCalendarEvent(this, event.eventId)
                }

                // terminate ourselves
                finish();
            } else {
                logger.info("snooze: Failed to move event ${event.eventId} by ${addTime/1000L} seconds")
            }
        }
    }

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

    fun OnButtonRescheduleCustomClick(v: View?) {
    }

    companion object {
        private val logger = Logger("ActivitySnooze");
    }

}
