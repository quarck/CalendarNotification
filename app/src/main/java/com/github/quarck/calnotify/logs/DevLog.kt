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


package com.github.quarck.calnotify.logs

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.text.format.DateUtils
import android.util.Log
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.utils.PersistentStorageBase
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream


class DevLoggerSettings(val ctx: Context) {

    var enabled
        get() = Settings(ctx).shouldKeepLogs
        set(value) {
            // ignored
        }
}

interface LoggerStorageInterface {
    fun addMessage(severity: Int, tag: String, message: String)
    fun getMessages(): String
    fun clear()
}

class DevLoggerDB(val context: Context):
        SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_CURRENT_VERSION),
        Closeable,
        LoggerStorageInterface {

    override fun onCreate(db: SQLiteDatabase) {

        val LOG_TAG = "DevLoggerDB"

        val CREATE_PKG_TABLE =
                "CREATE " +
                        "TABLE $TABLE_NAME " +
                        "( " +
                        "$KEY_TIME INTEGER, " +
                        "$KEY_SEVERITY INTEGER, " +
                        "$KEY_TAG TEXT, " +
                        "$KEY_MESSAGE TEXT " +
                        " )"

        Log.d(LOG_TAG, "Creating DB TABLE using query: " + CREATE_PKG_TABLE)

        db.execSQL(CREATE_PKG_TABLE)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {

        if (oldVersion != newVersion) {
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
            onCreate(db)
        }
    }

    override fun addMessage(severity: Int, tag: String, message: String) {

        writableDatabase.use {
            db ->

            val values = ContentValues();

            values.put(KEY_TIME, System.currentTimeMillis())
            values.put(KEY_SEVERITY, severity)
            values.put(KEY_TAG, tag)
            values.put(KEY_MESSAGE, message);

            db.insert(TABLE_NAME, // table
                    null, // nullColumnHack
                    values) // key/value -> keys = column names/ values = column
        }
    }

    override fun getMessages(): String {

        val ret = mutableListOf<String>()

        readableDatabase.use {
            db->

            val cursor = db.query(TABLE_NAME, // a. table
                    SELECTION_COLUMNS, // b. column names
                    null, // c. selections
                    null,
                    null, // e. group by
                    null, // f. h aving
                    null, // g. order by
                    null) // h. limit

            if (cursor.moveToFirst()) {

                do {
                    ret.add(cursorToLogLine(cursor))

                } while (cursor.moveToNext())
            }
            cursor.close()
        }

        return ret.joinToString("\n")
    }

    override fun clear() {
        writableDatabase.use { db ->
            db.delete(TABLE_NAME, null, null)
        }
    }

    private fun cursorToLogLine(cursor: Cursor): String {

        val time = cursor.getLong(PROJECTION_KEY_TIME)
        val sev = cursor.getInt(PROJECTION_KEY_SEVERITY)
        val tag = cursor.getString(PROJECTION_KEY_TAG)
        val msg = cursor.getString(PROJECTION_KEY_MESSAGE)

        val builder = StringBuffer(msg.length + tag.length + 128)

        builder.append(time)
                .append(" / ")

        val usec = time % 1000L
        val sec = (time / 1000L) % 60L
        val min = (time /  60000L) % 60L
        val hr = (time / 3600000L) % 24L

        builder.append(hr).append(':')
                .append(min).append(':')
                .append(sec).append('.')
                .append(usec)

        when (sev) {
            SEVERITY_ERROR ->
                builder.append(": ERROR: ")
            SEVERITY_WARNING ->
                builder.append(": WARNING: ")
            SEVERITY_INFO ->
                builder.append(": INFO: ")
            SEVERITY_DEBUG ->
                builder.append(": DEBUG: ")
            else ->
                builder.append(": ")
        }

        builder.append(tag)
                .append(": ")
                .append(msg)

        return builder.toString()
    }


    companion object {
        const val DATABASE_NAME = "devlogV3"
        const val TABLE_NAME = "messages"
        const val DATABASE_CURRENT_VERSION = 3

        const val KEY_TIME = "time"
        const val KEY_SEVERITY = "sev"
        const val KEY_TAG = "tag"
        const val KEY_MESSAGE = "msg"

        val SELECTION_COLUMNS = arrayOf<String>(
                KEY_TIME,
                KEY_SEVERITY,
                KEY_TAG,
                KEY_MESSAGE
        )

        const val PROJECTION_KEY_TIME = 0
        const val PROJECTION_KEY_SEVERITY = 1
        const val PROJECTION_KEY_TAG = 2
        const val PROJECTION_KEY_MESSAGE = 3

        const val SEVERITY_ERROR = 0
        const val SEVERITY_WARNING = 1
        const val SEVERITY_INFO = 2
        const val SEVERITY_DEBUG = 3
    }
}

object DevLog {

    var enabled: Boolean? = null

    private fun getIsEnabled(context: Context?): Boolean {

        if (enabled == null) {
            enabled = context != null && DevLoggerSettings(context).enabled
        }

        return enabled ?: false
    }

    fun refreshIsEnabled(context: Context) {
        enabled = DevLoggerSettings(context).enabled
    }

    private fun logToSqlite(context: Context?, severity: Int, tag: String, message: String) {

        if (context != null)
            DevLoggerDB(context).use {
                it.addMessage(severity, tag, message)
            }
    }

    fun error(context: Context?, tag: String, message: String) {

        if (getIsEnabled(context)) {
            logToSqlite(context, DevLoggerDB.SEVERITY_ERROR, tag, message)
        }

        Log.e(tag, message)
    }

    fun warn(context: Context?, tag: String, message: String) {

        if (getIsEnabled(context)) {
            logToSqlite(context, DevLoggerDB.SEVERITY_WARNING, tag, message)
        }

        Log.w(tag, message)
    }

    fun info(context: Context?, tag: String, message: String) {

        if (getIsEnabled(context)) {
            logToSqlite(context, DevLoggerDB.SEVERITY_INFO, tag, message)
        }

        Log.i(tag, message)
    }

    fun debug(tag: String, message: String) = Log.d(tag, message)

    fun debug(context: Context?, tag: String, message: String) = debug(tag, message)

    fun clear(context: Context) = DevLoggerDB(context).use { it.clear() }

    fun getMessages(context: Context) = DevLoggerDB(context).use { it.getMessages() }
}
