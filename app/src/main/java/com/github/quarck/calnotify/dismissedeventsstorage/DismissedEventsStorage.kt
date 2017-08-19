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
import com.github.quarck.calnotify.logs.DevLog
//import com.github.quarck.calnotify.logs.Logger
import java.io.Closeable

class DismissedEventsStorage(val context: Context)
    : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_CURRENT_VERSION), Closeable, DismissedEventsStorageInterface {

    private var impl: DismissedEventsStorageImplInterface

    init {
        impl = DismissedEventsStorageImplV2();
    }

    override fun onCreate(db: SQLiteDatabase)
            = impl.createDb(db)

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {

        DevLog.info(context, LOG_TAG, "onUpgrade $oldVersion -> $newVersion")

        if (oldVersion == newVersion)
            return

        if (newVersion != DATABASE_VERSION_V2)
            throw Exception("DB storage error: upgrade from $oldVersion to $newVersion is not supported")

        val implOld =
                when (oldVersion) {
                    DATABASE_VERSION_V1 -> DismissedEventsStorageImplV1()
                    else -> throw Exception("DB storage error: upgrade from $oldVersion to $newVersion is not supported")
                }

        try {
            impl.createDb(db)

            val events = implOld.getEventsImpl(db)

            DevLog.info(context, LOG_TAG, "${events.size} requests to convert")

            for ((event, time, type) in events) {
                impl.addEventImpl(db, type, time, event)
                implOld.deleteEventImpl(db, event)

                DevLog.debug(LOG_TAG, "Done event ${event.eventId}, inst ${event.instanceStartTime}")
            }

            if (implOld.getEventsImpl(db).isEmpty()) {
                DevLog.info(context, LOG_TAG, "Finally - dropping old tables")
                implOld.dropAll(db)
            }
            else {
                throw Exception("DB Upgrade failed: some requests are still in the old version of DB")
            }

        }
        catch (ex: Exception) {
            DevLog.error(context, LOG_TAG, "Exception during DB upgrade $oldVersion -> $newVersion: ${ex.message}, ${ex.stackTrace}")
            throw ex
        }
    }

    override fun addEvent(type: EventDismissType, event: EventAlertRecord)
            = addEvent(type, System.currentTimeMillis(), event)

    override fun addEvent(type: EventDismissType, changeTime: Long, event: EventAlertRecord)
            = synchronized(DismissedEventsStorage::class.java) { writableDatabase.use { impl.addEventImpl(it, type, changeTime, event) } }

    override fun addEvents(type: EventDismissType, events: Collection<EventAlertRecord>)
            = synchronized(DismissedEventsStorage::class.java) { writableDatabase.use { impl.addEventsImpl(it, type, System.currentTimeMillis(), events) } }

    override fun deleteEvent(entry: DismissedEventAlertRecord)
            = synchronized(DismissedEventsStorage::class.java) { writableDatabase.use { impl.deleteEventImpl(it, entry) } }

    override fun deleteEvent(event: EventAlertRecord)
            = synchronized(DismissedEventsStorage::class.java) { writableDatabase.use { impl.deleteEventImpl(it, event) } }

    override fun clearHistory()
            = synchronized(DismissedEventsStorage::class.java) { writableDatabase.use { impl.clearHistoryImpl(it) } }

    override val events: List<DismissedEventAlertRecord>
        get() = synchronized(DismissedEventsStorage::class.java) { readableDatabase.use { impl.getEventsImpl(it) } }

    override fun purgeOld(currentTime: Long, maxLiveTime: Long)
            = events.filter { (currentTime - it.dismissTime) > maxLiveTime }.forEach { deleteEvent(it) }

    override fun close() = super.close()

    companion object {
        private val LOG_TAG = "DismissedEventsStorage"

        private const val DATABASE_VERSION_V1 = 1
        private const val DATABASE_VERSION_V2 = 2
        private const val DATABASE_CURRENT_VERSION = DATABASE_VERSION_V2

        private const val DATABASE_NAME = "DismissedEvents"
    }
}
