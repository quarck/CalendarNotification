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

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.provider.CalendarContract
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.logs.Logger
import com.github.quarck.calnotify.permissions.PermissionsManager

object CalendarProvider: CalendarProviderInterface {
    private val logger = Logger("CalendarProvider");

    private val alertFields =
        arrayOf(
            CalendarContract.CalendarAlerts.EVENT_ID,
            CalendarContract.CalendarAlerts.STATE,
            CalendarContract.Events.CALENDAR_ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.DISPLAY_COLOR,
            CalendarContract.CalendarAlerts.ALARM_TIME,
            CalendarContract.CalendarAlerts.BEGIN,
            CalendarContract.CalendarAlerts.END,
            CalendarContract.Events.ALL_DAY
        )

    private val PROJECTION_INDEX_EVENT_ID = 0
    private val PROJECTION_INDEX_STATE = 1
    private val PROJECTION_INDEX_CALENDAR_ID = 2
    private val PROJECTION_INDEX_TITLE = 3
    private val PROJECTION_INDEX_DTSTART = 4
    private val PROJECTION_INDEX_DTEND = 5
    private val PROJECTION_INDEX_LOCATION = 6
    private val PROJECTION_INDEX_COLOR  = 7
    private val PROJECTION_INDEX_ALARM_TIME = 8
    private val PROJECTION_INDEX_INSTANCE_BEGIN = 9
    private val PROJECTION_INDEX_INSTANCE_END = 10
    private val PROJECTION_INDEX_ALL_DAY = 11

    private fun cursorToAlertRecord(cursor: Cursor, alarmTime: Long?): Pair<Int?, EventAlertRecord?> {

        val eventId: Long? = cursor.getLong(PROJECTION_INDEX_EVENT_ID)
        val state: Int? = cursor.getInt(PROJECTION_INDEX_STATE)
        val title: String? = cursor.getString(PROJECTION_INDEX_TITLE)
        val startTime: Long? = cursor.getLong(PROJECTION_INDEX_DTSTART)
        val endTime: Long? = cursor.getLong(PROJECTION_INDEX_DTEND)
        val location: String? = cursor.getString(PROJECTION_INDEX_LOCATION)
        val color: Int? = cursor.getInt(PROJECTION_INDEX_COLOR)
        val newAlarmTime: Long? = cursor.getLong(PROJECTION_INDEX_ALARM_TIME)
        val calendarId: Long? = cursor.getLong(PROJECTION_INDEX_CALENDAR_ID)

        val instanceStart: Long? = cursor.getLong(PROJECTION_INDEX_INSTANCE_BEGIN)
        val instanceEnd: Long? = cursor.getLong(PROJECTION_INDEX_INSTANCE_END)
        val allDay: Int? = cursor.getInt(PROJECTION_INDEX_ALL_DAY)

        if (eventId == null || state == null || title == null || startTime == null)
            return Pair(null, null);

        val event =
            EventAlertRecord(
                calendarId = calendarId ?: -1L,
                eventId = eventId,
                isAllDay = (allDay ?: 0) != 0,
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
                color = color ?: Consts.DEFAULT_CALENDAR_EVENT_COLOR,
                isRepeating = false // has to be updated separately
            );

        return Pair(state, event)
    }

