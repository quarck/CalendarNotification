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

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.github.quarck.calnotify.logs.Logger
import java.io.Closeable

class EventsStorage(context: Context)
: SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_CURRENT_VERSION), Closeable {

    private lateinit var impl: EventsStorageImplInterface

    init  {
        when (DATABASE_CURRENT_VERSION) {
            DATABASE_VERSION_V6 ->
                impl = EventsStorageImplV6();

            DATABASE_VERSION_V7 ->
                impl = EventsStorageImplV7();

            else ->
                throw NotImplementedError("DB Version $DATABASE_CURRENT_VERSION is not supported")
        }
    }

    override fun onCreate(db: SQLiteDatabase)
        = impl.createDb(db)

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        logger.debug("onUpgrade $oldVersion -> $newVersion")

        if (oldVersion != newVersion) {
            if (oldVersion < DATABASE_VERSION_V6) {

                logger.debug("Version too old - dropping everything");
                EventsStorageImplV6().dropAll(db)
                impl.createDb(db)

            } else if (oldVersion == DATABASE_VERSION_V6 && newVersion == DATABASE_VERSION_V7){

                logger.debug("V6 to V7 upgrade")

                try {
                    impl.createDb(db)

                    val implv6 = EventsStorageImplV6()
                    val events = implv6.getEventsImpl(db)

                    logger.debug("${events.size} events to convert")

                    for (event in events) {
                        impl.addEventImpl(db, event)
                        implv6.deleteEventImpl(db, event.eventId, event.instanceStartTime)

                        logger.debug("Done event ${event.eventId}, inst ${event.instanceStartTime}")
                    }

                    if (implv6.getEventsImpl(db).isEmpty()) {
                        logger.debug("Finally - dropping old tables")
                        implv6.dropAll(db)
                    } else {
                        throw Exception("DB Upgrade failed: some events are still in the old version of DB")
                    }

                } catch (ex: Exception) {
                    logger.error("Exception during DB upgrade $oldVersion -> $newVersion: ${ex.message}, ${ex.stackTrace}")
                    throw ex
                }

            } else {
                throw Exception("DB storage error: upgrade from $oldVersion to $newVersion is not supported")
            }
        }
    }


    fun addEvent(event: EventInstanceRecord)
        = synchronized (EventsStorage::class.java) { impl.addEventImpl(writableDatabase, event) }

    fun updateEvent(event: EventInstanceRecord,
                    alertTime: Long? = null,
                    title: String? = null,
                    snoozedUntil: Long? = null,
                    startTime: Long? = null,
                    endTime: Long? = null,
                    instanceStartTime: Long? = null,
                    instanceEndTime: Long? = null,
                    location: String? = null,
                    lastEventVisibility: Long? = null,
                    displayStatus: EventDisplayStatus? = null,
                    color: Int? = null
    ) {
        val newEvent =
            event.copy(
                alertTime = alertTime ?: event.alertTime,
                title = title ?: event.title,
                snoozedUntil = snoozedUntil ?: event.snoozedUntil,
                startTime = startTime ?: event.startTime,
                endTime = endTime ?: event.endTime,
                instanceStartTime = instanceStartTime ?: event.instanceStartTime,
                instanceEndTime = instanceEndTime ?: event.instanceEndTime,
                location = location ?: event.location,
                lastEventVisibility = lastEventVisibility ?: event.lastEventVisibility,
                displayStatus = displayStatus ?: event.displayStatus,
                color = color ?: event.color
            );

        updateEvent(newEvent)
    }

    @Suppress("unused")
    fun updateEvents(events: List<EventInstanceRecord>,
                     alertTime: Long? = null,
                     title: String? = null,
                     snoozedUntil: Long? = null,
                     startTime: Long? = null,
                     endTime: Long? = null,
                     instanceStartTime: Long? = null,
                     instanceEndTime: Long? = null,
                     location: String? = null,
                     lastEventVisibility: Long? = null,
                     displayStatus: EventDisplayStatus? = null,
                     color: Int? = null) {

        val newEvents =
            events.map {
                event ->
                event.copy(
                    alertTime = alertTime ?: event.alertTime,
                    title = title ?: event.title,
                    snoozedUntil = snoozedUntil ?: event.snoozedUntil,
                    startTime = startTime ?: event.startTime,
                    endTime = endTime ?: event.endTime,
                    instanceStartTime = instanceStartTime ?: event.instanceStartTime,
                    instanceEndTime = instanceEndTime ?: event.instanceEndTime,
                    location = location ?: event.location,
                    lastEventVisibility = lastEventVisibility ?: event.lastEventVisibility,
                    displayStatus = displayStatus ?: event.displayStatus,
                    color = color ?: event.color )
                }

        updateEvents(newEvents)
    }

    fun updateEvent(event: EventInstanceRecord)
        = synchronized(EventsStorage::class.java) {  writableDatabase.use { impl.updateEventImpl(it, event) } }

    fun updateEvents(events: List<EventInstanceRecord>)
        = synchronized(EventsStorage::class.java) { writableDatabase.use { impl.updateEventsImpl(it, events) } }

    fun getEvent(eventId: Long, instanceStartTime: Long): EventInstanceRecord?
        = synchronized(EventsStorage::class.java) { readableDatabase.use { impl.getEventImpl(it, eventId, instanceStartTime) } }

    fun deleteEvent(eventId: Long, instanceStartTime: Long)
        = synchronized(EventsStorage::class.java) { writableDatabase.use { impl.deleteEventImpl(it, eventId, instanceStartTime) } }

    @Suppress("unused")
    fun deleteEvent(ev: EventInstanceRecord)
        = synchronized(EventsStorage::class.java) { writableDatabase.use { impl.deleteEventImpl(it, ev.eventId, ev.instanceStartTime) } }

    val events: List<EventInstanceRecord>
        get() = synchronized(EventsStorage::class.java) { readableDatabase.use { impl.getEventsImpl(it) } }

    @Suppress("unused")
    fun getActiveEvents(currentTime: Long, threshold: Long): List<EventInstanceRecord>
        = synchronized(EventsStorage::class.java) { readableDatabase.use { impl.getActiveEventsImpl(it, currentTime, threshold) } }

    override fun close() {
        super.close();
    }

    companion object {
        private val logger = Logger("EventsStorage")

        private const val DATABASE_VERSION_V6 = 6
        private const val DATABASE_VERSION_V7 = 7
        private const val DATABASE_CURRENT_VERSION = DATABASE_VERSION_V7

        private const val DATABASE_NAME = "Events"
    }
}
