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
import com.github.quarck.calnotify.calendar.MonitorEventAlertEntry
import com.github.quarck.calnotify.logs.DevLog
//import com.github.quarck.calnotify.logs.Logger
import java.io.Closeable


class MonitorStorage(val context: Context)
    : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_CURRENT_VERSION), Closeable, MonitorStorageInterface {

    private var impl: MonitorStorageImplInterface

    init {
        impl = MonitorStorageImplV1(context);
    }

    override fun onCreate(db: SQLiteDatabase)
            = impl.createDb(db)

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        DevLog.info(context, LOG_TAG, "onUpgrade $oldVersion -> $newVersion")

        if (oldVersion != newVersion) {
            throw Exception("DB storage error: upgrade from $oldVersion to $newVersion is not supported")
        }
    }

    override fun addAlert(entry: MonitorEventAlertEntry)
            = synchronized(MonitorStorage::class.java) { writableDatabase.use { impl.addAlert(it, entry) } }

    override fun addAlerts(entries: Collection<MonitorEventAlertEntry>)
            = synchronized(MonitorStorage::class.java) { writableDatabase.use { impl.addAlerts(it, entries) } }

    override fun deleteAlert(entry: MonitorEventAlertEntry)
            = deleteAlert(entry.eventId, entry.alertTime, entry.instanceStartTime)

    override fun deleteAlerts(entries: Collection<MonitorEventAlertEntry>)
            = synchronized(MonitorStorage::class.java) { writableDatabase.use { impl.deleteAlerts(it, entries) } }

    override fun deleteAlert(eventId: Long, alertTime: Long, instanceStart: Long)
            = synchronized(MonitorStorage::class.java) { writableDatabase.use { impl.deleteAlert(it, eventId, alertTime, instanceStart) } }

    override fun deleteAlertsMatching(filter: (MonitorEventAlertEntry) -> Boolean)
            = synchronized(MonitorStorage::class.java) { writableDatabase.use { impl.deleteAlertsMatching(it, filter) } }

    override fun updateAlert(entry: MonitorEventAlertEntry)
            = synchronized(MonitorStorage::class.java) { writableDatabase.use { impl.updateAlert(it, entry) } }

    override fun updateAlerts(entries: Collection<MonitorEventAlertEntry>)
            = synchronized(MonitorStorage::class.java) { writableDatabase.use { impl.updateAlerts(it, entries) } }

    override fun getAlert(eventId: Long, alertTime: Long, instanceStart: Long): MonitorEventAlertEntry?
            = synchronized(MonitorStorage::class.java) { readableDatabase.use { impl.getAlert(it, eventId, alertTime, instanceStart) } }

    override fun getInstanceAlerts(eventId: Long, instanceStart: Long): List<MonitorEventAlertEntry>
            = synchronized(MonitorStorage::class.java) { readableDatabase.use { impl.getInstanceAlerts(it, eventId, instanceStart) } }

    override fun getNextAlert(since: Long): Long?
            = synchronized(MonitorStorage::class.java) { readableDatabase.use { impl.getNextAlert(it, since) } }

    override fun getAlertsAt(time: Long): List<MonitorEventAlertEntry>
            = synchronized(MonitorStorage::class.java) { readableDatabase.use { impl.getAlertsAt(it, time) } }

    override val alerts: List<MonitorEventAlertEntry>
        get() = synchronized(MonitorStorage::class.java) { readableDatabase.use { impl.getAlerts(it) } }

    override fun getAlertsForInstanceStartRange(scanFrom: Long, scanTo: Long): List<MonitorEventAlertEntry>
            = synchronized(MonitorStorage::class.java) { readableDatabase.use { impl.getAlertsForInstanceStartRange(it, scanFrom, scanTo) } }

    override fun getAlertsForAlertRange(scanFrom: Long, scanTo: Long): List<MonitorEventAlertEntry>
            = synchronized(MonitorStorage::class.java) { readableDatabase.use { impl.getAlertsForAlertRange(it, scanFrom, scanTo) } }

    companion object {
        private const val LOG_TAG = "MonitorStorage"

        private const val DATABASE_VERSION_V1 = 1
        private const val DATABASE_CURRENT_VERSION = DATABASE_VERSION_V1

        private const val DATABASE_NAME = "CalendarMonitor"
    }
}