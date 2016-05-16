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
import java.util.*

object QuietHoursManager
{
    var logger = Logger("QuietPeriodManager")

    private const val MINUTES_IN_HOUR = 60
    private const val MINUTES_IN_DAY = 24 * 60

	fun isEnabled(settings: Settings)
            = settings.quietHoursEnabled && (settings.quietHoursFrom != settings.quietHoursTo)

	// returns time in millis, when silent period ends, 
	// or 0 if we are not on silent 
	fun getSilentUntil(settings: Settings, time: Long = 0L): Long
	{
        var ret: Long = 0

        if (!isEnabled(settings))
			return 0

		val cal = Calendar.getInstance()
        if (time != 0L)
            cal.time = Date(time)

		val hour = cal.get(Calendar.HOUR_OF_DAY)
		val minute = cal.get(Calendar.MINUTE)

        val currentTm = hour * MINUTES_IN_HOUR + minute

		val from = settings.quietHoursFrom
		val to = settings.quietHoursTo

        val fromTm = from.component1() * MINUTES_IN_HOUR + from.component2()
        var toTm = to.component1() * MINUTES_IN_HOUR + to.component2()


		logger.debug("have silent period from $from ($fromTm) to $to ($toTm), current tm $currentTm")

		if (toTm < fromTm)
			toTm += MINUTES_IN_DAY

		if (inRange(currentTm, fromTm, toTm)
                || inRange(currentTm + MINUTES_IN_DAY, fromTm, toTm))
		{
			val silentLenghtMins = ((toTm + MINUTES_IN_DAY - currentTm) % (MINUTES_IN_DAY)).toLong()

			ret = System.currentTimeMillis() + silentLenghtMins * MINUTES_IN_HOUR * 1000 // convert minutes to milliseconds

            logger.debug("We are in the silent zone until $ret (it is $silentLenghtMins minutes from now)")
		}
		else
		{
            logger.debug("We are not in the silent mode")
		}

		return ret
	}

	private fun inRange(value: Int, low: Int, high: Int): Boolean
	{
		return (low <= value && value <= high)
	}
}