    override fun getAlertByTime(context: Context, alertTime: Long, skipDismissed: Boolean): List<EventAlertRecord> {

        if (!PermissionsManager.hasReadCalendar(context)) {
            logger.error("getFiredEventsDetails failed due to not sufficient permissions");
            return listOf();
        }

        val ret = arrayListOf<EventAlertRecord>()

        val selection = CalendarContract.CalendarAlerts.ALARM_TIME + "=?";

        val cursor: Cursor? =
            context.contentResolver.query(
                CalendarContract.CalendarAlerts.CONTENT_URI_BY_INSTANCE,
                alertFields,
                selection,
                arrayOf(alertTime.toString()),
                null
            );

        if (cursor != null && cursor.moveToFirst()) {
            do {
                val (state, event) = cursorToAlertRecord(cursor, alertTime.toLong());

                if (state != null && event != null) {
                    logger.info("Received event details: ${event.eventId}, st $state, from ${event.startTime} to ${event.endTime}")

                    if (!skipDismissed || state != CalendarContract.CalendarAlerts.STATE_DISMISSED) {
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

        ret.forEach {
            event ->
            event.isRepeating = isRepeatingEvent(context, event) ?: false
        }

        return ret
    }

    override fun getAlertByEventIdAndTime(context: Context, eventId: Long, alertTime: Long): EventAlertRecord? {

        if (!PermissionsManager.hasReadCalendar(context)) {
            logger.error("getEvent failed due to not sufficient permissions");
            return null;
        }

        var ret: EventAlertRecord? = null

        val selection = CalendarContract.CalendarAlerts.ALARM_TIME + "=?";

        val cursor: Cursor? =
                context.contentResolver.query(
                        CalendarContract.CalendarAlerts.CONTENT_URI_BY_INSTANCE,
                        alertFields,
                        selection,
                        arrayOf(alertTime.toString()),
                        null
                );

        if (cursor != null && cursor.moveToFirst()) {
            do {
                val eventPair = cursorToAlertRecord(cursor, alertTime)
                val event = eventPair.component2()

                if (event != null && event.eventId == eventId) {
                    ret = event;
                    break;
                }

            } while (cursor.moveToNext())
        } else {
            logger.error("Event $eventId not found")
        }

        cursor?.close()

        if (ret != null)
            ret.isRepeating = isRepeatingEvent(context, ret) ?: false

        return ret
    }

    override fun getEventAlerts(context: Context, eventId: Long, startingAlertTime: Long, maxEntries: Int): List<EventAlertRecord> {

        if (!PermissionsManager.hasReadCalendar(context)) {
            logger.error("getEventInstances failed due to not sufficient permissions");
            return listOf();
        }

        val ret = arrayListOf<EventAlertRecord>()

        val selection =
            "${CalendarContract.CalendarAlerts.ALARM_TIME} > ? AND ${CalendarContract.CalendarAlerts.EVENT_ID} = ?"

        val cursor: Cursor? =
            context.contentResolver.query(
                CalendarContract.CalendarAlerts.CONTENT_URI_BY_INSTANCE,
                alertFields,
                selection,
                arrayOf(startingAlertTime.toString(), eventId.toString()),
                null
            );

        var totalEntries = 0

        if (cursor != null && cursor.moveToFirst()) {
            do {
                val eventPair = cursorToAlertRecord(cursor, null)
                val event = eventPair.component2()

                if (event != null && event.eventId == eventId) {
                    ret.add(event)
                    ++ totalEntries
                    if (totalEntries >= maxEntries)
                        break;
                }

            } while (cursor.moveToNext())

        } else {
            logger.error("Event $eventId not found")
        }

        cursor?.close()

        ret.forEach {
            event ->
            event.isRepeating = isRepeatingEvent(context, event) ?: false
        }

        return ret
    }

    override fun getEventReminders(context: Context, eventId: Long): List<EventReminderRecord> {

        val ret = mutableListOf<EventReminderRecord>()

        var cursor: Cursor? = null

        try {
            val fields = arrayOf(
                    CalendarContract.Reminders.MINUTES,
                    CalendarContract.Reminders.METHOD)

            val selection = "${CalendarContract.Reminders.EVENT_ID} = ?"

            val selectionArgs = arrayOf(eventId.toString())

            cursor = context.contentResolver.query(
                    CalendarContract.Reminders.CONTENT_URI,
                    fields,
                    selection,
                    selectionArgs,
                    null);

            while (cursor != null && cursor.moveToNext()) {
                //
                val minutes: Long? = cursor.getLong(0)
                val method: Int? = cursor.getInt(1)

                if (minutes != null && minutes != -1L && method != null) {
                    ret.add(
                            EventReminderRecord(
                                    minutes * Consts.MINUTE_IN_SECONDS * 1000L,
                                    method))
                }
            }
        }
        catch (ex: Exception) {
            logger.error("Exception while reading event $eventId reminders: $ex, ${ex.stackTrace}")
        }
        finally {
            cursor?.close()
        }

        return ret
    }

    fun getEventLocalReminders(context: Context, eventId: Long): List<Long> {

        val ret = mutableListOf<Long>()

        val fields = arrayOf(CalendarContract.Reminders.MINUTES)

        val selection = "${CalendarContract.Reminders.EVENT_ID} = ?" +
                " AND ${CalendarContract.Reminders.METHOD} != ${CalendarContract.Reminders.METHOD_EMAIL}" +
                " AND ${CalendarContract.Reminders.METHOD} != ${CalendarContract.Reminders.METHOD_SMS}"

        val selectionArgs = arrayOf(eventId.toString())

        val cursor = context.contentResolver.query(
                CalendarContract.Reminders.CONTENT_URI,
                fields,
                selection,
                selectionArgs,
                null);

        while (cursor != null && cursor.moveToNext()) {
            //
            val minutes: Long? = cursor.getLong(0)

            if (minutes != null && minutes != -1L) {
                ret.add(minutes * Consts.MINUTE_IN_SECONDS * 1000L)
            }
        }

        cursor?.close()

        return ret
    }

    override fun getEvent(context: Context, eventId: Long): EventRecord? {

        if (!PermissionsManager.hasReadCalendar(context)) {
            logger.error("getEvent failed due to not sufficient permissions");
            return null;
        }

        var ret: EventRecord? = null

        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId);

        val fields =
            arrayOf(
                CalendarContract.Events.CALENDAR_ID,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.DTEND,
                CalendarContract.Events.ALL_DAY,
                CalendarContract.Events.EVENT_LOCATION,
                CalendarContract.Events.DISPLAY_COLOR
            )

        val cursor: Cursor? =
            context.contentResolver.query(
                uri, // CalendarContract.CalendarAlerts.CONTENT_URI,
                fields,
                null, //selection,
                null, //arrayOf(eventId.toString()),
                null
            );

        if (cursor != null && cursor.moveToFirst()) {

            val calendarId: Long? = cursor.getLong(0)
            val title: String? = cursor.getString(1)
            val start: Long? = cursor.getLong(2)
            val end: Long? = cursor.getLong(3)
            val allDay: Int? = cursor.getInt(4)
            val location: String? = cursor.getString(5)
            val color: Int? = cursor.getInt(6)

            if (title != null && start != null) {
                ret =
                    EventRecord(
                        calendarId = calendarId ?: -1L,
                        eventId = eventId,
                        title = title,
                        startTime = start,
                        endTime = end ?: 0L,
                        isAllDay = (allDay ?: 0) != 0,
                        location = location ?: "",
                        color = color ?: Consts.DEFAULT_CALENDAR_EVENT_COLOR,
                        reminders = listOf<EventReminderRecord>() // stub for now
                    );
            }
        } else {
            logger.error("Event $eventId not found")
        }

        cursor?.close()

        try {
            if (ret != null)
                ret.reminders = getEventReminders(context, eventId)
        } catch (ex: Exception) {
            logger.error("Exception while trying to read reminders for $eventId: ${ex.message}")
        }

        return ret
    }

    override fun dismissNativeEventAlert(context: Context, eventId: Long) {

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

    //
    // Reschedule works by creating a new event with exactly the same contents but for the new date / time
    // Original notification is dismissed after that
    //
    // Returns event ID of the new event, or -1 on error
    //
    override fun cloneAndMoveEvent(context: Context, event: EventAlertRecord, addTime: Long): Long {

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

        val fields = arrayOf(
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
                CalendarContract.Events.CUSTOM_APP_PACKAGE,
                CalendarContract.Events.CUSTOM_APP_URI
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
                    val timeZone: String? = cursor.getString(3)
                    val description: String? = cursor.getString(4)
                    val dtStart = (cursor.getLong(5) as Long?) ?: throw Exception("dtStart must not be null")
                    val dtEnd = (cursor.getLong(6) as Long?) ?: throw Exception("dtEnd must not be null")
                    val location: String? = cursor.getString(7)
                    val color: Int? = cursor.getInt(8)
                    val accessLevel: Int? = cursor.getInt(9)
                    val availability: Int? = cursor.getInt(10)
                    val hasAlarm: Int? = cursor.getInt(11)
                    val allDay: Int? = cursor.getInt(12)

                    val duration: String? = cursor.getString(13) // CalendarContract.Events.DURATION
                    val eventEndTimeZone: String? = cursor.getString(14) // CalendarContract.Events.EVENT_END_TIMEZONE
                    val hasExtProp: Long? = cursor.getLong(15) // CalendarContract.Events.HAS_EXTENDED_PROPERTIES
                    val organizer: String? = cursor.getString(16) // CalendarContract.Events.ORGANIZER
                    val customAppPackage: String? = cursor.getString(17) // CalendarContract.Events.CUSTOM_APP_PACKAGE
                    val appUri: String? = cursor.getString(18) // CalendarContract.Events.CUSTOM_APP_URI

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
                    if (customAppPackage != null)
                        values.put(CalendarContract.Events.CUSTOM_APP_PACKAGE, customAppPackage);
                    if (appUri != null)
                        values.put(CalendarContract.Events.CUSTOM_APP_URI, appUri);

                    values.put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CONFIRMED)
                    values.put(CalendarContract.Events.SELF_ATTENDEE_STATUS, CalendarContract.Events.STATUS_CONFIRMED)

                    logger.debug("Event details for calendarId: $calendarId / eventId: $eventId captured")
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
                val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values);

                // get the event ID that is the last element in the Uri
                ret = uri.lastPathSegment.toLong()
            } catch (ex: Exception) {
                logger.error("Exception while adding new event: ${ex.message}, ${ex.cause}, ${ex.stackTrace}");
            }
        } else {
            logger.error("Calendar event wasn't found");
        }

        if (ret != -1L) {
            // Now copy reminders
            val reminders = getEventReminders(context, event.eventId)
            for (reminder in reminders) {
                val reminderValues = ContentValues()
                reminderValues.put(CalendarContract.Reminders.MINUTES, reminder.millisecondsBefore / Consts.MINUTE_IN_SECONDS / 1000L)
                reminderValues.put(CalendarContract.Reminders.EVENT_ID, event.eventId)
                reminderValues.put(CalendarContract.Reminders.METHOD, reminder.method)
                context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminderValues)
            }
        }

        return ret;
    }

