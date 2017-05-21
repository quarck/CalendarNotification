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

package com.github.quarck.calnotify.calendarmonitor

import android.content.Context
import android.content.Intent
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.monitorstorage.MonitorStorage


interface CalendarMonitorInterface {

    fun onSystemTimeChange(context: Context)

    fun onAlarmBroadcast(context: Context, intent: Intent)

    fun onPeriodicRescanBroadcast(context: Context, intent: Intent)

    fun onBoot(context: Context): Boolean

    fun onAppStarted(context: Context): Boolean

    fun onUpgrade(context: Context): Boolean

    fun onProviderReminderBroadcast(context: Context, intent: Intent)

    fun onCalendarChange(context: Context)

//    fun getAlertsAt(context: Context, time: Long, mayRescan: Boolean): List<MonitorEventAlertEntry>

//    fun getAlertsAsEventAlertsAt(context: Context, time: Long, mayRescan: Boolean): List<EventAlertRecord>

    fun setAlertWasHandled(context: Context, ev: EventAlertRecord, createdByUs: Boolean)

    fun getAlertWasHandled(context: Context, ev: EventAlertRecord): Boolean

    fun getAlertWasHandled(db: MonitorStorage, ev: EventAlertRecord): Boolean
}
