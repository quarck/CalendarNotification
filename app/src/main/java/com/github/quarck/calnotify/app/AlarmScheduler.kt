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

package com.github.quarck.calnotify.app

import android.content.Context
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.broadcastreceivers.ReminderAlarmBroadcastReceiver
import com.github.quarck.calnotify.broadcastreceivers.ReminderExactAlarmBroadcastReceiver
import com.github.quarck.calnotify.broadcastreceivers.SnoozeAlarmBroadcastReceiver
import com.github.quarck.calnotify.broadcastreceivers.SnoozeExactAlarmBroadcastReceiver
import com.github.quarck.calnotify.calendar.isNotSpecial
import com.github.quarck.calnotify.eventsstorage.EventsStorage
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.persistentState
import com.github.quarck.calnotify.quiethours.QuietHoursManagerInterface
import com.github.quarck.calnotify.reminders.ReminderState
import com.github.quarck.calnotify.ui.MainActivity
import com.github.quarck.calnotify.utils.alarmManager
import com.github.quarck.calnotify.utils.cancelExactAndAlarm
import com.github.quarck.calnotify.utils.setExactAndAlarm


object AlarmScheduler : AlarmSchedulerInterface {

    const val LOG_TAG = "AlarmScheduler"

    override fun rescheduleAlarms(context: Context, settings: Settings, quietHoursManager: QuietHoursManagerInterface) {

        DevLog.debug(LOG_TAG, "rescheduleAlarms called");

        EventsStorage(context).use {
            db ->

            val events = db.events

            // Schedule event (snooze) alarm
            var nextEventAlarm =
                    events.filter { it.snoozedUntil != 0L && it.isNotSpecial }.map { it.snoozedUntil }.min()

            if (nextEventAlarm != null) {

                val currentTime = System.currentTimeMillis()

                if (nextEventAlarm < currentTime) {
                    DevLog.error(context, LOG_TAG, "CRITICAL: rescheduleAlarms: nextAlarm=$nextEventAlarm is less than currentTime $currentTime");
                    nextEventAlarm = currentTime + Consts.MINUTE_IN_SECONDS * 5 * 1000L;
                }

                DevLog.info(context, LOG_TAG, "next alarm at ${nextEventAlarm} (T+${(nextEventAlarm - currentTime) / 1000L}s)");

                context.alarmManager.setExactAndAlarm(
                        context,
                        settings.useSetAlarmClock,
                        nextEventAlarm,
                        SnoozeAlarmBroadcastReceiver::class.java, // ignored on KitKat and below
                        SnoozeExactAlarmBroadcastReceiver::class.java,
                        MainActivity::class.java)

                context.persistentState.nextSnoozeAlarmExpectedAt = nextEventAlarm

            }
            else { // if (nextEventAlarm != null) {

                DevLog.info(context, LOG_TAG, "Cancelling alarms (snooze and reminder)");

                context.alarmManager.cancelExactAndAlarm(
                        context,
                        SnoozeAlarmBroadcastReceiver::class.java,
                        SnoozeExactAlarmBroadcastReceiver::class.java)
            }

            val reminderState = ReminderState(context)

            // Schedule reminders alarm
            var reminderAlarmNextFire: Long? = null

            val quietHoursOneTimeReminderEnabled =
                    reminderState.quietHoursOneTimeReminderEnabled

            if (settings.remindersEnabled || quietHoursOneTimeReminderEnabled) {

                val hasActiveNotifications = events.filter { it.snoozedUntil == 0L && it.isNotSpecial }.any()

                if (hasActiveNotifications) {

                    reminderAlarmNextFire = System.currentTimeMillis() + settings.remindersIntervalMillis

                    if (quietHoursOneTimeReminderEnabled) {
                        // a little bit of a hack to set it to fire "as soon as possible after quiet hours"
                        reminderAlarmNextFire = System.currentTimeMillis() + Consts.ALARM_THRESHOLD
                    }

                    val quietUntil = quietHoursManager.getSilentUntil(settings, reminderAlarmNextFire)

                    if (quietUntil != 0L) {
                        DevLog.info(context, LOG_TAG, "Reminder alarm moved: $reminderAlarmNextFire -> ${quietUntil + Consts.ALARM_THRESHOLD}, reason: quiet hours");
                        // give a little extra delay, so if requests would fire precisely at the
                        // quietUntil, reminders would wait a bit longer
                        reminderAlarmNextFire = quietUntil + Consts.ALARM_THRESHOLD
                    }
                    else {
                        DevLog.info(context, LOG_TAG, "next fire: $reminderAlarmNextFire")
                    }

                }
                else {  // if (hasActiveNotifications)
                    DevLog.info(context, LOG_TAG, "no active requests")
                }
            }
            else { // if (settings.remindersEnabled || settings.quietHoursOneTimeReminderEnabled) {
                DevLog.info(context, LOG_TAG, "reminders are not enabled")
            }

            if (reminderAlarmNextFire != null) {
                context.alarmManager.setExactAndAlarm(
                        context,
                        settings.useSetAlarmClock,
                        reminderAlarmNextFire,
                        ReminderAlarmBroadcastReceiver::class.java, // ignored on KitKat and below
                        ReminderExactAlarmBroadcastReceiver::class.java,
                        MainActivity::class.java)

                reminderState.nextFireExpectedAt = reminderAlarmNextFire

            }
            else {
                context.alarmManager.cancelExactAndAlarm(
                        context,
                        ReminderAlarmBroadcastReceiver::class.java,
                        ReminderExactAlarmBroadcastReceiver::class.java)
            }
        }
    }
}
