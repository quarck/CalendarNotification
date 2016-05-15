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

import android.content.Context
import android.text.format.DateUtils
import com.github.quarck.calnotify.R
import java.text.DateFormat
import java.util.*

object EventRecordUtils {
    private const val oneDay = 24L * 3600L * 1000L;

    fun dayName(ctx: Context, time: Long, formatter: DateFormat): String {
        var ret: String = "";

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

fun Calendar.dayEquals(other: Calendar)
        = this.get(Calendar.YEAR) == other.get(Calendar.YEAR) &&
            this.get(Calendar.DAY_OF_YEAR) == other.get(Calendar.DAY_OF_YEAR);

fun areDatesOnSameDay(date1: Long, date2: Long): Boolean {

    var cal1 = Calendar.getInstance()
    var cal2 = Calendar.getInstance()

    cal1.time = Date(date1)
    cal2.time = Date(date2)

    return cal1.dayEquals(cal2)
}


fun EventRecord.formatText(ctx: Context): String {
    var sb = StringBuilder()

    if (this.startTime != 0L) {

        var currentTime = System.currentTimeMillis();

        var today = Date(currentTime)
        var start = Date(this.startTime)
        var end = Date(this.endTime)

        val currentDay = Calendar.getInstance()
        var startDay = Calendar.getInstance()
        var endDay = Calendar.getInstance()

        currentDay.time = today
        startDay.time = start
        endDay.time = end

        var dateFormatter = DateFormat.getDateInstance(DateFormat.SHORT)
        var timeFormatter = DateFormat.getTimeInstance(DateFormat.SHORT)

        if (!currentDay.dayEquals(startDay)) {

            if (startDay.dayEquals(endDay))
                sb.append(EventRecordUtils.dayName(ctx, startTime, dateFormatter));
            else
                sb.append(dateFormatter.format(start));

            sb.append(" ");
        }

        sb.append(timeFormatter.format(start));

        if (endTime != 0L) {

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

fun EventRecord.formatTime(ctx: Context): Pair<String, String> {
    var sbDay = StringBuilder()
    var sbTime = StringBuilder();

    if (this.startTime != 0L) {
        var currentTime = System.currentTimeMillis();

        var today = Date(currentTime)
        var start = Date(this.startTime)
        var end = Date(this.endTime)

        val currentDay = Calendar.getInstance()
        var startDay = Calendar.getInstance()
        var endDay = Calendar.getInstance()

        currentDay.time = today
        startDay.time = start
        endDay.time = end

        var timeFormatter = DateFormat.getTimeInstance(DateFormat.SHORT)

        sbTime.append(timeFormatter.format(Date(this.startTime)));

        if (endTime != 0L && endDay.dayEquals(startDay)) {
            sbTime.append(" - ");
            sbTime.append(timeFormatter.format(Date(this.endTime)))
        }

        sbDay.append(
                EventRecordUtils.dayName(
                        ctx,
                        startTime, DateFormat.getDateInstance(DateFormat.FULL)
                )
        );
    }

    return Pair(sbDay.toString(), sbTime.toString());
}

fun EventRecord.formatSnoozedUntil(ctx: Context): String {
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

