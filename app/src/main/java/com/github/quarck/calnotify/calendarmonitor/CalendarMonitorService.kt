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

import android.app.IntentService
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.utils.powerManager
import com.github.quarck.calnotify.utils.wakeLocked

class CalendarMonitorService : IntentService("CalendarMonitor") {

    override fun onHandleIntent(intent: Intent?) {

        if (intent == null) {
            DevLog.error(this, LOG_TAG, "Intent is null")
            return
        }

        val startDelay = intent?.getIntExtra(START_DELAY, 0)

        val shouldReloadCalendar = intent?.getBooleanExtra(RELOAD_CALENDAR, false)
        val shouldRescanMonitor = intent?.getBooleanExtra(RESCAN_MONITOR, true)

        val maxCalendarReloadTime = intent?.getLongExtra(MAX_RELOAD_TIME, Consts.MAX_CAL_RELOAD_TIME_DEFAULT)

        DevLog.error(this, LOG_TAG,
                "onHandleIntent: " +
                "startDelay=$startDelay, " +
                "shouldReloadCalendar=$shouldReloadCalendar, " +
                "maxCalendarReloadTime=$maxCalendarReloadTime, " +
                "shouldRescanMonitor=$shouldRescanMonitor, "
        )

        wakeLocked(powerManager, PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_NAME) {

            if (startDelay != 0) {
                try {
                    Thread.sleep(startDelay.toLong())
                }
                catch (ex: Exception) {
                }
            }

            if (shouldReloadCalendar) {
                try  {
                    ApplicationController.onCalendarReloadFromService(this, maxCalendarReloadTime)
                }
                catch (ex: Exception) {
                    DevLog.error(this, LOG_TAG, "Exception while reloading calendar: $ex, ${ex.message}, ${ex.stackTrace}")
                }
            }

            if (shouldRescanMonitor) {
                try {
                    ApplicationController.CalendarMonitor.onRescanFromService(this, intent)
                }
                catch (ex: Exception) {
                    DevLog.error(this, LOG_TAG, "Exception while rescannning calendar: $ex, ${ex.message}, ${ex.stackTrace}")
                }
            }
        }
    }

    companion object {
        private const val LOG_TAG = "CalendarMonitorSvc"

        private const val WAKE_LOCK_NAME = "CalendarMonitor"
        private const val START_DELAY = "start_delay"
        private const val RELOAD_CALENDAR = "reload_calendar"
        private const val RESCAN_MONITOR = "rescan_monitor"
        private const val MAX_RELOAD_TIME = "max_reload_time"

        fun startRescanService(
                context: Context,
                startDelay: Int = 0,
                reloadCalendar: Boolean = false, // should reload existing reminders
                rescanMonitor: Boolean = true,   // should perform calendar monitor rescan
                maxReloadCalendarTime: Long = Consts.MAX_CAL_RELOAD_TIME_DEFAULT // only affects reload, not rescan
        ) {
            val intent = Intent(context, CalendarMonitorService::class.java)

            intent.putExtra(START_DELAY, startDelay)
            intent.putExtra(RELOAD_CALENDAR, reloadCalendar)
            intent.putExtra(RESCAN_MONITOR, rescanMonitor)
            intent.putExtra(MAX_RELOAD_TIME, maxReloadCalendarTime)

            context.startService(intent)
        }
    }
}