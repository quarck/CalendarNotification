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

package com.github.quarck.calnotify.eventsstorage

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.calendar.EventDisplayStatus
import com.github.quarck.calnotify.logs.Logger
import java.util.*

class EventsStorageImplV7()
: EventsStorageImplInterface {

    @Suppress("ConvertToStringTemplate")
    override fun createDb(db: SQLiteDatabase) {

        val CREATE_PKG_TABLE =
            "CREATE " +
                "TABLE $TABLE_NAME " +
                "( " +
                "$KEY_CALENDAR_ID INTEGER, " +
                "$KEY_EVENTID INTEGER, " +

                "$KEY_ALERT_TIME INTEGER, " +

                "$KEY_NOTIFICATIONID INTEGER, " +
                "$KEY_TITLE TEXT, " +

                "$KEY_START INTEGER, " +
                "$KEY_END INTEGER, " +

                "$KEY_INSTANCE_START INTEGER, " +
                "$KEY_INSTANCE_END INTEGER, " +

                "$KEY_LOCATION STRING, " +
                "$KEY_SNOOZED_UNTIL INTEGER, " +
                "$KEY_LAST_EVENT_VISIBILITY INTEGER, " +
                "$KEY_DISPLAY_STATUS INTEGER, " +
                "$KEY_COLOR INTEGER, " +
                "$KEY_IS_REPEATING TEXT, " +

                "$KEY_ALL_DAY TEXT, " +
                "$KEY_RESERVED_INT3 TEXT, " +

                "$KEY_RESERVED_STR1 TEXT, " +
                "$KEY_RESERVED_STR2 TEXT, " +
                "$KEY_RESERVED_STR3 TEXT, " +

                "PRIMARY KEY ($KEY_EVENTID, $KEY_INSTANCE_START)" +
                " )"

        logger.debug("Creating DB TABLE using query: " + CREATE_PKG_TABLE)

        db.execSQL(CREATE_PKG_TABLE)

        val CREATE_INDEX = "CREATE UNIQUE INDEX $INDEX_NAME ON $TABLE_NAME ($KEY_EVENTID, $KEY_INSTANCE_START)"

        logger.debug("Creating DB INDEX using query: " + CREATE_INDEX)

        db.execSQL(CREATE_INDEX)
    }

    override fun dropAll(db: SQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        db.execSQL("DROP INDEX IF EXISTS " + INDEX_NAME);
    }

    override fun addEventImpl(db: SQLiteDatabase, event: EventAlertRecord) {
        logger.debug("addEvent " + event.eventId)

        if (event.notificationId == 0)
            event.notificationId = nextNotificationId(db);

        val values = eventRecordToContentValues(event, true)

        try {
            db.insertOrThrow(TABLE_NAME, // table
                null, // nullColumnHack
                values) // key/value -> keys = column names/ values = column
            // values
        } catch (ex: SQLiteConstraintException) {
            logger.debug("This entry (${event.eventId}) is already in the DB, updating!")
            // persist original notification id in this case
            event.notificationId = getEventImpl(db, event.eventId, event.instanceStartTime)?.notificationId ?: event.notificationId;
            updateEventImpl(db, event)
        }
    }

    private fun nextNotificationId(db: SQLiteDatabase): Int {

        var ret = 0;

        val query = "SELECT MAX(${KEY_NOTIFICATIONID}) FROM " + TABLE_NAME

        val cursor = db.rawQuery(query, null)

        if (cursor != null && cursor.moveToFirst()) {
            try {
                ret = cursor.getString(0).toInt() + 1
            } catch (ex: Exception) {
                ret = 0;
            }
        }

        cursor?.close()

        if (ret == 0)
            ret = Consts.NOTIFICATION_ID_DYNAMIC_FROM;

        logger.debug("nextNotificationId, returning $ret")

        return ret
    }

    override fun updateEventImpl(db: SQLiteDatabase, event: EventAlertRecord) {
        val values = eventRecordToContentValues(event)

        logger.debug("Updating event, eventId=${event.eventId}, instance=${event.instanceStartTime}");

        db.update(TABLE_NAME, // table
            values, // column/value
             "$KEY_EVENTID = ? AND $KEY_INSTANCE_START = ?", // selections
            arrayOf(event.eventId.toString(), event.instanceStartTime.toString())) // selection args
    }

    override fun updateEventsImpl(db: SQLiteDatabase, events: List<EventAlertRecord>) {
        logger.debug("Updating ${events.size} events");

        for (event in events) {
            val values = eventRecordToContentValues(event)

            db.update(TABLE_NAME, // table
                values, // column/value
                "$KEY_EVENTID = ? AND $KEY_INSTANCE_START = ?", // selections
                arrayOf(event.eventId.toString(), event.instanceStartTime.toString())) // selection args
        }
    }

    override fun updateEventAndInstanceTimesImpl(db: SQLiteDatabase, event: EventAlertRecord, instanceStart: Long, instanceEnd: Long) {

        val values = eventRecordToContentValues(
            event = event.copy(instanceStartTime = instanceStart, instanceEndTime = instanceEnd),
            includeKeyValues = true )

        logger.debug("Updating event, eventId=${event.eventId}, instance=${event.instanceStartTime}->$instanceStart");

        db.update(TABLE_NAME, // table
            values, // column/value
            "$KEY_EVENTID = ? AND $KEY_INSTANCE_START = ?", // selections
            arrayOf(event.eventId.toString(), event.instanceStartTime.toString())) // selection args
    }

    override fun getEventImpl(db: SQLiteDatabase, eventId: Long, instanceStartTime: Long): EventAlertRecord? {
        val cursor = db.query(TABLE_NAME, // a. table
            SELECT_COLUMNS, // b. column names
            " $KEY_EVENTID = ? AND $KEY_INSTANCE_START = ?", // c. selections
            arrayOf(eventId.toString(), instanceStartTime.toString()), // d. selections args
            null, // e. group by
            null, // f. having
            null, // g. order by
            null) // h. limit

        var event: EventAlertRecord? = null

        if (cursor != null) {
            if (cursor.moveToFirst())
                event = cursorToEventRecord(cursor)

            cursor.close()
        }

        return event
    }

    override fun getEventsImpl(db: SQLiteDatabase): List<EventAlertRecord> {
        val ret = LinkedList<EventAlertRecord>()

        val cursor = db.query(TABLE_NAME, // a. table
            SELECT_COLUMNS, // b. column names
            null, // c. selections
            null,
            null, // e. group by
            null, // f. h aving
            null, // g. order by
            null) // h. limit

        if (cursor.moveToFirst()) {
            do {
                ret.add(cursorToEventRecord(cursor))

            } while (cursor.moveToNext())
        }
        cursor.close()

        logger.debug("eventsImpl, returnint ${ret.size} events")

        return ret
    }

    override fun getEventInstancesImpl(db: SQLiteDatabase, eventId: Long): List<EventAlertRecord> {
        val ret = LinkedList<EventAlertRecord>()

        val cursor = db.query(TABLE_NAME, // a. table
            SELECT_COLUMNS, // b. column names
            " $KEY_EVENTID = ?", // c. selections
            arrayOf(eventId.toString()), // d. selections args
            null, // e. group by
            null, // f. h aving
            null, // g. order by
            null) // h. limit

        if (cursor.moveToFirst()) {
            do {
                ret.add(cursorToEventRecord(cursor))
            } while (cursor.moveToNext())
        }
        cursor.close()

        logger.debug("eventsImpl, returnint ${ret.size} events")

        return ret
    }

    override fun deleteEventImpl(db: SQLiteDatabase, eventId: Long, instanceStartTime: Long) {

        db.delete(
            TABLE_NAME,
            " $KEY_EVENTID = ? AND $KEY_INSTANCE_START = ?",
            arrayOf(eventId.toString(), instanceStartTime.toString()))

        logger.debug("deleteEventImpl ${eventId}, instance=${instanceStartTime} ")
    }

    private fun eventRecordToContentValues(event: EventAlertRecord, includeKeyValues: Boolean = false): ContentValues {
        val values = ContentValues();

        values.put(KEY_CALENDAR_ID, event.calendarId)
        if (includeKeyValues)
            values.put(KEY_EVENTID, event.eventId);
        values.put(KEY_ALERT_TIME, event.alertTime)
        values.put(KEY_NOTIFICATIONID, event.notificationId);
        values.put(KEY_TITLE, event.title);
        values.put(KEY_START, event.startTime);
        values.put(KEY_END, event.endTime);
        if (includeKeyValues)
            values.put(KEY_INSTANCE_START, event.instanceStartTime);
        values.put(KEY_INSTANCE_END, event.instanceEndTime);
        values.put(KEY_LOCATION, event.location);
        values.put(KEY_SNOOZED_UNTIL, event.snoozedUntil);
        values.put(KEY_LAST_EVENT_VISIBILITY, event.lastEventVisibility);
        values.put(KEY_DISPLAY_STATUS, event.displayStatus.code);
        values.put(KEY_COLOR, event.color)
        values.put(KEY_IS_REPEATING, event.isRepeating)
        values.put(KEY_ALL_DAY, if (event.isAllDay) 1 else 0)

        // Fill reserved keys with some placeholders
        values.put(KEY_RESERVED_INT3, 0L)

        values.put(KEY_RESERVED_STR1, "")
        values.put(KEY_RESERVED_STR2, "")
        values.put(KEY_RESERVED_STR3, "")

        return values;
    }

    private fun cursorToEventRecord(cursor: Cursor): EventAlertRecord {

        return EventAlertRecord(
            calendarId = (cursor.getLong(PROJECTION_KEY_CALENDAR_ID) as Long?) ?: -1L,
            eventId = cursor.getLong(PROJECTION_KEY_EVENTID),
            alertTime = cursor.getLong(PROJECTION_KEY_ALERT_TIME),
            notificationId = cursor.getInt(PROJECTION_KEY_NOTIFICATIONID),
            title = cursor.getString(PROJECTION_KEY_TITLE),
            startTime = cursor.getLong(PROJECTION_KEY_START),
            endTime = cursor.getLong(PROJECTION_KEY_END),
            instanceStartTime = cursor.getLong(PROJECTION_KEY_INSTANCE_START),
            instanceEndTime = cursor.getLong(PROJECTION_KEY_INSTANCE_END),
            location = cursor.getString(PROJECTION_KEY_LOCATION),
            snoozedUntil = cursor.getLong(PROJECTION_KEY_SNOOZED_UNTIL),
            lastEventVisibility = cursor.getLong(PROJECTION_KEY_LAST_EVENT_VISIBILITY),
            displayStatus = EventDisplayStatus.fromInt(cursor.getInt(PROJECTION_KEY_DISPLAY_STATUS)),
            color = cursor.getInt(PROJECTION_KEY_COLOR),
            isRepeating = cursor.getInt(PROJECTION_KEY_IS_REPEATING) != 0,
            isAllDay = cursor.getInt(PROJECTION_KEY_ALL_DAY) != 0
        )
    }

    companion object {
        private val logger = Logger("EventsStorageImplV7")

        private const val TABLE_NAME = "eventsV7"
        private const val INDEX_NAME = "eventsIdxV7"

        private const val KEY_CALENDAR_ID = "calendarId"
        private const val KEY_EVENTID = "eventId"

        private const val KEY_IS_REPEATING = "i1"
        private const val KEY_ALL_DAY = "i2"

        private const val KEY_NOTIFICATIONID = "notificationId"
        private const val KEY_TITLE = "title"
        private const val KEY_START = "eventStart"
        private const val KEY_END = "eventEnd"
        private const val KEY_INSTANCE_START = "instanceStart"
        private const val KEY_INSTANCE_END = "instanceEnd"
        private const val KEY_LOCATION = "location"
        private const val KEY_SNOOZED_UNTIL = "snoozeUntil"
        private const val KEY_DISPLAY_STATUS = "displayStatus"
        private const val KEY_LAST_EVENT_VISIBILITY = "lastSeen"
        private const val KEY_COLOR = "color"
        private const val KEY_ALERT_TIME = "alertTime"

        private const val KEY_RESERVED_STR1 = "s1"
        private const val KEY_RESERVED_STR2 = "s2"
        private const val KEY_RESERVED_STR3 = "s3"

        private const val KEY_RESERVED_INT3 = "i3"

        private val SELECT_COLUMNS = arrayOf<String>(
            KEY_CALENDAR_ID,
            KEY_EVENTID,
            KEY_ALERT_TIME,
            KEY_NOTIFICATIONID,
            KEY_TITLE,
            KEY_START,
            KEY_END,
            KEY_INSTANCE_START,
            KEY_INSTANCE_END,
            KEY_LOCATION,
            KEY_SNOOZED_UNTIL,
            KEY_LAST_EVENT_VISIBILITY,
            KEY_DISPLAY_STATUS,
            KEY_COLOR,
            KEY_IS_REPEATING,
            KEY_ALL_DAY
        )

        const val PROJECTION_KEY_CALENDAR_ID = 0;
        const val PROJECTION_KEY_EVENTID = 1;
        const val PROJECTION_KEY_ALERT_TIME = 2;
        const val PROJECTION_KEY_NOTIFICATIONID = 3;
        const val PROJECTION_KEY_TITLE = 4;
        const val PROJECTION_KEY_START = 5;
        const val PROJECTION_KEY_END = 6;
        const val PROJECTION_KEY_INSTANCE_START = 7;
        const val PROJECTION_KEY_INSTANCE_END = 8;
        const val PROJECTION_KEY_LOCATION = 9;
        const val PROJECTION_KEY_SNOOZED_UNTIL = 10;
        const val PROJECTION_KEY_LAST_EVENT_VISIBILITY = 11;
        const val PROJECTION_KEY_DISPLAY_STATUS = 12;
        const val PROJECTION_KEY_COLOR = 13;
        const val PROJECTION_KEY_IS_REPEATING = 14;
        const val PROJECTION_KEY_ALL_DAY = 15;
    }
}
