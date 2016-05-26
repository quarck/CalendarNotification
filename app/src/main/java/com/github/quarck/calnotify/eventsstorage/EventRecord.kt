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

enum class EventDisplayStatus(val code: Int) {
    Hidden(0),
    DisplayedNormal(1),
    DisplayedCollapsed(2);

    companion object {
        fun fromInt(v: Int) = EventDisplayStatus.values()[v]
    }
}

data class EventRecord(
        val eventId: Long,
        val alertTime: Long,
        /// var notificationId: Int,
        var title: String,
        var startTime: Long,
        var endTime: Long,
        var location: String,
        var lastEventVisibility: Long,
        var snoozedUntil: Long = 0,
        // var displayStatus: EventDisplayStatus = EventDisplayStatus.Hidden,
        var color: Int = 0
) {
    fun updateFrom(newEvent: EventRecord): Boolean {
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

        if (location != newEvent.location) {
            location = newEvent.location
            ret = true
        }

        if (color != newEvent.color) {
            color = newEvent.color
            ret = true
        }

        return ret
    }
}
