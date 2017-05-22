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

package com.github.quarck.calnotify.calendar

data class MonitorEventAlertEntryKey(
        val eventId: Long,
        val alertTime: Long,
        val instanceStartTime: Long
)

data class MonitorEventAlertEntry(
        val calendarId: Long,
        val eventId: Long,
        val isAllDay: Boolean,
        val alertTime: Long,
        val instanceStartTime: Long,
        val instanceEndTime: Long,
        var alertCreatedByUs: Boolean,
        var wasHandled: Boolean // we should keep event alerts for a little bit longer to avoid double
                                // alerting when reacting to different notification sources
                                // (e.g. calendar provider vs our internal manual handler)
) {
    val key: MonitorEventAlertEntryKey
        get() = MonitorEventAlertEntryKey(eventId, alertTime, instanceStartTime)
}
