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
import com.github.quarck.calnotify.eventsstorage.EventDisplayStatus
import com.github.quarck.calnotify.eventsstorage.EventRecord
import com.github.quarck.calnotify.logs.Logger
import com.github.quarck.calnotify.permissions.PermissionsManager
import java.util.*

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
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.DISPLAY_COLOR,
            CalendarContract.CalendarAlerts.ALARM_TIME,
            CalendarContract.Events.CALENDAR_ID,
            CalendarContract.CalendarAlerts.BEGIN,
            CalendarContract.CalendarAlerts.END
        )

    private fun cursorToEventRecord(cursor: Cursor, alarmTime: Long?): Pair<Int?, EventRecord?> {
        val eventId: Long? = cursor.getLong(0)
        val state: Int? = cursor.getInt(1)
        val title: String? = cursor.getString(2)
        val startTime: Long? = cursor.getLong(4)
        val endTime: Long? = cursor.getLong(5)
        val location: String? = cursor.getString(6)
        val color: Int? = cursor.getInt(7)
        val newAlarmTime: Long? = cursor.getLong(8)
        val calendarId: Long? = cursor.getLong(9)

        val instanceStart: Long? = cursor.getLong(10)
        val instanceEnd: Long? = cursor.getLong(11)

        if (eventId == null || state == null || title == null || startTime == null)
            return Pair(null, null);

        val event =
            EventRecord(
                calendarId = calendarId ?: -1L,
                eventId = eventId,
                notificationId = 0,
                alertTime = alarmTime ?: newAlarmTime ?: 0,
                title = title,
                startTime = startTime,
                endTime = endTime ?: 0L,
                instanceStartTime = instanceStart ?: 0L,
                instanceEndTime = instanceEnd ?: 0L,
                location = location ?: "",
                lastEventVisibility = 0L,
                displayStatus = EventDisplayStatus.Hidden,
                color = color ?: Consts.DEFAULT_CALENDAR_EVENT_COLOR
            );

        return Pair(state, event)
    }

    fun getInstancesByAlertTime(context: Context, alertTime: String): List<EventRecord>? {

        if (!PermissionsManager.hasReadCalendar(context)) {
            logger.error("getFiredEventsDetails failed due to not sufficient permissions");
            return null;
        }

        val ret = arrayListOf<EventRecord>()

        val selection = CalendarContract.CalendarAlerts.ALARM_TIME + "=?";

        val cursor: Cursor? =
                context.contentResolver.query(
                        CalendarContract.CalendarAlerts.CONTENT_URI_BY_INSTANCE,
                        eventFields,
                        selection,
                        arrayOf(alertTime),
                        null
                );

        if (cursor != null && cursor.moveToFirst()) {
            do {
                val (state, event) = cursorToEventRecord(cursor, alertTime.toLong());

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

        if (!PermissionsManager.hasWriteCalendar(context)) {
            logger.error("getFiredEventsDetails failed due to not sufficient permissions");
            return;
        }

        try {
            val uri = CalendarContract.CalendarAlerts.CONTENT_URI;

            val selection =
                    "(" +
                            "${CalendarContract.CalendarAlerts.STATE}=${CalendarContract.CalendarAlerts.STATE_FIRED}" +
                            " OR " +
                            "${CalendarContract.CalendarAlerts.STATE}=${CalendarContract.CalendarAlerts.STATE_SCHEDULED}" +
                            ")" +
                            " AND ${CalendarContract.CalendarAlerts.EVENT_ID}=$eventId";

            val dismissValues = ContentValues();
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

    @Suppress("UNUSED_VARIABLE")
    fun getEventInstance(context: Context, eventId: Long, alertTime: Long): EventRecord? {

        if (!PermissionsManager.hasReadCalendar(context)) {
            logger.error("getEvent failed due to not sufficient permissions");
            return null;
        }

        var ret: EventRecord? = null

        val selection = CalendarContract.CalendarAlerts.ALARM_TIME + "=?";

        val cursor: Cursor? =
                context.contentResolver.query(
                        CalendarContract.CalendarAlerts.CONTENT_URI_BY_INSTANCE,
                        eventFields,
                        selection,
                        arrayOf(alertTime.toString()),
                        null
                );

        if (cursor != null && cursor.moveToFirst()) {
            do {
                val (state, event) = cursorToEventRecord(cursor, alertTime);

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

        if (!PermissionsManager.hasReadCalendar(context)) {
            logger.error("getEvent failed due to not sufficient permissions");
            return null;
        }

        var ret: EventRecord? = null

//        val selection = CalendarContract.CalendarAlerts.EVENT_ID + "= ?";

        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId);

        val smallEventFields =
            arrayOf(
                CalendarContract.Events.CALENDAR_ID,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.DTEND,
                CalendarContract.Events.EVENT_LOCATION,
                CalendarContract.Events.DISPLAY_COLOR
            )

        val cursor: Cursor? =
            context.contentResolver.query(
                uri, // CalendarContract.CalendarAlerts.CONTENT_URI,
                smallEventFields,
                null, //selection,
                null, //arrayOf(eventId.toString()),
                null
            );

        if (cursor != null && cursor.moveToFirst()) {

            val calendarId: Long? = cursor.getLong(0)
            val title: String? = cursor.getString(1)
            val start: Long? = cursor.getLong(2)
            val end: Long? = cursor.getLong(3)
            val location: String? = cursor.getString(4)
            val color: Int? = cursor.getInt(5)

            if (title != null && start != null) {
                ret =
                    EventRecord(
                        calendarId = calendarId ?: -1L,
                        eventId = eventId,
                        notificationId = 0,
                        alertTime = 0,
                        title = title,
                        startTime = start,
                        endTime = end ?: (start + Consts.HOUR_IN_SECONDS*1000L),
                        instanceStartTime = 0L, // unknown
                        instanceEndTime = 0L, // unknown
                        location = location ?: "",
                        lastEventVisibility = 0L,
                        displayStatus = EventDisplayStatus.Hidden,
                        color = color ?: Consts.DEFAULT_CALENDAR_EVENT_COLOR
                    );
            }
        } else {
            logger.error("Event $eventId not found")
        }

        cursor?.close()

        return ret
    }

    //
    // Reschedule works by creating a new event with exactly the same contents but for the new date / time
    // Original notification is dismissed after that
    //
    // Returns event ID of the new event, or -1 on error
    //
    fun cloneAndMoveEvent(context: Context, event: EventRecord, addTime: Long): Long {

        var ret = -1L;

        logger.debug("Request to reschedule event ${event.eventId}, addTime=$addTime");

        if (!PermissionsManager.hasReadCalendar(context)
                || !PermissionsManager.hasWriteCalendar(context)) {
            logger.error("rescheduleEvent failed due to not sufficient permissions");
            return -1;
        }

        if (event.alertTime == 0L) {
            logger.error("Alert time is zero");
            return -1;
        }

        var fields = arrayOf(
                CalendarContract.CalendarAlerts.EVENT_ID,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.CALENDAR_ID,
                CalendarContract.Events.EVENT_TIMEZONE,
                CalendarContract.Events.DESCRIPTION,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.DTEND,
                CalendarContract.Events.EVENT_LOCATION,
                CalendarContract.Events.DISPLAY_COLOR,
                CalendarContract.Events.ACCESS_LEVEL,
                CalendarContract.Events.AVAILABILITY,
                CalendarContract.Events.HAS_ALARM,
                CalendarContract.Events.ALL_DAY,
                CalendarContract.Events.DURATION,
                CalendarContract.Events.EVENT_END_TIMEZONE,
                CalendarContract.Events.HAS_EXTENDED_PROPERTIES,
                CalendarContract.Events.ORGANIZER,
                CalendarContract.Events.IS_ORGANIZER,
                CalendarContract.Events.CUSTOM_APP_PACKAGE,
                CalendarContract.Events.CUSTOM_APP_URI,
                CalendarContract.Events.UID_2445
        )

        //
        // First - retrieve full set of events we are looking for
        //
        var values: ContentValues? = null // values for the new event

        val selection = CalendarContract.CalendarAlerts.ALARM_TIME + "=?";

        val cursor: Cursor? =
                context.contentResolver.query(
                        CalendarContract.CalendarAlerts.CONTENT_URI_BY_INSTANCE,
                        fields,
                        selection,
                        arrayOf(event.alertTime.toString()),
                        null
                );

        try {
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    val eventId = cursor.getLong(0)
                    if (eventId != event.eventId)
                        continue;

                    values = ContentValues()

                    val title: String = (cursor.getString(1) as String?) ?: throw Exception("Title must not be null")
                    val calendarId: Long = (cursor.getLong(2) as Long?) ?: throw Exception("Calendar ID must not be null");
                    var timeZone: String? = cursor.getString(3)
                    var description: String? = cursor.getString(4)
                    var dtStart = (cursor.getLong(5) as Long?) ?: throw Exception("dtStart must not be null")
                    var dtEnd = (cursor.getLong(6) as Long?) ?: throw Exception("dtEnd must not be null")
                    var location: String? = cursor.getString(7)
                    var color: Int? = cursor.getInt(8)
                    var accessLevel: Int? = cursor.getInt(9)
                    var availability: Int? = cursor.getInt(10)
                    var hasAlarm: Int? = cursor.getInt(11)
                    var allDay: Int? = cursor.getInt(12)

                    var duration: String? = cursor.getString(13) // CalendarContract.Events.DURATION
                    var eventEndTimeZone: String? = cursor.getString(14) // CalendarContract.Events.EVENT_END_TIMEZONE
                    var hasExtProp: Long? = cursor.getLong(15) // CalendarContract.Events.HAS_EXTENDED_PROPERTIES
                    var organizer: String? = cursor.getString(16) // CalendarContract.Events.ORGANIZER
                    var isOrganizer: String? = cursor.getString(17) // CalendarContract.Events.IS_ORGANIZER
                    var customAppPackage: String? = cursor.getString(18) // CalendarContract.Events.CUSTOM_APP_PACKAGE
                    var appUri: String? = cursor.getString(19) // CalendarContract.Events.CUSTOM_APP_URI
                    var uid2445: String? = cursor.getString(20) // CalendarContract.Events.UID_2445

                    values.put(CalendarContract.Events.TITLE, title);
                    values.put(CalendarContract.Events.CALENDAR_ID, calendarId);
                    values.put(CalendarContract.Events.EVENT_TIMEZONE, timeZone);
                    values.put(CalendarContract.Events.DESCRIPTION, description ?: "");

                    values.put(CalendarContract.Events.DTSTART, dtStart + addTime);
                    values.put(CalendarContract.Events.DTEND, dtEnd + addTime);

                    if (location != null)
                        values.put(CalendarContract.Events.EVENT_LOCATION, location);
                    if (color != null)
                        values.put(CalendarContract.Events.EVENT_COLOR, color);
                    if (accessLevel != null)
                        values.put(CalendarContract.Events.ACCESS_LEVEL, accessLevel);
                    if (availability != null)
                        values.put(CalendarContract.Events.AVAILABILITY, availability);
                    if (hasAlarm != null)
                       values.put(CalendarContract.Events.HAS_ALARM, hasAlarm);
                    if (allDay != null)
                        values.put(CalendarContract.Events.ALL_DAY, allDay);
                    if (duration != null)
                        values.put(CalendarContract.Events.DURATION, duration);
                    if (eventEndTimeZone != null)
                        values.put(CalendarContract.Events.EVENT_END_TIMEZONE, eventEndTimeZone);
                    if (hasExtProp != null)
                        values.put(CalendarContract.Events.HAS_EXTENDED_PROPERTIES, hasExtProp);
                    if (organizer != null)
                        values.put(CalendarContract.Events.ORGANIZER, organizer);
                    if (isOrganizer != null)
                        values.put(CalendarContract.Events.IS_ORGANIZER, isOrganizer);
                    if (customAppPackage != null)
                        values.put(CalendarContract.Events.CUSTOM_APP_PACKAGE, customAppPackage);
                    if (appUri != null)
                        values.put(CalendarContract.Events.CUSTOM_APP_URI, appUri);
                    if (uid2445 != null)
                        values.put(CalendarContract.Events.UID_2445, uid2445);

                    values.put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CONFIRMED)
                    values.put(CalendarContract.Events.SELF_ATTENDEE_STATUS, CalendarContract.Events.STATUS_CONFIRMED)

                    logger.debug(
                            "Capturd information so far: title: $title, id: $calendarId, color: $color, timeZone: " +
                                    "$timeZone, desc: $description, dtStart: $dtStart, dtEnd: $dtEnd, " +
                                    "accessLevel: $accessLevel, availability: $availability, hasAlarm: $hasAlarm, " +
                                    "duration: $duration, eventEndTimeZone: $eventEndTimeZone, hasExtProp: $hasExtProp, " +
                                    "organizer: $organizer, isOrg: $isOrganizer, pkg: $customAppPackage, uri: $appUri, " +
                                    "uid: $uid2445"
                    );

                    break;

                } while (cursor.moveToNext())
            }


        } catch (ex: Exception) {
            logger.error("Exception while reading calendar event: ${ex.message}, ${ex.cause}, ${ex.stackTrace}");
        } finally {
            cursor?.close()
        }

        if (values != null) {
            try {
                var uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values);
                // get the event ID that is the last element in the Uri
                ret = uri.getLastPathSegment().toLong()
            } catch (ex: Exception) {
                logger.error("Exception while adding new event: ${ex.message}, ${ex.cause}, ${ex.stackTrace}");
            }
        } else {
            logger.error("Calendar event wasn't found");
        }

        return ret;
    }

    fun isRepeatingEvent(context: Context, event: EventRecord): Boolean? {
        var ret: Boolean? = null

        if (!PermissionsManager.hasReadCalendar(context)
                || !PermissionsManager.hasWriteCalendar(context)) {
            logger.error("isRepeatingEvent failed due to not sufficient permissions");
            return null;
        }

        if (event.alertTime == 0L) {
            logger.error("Alert time is zero");
            return null;
        }

        val fields = arrayOf(
                CalendarContract.CalendarAlerts.EVENT_ID,
                CalendarContract.Events.RRULE,
                CalendarContract.Events.RDATE
        )

        val selection = CalendarContract.CalendarAlerts.ALARM_TIME + "=?";

        val cursor: Cursor? =
                context.contentResolver.query(
                        CalendarContract.CalendarAlerts.CONTENT_URI_BY_INSTANCE,
                        fields,
                        selection,
                        arrayOf(event.alertTime.toString()),
                        null
                );
        try {
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    val eventId = cursor.getLong(0)
                    if (eventId != event.eventId)
                        continue;

                    val rRule: String? = cursor.getString(1);
                    val rDate: String? = cursor.getString(2);

                    if (rRule != null && rRule.length > 0)
                        ret = true;
                    else if (rDate != null && rDate.length > 0)
                        ret = true;
                    else
                        ret = false;
                    break;

                } while (cursor.moveToNext())
            }
        } catch (ex: Exception) {
            ret = null
        }

        cursor?.close()

        return ret;
    }

    fun moveEvent(context: Context, event: EventRecord, addTime: Long): Boolean {

        var ret = false;

        logger.debug("Request to reschedule event ${event.eventId}, addTime=$addTime");

        if (!PermissionsManager.hasReadCalendar(context)
                || !PermissionsManager.hasWriteCalendar(context)) {
            logger.error("rescheduleEvent failed due to not sufficient permissions");
            return false;
        }

        try {
            var values = ContentValues();

            var currentTime = System.currentTimeMillis()

            var newStartTime = event.startTime + addTime
            var newEndTime = event.endTime + addTime

            while (newStartTime < currentTime + Consts.ALARM_THRESHOULD) {
                logger.error("Requested time is already in the past, adding another ${addTime/1000} sec")

                newStartTime += addTime
                newEndTime += addTime
            }

            values.put(CalendarContract.Events.DTSTART, newStartTime);
            values.put(CalendarContract.Events.DTEND, newEndTime);

            var updateUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.eventId);
            var updated = context.contentResolver.update(updateUri, values, null, null);

            ret = updated > 0

        } catch (ex: Exception) {
            logger.error("Exception while reading calendar event: ${ex.message}, ${ex.cause}, ${ex.stackTrace}");
        }

        return ret;
    }


    fun getCalendars(context: Context): List<CalendarRecord> {

        val ret = mutableListOf<CalendarRecord>()

        val fields =
            arrayOf(
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.OWNER_ACCOUNT,
                CalendarContract.Calendars.ACCOUNT_NAME,
                CalendarContract.Calendars.ACCOUNT_TYPE,
                CalendarContract.Calendars.CALENDAR_COLOR
            )

        val uri = CalendarContract.Calendars.CONTENT_URI

        val cursor = context.contentResolver.query(uri, fields, null, null, null);

        while (cursor != null && cursor.moveToNext()) {

            // Get the field values
            val calID: Long? = cursor.getLong(0);
            val displayName: String? = cursor.getString(1);
            val accountName: String? = cursor.getString(2);
            val ownerName: String? = cursor.getString(3);
            val accountType: String? = cursor.getString(4)
            val color: Int? = cursor.getInt(5)

            // Do something with the values...

            ret.add(CalendarRecord(
                calendarId = calID ?: -1L,
                owner = ownerName ?: "",
                accountName = accountName ?: "",
                accountType = accountType ?: "",
                name = displayName ?: "",
                color = color ?: Consts.DEFAULT_CALENDAR_EVENT_COLOR
            ))
        }

        cursor?.close()

        return ret
    }
}
