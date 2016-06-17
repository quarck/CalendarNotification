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

package com.github.quarck.calnotify.quiethours

import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.logs.Logger
import com.github.quarck.calnotify.utils.createCalendarTime
import java.util.*

object QuietHoursManager: QuietHoursManagerInterface {
    var logger = Logger("QuietPeriodManager")

    private fun isEnabled(settings: Settings)
            = settings.quietHoursEnabled && (settings.quietHoursFrom != settings.quietHoursTo)

    override fun isInsideQuietPeriod(settings: Settings, time: Long) =
        getSilentUntil(settings, time) > 0L

    override fun isInsideQuietPeriod(settings: Settings, currentTimes: LongArray) =
        getSilentUntil(settings, currentTimes).map { it -> it > 0L }.toBooleanArray()

    // returns time in millis, when silent period ends,
    // or 0 if we are not on silent
    override fun getSilentUntil(settings: Settings, time: Long): Long {
        var ret: Long = 0

        if (!isEnabled(settings))
            return 0

        val currentTime = if (time != 0L) time else System.currentTimeMillis()

        val cal = Calendar.getInstance()
        cal.timeInMillis = currentTime

        val from = settings.quietHoursFrom
        var silentFrom = createCalendarTime(currentTime, from.component1(), from.component2())

        val to = settings.quietHoursTo
        var silentTo = createCalendarTime(currentTime, to.component1(), to.component2())

        logger.debug("getSilentUntil: ct=$currentTime, $from to $to");

        // Current silent period could have started yesterday, so account for this by rolling it back to one day
        silentFrom.add(Calendar.DATE, -1);
        silentTo.add(Calendar.DATE, -1);

        // Check if "from" is before "to", otherwise add an extra day to "to"
        if (silentTo.before(silentFrom))
            silentTo.add(Calendar.DATE, 1);

        var cnt = 0

        while (silentFrom.before(cal)) {

            if (cal.after(silentFrom) && cal.before(silentTo)) {
                // this hits silent period -- so it should be silent until 'silentTo'
                ret = silentTo.timeInMillis
                logger.debug("Time hits silent period range, would be silent for ${(ret - currentTime) / 1000L} seconds since expected wake up time")
                break;
            }

            silentFrom.add(Calendar.DATE, 1);
            silentTo.add(Calendar.DATE, 1);

            if (++cnt > 1000)
                break;
        }

        return ret
    }

    override fun getSilentUntil(settings: Settings, currentTimes: LongArray): LongArray {

        var ret = LongArray(currentTimes.size)

        if (!isEnabled(settings))
            return ret

        if (ret.size == 0)
            return ret

        val cals =
            Array<Calendar>(ret.size, {
                idx ->
                var cal = Calendar.getInstance()
                cal.timeInMillis = currentTimes[idx]
                cal
            })

        val from = settings.quietHoursFrom
        var silentFrom: Calendar = createCalendarTime(currentTimes[0], from.component1(), from.component2())

        val to = settings.quietHoursTo
        var silentTo = createCalendarTime(currentTimes[0], to.component1(), to.component2())

        // Current silent period could have started yesterday, so account for this by rolling it back to one day
        silentFrom.add(Calendar.DATE, -1);
        silentTo.add(Calendar.DATE, -1);

        // Check if "from" is before "to", otherwise add an extra day to "to"
        if (silentTo.before(silentFrom))
            silentTo.add(Calendar.DATE, 1);

        var cnt = 0

        while (true) {

            var allPassed = true

            logger.debug("Iterating $silentFrom")

            for ( (idx, cal) in cals.withIndex()) {

                logger.debug("Idx=$idx, cal=$cal")

                if (silentFrom.before(cal)) {
                    logger.debug("Not passed")
                    allPassed = false
                }

                if (cal.after(silentFrom) && cal.before(silentTo))
                    // this hits silent period -- so it should be silent until 'silentTo'
                    ret[idx] = silentTo.timeInMillis
            }

            if (allPassed)
                break

            silentFrom.add(Calendar.DATE, 1);
            silentTo.add(Calendar.DATE, 1);

            if (++cnt > 1000)
                break;
        }

        return ret
    }

}
