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

import com.github.quarck.calnotify.calendar.MonitorEventAlertEntry

interface MonitorStorageInterface {

    fun addAlert(entry: MonitorEventAlertEntry)
    fun addAlerts(entries: Collection<MonitorEventAlertEntry>)

    fun getAlert(eventId: Long, alertTime: Long, instanceStart: Long): MonitorEventAlertEntry?

    fun getInstanceAlerts(eventId: Long, instanceStart: Long): List<MonitorEventAlertEntry>

    fun deleteAlert(entry: MonitorEventAlertEntry)
    fun deleteAlerts(entries: Collection<MonitorEventAlertEntry>)
    fun deleteAlert(eventId: Long, alertTime: Long, instanceStart: Long)

    fun deleteAlertsMatching(filter: (MonitorEventAlertEntry) -> Boolean)

    fun updateAlert(entry: MonitorEventAlertEntry)
    fun updateAlerts(entries: Collection<MonitorEventAlertEntry>)

    fun getNextAlert(since: Long): Long?
    fun getAlertsAt(time: Long): List<MonitorEventAlertEntry>

    val alerts: List<MonitorEventAlertEntry> get

    fun getAlertsForInstanceStartRange(scanFrom: Long, scanTo: Long): List<MonitorEventAlertEntry>

    fun getAlertsForAlertRange(scanFrom: Long, scanTo: Long): List<MonitorEventAlertEntry>
}