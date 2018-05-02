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

import android.content.Context
import com.github.quarck.calnotify.NotificationSettingsSnapshot

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

    const val NOTIFICATION_CHANNEL_ID_REGULAR = "reg"
    const val NOTIFICATION_CHANNEL_ID_ALARM = "alarm"
    const val NOTIFICATION_CHANNEL_ID_SILENT = "quiet"
    const val NOTIFICATION_CHANNEL_ID_REMINDER = "reminder"
    const val NOTIFICAITON_CHANNEL_ID_REMINDER_ALARM = "ralarm"

    val createdChannels = hashSetOf<NotificationChannelPurpose>()


    fun createNotificationChannel(
            context: Context,
            notificationPurpose: NotificationChannelPurpose
    ): String {

        val channelName: Int
        val channelDesc: Int
        val channelId: String

        when (notificationSettings.ringtoneOrigin) {
            RingtoneOrigin.Main -> {
                if (isAlarmStream) {
                    channelName = R.string.channel_main_alarm_name
                    channelDesc = R.string.channel_main_alarm_desc
                    channelId = Consts.NOTIFICAITON_CHANNEL_ID_MAIN_ALARM
                } else {
                    channelName = R.string.channel_main_name
                    channelDesc = R.string.channel_main_desc
                    channelId = Consts.NOTIFICATION_CHANNEL_ID_MAIN
                }
            }
            RingtoneOrigin.Reminder -> {
                if (isAlarmStream) {
                    channelName = R.string.channel_reminder_alarm_name
                    channelDesc = R.string.channel_reminder_alarm_desc
                    channelId = Consts.NOTIFICATION_CHANNEL_ID_ALARM_REMINDER
                } else {
                    channelName = R.string.channel_reminder_name
                    channelDesc = R.string.channel_reminder_desc
                    channelId = Consts.NOTIFICATION_CHANNEL_ID_REMINDER
                }
            }
            RingtoneOrigin.Quiet -> {
                channelName = R.string.channel_quiet_name
                channelDesc = R.string.channel_quiet_desc
                channelId = Consts.NOTIFICATION_CHANNEL_ID_QUIET
            }
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            return channelId

        val notificationChannel =
                NotificationChannel(
                        channelId,
                        context.resources.getString(channelName),
                        if (isAlarmStream)
                            NotificationManager.IMPORTANCE_HIGH
                        else
                            NotificationManager.IMPORTANCE_DEFAULT
                )

        // Configure the notification channel.
        notificationChannel.description = context.resources.getString(channelDesc)

        if (notificationSettings.ledNotificationOn) {
            notificationChannel.enableLights(true)
            notificationChannel.lightColor = notificationSettings.ledColor
        }
        else {
            notificationChannel.enableLights(false)
        }

        if (notificationSettings.vibrationOn) {
            notificationChannel.enableVibration(true)
            notificationChannel.vibrationPattern = notificationSettings.vibrationPattern
        }
        else  {
            notificationChannel.enableVibration(false)
        }

        val attribBuilder = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_UNKNOWN)

        if (isAlarmStream) {
            attribBuilder
                    .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                    .setLegacyStreamType(AudioManager.STREAM_ALARM)
                    .setUsage(AudioAttributes.USAGE_ALARM)
        }
        else {
            attribBuilder.setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
        }

        notificationChannel.setSound(notificationSettings.ringtoneUri, attribBuilder.build())

        context.notificationManager.createNotificationChannel(notificationChannel)

        return channelId
    }
}