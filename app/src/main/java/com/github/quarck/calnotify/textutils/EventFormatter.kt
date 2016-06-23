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

package com.github.quarck.calnotify.textutils

import android.content.Context
import android.text.format.DateUtils
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.calendar.displayedEndTime
import com.github.quarck.calnotify.calendar.displayedStartTime
import com.github.quarck.calnotify.utils.dayEquals
import com.github.quarck.calnotify.utils.sameCalendarDay
import java.text.DateFormat
import java.util.*

class EventFormatter(val ctx: Context): EventFormatterInterface {

    fun getDayName(ctx: Context, time: Long, showWeekDay: Boolean) =
        when {
            DateUtils.isToday(time) ->
                ctx.resources.getString(R.string.today)

            DateUtils.isToday(time - Consts.DAY_IN_MILLISECONDS) ->
                ctx.resources.getString(R.string.tomorrow)

            else ->
                DateUtils.formatDateTime(
                    ctx, time,
                    DateUtils.FORMAT_SHOW_DATE or
                        (if (showWeekDay) DateUtils.FORMAT_SHOW_WEEKDAY else 0))
        }

    override fun formatNotificationSecondaryText(event: EventAlertRecord): String {
        val sb = StringBuilder()

        sb.append(formatDateTimeOneLineRegular(event, false))

        if (event.location != "") {
            sb.append("\n")
            sb.append(ctx.resources.getString(R.string.location));
            sb.append(" ")
            sb.append(event.location)
        }

        return sb.toString()
    }


    override fun formatDateTimeTwoLines(event: EventAlertRecord, showWeekDay: Boolean): Pair<String, String>  =
        when {
            event.isAllDay ->
                formatDateTimeTwoLinesAllDay(event, showWeekDay)

            else ->
                formatDateTimeTwoLinesRegular(event, showWeekDay)
        }

    private fun formatDateTimeTwoLinesRegular(event: EventAlertRecord, showWeekDay: Boolean = true): Pair<String, String> {

        val startTime = event.displayedStartTime
        val endTime = event.displayedEndTime

        val startIsToday = DateUtils.isToday(startTime)
        val endIsToday = DateUtils.isToday(endTime)

        val weekFlag = if (showWeekDay) DateUtils.FORMAT_SHOW_WEEKDAY else 0

        val line1: String
        val line2: String

        if (startIsToday && endIsToday) {
            // Today
            line1 = ctx.resources.getString(R.string.today)
            line2 = DateUtils.formatDateRange(
                ctx, startTime, endTime, DateUtils.FORMAT_SHOW_TIME)

        } else if (startIsToday && (endTime == 0L)) {
            // Starts today, but have no end time
            line1 = ctx.resources.getString(R.string.today)
            line2 = DateUtils.formatDateTime(
                ctx, startTime, DateUtils.FORMAT_SHOW_TIME)

        } else {
            // Not today...
            val startIsTomorrow = DateUtils.isToday(startTime - Consts.DAY_IN_MILLISECONDS)
            val endIsTomorrow = DateUtils.isToday(endTime - Consts.DAY_IN_MILLISECONDS)

            if (startIsTomorrow && endIsTomorrow) {
                // tomorrow
                line1 = ctx.resources.getString(R.string.tomorrow)
                line2 = DateUtils.formatDateRange(
                        ctx, startTime, endTime, DateUtils.FORMAT_SHOW_TIME)
            } else if (startIsTomorrow && (endTime == 0L)) {
                // tomorrow, no end time
                line1 = ctx.resources.getString(R.string.tomorrow)
                line2 = DateUtils.formatDateTime(
                        ctx, startTime, DateUtils.FORMAT_SHOW_TIME)
            } else {
                // not tomorrow...
                if (sameCalendarDay(startTime, endTime)) {
                    // but same start and and days (so end can't be zero)
                    line1 = DateUtils.formatDateTime(
                        ctx, startTime, DateUtils.FORMAT_SHOW_DATE or weekFlag)

                    line2 = DateUtils.formatDateRange(
                        ctx, startTime, endTime,
                        DateUtils.FORMAT_SHOW_TIME)
                } else {
                    line1 = DateUtils.formatDateTime(
                        ctx, startTime,
                        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or weekFlag)

                    line2 =
                        if (endTime != 0L)
                            DateUtils.formatDateTime(
                                ctx, endTime,
                                DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or weekFlag)
                        else
                            ""
                }
            }
        }

        return Pair(line1, line2)
    }

