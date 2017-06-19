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
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.calendar.EventDisplayStatus
import com.github.quarck.calnotify.logs.DevLog
//import com.github.quarck.calnotify.logs.Logger
import java.util.*

class EventsStorageImplV6() : EventsStorageImplInterface {

    @Suppress("ConvertToStringTemplate")
    override fun createDb(db: SQLiteDatabase) {
        val CREATE_PKG_TABLE =
                "CREATE " +
                        "TABLE $TABLE_NAME " +
                        "( " +
                        "$KEY_EVENTID INTEGER PRIMARY KEY, " +
                        "$KEY_NOTIFICATIONID INTEGER, " +
                        "$KEY_TITLE TEXT, " +
                        "$KEY_DESC TEXT, " +
                        "$KEY_START INTEGER, " +
                        "$KEY_END INTEGER, " +
                        "$KEY_LOCATION LOCATION, " +
                        "$KEY_SNOOZED_UNTIL INTEGER, " +
                        "$KEY_LAST_EVENT_FIRE INTEGER, " +
                        "$KEY_IS_DISPLAYED INTEGER, " +
                        "$KEY_COLOR INTEGER, " +
                        "$KEY_ALERT_TIME INTEGER, " +
                        "$KEY_RESERVED_STR1 TEXT, " +
                        "$KEY_RESERVED_STR2 TEXT, " +
                        "$KEY_RESERVED_STR3 TEXT, " +
                        "$KEY_CALENDAR_ID INTEGER, " +
                        "$KEY_INSTANCE_START INTEGER, " +
                        "$KEY_INSTANCE_END INTEGER" +
                        " )"

        DevLog.debug(LOG_TAG, "Creating DB TABLE using query: " + CREATE_PKG_TABLE)

        db.execSQL(CREATE_PKG_TABLE)

        val CREATE_INDEX = "CREATE UNIQUE INDEX $INDEX_NAME ON $TABLE_NAME ($KEY_EVENTID)"

        DevLog.debug(LOG_TAG, "Creating DB INDEX using query: " + CREATE_INDEX)

        db.execSQL(CREATE_INDEX)
    }

    override fun dropAll(db: SQLiteDatabase): Boolean {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        db.execSQL("DROP INDEX IF EXISTS " + INDEX_NAME);
        return true
    }

    override fun addEventImpl(db: SQLiteDatabase, event: EventAlertRecord): Boolean {
        DevLog.debug(LOG_TAG, "addEventImpl " + event.eventId)

        if (event.notificationId == 0)
            event.notificationId = nextNotificationId(db);

        val values = eventRecordToContentValues(event, true)

        try {
            db.insertOrThrow(TABLE_NAME, // table
                    null, // nullColumnHack
                    values) // key/value -> keys = column names/ values = column
            // values
        }
        catch (ex: SQLiteConstraintException) {
            // Close Db before attempting to open it again from another method

            DevLog.debug(LOG_TAG, "This entry (${event.eventId}) is already in the DB, updating!")

            // persist original notification id in this case
            event.notificationId = getEventImpl(db, event.eventId, event.instanceStartTime)?.notificationId ?: event.notificationId;

            updateEventImpl(db, event)
        }

        return true
    }

    private fun nextNotificationId(db: SQLiteDatabase): Int {

        var ret = 0;

        val query = "SELECT MAX(${KEY_NOTIFICATIONID}) FROM " + TABLE_NAME

        val cursor = db.rawQuery(query, null)

        if (cursor != null && cursor.moveToFirst()) {
            try {
                ret = cursor.getString(0).toInt() + 1
            }
            catch (ex: Exception) {
                ret = 0;
            }
        }

        cursor?.close()

        if (ret == 0)
            ret = Consts.NOTIFICATION_ID_DYNAMIC_FROM;

        DevLog.debug(LOG_TAG, "nextNotificationId, returning $ret")

        return ret
    }

    override fun updateEventImpl(db: SQLiteDatabase, event: EventAlertRecord): Boolean {

        val values = eventRecordToContentValues(event)

        DevLog.debug(LOG_TAG, "Updating event, eventId=${event.eventId}");

        db.update(TABLE_NAME, // table
                values, // column/value
                KEY_EVENTID + " = ?", // selections
                arrayOf<String>(event.eventId.toString())) // selection args

        return true
    }

    override fun updateEventsImpl(db: SQLiteDatabase, events: List<EventAlertRecord>): Boolean {

        DevLog.debug(LOG_TAG, "Updating ${events.size} events");

        for (event in events) {
            val values = eventRecordToContentValues(event)

            db.update(TABLE_NAME, // table
                    values, // column/value
                    KEY_EVENTID + " = ?", // selections
                    arrayOf<String>(event.eventId.toString())) // selection args
        }

        return true
    }

    override fun updateEventAndInstanceTimesImpl(db: SQLiteDatabase, event: EventAlertRecord, instanceStart: Long, instanceEnd: Long): Boolean {
        // V6 - instance start is not key value
        updateEventImpl(db, event.copy(instanceStartTime = instanceStart, instanceEndTime = instanceEnd))
        return true
    }


