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

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.broadcastreceivers.ManualEventAlarmBroadcastReceiver
import com.github.quarck.calnotify.broadcastreceivers.ManualEventAlarmPerioidicRescanBroadcastReceiver
import com.github.quarck.calnotify.broadcastreceivers.ManualEventExactAlarmBroadcastReceiver
import com.github.quarck.calnotify.calendar.*
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.monitorstorage.MonitorStorage
import com.github.quarck.calnotify.permissions.PermissionsManager
import com.github.quarck.calnotify.ui.MainActivity
import com.github.quarck.calnotify.utils.alarmManager
import com.github.quarck.calnotify.utils.cancelExactAndAlarm
import com.github.quarck.calnotify.utils.setExactAndAlarm


class CalendarMonitor(val calendarProvider: CalendarProviderInterface) :
        CalendarMonitorInterface {

    private val providerScanner: CalendarMonitorProvider by lazy {
        CalendarMonitorProvider(calendarProvider, this)
    }

    private val manualScanner: CalendarMonitorManual by lazy {
        CalendarMonitorManual(calendarProvider, this)
    }

    private var lastScan = 0L

    override fun onSystemTimeChange(context: Context) {

        DevLog.info(context, LOG_TAG, "onSystemTimeChange");
        launchRescanService(context)
    }

    override fun onPeriodicRescanBroadcast(context: Context, intent: Intent) {

        DevLog.info(context, LOG_TAG, "onPeriodicRescanBroadcast");

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastScan < Consts.ALARM_THRESHOLD / 4)
            return

        launchRescanService(context)
    }

    // should return true if we have fired at new events, so UI should reload if it is open
    override fun onAppResumed(context: Context, monitorSettingsChanged: Boolean) {

        DevLog.info(context, LOG_TAG, "onAppResumed")

        val currentTime = System.currentTimeMillis()
        val doMonitorRescan = monitorSettingsChanged || (currentTime - lastScan >= Consts.ALARM_THRESHOLD / 4)

        launchRescanService(
                context,
                reloadCalendar = true,
                rescanMonitor = doMonitorRescan,
                maxReloadTime = Consts.MAX_CAL_RELOAD_TIME_ON_UI_START_MILLIS
        )
    }

    override fun onAlarmBroadcast(context: Context, intent: Intent) {

        if (!Settings(context).enableCalendarRescan) {
            DevLog.error(context, LOG_TAG, "onAlarmBroadcast - manual scan disabled")
            setOrCancelAlarm(context, Long.MAX_VALUE)
            return
        }

        if (!PermissionsManager.hasAllPermissionsNoCache(context)) {
            DevLog.error(context, LOG_TAG, "onAlarmBroadcast - no calendar permission to proceed")
            setOrCancelAlarm(context, Long.MAX_VALUE)
            return
        }

        DevLog.info(context, LOG_TAG, "onAlarmBroadcast")

        try {

            val state = CalendarMonitorState(context)

            val currentTime = System.currentTimeMillis()

            var firedProvider = false
            var firedManual = false

            val nextEventFireFromProvider = state.nextEventFireFromProvider
            if (nextEventFireFromProvider < currentTime + Consts.ALARM_THRESHOLD) {
                DevLog.info(context, LOG_TAG, "onAlarmBroadcast: nextEventFireFromProvider $nextEventFireFromProvider < current time $currentTime + THRS, checking what to fire")
                firedProvider = providerScanner.manualFireEventsAt_NoHousekeeping(context, state, state.nextEventFireFromProvider, state.prevEventFireFromProvider)
            }

            val nextEventFireFromScan = state.nextEventFireFromScan
            if (nextEventFireFromScan < currentTime + Consts.ALARM_THRESHOLD) {
                DevLog.info(context, LOG_TAG, "onAlarmBroadcast: nextEventFireFromScan $nextEventFireFromScan is less than current time $currentTime + THRS, checking what to fire")
                firedManual = manualScanner.manualFireEventsAt_NoHousekeeping(context, state.nextEventFireFromScan, state.prevEventFireFromScan)
            }

            if (firedProvider || firedManual) {
                ApplicationController.afterCalendarEventFired(context)
            }

            launchRescanService(
                    context,
                    reloadCalendar = true,
                    rescanMonitor = true,
                    maxReloadTime = Consts.MAX_CAL_RELOAD_TIME_ON_AFTER_FIRE_MILLIS
            )
        }
        catch (ex: Exception) {
            DevLog.error(context, LOG_TAG, "Exception in onAlarmBroadcast: $ex, ${ex.message}, ${ex.stackTrace}")
        }

    }

    // proper broadcast from the Calendar Provider. Normally this is a proper
    // way of receiving information about ongoing events. Apparently not always
    // working, that's why the rest of the class is here
    override fun onProviderReminderBroadcast(context: Context, intent: Intent) {

        if (!PermissionsManager.hasAllPermissionsNoCache(context)) {
            DevLog.error(context, LOG_TAG, "onProviderReminderBroadcast - no calendar permission to proceed")
            setOrCancelAlarm(context, Long.MAX_VALUE)
            return
        }

        DevLog.info(context, LOG_TAG, "onProviderReminderBroadcast");

        val uri = intent.data;

        val alertTime = uri?.lastPathSegment?.toLongOrNull()
        if (alertTime == null) {
            DevLog.error(context, LOG_TAG, "ERROR alertTime is null!")
            launchRescanService(context)
            return
        }

        val eventsToPost = mutableListOf<EventAlertRecord>()
        val eventsToSilentlyDrop = mutableListOf<EventAlertRecord>()

        try {
            val events = CalendarProvider.getAlertByTime(context, alertTime, skipDismissed = false)

            for (event in events) {

                if (getAlertWasHandled(context, event)) {
                    DevLog.info(context, LOG_TAG, "Broadcast: Event ${event.eventId} / ${event.instanceStartTime} was handled already")
                    continue
                }

                DevLog.info(context, LOG_TAG, "Broadcast: Seen event ${event.eventId} / ${event.instanceStartTime}")

                event.origin = EventOrigin.ProviderBroadcast
                event.timeFirstSeen = System.currentTimeMillis()

                if (ApplicationController.shouldMarkEventAsHandledAndSkip(context, event)) {
                    eventsToSilentlyDrop.add(event)
                }
                else if (ApplicationController.registerNewEvent(context, event)) {
                    eventsToPost.add(event)
                }
            }

        }
        catch (ex: Exception) {
            DevLog.error(context, LOG_TAG, "Exception while trying to load fired event details, ${ex.message}, ${ex.stackTrace}")
        }

        try {
            ApplicationController.postEventNotifications(context, eventsToPost)

            for (event in eventsToSilentlyDrop) {
                setAlertWasHandled(context, event, createdByUs = false)
                CalendarProvider.dismissNativeEventAlert(context, event.eventId);
                DevLog.info(context, LOG_TAG, "IGNORED Event ${event.eventId} / ${event.instanceStartTime} is marked as handled in the DB and in the provider")
            }

            for (event in eventsToPost) {
                setAlertWasHandled(context, event, createdByUs = false)
                CalendarProvider.dismissNativeEventAlert(context, event.eventId);
                DevLog.info(context, LOG_TAG, "Event ${event.eventId} / ${event.instanceStartTime}: marked as handled in the DB and in the provider")
            }
        }
        catch (ex: Exception) {
            DevLog.error(context, LOG_TAG, "Exception while posting notifications: $ex, ${ex.stackTrace}")
        }

        ApplicationController.afterCalendarEventFired(context)

        launchRescanService(
                context,
                reloadCalendar = true,
                rescanMonitor = true,
                maxReloadTime = Consts.MAX_CAL_RELOAD_TIME_ON_AFTER_FIRE_MILLIS
        )
    }

    // should return true if we have fired at new events, so UI should reload if it is open
    override fun launchRescanService(
            context: Context,
            delayed: Int,
            reloadCalendar: Boolean,
            rescanMonitor: Boolean,
            maxReloadTime: Long
    ) {
        lastScan = System.currentTimeMillis()

        CalendarMonitorService.startRescanService(context, delayed, reloadCalendar, rescanMonitor, maxReloadTime)
    }

    override fun onRescanFromService(context: Context, intent: Intent?) {

        if (!Settings(context).enableCalendarRescan) {
            DevLog.error(context, LOG_TAG, "onRescanFromService - manual scan disabled")
            setOrCancelAlarm(context, Long.MAX_VALUE)
            return
        }

        if (!PermissionsManager.hasAllPermissionsNoCache(context)) {
            DevLog.error(context, LOG_TAG, "onRescanFromService - no calendar permission to proceed")
            setOrCancelAlarm(context, Long.MAX_VALUE)
            return
        }

        DevLog.info(context, LOG_TAG, "onRescanFromService")

        var firedAnything = false

        try {

            val scanStart = System.currentTimeMillis()

            schedulePeriodicRescanAlarm(context)

            val state = CalendarMonitorState(context)

            val scanPh1 = System.currentTimeMillis()

            val (nextAlarmFromProvider, firedEventsProvider) = providerScanner.scanNextEvent(context, state)

            val scanPh2 = System.currentTimeMillis()

            val (nextAlarmFromManual, firedEventsManual) = manualScanner.scanNextEvent(context, state)

            val scanPh3 = System.currentTimeMillis()

            setOrCancelAlarm(context, Math.min(nextAlarmFromProvider, nextAlarmFromManual))

            val scanPh4 = System.currentTimeMillis()

            DevLog.info(context, LOG_TAG, "Next alarm from provider: $nextAlarmFromProvider, manual: $nextAlarmFromManual, " +
                    "perf: ${scanPh4 - scanStart}, ${scanPh1 - scanStart}, ${scanPh2 - scanPh1}, ${scanPh3 - scanPh2}, ${scanPh4 - scanPh3}")

            firedAnything = firedEventsProvider || firedEventsManual
        }
        catch (ex: java.lang.SecurityException) {
            DevLog.error(context, LOG_TAG, "onRescanFromService: SecurityException, ${ex.message}, ${ex.stackTrace}")
        }
        catch (ex: Exception) {
            DevLog.error(context, LOG_TAG, "onRescanFromService: exception, ${ex.message}, ${ex.stackTrace}")
        }

        if (firedAnything)
            ApplicationController.afterCalendarEventFired(context)
    }

    private fun setOrCancelAlarm(context: Context, time: Long) {

        DevLog.debug(LOG_TAG, "setOrCancelAlarm");

        val settings = Settings(context)

        if (time != Long.MAX_VALUE && time != 0L) {

            val now = System.currentTimeMillis()

            DevLog.info(context, LOG_TAG, "Setting alarm at $time (T+${(time - now) / 1000L / 60L}min)")

            val exactTime = time + Consts.ALARM_THRESHOLD / 2 // give calendar provider a little chance - schedule alarm to a bit after

            context.alarmManager.setExactAndAlarm(
                    context,
                    settings.useSetAlarmClockForFailbackEventPaths,
                    exactTime,
                    ManualEventAlarmBroadcastReceiver::class.java, // ignored on KitKat and below
                    ManualEventExactAlarmBroadcastReceiver::class.java,
                    MainActivity::class.java // alarm info intent
                    )
        }
        else {
            DevLog.info(context, LOG_TAG, "No next alerts, cancelling")
            context.alarmManager.cancelExactAndAlarm(
                    context,
                    ManualEventAlarmBroadcastReceiver::class.java,
                    ManualEventExactAlarmBroadcastReceiver::class.java
                    )
        }
    }

    private fun schedulePeriodicRescanAlarm(context: Context) {

        val interval = Consts.CALENDAR_RESCAN_INTERVAL
        val next = System.currentTimeMillis() + interval

        DevLog.debug(LOG_TAG, "schedulePeriodicRescanAlarm, interval: $interval");

        val intent = Intent(context, ManualEventAlarmPerioidicRescanBroadcastReceiver::class.java);
        val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)

        context.alarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP, next, interval, pendingIntent)
    }

    override fun getAlertsAt(context: android.content.Context, time: Long): List<MonitorEventAlertEntry> {

        val ret = MonitorStorage(context).use {
            db ->
            db.getAlertsAt(time)
        }

        return ret
    }

    override fun getAlertsForAlertRange(context: Context, scanFrom: Long, scanTo: Long): List<MonitorEventAlertEntry> {

        val ret = MonitorStorage(context).use {
            db ->
            db.getAlertsForAlertRange(scanFrom, scanTo)
        }

        return ret
    }

    override fun setAlertWasHandled(context: Context, ev: EventAlertRecord, createdByUs: Boolean) {

        MonitorStorage(context).use {
            db ->
            var alert: MonitorEventAlertEntry? = db.getAlert(ev.eventId, ev.alertTime, ev.instanceStartTime)

            if (alert != null) {

                DevLog.debug(LOG_TAG, "setAlertWasHandled, ${ev.eventId}, ${ev.instanceStartTime}, ${ev.alertTime}: seen this alert already, updating status to wasHandled");
                alert.wasHandled = true
                db.updateAlert(alert)

            }
            else {

                DevLog.debug(LOG_TAG, "setAlertWasHandled, ${ev.eventId}, ${ev.instanceStartTime}, ${ev.alertTime}: new alert, simply adding");
                alert = MonitorEventAlertEntry(
                        eventId = ev.eventId,
                        alertTime = ev.alertTime,
                        isAllDay = ev.isAllDay,
                        instanceStartTime = ev.instanceStartTime,
                        instanceEndTime = ev.instanceEndTime,
                        wasHandled = true,
                        alertCreatedByUs = createdByUs
                )
                db.addAlert(alert)
            }
        }
    }

    override fun getAlertWasHandled(db: MonitorStorage, ev: EventAlertRecord): Boolean {
        DevLog.debug(LOG_TAG, "getAlertWasHandled, ${ev.eventId}, ${ev.instanceStartTime}, ${ev.alertTime}");
        return db.getAlert(ev.eventId, ev.alertTime, ev.instanceStartTime)?.wasHandled ?: false
    }

    override fun getAlertWasHandled(context: Context, ev: EventAlertRecord): Boolean {
        return MonitorStorage(context).use {
            db ->
            getAlertWasHandled(db, ev)
        }
    }

    companion object {
        private const val LOG_TAG = "CalendarMonitor"
        //val logger = com.github.quarck.calnotify.logs.Logger(LOG_TAG)
    }
}