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


package com.github.quarck.calnotify.monitorstorage

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import com.github.quarck.calnotify.calendar.MonitorEventAlertEntry
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.utils.detailed

//import com.github.quarck.calnotify.logs.Logger

class MonitorStorageImplV1(val context: Context) : MonitorStorageImplInterface {

    override fun createDb(db: SQLiteDatabase) {
        val CREATE_PKG_TABLE =
                "CREATE " +
                        "TABLE $TABLE_NAME " +
                        "( " +
                        "$KEY_CALENDAR_ID INTEGER, " +
                        "$KEY_EVENTID INTEGER, " +

                        "$KEY_ALERT_TIME INTEGER, " +

                        "$KEY_INSTANCE_START INTEGER, " +
                        "$KEY_INSTANCE_END INTEGER, " +

                        "$KEY_ALL_DAY INTEGER, " +

                        "$KEY_WE_CREATED_ALERT INTEGER, " +

                        "$KEY_WAS_HANDLED INTEGER, " +

                        "$KEY_RESERVED_INT1 INTEGER, " +
                        "$KEY_RESERVED_INT2 INTEGER, " +


                        "PRIMARY KEY ($KEY_EVENTID, $KEY_ALERT_TIME, $KEY_INSTANCE_START)" +
                        " )"

        DevLog.debug(LOG_TAG, "Creating DB TABLE using query: " + CREATE_PKG_TABLE)

        db.execSQL(CREATE_PKG_TABLE)

        val CREATE_INDEX = "CREATE UNIQUE INDEX $INDEX_NAME ON $TABLE_NAME ($KEY_EVENTID, $KEY_ALERT_TIME, $KEY_INSTANCE_START)"

        DevLog.debug(LOG_TAG, "Creating DB INDEX using query: " + CREATE_INDEX)

        db.execSQL(CREATE_INDEX)

    }

    override fun addAlert(db: SQLiteDatabase, entry: MonitorEventAlertEntry) {

        //DevLog.debug(LOG_TAG, "addAlert $entry")

        val values = recordToContentValues(entry)

        try {
            db.insertOrThrow(TABLE_NAME, // table
                    null, // nullColumnHack
                    values) // key/value -> keys = column names/ values = column
            // values
        }
        catch (ex: SQLiteConstraintException) {
            DevLog.debug(LOG_TAG, "This entry (${entry.eventId} / ${entry.alertTime}) is already in the DB!, updating instead")
            updateAlert(db, entry)
        }
        catch (ex: Exception) {
            DevLog.error(LOG_TAG, "addAlert($entry): exception ${ex.detailed}")
        }
    }

    override fun addAlerts(db: SQLiteDatabase, entries: Collection<MonitorEventAlertEntry>) {

        try {
            db.beginTransaction()

            for (entry in entries)
                addAlert(db, entry)

            db.setTransactionSuccessful()
        }
        finally {
            db.endTransaction()
        }
    }

    override fun deleteAlert(db: SQLiteDatabase, eventId: Long, alertTime: Long, instanceStart: Long) {

        //DevLog.debug(LOG_TAG, "deleteAlert $eventId / $alertTime")

        try {
            db.delete(
                    TABLE_NAME,
                    "$KEY_EVENTID = ? AND $KEY_ALERT_TIME = ? AND $KEY_INSTANCE_START = ?",
                    arrayOf(eventId.toString(), alertTime.toString(), instanceStart.toString()))
        }
        catch (ex: Exception) {
            DevLog.error(LOG_TAG, "deleteAlert($eventId, $alertTime): exception ${ex.detailed}")
        }
    }

    override fun deleteAlerts(db: SQLiteDatabase, entries: Collection<MonitorEventAlertEntry>) {

        try {
            db.beginTransaction()

            for (entry in entries)
                deleteAlert(db, entry.eventId, entry.alertTime, entry.instanceStartTime)

            db.setTransactionSuccessful()
        }
        finally {
            db.endTransaction()
        }
    }

