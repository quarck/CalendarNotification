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

package com.github.quarck.calnotify.calendar

data class EventAlertRecord(
    val calendarId: Long,
    val eventId: Long,
    var isAllDay: Boolean,
    var isRepeating: Boolean,
    var alertTime: Long,
    var notificationId: Int,
    var title: String,
    var startTime: Long,
    var endTime: Long,
    var instanceStartTime: Long,
    var instanceEndTime: Long,
    var location: String,
    var lastEventVisibility: Long,
    var snoozedUntil: Long = 0,
    var displayStatus: EventDisplayStatus = EventDisplayStatus.Hidden,
    var color: Int = 0
)


fun EventAlertRecord.updateFrom(newEvent: EventAlertRecord): Boolean {
    var ret = false

    if (title != newEvent.title) {
        title = newEvent.title
        ret = true
    }

    if (alertTime != newEvent.alertTime) {
        alertTime = newEvent.alertTime
        ret = true
    }

    if (startTime != newEvent.startTime) {
        startTime = newEvent.startTime
        ret = true
    }

    if (endTime != newEvent.endTime) {
        endTime = newEvent.endTime
        ret = true
    }

    if (isAllDay != newEvent.isAllDay) {
        isAllDay = newEvent.isAllDay
        ret = true
    }

    if (location != newEvent.location) {
        location = newEvent.location
        ret = true
    }

    if (color != newEvent.color) {
        color = newEvent.color
        ret = true
    }

    if (isRepeating != newEvent.isRepeating) { // only for upgrading from prev versions of DB
        isRepeating = newEvent.isRepeating
        ret = true
    }

    return ret
}

fun EventAlertRecord.updateFrom(newEvent: EventRecord): Boolean {
    var ret = false

    if (title != newEvent.title) {
        title = newEvent.title
        ret = true
    }

    if (startTime != newEvent.startTime) {
        startTime = newEvent.startTime
        ret = true
    }

    if (endTime != newEvent.endTime) {
        endTime = newEvent.endTime
        ret = true
    }

    if (color != newEvent.color) {
        color = newEvent.color
        ret = true
    }

    if (isAllDay != newEvent.isAllDay) {
        isAllDay = newEvent.isAllDay
        ret = true
    }

    return ret
}

val EventAlertRecord.displayedStartTime: Long
    get() = if (instanceStartTime != 0L) instanceStartTime else startTime

val EventAlertRecord.displayedEndTime: Long
    get() = if (instanceEndTime != 0L) instanceEndTime else endTime
