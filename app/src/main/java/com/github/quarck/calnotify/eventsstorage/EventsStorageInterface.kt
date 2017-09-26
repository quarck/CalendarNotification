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

package com.github.quarck.calnotify.eventsstorage

import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.calendar.EventDisplayStatus

data class EventWithNewInstanceTime(
        val event: EventAlertRecord,
        val newInstanceStartTime: Long,
        val newInstanceEndTime: Long
)

interface EventsStorageInterface {

    fun addEvent(event: EventAlertRecord): Boolean

    fun addEvents(events: List<EventAlertRecord>): Boolean

    fun updateEvent(
            event: EventAlertRecord,
            alertTime: Long? = null,
            title: String? = null,
            snoozedUntil: Long? = null,
            startTime: Long? = null,
            endTime: Long? = null,
            location: String? = null,
            lastStatusChangeTime: Long? = null,
            displayStatus: EventDisplayStatus? = null,
            color: Int? = null,
            isRepeating: Boolean? = null,
            isMuted: Boolean? = null): Pair<Boolean, EventAlertRecord>

    fun updateEvents(
            events: List<EventAlertRecord>,
            alertTime: Long? = null,
            title: String? = null,
            snoozedUntil: Long? = null,
            startTime: Long? = null,
            endTime: Long? = null,
            location: String? = null,
            lastStatusChangeTime: Long? = null,
            displayStatus: EventDisplayStatus? = null,
            color: Int? = null,
            isRepeating: Boolean? = null): Boolean

    fun updateEventAndInstanceTimes(event: EventAlertRecord, instanceStart: Long, instanceEnd: Long): Boolean

    fun updateEventsAndInstanceTimes(events: Collection<EventWithNewInstanceTime>): Boolean

    fun updateEvent(event: EventAlertRecord): Boolean

    fun updateEvents(events: List<EventAlertRecord>): Boolean

    fun getEvent(eventId: Long, instanceStartTime: Long): EventAlertRecord?

    fun getEventInstances(eventId: Long): List<EventAlertRecord>

    fun deleteEvent(eventId: Long, instanceStartTime: Long): Boolean

    fun deleteEvent(ev: EventAlertRecord): Boolean

    fun deleteEvents(events: Collection<EventAlertRecord>): Int

    val events: List<EventAlertRecord> get
}