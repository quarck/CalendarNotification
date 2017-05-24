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

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.calendar.EventDisplayStatus
import com.github.quarck.calnotify.logs.Logger
import java.io.Closeable

class DismissedEventsStorage(context: Context)
: SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_CURRENT_VERSION), Closeable, DismissedEventsStorageInterface {

    private lateinit var impl: DismissedEventsStorageImplInterface

    init  {
        when (DATABASE_CURRENT_VERSION) {
            DATABASE_VERSION_V1 ->
                impl = DismissedEventsStorageImplV1();

            else ->
                throw NotImplementedError("DB Version $DATABASE_CURRENT_VERSION is not supported")
        }
    }

    override fun onCreate(db: SQLiteDatabase)
        = impl.createDb(db)

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        logger.debug("onUpgrade $oldVersion -> $newVersion")

        if (oldVersion != newVersion) {
            throw Exception("DB storage error: upgrade from $oldVersion to $newVersion is not supported")
        }
    }

    override fun addEvent(type: EventDismissType, event: EventAlertRecord)
        = addEvent(type, System.currentTimeMillis(), event)

    override fun addEvent(type: EventDismissType, changeTime: Long, event: EventAlertRecord)
        = synchronized (DismissedEventsStorage::class.java) { writableDatabase.use { impl.addEventImpl(it, type, changeTime, event) } }

    override fun addEvents(type: EventDismissType, events: Collection<EventAlertRecord>)
        = synchronized (DismissedEventsStorage::class.java) { writableDatabase.use { impl.addEventsImpl(it, type, System.currentTimeMillis(), events) } }

    override fun deleteEvent(entry: DismissedEventAlertRecord)
        = synchronized (DismissedEventsStorage::class.java) { writableDatabase.use { impl.deleteEventImpl(it, entry) } }

    override fun deleteEvent(event: EventAlertRecord)
        = synchronized (DismissedEventsStorage::class.java) { writableDatabase.use { impl.deleteEventImpl(it, event) } }

    override fun clearHistory()
        = synchronized (DismissedEventsStorage::class.java) { writableDatabase.use { impl.clearHistoryImpl(it) } }

    override val events: List<DismissedEventAlertRecord>
        get() = synchronized(DismissedEventsStorage::class.java) { readableDatabase.use { impl.getEventsImpl(it) } }

    override fun purgeOld(currentTime: Long, maxLiveTime: Long)
        = events.filter { (currentTime - it.dismissTime) > maxLiveTime }.forEach { deleteEvent(it) }

    override fun close() = super.close()

    companion object {
        private val logger = Logger("DismissedEventsStorage")

        private const val DATABASE_VERSION_V1 = 1
        private const val DATABASE_CURRENT_VERSION = DATABASE_VERSION_V1

        private const val DATABASE_NAME = "DismissedEvents"
    }
}
