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
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.calendar.displayedEndTime
import com.github.quarck.calnotify.calendar.displayedStartTime
import com.github.quarck.calnotify.utils.dayEquals
import java.text.DateFormat
import java.util.*

object EventRecordUtils {
    private const val oneDay = 24L * 3600L * 1000L;

    fun dayName(ctx: Context, time: Long, formatter: DateFormat): String {
        val ret: String;

        if (DateUtils.isToday(time)) {
            ret = ctx.resources.getString(R.string.today);
        } else if (DateUtils.isToday(time - oneDay)) {
            ret = ctx.resources.getString(R.string.tomorrow);
        } else {
            ret = formatter.format(Date(time));
        }

        return ret;
    }
}

fun EventAlertRecord.formatText(ctx: Context): String {
    val sb = StringBuilder()

    if (this.displayedStartTime != 0L) {

        val currentTime = System.currentTimeMillis();

        val today = Date(currentTime)
        val start = Date(this.displayedStartTime)
        val end = Date(this.displayedEndTime)

        val currentDay = Calendar.getInstance()
        val startDay = Calendar.getInstance()
        val endDay = Calendar.getInstance()

        currentDay.time = today
        startDay.time = start
        endDay.time = end

        val dateFormatter = DateFormat.getDateInstance(DateFormat.SHORT)
        val timeFormatter = DateFormat.getTimeInstance(DateFormat.SHORT)

        if (!currentDay.dayEquals(startDay)) {

            if (startDay.dayEquals(endDay))
                sb.append(EventRecordUtils.dayName(ctx, displayedStartTime, dateFormatter));
            else
                sb.append(dateFormatter.format(start));

            sb.append(" ");
        }

        sb.append(timeFormatter.format(start));

        if (displayedEndTime != 0L) {

            sb.append(" - ");

            if (!endDay.dayEquals(startDay)) {
                sb.append(dateFormatter.format(end))
                sb.append(" ");
            }

            sb.append(timeFormatter.format(end))
        }
    }

    if (this.location != "") {
        sb.append("\n")
        sb.append(ctx.resources.getString(R.string.location));
        sb.append(this.location)
    }

    return sb.toString()
}

fun EventAlertRecord.formatTime(ctx: Context): Pair<String, String> {
    val sbDay = StringBuilder()
    val sbTime = StringBuilder();

    if (this.displayedStartTime != 0L) {
        val currentTime = System.currentTimeMillis();

        val today = Date(currentTime)
        val start = Date(this.displayedStartTime)
        val end = Date(this.displayedEndTime)

        val currentDay = Calendar.getInstance()
        val startDay = Calendar.getInstance()
        val endDay = Calendar.getInstance()

        currentDay.time = today
        startDay.time = start
        endDay.time = end

        val timeFormatter = DateFormat.getTimeInstance(DateFormat.SHORT)

        sbTime.append(timeFormatter.format(Date(this.displayedStartTime)));

        if (this.displayedEndTime != 0L && endDay.dayEquals(startDay)) {
            sbTime.append(" - ");
            sbTime.append(timeFormatter.format(Date(this.displayedEndTime)))
        }

        sbDay.append(
                EventRecordUtils.dayName(
                        ctx,
                    displayedStartTime, DateFormat.getDateInstance(DateFormat.FULL)
                )
        );
    }

    return Pair(sbDay.toString(), sbTime.toString());
}

fun EventAlertRecord.shortFormatDayOrTime(ctx: Context): String {
    val sb = StringBuilder()

    if (this.displayedStartTime != 0L) {
        val currentTime = System.currentTimeMillis();

        val today = Date(currentTime)
        val start = Date(this.displayedStartTime)

        val currentDay = Calendar.getInstance()
        val startDay = Calendar.getInstance()

        currentDay.time = today
        startDay.time = start

        if (currentDay.dayEquals(startDay)) {
            val timeFormatter = DateFormat.getTimeInstance(DateFormat.SHORT)
            sb.append(timeFormatter.format(Date(this.displayedStartTime)))
        } else {
            val dateFormatter = DateFormat.getDateInstance(DateFormat.SHORT)
            sb.append(dateFormatter.format(Date(this.displayedStartTime)))
        }
    }

    return sb.toString()
}

fun EventAlertRecord.formatSnoozedUntil(ctx: Context): String {
    var sb = StringBuilder();

    if (snoozedUntil != 0L) {
        var currentTime = System.currentTimeMillis();

        val currentDay = Calendar.getInstance()
        var snoozedDay = Calendar.getInstance()

        currentDay.time = Date(currentTime)
        snoozedDay.time = Date(this.snoozedUntil)

        var timeFormatter = DateFormat.getTimeInstance(DateFormat.SHORT)

        if (!snoozedDay.dayEquals(currentDay)) {
            sb.append(
                    EventRecordUtils.dayName(
                            ctx,
                            snoozedUntil, DateFormat.getDateInstance(DateFormat.SHORT)
                    )
            );
            sb.append(" ");
        }

        sb.append(timeFormatter.format(Date(snoozedUntil)));
    }

    return sb.toString();
}

