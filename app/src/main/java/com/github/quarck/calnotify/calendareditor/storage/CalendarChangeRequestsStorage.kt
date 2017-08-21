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

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.github.quarck.calnotify.calendareditor.CalendarChangeRequest
import com.github.quarck.calnotify.logs.DevLog
import java.io.Closeable

class CalendarChangeRequestsStorage(val context: Context)
    : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_CURRENT_VERSION)
        , Closeable
        , CalendarChangeRequestsStorageInterface {

    private var impl: CalendarChangeRequestsStorageImplInterface

    init {
        impl = CalendarChangeRequestsStorageImplV2();
    }

    override fun onCreate(db: SQLiteDatabase)
            = impl.createDb(db)

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {

        DevLog.info(context, LOG_TAG, "onUpgrade $oldVersion -> $newVersion")

        if (oldVersion == newVersion)
            return

        if (newVersion != DATABASE_VERSION_V2)
            throw Exception("DB storage error: upgrade from $oldVersion to $newVersion is not supported")

        impl.dropAll(db)
        impl.createDb(db)
    }

    override fun add(req: CalendarChangeRequest)
            = synchronized(CalendarChangeRequestsStorage::class.java) { writableDatabase.use { impl.addImpl(it, req) } }

    override fun deleteForEventId(eventId: Long)
            = synchronized(CalendarChangeRequestsStorage::class.java) { writableDatabase.use { impl.deleteForEventIdImpl(it, eventId) } }

    override fun delete(req: CalendarChangeRequest)
            = synchronized(CalendarChangeRequestsStorage::class.java) { writableDatabase.use { impl.deleteImpl(it, req) } }

    override fun deleteMany(requests: List<CalendarChangeRequest>)
            = synchronized(CalendarChangeRequestsStorage::class.java) { writableDatabase.use { impl.deleteManyImpl(it, requests) } }

    override fun update(req: CalendarChangeRequest)
            = synchronized(CalendarChangeRequestsStorage::class.java) { writableDatabase.use { impl.updateImpl(it, req) } }

    override fun updateMany(requests: List<CalendarChangeRequest>)
            = synchronized(CalendarChangeRequestsStorage::class.java) { writableDatabase.use { impl.updateManyImpl(it, requests) } }


    override val requests: List<CalendarChangeRequest>
        get() = synchronized(CalendarChangeRequestsStorage::class.java) { readableDatabase.use { impl.getImpl(it) } }

    override fun close() = super.close()

    companion object {
        private val LOG_TAG = "CalendarChangeRequestsStorage"

        private const val DATABASE_VERSION_V2 = 2
        private const val DATABASE_CURRENT_VERSION = DATABASE_VERSION_V2

        private const val DATABASE_NAME = "calEditReqs"
    }
}