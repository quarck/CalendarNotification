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

package com.github.quarck.calnotify.calendarmonitor

import android.content.Context
import com.github.quarck.calnotify.calendar.CalendarProviderInterface


class CalendarMonitorManual(
        val calendarProvider: CalendarProviderInterface,
        val calendarMonitor: CalendarMonitorInterface
) {

    // should return true if we have fired at new events, so UI should reload if it is open
    fun manualFireEventsAt(context: Context, nextEventFire: Long, prevEventFire: Long? = null): Boolean {

        logger.debug("manualFireEventsAt - this is not implemented yet");

        return false
    }

    companion object {
        val logger = com.github.quarck.calnotify.logs.Logger("CalendarMonitorManual")
    }

    fun scanNextEvent(context: Context, state: CalendarMonitorState): Pair<Long, Boolean> {
        return Pair(Long.MAX_VALUE, false)
    }
}