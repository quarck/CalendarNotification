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
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.provider.CalendarContract
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.eventsstorage.EventRecord
import com.github.quarck.calnotify.logs.Logger

object CalendarUtils {
    private val logger = Logger("CalendarUtils");

    private val eventFields =
            arrayOf(
                    CalendarContract.CalendarAlerts.EVENT_ID,
                    CalendarContract.CalendarAlerts.STATE,
                    CalendarContract.CalendarAlerts.TITLE,
                    CalendarContract.CalendarAlerts.DESCRIPTION,
                    CalendarContract.CalendarAlerts.DTSTART,
                    CalendarContract.CalendarAlerts.DTEND,
                    CalendarContract.CalendarAlerts.EVENT_LOCATION,
                    CalendarContract.CalendarAlerts.DISPLAY_COLOR,
                    CalendarContract.CalendarAlerts.ALARM_TIME
            )

    private fun cursorToEventRecord(cursor: Cursor, alarmTime: Long?): Pair<Int?, EventRecord?> {
        var eventId: Long? = cursor.getLong(0)
        var state: Int? = cursor.getInt(1)
        var title: String? = cursor.getString(2)
        var start: Long? = cursor.getLong(4)
        var end: Long? = cursor.getLong(5)
        var location: String? = cursor.getString(6)
        var color: Int? = cursor.getInt(7)
        var newAlarmTime: Long? = cursor.getLong(8)

        if (eventId == null || state == null || title == null || start == null)
            return Pair(null, null);

        var event =
                EventRecord(
                        eventId = eventId,
                        notificationId = 0,
                        alertTime = alarmTime ?: newAlarmTime ?: 0,
                        title = title,
                        startTime = start,
                        endTime = end ?: (start + Consts.HOUR_IN_SECONDS*1000L),
                        location = location ?: "",
                        lastEventUpdate = System.currentTimeMillis(),
                        isDisplayed = false,
                        color = color ?: Consts.DEFAULT_COLOR
                );

        return Pair(state, event)
    }

    fun getFiredEventsDetails(context: Context, alertTime: String): List<EventRecord> {
        var ret = arrayListOf<EventRecord>()

        var selection = CalendarContract.CalendarAlerts.ALARM_TIME + "=?";

        var cursor: Cursor? =
                context.contentResolver.query(
                        CalendarContract.CalendarAlerts.CONTENT_URI_BY_INSTANCE,
                        eventFields,
                        selection,
                        arrayOf(alertTime),
                        null
                );

        if (cursor != null && cursor.moveToFirst()) {
            do {
                var (state, event) = cursorToEventRecord(cursor, alertTime.toLong());

                if (state != null && event != null) {
                    logger.info("Received event details: ${event.eventId}, st ${state}, from ${event.startTime} to ${event.endTime}")

                    if (state != CalendarContract.CalendarAlerts.STATE_DISMISSED) {
                        ret.add(event)
                    } else {
                        logger.info("Ignored dismissed event ${event.eventId}")
                    }
                } else {
                    logger.error("Cannot read fired event details!!")
                }

            } while (cursor.moveToNext())
        } else {
            logger.error("Failed to parse event - no events at $alertTime")
        }

        cursor?.close()

        return ret
    }

    fun dismissNativeEventReminder(context: Context, eventId: Long) {
        try {
            var uri = CalendarContract.CalendarAlerts.CONTENT_URI;

            var selection =
                    "(" +
                            "${CalendarContract.CalendarAlerts.STATE}=${CalendarContract.CalendarAlerts.STATE_FIRED}" +
                            " OR " +
                            "${CalendarContract.CalendarAlerts.STATE}=${CalendarContract.CalendarAlerts.STATE_SCHEDULED}" +
                            ")" +
                            " AND ${CalendarContract.CalendarAlerts.EVENT_ID}=$eventId";

            var dismissValues = ContentValues();
            dismissValues.put(
                    CalendarContract.CalendarAlerts.STATE,
                    CalendarContract.CalendarAlerts.STATE_DISMISSED
            );

            context.contentResolver.update(uri, dismissValues, selection, null);

            logger.debug("dismissNativeEventReminder: eventId $eventId");
        } catch (ex: Exception) {
            logger.debug("dismissNativeReminder failed")
        }
    }

    fun getEvent(context: Context, eventId: Long, alertTime: Long): EventRecord? {
        var ret: EventRecord? = null

        var selection = CalendarContract.CalendarAlerts.ALARM_TIME + "=?";

        var cursor: Cursor? =
                context.contentResolver.query(
                        CalendarContract.CalendarAlerts.CONTENT_URI_BY_INSTANCE,
                        eventFields,
                        selection,
                        arrayOf(alertTime.toString()),
                        null
                );

        if (cursor != null && cursor.moveToFirst()) {
            do {
                var (state, event) = cursorToEventRecord(cursor, alertTime);

                if (event != null && event.eventId == eventId) {
                    ret = event;
                    break;
                }

            } while (cursor.moveToNext())
        } else {
            logger.error("Event $eventId not found")
        }

        cursor?.close()

        return ret
    }

    fun getEvent(context: Context, eventId: Long): EventRecord? {
        var ret: EventRecord? = null

        var selection = CalendarContract.CalendarAlerts.EVENT_ID + "=?";

        var cursor: Cursor? =
                context.contentResolver.query(
                        CalendarContract.CalendarAlerts.CONTENT_URI,
                        eventFields,
                        selection,
                        arrayOf(eventId.toString()),
                        null
                );

        if (cursor != null && cursor.moveToFirst()) {
            do {
                var (state, event) = cursorToEventRecord(cursor, null);

                if (event != null && event.eventId == eventId) {
                    ret = event;
                    break;
                }

            } while (cursor.moveToNext())
        } else {
            logger.error("Event $eventId not found")
        }

        cursor?.close()

        return ret
    }



    fun getCalendarViewIntent(eventId: Long): Intent {
        var calendarIntentUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId);
        return Intent(Intent.ACTION_VIEW).setData(calendarIntentUri);
    }

    fun getCalendarEditIntent(eventId: Long): Intent {
        var uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId);
        var intent = Intent(Intent.ACTION_VIEW).setData(uri)
        return intent
    }

    fun viewCalendarEvent(context: Context, eventId: Long) {
        context.startActivity(getCalendarViewIntent(eventId))
    }

    fun editCalendarEvent(context: Context, eventId: Long) {
        context.startActivity(getCalendarEditIntent(eventId))
    }
}
