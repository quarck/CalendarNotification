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
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.PowerManager
import android.os.Vibrator
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.globalState
import com.github.quarck.calnotify.logs.Logger
import com.github.quarck.calnotify.notification.EventNotificationManager
import com.github.quarck.calnotify.app.ReminderAlarm
import com.github.quarck.calnotify.quiethours.QuietHoursManager
import com.github.quarck.calnotify.utils.audioManager
import com.github.quarck.calnotify.utils.powerManager
import com.github.quarck.calnotify.utils.vibratorService
import com.github.quarck.calnotify.utils.wakeLocked

class ReminderAlarmBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {

        logger.debug("Alarm received")

        if (context == null || intent == null) {
            return;
        }

        context.globalState.lastTimerBroadcastReceived = System.currentTimeMillis()

        wakeLocked(context.powerManager, PowerManager.PARTIAL_WAKE_LOCK, Consts.REMINDER_WAKE_LOCK_NAME) {

            if (!ApplicationController.hasActiveEvents(context))
                return@wakeLocked

            val settings = Settings(context)

            val reminderInterval = settings.remindersIntervalMillis
            val currentTime = System.currentTimeMillis()

            val silentUntil = QuietHoursManager.getSilentUntil(settings)

            var nextFireAt = 0L
            var shouldFire = false

            if (settings.quietHoursOneTimeReminderEnabled) {

                if (silentUntil == 0L) {
                    logger.debug("One-shot enabled, not in quiet hours, firing")

                    shouldFire = true

                    // Check if regular reminders are enabled and schedule reminder if necessary
                    if (settings.remindersEnabled) {
                        logger.debug("Regular reminders enabled, arming")
                        nextFireAt = currentTime + reminderInterval
                    }

                } else {
                    logger.debug("One-shot enabled, inside quiet hours, postpone until $silentUntil")
                    nextFireAt = silentUntil
                }

            } else if (settings.remindersEnabled) {

                logger.debug("Reminders are enabled")

                val sinceLastFire =
                    currentTime - Math.max( context.globalState.notificationLastFireTime,
                            context.globalState.reminderLastFireTime);

                val numRemindersFired = context.globalState.numRemindersFired
                val maxFires = settings.maxNumberOfReminders

                if (maxFires == 0 || numRemindersFired <= maxFires) {

                    if (silentUntil != 0L) {
                        logger.debug("Reminder postponed until $silentUntil due to quiet hours");
                        nextFireAt = silentUntil

                    } else if (sinceLastFire < reminderInterval - Consts.ALARM_THRESHOULD)  {
                        // Schedule actual time to fire based on how long ago we have fired
                        val leftMillis = reminderInterval - sinceLastFire;
                        logger.debug("Early alarm: since last: ${sinceLastFire}, interval: ${reminderInterval}, thr: ${Consts.ALARM_THRESHOULD}, left: ${leftMillis}");
                        nextFireAt = currentTime + leftMillis

                    } else {
                        logger.debug("Good to fire, since last: ${sinceLastFire}, interval: ${reminderInterval}")

                        nextFireAt = currentTime + reminderInterval
                        shouldFire = true
                    }
                } else {
                    logger.debug("Exceeded max fires $maxFires, fired $numRemindersFired times")
                }
            } else {
                logger.debug("Reminders are disabled")
            }

            if (nextFireAt != 0L)
                ReminderAlarm.scheduleAlarmMillisAt(context, nextFireAt)

            if (shouldFire)
                fireReminder(context, currentTime, settings)
        }
    }

    private fun fireReminder(context: Context, currentTime: Long, settings: Settings) {

        logger.debug("Firing reminder")

        ApplicationController.fireEventReminder(context);

        // following will actually write xml to file, so check if it is 'true' at the moment
        // before writing 'false' and so wasting flash memory cycles.
        if (settings.quietHoursOneTimeReminderEnabled)
            settings.quietHoursOneTimeReminderEnabled = false;
        else
            context.globalState.numRemindersFired ++;

        context.globalState.reminderLastFireTime = currentTime

        /*
                val pattern = longArrayOf(0, Consts.VIBRATION_DURATION);
                ctx.vibratorService.vibrate(pattern, -1)

                if (settings.ringtoneURI != null) {
                    try {
                        val notificationUri = settings.ringtoneURI

                        val mediaPlayer = MediaPlayer()

                        mediaPlayer.setDataSource(ctx, notificationUri)
                        mediaPlayer.setAudioStreamType(AudioManager.STREAM_NOTIFICATION)
                        mediaPlayer.prepare()
                        mediaPlayer.setOnCompletionListener { mp -> mp.release() }
                        mediaPlayer.start()

                    } catch (e: Exception) {
                        logger.debug("Exception while playing notification")
                        e.printStackTrace()
                    }
                }
        */

    }

    companion object {
        private val logger = Logger("BroadcastReceiverReminderAlarm");
    }
}
