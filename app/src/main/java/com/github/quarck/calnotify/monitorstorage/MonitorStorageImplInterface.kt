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

import android.database.sqlite.SQLiteDatabase
import com.github.quarck.calnotify.calendar.MonitorEventAlertEntry

interface MonitorStorageImplInterface {
    fun createDb(db: SQLiteDatabase)

    fun addAlert(db: SQLiteDatabase, entry: MonitorEventAlertEntry)
    fun addAlerts(db: SQLiteDatabase, entries: Collection<MonitorEventAlertEntry>)

    fun getAlert(db: SQLiteDatabase, eventId: Long, alertTime: Long, instanceStart: Long): MonitorEventAlertEntry?

    fun deleteAlert(db: SQLiteDatabase, eventId: Long, alertTime: Long, instanceStart: Long)
    fun deleteAlerts(db: SQLiteDatabase, entries: Collection<MonitorEventAlertEntry>)
    fun deleteHandledAlertsForInstanceStartOlderThan(db: SQLiteDatabase, instanceStartTime: Long)

    fun updateAlert(db: SQLiteDatabase, entry: MonitorEventAlertEntry)
    fun updateAlerts(db: SQLiteDatabase, entries: Collection<MonitorEventAlertEntry>)

    fun getNextAlert(db: SQLiteDatabase, since: Long): Long?
    fun getAlertsAt(db: SQLiteDatabase, time: Long): List<MonitorEventAlertEntry>

    fun getAlerts(db: SQLiteDatabase): List<MonitorEventAlertEntry>
    fun getAlertsForInstanceStartRange(db: SQLiteDatabase, scanFrom: Long, scanTo: Long): List<MonitorEventAlertEntry>
    fun getAlertsForAlertRange(db: SQLiteDatabase, scanFrom: Long, scanTo: Long): List<MonitorEventAlertEntry>
}