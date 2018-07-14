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
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.Build
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.NotificationSettingsSnapshot
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.utils.notificationManager


object NotificationChannelManager {

    // Note: don't forget to change notification_preferences.xml and reminder_preferences.xml if
    // channel name is changed!
    const val NOTIFICATION_CHANNEL_ID_DEFAULT = "com.github.calnotify.notify.v1.cal"
    const val NOTIFICATION_CHANNEL_ID_ALARM = "com.github.calnotify.notify.v1.calalrm"
    const val NOTIFICATION_CHANNEL_ID_SILENT = "com.github.calnotify.notify.v1.calquiet"

    const val NOTIFICATION_CHANNEL_ID_REMINDER = "com.github.calnotify.notify.v1.rem"
    const val NOTIFICAITON_CHANNEL_ID_REMINDER_ALARM = "com.github.calnotify.notify.v1.remalrm"

    fun createDefaultNotificationChannelDebug(context: Context): String {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return ""
        }

        val channelId = NOTIFICATION_CHANNEL_ID_DEFAULT

//        val settings = Settings(context)

        val notificationChannel =
                NotificationChannel(
                        channelId,
                        context.getString(R.string.debug_notifications),
                        NotificationManager.IMPORTANCE_DEFAULT
                )

        // Configure the notification channel.
        notificationChannel.description = context.getString(R.string.debug_notifications_description)

        notificationChannel.enableLights(true)
        notificationChannel.lightColor = Consts.DEFAULT_LED_COLOR

        notificationChannel.enableVibration(true)
        notificationChannel.vibrationPattern = Consts.VIBRATION_PATTERN_DEFAULT

        notificationChannel.importance = NotificationManager.IMPORTANCE_DEFAULT;

