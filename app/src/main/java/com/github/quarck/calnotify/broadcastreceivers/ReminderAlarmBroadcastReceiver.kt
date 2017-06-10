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

package com.github.quarck.calnotify.broadcastreceivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.PowerManager
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.globalState
import com.github.quarck.calnotify.logs.DevLog
//import com.github.quarck.calnotify.logs.Logger
import com.github.quarck.calnotify.persistentState
import com.github.quarck.calnotify.quiethours.QuietHoursManager
import com.github.quarck.calnotify.reminders.ReminderState
import com.github.quarck.calnotify.ui.MainActivity
import com.github.quarck.calnotify.utils.alarmManager
import com.github.quarck.calnotify.utils.powerManager
import com.github.quarck.calnotify.utils.setExactAndAlarm
import com.github.quarck.calnotify.utils.wakeLocked

open class ReminderAlarmGenericBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {

        DevLog.debug(LOG_TAG, "Alarm received")

        if (context == null || intent == null) {
            return;
        }

        context.globalState.lastTimerBroadcastReceived = System.currentTimeMillis()

        wakeLocked(context.powerManager, PowerManager.PARTIAL_WAKE_LOCK, Consts.REMINDER_WAKE_LOCK_NAME) {

            if (!ApplicationController.hasActiveEvents(context)) {
                DevLog.info(context, LOG_TAG, "Reminder broadcast alarm received: no active events")
                return@wakeLocked
            }

            val settings = Settings(context)

            val reminderInterval = settings.remindersIntervalMillis
            val currentTime = System.currentTimeMillis()

            val silentUntil = QuietHoursManager.getSilentUntil(settings)

            var nextFireAt = 0L
            var shouldFire = false
            var itIsAfterQuietHoursReminder = false

            val reminderState = ReminderState(context)

            if (reminderState.quietHoursOneTimeReminderEnabled) {

                if (silentUntil == 0L) {
                    DevLog.info(context, LOG_TAG, "One-shot enabled, not in quiet hours, firing")

                    shouldFire = true
                    itIsAfterQuietHoursReminder = true

                    // Check if regular reminders are enabled and schedule reminder if necessary
                    if (settings.remindersEnabled) {
                        nextFireAt = currentTime + reminderInterval
                        DevLog.info(context, LOG_TAG, "Regular reminders enabled, arming next fire at $nextFireAt")
                    }

                }
                else {
                    nextFireAt = silentUntil
                    DevLog.info(context, LOG_TAG, "One-shot enabled, inside quiet hours, postpone until $silentUntil")
                }

            }
            else if (settings.remindersEnabled) {

                val lastFireTime = Math.max(
                        context.persistentState.notificationLastFireTime,
                        reminderState.reminderLastFireTime)

                val sinceLastFire = currentTime - lastFireTime;

                val numRemindersFired = reminderState.numRemindersFired
                val maxFires = settings.maxNumberOfReminders

                DevLog.info(context, LOG_TAG, "Reminders are enabled, lastFire=$lastFireTime, sinceLastFire=$sinceLastFire, numFired=$numRemindersFired, maxFires=$maxFires")

                if (maxFires == 0 || numRemindersFired < maxFires) {

                    if (silentUntil != 0L) {
                        DevLog.info(context, LOG_TAG, "Reminder postponed until $silentUntil due to quiet hours");
                        nextFireAt = silentUntil

                    }
                    else if (reminderInterval - sinceLastFire > Consts.ALARM_THRESHOLD) {
                        // Schedule actual time to fire based on how long ago we have fired
                        val leftMillis = reminderInterval - sinceLastFire;
                        nextFireAt = currentTime + leftMillis

                        DevLog.info(context, LOG_TAG, "Early alarm: since last: ${sinceLastFire}, interval: ${reminderInterval}, thr: ${Consts.ALARM_THRESHOLD}, left: ${leftMillis}, moving alarm to $nextFireAt");
                    }
                    else {
                        nextFireAt = currentTime + reminderInterval
                        shouldFire = true

                        DevLog.info(context, LOG_TAG, "Good to fire, since last: ${sinceLastFire}, interval: ${reminderInterval}, next fire expected at $nextFireAt")

                        if (currentTime > reminderState.nextFireExpectedAt + Consts.ALARM_THRESHOLD) {
                            DevLog.error(context, LOG_TAG, "WARNING: reminder alarm expected at ${reminderState.nextFireExpectedAt}, " +
                                    "received $currentTime, ${(currentTime - reminderState.nextFireExpectedAt) / 1000L}s late")

                            ApplicationController.onReminderAlarmLate(context, currentTime, reminderState.nextFireExpectedAt)
                        }
                    }
                }
                else {
                    DevLog.info(context, LOG_TAG, "Exceeded max fires $maxFires, fired $numRemindersFired times")
                }
            }
            else {
                DevLog.info(context, LOG_TAG, "Reminders are disabled")
            }

            if (nextFireAt != 0L) {
                context.alarmManager.setExactAndAlarm(
                        context,
                        Settings(context),
                        nextFireAt,
                        ReminderAlarmBroadcastReceiver::class.java, // ignored on KitKat and below
                        ReminderExactAlarmBroadcastReceiver::class.java,
                        MainActivity::class.java)

                reminderState.nextFireExpectedAt = nextFireAt
            }

            if (shouldFire)
                fireReminder(context, currentTime, itIsAfterQuietHoursReminder)
        }
    }

    private fun fireReminder(context: Context, currentTime: Long, itIsAfterQuietHoursReminder: Boolean) {

        DevLog.info(context, LOG_TAG, "Firing reminder, current time ${System.currentTimeMillis()}")

        ApplicationController.fireEventReminder(context, itIsAfterQuietHoursReminder);

        ReminderState(context).onReminderFired(currentTime)

        // auto-remove reminder notification after 15 seconds. All we need it for is to
        // play a sound
        val delayInMilliseconds = 15000L

        Handler().postDelayed(
                {
                    ApplicationController.cleanupEventReminder(context)
                },
                delayInMilliseconds
        )
    }

    companion object {
        private const val LOG_TAG = "BroadcastReceiverReminderAlarm"
    }
}

class ReminderAlarmBroadcastReceiver : ReminderAlarmGenericBroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) = super.onReceive(context, intent)
}

class ReminderExactAlarmBroadcastReceiver : ReminderAlarmGenericBroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) = super.onReceive(context, intent)
}
