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

package com.github.quarck.calnotify.addevent.storage

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import com.github.quarck.calnotify.logs.DevLog
import java.util.*

class NewEventsStorageImplV1: NewEventsStorageImplInterface {

    @Suppress("ConvertToStringTemplate")
    override fun createDb(db: SQLiteDatabase) {

        val CREATE_PKG_TABLE =
                "CREATE " +
                        "TABLE $TABLE_NAME " +
                        "( " +
                        "$KEY_ID INTEGER, " +
                        "$KEY_CALENDAR_ID INTEGER, " +
                        "$KEY_EVENTID INTEGER, " +
                        "$KEY_REPEATING_RULE TEXT, " +
                        "$KEY_ALL_DAY INTEGER, " +
                        "$KEY_TITLE TEXT, " +
                        "$KEY_DESC TEXT, " +
                        "$KEY_START INTEGER, " +
                        "$KEY_END INTEGER, " +
                        "$KEY_LOCATION TEXT, " +
                        "$KEY_TIMEZONE TEXT, " +
                        "$KEY_COLOR INTEGER, " +
                        "$KEY_REMINDERS TEXT, " +

                        "$KEY_RESERVED_INT1 INTEGER, " +
                        "$KEY_RESERVED_INT2 INTEGER, " +
                        "$KEY_RESERVED_INT3 INTEGER, " +
                        "$KEY_RESERVED_INT4 INTEGER, " +
                        "$KEY_RESERVED_INT5 INTEGER, " +
                        "$KEY_RESERVED_INT6 INTEGER, " +
                        "$KEY_RESERVED_INT7 INTEGER, " +
                        "$KEY_RESERVED_INT8 INTEGER, " +
                        "$KEY_RESERVED_INT9 INTEGER, " +

                        "$KEY_RESERVED_STR1 TEXT, " +
                        "$KEY_RESERVED_STR2 TEXT, " +
                        "$KEY_RESERVED_STR3 TEXT, " +

                        "PRIMARY KEY ($KEY_ID)" +
                        " )"

        DevLog.debug(LOG_TAG, "Creating DB TABLE using query: " + CREATE_PKG_TABLE)

        db.execSQL(CREATE_PKG_TABLE)

        val CREATE_INDEX = "CREATE UNIQUE INDEX $INDEX_NAME ON $TABLE_NAME ($KEY_EVENTID)"

        DevLog.debug(LOG_TAG, "Creating DB INDEX using query: " + CREATE_INDEX)

        db.execSQL(CREATE_INDEX)
    }

    override fun dropAll(db: SQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        db.execSQL("DROP INDEX IF EXISTS " + INDEX_NAME);
    }


    override fun addEventImpl(db: SQLiteDatabase, event: NewEventRecord) {

        val values = eventRecordToContentValues(event)

        try {
            val id = db.insertOrThrow(TABLE_NAME, // table
                    null, // nullColumnHack
                    values) // key/value -> keys = column names/ values = column
            // values

            event.id = id
        }
        catch (ex: SQLiteConstraintException) {
//            DevLog.debug(LOG_TAG, "This entry (${event.eventId}) is already in the DB!")
        }
    }

    override fun deleteEventImpl(db: SQLiteDatabase, entry: NewEventRecord) {
        db.delete(
                TABLE_NAME,
                " $KEY_ID = ?",
                arrayOf(entry.id.toString()))

    }

    override fun deleteEventsImpl(db: SQLiteDatabase, events: List<NewEventRecord>) {

        try {
            db.beginTransaction()

            for (event in events) {
                deleteEventImpl(db, event)
            }

            db.setTransactionSuccessful()
        }
        finally {
            db.endTransaction()
        }
    }

    override fun updateEventImpl(db: SQLiteDatabase, entry: NewEventRecord) {
        val values = eventRecordToContentValues(entry)

        db.update(TABLE_NAME, // table
                values, // column/value
                "$KEY_ID = ?",
                arrayOf(entry.id.toString()))
    }

    override fun updateEventsImpl(db: SQLiteDatabase, events: List<NewEventRecord>) {
        try {
            db.beginTransaction()

            for (event in events) {
                updateEventImpl(db, event)
            }

            db.setTransactionSuccessful()
        }
        finally {
            db.endTransaction()
        }
    }

    override fun getEventsImpl(db: SQLiteDatabase): List<NewEventRecord> {

        val ret = LinkedList<NewEventRecord>()

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

        DevLog.debug(LOG_TAG, "eventsImpl, returning ${ret.size} events")

        return ret
    }

