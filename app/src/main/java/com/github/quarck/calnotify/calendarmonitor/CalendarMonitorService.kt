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

        var startDelay = intent?.getIntExtra(START_DELAY, 0)

        val shouldReloadCalendar = intent.getBooleanExtra(RELOAD_CALENDAR, false)
        val shouldRescanMonitor = intent.getBooleanExtra(RESCAN_MONITOR, true)
        val userActionUntil = intent.getLongExtra(USER_ACTION_UNTIL, 0)

        DevLog.info(this, LOG_TAG,
                "onHandleIntent: " +
                "startDelay=$startDelay, " +
                "shouldReloadCalendar=$shouldReloadCalendar, " +
                "shouldRescanMonitor=$shouldRescanMonitor, "
        )

        wakeLocked(powerManager, PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_NAME) {

            if (shouldReloadCalendar && startDelay > MAX_TIME_WITHOUT_QUICK_RESCAN) {
                try  {
                    sleep(QUICK_RESCAN_SLEEP_BEFORE)
                    startDelay -= QUICK_RESCAN_SLEEP_BEFORE

                    ApplicationController.onCalendarRescanForRescheduledFromService(this, userActionUntil)
                }
                catch (ex: Exception) {
                    DevLog.error(this, LOG_TAG, "Exception while reloading calendar: $ex, ${ex.message}, ${ex.stackTrace}")
                }
            }

            if (startDelay != 0) {
                sleep(startDelay)
            }

            if (shouldReloadCalendar) {
                try  {
                    ApplicationController.onCalendarReloadFromService(this, userActionUntil)
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

    fun sleep(time: Int) {
        try {
            Thread.sleep(time.toLong())
        }
        catch (ex: Exception) {
        }
    }

    companion object {
        private const val LOG_TAG = "CalendarMonitorSvc"

        private const val WAKE_LOCK_NAME = "CalendarMonitor"
        private const val START_DELAY = "start_delay"
        private const val RELOAD_CALENDAR = "reload_calendar"
        private const val RESCAN_MONITOR = "rescan_monitor"
        private const val USER_ACTION_UNTIL = "user_action_until"

        private const val MAX_TIME_WITHOUT_QUICK_RESCAN = 1000
        private const val QUICK_RESCAN_SLEEP_BEFORE = 300

        fun startRescanService(
                context: Context,
                startDelay: Int = 0,
                reloadCalendar: Boolean = false, // should reload existing reminders
                rescanMonitor: Boolean = true,   // should perform calendar monitor rescan
                userActionUntil: Long = 0 // Time in millis - max deadline to treat as a user action
        ) {
            val intent = Intent(context, CalendarMonitorService::class.java)

            intent.putExtra(START_DELAY, startDelay)
            intent.putExtra(RELOAD_CALENDAR, reloadCalendar)
            intent.putExtra(RESCAN_MONITOR, rescanMonitor)
            intent.putExtra(USER_ACTION_UNTIL, userActionUntil)

            context.startService(intent)
        }
    }
}