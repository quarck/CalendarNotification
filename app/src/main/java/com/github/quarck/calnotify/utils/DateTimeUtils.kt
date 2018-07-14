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

    val utcTimeZoneName = "UTC"

    val utcTimeZone: TimeZone by lazy { java.util.TimeZone.getTimeZone(utcTimeZoneName) }

    fun createUTCCalendarTime(timeMillis: Long): Calendar {
        val ret = Calendar.getInstance(utcTimeZone)
        ret.timeInMillis = timeMillis
        return ret
    }

    fun createUTCCalendarDate(year: Int, month: Int, dayOfMonth: Int): Calendar {
        val ret = Calendar.getInstance(utcTimeZone)
        ret.set(Calendar.YEAR, year)
        ret.set(Calendar.MONTH, month)
        ret.set(Calendar.DAY_OF_MONTH, dayOfMonth)
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

    fun calendarDayEquals(left: Calendar, right: Calendar) =
            left.get(Calendar.YEAR) == right.get(Calendar.YEAR)
                    && left.get(Calendar.DAY_OF_YEAR) == right.get(Calendar.DAY_OF_YEAR)

    fun calendarDayEqualsOrLess(left: Calendar, right: Calendar): Boolean {
        val leftYear = left.get(Calendar.YEAR)
        val rightYear = right.get(Calendar.YEAR)

        if (leftYear < rightYear)
            return true
        if (leftYear > rightYear)
            return false

        val leftDay = left.get(Calendar.DAY_OF_YEAR)
        val rightDay = right.get(Calendar.DAY_OF_YEAR)

        if (leftDay <= rightDay)
            return true
        return false
    }

    fun calendarDayEquals(timeMillisLeft: Long, timeMillisRight: Long) =
            calendarDayEquals(createCalendarTime(timeMillisLeft), createCalendarTime(timeMillisRight))

    fun calendarDayUTCEquals(timeMillisLeft: Long, timeMillisRight: Long) =
            calendarDayEquals(createUTCCalendarTime(timeMillisLeft), createUTCCalendarTime(timeMillisRight))

    // very special case required for calendar full-day requests, such requests
    // are stored in UTC format, so to check if event is today we have to
    // convert current date in local time zone into year / day of year and compare
    // it with event time in UTC converted to year / day of year
    fun isUTCToday(timeInUTC: Long) =
            calendarDayEquals(createUTCCalendarTime(timeInUTC), createCalendarTime(System.currentTimeMillis()))

    fun isUTCTodayOrInThePast(timeInUTC: Long): Boolean {
        val time = createUTCCalendarTime(timeInUTC)
        val now = createCalendarTime(System.currentTimeMillis())
        return calendarDayEqualsOrLess(time, now)
    }
}

var Calendar.year: Int
    get() = this.get(Calendar.YEAR)
    set(value) = this.set(Calendar.YEAR, value)

var Calendar.month: Int
    get() = this.get(Calendar.MONTH)
    set(value) = this.set(Calendar.MONTH, value)

var Calendar.dayOfMonth: Int
    get() = this.get(Calendar.DAY_OF_MONTH)
    set(value) = this.set(Calendar.DAY_OF_MONTH, value)

var Calendar.hourOfDay: Int
    get() = this.get(Calendar.HOUR_OF_DAY)
    set(value) = this.set(Calendar.HOUR_OF_DAY, value)

var Calendar.minute: Int
    get() = this.get(Calendar.MINUTE)
    set(value) = this.set(Calendar.MINUTE, value)

var Calendar.second: Int
    get() = this.get(Calendar.SECOND)
    set(value) = this.set(Calendar.SECOND, value)

fun Calendar.addDays(days: Int) {
    this.add(Calendar.DATE, days)
}

fun Calendar.addHours(hours: Int) {
    this.add(Calendar.HOUR, hours)
}

fun Calendar.addMinutes(minutes: Int) {
    this.add(Calendar.MINUTE, minutes)
}
