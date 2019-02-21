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
import android.net.Uri
import android.os.Build
import android.support.v4.app.NotificationCompat
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.utils.notificationManager

class NotificationChannelAttributes(
        // mandatory attributes
        var channelId: String,
        var name: String,
        var importance: Int,
        //var channelIdOffset: Int,
        // Optional attributes
        var description: String? = null,
        var group: String? = null,
        var showBadge: Boolean? = null,
        var sound: Uri? = null,
        var audioAttributes: AudioAttributes? = null,
        var legacyStreamType: Int? = null,
        var enableLights: Boolean? = null,
        var lightColor: Int? = null,
        var enableVibration: Boolean? = null,
        var vibrationPattern: LongArray? = null,
        //var usage: Int? = null,
        var bypassDnd: Boolean? = null,
        var lockscreenVisibility: Int? = null
) {
    companion object {
        val IMPORTANCE_NONE = 0
        val IMPORTANCE_MIN = 1
        val IMPORTANCE_LOW = 2
        val IMPORTANCE_DEFAULT = 3
        val IMPORTANCE_HIGH = 4
    }
}

fun NotificationManager.createNotificationChannel(attr: NotificationChannelAttributes) {

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
        return

    val translatedImportance =
            when (importance){
                NotificationChannelAttributes.IMPORTANCE_NONE ->
                    NotificationManager.IMPORTANCE_NONE
                NotificationChannelAttributes.IMPORTANCE_MIN  ->
                    NotificationManager.IMPORTANCE_MIN
                NotificationChannelAttributes.IMPORTANCE_LOW  ->
                    NotificationManager.IMPORTANCE_LOW
                NotificationChannelAttributes.IMPORTANCE_DEFAULT  ->
                    NotificationManager.IMPORTANCE_DEFAULT
                NotificationChannelAttributes.IMPORTANCE_HIGH  ->
                    NotificationManager.IMPORTANCE_HIGH
                else ->
                    NotificationManager.IMPORTANCE_DEFAULT
            }


    val channel =
            NotificationChannel(
                    attr.channelId,
                    attr.name,
                    translatedImportance
            )

    if (attr.description != null)
        channel.description = attr.description

    if (attr.group != null)
        channel.group = attr.group

    if (attr.showBadge != null)
        channel.setShowBadge(attr.showBadge ?: false)

    if (attr.sound != null && attr.audioAttributes != null)
        channel.setSound(attr.sound, attr.audioAttributes)

    if (attr.enableLights != null)
        channel.enableLights(attr.enableLights ?: false)

    if (attr.lightColor != null)
        channel.lightColor = attr.lightColor ?: 0

    if (attr.enableVibration != null)
        channel.enableVibration(attr.enableVibration ?: false)

    if (attr.vibrationPattern != null)
        channel.vibrationPattern = attr.vibrationPattern

    //if (attr.usage != null)
    //    channel.usage = attr.usage

    if (attr.bypassDnd != null)
        channel.setBypassDnd(attr.bypassDnd ?: false)

    if (attr.lockscreenVisibility != null)
        channel.lockscreenVisibility = attr.lockscreenVisibility ?: 0

    channel.importance = translatedImportance

    this.createNotificationChannel(channel)
}

fun NotificationCompat.Builder.applyChannelAttributes(attr: NotificationChannelAttributes) {

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        return

//    if (attr.showBadge != null)
//        channel.setShowBadge(attr.showBadge ?: false)

    if (attr.sound != null)  {
        if (attr.legacyStreamType != null)
            this.setSound(attr.sound, attr.legacyStreamType ?: 0)
        else
            this.setSound(attr.sound)
    }

    if ((attr.enableLights ?: false) && attr.lightColor != null) {
        this.setLights(attr.lightColor ?: 0, 1000, 1000)
    }

    if ((attr.enableVibration ?: false) && attr.vibrationPattern != null) {
        this.setVibrate(attr.vibrationPattern)
    }

    if (attr.lockscreenVisibility != null)
        this.setVisibility(attr.lockscreenVisibility ?: 0)

    when (attr.importance) {
        // 0
        NotificationManager.IMPORTANCE_NONE ->
            this.setPriority(NotificationCompat.PRIORITY_MIN)

        // 1
        NotificationManager.IMPORTANCE_MIN ->
            this.setPriority(NotificationCompat.PRIORITY_MIN)

        // 2
        NotificationManager.IMPORTANCE_LOW ->
            this.setPriority(NotificationCompat.PRIORITY_LOW)

        // 3
        NotificationManager.IMPORTANCE_DEFAULT ->
            this.setPriority(NotificationCompat.PRIORITY_DEFAULT)

        // 4
        NotificationManager.IMPORTANCE_HIGH ->
            this.setPriority(NotificationCompat.PRIORITY_MAX)
    }

//    if (attr.bypassDnd != null)
//        channel.setBypassDnd(attr.bypassDnd ?: false)
}

object NotificationChannelManager {

    // Note: don't forget to change notification_preferences.xml and reminder_preferences.xml if
    // channel name is changed!

