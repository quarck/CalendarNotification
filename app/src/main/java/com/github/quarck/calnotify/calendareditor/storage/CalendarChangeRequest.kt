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

package com.github.quarck.calnotify.calendareditor.storage

import com.github.quarck.calnotify.Consts

data class EventCreationRequestReminder(val time: Long, val isEmail: Boolean) {

    fun serialize() = "$time,${if(isEmail) 1 else 0}"

    companion object {
        fun deserialize(str: String): EventCreationRequestReminder {
            val (time, isEmail) = str.split(',')
            return EventCreationRequestReminder(
                    time = time.toLong(),
                    isEmail = isEmail.toInt() != 0
            )
        }
    }

    val allDayDaysBefore: Int
        get() = ((this.time + Consts.DAY_IN_MILLISECONDS) / Consts.DAY_IN_MILLISECONDS).toInt()

    val allDayHourOfDayAndMinute: Pair<Int, Int>
        get() {
            val timeOfDayMillis =
                    if (this.time >= 0L) { // on the day of event
                        Consts.DAY_IN_MILLISECONDS - this.time % Consts.DAY_IN_MILLISECONDS
                    }
                    else  {
                        -this.time
                    }

            val timeOfDayMinutes = timeOfDayMillis.toInt() / 1000 / 60

            val minute = timeOfDayMinutes % 60
            val hourOfDay = timeOfDayMinutes / 60

            return Pair(hourOfDay, minute)
        }
}

fun List<EventCreationRequestReminder>.serialize()
        = this.map { it.serialize() }.joinToString(separator = ";")

fun String.deserializeNewEventReminders()
        = this.split(";").filter { it != "" }.map { EventCreationRequestReminder.deserialize(it) }.toList()

enum class EventChangeStatus(val code: Int) {
    Dirty(0),
    ConfirmedInitially(1),
    ConfirmedSecond(2),
    ConfirmedFully(3);

    companion object {
        @JvmStatic
        fun fromInt(v: Int) = values()[v]
    }
}

enum class EventChangeRequestType(val code: Int) {
    AddNewEvent(0),
    MoveExistingEvent(1);

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
        val title: String,
        val desc: String,
        val location: String,
        val timezone: String,
        val startTime: Long,
        val endTime: Long,
        val isAllDay: Boolean,
        val reminders: List<EventCreationRequestReminder>,

        val repeatingRule: String = "", // empty if not repeating
        val repeatingRDate: String = "", // empty if not repeating
        val repeatingExRule: String = "", // empty if not repeating
        val repeatingExRDate: String = "", // empty if not repeating

        val colour: Int = 0,
        var status: EventChangeStatus = EventChangeStatus.Dirty,
        var lastStatusUpdate: Long = 0
) {
    fun onValidated(success: Boolean, time: Long = 0L): Boolean {

        val timeValidated = if (time != 0L) time else System.currentTimeMillis()

        if (!success) {
            status = EventChangeStatus.Dirty
            lastStatusUpdate = timeValidated
            return true
        }

        if (timeValidated - lastStatusUpdate < Consts.NEW_EVENT_MIN_STATUS_STEP_MILLIS) {
            return false
        }

        status =
                when (status) {
                    EventChangeStatus.Dirty ->
                        EventChangeStatus.ConfirmedInitially

                    EventChangeStatus.ConfirmedInitially ->
                        EventChangeStatus.ConfirmedSecond

                    EventChangeStatus.ConfirmedSecond ->
                        EventChangeStatus.ConfirmedFully

                    EventChangeStatus.ConfirmedFully ->
                        EventChangeStatus.ConfirmedFully
                }

        lastStatusUpdate = timeValidated

        return true
    }
}

