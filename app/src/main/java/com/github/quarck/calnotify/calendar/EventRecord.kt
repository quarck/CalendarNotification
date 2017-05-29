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

data class EventReminderRecord(val millisecondsBefore: Long, val method: Int)

data class EventRecord(
        val calendarId: Long,
        val eventId: Long,
        var isAllDay: Boolean,
        var title: String,
        var startTime: Long,
        var endTime: Long,
        var reminders: List<EventReminderRecord>,
        var location: String,
        var color: Int = 0,
        var eventStatus: EventStatus = EventStatus.Confirmed,
        var attendanceStatus: AttendanceStatus = AttendanceStatus.None
)

fun EventRecord.nextAlarmTime(currentTime: Long): Long {
    var ret = 0L

    for (reminder in reminders) {
        val reminderTime = startTime - reminder.millisecondsBefore

        if (ret == 0L) {
            // First entry - simply store
            ret = reminderTime
        }
        else if ((ret > currentTime) && (reminderTime > currentTime)) {
            // Both in the future - look for the closest time
            if (reminderTime < ret)
                ret = reminderTime
        }
        else if ((ret <= currentTime) && (reminderTime > currentTime)) {
            // current in the future, 'ret' in the past - update ret
            ret = reminderTime
        }
        else if ((ret > currentTime) && (reminderTime <= currentTime)) {
            //  'ret' is in the future, current in the past - ignore
        }
        else if ((ret <= currentTime) && (reminderTime <= currentTime)) {
            // both 'ret' and current are in the past - pick most recent
            if (reminderTime > ret)
                ret = reminderTime
        }
    }

    return ret
}