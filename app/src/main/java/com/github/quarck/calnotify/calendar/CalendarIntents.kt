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
import com.github.quarck.calnotify.logs.Logger


object CalendarIntents {

    val logger = Logger("CalendarIntents")

    private fun intentForAction(action: String, eventId: Long,
                                instanceBeginTime: Long, instanceEndTime: Long): Intent {

        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId);
        val intent = Intent(action).setData(uri)

        if (instanceBeginTime != 0L &&
            instanceEndTime != 0L &&
            instanceBeginTime < instanceEndTime) {
            // only add if it is a valid instance start / end time, and we need both
            logger.debug("Adding instance start / end for event $eventId, start: $instanceBeginTime, end: $instanceEndTime");
            intent.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, instanceBeginTime)
            intent.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, instanceEndTime)
        }
        return intent
    }

    fun getCalendarViewIntent(eventId: Long, instanceBeginTime: Long, instanceEndTime: Long)
        = intentForAction(Intent.ACTION_VIEW, eventId, instanceBeginTime, instanceEndTime)

    fun getCalendarEditIntent(eventId: Long, instanceBeginTime: Long, instanceEndTime: Long)
        = intentForAction(Intent.ACTION_EDIT, eventId, instanceBeginTime, instanceEndTime)

    fun viewCalendarEvent(context: Context, eventId: Long, instanceBeginTime: Long, instanceEndTime: Long)
        = context.startActivity(getCalendarViewIntent(eventId, instanceBeginTime, instanceEndTime))

    fun editCalendarEvent(context: Context, eventId: Long, instanceBeginTime: Long, instanceEndTime: Long)
        = context.startActivity(getCalendarEditIntent(eventId, instanceBeginTime, instanceEndTime))
}