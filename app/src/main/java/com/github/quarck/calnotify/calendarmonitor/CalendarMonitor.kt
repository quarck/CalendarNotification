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
import com.github.quarck.calnotify.broadcastreceivers.ManualEventExactAlarmBroadcastReceiver
import com.github.quarck.calnotify.broadcastreceivers.ManualEventAlarmPeriodicRescanBroadcastReceiver
import com.github.quarck.calnotify.calendar.*
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.monitorstorage.MonitorStorage
import com.github.quarck.calnotify.permissions.PermissionsManager
import com.github.quarck.calnotify.ui.MainActivity
import com.github.quarck.calnotify.utils.alarmManager
import com.github.quarck.calnotify.utils.cancelExactAndAlarm
import com.github.quarck.calnotify.utils.detailed
import com.github.quarck.calnotify.utils.setExactAndAlarm


class CalendarMonitor(val calendarProvider: CalendarProviderInterface) :
        CalendarMonitorInterface {

    private val manualScanner: CalendarMonitorManual by lazy {
        CalendarMonitorManual(calendarProvider, this)
    }

    private var lastScan = 0L

    override fun onSystemTimeChange(context: Context) {

        DevLog.info(LOG_TAG, "onSystemTimeChange");
        launchRescanService(context)
    }

    override fun onPeriodicRescanBroadcast(context: Context, intent: Intent) {

        DevLog.info(LOG_TAG, "onPeriodicRescanBroadcast");

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastScan < Consts.ALARM_THRESHOLD / 4)
            return

        launchRescanService(context)
    }

    // should return true if we have fired at new requests, so UI should reload if it is open
    override fun onAppResumed(context: Context, monitorSettingsChanged: Boolean) {

        DevLog.info(LOG_TAG, "onAppResumed")

        val currentTime = System.currentTimeMillis()
        val doMonitorRescan = monitorSettingsChanged || (currentTime - lastScan >= Consts.ALARM_THRESHOLD / 4)

        launchRescanService(
                context,
                reloadCalendar = true,
                rescanMonitor = doMonitorRescan,
                userActionUntil = System.currentTimeMillis() + Consts.MAX_USER_ACTION_DELAY
        )
    }

    override fun onAlarmBroadcast(context: Context, intent: Intent) {

        if (!Settings(context).enableCalendarRescan) {
            DevLog.error(LOG_TAG, "onAlarmBroadcast - manual scan disabled")
            setOrCancelAlarm(context, Long.MAX_VALUE)
            return
        }

        if (!PermissionsManager.hasAllCalendarPermissionsNoCache(context)) {
            DevLog.error(LOG_TAG, "onAlarmBroadcast - no calendar permission to proceed")
            setOrCancelAlarm(context, Long.MAX_VALUE)
            return
        }

        DevLog.info(LOG_TAG, "onAlarmBroadcast")

        try {

            val state = CalendarMonitorState(context)

            val currentTime = System.currentTimeMillis()

            val nextEventFireFromScan = state.nextEventFireFromScan
            if (nextEventFireFromScan < currentTime + Consts.ALARM_THRESHOLD) {

                DevLog.info(LOG_TAG, "onAlarmBroadcast: nextEventFireFromScan $nextEventFireFromScan is less than current" +
                        " time $currentTime + THRS, checking what to fire")

                val firedManual = manualScanner.manualFireEventsAt_NoHousekeeping(
                        context, state.nextEventFireFromScan, state.prevEventFireFromScan)

                if (firedManual) {
                    ApplicationController.afterCalendarEventFired(context)
                }
            }

            launchRescanService(
                    context,
                    reloadCalendar = true,
                    rescanMonitor = true
            )
        }
        catch (ex: Exception) {
            DevLog.error(LOG_TAG, "Exception in onAlarmBroadcast: $ex, ${ex.detailed}")
        }
    }

    // proper broadcast from the Calendar Provider. Normally this is a proper
    // way of receiving information about ongoing requests. Apparently not always
    // working, that's why the rest of the class is here
    override fun onProviderReminderBroadcast(context: Context, intent: Intent) {

        if (!PermissionsManager.hasAllCalendarPermissionsNoCache(context)) {
            DevLog.error(LOG_TAG, "onProviderReminderBroadcast - no calendar permission to proceed")
            setOrCancelAlarm(context, Long.MAX_VALUE)
            return
        }

        DevLog.info(LOG_TAG, "onProviderReminderBroadcast");

        val uri = intent.data;

        val alertTime = uri?.lastPathSegment?.toLongOrNull()
        if (alertTime == null) {
            DevLog.error(LOG_TAG, "ERROR alertTime is null!")
            launchRescanService(context)
            return
        }

        val eventsToPost = mutableListOf<EventAlertRecord>()
        val eventsToSilentlyDrop = mutableListOf<EventAlertRecord>()

        try {

            val settings = Settings(context)

            val events = CalendarProvider.getAlertByTime(
                    context, alertTime,
                    skipDismissed = false,
                    skipExpiredEvents = settings.skipExpiredEvents
            )

            for (event in events) {

                if (getAlertWasHandled(context, event)) {
                    DevLog.info(LOG_TAG, "Broadcast: Event ${event.eventId} / ${event.instanceStartTime} was handled already")
                    continue
                }

                DevLog.info(LOG_TAG, "Broadcast: Seen event ${event.eventId} / ${event.instanceStartTime}")

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
            DevLog.error(LOG_TAG, "Exception while trying to load fired event details, ${ex.detailed}")
        }

        try {
            ApplicationController.postEventNotifications(context, eventsToPost)

            for (event in eventsToSilentlyDrop) {
                setAlertWasHandled(context, event, createdByUs = false)
                CalendarProvider.dismissNativeEventAlert(context, event.eventId);
                DevLog.info(LOG_TAG, "IGNORED Event ${event.eventId} / ${event.instanceStartTime} is marked as handled in the DB and in the provider")
            }

            for (event in eventsToPost) {
                setAlertWasHandled(context, event, createdByUs = false)
                CalendarProvider.dismissNativeEventAlert(context, event.eventId);
                DevLog.info(LOG_TAG, "Event ${event.eventId} / ${event.instanceStartTime}: marked as handled in the DB and in the provider")
            }
        }
        catch (ex: Exception) {
            DevLog.error(LOG_TAG, "Exception while posting notifications: ${ex.detailed}")
        }

        ApplicationController.afterCalendarEventFired(context)

        launchRescanService(
                context,
                reloadCalendar = true,
                rescanMonitor = true
        )
    }

    // should return true if we have fired at new requests, so UI should reload if it is open
    override fun launchRescanService(
            context: Context,
            delayed: Int,
            reloadCalendar: Boolean,
            rescanMonitor: Boolean,
            userActionUntil: Long
    ) {
        lastScan = System.currentTimeMillis()

        CalendarMonitorService.startRescanService(context, delayed, reloadCalendar, rescanMonitor, userActionUntil)
    }

    override fun onEventEditedByUs(context: Context, eventId: Long) {

        DevLog.info(LOG_TAG, "onEventEditedByUs")

        val settings = Settings(context)

        if (!settings.enableCalendarRescan) {
            DevLog.error(LOG_TAG, "onEventEditedByUs - manual scan disabled")
            return
        }

        if (!settings.rescanCreatedEvent) {
            DevLog.error(LOG_TAG, "onEventEditedByUs - manual scan disabled[2]")
            return
        }


        if (!PermissionsManager.hasAllCalendarPermissionsNoCache(context)) {
            DevLog.error(LOG_TAG, "onEventEditedByUs - no calendar permission to proceed")
            setOrCancelAlarm(context, Long.MAX_VALUE)
            return
        }

        val event: EventRecord? = calendarProvider.getEvent(context, eventId)
        if (event == null) {
            DevLog.error(LOG_TAG, "onEventEditedByUs - cannot find event $eventId")
            return
        }

        var firedAnything = false

        try {

            val scanStart = System.currentTimeMillis()

            firedAnything = manualScanner.scanForSingleEvent(context, event)

            val scanEnd = System.currentTimeMillis()

            DevLog.info(LOG_TAG, "scanForSingleEvent, perf: ${scanEnd - scanStart}")
        }
        catch (ex: java.lang.SecurityException) {
            DevLog.error(LOG_TAG, "onEventEditedByUs: SecurityException, ${ex.detailed}")
        }
        catch (ex: Exception) {
            DevLog.error(LOG_TAG, "onEventEditedByUs: exception, ${ex.detailed}")
        }

        if (firedAnything)
            ApplicationController.afterCalendarEventFired(context)
    }

    override fun onRescanFromService(context: Context) {

        if (!PermissionsManager.hasAllCalendarPermissionsNoCache(context)) {
            DevLog.error(LOG_TAG, "onRescanFromService - no calendar permission to proceed")
            setOrCancelAlarm(context, Long.MAX_VALUE)
            return
        }

        // Always schedule it regardless of..
        schedulePeriodicRescanAlarm(context)

        if (!Settings(context).enableCalendarRescan) {
            DevLog.error(LOG_TAG, "onRescanFromService - manual scan disabled")
            setOrCancelAlarm(context, Long.MAX_VALUE)
            return
        }

        DevLog.info(LOG_TAG, "onRescanFromService")

        var firedAnything = false

        try {

            val t0 = System.currentTimeMillis()

            val state = CalendarMonitorState(context)

            val t1 = System.currentTimeMillis()

            val (nextAlarmFromManual, firedEventsManual) = manualScanner.scanNextEvent(context, state)

            val t2 = System.currentTimeMillis()

            setOrCancelAlarm(context, nextAlarmFromManual)

            val t3 = System.currentTimeMillis()

            DevLog.info(LOG_TAG, "Manual scan, next alarm: $nextAlarmFromManual, " +
                    "timings: ${t3-t2},${t2-t1},${t1-t0}")

            firedAnything = firedEventsManual
        }
        catch (ex: java.lang.SecurityException) {
            DevLog.error(LOG_TAG, "onRescanFromService: SecurityException, ${ex.detailed}")
        }
        catch (ex: Exception) {
            DevLog.error(LOG_TAG, "onRescanFromService: exception, ${ex.detailed}")
        }

        if (firedAnything)
            ApplicationController.afterCalendarEventFired(context)
    }

    private fun setOrCancelAlarm(context: Context, time: Long) {

        DevLog.debug(LOG_TAG, "setOrCancelAlarm");

        val settings = Settings(context)

        if (time != Long.MAX_VALUE && time != 0L) {

            val now = System.currentTimeMillis()

            DevLog.info(LOG_TAG, "Setting alarm at $time (T+${(time - now) / 1000L / 60L}min)")

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
            DevLog.info(LOG_TAG, "No next alerts, cancelling")
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

        val intent = Intent(context, ManualEventAlarmPeriodicRescanBroadcastReceiver::class.java);
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