    private fun isRepeatingEvent(context: Context, event: EventAlertRecord): Boolean? {
        var ret: Boolean? = null

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

    override fun isRepeatingEvent(context: Context, eventId: Long): Boolean? {

        var ret: Boolean? = null

        val fields = arrayOf(
                CalendarContract.CalendarAlerts.EVENT_ID,
                CalendarContract.Events.RRULE,
                CalendarContract.Events.RDATE
        )

        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId);

        var cursor: Cursor? = null

        try {
            cursor =
                    context.contentResolver.query(
                    uri,
                    fields,
                    null,
                    null,
                    null
            );

            if (cursor != null && cursor.moveToFirst()) {
                do {
                    val eventIdEvent = cursor.getLong(0)
                    if (eventIdEvent != eventId)
                        continue;

                    val rRule: String? = cursor.getString(1);
                    val rDate: String? = cursor.getString(2);

                    if (rRule != null && rRule.isNotEmpty())
                        ret = true;
                    else if (rDate != null && rDate.isNotEmpty())
                        ret = true;
                    else
                        ret = false;
                    break;

                } while (cursor.moveToNext())
            }
        }
        catch (ex: Exception) {
            ret = null
        }
        finally {
            cursor?.close()
        }


        return ret;
    }

    override fun moveEvent(context: Context, event: EventAlertRecord, addTime: Long): Boolean {

        var ret = false;

        logger.debug("Request to reschedule event ${event.eventId}, addTime=$addTime");

        if (!PermissionsManager.hasReadCalendar(context)
                || !PermissionsManager.hasWriteCalendar(context)) {
            logger.error("rescheduleEvent failed due to not sufficient permissions");
            return false;
        }

        try {
            val values = ContentValues();

            val currentTime = System.currentTimeMillis()

            var newStartTime = event.startTime + addTime
            var newEndTime = event.endTime + addTime

            while (newStartTime < currentTime + Consts.ALARM_THRESHOLD) {
                logger.error("Requested time is already in the past, adding another ${addTime/1000} sec")

                newStartTime += addTime
                newEndTime += addTime
            }

            values.put(CalendarContract.Events.DTSTART, newStartTime);
            values.put(CalendarContract.Events.DTEND, newEndTime);

            val updateUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.eventId);
            val updated = context.contentResolver.update(updateUri, values, null, null);

            ret = updated > 0

            if (ret) {
                event.startTime = newStartTime
                event.endTime = newEndTime
            }

        } catch (ex: Exception) {
            logger.error("Exception while reading calendar event: ${ex.message}, ${ex.cause}, ${ex.stackTrace}");
        }

