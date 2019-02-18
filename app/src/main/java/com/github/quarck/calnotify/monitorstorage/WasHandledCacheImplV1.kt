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
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.utils.detailed

//import com.github.quarck.calnotify.logs.Logger

class WasHandledCacheImplV1(val context: Context) : WasHandledCacheImplInterface {

    override fun createDb(db: SQLiteDatabase) {
        val CREATE_PKG_TABLE =
                "CREATE " +
                        "TABLE $TABLE_NAME " +
                        "( " +
                        "$KEY_EVENT_MD5 TEXT, " +
                        "$KEY_HANDLED_TIME INTEGER, " +

                        "$KEY_RESERVED_INT1 INTEGER, " +
                        "$KEY_RESERVED_INT2 INTEGER, " +
                        "$KEY_RESERVED_STR1 TEXT, " +
                        "$KEY_RESERVED_STR2 TEXT, " +
                        "PRIMARY KEY ($KEY_EVENT_MD5)" +
                        " )"

        DevLog.debug(LOG_TAG, "Creating DB TABLE using query: " + CREATE_PKG_TABLE)

        db.execSQL(CREATE_PKG_TABLE)

        val CREATE_INDEX = "CREATE UNIQUE INDEX $INDEX_NAME ON $TABLE_NAME ($KEY_EVENT_MD5, $KEY_HANDLED_TIME)"

        DevLog.debug(LOG_TAG, "Creating DB INDEX using query: " + CREATE_INDEX)

        db.execSQL(CREATE_INDEX)
    }

    override fun addHandledAlert(db: SQLiteDatabase, entry: EventAlertRecord) {

       val values = recordToContentValues(entry)

        try {
            db.insertOrThrow(
                    TABLE_NAME, // table
                    null, // nullColumnHack
                    values) // key/value -> keys = column names/ values = column
            // values
        }
        catch (ex: SQLiteConstraintException) {
            DevLog.debug(LOG_TAG, "This entry (${entry.eventId} / ${entry.alertTime}) is already in the DB!, updating instead")
        }
        catch (ex: Exception) {
            DevLog.error(LOG_TAG, "addAlert($entry): exception ${ex.detailed}")
        }
    }

    override fun addHandledAlerts(db: SQLiteDatabase, entries: Collection<EventAlertRecord>) {

        try {
            db.beginTransaction()

            for (entry in entries)
                addHandledAlert(db, entry)

            db.setTransactionSuccessful()
        }
        finally {
            db.endTransaction()
        }
    }

    override fun getAlertWasHandled(db: SQLiteDatabase, entry: EventAlertRecord): Boolean {

        var ret = false

        var cursor: Cursor? = null

        try {
            cursor = db.query(
                    TABLE_NAME,                 // a. table
                    arrayOf(KEY_EVENT_MD5),     // b. column names
                    "$KEY_EVENT_MD5 = ?",   // selection format
                    arrayOf(entry.essenceMD5()),     // selection args
                    null, // e. group by
                    null, // f. h aving
                    null, // g. order by
                    null) // h. limit

            if (cursor != null && cursor.moveToFirst()) {
                ret = true
            }
        }
        catch (ex: Exception) {
            DevLog.error(LOG_TAG, "getAlertWasHandled: exception ${ex.detailed}")
        }
        finally {
            cursor?.close()
        }

        DevLog.info(LOG_TAG, "getAlertWasHandled: $ret for ${entry.toPublicString()}")

        return ret
    }

    override fun getAlertsWereHandled(db: SQLiteDatabase, entries: Collection<EventAlertRecord>): BooleanArray {
        // a bit lazy implementation for now
        return entries.map { it -> getAlertWasHandled(db, it) }.toBooleanArray()
    }

    override fun removeOldEntries(db: SQLiteDatabase, minAge: Long): Int {

        var ret = 0

        try {

            val deleteUpTo = System.currentTimeMillis() - minAge

            ret = db.delete(
                    TABLE_NAME,
                    "$KEY_HANDLED_TIME < ?",
                    arrayOf(deleteUpTo.toString())
            )
        }
        catch (ex: Exception) {
            DevLog.error(LOG_TAG, "removeOldEntries($minAge): exception ${ex.detailed}")
        }

        return ret
    }

    private fun recordToContentValues(entry: EventAlertRecord): ContentValues {

        val values = ContentValues()

        values.put(KEY_EVENT_MD5, entry.essenceMD5())
        values.put(KEY_HANDLED_TIME, System.currentTimeMillis())

        values.put(KEY_RESERVED_INT1, 0)
        values.put(KEY_RESERVED_INT2, 0)
        values.put(KEY_RESERVED_STR1, "")
        values.put(KEY_RESERVED_STR2, "")

        return values;
    }

    companion object {
        private const val LOG_TAG = "WasHandledCacheImplV1"

        private const val TABLE_NAME = "manualAlertsV1"
        private const val INDEX_NAME = "manualAlertsV1IdxV1"

        private const val KEY_EVENT_MD5 = "a"
        private const val KEY_HANDLED_TIME = "b"

        private const val KEY_RESERVED_INT1 = "w"
        private const val KEY_RESERVED_INT2 = "x"
        private const val KEY_RESERVED_STR1 = "y"
        private const val KEY_RESERVED_STR2 = "z"

    }
}