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
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.utils.detailed
import com.github.quarck.calnotify.utils.powerManager
import com.github.quarck.calnotify.utils.wakeLocked
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobInfo
import android.content.ComponentName
import android.app.job.JobService
import android.os.Build
import com.github.quarck.calnotify.BuildConfig
import com.github.quarck.calnotify.Consts

class CalendarMonitorIntentService : IntentService("CalendarMonitorIntentService") {

    override fun onHandleIntent(intent: Intent?) {

        if (intent == null) {
            DevLog.error(this, LOG_TAG, "Intent is null")
            return
        }

        var startDelay = intent.getIntExtra(START_DELAY, 0)

        val shouldReloadCalendar = intent.getBooleanExtra(RELOAD_CALENDAR, false)
        val userActionUntil = intent.getLongExtra(USER_ACTION_UNTIL, 0)

        DevLog.info(this, LOG_TAG,
                "onHandleIntent: " +
                        "startDelay=$startDelay, " +
                        "shouldReloadCalendar=$shouldReloadCalendar, "
        )

        if (shouldReloadCalendar && startDelay > MAX_TIME_WITHOUT_QUICK_RESCAN) {
            try  {
                sleep(QUICK_RESCAN_SLEEP_BEFORE)
                startDelay -= QUICK_RESCAN_SLEEP_BEFORE

                ApplicationController.onCalendarRescanForRescheduledFromService(
                        this,
                        userActionUntil
                )
            }
            catch (ex: Exception) {
                DevLog.error(this, LOG_TAG, "Exception while reloading calendar: ${ex.detailed}")
            }
        }

        if (startDelay != 0) {
            sleep(startDelay)
        }

        if (shouldReloadCalendar) {
            try  {
                ApplicationController.onCalendarReloadFromService(
                        this,
                        userActionUntil
                )
            }
            catch (ex: Exception) {
                DevLog.error(this, LOG_TAG, "Exception while rescanning calendar: ${ex.detailed}")
            }
        }

        // Always rescan CalendarChangeRequestMonitor
        try {
            ApplicationController.AddEventMonitorInstance.onRescanFromService(this)
        }
        catch (ex: Exception) {
            DevLog.error(this, LOG_TAG, "Exception while reloading calendar (2nd): ${ex.detailed}")
        }

        try {
            ApplicationController.CalendarMonitor.onRescanFromService(this)
        }
        catch (ex: Exception) {
            DevLog.error(this, LOG_TAG, "Exception while re-scanning calendar: ${ex.detailed}")
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
        private const val USER_ACTION_UNTIL = "user_action_until"

        private const val MAX_TIME_WITHOUT_QUICK_RESCAN = 1000
        private const val QUICK_RESCAN_SLEEP_BEFORE = 300

        fun startRescanService(
                context: Context,
                startDelay: Int = 0,
                reloadCalendar: Boolean = false, // should reload existing reminders
                userActionUntil: Long = 0 // Time in millis - max deadline to treat as a user action
        ) {
            val intent = Intent(context, CalendarMonitorIntentService::class.java)

            intent.putExtra(START_DELAY, startDelay)
            intent.putExtra(RELOAD_CALENDAR, reloadCalendar)
            intent.putExtra(USER_ACTION_UNTIL, userActionUntil)

            try {
                context.startService(intent)
            }
            catch (ex: Exception){
                DevLog.error(context, LOG_TAG, "Failed to start rescan service, ex: $ex, ${ex.stackTrace}")
            }
        }
    }
}

class CalendarMonitorOneTimeJobService : JobService()  {

