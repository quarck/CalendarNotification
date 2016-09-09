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
import com.github.quarck.calnotify.quiethours.QuietHoursManagerInterface
import com.github.quarck.calnotify.utils.alarmManager
import com.github.quarck.calnotify.utils.isMarshmallow
import com.github.quarck.calnotify.utils.setExactCompat


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

                val intent = Intent(context, SnoozeExactAlarmBroadcastReceiver::class.java);
                val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)

                context.alarmManager.setExactCompat(AlarmManager.RTC_WAKEUP, nextEventAlarm, pendingIntent);

                if (isMarshmallow) {
                    // For marshmallow - set another alarm that is allowed to fire in idle
                    // such alarm is not exact regardless of the name, so not good for regular daily usage
                    // that's why setting two alarms, one is to be guaranteed to run in non-power save mode
                    // at given time, and another - allowed in power saver mode

                    val intentMM = Intent(context, SnoozeAlarmBroadcastReceiver::class.java);
                    val pendingIntentMM = PendingIntent.getBroadcast(context, 0, intentMM, PendingIntent.FLAG_UPDATE_CURRENT)

                    context.alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextEventAlarm + Consts.ALARM_THRESHOULD, pendingIntentMM);

                    logger.info("MM style alarm added");
                }

            } else {
                logger.info("No next events, keeping the alarm since it would make no difference if it would be cancelled");
            }

            // Schedule reminders alarm
            if (settings.remindersEnabled || settings.quietHoursOneTimeReminderEnabled) {

                val hasActiveNotifications = events.filter { it.snoozedUntil == 0L }.any()

                if (hasActiveNotifications) {

                    val remindInterval = settings.remindersIntervalMillis
                    var nextFire = System.currentTimeMillis() + remindInterval

                    if (settings.quietHoursOneTimeReminderEnabled)
                        nextFire = System.currentTimeMillis() + Consts.ALARM_THRESHOULD

                    val quietUntil = quietHoursManager.getSilentUntil(settings, nextFire)

                    if (quietUntil != 0L) {
                        logger.info("Reminder alarm moved from $nextFire to ${quietUntil+Consts.ALARM_THRESHOULD} due to silent period");

                        // give a little extra delay, so if events would fire precisely at the quietUntil,
                        // reminders would wait a bit longer
                        nextFire = quietUntil + Consts.ALARM_THRESHOULD
                    }

                    logger.info("Setting reminder alarm at $nextFire")

                    val intent = Intent(context, ReminderExactAlarmBroadcastReceiver::class.java)
                    val pendIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)

                    context.alarmManager.setExactCompat(AlarmManager.RTC_WAKEUP, nextFire, pendIntent)

                    if (isMarshmallow) {
                        // For marshmallow - set another alarm that is allowed to fire in idle
                        // such alarm is not exact regardless of the name, so not good for regular daily usage
                        // that's why setting two alarms, one is to be guaranteed to run in non-power save mode
                        // at given time, and another - allowed in power saver mode

                        val intentMM = Intent(context, ReminderAlarmBroadcastReceiver::class.java)
                        val pendIntentMM = PendingIntent.getBroadcast(context, 0, intentMM, PendingIntent.FLAG_UPDATE_CURRENT)

                        context.alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextFire + Consts.ALARM_THRESHOULD, pendIntentMM)

                        logger.info("MM-style reminder alarm added")
                    }
                }
            }
        }
    }
}