    override fun deleteAlertsMatching(db: SQLiteDatabase, filter: (MonitorEventAlertEntry) -> Boolean) {

        try {
            val alerts = getAlerts(db)

            db.beginTransaction()

            for (alert in alerts) {
                if (filter(alert))
                    deleteAlert(db, alert.eventId, alert.alertTime, alert.instanceStartTime)
            }

            db.setTransactionSuccessful()
        }
        catch (ex: Exception) {
            DevLog.error(LOG_TAG, "deleteAlertsMatching: exception ${ex.detailed}")
        }
        finally {
            db.endTransaction()
        }
    }

    override fun updateAlert(db: SQLiteDatabase, entry: MonitorEventAlertEntry) {
        val values = recordToContentValues(entry)

        //DevLog.debug(LOG_TAG, "Updating alert entry, eventId=${entry.eventId}, alertTime =${entry.alertTime}");

        db.update(TABLE_NAME, // table
                values, // column/value
                "$KEY_EVENTID = ? AND $KEY_ALERT_TIME = ? AND $KEY_INSTANCE_START = ?",
                arrayOf(entry.eventId.toString(), entry.alertTime.toString(), entry.instanceStartTime.toString()))
    }

    override fun updateAlerts(db: SQLiteDatabase, entries: Collection<MonitorEventAlertEntry>) {

        //DevLog.debug(LOG_TAG, "Updating ${entries.size} alerts");

        try {
            db.beginTransaction()

            for (entry in entries) {
                //DevLog.debug(LOG_TAG, "Updating alert entry, eventId=${entry.eventId}, alertTime =${entry.alertTime}");

                val values = recordToContentValues(entry)

                db.update(TABLE_NAME, // table
                        values, // column/value
                        "$KEY_EVENTID = ? AND $KEY_ALERT_TIME = ? AND $KEY_INSTANCE_START = ?",
                        arrayOf(entry.eventId.toString(), entry.alertTime.toString(), entry.instanceStartTime.toString()))
            }

            db.setTransactionSuccessful()
        }
        finally {
            db.endTransaction()
        }
    }

    override fun getAlert(db: SQLiteDatabase, eventId: Long, alertTime: Long, instanceStart: Long): MonitorEventAlertEntry? {

        var ret: MonitorEventAlertEntry? = null

        var cursor: Cursor? = null

        try {
            cursor = db.query(TABLE_NAME, // a. table
                    SELECT_COLUMNS, // b. column names
                    "$KEY_EVENTID = ? AND $KEY_ALERT_TIME = ? AND $KEY_INSTANCE_START = ?",
                    arrayOf(eventId.toString(), alertTime.toString(), instanceStart.toString()),
                    null, // e. group by
                    null, // f. h aving
                    null, // g. order by
                    null) // h. limit

            if (cursor != null && cursor.moveToFirst()) {
                ret = cursorToRecord(cursor)
            }
        }
        catch (ex: Exception) {
            DevLog.error(LOG_TAG, "getAlert: exception $ex, stack: ${ex.detailed}")
        }
        finally {
            cursor?.close()
        }

        //DevLog.debug(LOG_TAG, "getAlert($eventId, $alertTime), returning ${ret}")

        return ret
    }


    override fun getNextAlert(db: SQLiteDatabase, since: Long): Long? {

        var ret: Long? = null;

        val query = "SELECT MIN($KEY_ALERT_TIME) FROM $TABLE_NAME WHERE $KEY_ALERT_TIME >= $since"

        var cursor: Cursor? = null

        try {
            cursor = db.rawQuery(query, null)

            if (cursor != null && cursor.moveToFirst())
                ret = cursor.getLong(0)

        }
        catch (ex: Exception) {
            DevLog.error(LOG_TAG, "getNextAlert: exception ${ex.detailed}")
        }
        finally {
            cursor?.close()
        }

        //DevLog.debug(LOG_TAG, "getNextAlert, returning $ret")

        return ret
    }

    override fun getAlertsAt(db: SQLiteDatabase, time: Long): List<MonitorEventAlertEntry> {
        val ret = arrayListOf<MonitorEventAlertEntry>()

        var cursor: Cursor? = null

        try {
            cursor = db.query(TABLE_NAME, // a. table
                    SELECT_COLUMNS, // b. column names
                    "$KEY_ALERT_TIME = ?",
                    arrayOf(time.toString()),
                    null, // e. group by
                    null, // f. h aving
                    null, // g. order by
                    null) // h. limit

            if (cursor != null && cursor.moveToFirst()) {
                do {
                    ret.add(cursorToRecord(cursor))
                } while (cursor.moveToNext())
            }
        }
        catch (ex: Exception) {
            DevLog.error(LOG_TAG, "getAlertsAt: exception ${ex.detailed}")
        }
        finally {
            cursor?.close()
        }

        //DevLog.debug(LOG_TAG, "getAlertsAt($time), returning ${ret.size} requests")

        return ret
    }

