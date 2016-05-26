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
    private var byNotificationId = mutableMapOf<Int, NotificationState>()

    private fun nextNotificationId(): Int {

        val ent = byNotificationId.maxBy { e -> e.key }

        return if (ent != null) ent.key + 1
                else Consts.NOTIFICATION_ID_DYNAMIC_FROM
    }

    val isEmpty: Boolean
        get() =
            synchronized(this) {
                byEventId.isEmpty() || byNotificationId.isEmpty()
            }

    operator fun get(eventId: Long): NotificationState =
        synchronized(this) {
            var state = byEventId.get(eventId)
            if (state == null) {
                state = NotificationState(nextNotificationId())
                byEventId.put(eventId, state)
                byNotificationId.put(state.notificationId, state)
            }

            return state;
        }

    fun unregisterEvent(eventId: Long, notificationId: Int) =
        synchronized(this){
            byEventId.remove(eventId)
            byNotificationId.remove(notificationId)
        }

    fun unregisterEvent(event: EventRecord) =
        synchronized(this){
            val state = byEventId.get(event.eventId)
            if (state != null) {
                byEventId.remove(event.eventId)
                byNotificationId.remove(state.notificationId)
            }
        }

    fun getNotificationId(eventId: Long) =
        get(eventId).notificationId

    fun getDisplayStatus(eventId: Long) =
        get(eventId).displayStatus

    fun setDisplayStatus(eventId: Long, displayStatus: EventDisplayStatus) {
        val state = get(eventId)
        state.displayStatus = displayStatus
    }
}