//
//   Calendar Notifications Plus
//   Copyright (C) 2017 Sergey Parshin (s.parshin.sc@gmail.com)
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

package com.github.quarck.calnotify.calendareditor

import com.github.quarck.calnotify.calendar.CalendarEventDetails


enum class EventChangeStatus(val code: Int) {
    Dirty(0),
    Synced(1),
    Failed(1000);

    companion object {
        @JvmStatic
        fun fromInt(v: Int) = values()[v]
    }
}

enum class EventChangeRequestType(val code: Int) {
    AddNewEvent(0),
    MoveExistingEvent(1),
    EditExistingEvent(2);

    companion object {
        @JvmStatic
        fun fromInt(v: Int) = values()[v]
    }
}

data class CalendarChangeRequest(
        var id: Long,
        var type: EventChangeRequestType,
        var eventId: Long,
        val calendarId: Long,

        val details: CalendarEventDetails,
        val oldDetails: CalendarEventDetails,

        var status: EventChangeStatus = EventChangeStatus.Dirty,
        var lastStatusUpdate: Long = 0,
        var numRetries: Int = 0,
        var lastRetryTime: Long = 0
) {
    fun onValidated(success: Boolean) {

        lastStatusUpdate = System.currentTimeMillis()

        status = if (success) EventChangeStatus.Synced else EventChangeStatus.Dirty
    }
}

