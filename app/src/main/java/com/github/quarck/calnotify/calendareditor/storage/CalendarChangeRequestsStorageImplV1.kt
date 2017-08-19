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

package com.github.quarck.calnotify.calendareditor.storage

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import com.github.quarck.calnotify.logs.DevLog
import java.util.*

class CalendarChangeRequestsStorageImplV1 : CalendarChangeRequestsStorageImplInterface {

    @Suppress("ConvertToStringTemplate")
    override fun createDb(db: SQLiteDatabase) {

        val CREATE_PKG_TABLE =
                "CREATE " +
                        "TABLE $TABLE_NAME " +
                        "( " +
                        "$KEY_ID INTEGER, " +
                        "$KEY_TYPE INTEGER, " +
                        "$KEY_CALENDAR_ID INTEGER, " +
                        "$KEY_EVENTID INTEGER, " +
                        "$KEY_STATUS INTEGER, " +
                        "$KEY_STATUS_TIMESTAMP INTEGER, " +
                        "$KEY_REPEATING_RULE TEXT, " +
                        "$KEY_REPEATING_DATE TEXT, " +
                        "$KEY_EXT_REPEATING_RULE TEXT, " +
                        "$KEY_EXT_REPEATING_DATE TEXT, " +
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


    override fun addEventImpl(db: SQLiteDatabase, req: CalendarChangeRequest) {

        val values = reqRecordToContentValues(req)

        try {
            val id = db.insertOrThrow(TABLE_NAME, // table
                    null, // nullColumnHack
                    values) // key/value -> keys = column names/ values = column
            // values

            req.id = id
        }
        catch (ex: SQLiteConstraintException) {
//            DevLog.debug(LOG_TAG, "This entry (${req.eventId}) is already in the DB!")
        }
    }

    override fun deleteEventImpl(db: SQLiteDatabase, req: CalendarChangeRequest) {
        db.delete(
                TABLE_NAME,
                " $KEY_ID = ?",
                arrayOf(req.id.toString()))

    }

    override fun deleteEventsImpl(db: SQLiteDatabase, requests: List<CalendarChangeRequest>) {

        try {
            db.beginTransaction()

            for (req in requests) {
                deleteEventImpl(db, req)
            }

            db.setTransactionSuccessful()
        }
        finally {
            db.endTransaction()
        }
    }

    override fun updateEventImpl(db: SQLiteDatabase, req: CalendarChangeRequest) {
        val values = reqRecordToContentValues(req)

        db.update(TABLE_NAME, // table
                values, // column/value
                "$KEY_ID = ?",
                arrayOf(req.id.toString()))
    }

    override fun updateEventsImpl(db: SQLiteDatabase, requests: List<CalendarChangeRequest>) {
        try {
            db.beginTransaction()

            for (req in requests) {
                updateEventImpl(db, req)
            }

            db.setTransactionSuccessful()
        }
        finally {
            db.endTransaction()
        }
    }

    override fun getEventsImpl(db: SQLiteDatabase): List<CalendarChangeRequest> {

        val ret = LinkedList<CalendarChangeRequest>()

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

        DevLog.debug(LOG_TAG, "eventsImpl, returning ${ret.size} requests")

        return ret
    }

    private fun reqRecordToContentValues(req: CalendarChangeRequest): ContentValues {
        val values = ContentValues();

        if (req.id > 0)
            values.put(KEY_ID, req.id)
        values.put(KEY_TYPE, req.type.code)
        values.put(KEY_EVENTID, req.eventId);
        values.put(KEY_STATUS, req.status.code)
        values.put(KEY_STATUS_TIMESTAMP, req.lastStatusUpdate)
        values.put(KEY_CALENDAR_ID, req.calendarId);
        values.put(KEY_REPEATING_RULE, req.repeatingRule);
        values.put(KEY_REPEATING_DATE, req.repeatingRDate)
        values.put(KEY_EXT_REPEATING_RULE, req.repeatingExRule);
        values.put(KEY_EXT_REPEATING_DATE, req.repeatingExRDate)
        values.put(KEY_ALL_DAY, req.isAllDay);
        values.put(KEY_TITLE, req.title);
        values.put(KEY_DESC, req.desc);
        values.put(KEY_START, req.startTime);
        values.put(KEY_END, req.endTime);
        values.put(KEY_LOCATION, req.location);
        values.put(KEY_TIMEZONE, req.timezone);
        values.put(KEY_COLOR, req.colour);
        values.put(KEY_REMINDERS, req.reminders.serialize());


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

    private fun cursorToEventRecord(cursor: Cursor): CalendarChangeRequest {

        val reminders = cursor.getString(PROJECTION_KEY_REMINDERS).deserializeNewEventReminders()

        val req = CalendarChangeRequest(
                id = cursor.getLong(PROJECTION_KEY_ID),
                type = EventChangeRequestType.fromInt(cursor.getInt(PROJECTION_KEY_TYPE)),
                calendarId = cursor.getLong(PROJECTION_KEY_CALENDAR_ID),
                status = EventChangeStatus.fromInt(cursor.getInt(PROJECTION_KEY_STATUS)),
                lastStatusUpdate = cursor.getLong(PROJECTION_KEY_STATUS_TIMESTAMP),
                eventId = cursor.getLong(PROJECTION_KEY_EVENTID),
                title = cursor.getString(PROJECTION_KEY_TITLE),
                desc = cursor.getString(PROJECTION_KEY_DESC),
                startTime = cursor.getLong(PROJECTION_KEY_START),
                endTime = cursor.getLong(PROJECTION_KEY_END),
                location = cursor.getString(PROJECTION_KEY_LOCATION),
                colour = cursor.getInt(PROJECTION_KEY_COLOR),
                repeatingRule = cursor.getString(PROJECTION_KEY_REPEATING_RULE),
                repeatingRDate = cursor.getString(PROJECTION_KEY_REPEATING_DATE),
                repeatingExRule = cursor.getString(PROJECTION_KEY_EXT_REPEATING_RULE),
                repeatingExRDate = cursor.getString(PROJECTION_KEY_EXT_REPEATING_DATE),
                isAllDay = cursor.getInt(PROJECTION_KEY_ALL_DAY) != 0,
                timezone = cursor.getString(PROJECTION_KEY_TIMEZONE),

                reminders = reminders
        )

        return req
    }

    companion object {

        private const val LOG_TAG = "CalendarChangeRequestsStorageImplV1"

        private const val TABLE_NAME = "newEventsV1"
        private const val INDEX_NAME = "newEventsIdxV1"


        private const val KEY_ID = "id"

        private const val KEY_TYPE = "t"

        private const val KEY_STATUS = "s"
        private const val KEY_STATUS_TIMESTAMP = "sTm"

        private const val KEY_CALENDAR_ID = "calendarId"
        private const val KEY_EVENTID = "eventId"

        private const val KEY_REPEATING_RULE = "rRule"
        private const val KEY_REPEATING_DATE = "rDate"

        private const val KEY_EXT_REPEATING_RULE = "rExtRule"
        private const val KEY_EXT_REPEATING_DATE = "rExtDate"

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

                KEY_TYPE,

                KEY_CALENDAR_ID,
                KEY_EVENTID,

                KEY_STATUS,
                KEY_STATUS_TIMESTAMP,

                KEY_REPEATING_RULE,
                KEY_REPEATING_DATE,
                KEY_EXT_REPEATING_RULE,
                KEY_EXT_REPEATING_DATE,

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
        const val PROJECTION_KEY_TYPE = 1
        const val PROJECTION_KEY_CALENDAR_ID = 2
        const val PROJECTION_KEY_EVENTID = 3
        const val PROJECTION_KEY_STATUS = 4
        const val PROJECTION_KEY_STATUS_TIMESTAMP = 5
        const val PROJECTION_KEY_REPEATING_RULE = 6
        const val PROJECTION_KEY_REPEATING_DATE = 7
        const val PROJECTION_KEY_EXT_REPEATING_RULE = 8
        const val PROJECTION_KEY_EXT_REPEATING_DATE = 9
        const val PROJECTION_KEY_ALL_DAY = 10
        const val PROJECTION_KEY_TITLE = 11
        const val PROJECTION_KEY_DESC = 12
        const val PROJECTION_KEY_START = 13
        const val PROJECTION_KEY_END = 14
        const val PROJECTION_KEY_LOCATION = 15
        const val PROJECTION_KEY_TIMEZONE = 16
        const val PROJECTION_KEY_COLOR = 17
        const val PROJECTION_KEY_REMINDERS = 18
    }

}