    override fun onStartJob(params: JobParameters): Boolean {

        DevLog.info(this, LOG_TAG, "onStartJob ")

        try  {
            ApplicationController.onCalendarRescanForRescheduledFromService(
                    this,
                    0
            )
        }
        catch (ex: Exception) {
            DevLog.error(this, LOG_TAG, "Exception while reloading calendar: ${ex.detailed}")
        }

        try  {
            ApplicationController.onCalendarReloadFromService(this, 0)
        }
        catch (ex: Exception) {
            DevLog.error(this, LOG_TAG, "Exception while rescanning calendar: ${ex.detailed}")
        }

        try {
            ApplicationController.CalendarMonitor.onRescanFromService(this)
        }
        catch (ex: Exception) {
            DevLog.error(this, LOG_TAG, "Exception while re-scanning calendar: ${ex.detailed}")
        }

        try {
            ApplicationController.AddEventMonitorInstance.onRescanFromService(this)
        }
        catch (ex: Exception) {
            DevLog.error(this, LOG_TAG, "Exception while reloading calendar (2nd): ${ex.detailed}")
        }

        return false
    }

    override fun onStopJob(params: JobParameters): Boolean {
        return false
    }

    companion object {
        private const val LOG_TAG = "CalendarMonitorSvc"

        private fun getJobInfo(delayMillis: Long): JobInfo {
            val component = ComponentName(
                    "com.github.quarck.calnotify",
                    CalendarMonitorOneTimeJobService::class.java.name)
            val builder =
                    JobInfo.Builder(Consts.JobIDS.CALENDAR_RESCAN_ONCE, component)
                            .setPersisted(false)
                            .setMinimumLatency(delayMillis)
                            .setRequiresDeviceIdle(false)

            return builder.build()
        }

        fun schedule(context: Context, delayMillis: Long) {
            val js = context.getSystemService(JobScheduler::class.java) ?: return
            val jobs = js.allPendingJobs ?: return
            if (jobs.any { j -> j.id == Consts.JobIDS.CALENDAR_RESCAN_ONCE })
                return

            context.getSystemService(JobScheduler::class.java)?.schedule(getJobInfo(delayMillis))
        }
    }
}

class CalendarMonitorPeriodicJobService : JobService()  {

    override fun onStartJob(params: JobParameters): Boolean {

        DevLog.info(this, LOG_TAG, "onStartJob ")

        try  {
            ApplicationController.onCalendarRescanForRescheduledFromService(
                    this,
                    0
            )
        }
        catch (ex: Exception) {
            DevLog.error(this, LOG_TAG, "Exception while reloading calendar: ${ex.detailed}")
        }

        try  {
            ApplicationController.onCalendarReloadFromService(this, 0)
        }
        catch (ex: Exception) {
            DevLog.error(this, LOG_TAG, "Exception while rescanning calendar: ${ex.detailed}")
        }

        try {
            ApplicationController.CalendarMonitor.onRescanFromService(this)
        }
        catch (ex: Exception) {
            DevLog.error(this, LOG_TAG, "Exception while re-scanning calendar: ${ex.detailed}")
        }

        try {
            ApplicationController.AddEventMonitorInstance.onRescanFromService(this)
        }
        catch (ex: Exception) {
            DevLog.error(this, LOG_TAG, "Exception while reloading calendar (2nd): ${ex.detailed}")
        }

        return false
    }

    override fun onStopJob(params: JobParameters): Boolean {
        return false
    }

    companion object {
        private const val LOG_TAG = "CalendarMonitorSvc"

        private fun getJobInfo(): JobInfo {
            val component = ComponentName(
                    BuildConfig.APPLICATION_ID,
                    CalendarMonitorPeriodicJobService::class.java.name)
            val builder =  JobInfo.Builder(Consts.JobIDS.CALENDAR_RESCAN, component)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                builder.setPeriodic(Consts.CALENDAR_RESCAN_INTERVAL,
                                        Consts.CALENDAR_RESCAN_INTERVAL/2)
            }
            else {
                builder.setPeriodic(Consts.CALENDAR_RESCAN_INTERVAL)
            }
            builder.setPersisted(true)
            builder.setRequiresDeviceIdle(false)

            return builder.build()
        }

        fun schedule(context: Context) {
            val js = context.getSystemService(JobScheduler::class.java) ?: return
            val jobs = js.allPendingJobs ?: return
            if (jobs.any { j -> j.id == Consts.JobIDS.CALENDAR_RESCAN })
                return

            context.getSystemService(JobScheduler::class.java)?.schedule(getJobInfo())
        }

    }
}