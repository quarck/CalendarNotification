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

import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.eventsstorage.EventDisplayStatus
import com.github.quarck.calnotify.eventsstorage.EventRecord

class NotificationState(
    var notificationId: Int,
    var displayStatus: EventDisplayStatus = EventDisplayStatus.Hidden)

class NotificationStateTracker {

    private var byEventId =  mutableMapOf<Long, NotificationState>()

    private fun nextNotificationId(): Int {
        val ent = byEventId.maxBy { e -> e.value.notificationId }
        return if (ent != null) ent.value.notificationId + 1
                else Consts.NOTIFICATION_ID_DYNAMIC_FROM
    }

    val isEmpty: Boolean
        get() = synchronized(this) { byEventId.isEmpty() }

    operator fun get(eventId: Long): NotificationState =
        synchronized(this) {
            var state = byEventId.get(eventId)
            if (state == null) {
                state = NotificationState(nextNotificationId())
                byEventId.put(eventId, state)
            }

            return state;
        }

    operator fun get(event: EventRecord) = get(event.eventId)

    fun unregisterEvent(eventId: Long) =
        synchronized(this){
            byEventId.remove(eventId)
        }

    fun getNotificationId(eventId: Long) =
        get(eventId).notificationId
}