    override fun getAlerts(db: SQLiteDatabase): List<MonitorEventAlertEntry> {
        val ret = arrayListOf<MonitorEventAlertEntry>()

        var cursor: Cursor? = null

        try {
            cursor = db.query(TABLE_NAME, // a. table
                    SELECT_COLUMNS, // b. column names
                    null, // c. selections
                    null,
                    null, // e. group by
                    null, // f. h aving
                    null, // g. order by
                    null) // h. limit

            if (cursor != null && cursor.moveToFirst()) {
                do {
                    ret.add(cursorToRecord(cursor))
                } while (cursor.moveToNext())
            }
        }
        catch (ex: Exception) {
            DevLog.error(LOG_TAG, "getAlertsAt: exception ${ex.detailed}")
        }
        finally {
            cursor?.close()
        }

        //DevLog.debug(LOG_TAG, "getAlerts, returnint ${ret.size} requests")

        return ret
    }

    override fun getInstanceAlerts(db: SQLiteDatabase, eventId: Long, instanceStart: Long): List<MonitorEventAlertEntry> {

        val ret = arrayListOf<MonitorEventAlertEntry>()

        var cursor: Cursor? = null

        val selection = "$KEY_EVENTID = ? AND $KEY_INSTANCE_START = ?"
        val selectionArgs = arrayOf(eventId.toString(), instanceStart.toString())

        try {
            cursor = db.query(TABLE_NAME, // a. table
                    SELECT_COLUMNS, // b. column names
                    selection,
                    selectionArgs,
                    null, // e. group by
                    null, // f. h aving
                    null, // g. order by
                    null) // h. limit

            if (cursor != null && cursor.moveToFirst()) {
                do {
                    ret.add(cursorToRecord(cursor))
                } while (cursor.moveToNext())
            }
        }
        catch (ex: Exception) {
            DevLog.error(LOG_TAG, "getInstanceAlerts: exception ${ex.detailed}")
        }
        finally {
            cursor?.close()
        }

        //DevLog.debug(LOG_TAG, "getAlerts, returnint ${ret.size} requests")

        return ret
    }

    override fun getAlertsForInstanceStartRange(db: SQLiteDatabase, scanFrom: Long, scanTo: Long): List<MonitorEventAlertEntry> {
        val ret = arrayListOf<MonitorEventAlertEntry>()

        var cursor: Cursor? = null

        val selection = "$KEY_INSTANCE_START >= ? AND $KEY_INSTANCE_START <= ?"
        val selectionArgs = arrayOf(scanFrom.toString(), scanTo.toString())

        try {
            cursor = db.query(TABLE_NAME, // a. table
                    SELECT_COLUMNS, // b. column names
                    selection,
                    selectionArgs,
                    null, // e. group by
                    null, // f. h aving
                    null, // g. order by
                    null) // h. limit

            if (cursor != null && cursor.moveToFirst()) {
                do {
                    ret.add(cursorToRecord(cursor))
                } while (cursor.moveToNext())
            }
        }
        catch (ex: Exception) {
            DevLog.error(LOG_TAG, "getAlertsAt: exception ${ex.detailed}")
        }
        finally {
            cursor?.close()
        }

        //DevLog.debug(LOG_TAG, "getAlerts, returnint ${ret.size} requests")

        return ret
    }

