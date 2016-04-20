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

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.github.quarck.calnotify.broadcastreceivers.BroadcastReceiverReminderAlarm
import com.github.quarck.calnotify.logs.Logger
import com.github.quarck.calnotify.utils.alarmManager
import com.github.quarck.calnotify.utils.setExactCompat

object ReminderAlarm {

    private val logger = Logger("ReminderManager");

    fun scheduleAlarmMillis(context: Context, nextMillis: Long) {

        logger.debug("Setting reminder alarm in  ${nextMillis/1000L} seconds")

        val intent = Intent(context, BroadcastReceiverReminderAlarm::class.java)

        val pendIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)

        context.alarmManager.setExactCompat(
                AlarmManager.RTC_WAKEUP,
                nextMillis,
                pendIntent)
    }

    fun cancelAlarm(context: Context) {

        logger.debug("Cancelling reminder alarm")

        val intent = Intent(context, BroadcastReceiverReminderAlarm::class.java)
        val sender = PendingIntent.getBroadcast(context, 0, intent, 0)

        context.alarmManager.cancel(sender)
    }
}