    private fun formatDateTimeTwoLinesAllDay(event: EventAlertRecord, showWeekDay: Boolean = true): Pair<String, String> {

        val ret: Pair<String, String>

        val startTime = event.displayedStartTime
        val endTime = event.displayedEndTime

        val startIsToday = DateUtils.isToday(startTime)
        val endIsToday = DateUtils.isToday(endTime)

        val weekFlag = if (showWeekDay) DateUtils.FORMAT_SHOW_WEEKDAY else 0

        if (startIsToday && (endIsToday || endTime == 0L)) {
            // full day one-day event that is today
            ret = Pair(ctx.resources.getString(R.string.today), "")
        } else {
            val startIsTomorrow = DateUtils.isToday(startTime - Consts.DAY_IN_MILLISECONDS)
            val endIsTomorrow = DateUtils.isToday(endTime - Consts.DAY_IN_MILLISECONDS)

            if (startIsTomorrow && (endIsTomorrow || endTime == 0L)) {
                // full-day one-day event that is tomorrow
                ret = Pair(ctx.resources.getString(R.string.tomorrow), "")
            } else {
                // otherwise -- format full range if we have end time, or just start time
                // if we only have start time
                if (endTime != 0L) {
                    val from = DateUtils.formatDateTime(
                        ctx, startTime, DateUtils.FORMAT_SHOW_DATE or weekFlag)

                    val to = DateUtils.formatDateTime(
                        ctx, endTime, DateUtils.FORMAT_SHOW_DATE or weekFlag);

                    val hyp = ctx.resources.getString(R.string.hyphen)

                    ret = Pair("$from $hyp", to)
                } else {
                    ret = Pair(DateUtils.formatDateTime(
                            ctx, startTime, DateUtils.FORMAT_SHOW_DATE or weekFlag), "")
                }
            }
        }

        return ret
    }

    override fun formatDateTimeOneLine(event: EventAlertRecord, showWeekDay: Boolean) =
        when {
            event.isAllDay ->
                formatDateTimeOneLineAllDay(event, showWeekDay)
            else ->
                formatDateTimeOneLineRegular(event, showWeekDay)
        }

    private fun formatDateTimeOneLineRegular(event: EventAlertRecord, showWeekDay: Boolean = false): String {

        val startTime = event.displayedStartTime
        val endTime = event.displayedEndTime

        val startIsToday = DateUtils.isToday(startTime)
        val endIsToday = DateUtils.isToday(endTime)

        val weekFlag = if (showWeekDay) DateUtils.FORMAT_SHOW_WEEKDAY else 0

        val ret: String

        if (startIsToday && endIsToday) {
            ret = DateUtils.formatDateRange(ctx, startTime, endTime, DateUtils.FORMAT_SHOW_TIME)
        } else if (startIsToday && (endTime == 0L)) {
            ret = DateUtils.formatDateTime(ctx, startTime, DateUtils.FORMAT_SHOW_TIME)
        } else {

            val startIsTomorrow = DateUtils.isToday(startTime - Consts.DAY_IN_MILLISECONDS)
            val endIsTomorrow = DateUtils.isToday(endTime - Consts.DAY_IN_MILLISECONDS)

            if (startIsTomorrow && endIsTomorrow) {
                ret = ctx.resources.getString(R.string.tomorrow) + " " +
                    DateUtils.formatDateRange(ctx, startTime, endTime, DateUtils.FORMAT_SHOW_TIME)
            } else if (startIsTomorrow && (endTime == 0L)) {
                ret = ctx.resources.getString(R.string.tomorrow) + " " +
                    DateUtils.formatDateTime(ctx, startTime, DateUtils.FORMAT_SHOW_TIME)
            } else {

                if (endTime != 0L) {
                    ret = DateUtils.formatDateRange(
                        ctx, startTime, endTime,
                        DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_DATE or weekFlag)
                } else {
                    ret = DateUtils.formatDateTime(
                        ctx, startTime,
                        DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_DATE or weekFlag)
                }
            }
        }

        return ret
    }

    private fun formatDateTimeOneLineAllDay(event: EventAlertRecord, showWeekDay: Boolean = false): String {

        val startTime = event.displayedStartTime
        val endTime = event.displayedEndTime

        val startIsToday = DateUtils.isToday(startTime)
        val endIsToday = DateUtils.isToday(endTime)

        val weekFlag = if (showWeekDay) DateUtils.FORMAT_SHOW_WEEKDAY else 0

        val ret: String

        if (startIsToday && (endIsToday || endTime == 0L)) {
            // full day one-day event that is today
            ret = ctx.resources.getString(R.string.today)
        } else {
            val startIsTomorrow = DateUtils.isToday(startTime - Consts.DAY_IN_MILLISECONDS)
            val endIsTomorrow = DateUtils.isToday(endTime - Consts.DAY_IN_MILLISECONDS)

            if (startIsTomorrow && (endIsTomorrow || endTime == 0L)) {
                // full-day one-day event that is tomorrow
                ret = ctx.resources.getString(R.string.tomorrow)
            } else {
                // otherwise -- format full range if we have end time, or just start time
                // if we only have start time
                if (endTime != 0L) {
                    ret = DateUtils.formatDateRange(ctx, startTime, endTime,
                        DateUtils.FORMAT_SHOW_DATE or weekFlag)
                } else {
                    ret = DateUtils.formatDateTime(ctx, startTime,
                        DateUtils.FORMAT_SHOW_DATE or weekFlag)
                }
            }
        }

        return ret
    }

    override fun formatSnoozedUntil(event: EventAlertRecord): String {

        val ret: String

        if (event.snoozedUntil != 0L) {
            var flags = DateUtils.FORMAT_SHOW_TIME

            if (!DateUtils.isToday(event.snoozedUntil))
                flags = flags or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY

            if ((event.snoozedUntil - System.currentTimeMillis()) / (Consts.DAY_IN_MILLISECONDS * 30) >= 3L) // over 3mon - show year
                flags = flags or DateUtils.FORMAT_SHOW_YEAR

            ret = DateUtils.formatDateTime(ctx, event.snoozedUntil, flags)
        } else {
            ret = ""
        }

        return ret
    }

}