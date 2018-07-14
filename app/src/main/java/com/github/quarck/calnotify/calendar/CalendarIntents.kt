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

package com.github.quarck.calnotify.calendar

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import com.github.quarck.calnotify.logs.DevLog
//import com.github.quarck.calnotify.logs.Logger


object CalendarIntents {

    private const val LOG_TAG = "CalendarIntents"

    private fun intentForAction(action: String, eventId: Long): Intent {

        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId);
        val intent = Intent(action).setData(uri)

        return intent
    }

    private fun intentForAction(action: String, event: EventAlertRecord): Intent {

        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.eventId);
        val intent = Intent(action).setData(uri)

        val shouldAddEventTime = event.isRepeating

        val canAddEventTime =
                event.instanceStartTime != 0L &&
                        event.instanceEndTime != 0L &&
                        event.instanceStartTime < event.instanceEndTime

        if (shouldAddEventTime && canAddEventTime) {
            // only add if it is a valid instance start / end time, and we need both
            DevLog.debug(LOG_TAG, "Adding instance start / end for event ${event.eventId}, start: ${event.instanceStartTime}, end: ${event.instanceEndTime}");
            intent.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.instanceStartTime)
            intent.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.instanceEndTime)
        }

        return intent
    }

    fun viewCalendarEvent(context: Context, event: EventAlertRecord)
            = context.startActivity(intentForAction(Intent.ACTION_VIEW, event))

    fun viewCalendarEvent(context: Context, eventId: Long)
            = context.startActivity(intentForAction(Intent.ACTION_VIEW, eventId))
}