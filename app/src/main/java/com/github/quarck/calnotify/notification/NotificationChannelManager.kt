//
//   Calendar Notifications Plus
//   Copyright (C) 2018 Sergey Parshin (s.parshin.sc@gmail.com)
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

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import com.github.quarck.calnotify.NotificationSettingsSnapshot
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.utils.notificationManager

enum class NotificationChannelPurpose(val code: Int) {
    RegularNotification(0),
    RegularAlarmNotification(1),
    RegularSilentNotification(2),
    ReminderNotification(3),
    ReminderAlarmNotification(4);

    companion object {
        @JvmStatic
        fun fromInt(v: Int) = values()[v]
    }
}

object NotificationChannelManager {

    const val NOTIFICATION_CHANNEL_ID_DEFAULT = "com.github.calnotify.notify.def"
    const val NOTIFICATION_CHANNEL_ID_ALARM = "com.github.calnotify.notify.alarm"
    const val NOTIFICATION_CHANNEL_ID_SILENT = "com.github.calnotify.notify.quiet"
    const val NOTIFICATION_CHANNEL_ID_REMINDER = "com.github.calnotify.notify.reminder"
    const val NOTIFICAITON_CHANNEL_ID_REMINDER_ALARM = "com.github.calnotify.notify.ralarm"

    fun createDefaultNotificationChannel(context: Context): String {

        val channelId = NOTIFICATION_CHANNEL_ID_DEFAULT

        val settings = Settings(context)

        val notificationChannel =
                NotificationChannel(
                        channelId,
                        context.getString(R.string.notification_channel_default),
                        NotificationManager.IMPORTANCE_DEFAULT
                )

        // Configure the notification channel.
        notificationChannel.description = context.getString(R.string.notification_channel_default_desc)

        notificationChannel.enableLights(true)
        notificationChannel.lightColor = settings.ledColor

        notificationChannel.enableVibration(true)
        notificationChannel.vibrationPattern = settings.vibrationPattern

        val attribBuilder = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_UNKNOWN)

        attribBuilder.setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)

        notificationChannel.setSound(settings.ringtoneURI, attribBuilder.build())

        context.notificationManager.createNotificationChannel(notificationChannel)

        return channelId
    }

    fun createNotificationChannel(
            context: Context,
            notificationPurpose: NotificationChannelPurpose
    ): String {

        val channelId: String
        val channelName: String
        val channelDesc: String

        var isAlarm = false
        var isSilent = false
        var isReminder = false

        val settings = Settings(context)

        when (notificationPurpose) {

            NotificationChannelPurpose.RegularNotification -> {
                channelId = NOTIFICATION_CHANNEL_ID_DEFAULT
                channelName = context.getString(R.string.notification_channel_default)
                channelDesc = context.getString(R.string.notification_channel_default_desc)
            }

            NotificationChannelPurpose.RegularAlarmNotification -> {
                channelId = NOTIFICATION_CHANNEL_ID_ALARM
                channelName = context.getString(R.string.notification_channel_alarm)
                channelDesc = context.getString(R.string.notification_channel_alarm_desc)
                isAlarm = true
            }

            NotificationChannelPurpose.RegularSilentNotification -> {
                channelId = NOTIFICATION_CHANNEL_ID_SILENT
                channelName = context.getString(R.string.notification_channel_silent)
                channelDesc = context.getString(R.string.notification_channel_silent_desc)
                isSilent = true
            }

            NotificationChannelPurpose.ReminderNotification -> {
                channelId = NOTIFICATION_CHANNEL_ID_REMINDER
                channelName = context.getString(R.string.notification_channel_reminders)
                channelDesc = context.getString(R.string.notification_channel_reminders_desc)
                isReminder = true
            }

            NotificationChannelPurpose.ReminderAlarmNotification -> {
                channelId = NOTIFICAITON_CHANNEL_ID_REMINDER_ALARM
                channelName = context.getString(R.string.notification_channel_alarm_reminders)
                channelDesc = context.getString(R.string.notification_channel_alarm_reminders_desc)
                isAlarm = true
                isReminder = true
            }
        }

        val notificationChannel =
                NotificationChannel(
                        channelId,
                        channelName,
                        if (isAlarm)
                            NotificationManager.IMPORTANCE_HIGH
                        else
                            NotificationManager.IMPORTANCE_DEFAULT
                )

        // Configure the notification channel.
        notificationChannel.description = channelDesc

        if (settings.ledNotificationOn) {
            notificationChannel.enableLights(true)
            notificationChannel.lightColor = settings.ledColor
        }
        else {
            notificationChannel.enableLights(false)
        }


        if (!isSilent) {
            if (!isReminder) {
                if (settings.vibraOn) {
                    notificationChannel.enableVibration(true)
                    notificationChannel.vibrationPattern = settings.vibrationPattern
                } else {
                    notificationChannel.enableVibration(false)
                }

            } else {
                if (settings.reminderVibraOn) {
                    notificationChannel.enableVibration(true)
                    notificationChannel.vibrationPattern = settings.reminderVibrationPattern
                } else {
                    notificationChannel.enableVibration(false)
                }
            }
        }

        val attribBuilder = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_UNKNOWN)

        if (isAlarm) {
            attribBuilder
                    .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                    .setLegacyStreamType(AudioManager.STREAM_ALARM)
                    .setUsage(AudioAttributes.USAGE_ALARM)
        }
        else {
            attribBuilder.setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
        }

        if (!isSilent) {
            if (!isReminder)
                notificationChannel.setSound(settings.ringtoneURI, attribBuilder.build())
            else
                notificationChannel.setSound(settings.reminderRingtoneURI, attribBuilder.build())
        }

        context.notificationManager.createNotificationChannel(notificationChannel)

        return channelId
    }
}