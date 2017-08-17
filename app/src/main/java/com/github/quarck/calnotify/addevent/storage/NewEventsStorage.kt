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

package com.github.quarck.calnotify.addevent.storage

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.github.quarck.calnotify.logs.DevLog
import java.io.Closeable

class NewEventsStorage(val context: Context)
    : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_CURRENT_VERSION)
        , Closeable
        , NewEventsStorageInterface {

    private var impl: NewEventsStorageImplInterface

    init {
        impl = NewEventsStorageImplV1();
    }

    override fun onCreate(db: SQLiteDatabase)
            = impl.createDb(db)

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {

        DevLog.info(context, LOG_TAG, "onUpgrade $oldVersion -> $newVersion")

        if (oldVersion == newVersion)
            return

        if (newVersion != DATABASE_VERSION_V1)
            throw Exception("DB storage error: upgrade from $oldVersion to $newVersion is not supported")

//        val implOld =
//                when (oldVersion) {
//                    DATABASE_VERSION_V0 -> NewEventsStorageImplV0()
//                    else -> throw Exception("DB storage error: upgrade from $oldVersion to $newVersion is not supported")
//                }
//
//        try {
//            impl.createDb(db)
//
//            val events = implOld.getEventsImpl(db)
//
//            DevLog.info(context, LOG_TAG, "${events.size} events to convert")
//
//            for ((event, time, type) in events) {
//                impl.addEventImpl(db, type, time, event)
//                implOld.deleteEventImpl(db, event)
//
//                DevLog.debug(LOG_TAG, "Done event ${event.eventId}, inst ${event.instanceStartTime}")
//            }
//
//            if (implOld.getEventsImpl(db).isEmpty()) {
//                DevLog.info(context, LOG_TAG, "Finally - dropping old tables")
//                implOld.dropAll(db)
//            }
//            else {
//                throw Exception("DB Upgrade failed: some events are still in the old version of DB")
//            }
//
//        }
//        catch (ex: Exception) {
//            DevLog.error(context, LOG_TAG, "Exception during DB upgrade $oldVersion -> $newVersion: ${ex.message}, ${ex.stackTrace}")
//            throw ex
//        }
    }

    override fun addEvent(event: NewEventRecord)
            = synchronized(NewEventsStorage::class.java) { writableDatabase.use { impl.addEventImpl(it, event) } }

    override fun deleteEvent(event: NewEventRecord)
            = synchronized(NewEventsStorage::class.java) { writableDatabase.use { impl.deleteEventImpl(it, event) } }

    override fun deleteEvents(events: List<NewEventRecord>)
            = synchronized(NewEventsStorage::class.java) { writableDatabase.use { impl.deleteEventsImpl(it, events) } }

    override fun updateEvent(event: NewEventRecord)
            = synchronized(NewEventsStorage::class.java) { writableDatabase.use { impl.updateEventImpl(it, event) } }

    override fun updateEvents(events: List<NewEventRecord>)
            = synchronized(NewEventsStorage::class.java) { writableDatabase.use { impl.updateEventsImpl(it, events) } }


    override val events: List<NewEventRecord>
        get() = synchronized(NewEventsStorage::class.java) { readableDatabase.use { impl.getEventsImpl(it) } }

    override fun close() = super.close()

    companion object {
        private val LOG_TAG = "NewEventsStorage"

        private const val DATABASE_VERSION_V1 = 1
        private const val DATABASE_CURRENT_VERSION = DATABASE_VERSION_V1

        private const val DATABASE_NAME = "NewEvents"
    }
}