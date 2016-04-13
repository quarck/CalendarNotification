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
                val lastFireTime = context.globalState.lastFireTime

                val interval = settings.remindersIntervalMillis

                val sinceLastFire = currentTime - lastFireTime;

                if (sinceLastFire < interval - Consts.ALARM_THRESHOULD)  {
                    logger.debug("Seen alarm to early, re-schedule and go back");

                    val leftMillis = interval - sinceLastFire;
                    ReminderAlarm.scheduleAlarmMillis(context, interval, leftMillis)

                } else {
                    // OK ot fire
                    logger.debug("Since last fire: ${sinceLastFire/1000L}, interval ${interval/1000L}")

                    val fired = checkPhoneSilentAndFire(context, settings)
                    if (fired) {
                        context.globalState.lastFireTime = currentTime
                    }
                }

            } else {
                logger.debug("Reminders are disabled or nothing to remind about, received this by error")
                ReminderAlarm.cancelAlarm(context)
            }
        }
    }

    private fun checkPhoneSilentAndFire(ctx: Context, settings: Settings): Boolean {

        var mayFireVibration = false
        var mayFireSound = false

        var fired = false

        val am = ctx.audioManager

        when (am.ringerMode) {

            AudioManager.RINGER_MODE_SILENT ->
                logger.debug("checkPhoneSilentAndFire: AudioManager.RINGER_MODE_SILENT")

            AudioManager.RINGER_MODE_VIBRATE -> {
                logger.debug("checkPhoneSilentAndFire: AudioManager.RINGER_MODE_VIBRATE")
                mayFireVibration = true
            }

            AudioManager.RINGER_MODE_NORMAL -> {
                logger.debug("checkPhoneSilentAndFire: AudioManager.RINGER_MODE_NORMAL")
                mayFireVibration = true
                mayFireSound = true
            }
        }

        if (mayFireVibration) {
            logger.debug("Firing vibro-alarm")

            fired = true

            val v = ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            val pattern = longArrayOf(0, Consts.VIBRATION_DURATION);
            v.vibrate(pattern, -1)
        }

        if (mayFireSound) {
            logger.debug("Playing sound notification, if URI is not null")

            fired = true

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

        return fired
    }

    companion object {
        private val logger = Logger("BroadcastReceiverReminderAlarm");
    }
}
