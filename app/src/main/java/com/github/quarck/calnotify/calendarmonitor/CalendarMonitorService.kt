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
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.utils.powerManager
import com.github.quarck.calnotify.utils.wakeLocked

class CalendarMonitorService : IntentService("CalendarMonitorService") {

    override fun onHandleIntent(intent: Intent?) {

        val startDelay = intent?.getIntExtra(START_DELAY, 0) ?: 0

        wakeLocked(powerManager, PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_NAME) {

            if (startDelay != 0) {
                try {
                    Thread.sleep(startDelay.toLong())
                }
                catch (ex: Exception) {
                }
            }

            ApplicationController.CalendarMonitorService.onRescanFromService(this, intent)
        }
    }

    companion object {
        private const val WAKE_LOCK_NAME = "CalendarMonitor"
        private const val START_DELAY = "start_delay"

        fun startRescanService(context: Context, startDelay: Int) {
            val intent = Intent(context, CalendarMonitorService::class.java)
            intent.putExtra(START_DELAY, startDelay)
            context.startService(intent)
        }
    }
}