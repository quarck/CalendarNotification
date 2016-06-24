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

package com.github.quarck.calnotify.utils

import java.util.*

object DateTimeUtils {

    val timeZoneName = "UTC"

    val utcTimeZone: TimeZone by lazy { java.util.TimeZone.getTimeZone(timeZoneName) }

    fun createUTCCalendarTime(timeMillis: Long): Calendar {
        val ret = Calendar.getInstance(utcTimeZone)
        ret.timeInMillis = timeMillis
        return ret
    }

    fun createCalendarTime(timeMillis: Long, hour: Int, minute: Int): Calendar {
        val ret = Calendar.getInstance()
        ret.timeInMillis = timeMillis
        ret.set(Calendar.HOUR_OF_DAY, hour)
        ret.set(Calendar.MINUTE, minute)
        ret.set(Calendar.SECOND, 0)
        return ret
    }

    fun createCalendarTime(timeMillis: Long): Calendar {
        val ret = Calendar.getInstance()
        ret.timeInMillis = timeMillis
        return ret
    }

    fun calendarDayEquals(left: Calendar, right: Calendar)
        = left.get(Calendar.YEAR) == right.get(Calendar.YEAR) &&
        left.get(Calendar.DAY_OF_YEAR) == right.get(Calendar.DAY_OF_YEAR);

    fun calendarDayEquals(timeMillisLeft: Long, timeMillisRight: Long) =
        calendarDayEquals(createCalendarTime(timeMillisLeft), createCalendarTime(timeMillisRight))


    // very special case required for calendar full-day events, such events
    // are stored in UTC format, so to check if event is today we have to
    // convert current date in local time zone into year / day of year and compare
    // it with event time in UTC converted to year / day of year
    fun isUTCToday(timeInUTC: Long) =
        calendarDayEquals(createUTCCalendarTime(timeInUTC), createCalendarTime(System.currentTimeMillis()))
}