        val attribBuilder = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_UNKNOWN)

        attribBuilder.setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)

        notificationChannel.setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                attribBuilder.build()
        )

        context.notificationManager.createNotificationChannel(notificationChannel)

        return channelId
    }

    enum class SoundState {
        Normal,
        Alarm,
        Silent
    }

    private fun createNotificationChannelForSoundState(
            context: Context,
            soundState: SoundState
    ): String {

        val channelId: String
        val channelName: String
        val channelDesc: String

//        val settings = Settings(context)

        var importance = NotificationManager.IMPORTANCE_DEFAULT

        // Regular notification - NOT a reminder
        when (soundState) {
            NotificationChannelManager.SoundState.Normal -> {
                channelId = NOTIFICATION_CHANNEL_ID_DEFAULT
                channelName = context.getString(R.string.notification_channel_default)
                channelDesc = context.getString(R.string.notification_channel_default_desc)
                importance = NotificationManager.IMPORTANCE_DEFAULT
            }
            NotificationChannelManager.SoundState.Alarm -> {
                channelId = NOTIFICATION_CHANNEL_ID_ALARM
                channelName = context.getString(R.string.notification_channel_alarm)
                channelDesc = context.getString(R.string.notification_channel_alarm_desc)
                importance = NotificationManager.IMPORTANCE_HIGH
            }
            NotificationChannelManager.SoundState.Silent -> {
                channelId = NOTIFICATION_CHANNEL_ID_SILENT
                channelName = context.getString(R.string.notification_channel_silent)
                channelDesc = context.getString(R.string.notification_channel_silent_desc)
                importance = NotificationManager.IMPORTANCE_LOW
            }
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return channelId
        }

        DevLog.info(context, LOG_TAG, "Notification channel for state $soundState " +
                " -> channel ID $channelId, importance $importance")

        // Configure the notification channel.
        val notificationChannel = NotificationChannel(channelId, channelName, importance)
        notificationChannel.description = channelDesc

        // If we don't enable it now (at channel creation) - no way to enable it later
        notificationChannel.enableLights(true)
        notificationChannel.lightColor = Consts.DEFAULT_LED_COLOR

        val attribBuilder = AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_UNKNOWN)

        if (soundState == SoundState.Alarm) {
            attribBuilder
                    //.setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                    .setLegacyStreamType(AudioManager.STREAM_ALARM)
                    .setUsage(AudioAttributes.USAGE_ALARM)

            notificationChannel.setBypassDnd(true)

            DevLog.info(context, LOG_TAG, "Alarm attributes applied")
        }
        else {
            attribBuilder.setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
        }

        if (soundState != SoundState.Silent) {
            notificationChannel.setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    attribBuilder.build()
            )

            notificationChannel.enableVibration(true)

            if (soundState == SoundState.Normal)
                notificationChannel.vibrationPattern = Consts.VIBRATION_PATTERN_DEFAULT
            else
                notificationChannel.vibrationPattern = Consts.VIBRATION_PATTERN_ALARM
        }

        context.notificationManager.createNotificationChannel(notificationChannel)

        return channelId
    }

    private fun createReminderNotificationChannelForSoundState(
            context: Context,
            soundState: SoundState
    ): String {

        val channelId: String
        val channelName: String
        val channelDesc: String

//        val settings = Settings(context)

        val importance: Int

        // Reminder notification
        // isRepost is ignored
        if (soundState == SoundState.Alarm) {
            // use alarm reminder channel
            channelId = NOTIFICAITON_CHANNEL_ID_REMINDER_ALARM
            channelName = context.getString(R.string.notification_channel_alarm_reminders)
            channelDesc = context.getString(R.string.notification_channel_alarm_reminders_desc)
            importance = NotificationManager.IMPORTANCE_HIGH
        }
        else { // if (soundState == SoundState.Alarm) {
            // use regular channel - there are no silent reminders
            channelId = NOTIFICATION_CHANNEL_ID_REMINDER
            channelName = context.getString(R.string.notification_channel_reminders)
            channelDesc = context.getString(R.string.notification_channel_reminders_desc)
            importance = NotificationManager.IMPORTANCE_DEFAULT
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return channelId
        }

        DevLog.info(context, LOG_TAG, "Notification channel for reminder state $soundState" +
                " -> channel ID $channelId, importance $importance")

        // Configure the notification channel.
        val notificationChannel = NotificationChannel(channelId, channelName, importance)
        notificationChannel.description = channelDesc

        // If we don't enable it now (at channel creation) - no way to enable it later
        notificationChannel.enableLights(true)
        notificationChannel.lightColor = Consts.DEFAULT_LED_COLOR

        val attribBuilder = AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_UNKNOWN)

        if (soundState == SoundState.Alarm) {
            attribBuilder
                    //.setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                    .setLegacyStreamType(AudioManager.STREAM_ALARM)
                    .setUsage(AudioAttributes.USAGE_ALARM)

            notificationChannel.setBypassDnd(true)

            DevLog.info(context, LOG_TAG, "Alarm attributes applied")
        }
        else {
            attribBuilder.setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
        }

        notificationChannel.setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                attribBuilder.build()
        )

        notificationChannel.enableVibration(true)

        if (soundState == SoundState.Normal)
            notificationChannel.vibrationPattern = Consts.VIBRATION_PATTERN_REMINDER
        else
            notificationChannel.vibrationPattern = Consts.VIBRATION_PATTERN_ALARM_REMINDER

        context.notificationManager.createNotificationChannel(notificationChannel)

        return channelId
    }

    fun createNotificationChannel(
            context: Context,
            soundState: SoundState,
            isReminder: Boolean
    ): String {
        return if (!isReminder)
            createNotificationChannelForSoundState(context, soundState)
        else
            createReminderNotificationChannelForSoundState(context, soundState)
    }

    fun launchSystemSettingForChannel(context: Context, soundState: SoundState, isReminder: Boolean) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = createNotificationChannel(context, soundState, isReminder)
            val intent = Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            intent.putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
            intent.putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, channel)
            context.startActivity(intent)
        }
    }

    private const val LOG_TAG = "NotificationChannelManager"
}