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

import android.provider.CalendarContract
import com.github.quarck.calnotify.Consts

data class EventReminderRecord(
        val millisecondsBefore: Long,
        val method: Int = CalendarContract.Reminders.METHOD_DEFAULT
) {
    fun serialize() = "$millisecondsBefore,$method"

    companion object {
        fun deserialize(str: String): EventReminderRecord {
            val (time, method) = str.split(',')
            return EventReminderRecord(
                    millisecondsBefore = time.toLong(),
                    method = method.toInt()
            )
        }

        fun minutes(mins: Int) = EventReminderRecord(mins * Consts.MINUTE_IN_MILLISECONDS)
    }
}

val EventReminderRecord.allDayDaysBefore: Int
    get() = ((this.millisecondsBefore + Consts.DAY_IN_MILLISECONDS) / Consts.DAY_IN_MILLISECONDS).toInt()

val EventReminderRecord.allDayHourOfDayAndMinute: Pair<Int, Int>
    get() {
        val timeOfDayMillis =
                if (this.millisecondsBefore >= 0L) { // on the day of event
                    Consts.DAY_IN_MILLISECONDS - this.millisecondsBefore % Consts.DAY_IN_MILLISECONDS
                }
                else  {
                    -this.millisecondsBefore
                }

        val timeOfDayMinutes = timeOfDayMillis.toInt() / 1000 / 60

        val minute = timeOfDayMinutes % 60
        val hourOfDay = timeOfDayMinutes / 60

        return Pair(hourOfDay, minute)
    }


fun List<EventReminderRecord>.serialize()
        = this.map { it.serialize() }.joinToString(separator = ";")

fun String.deserializeCalendarEventReminders()
        = this.split(";").filter { it != "" }.map { EventReminderRecord.deserialize(it) }.toList()

data class CalendarEventDetails(
        val title: String,
        val desc: String,
        val location: String,

        val timezone: String,

        val startTime: Long,
        val endTime: Long,

        val isAllDay: Boolean,

        var reminders: List<EventReminderRecord>,

        val repeatingRule: String = "", // empty if not repeating
        val repeatingRDate: String = "", // empty if not repeating
        val repeatingExRule: String = "", // empty if not repeating
        val repeatingExRDate: String = "", // empty if not repeating

        val color: Int = 0
) {
    companion object {
        fun createEmpty() = CalendarEventDetails(
                desc = "",
                location = "",
                timezone = "",
                startTime = 0,
                endTime = 0,
                isAllDay = false,
                reminders = listOf<EventReminderRecord>(), title = ""
        )
    }
}

data class EventRecord(
        val calendarId: Long,
        val eventId: Long,
        val details: CalendarEventDetails,
        var eventStatus: EventStatus = EventStatus.Confirmed,
        var attendanceStatus: AttendanceStatus = AttendanceStatus.None
) {
    val title: String get() = details.title
    val desc: String get() = details.desc

    val location: String get() = details.location

    val timezone: String get() = details.timezone

    val startTime: Long get() = details.startTime
    val endTime: Long get() = details.endTime

    val isAllDay: Boolean get() = details.isAllDay

    var reminders: List<EventReminderRecord>
        get() = details.reminders
        set(value) {
            details.reminders = value
        }

    val repeatingRule: String get() = details.repeatingRule
    val repeatingRDate: String get() = details.repeatingRDate
    val repeatingExRule: String get() = details.repeatingExRule
    val repeatingExRDate: String get() = details.repeatingExRDate

    val color: Int get() = details.color
}


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