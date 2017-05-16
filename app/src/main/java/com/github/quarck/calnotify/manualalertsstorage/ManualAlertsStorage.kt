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


package com.github.quarck.calnotify.manualalertsstorage

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.github.quarck.calnotify.calendar.ManualEventAlertEntry
import com.github.quarck.calnotify.logs.Logger
import java.io.Closeable


class ManualAlertsStorage(val context: Context)
    : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_CURRENT_VERSION), Closeable,  ManualAlertsStorageInterface {

    private var impl: ManualAlertsStorageImplInterface

    init  {
        when (DATABASE_CURRENT_VERSION) {
            DATABASE_VERSION_V1 ->
                impl = ManualAlertsStorageImplV1();

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

    override fun addAlert(entry: ManualEventAlertEntry)
        = synchronized (ManualAlertsStorage::class.java) { writableDatabase.use { impl.addAlert(it, entry) } }

    override fun addAlerts(entries: List<ManualEventAlertEntry>)
        = synchronized (ManualAlertsStorage::class.java) { writableDatabase.use { impl.addAlerts(it, entries) } }

    override fun deleteAlert(entry: ManualEventAlertEntry)
        = deleteAlert(entry.eventId, entry.alertTime, entry.instanceStartTime)

    override fun deleteAlert(eventId: Long, alertTime: Long, instanceStart: Long)
        = synchronized (ManualAlertsStorage::class.java) { writableDatabase.use { impl.deleteAlert(it, eventId, alertTime, instanceStart) } }

    override fun deleteAlertsForEventsOlderThan(time: Long)
        = synchronized (ManualAlertsStorage::class.java) { writableDatabase.use { impl.deleteAlertsForEventsOlderThan(it, time) } }

    override fun updateAlert(entry: ManualEventAlertEntry)
        = synchronized (ManualAlertsStorage::class.java) { writableDatabase.use { impl.updateAlert(it, entry) } }

    override fun updateAlerts(entries: List<ManualEventAlertEntry>)
        = synchronized (ManualAlertsStorage::class.java) { writableDatabase.use { impl.updateAlerts(it, entries) } }

    override fun getAlert(eventId: Long, alertTime: Long, instanceStart: Long): ManualEventAlertEntry?
        = synchronized(ManualAlertsStorage::class.java) { readableDatabase.use { impl.getAlert(it, eventId, alertTime, instanceStart) } }

    override fun getNextAlert(since: Long): Long?
        = synchronized(ManualAlertsStorage::class.java) { readableDatabase.use { impl.getNextAlert(it, since) } }

    override fun getAlertsAt(time: Long): List<ManualEventAlertEntry>
        = synchronized(ManualAlertsStorage::class.java) { readableDatabase.use { impl.getAlertsAt(it, time) } }

    override val alerts: List<ManualEventAlertEntry>
        get() = synchronized(ManualAlertsStorage::class.java) { readableDatabase.use { impl.getAlerts(it) } }

    companion object {
        private val logger = Logger("ManualAlertsStorage")

        private const val DATABASE_VERSION_V1 = 1
        private const val DATABASE_CURRENT_VERSION = DATABASE_VERSION_V1

        private const val DATABASE_NAME = "ManualAlerts"
    }
}