    override fun getEventImpl(db: SQLiteDatabase, eventId: Long, instanceStartTime: Long): EventAlertRecord? {

        val selection =
                if (instanceStartTime != 0L)
                    " $KEY_EVENTID = ? AND $KEY_INSTANCE_START = ?"
                else
                    " $KEY_EVENTID = ?"

        val selectionArgs =
                if (instanceStartTime != 0L)
                    arrayOf(eventId.toString(), instanceStartTime.toString())
                else
                    arrayOf(eventId.toString())

        val cursor = db.query(TABLE_NAME, // a. table
                SELECT_COLUMNS, // b. column names
                selection, // c. selections
                selectionArgs, // d. selections args
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

    override fun getEventInstancesImpl(db: SQLiteDatabase, eventId: Long): List<EventAlertRecord> {
        val event = getEventImpl(db, eventId, 0L)
        if (event != null)
            return listOf(event)
        else
            return listOf<EventAlertRecord>()
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

        DevLog.debug(LOG_TAG, "eventsImpl, returnint ${ret.size} events")

        return ret
    }

    override fun deleteEventImpl(db: SQLiteDatabase, eventId: Long, instanceStartTime: Long): Boolean {

        val selection =
                if (instanceStartTime != 0L)
                    " $KEY_EVENTID = ? AND $KEY_INSTANCE_START = ?"
                else
                    " $KEY_EVENTID = ?"

        val selectionArgs =
                if (instanceStartTime != 0L)
                    arrayOf(eventId.toString(), instanceStartTime.toString())
                else
                    arrayOf(eventId.toString())

        db.delete(TABLE_NAME, selection, selectionArgs)

        DevLog.debug(LOG_TAG, "deleteNotification ${eventId}")

        return true
    }

    override fun updateEventsAndInstanceTimesImpl(db: SQLiteDatabase, events: Collection<EventWithNewInstanceTime>): Boolean {
        throw NotImplementedError("Don't suppose to use this for V6")
    }

    override fun deleteEventsImpl(db: SQLiteDatabase, events: Collection<EventAlertRecord>): Int {
        throw NotImplementedError("Don't suppose to use this for V6")
    }

    override fun addEventsImpl(db: SQLiteDatabase, events: List<EventAlertRecord>): Boolean {
        throw NotImplementedError("Don't suppose to use this for V6")
    }

    private fun eventRecordToContentValues(event: EventAlertRecord, includeId: Boolean = false): ContentValues {
        val values = ContentValues();

        if (includeId)
            values.put(KEY_EVENTID, event.eventId);

        values.put(KEY_CALENDAR_ID, event.calendarId)
        values.put(KEY_NOTIFICATIONID, event.notificationId);
        values.put(KEY_TITLE, event.title);
        values.put(KEY_DESC, ""); // we have no description anymore
        values.put(KEY_START, event.startTime);
        values.put(KEY_END, event.endTime);
        values.put(KEY_INSTANCE_START, event.instanceStartTime);
        values.put(KEY_INSTANCE_END, event.instanceEndTime);
        values.put(KEY_LOCATION, event.location);
        values.put(KEY_SNOOZED_UNTIL, event.snoozedUntil);
        values.put(KEY_LAST_EVENT_FIRE, event.lastStatusChangeTime);
        values.put(KEY_IS_DISPLAYED, event.displayStatus.code);
        values.put(KEY_COLOR, event.color)
        values.put(KEY_ALERT_TIME, event.alertTime)

        return values;
    }

    private fun cursorToEventRecord(cursor: Cursor): EventAlertRecord {

        return EventAlertRecord(
                calendarId = (cursor.getLong(0) as Long?) ?: -1L,
                eventId = cursor.getLong(1),
                notificationId = cursor.getInt(2),
                title = cursor.getString(3),
                startTime = cursor.getLong(4),
                endTime = cursor.getLong(5),
                instanceStartTime = cursor.getLong(6),
                instanceEndTime = cursor.getLong(7),
                location = cursor.getString(8),
                snoozedUntil = cursor.getLong(9),
                lastStatusChangeTime = cursor.getLong(10),
                displayStatus = EventDisplayStatus.fromInt(cursor.getInt(11)),
                color = cursor.getInt(12),
                alertTime = cursor.getLong(13),
                isRepeating = false,
                isAllDay = false
        )
    }

    companion object {
        private const val LOG_TAG = "EventsStorageImplV6"

        private const val TABLE_NAME = "events"
        private const val INDEX_NAME = "eventsIdx"

        private const val KEY_CALENDAR_ID = "i1"
        private const val KEY_EVENTID = "eventId"
        private const val KEY_NOTIFICATIONID = "notificationId"
        private const val KEY_TITLE = "title"
        private const val KEY_DESC = "description"
        private const val KEY_START = "start"
        private const val KEY_END = "end"
        private const val KEY_INSTANCE_START = "i2"
        private const val KEY_INSTANCE_END = "i3"
        private const val KEY_LOCATION = "location"
        private const val KEY_SNOOZED_UNTIL = "snoozeUntil"
        private const val KEY_IS_DISPLAYED = "displayed"
        private const val KEY_LAST_EVENT_FIRE = "lastFire"
        private const val KEY_COLOR = "color"
        private const val KEY_ALERT_TIME = "alertTime"

        private const val KEY_RESERVED_STR1 = "s1"
        private const val KEY_RESERVED_STR2 = "s2"
        private const val KEY_RESERVED_STR3 = "s3"

        private val SELECT_COLUMNS = arrayOf<String>(
                KEY_CALENDAR_ID,
                KEY_EVENTID,
                KEY_NOTIFICATIONID,
                KEY_TITLE,
                KEY_START,
                KEY_END,
                KEY_INSTANCE_START,
                KEY_INSTANCE_END,
                KEY_LOCATION,
                KEY_SNOOZED_UNTIL,
                KEY_LAST_EVENT_FIRE,
                KEY_IS_DISPLAYED,
                KEY_COLOR,
                KEY_ALERT_TIME
        )
    }
}