    const val NOTIFICATION_CHANNEL_ID_DEFAULT = "com.github.calnotify.notify.v1.cal"
    const val NOTIFICATION_CHANNEL_ID_ALARM = "com.github.calnotify.notify.v1.calalrm"
    const val NOTIFICATION_CHANNEL_ID_SILENT = "com.github.calnotify.notify.v1.calquiet"
    const val NOTIFICATION_CHANNEL_ID_REMINDER = "com.github.calnotify.notify.v1.rem"
    const val NOTIFICAITON_CHANNEL_ID_REMINDER_ALARM = "com.github.calnotify.notify.v1.remalrm"

    const val MAX_NOTIFICATION_IDS = Int.MAX_VALUE -1

//    const val NOTIFICATION_CHANNEL_ID_REAL_ALARM = "com.github.calnotify.notify.realalarm"
//    const val NOTIFICATION_CHANNEL_ID_REAL_ALARM_OFS = 5

    fun createDefaultNotificationChannelDebug(context: Context): NotificationChannelAttributes {

        val channelId = NOTIFICATION_CHANNEL_ID_DEFAULT

//        val settings = Settings(context)

        val notificationChannel =
                NotificationChannelAttributes(
                        channelId,
                        context.getString(R.string.debug_notifications),
                        NotificationChannelAttributes.IMPORTANCE_DEFAULT
                )

        // Configure the notification channel.
        notificationChannel.description = context.getString(R.string.debug_notifications_description)

        notificationChannel.enableLights = true
        notificationChannel.lightColor = Consts.DEFAULT_LED_COLOR

        notificationChannel.enableVibration = true
        notificationChannel.vibrationPattern = Consts.VIBRATION_PATTERN_DEFAULT

        notificationChannel.importance = NotificationChannelAttributes.IMPORTANCE_DEFAULT;

        val attribBuilder = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_UNKNOWN)

        attribBuilder.setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)

        notificationChannel.sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        notificationChannel.audioAttributes = attribBuilder.build()

        context.notificationManager.createNotificationChannel(notificationChannel)

        return notificationChannel
    }

    enum class SoundState {
        Normal,
        Alarm,
        Silent
    }

    private fun createNotificationChannelForSoundState(
            context: Context,
            soundState: SoundState
    ): NotificationChannelAttributes {

        val channelId: String
        val channelName: String
        val channelDesc: String
        val channelOffset: Int

//        val settings = Settings(context)

        var importance = NotificationChannelAttributes.IMPORTANCE_DEFAULT

        // Regular notification - NOT a reminder
        when (soundState) {
            NotificationChannelManager.SoundState.Normal -> {
                channelId = NOTIFICATION_CHANNEL_ID_DEFAULT
                channelName = context.getString(R.string.notification_channel_default)
                channelDesc = context.getString(R.string.notification_channel_default_desc)
                importance = NotificationChannelAttributes.IMPORTANCE_DEFAULT
            }
            NotificationChannelManager.SoundState.Alarm -> {
                channelId = NOTIFICATION_CHANNEL_ID_ALARM

                channelName = context.getString(R.string.notification_channel_alarm)
                channelDesc = context.getString(R.string.notification_channel_alarm_desc)
                importance = NotificationChannelAttributes.IMPORTANCE_HIGH
            }
            NotificationChannelManager.SoundState.Silent -> {
                channelId = NOTIFICATION_CHANNEL_ID_SILENT

                channelName = context.getString(R.string.notification_channel_silent)
                channelDesc = context.getString(R.string.notification_channel_silent_desc)
                importance = NotificationChannelAttributes.IMPORTANCE_LOW
            }
        }

        DevLog.info(LOG_TAG, "Notification channel for state $soundState " +
                " -> channel ID $channelId, importance $importance")

        // Configure the notification channel.
        val notificationChannel = NotificationChannelAttributes(channelId, channelName, importance)
        notificationChannel.description = channelDesc

        // If we don't enable it now (at channel creation) - no way to enable it later
        notificationChannel.enableLights = true
        notificationChannel.lightColor = Consts.DEFAULT_LED_COLOR

        val attribBuilder = AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_UNKNOWN)

        if (soundState == SoundState.Alarm) {
            attribBuilder
                    .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                    .setLegacyStreamType(AudioManager.STREAM_ALARM)
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)

            notificationChannel.bypassDnd = true
            notificationChannel.legacyStreamType = AudioManager.STREAM_ALARM

            DevLog.info(LOG_TAG, "Alarm attributes applied")
        }
        else {
            attribBuilder.setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
        }

        if (soundState != SoundState.Silent) {
            notificationChannel.sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            notificationChannel.audioAttributes = attribBuilder.build()

            notificationChannel.enableVibration = true

            if (soundState == SoundState.Normal)
                notificationChannel.vibrationPattern = Consts.VIBRATION_PATTERN_DEFAULT
            else {
                notificationChannel.vibrationPattern = Consts.VIBRATION_PATTERN_ALARM
                notificationChannel.legacyStreamType = AudioManager.STREAM_ALARM
            }
        }

        context.notificationManager.createNotificationChannel(notificationChannel)

        return notificationChannel
    }

    private fun createReminderNotificationChannelForSoundState(
            context: Context,
            soundState: SoundState
    ): NotificationChannelAttributes {

        val channelId: String
        val channelName: String
        val channelDesc: String
        val channelOffset: Int

//        val settings = Settings(context)

        val importance: Int

        // Reminder notification
        // isRepost is ignored
        if (soundState == SoundState.Alarm) {
            // use alarm reminder channel
            channelId = NOTIFICAITON_CHANNEL_ID_REMINDER_ALARM
            channelName = context.getString(R.string.notification_channel_alarm_reminders)
            channelDesc = context.getString(R.string.notification_channel_alarm_reminders_desc)
            importance = NotificationChannelAttributes.IMPORTANCE_HIGH
        }
        else { // if (soundState == SoundState.Alarm) {
            // use regular channel - there are no silent reminders
            channelId = NOTIFICATION_CHANNEL_ID_REMINDER
            channelName = context.getString(R.string.notification_channel_reminders)
            channelDesc = context.getString(R.string.notification_channel_reminders_desc)
            importance = NotificationChannelAttributes.IMPORTANCE_DEFAULT
        }

        DevLog.info(LOG_TAG, "Notification channel for reminder state $soundState" +
                " -> channel ID $channelId, importance $importance")

        // Configure the notification channel.
        val notificationChannel = NotificationChannelAttributes(channelId, channelName, importance)
        notificationChannel.description = channelDesc

        // If we don't enable it now (at channel creation) - no way to enable it later
        notificationChannel.enableLights = true
        notificationChannel.lightColor = Consts.DEFAULT_LED_COLOR

        val attribBuilder = AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_UNKNOWN)

        if (soundState == SoundState.Alarm) {
            attribBuilder
                    .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                    .setLegacyStreamType(AudioManager.STREAM_ALARM)
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)

            notificationChannel.bypassDnd = true
            notificationChannel.legacyStreamType = AudioManager.STREAM_ALARM

            DevLog.info(LOG_TAG, "Alarm attributes applied")
        }
        else {
            attribBuilder.setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
        }

        notificationChannel.sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        notificationChannel.audioAttributes = attribBuilder.build()

        notificationChannel.enableVibration = true

        if (soundState == SoundState.Normal)
            notificationChannel.vibrationPattern = Consts.VIBRATION_PATTERN_REMINDER
        else {
            notificationChannel.vibrationPattern = Consts.VIBRATION_PATTERN_ALARM_REMINDER
            notificationChannel.legacyStreamType = AudioManager.STREAM_ALARM
        }

        context.notificationManager.createNotificationChannel(notificationChannel)

        return notificationChannel
    }

    fun createNotificationChannel(
            context: Context,
            soundState: SoundState,
            isReminder: Boolean,
            settings: Settings
    ) : NotificationChannelAttributes  {

        val allowedSoundState =
                if (settings.allowMuteAndAlarm)
                    soundState
                else
                    SoundState.Normal

        return when {
            isReminder ->
                createReminderNotificationChannelForSoundState(context, allowedSoundState)
            else ->
                createNotificationChannelForSoundState(context, allowedSoundState)
        }
    }