        return ret;
    }

    override fun getCalendars(context: Context): List<CalendarRecord> {

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

    override fun findNextAlarmTime(cr: ContentResolver, millis: Long): Long? {

        var alarmTime: Long? = null

        val alarmTimeColumn = CalendarContract.CalendarAlerts.ALARM_TIME

        val projection = arrayOf(alarmTimeColumn)
        val selection = alarmTimeColumn + ">=?";
        val sortOrder = alarmTimeColumn + " ASC";

        val cursor = cr.query(
                CalendarContract.CalendarAlerts.CONTENT_URI,
                projection,
                selection,
                arrayOf(millis.toString()),
                sortOrder)

        try {
            if (cursor != null && cursor.moveToFirst()) {
                alarmTime = cursor.getLong(0)
            }
        } finally {
            cursor?.close()
        }

        return alarmTime
    }

    override fun getHandledCalendarsIds(context: Context, settings: Settings): Set<Long> {
        val handledCalendars =
                getCalendars(context)
                        .filter { settings.getCalendarIsHandled(it.calendarId) }
                        .map { it.calendarId }
                        .toSet()

        return handledCalendars
    }

    data class EventEntry(
            val eventId: Long,
            val instanceStart: Long,
            val instanceEnd: Long,
            val isAllDay: Long
    )

    override fun getEventAlertsForInstancesInRange(context: Context, instanceFrom: Long, instanceTo: Long): List<MonitorEventAlertEntry> {
        val ret = arrayListOf<MonitorEventAlertEntry>()

        if (!PermissionsManager.hasReadCalendar(context)) {
            logger.error("getEventAlertsForInstancesInRange failed due to not sufficient permissions");
            return ret;
        }

        val settings = Settings(context)

        val handledCalendars = getHandledCalendarsIds(context, settings)

        val shouldRemindForEventsWithNoReminders = settings.shouldRemindForEventsWithNoReminders

        val defaultReminderTimeForEventWithNoReminder =
                settings.defaultReminderTimeForEventWithNoReminder

        val defaultReminderTimeForAllDayEventWithNoreminder =
                settings.defaultReminderTimeForAllDayEventWithNoreminder

        try {
            val projection =
                    arrayOf(
                            CalendarContract.Instances.EVENT_ID,
                            CalendarContract.Events.CALENDAR_ID,
                            CalendarContract.Instances.BEGIN,
                            CalendarContract.Instances.END,
                            CalendarContract.Events.ALL_DAY
                    )
            val PROJECTION_INDEX_INST_EVENT_ID = 0
            val PROJECTION_INDEX_INST_CALENDAR_ID = 1
            val PROJECTION_INDEX_INST_BEGIN = 2
            val PROJECTION_INDEX_INST_END = 3
            val PROJECTION_INDEX_INST_ALL_DAY = 4

            logger.info("getEventAlertsForInstancesInRange: Manual event scan started, range: from $instanceFrom to $instanceTo")


            val intermitEvents = arrayListOf<EventEntry>()

            val scanStart = System.currentTimeMillis()

            val instanceCursor: Cursor? =
                    CalendarContract.Instances.query(
                            context.contentResolver,
                            projection,
                            instanceFrom,
                            instanceTo
                    )

            if (instanceCursor != null && instanceCursor.moveToFirst()) {

                do {
                    val eventId: Long? = instanceCursor.getLong(PROJECTION_INDEX_INST_EVENT_ID)
                    val calendarId: Long? = instanceCursor.getLong(PROJECTION_INDEX_INST_CALENDAR_ID)

                    val instanceStart: Long? = instanceCursor.getLong(PROJECTION_INDEX_INST_BEGIN)

                    val instanceEnd: Long? = instanceCursor.getLong(PROJECTION_INDEX_INST_END)

                    val isAllDay: Long? = instanceCursor.getLong(PROJECTION_INDEX_INST_ALL_DAY)

                    if (instanceStart == null || eventId == null || calendarId == null) {
                        logger.info("Got entry with one of: instanceStart, eventId or calendarId not present - skipping")
                        continue;
                    }

                    if (!handledCalendars.contains(calendarId) || calendarId == -1L) {
                        logger.info("Event id $eventId - belongs to calendar $calendarId, which is not handled - skipping")
                        continue
                    }

                    if (instanceStart < instanceFrom) {
                        logger.info("Event id $eventId: instanceStart $instanceStart is actully before instanceFrom $instanceFrom, skipping")
                        continue
                    }

                    intermitEvents.add(
                            EventEntry(
                                eventId = eventId,
                                instanceStart = instanceStart,
                                instanceEnd = instanceEnd ?: instanceStart + 3600L * 1000L,
                                isAllDay = isAllDay ?: 0L
                        ))

                } while (instanceCursor.moveToNext())

                val knownReminders =
                        intermitEvents
                                .map { it.eventId }
                                .toSet()
                                .map {
                                    eventId -> eventId to
                                        getEventLocalReminders(context, eventId)
//                                        getEventReminders(context, eventId)
//                                                .filter { reminder ->
//                                                            reminder.method != CalendarContract.Reminders.METHOD_EMAIL &&
//                                                            reminder.method != CalendarContract.Reminders.METHOD_SMS }
//                                                .map { reminder -> reminder.millisecondsBefore }
//                                                .toLongArray()
                                }
                                .toMap()

                for (evt in intermitEvents) {
                    val reminders = knownReminders[evt.eventId] // getEventLocalReminders(context, eventId);

                    var hasAnyReminders = false

                    if (reminders != null)
                        for (reminder in reminders){

                            val alertTime = evt.instanceStart - reminder//s[reminderIdx];

                            val entry = MonitorEventAlertEntry(
                                    evt.eventId,
                                    evt.isAllDay != 0L,
                                    alertTime,
                                    evt.instanceStart,
                                    evt.instanceEnd,
                                    false,
                                    false
                            )

                            ret.add(entry)
                            hasAnyReminders = true
                        }

                    if (!hasAnyReminders && shouldRemindForEventsWithNoReminders) {

                        val alertTime =
                                if (evt.isAllDay == 0L)
                                    evt.instanceStart - defaultReminderTimeForEventWithNoReminder
                                else
                                    evt.instanceStart - defaultReminderTimeForAllDayEventWithNoreminder

                        val entry = MonitorEventAlertEntry(
                                evt.eventId,
                                evt.isAllDay != 0L,
                                alertTime,
                                evt.instanceStart,
                                evt.instanceEnd,
                                true,
                                false
                        )

                        ret.add(entry)
                    }
                }

            } else {
                logger.error("getEventInstances: no pending event alerts found")
            }

            instanceCursor?.close()

            val scanEnd = System.currentTimeMillis()
            logger.info("getEventAlertsForInstancesInRange(): scan finished, got ${ret.size} entries, scan time: ${scanEnd-scanStart}ms")
        }
        catch (ex: Exception) {
            logger.error("getEventAlertsForInstancesInRange(): got exception ${ex.message}, ${ex}, ${ex.stackTrace}")
        }

        return ret
    }
}
