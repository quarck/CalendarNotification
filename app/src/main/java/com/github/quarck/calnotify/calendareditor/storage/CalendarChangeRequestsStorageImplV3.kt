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
import com.github.quarck.calnotify.calendar.CalendarEventDetails
import com.github.quarck.calnotify.calendar.deserializeCalendarEventReminders
import com.github.quarck.calnotify.calendar.serialize
import com.github.quarck.calnotify.calendareditor.*
import com.github.quarck.calnotify.logs.DevLog
import java.util.*

class CalendarChangeRequestsStorageImplV3 : CalendarChangeRequestsStorageImplInterface {

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

                        "$KEY_NUM_RETRIES INTEGER, " +
                        "$KEY_LAST_RETRY_TIME INTEGER, " +

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

                        "$KEY_OLD_REPEATING_RULE TEXT, " +
                        "$KEY_OLD_REPEATING_DATE TEXT, " +
                        "$KEY_OLD_EXT_REPEATING_RULE TEXT, " +
                        "$KEY_OLD_EXT_REPEATING_DATE TEXT, " +
                        "$KEY_OLD_ALL_DAY INTEGER, " +
                        "$KEY_OLD_TITLE TEXT, " +
                        "$KEY_OLD_DESC TEXT, " +
                        "$KEY_OLD_START INTEGER, " +
                        "$KEY_OLD_END INTEGER, " +
                        "$KEY_OLD_LOCATION TEXT, " +
                        "$KEY_OLD_TIMEZONE TEXT, " +
                        "$KEY_OLD_COLOR INTEGER, " +
                        "$KEY_OLD_REMINDERS TEXT, " +

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
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME_V1);
        db.execSQL("DROP INDEX IF EXISTS " + INDEX_NAME_V1);

        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME_V2);
        db.execSQL("DROP INDEX IF EXISTS " + INDEX_NAME_V2);

        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        db.execSQL("DROP INDEX IF EXISTS " + INDEX_NAME);
    }


    override fun addImpl(db: SQLiteDatabase, req: CalendarChangeRequest) {

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

    override fun deleteImpl(db: SQLiteDatabase, req: CalendarChangeRequest) {
        db.delete(
                TABLE_NAME,
                " $KEY_ID = ?",
                arrayOf(req.id.toString()))

    }

    override fun deleteForEventIdImpl(db: SQLiteDatabase, eventId: Long) {
        db.delete(
                TABLE_NAME,
                " $KEY_EVENTID = ?",
                arrayOf(eventId.toString()))
    }

    override fun deleteManyImpl(db: SQLiteDatabase, requests: List<CalendarChangeRequest>) {

        try {
            db.beginTransaction()

            for (req in requests) {
                deleteImpl(db, req)
            }

            db.setTransactionSuccessful()
        }
        finally {
            db.endTransaction()
        }
    }

    override fun updateImpl(db: SQLiteDatabase, req: CalendarChangeRequest) {
        val values = reqRecordToContentValues(req)

        db.update(TABLE_NAME, // table
                values, // column/value
                "$KEY_ID = ?",
                arrayOf(req.id.toString()))
    }

    override fun updateManyImpl(db: SQLiteDatabase, requests: List<CalendarChangeRequest>) {
        try {
            db.beginTransaction()

            for (req in requests) {
                updateImpl(db, req)
            }

            db.setTransactionSuccessful()
        }
        finally {
            db.endTransaction()
        }
    }

    override fun getImpl(db: SQLiteDatabase): List<CalendarChangeRequest> {

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
        values.put(KEY_NUM_RETRIES, req.numRetries)
        values.put(KEY_LAST_RETRY_TIME, req.lastRetryTime)

        values.put(KEY_REPEATING_RULE, req.details.repeatingRule);
        values.put(KEY_REPEATING_DATE, req.details.repeatingRDate)
        values.put(KEY_EXT_REPEATING_RULE, req.details.repeatingExRule);
        values.put(KEY_EXT_REPEATING_DATE, req.details.repeatingExRDate)
        values.put(KEY_ALL_DAY, req.details.isAllDay);
        values.put(KEY_TITLE, req.details.title);
        values.put(KEY_DESC, req.details.desc);
        values.put(KEY_START, req.details.startTime);
        values.put(KEY_END, req.details.endTime);
        values.put(KEY_LOCATION, req.details.location);
        values.put(KEY_TIMEZONE, req.details.timezone);
        values.put(KEY_COLOR, req.details.color);
        values.put(KEY_REMINDERS, req.details.reminders.serialize());

        values.put(KEY_OLD_REPEATING_RULE, req.oldDetails.repeatingRule);
        values.put(KEY_OLD_REPEATING_DATE, req.oldDetails.repeatingRDate)
        values.put(KEY_OLD_EXT_REPEATING_RULE, req.oldDetails.repeatingExRule);
        values.put(KEY_OLD_EXT_REPEATING_DATE, req.oldDetails.repeatingExRDate)
        values.put(KEY_OLD_ALL_DAY, req.oldDetails.isAllDay);
        values.put(KEY_OLD_TITLE, req.oldDetails.title);
        values.put(KEY_OLD_DESC, req.oldDetails.desc);
        values.put(KEY_OLD_START, req.oldDetails.startTime);
        values.put(KEY_OLD_END, req.oldDetails.endTime);
        values.put(KEY_OLD_LOCATION, req.oldDetails.location);
        values.put(KEY_OLD_TIMEZONE, req.oldDetails.timezone);
        values.put(KEY_OLD_COLOR, req.oldDetails.color);
        values.put(KEY_OLD_REMINDERS, req.oldDetails.reminders.serialize());

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

        val reminders = cursor.getString(PROJECTION_KEY_REMINDERS).deserializeCalendarEventReminders()

        val details = CalendarEventDetails (
                title = cursor.getString(PROJECTION_KEY_TITLE),
                desc = cursor.getString(PROJECTION_KEY_DESC),
                startTime = cursor.getLong(PROJECTION_KEY_START),
                endTime = cursor.getLong(PROJECTION_KEY_END),
                location = cursor.getString(PROJECTION_KEY_LOCATION),
                color = cursor.getInt(PROJECTION_KEY_COLOR),
                repeatingRule = cursor.getString(PROJECTION_KEY_REPEATING_RULE),
                repeatingRDate = cursor.getString(PROJECTION_KEY_REPEATING_DATE),
                repeatingExRule = cursor.getString(PROJECTION_KEY_EXT_REPEATING_RULE),
                repeatingExRDate = cursor.getString(PROJECTION_KEY_EXT_REPEATING_DATE),
                isAllDay = cursor.getInt(PROJECTION_KEY_ALL_DAY) != 0,
                timezone = cursor.getString(PROJECTION_KEY_TIMEZONE),
                reminders = reminders
        )

        val oldReminders = cursor.getString(PROJECTION_KEY_OLD_REMINDERS).deserializeCalendarEventReminders()

        val oldDetails = CalendarEventDetails (
                title = cursor.getString(PROJECTION_KEY_OLD_TITLE),
                desc = cursor.getString(PROJECTION_KEY_OLD_DESC),
                startTime = cursor.getLong(PROJECTION_KEY_OLD_START),
                endTime = cursor.getLong(PROJECTION_KEY_OLD_END),
                location = cursor.getString(PROJECTION_KEY_OLD_LOCATION),
                color = cursor.getInt(PROJECTION_KEY_OLD_COLOR),
                repeatingRule = cursor.getString(PROJECTION_KEY_OLD_REPEATING_RULE),
                repeatingRDate = cursor.getString(PROJECTION_KEY_OLD_REPEATING_DATE),
                repeatingExRule = cursor.getString(PROJECTION_KEY_OLD_EXT_REPEATING_RULE),
                repeatingExRDate = cursor.getString(PROJECTION_KEY_OLD_EXT_REPEATING_DATE),
                isAllDay = cursor.getInt(PROJECTION_KEY_OLD_ALL_DAY) != 0,
                timezone = cursor.getString(PROJECTION_KEY_OLD_TIMEZONE),
                reminders = oldReminders
        )

        val req = CalendarChangeRequest(
                id = cursor.getLong(PROJECTION_KEY_ID),
                type = EventChangeRequestType.fromInt(cursor.getInt(PROJECTION_KEY_TYPE)),
                calendarId = cursor.getLong(PROJECTION_KEY_CALENDAR_ID),
                status = EventChangeStatus.fromInt(cursor.getInt(PROJECTION_KEY_STATUS)),
                lastStatusUpdate = cursor.getLong(PROJECTION_KEY_STATUS_TIMESTAMP),
                eventId = cursor.getLong(PROJECTION_KEY_EVENTID),
                numRetries = cursor.getInt(PROJECTION_KEY_NUM_RETRIES),
                lastRetryTime = cursor.getLong(PROJECTION_KEY_LAST_RETRY_TIME),
                details = details,
                oldDetails = oldDetails
        )

        return req
    }

    companion object {

        private const val LOG_TAG = "CalendarChangeRequestsStorageImplV3"

        private const val TABLE_NAME_V1 = "newEventsV1"
        private const val INDEX_NAME_V1 = "newEventsIdxV1"

        private const val TABLE_NAME_V2 = "newEventsV2"
        private const val INDEX_NAME_V2 = "newEventsIdxV2"

        private const val TABLE_NAME = "newEventsV3"
        private const val INDEX_NAME = "newEventsIdxV3"

        private const val KEY_ID = "id"
        private const val KEY_TYPE = "t"
        private const val KEY_STATUS = "s"
        private const val KEY_STATUS_TIMESTAMP = "sTm"
        private const val KEY_NUM_RETRIES = "retries"
        private const val KEY_LAST_RETRY_TIME = "retryTime"

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

        private const val KEY_OLD_REPEATING_RULE = "oldRRule"
        private const val KEY_OLD_REPEATING_DATE = "oldRDate"
        private const val KEY_OLD_EXT_REPEATING_RULE = "oldRExtRule"
        private const val KEY_OLD_EXT_REPEATING_DATE = "oldRExtDate"
        private const val KEY_OLD_ALL_DAY = "oldAllDay"
        private const val KEY_OLD_TITLE = "oldTitle"
        private const val KEY_OLD_DESC = "oldDesc"
        private const val KEY_OLD_START = "oldEventStart"
        private const val KEY_OLD_END = "oldEventEnd"
        private const val KEY_OLD_LOCATION = "oldLocation"
        private const val KEY_OLD_TIMEZONE = "oldTz"
        private const val KEY_OLD_COLOR = "oldColor"
        private const val KEY_OLD_REMINDERS = "oldReminders"

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
                KEY_NUM_RETRIES,
                KEY_LAST_RETRY_TIME,

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
                KEY_REMINDERS,

                KEY_OLD_REPEATING_RULE,
                KEY_OLD_REPEATING_DATE,
                KEY_OLD_EXT_REPEATING_RULE,
                KEY_OLD_EXT_REPEATING_DATE,
                KEY_OLD_ALL_DAY,
                KEY_OLD_TITLE,
                KEY_OLD_DESC,
                KEY_OLD_START,
                KEY_OLD_END,
                KEY_OLD_LOCATION,
                KEY_OLD_TIMEZONE,
                KEY_OLD_COLOR,
                KEY_OLD_REMINDERS
        )

        const val PROJECTION_KEY_ID = 0
        const val PROJECTION_KEY_TYPE = 1
        const val PROJECTION_KEY_CALENDAR_ID = 2
        const val PROJECTION_KEY_EVENTID = 3
        const val PROJECTION_KEY_STATUS = 4
        const val PROJECTION_KEY_STATUS_TIMESTAMP = 5
        const val PROJECTION_KEY_NUM_RETRIES = 6
        const val PROJECTION_KEY_LAST_RETRY_TIME = 7


        const val PROJECTION_KEY_REPEATING_RULE = 8
        const val PROJECTION_KEY_REPEATING_DATE = 9
        const val PROJECTION_KEY_EXT_REPEATING_RULE = 10
        const val PROJECTION_KEY_EXT_REPEATING_DATE = 11
        const val PROJECTION_KEY_ALL_DAY = 12
        const val PROJECTION_KEY_TITLE = 13
        const val PROJECTION_KEY_DESC = 14
        const val PROJECTION_KEY_START = 15
        const val PROJECTION_KEY_END = 16
        const val PROJECTION_KEY_LOCATION = 17
        const val PROJECTION_KEY_TIMEZONE = 18
        const val PROJECTION_KEY_COLOR = 19
        const val PROJECTION_KEY_REMINDERS = 20

        const val PROJECTION_KEY_OLD_REPEATING_RULE = 21
        const val PROJECTION_KEY_OLD_REPEATING_DATE = 22
        const val PROJECTION_KEY_OLD_EXT_REPEATING_RULE = 23
        const val PROJECTION_KEY_OLD_EXT_REPEATING_DATE = 24
        const val PROJECTION_KEY_OLD_ALL_DAY = 25
        const val PROJECTION_KEY_OLD_TITLE = 26
        const val PROJECTION_KEY_OLD_DESC = 27
        const val PROJECTION_KEY_OLD_START = 28
        const val PROJECTION_KEY_OLD_END = 29
        const val PROJECTION_KEY_OLD_LOCATION = 30
        const val PROJECTION_KEY_OLD_TIMEZONE = 31
        const val PROJECTION_KEY_OLD_COLOR = 32
        const val PROJECTION_KEY_OLD_REMINDERS = 33
    }

}