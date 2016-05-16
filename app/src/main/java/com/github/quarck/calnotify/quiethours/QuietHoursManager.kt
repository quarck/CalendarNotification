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

import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.logs.Logger
import com.github.quarck.calnotify.utils.calendarWithTimeMillisHourAndMinute
import java.util.*

object QuietHoursManager
{
    var logger = Logger("QuietPeriodManager")

	fun isEnabled(settings: Settings)
            = settings.quietHoursEnabled && (settings.quietHoursFrom != settings.quietHoursTo)

	// returns time in millis, when silent period ends, 
	// or 0 if we are not on silent 
	fun getSilentUntil(settings: Settings, time: Long = 0L): Long
	{
        var ret: Long = 0

        if (!isEnabled(settings))
			return 0

		val currentTime = if (time != 0L) time else System.currentTimeMillis()

		val cal = Calendar.getInstance()
		cal.timeInMillis = currentTime

		val from = settings.quietHoursFrom
		var silentFrom = calendarWithTimeMillisHourAndMinute(currentTime, from.component1(), from.component2())

		val to = settings.quietHoursTo
		var silentTo = calendarWithTimeMillisHourAndMinute(currentTime, to.component1(), to.component2())

		if (silentTo.before(silentFrom))
			silentTo.roll(Calendar.DAY_OF_MONTH, true);

		while (cal.before(silentTo)) {

			if (cal.after(silentFrom) && cal.before(silentTo)) {
				// this hits silent period -- so it should be silent until 'silentTo'
				ret = silentTo.timeInMillis
				logger.debug("[Virtual]CurrentTime hits silent period range from $silentFrom to $silentTo, would be silent for ${(ret-currentTime)/1000L} seconds since [virtual]currentTime")
				break;
			}

			silentFrom.roll(Calendar.DAY_OF_MONTH, true)
			silentTo.roll(Calendar.DAY_OF_MONTH, true)
		}

		return ret
	}
}