    private fun eventRecordToContentValues(event: NewEventRecord): ContentValues {
        val values = ContentValues();

        if (event.id > 0)
            values.put(KEY_ID, event.id)
        values.put(KEY_EVENTID, event.eventId);
        values.put(KEY_CALENDAR_ID, event.calendarId);
        values.put(KEY_REPEATING_RULE, event.repeatingRule);
        values.put(KEY_ALL_DAY, event.isAllDay);
        values.put(KEY_TITLE, event.title);
        values.put(KEY_DESC, event.desc);
        values.put(KEY_START, event.startTime);
        values.put(KEY_END, event.endTime);
        values.put(KEY_LOCATION, event.location);
        values.put(KEY_TIMEZONE, event.timezone);
        values.put(KEY_COLOR, event.colour);
        values.put(KEY_REMINDERS, event.reminders.serialize());


        // Fill reserved keys with some placeholders
        values.put(KEY_RESERVED_INT1, 0L)
        values.put(KEY_RESERVED_INT2, 0L)
        values.put(KEY_RESERVED_INT3, 0L)
        values.put(KEY_RESERVED_INT4, 0L)
        values.put(KEY_RESERVED_INT5, 0L)
        values.put(KEY_RESERVED_INT6, 0L)
        values.put(KEY_RESERVED_INT7, 0L)
        values.put(KEY_RESERVED_INT8, 0L)
        values.put(KEY_RESERVED_INT9, 0L)

        values.put(KEY_RESERVED_STR1, "")
        values.put(KEY_RESERVED_STR2, "")
        values.put(KEY_RESERVED_STR3, "")

        return values;
    }

    private fun cursorToEventRecord(cursor: Cursor): NewEventRecord {

        val reminders = cursor.getString(PROJECTION_KEY_REMINDERS).deserializeNewEventReminders()

        val event = NewEventRecord(
                id = cursor.getLong(PROJECTION_KEY_ID),
                calendarId = cursor.getLong(PROJECTION_KEY_CALENDAR_ID),
                eventId = cursor.getLong(PROJECTION_KEY_EVENTID),
                title = cursor.getString(PROJECTION_KEY_TITLE),
                desc = cursor.getString(PROJECTION_KEY_DESC),
                startTime = cursor.getLong(PROJECTION_KEY_START),
                endTime = cursor.getLong(PROJECTION_KEY_END),
                location = cursor.getString(PROJECTION_KEY_LOCATION),
                colour = cursor.getInt(PROJECTION_KEY_COLOR),
                repeatingRule = cursor.getString(PROJECTION_KEY_REPEATING_RULE),
                isAllDay = cursor.getInt(PROJECTION_KEY_ALL_DAY) != 0,
                timezone = cursor.getString(PROJECTION_KEY_TIMEZONE),

                reminders = reminders
        )

        return event
    }

    companion object {

        private const val LOG_TAG = "NewEventsStorageImplV1"

        private const val TABLE_NAME = "newEventsV1"
        private const val INDEX_NAME = "newEventsIdxV1"


        private const val KEY_ID = "id"

        private const val KEY_CALENDAR_ID = "calendarId"
        private const val KEY_EVENTID = "eventId"

        private const val KEY_REPEATING_RULE = "repeatingRule"
        private const val KEY_ALL_DAY = "allDay"

        private const val KEY_TITLE = "title"
        private const val KEY_DESC = "desc"
        private const val KEY_START = "eventStart"
        private const val KEY_END = "eventEnd"
        private const val KEY_LOCATION = "location"

        private const val KEY_TIMEZONE = "tz"

        private const val KEY_COLOR = "color"

        private const val KEY_REMINDERS = "reminders"

        private const val KEY_RESERVED_STR1 = "s1"
        private const val KEY_RESERVED_STR2 = "s2"
        private const val KEY_RESERVED_STR3 = "s3"

        private const val KEY_RESERVED_INT1 = "i1"
        private const val KEY_RESERVED_INT2 = "i2"
        private const val KEY_RESERVED_INT3 = "i3"
        private const val KEY_RESERVED_INT4 = "i4"
        private const val KEY_RESERVED_INT5 = "i5"
        private const val KEY_RESERVED_INT6 = "i6"
        private const val KEY_RESERVED_INT7 = "i7"
        private const val KEY_RESERVED_INT8 = "i8"
        private const val KEY_RESERVED_INT9 = "i9"

        private val SELECT_COLUMNS = arrayOf<String>(
                KEY_ID,
                KEY_CALENDAR_ID,
                KEY_EVENTID,
                KEY_REPEATING_RULE,
                KEY_ALL_DAY,
                KEY_TITLE,
                KEY_DESC,
                KEY_START,
                KEY_END,
                KEY_LOCATION,
                KEY_TIMEZONE,
                KEY_COLOR,
                KEY_REMINDERS
        )

        const val PROJECTION_KEY_ID = 0
        const val PROJECTION_KEY_CALENDAR_ID = 1
        const val PROJECTION_KEY_EVENTID = 2
        const val PROJECTION_KEY_REPEATING_RULE = 3
        const val PROJECTION_KEY_ALL_DAY = 4
        const val PROJECTION_KEY_TITLE = 5
        const val PROJECTION_KEY_DESC = 6
        const val PROJECTION_KEY_START = 7
        const val PROJECTION_KEY_END = 8
        const val PROJECTION_KEY_LOCATION = 9
        const val PROJECTION_KEY_TIMEZONE = 10
        const val PROJECTION_KEY_COLOR = 11
        const val PROJECTION_KEY_REMINDERS = 12
    }

}