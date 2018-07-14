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

package com.github.quarck.calnotify.calendareditor

import android.content.Context
import com.github.quarck.calnotify.calendar.CalendarEventDetails
import com.github.quarck.calnotify.calendar.CalendarRecord
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.calendar.EventRecord

interface CalendarChangeManagerInterface {

    /**
     * Returns event Id on success, -1L on failure
     * event
     * @param [context] - Android context
     * @param [calendarId] - Calendar ID to use
     * @param [calendarOwnerAccount] - Account name of the calendar owner
     * @param [details] - details of the new event to create
     */
    fun createEvent(context: Context, calendarId: Long, calendarOwnerAccount: String, details: CalendarEventDetails): Long

    /**
     * Moves calendar event by given time, returns true on success.
     * event
     * @param [context] - Android context
     * @param [event] - event to move
     * @param [addTimeMillis] - amount of time to add
     */
    fun moveEvent(context: Context, event: EventAlertRecord, addTimeMillis: Long): Boolean

    /**
     * Updates calendar event with the new details passed by the caller
     * @param [context] - Android context
     * @param [eventToEdit]] - Original event to be edited
     * @param [details] - new details to be updated
     * @returns true on success
     */
    fun updateEvent(context: Context, eventToEdit: EventRecord, details: CalendarEventDetails): Boolean

    /**
     * For repeating events - creates a copy of event instance with the time "addTime" added
     * to the start time
     * @param [context] - Android context
     * @param [calendar] - Calendar, this event belongs to
     * @param [event] - event to move
     * @param [addTimeMillis] - amount of time to add
     */
    fun moveRepeatingAsCopy(context: Context, calendar: CalendarRecord, event: EventAlertRecord, addTimeMillis: Long): Long
}