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

package com.github.quarck.calnotify.notification

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.PowerManager
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.logs.Logger
import com.github.quarck.calnotify.utils.backgroundWakeLocked
import com.github.quarck.calnotify.utils.powerManager
import com.github.quarck.calnotify.utils.vibratorService

class FailbackAudioReminder : FailbackAudioReminderInterface {

    override fun fireReminder(settings: Settings, ctx: Context) {

        if (settings.reminderVibraOn) {
            ctx.vibratorService.vibrate(
                    settings.reminderVibrationPattern,
                    -1)
        }

        if (settings.reminderRingtoneURI != null) {
            try {
                val notificationUri = settings.reminderRingtoneURI

                val mediaPlayer = MediaPlayer()

                mediaPlayer.setDataSource(ctx, notificationUri)
                mediaPlayer.setAudioStreamType(AudioManager.STREAM_NOTIFICATION)
                mediaPlayer.prepare()
                mediaPlayer.setOnCompletionListener { mp -> mp.release() }
                mediaPlayer.start()

            }
            catch (e: Exception) {
                logger.debug("Exception while playing notification")
                e.printStackTrace()
            }
        }

        if (settings.notificationWakeScreen) {
            //
            backgroundWakeLocked(
                    ctx.powerManager,
                    PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    Consts.SCREEN_WAKE_LOCK_NAME) {
                // Screen would actually be turned on for a duration of screen timeout set by the user
                // So don't need to keep wakelock for too long
                Thread.sleep(Consts.WAKE_SCREEN_DURATION)
            }
        }

    }

    companion object {
        val logger = Logger("FailbackAudioReminder")
    }
}