    override fun getAlertsForAlertRange(db: SQLiteDatabase, scanFrom: Long, scanTo: Long): List<MonitorEventAlertEntry> {
        val ret = arrayListOf<MonitorEventAlertEntry>()

        var cursor: Cursor? = null

        val selection = "$KEY_ALERT_TIME >= ? AND $KEY_ALERT_TIME <= ?"
        val selectionArgs = arrayOf(scanFrom.toString(), scanTo.toString())

        try {
            cursor = db.query(TABLE_NAME, // a. table
                    SELECT_COLUMNS, // b. column names
                    selection,
                    selectionArgs,
                    null, // e. group by
                    null, // f. h aving
                    null, // g. order by
                    null) // h. limit

            if (cursor != null && cursor.moveToFirst()) {
                do {
                    ret.add(cursorToRecord(cursor))
                } while (cursor.moveToNext())
            }
        }
        catch (ex: Exception) {
            DevLog.error(LOG_TAG, "getAlertsAt: exception ${ex.detailed}")
        }
        finally {
            cursor?.close()
        }

        //DevLog.debug(LOG_TAG, "getAlerts, returnint ${ret.size} requests")

        return ret
    }

    private fun recordToContentValues(entry: MonitorEventAlertEntry): ContentValues {
        val values = ContentValues();

        values.put(KEY_CALENDAR_ID, -1)
        values.put(KEY_EVENTID, entry.eventId);
        values.put(KEY_ALERT_TIME, entry.alertTime)
        values.put(KEY_INSTANCE_START, entry.instanceStartTime);
        values.put(KEY_INSTANCE_END, entry.instanceEndTime);
        values.put(KEY_ALL_DAY, if (entry.isAllDay) 1 else 0)
        values.put(KEY_WE_CREATED_ALERT, if (entry.alertCreatedByUs) 1 else 0)
        values.put(KEY_WAS_HANDLED, if (entry.wasHandled) 1 else 0)

        // Fill reserved keys with some placeholders
        values.put(KEY_RESERVED_INT1, 0L)
        values.put(KEY_RESERVED_INT2, 0L)

        return values;
    }

    private fun cursorToRecord(cursor: Cursor): MonitorEventAlertEntry {

        return MonitorEventAlertEntry(
                eventId = cursor.getLong(PROJECTION_KEY_EVENTID),
                alertTime = cursor.getLong(PROJECTION_KEY_ALERT_TIME),
                instanceStartTime = cursor.getLong(PROJECTION_KEY_INSTANCE_START),
                instanceEndTime = cursor.getLong(PROJECTION_KEY_INSTANCE_END),
                isAllDay = cursor.getInt(PROJECTION_KEY_ALL_DAY) != 0,
                alertCreatedByUs = cursor.getInt(PROJECTION_KEY_WE_CREATED_ALERT) != 0,
                wasHandled = cursor.getInt(PROJECTION_KEY_WAS_HANDLED) != 0
        )
    }


    companion object {
        private const val LOG_TAG = "MonitorStorageImplV1"

        private const val TABLE_NAME = "manualAlertsV1"
        private const val INDEX_NAME = "manualAlertsV1IdxV1"

        private const val KEY_CALENDAR_ID = "calendarId"
        private const val KEY_EVENTID = "eventId"
        private const val KEY_ALL_DAY = "allDay"
        private const val KEY_ALERT_TIME = "alertTime"
        private const val KEY_INSTANCE_START = "instanceStart"
        private const val KEY_INSTANCE_END = "instanceEnd"
        private const val KEY_WE_CREATED_ALERT = "alertCreatedByUs"
        private const val KEY_WAS_HANDLED = "wasHandled"


        private const val KEY_RESERVED_INT1 = "i1"
        private const val KEY_RESERVED_INT2 = "i2"

        private val SELECT_COLUMNS = arrayOf<String>(
                KEY_CALENDAR_ID,
                KEY_EVENTID,
                KEY_ALERT_TIME,
                KEY_INSTANCE_START,
                KEY_INSTANCE_END,
                KEY_ALL_DAY,
                KEY_WE_CREATED_ALERT,
                KEY_WAS_HANDLED
        )

        const val PROJECTION_KEY_CALENDAR_ID = 0;
        const val PROJECTION_KEY_EVENTID = 1;
        const val PROJECTION_KEY_ALERT_TIME = 2;
        const val PROJECTION_KEY_INSTANCE_START = 3;
        const val PROJECTION_KEY_INSTANCE_END = 4;
        const val PROJECTION_KEY_ALL_DAY = 5;
        const val PROJECTION_KEY_WE_CREATED_ALERT = 6;
        const val PROJECTION_KEY_WAS_HANDLED = 7;
    }

}