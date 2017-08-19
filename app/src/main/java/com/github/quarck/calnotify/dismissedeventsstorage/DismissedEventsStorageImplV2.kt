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

package com.github.quarck.calnotify.dismissedeventsstorage

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.calendar.EventDisplayStatus
import com.github.quarck.calnotify.logs.DevLog
//import com.github.quarck.calnotify.logs.Logger
import java.util.*

class DismissedEventsStorageImplV2()
    : DismissedEventsStorageImplInterface {

    @Suppress("ConvertToStringTemplate")
    override fun createDb(db: SQLiteDatabase) {

        val CREATE_PKG_TABLE =
                "CREATE " +
                        "TABLE $TABLE_NAME " +
                        "( " +
                        "$KEY_CALENDAR_ID INTEGER, " +
                        "$KEY_EVENTID INTEGER, " +

                        "$KEY_DISMISS_TIME INTEGER, " +
                        "$KEY_DISMISS_TYPE INTEGER, " +

                        "$KEY_ALERT_TIME INTEGER, " +

                        "$KEY_TITLE TEXT, " +

                        "$KEY_START INTEGER, " +
                        "$KEY_END INTEGER, " +

                        "$KEY_INSTANCE_START INTEGER, " +
                        "$KEY_INSTANCE_END INTEGER, " +

                        "$KEY_LOCATION TEXT, " +
                        "$KEY_SNOOZED_UNTIL INTEGER, " +
                        "$KEY_LAST_EVENT_VISIBILITY INTEGER, " +
                        "$KEY_DISPLAY_STATUS INTEGER, " +
                        "$KEY_COLOR INTEGER, " +
                        "$KEY_IS_REPEATING INTEGER, " +

                        "$KEY_ALL_DAY INTEGER, " +
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

                        "PRIMARY KEY ($KEY_EVENTID, $KEY_INSTANCE_START)" +
                        " )"

        DevLog.debug(LOG_TAG, "Creating DB TABLE using query: " + CREATE_PKG_TABLE)

        db.execSQL(CREATE_PKG_TABLE)

        val CREATE_INDEX = "CREATE UNIQUE INDEX $INDEX_NAME ON $TABLE_NAME ($KEY_EVENTID, $KEY_INSTANCE_START)"

        DevLog.debug(LOG_TAG, "Creating DB INDEX using query: " + CREATE_INDEX)

        db.execSQL(CREATE_INDEX)
    }

    override fun dropAll(db: SQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        db.execSQL("DROP INDEX IF EXISTS " + INDEX_NAME);
    }

    override fun addEventImpl(db: SQLiteDatabase, type: EventDismissType, changeTime: Long, event: EventAlertRecord) {

//        DevLog.debug(LOG_TAG, "addEventImpl " + event.eventId)

        val values = eventRecordToContentValues(event, changeTime, type)

        try {
            db.insertOrThrow(TABLE_NAME, // table
                    null, // nullColumnHack
                    values) // key/value -> keys = column names/ values = column
            // values
        }
        catch (ex: SQLiteConstraintException) {
//            DevLog.debug(LOG_TAG, "This entry (${event.eventId}) is already in the DB!")
        }
    }

    override fun addEventsImpl(db: SQLiteDatabase, type: EventDismissType, changeTime: Long, events: Collection<EventAlertRecord>) {

        try {
            db.beginTransaction()

            for (event in events)
                addEventImpl(db, type, changeTime, event)

            db.setTransactionSuccessful()
        }
        finally {
            db.endTransaction()
        }
    }


    override fun deleteEventImpl(db: SQLiteDatabase, entry: DismissedEventAlertRecord) {
        db.delete(
                TABLE_NAME,
                " $KEY_EVENTID = ? AND $KEY_INSTANCE_START = ?",
                arrayOf(entry.event.eventId.toString(), entry.event.instanceStartTime.toString()))

//        DevLog.debug(LOG_TAG, "deleteEventImpl ${entry.event.eventId}, instance=${entry.event.instanceStartTime} ")
    }

    override fun deleteEventImpl(db: SQLiteDatabase, event: EventAlertRecord) {
        db.delete(
                TABLE_NAME,
                " $KEY_EVENTID = ? AND $KEY_INSTANCE_START = ?",
                arrayOf(event.eventId.toString(), event.instanceStartTime.toString()))

//        DevLog.debug(LOG_TAG, "deleteEventImpl ${event.eventId}, instance=${event.instanceStartTime} ")
    }

    override fun clearHistoryImpl(db: SQLiteDatabase) {
        db.delete(TABLE_NAME, null, null)
    }

    override fun getEventsImpl(db: SQLiteDatabase): List<DismissedEventAlertRecord> {

        val ret = LinkedList<DismissedEventAlertRecord>()

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

    private fun eventRecordToContentValues(event: EventAlertRecord, time: Long, type: EventDismissType): ContentValues {
        val values = ContentValues();

        values.put(KEY_CALENDAR_ID, event.calendarId)
        values.put(KEY_EVENTID, event.eventId);
        values.put(KEY_DISMISS_TIME, time)
        values.put(KEY_DISMISS_TYPE, type.code)
        values.put(KEY_ALERT_TIME, event.alertTime)
        values.put(KEY_TITLE, event.title);
        values.put(KEY_START, event.startTime);
        values.put(KEY_END, event.endTime);
        values.put(KEY_INSTANCE_START, event.instanceStartTime);
        values.put(KEY_INSTANCE_END, event.instanceEndTime);
        values.put(KEY_LOCATION, event.location);
        values.put(KEY_SNOOZED_UNTIL, event.snoozedUntil);
        values.put(KEY_LAST_EVENT_VISIBILITY, event.lastStatusChangeTime);
        values.put(KEY_DISPLAY_STATUS, event.displayStatus.code);
        values.put(KEY_COLOR, event.color)
        values.put(KEY_IS_REPEATING, event.isRepeating)
        values.put(KEY_ALL_DAY, if (event.isAllDay) 1 else 0)

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

    private fun cursorToEventRecord(cursor: Cursor): DismissedEventAlertRecord {

        val event = EventAlertRecord(
                calendarId = (cursor.getLong(PROJECTION_KEY_CALENDAR_ID) as Long?) ?: -1L,
                eventId = cursor.getLong(PROJECTION_KEY_EVENTID),
                alertTime = cursor.getLong(PROJECTION_KEY_ALERT_TIME),
                notificationId = 0,
                title = cursor.getString(PROJECTION_KEY_TITLE),
                startTime = cursor.getLong(PROJECTION_KEY_START),
                endTime = cursor.getLong(PROJECTION_KEY_END),
                instanceStartTime = cursor.getLong(PROJECTION_KEY_INSTANCE_START),
                instanceEndTime = cursor.getLong(PROJECTION_KEY_INSTANCE_END),
                location = cursor.getString(PROJECTION_KEY_LOCATION),
                snoozedUntil = cursor.getLong(PROJECTION_KEY_SNOOZED_UNTIL),
                lastStatusChangeTime = cursor.getLong(PROJECTION_KEY_LAST_EVENT_VISIBILITY),
                displayStatus = EventDisplayStatus.fromInt(cursor.getInt(PROJECTION_KEY_DISPLAY_STATUS)),
                color = cursor.getInt(PROJECTION_KEY_COLOR),
                isRepeating = cursor.getInt(PROJECTION_KEY_IS_REPEATING) != 0,
                isAllDay = cursor.getInt(PROJECTION_KEY_ALL_DAY) != 0
        )

        return DismissedEventAlertRecord(
                event,
                cursor.getLong(PROJECTION_KEY_DISMISS_TIME),
                EventDismissType.fromInt(cursor.getInt(PROJECTION_KEY_DISMISS_TYPE))
        )
    }

    companion object {
        private const val LOG_TAG = "DismissedEventsStorageImplV2"

        private const val TABLE_NAME = "dismissedEventsV2"
        private const val INDEX_NAME = "dismissedEventsIdxV2"

        private const val KEY_CALENDAR_ID = "calendarId"
        private const val KEY_EVENTID = "eventId"

        private const val KEY_DISMISS_TIME = "dismissTime"
        private const val KEY_DISMISS_TYPE = "dismissType"

        private const val KEY_IS_REPEATING = "isRepeating"
        private const val KEY_ALL_DAY = "allDay"

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
                KEY_CALENDAR_ID,
                KEY_EVENTID,
                KEY_DISMISS_TIME,
                KEY_DISMISS_TYPE,
                KEY_ALERT_TIME,
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
        const val PROJECTION_KEY_DISMISS_TIME = 2;
        const val PROJECTION_KEY_DISMISS_TYPE = 3;
        const val PROJECTION_KEY_ALERT_TIME = 4;
        const val PROJECTION_KEY_TITLE = 5;
        const val PROJECTION_KEY_START = 6;
        const val PROJECTION_KEY_END = 7;
        const val PROJECTION_KEY_INSTANCE_START = 8;
        const val PROJECTION_KEY_INSTANCE_END = 9;
        const val PROJECTION_KEY_LOCATION = 10;
        const val PROJECTION_KEY_SNOOZED_UNTIL = 11;
        const val PROJECTION_KEY_LAST_EVENT_VISIBILITY = 12;
        const val PROJECTION_KEY_DISPLAY_STATUS = 13;
        const val PROJECTION_KEY_COLOR = 14;
        const val PROJECTION_KEY_IS_REPEATING = 15;
        const val PROJECTION_KEY_ALL_DAY = 16;
    }
}