//    fun createNotificationChannelForRealAlarms(
//            context: Context
//    ): NotificationChannelAttributes {
//
//        val importance = NotificationChannelAttributes.IMPORTANCE_HIGH
//
//        val channelId = NOTIFICATION_CHANNEL_ID_REAL_ALARM
//        val channelIdOffset = NOTIFICATION_CHANNEL_ID_REAL_ALARM_OFS
//        val channelName = context.getString(R.string.real_alarms_title_please_dont_translate)
//        val channelDesc = context.getString(R.string.empty)
//
//        // Configure the notification channel.
//        val notificationChannel = NotificationChannelAttributes(channelId, channelName,
//                importance, channelIdOffset, NUM_CHANNELS)
//        notificationChannel.description = channelDesc
//
//        // If we don't enable it now (at channel creation) - no way to enable it later
//        notificationChannel.enableLights = true
//        notificationChannel.lightColor = Consts.DEFAULT_LED_COLOR
//
//        val attribBuilder = AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_UNKNOWN)
//
//        attribBuilder
//                .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
//                .setLegacyStreamType(AudioManager.STREAM_ALARM)
//                .setUsage(AudioAttributes.USAGE_ALARM)
//                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
//
//        notificationChannel.bypassDnd = true
//
//        DevLog.info(context, LOG_TAG, "Alarm attributes applied")
//
//        notificationChannel.sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
//        notificationChannel.audioAttributes = attribBuilder.build()
//
//        notificationChannel.enableVibration = true
//
//        notificationChannel.vibrationPattern = Consts.VIBRATION_PATTERN_REAL_ALARM
//
//        context.notificationManager.createNotificationChannel(notificationChannel)
//
//        return notificationChannel
//    }

    fun launchSystemSettingForChannel(context: Context, soundState: SoundState, isReminder: Boolean) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = createNotificationChannel(context, soundState, isReminder, Settings(context))
            val intent = Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            intent.putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
            intent.putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, channel.channelId)
            context.startActivity(intent)
        }
    }

    private const val LOG_TAG = "NotificationChannelManager"
}