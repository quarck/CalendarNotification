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
import com.github.quarck.calnotify.EventsManager
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.globalState
import com.github.quarck.calnotify.logs.Logger
import com.github.quarck.calnotify.notification.ReminderAlarm
import com.github.quarck.calnotify.utils.audioManager
import com.github.quarck.calnotify.utils.powerManager
import com.github.quarck.calnotify.utils.vibratorService
import com.github.quarck.calnotify.utils.wakeLocked

class BroadcastReceiverReminderAlarm : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {

        logger.debug("Alarm received")

        if (context == null || intent == null) {
            return;
        }

        wakeLocked(context.powerManager, PowerManager.PARTIAL_WAKE_LOCK, Consts.WAKE_LOCK_NAME) {

            val settings = Settings(context)

            if (settings.remindersEnabled && EventsManager.hasActiveEvents(context)) {

                logger.debug("Reminders are enabled and have something to remind about")

                val currentTime = System.currentTimeMillis()

                val lastReminder = context.globalState.reminderLastFireTime
                val lastNotification = context.globalState.notificationLastFireTime
                val interval = settings.remindersIntervalMillis

                val lastFireTime = Math.max(lastNotification, lastReminder);

                val numRemindersFired = (lastFireTime - lastReminder) / interval
                val maxFires = settings.maxNumerOfReminders

                if (maxFires == 0 || numRemindersFired <= maxFires) {

                    val sinceLastFire = currentTime - lastFireTime;

                    if (sinceLastFire < interval - Consts.ALARM_THRESHOULD)  {
                        val leftMillis = interval - sinceLastFire;

                        logger.debug("Seen alarm to early: sinceLastFire=${sinceLastFire/1000}, interval=${interval/1000}, thr=${Consts.ALARM_THRESHOULD/1000}, left=${leftMillis/1000}, re-schedule and go back");

                        // Schedule actual time to fire based on how long ago we have fired
                        ReminderAlarm.scheduleAlarmMillis(context, leftMillis)

                    } else {
                        // OK ot fire
                        logger.debug("Since last fire: ${sinceLastFire/1000L}, interval ${interval/1000L}")

                        fireReminder(context, settings)
                        context.globalState.reminderLastFireTime = currentTime

                        // Should schedule next alarm
                        ReminderAlarm.scheduleAlarmMillis(context, interval)
                    }
                } else {
                    logger.debug("Exceeded max numer of fires, maxFires=$maxFires, numRemindersFired=$numRemindersFired")
                    // ReminderAlarm.cancelAlarm(context) // no need to cancel - no longer using repeating alarms
                }
            } else {
                logger.debug("Reminders are disabled or nothing to remind about, received this by error")
                // ReminderAlarm.cancelAlarm(context) // no need to cancel - no longer using repeating alarms
            }
        }
    }

    private fun fireReminder(ctx: Context, settings: Settings) {

        logger.debug("Firing reminder")

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
    }

    companion object {
        private val logger = Logger("BroadcastReceiverReminderAlarm");
    }
}
