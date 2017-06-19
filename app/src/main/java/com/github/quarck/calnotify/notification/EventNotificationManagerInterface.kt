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
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.textutils.EventFormatterInterface

interface EventNotificationManagerInterface {
    fun onEventAdded(ctx: Context, formatter: EventFormatterInterface, event: EventAlertRecord);

    fun onEventDismissed(context: Context, formatter: EventFormatterInterface, eventId: Long, notificationId: Int);

    fun onEventsDismissed(context: Context, formatter: EventFormatterInterface, events: Collection<EventAlertRecord>, postNotifications: Boolean/* = true*/, hasActiveEvents: Boolean);

    fun onEventSnoozed(context: Context, formatter: EventFormatterInterface, eventId: Long, notificationId: Int);

    fun onAllEventsSnoozed(context: Context)

    fun postEventNotifications(context: Context, formatter: EventFormatterInterface, force: Boolean, primaryEventId: Long?);

    fun fireEventReminder(context: Context, itIsAfterQuietHoursReminder: Boolean)

    fun cleanupEventReminder(context: Context)

    fun onEventRestored(context: Context, formatter: EventFormatterInterface, event: EventAlertRecord)

    fun postNotificationsAutoDismissedDebugMessage(context: Context)

    fun postNotificationsAlarmDelayDebugMessage(context: Context, title: String, text: String)

    fun postNotificationsSnoozeAlarmDelayDebugMessage(context: Context, title: String, text: String)
}