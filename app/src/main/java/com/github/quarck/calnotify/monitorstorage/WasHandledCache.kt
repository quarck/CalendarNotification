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

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.logs.DevLog
//import com.github.quarck.calnotify.logs.Logger
import java.io.Closeable


class WasHandledCache(val context: Context)
    : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_CURRENT_VERSION), Closeable, WasHandledCacheInterface {

    private var impl: WasHandledCacheImplInterface

    init {
        impl = WasHandledCacheImplV1(context);
    }

    override fun onCreate(db: SQLiteDatabase)
            = impl.createDb(db)

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        DevLog.info(LOG_TAG, "onUpgrade $oldVersion -> $newVersion")

        if (oldVersion != newVersion) {
            throw Exception("DB storage error: upgrade from $oldVersion to $newVersion is not supported")
        }
    }

    override fun addHandledAlert(entry: EventAlertRecord)
            = synchronized(WasHandledCache::class.java) { writableDatabase.use { impl.addHandledAlert(it, entry) } }

    override fun addHandledAlerts(entries: Collection<EventAlertRecord>)
            = synchronized(WasHandledCache::class.java) { writableDatabase.use { impl.addHandledAlerts(it, entries) } }

    override fun getAlertWasHandled(entry: EventAlertRecord): Boolean
            = synchronized(WasHandledCache::class.java) { readableDatabase.use { impl.getAlertWasHandled(it, entry) } }

    override fun getAlertsWereHandled(entries: Collection<EventAlertRecord>): BooleanArray
            = synchronized(WasHandledCache::class.java) { readableDatabase.use { impl.getAlertsWereHandled(it, entries) } }

    override fun removeOldEntries(minAge: Long): Int
            = synchronized(WasHandledCache::class.java) { writableDatabase.use { impl.removeOldEntries(it, minAge) } }

    companion object {
        private const val LOG_TAG = "WasHandledCache"

        private const val DATABASE_VERSION_V1 = 1
        private const val DATABASE_CURRENT_VERSION = DATABASE_VERSION_V1

        private const val DATABASE_NAME = "WasHandledCache"
    }
}