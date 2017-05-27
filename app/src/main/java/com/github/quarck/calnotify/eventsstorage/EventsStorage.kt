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
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.calendar.EventDisplayStatus
import com.github.quarck.calnotify.logs.Logger
import java.io.Closeable
import java.security.InvalidParameterException

class EventsStorage(context: Context)
: SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_CURRENT_VERSION), Closeable, EventsStorageInterface {

    private lateinit var impl: EventsStorageImplInterface

    init  {
        when (DATABASE_CURRENT_VERSION) {
            DATABASE_VERSION_V6 ->
                impl = EventsStorageImplV6();

            DATABASE_VERSION_V7 ->
                impl = EventsStorageImplV7();

            DATABASE_VERSION_V8 ->
                impl = EventsStorageImplV8();

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
                throw Exception("Not known version -- this was never released to public")
            }
            else if (oldVersion in DATABASE_VERSION_V6..DATABASE_VERSION_V7 &&
                    newVersion  == DATABASE_VERSION_V8 &&
                    oldVersion < newVersion) {

                logger.debug("V${oldVersion} to V${newVersion} upgrade")

                try {
                    impl.createDb(db)

                    val implOld =
                            when (oldVersion) {
                                DATABASE_VERSION_V6 -> EventsStorageImplV6()
                                DATABASE_VERSION_V7 -> EventsStorageImplV7()
                                else -> throw Exception() // placeholder - would never happen
                            }

                    val events = implOld.getEventsImpl(db)

                    logger.debug("${events.size} events to convert")

                    for (event in events) {
                        impl.addEventImpl(db, event)
                        implOld.deleteEventImpl(db, event.eventId, event.instanceStartTime)

                        logger.debug("Done event ${event.eventId}, inst ${event.instanceStartTime}")
                    }

                    if (implOld.getEventsImpl(db).isEmpty()) {
                        logger.debug("Finally - dropping old tables")
                        implOld.dropAll(db)
                    } else {
                        throw Exception("DB Upgrade failed: some events are still in the old version of DB")
                    }

                } catch (ex: Exception) {
                    logger.error("Exception during DB upgrade $oldVersion -> $newVersion: ${ex.message}, ${ex.stackTrace}")
                    throw ex
                }

            }
            else {
                throw Exception("DB storage error: upgrade from $oldVersion to $newVersion is not supported")
            }
        }
    }


    override fun addEvent(event: EventAlertRecord)
        = synchronized (EventsStorage::class.java) { writableDatabase.use { impl.addEventImpl(it, event) } }

    override fun addEvents(events: List<EventAlertRecord>)
        = synchronized (EventsStorage::class.java) { writableDatabase.use { impl.addEventsImpl(it, events) } }

    override fun updateEvent(event: EventAlertRecord,
                             alertTime: Long?,
                             title: String?,
                             snoozedUntil: Long?,
                             startTime: Long?,
                             endTime: Long?,
                             location: String?,
                             lastEventVisibility: Long?,
                             displayStatus: EventDisplayStatus?,
                             color: Int?,
                             isRepeating: Boolean?
    ): EventAlertRecord {
        val newEvent =
            event.copy(
                alertTime = alertTime ?: event.alertTime,
                title = title ?: event.title,
                snoozedUntil = snoozedUntil ?: event.snoozedUntil,
                startTime = startTime ?: event.startTime,
                endTime = endTime ?: event.endTime,
                location = location ?: event.location,
                lastEventVisibility = lastEventVisibility ?: event.lastEventVisibility,
                displayStatus = displayStatus ?: event.displayStatus,
                color = color ?: event.color,
                isRepeating = isRepeating ?: event.isRepeating
            );

        updateEvent(newEvent)

        return newEvent
    }

    @Suppress("unused")
    override fun updateEvents(events: List<EventAlertRecord>,
                              alertTime: Long?,
                              title: String?,
                              snoozedUntil: Long?,
                              startTime: Long?,
                              endTime: Long?,
                              location: String?,
                              lastEventVisibility: Long?,
                              displayStatus: EventDisplayStatus?,
                              color: Int?,
                              isRepeating: Boolean?) {

        val newEvents =
            events.map {
                event ->
                event.copy(
                    alertTime = alertTime ?: event.alertTime,
                    title = title ?: event.title,
                    snoozedUntil = snoozedUntil ?: event.snoozedUntil,
                    startTime = startTime ?: event.startTime,
                    endTime = endTime ?: event.endTime,
                    location = location ?: event.location,
                    lastEventVisibility = lastEventVisibility ?: event.lastEventVisibility,
                    displayStatus = displayStatus ?: event.displayStatus,
                    color = color ?: event.color,
                    isRepeating = isRepeating ?: event.isRepeating)
                }

        updateEvents(newEvents)
    }

    override fun updateEventAndInstanceTimes(event: EventAlertRecord, instanceStart: Long, instanceEnd: Long)
        = synchronized(EventsStorage::class.java) {  writableDatabase.use { impl.updateEventAndInstanceTimesImpl(it, event, instanceStart, instanceEnd) } }

    override fun updateEventsAndInstanceTimes(events: Collection<EventWithNewInstanceTime>)
        = synchronized(EventsStorage::class.java) {  writableDatabase.use { impl.updateEventsAndInstanceTimesImpl(it, events) } }

    override fun updateEvent(event: EventAlertRecord)
        = synchronized(EventsStorage::class.java) {  writableDatabase.use { impl.updateEventImpl(it, event) } }

    override fun updateEvents(events: List<EventAlertRecord>)
        = synchronized(EventsStorage::class.java) { writableDatabase.use { impl.updateEventsImpl(it, events) } }

    override fun getEvent(eventId: Long, instanceStartTime: Long): EventAlertRecord?
        = synchronized(EventsStorage::class.java) { readableDatabase.use { impl.getEventImpl(it, eventId, instanceStartTime) } }

    override fun getEventInstances(eventId: Long): List<EventAlertRecord>
        = synchronized(EventsStorage::class.java) { readableDatabase.use { impl.getEventInstancesImpl(it, eventId) } }

    override fun deleteEvent(eventId: Long, instanceStartTime: Long)
        = synchronized(EventsStorage::class.java) { writableDatabase.use { impl.deleteEventImpl(it, eventId, instanceStartTime) } }

    @Suppress("unused")
    override fun deleteEvent(ev: EventAlertRecord)
        = synchronized(EventsStorage::class.java) { writableDatabase.use { impl.deleteEventImpl(it, ev.eventId, ev.instanceStartTime) } }

    override fun deleteEvents(events: Collection<EventAlertRecord>)
        = synchronized(EventsStorage::class.java) { writableDatabase.use { impl.deleteEventsImpl(it, events) } }

    override val events: List<EventAlertRecord>
        get() = synchronized(EventsStorage::class.java) { readableDatabase.use { impl.getEventsImpl(it) } }

    override fun close() {
        super.close();
    }

    companion object {
        private val logger = Logger("EventsStorage")

        private const val DATABASE_VERSION_V6 = 6
        private const val DATABASE_VERSION_V7 = 7
        private const val DATABASE_VERSION_V8 = 8

        private const val DATABASE_CURRENT_VERSION = DATABASE_VERSION_V8

        private const val DATABASE_NAME = "Events"
    }
}
