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
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.logs.Logger
import java.util.*

public class EventsStorage(context: Context)
: SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    // Still used by some test code
    fun addEvent(
            eventId: Long,
            alertTime: Long,
            title: String,
//            description: String,
            startTime: Long, endTime: Long,
            location: String,
            lastEventUpdate: Long,
            displayStatus: EventDisplayStatus,
            color: Int
    ): EventRecord {
        var ret =
                EventRecord(
                        eventId = eventId,
                        notificationId = 0,
                        alertTime = alertTime,
                        title = title,
//                        description = description,
                        startTime = startTime,
                        endTime = endTime,
                        location = location,
                        lastEventUpdate = lastEventUpdate,
                        displayStatus = displayStatus,
                        color = color
                )

        synchronized (EventsStorage::class.java) {
            addEvent(ret)
        }

        return ret
    }

    fun addEvent(event: EventRecord)
            = synchronized (EventsStorage::class.java) { addEventImpl(event) }

    fun updateEvent(event: EventRecord,
                    alertTime: Long? = null,
                    title: String? = null,
                    snoozedUntil: Long? = null,
                    startTime: Long? = null,
                    endTime: Long? = null,
                    location: String? = null,
                    lastEventUpdate: Long? = null,
                    displayStatus: EventDisplayStatus? = null,
                    color: Int? = null
    ) {
        var newEvent =
                event.copy(
                        alertTime = alertTime ?: event.alertTime,
                        title = title ?: event.title,
                        snoozedUntil = snoozedUntil ?: event.snoozedUntil,
                        startTime = startTime ?: event.startTime,
                        endTime = endTime ?: event.endTime,
                        location = location ?: event.location,
                        lastEventUpdate = lastEventUpdate ?: event.lastEventUpdate,
                        displayStatus = displayStatus ?: event.displayStatus,
                        color = color ?: event.color
                );

        updateEvent(newEvent)
    }

    fun updateEvent(event: EventRecord)
            = synchronized(EventsStorage::class.java) { updateEventImpl(event) }

    fun getEvent(eventId: Long): EventRecord?
            = synchronized(EventsStorage::class.java) { return getEventImpl(eventId) }

    fun deleteEvent(eventId: Long)
            = synchronized(EventsStorage::class.java) { deleteEventImpl(eventId) }

    fun deleteEvent(ev: EventRecord)
            = synchronized(EventsStorage::class.java) { deleteEventImpl(ev.eventId) }

    val events: List<EventRecord>
        get() = synchronized(EventsStorage::class.java) { return eventsImpl }

    ////////////////////////// Implementations for DB operations //////////////////////
    ///// TODO: move into *Impl class

    override fun onCreate(db: SQLiteDatabase) {
        var CREATE_PKG_TABLE =
                "CREATE " +
                        "TABLE ${TABLE_NAME} " +
                        "( " +
                        "${KEY_EVENTID} INTEGER PRIMARY KEY, " +
                        "${KEY_NOTIFICATIONID} INTEGER, " +
                        "${KEY_TITLE} TEXT, " +
                        "${KEY_DESC} TEXT, " +
                        "${KEY_START} INTEGER, " +
                        "${KEY_END} INTEGER, " +
                        "${KEY_LOCATION} LOCATION, " +
                        "${KEY_SNOOZED_UNTIL} INTEGER, " +
                        "${KEY_LAST_EVENT_FIRE} INTEGER, " +
                        "${KEY_IS_DISPLAYED} INTEGER, " +
                        "${KEY_COLOR} INTEGER, " +
                        "${KEY_ALERT_TIME} INTEGER, " +
                        "${KEY_RESERVED_STR1} TEXT, " +
                        "${KEY_RESERVED_STR2} TEXT, " +
                        "${KEY_RESERVED_STR3} TEXT, " +
                        "${KEY_RESERVED_INT1} INTEGER, " +
                        "${KEY_RESERVED_INT2} INTEGER, " +
                        "${KEY_RESERVED_INT3} INTEGER" +
                        " )"

        logger.debug("Creating DB TABLE using query: " + CREATE_PKG_TABLE)

        db.execSQL(CREATE_PKG_TABLE)

        val CREATE_INDEX = "CREATE UNIQUE INDEX ${INDEX_NAME} ON ${TABLE_NAME} (${KEY_EVENTID})"

        logger.debug("Creating DB INDEX using query: " + CREATE_INDEX)

        db.execSQL(CREATE_INDEX)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        logger.debug("DROPPING table and index")

        if (oldVersion != newVersion) {
            if (oldVersion < DATABASE_RELEASE_ONE_VERSION) {
                db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
                db.execSQL("DROP INDEX IF EXISTS " + INDEX_NAME);
                onCreate(db);
            } else {
                TODO("This has to be implemented whenever you are going to extend the database")
            }
        }
    }

    private fun addEventImpl(event: EventRecord) {
        logger.debug("addEvent " + event.toString())

        if (event.notificationId == 0)
            event.notificationId = nextNotificationId();

        val db = this.writableDatabase

        val values = eventRecordToContentValues(event, true)

        try {
            db.insertOrThrow(TABLE_NAME, // table
                    null, // nullColumnHack
                    values) // key/value -> keys = column names/ values = column
            // values
            db.close()
        } catch (ex: SQLiteConstraintException) {
            // Close Db before attempting to open it again from another method
            db.close()

            logger.debug("This entry (${event.eventId}) is already in the DB, updating!")

            // persist original notification id in this case
            event.notificationId = getEventImpl(event.eventId)?.notificationId ?: event.notificationId;

            updateEventImpl(event)
        }
    }

    private fun updateEventImpl(event: EventRecord) {
        val db = this.writableDatabase

        val values = eventRecordToContentValues(event)

        logger.debug("Updating event, eventId=${event.eventId}");

        db.update(TABLE_NAME, // table
                values, // column/value
                KEY_EVENTID + " = ?", // selections
                arrayOf<String>(event.eventId.toString())) // selection args

        db.close()
    }

    private fun nextNotificationId(): Int {
        var ret = 0;

        val db = this.readableDatabase

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

    private fun getEventImpl(eventId: Long): EventRecord? {
        val db = this.readableDatabase

        val cursor = db.query(TABLE_NAME, // a. table
                SELECT_COLUMNS, // b. column names
                " ${KEY_EVENTID} = ?", // c. selections
                arrayOf<String>(eventId.toString()), // d. selections args
                null, // e. group by
                null, // f. h aving
                null, // g. order by
                null) // h. limit

        var event: EventRecord? = null

        if (cursor != null) {
            if (cursor.moveToFirst())
                event = cursorToEventRecord(cursor)

            cursor.close()
        }

        return event
    }


    private val eventsImpl: List<EventRecord>
        get() {
            val ret = LinkedList<EventRecord>()

            val query = "SELECT * FROM " + TABLE_NAME

            val db = this.readableDatabase
            val cursor = db.rawQuery(query, null)

            if (cursor.moveToFirst()) {
                do {
                    ret.add(cursorToEventRecord(cursor))

                } while (cursor.moveToNext())

                cursor.close()
            }

            logger.debug("eventsImpl, returnint ${ret.size} events")

            return ret
        }

    private fun deleteEventImpl(eventId: Long) {
        val db = this.writableDatabase

        db.delete(TABLE_NAME, // table name
                KEY_EVENTID + " = ?", // selections
                arrayOf(eventId.toString())) // selections args

        db.close()

        logger.debug("deleteNotification ${eventId}")
    }

    private fun eventRecordToContentValues(event: EventRecord, includeId: Boolean = false): ContentValues {
        var values = ContentValues();

        if (includeId)
            values.put(KEY_EVENTID, event.eventId);

        values.put(KEY_NOTIFICATIONID, event.notificationId);
        values.put(KEY_TITLE, event.title);
        values.put(KEY_DESC, ""); // we have no description anymore
        values.put(KEY_START, event.startTime);
        values.put(KEY_END, event.endTime);
        values.put(KEY_LOCATION, event.location);
        values.put(KEY_SNOOZED_UNTIL, event.snoozedUntil);
        values.put(KEY_LAST_EVENT_FIRE, event.lastEventUpdate);
        values.put(KEY_IS_DISPLAYED, event.displayStatus.code);
        values.put(KEY_COLOR, event.color)
        values.put(KEY_ALERT_TIME, event.alertTime)

        return values;
    }

    private fun cursorToEventRecord(cursor: Cursor): EventRecord {

        return EventRecord(
                eventId = cursor.getLong(0),
                notificationId = cursor.getInt(1),
                title = cursor.getString(2),
//                description = cursor.getString(3),
                startTime = cursor.getLong(4),
                endTime = cursor.getLong(5),
                location = cursor.getString(6),
                snoozedUntil = cursor.getLong(7),
                lastEventUpdate = cursor.getLong(8),
                displayStatus = EventDisplayStatus.fromInt(cursor.getInt(9)),
                color = cursor.getInt(10),
                alertTime = cursor.getLong(11)
        )
    }

    companion object {
        private val logger = Logger("EventsStorage")

        private const val DATABASE_VERSION = 6
        private const val DATABASE_RELEASE_ONE_VERSION = 6

        private const val DATABASE_NAME = "Events"

        private const val TABLE_NAME = "events"
        private const val INDEX_NAME = "eventsIdx"

        private const val KEY_EVENTID = "eventId"
        private const val KEY_NOTIFICATIONID = "notificationId"
        private const val KEY_TITLE = "title"
        private const val KEY_DESC = "description"
        private const val KEY_START = "start"
        private const val KEY_END = "end"
        private const val KEY_LOCATION = "location"
        private const val KEY_SNOOZED_UNTIL = "snoozeUntil"
        private const val KEY_IS_DISPLAYED = "displayed"
        private const val KEY_LAST_EVENT_FIRE = "lastFire"
        private const val KEY_COLOR = "color"
        private const val KEY_ALERT_TIME = "alertTime"

        private const val KEY_RESERVED_STR1 = "s1"
        private const val KEY_RESERVED_STR2 = "s2"
        private const val KEY_RESERVED_STR3 = "s3"
        private const val KEY_RESERVED_INT1 = "i1"
        private const val KEY_RESERVED_INT2 = "i2"
        private const val KEY_RESERVED_INT3 = "i3"

        private val SELECT_COLUMNS = arrayOf<String>(
                KEY_EVENTID, KEY_NOTIFICATIONID,
                KEY_TITLE, KEY_DESC,
                KEY_START, KEY_END,
                KEY_LOCATION,
                KEY_SNOOZED_UNTIL,
                KEY_LAST_EVENT_FIRE,
                KEY_IS_DISPLAYED,
                KEY_COLOR,
                KEY_ALERT_TIME
        )
    }
}
