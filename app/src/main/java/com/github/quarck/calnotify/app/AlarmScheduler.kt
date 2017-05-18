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

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.broadcastreceivers.ReminderAlarmBroadcastReceiver
import com.github.quarck.calnotify.broadcastreceivers.ReminderExactAlarmBroadcastReceiver
import com.github.quarck.calnotify.broadcastreceivers.SnoozeAlarmBroadcastReceiver
import com.github.quarck.calnotify.broadcastreceivers.SnoozeExactAlarmBroadcastReceiver
import com.github.quarck.calnotify.eventsstorage.EventsStorage
import com.github.quarck.calnotify.logs.Logger
import com.github.quarck.calnotify.persistentState
import com.github.quarck.calnotify.quiethours.QuietHoursManagerInterface
import com.github.quarck.calnotify.reminders.ReminderState
import com.github.quarck.calnotify.ui.MainActivity
import com.github.quarck.calnotify.utils.alarmManager
import com.github.quarck.calnotify.utils.cancelExactAndAlarm
import com.github.quarck.calnotify.utils.setExactAndAlarm


object AlarmScheduler: AlarmSchedulerInterface {

    val logger = Logger("AlarmScheduler")

    override fun rescheduleAlarms(context: Context, settings: Settings, quietHoursManager: QuietHoursManagerInterface) {

        logger.debug("rescheduleAlarms called");

        EventsStorage(context).use {
            db ->

            val events = db.events

            // Schedule event (snooze) alarm
            var nextEventAlarm =
                    events.filter { it.snoozedUntil != 0L }.map { it.snoozedUntil }.min()

            if (nextEventAlarm != null) {

                val currentTime = System.currentTimeMillis()

                if (nextEventAlarm < currentTime) {
                    logger.error("CRITICAL: nextAlarm=$nextEventAlarm is less than currentTime $currentTime");
                    nextEventAlarm = currentTime + Consts.MINUTE_IN_SECONDS * 5 * 1000L;
                }

                logger.info("Scheduling next alarm at ${nextEventAlarm}, in ${(nextEventAlarm - currentTime) / 1000L} seconds");

                context.alarmManager.setExactAndAlarm(
                        context,
                        settings,
                        nextEventAlarm,
                        SnoozeAlarmBroadcastReceiver::class.java, // ignored on KitKat and below
                        SnoozeExactAlarmBroadcastReceiver::class.java,
                        MainActivity::class.java,
                        logger)

            } else { // if (nextEventAlarm != null) {

                logger.info("No next events, cancelling alarms");

                context.alarmManager.cancelExactAndAlarm(
                        context,
                        settings,
                        SnoozeAlarmBroadcastReceiver::class.java, // ignored on KitKat and below
                        SnoozeExactAlarmBroadcastReceiver::class.java,
                        logger)
            }

            // Schedule reminders alarm
            var reminderAlarmNextFire: Long? = null

            val quietHoursOneTimeReminderEnabled =
                ReminderState(context).quietHoursOneTimeReminderEnabled

            if (settings.remindersEnabled || quietHoursOneTimeReminderEnabled) {

                val hasActiveNotifications = events.filter { it.snoozedUntil == 0L }.any()

                if (hasActiveNotifications) {

                    reminderAlarmNextFire = System.currentTimeMillis() + settings.remindersIntervalMillis

                    if (quietHoursOneTimeReminderEnabled) {
                        // a little bit of a hack to set it to fire "as soon as possible after quiet hours"
                        reminderAlarmNextFire = System.currentTimeMillis() + Consts.ALARM_THRESHOLD
                    }

                    val quietUntil = quietHoursManager.getSilentUntil(settings, reminderAlarmNextFire)

                    if (quietUntil != 0L) {
                        logger.info("Reminder alarm moved from $reminderAlarmNextFire to ${quietUntil+Consts.ALARM_THRESHOLD} due to silent period");
                        // give a little extra delay, so if events would fire precisely at the
                        // quietUntil, reminders would wait a bit longer
                        reminderAlarmNextFire = quietUntil + Consts.ALARM_THRESHOLD
                    }

                    logger.info("Reminder alarm: next fire at $reminderAlarmNextFire")

                } else {  // if (hasActiveNotifications)
                    logger.info("Reminder alarm: no active events to remind about")
                }
            } else { // if (settings.remindersEnabled || settings.quietHoursOneTimeReminderEnabled) {
                logger.info("Reminder alarm: reminders are not enabled")
            }

            if (reminderAlarmNextFire != null) {
                context.alarmManager.setExactAndAlarm(
                        context,
                        settings,
                        reminderAlarmNextFire,
                        ReminderAlarmBroadcastReceiver::class.java, // ignored on KitKat and below
                        ReminderExactAlarmBroadcastReceiver::class.java,
                        MainActivity::class.java,
                        logger)
            } else {
                context.alarmManager.cancelExactAndAlarm(
                        context,
                        settings,
                        ReminderAlarmBroadcastReceiver::class.java, // ignored on KitKat and below
                        ReminderExactAlarmBroadcastReceiver::class.java,
                        logger)
            }
        }
    }
}
