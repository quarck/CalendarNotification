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

//TODO: when seeing a new event with alarm time in the past -- notify immediately

//TODO: need manual rescan timer, to re-scan events fully every few hours (preferably when on battery)

package com.github.quarck.calnotify.calendarmonitor

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.app.AlarmScheduler
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.broadcastreceivers.*
import com.github.quarck.calnotify.calendar.*
import com.github.quarck.calnotify.manualalertsstorage.ManualAlertsStorage
import com.github.quarck.calnotify.ui.MainActivity
import com.github.quarck.calnotify.utils.alarmManager
import com.github.quarck.calnotify.utils.cancelExactAndAlarm
import com.github.quarck.calnotify.utils.setExactAndAlarm


// ManualEventAlarmPerioidicRescanBroadcastReceiver

class CalendarManualMonitor(val calendarProvider: CalendarProviderInterface):
        CalendarManualMonitorInterface {

    private var lastScan = 0L

    // should return true if we have fired at new events, so UI should reload if it is open
    override fun onBoot(context: Context): Boolean {
        logger.debug("onBoot: scanning and setting alarms");

        lastScan = System.currentTimeMillis()
        return scanAndScheduleAlarms(context)
    }

    override fun onPeriodicRescanBroadcast(context: Context, intent: Intent) {
        logger.debug("onPeriodicRescanBroadcast: scanning and setting alarms");

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastScan < Consts.ALARM_THRESHOLD/4 )
            return
        lastScan = currentTime

        scanAndScheduleAlarms(context)
    }

    // should return true if we have fired at new events, so UI should reload if it is open
    override fun onAppStarted(context: Context): Boolean {
        logger.debug("onAppStarted: scanning and setting alarms");

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastScan < Consts.ALARM_THRESHOLD/4 )
            return false
        lastScan = currentTime

        return scanAndScheduleAlarms(context)
    }

    // should return true if we have fired at new events, so UI should reload if it is open
    override fun onUpgrade(context: Context): Boolean {
        logger.debug("onUpgrade: scanning and setting alarms");

        lastScan = System.currentTimeMillis()
        return scanAndScheduleAlarms(context)
    }

    override fun onAlarmBroadcast(context: Context, intent: Intent) {

        logger.debug("onAlarmBroadcast");

        val state = CalendarMonitorState(context)

        val currentTime = System.currentTimeMillis()

        val nextEventFireFromProvider = state.nextEventFireFromProvider
        if (nextEventFireFromProvider < currentTime + Consts.ALARM_THRESHOLD) {
            logger.info("onAlarmBroadcast: nextEventFireFromProvider $nextEventFireFromProvider is less than current time, checking what to fire")
            manualFireProviderEventsAt(context, state, state.nextEventFireFromProvider, state.prevEventFireFromProvider)
        }

        val nextEventFireFromScan = state.nextEventFireFromScan
        if (nextEventFireFromScan < currentTime + Consts.ALARM_THRESHOLD) {
            logger.info("onAlarmBroadcast: nextEventFireFromScan $nextEventFireFromScan is less than current time, checking what to fire")
            manualFireManualEventsAt(context, state.nextEventFireFromScan, state.prevEventFireFromScan)
        }

        scanAndScheduleAlarms(context)
        lastScan = System.currentTimeMillis()
    }

    override fun onCalendarChange(context: Context) {

        logger.debug("onCalendarChange");

        val delayInMilliseconds = 1500L
        Handler().postDelayed({ scanAndScheduleAlarms(context) }, delayInMilliseconds)
    }

    override fun onProviderReminderBroadcast(context: Context, intent: Intent) {

        logger.debug("onProviderReminderBroadcast");

        val removeOriginal = Settings(context).removeOriginal

        val uri = intent.data;

        val alertTime = uri?.lastPathSegment?.toLongOrNull()
        if (alertTime == null) {
            logger.error("ERROR alertTime is null!")
            scanAndScheduleAlarms(context)
            return
        }

        try {
            val events = CalendarProvider.getAlertByTime(context, alertTime, skipDismissed = false)

            for (event in events) {

                if (getAlertWasHandled(context, event)) {
                    logger.info("Seen event ${event.eventId} / ${event.instanceStartTime} - it was handled already, skipping")
                    continue
                }

                logger.info("Seen event ${event.eventId} / ${event.instanceStartTime}")

                event.origin = EventOrigin.ProviderBroadcast
                event.timeFirstSeen = System.currentTimeMillis()
                val wasHandled = ApplicationController.onCalendarEventFired(context, event);

                if (wasHandled) {
                    setAlertWasHandled(context, event, createdByUs = false)
                    logger.info("Event ${event.eventId} / ${event.instanceStartTime} is marked as handled in the DB")
                }

                if (wasHandled && removeOriginal) {
                    logger.info("Dismissing original reminder")

                    CalendarProvider.dismissNativeEventAlert(context, event.eventId);
//                        shouldAbortBroadcast = true;
                }
            }

        } catch (ex: Exception) {
            logger.error("Exception while trying to load fired event details, ${ex.message}, ${ex.stackTrace}")
        }

        scanAndScheduleAlarms(context)
    }


    // returns true if has fired any single new event
    private fun doManualFireProviderEventsAt(context: Context, state: CalendarMonitorState, alertTime: Long): Boolean {

        var ret = false

        logger.debug("doManualFireProviderEventsAt, alertTime=$alertTime");

        try {
            val events = CalendarProvider.getAlertByTime(context, alertTime, skipDismissed = false)

            for (event in events) {

                if (getAlertWasHandled(context, event)) {
                    logger.info("Seen event ${event.eventId} / ${event.instanceStartTime} - it was handled already [properly by Calendar Provider receiver]")
                    continue
                }

                logger.info("Warning: Calendar Privider didn't handle this: Seen event ${event.eventId} / ${event.instanceStartTime}")

                ret = true

                event.origin = EventOrigin.ProviderManual
                event.timeFirstSeen = System.currentTimeMillis()
                val wasHandled = ApplicationController.onCalendarEventFired(context, event);

                if (wasHandled) {
                    setAlertWasHandled(context, event, createdByUs = false)
                    logger.info("Event ${event.eventId} / ${event.instanceStartTime} is marked as handled in the DB")
                }
            }

        }
        catch (ex: Exception) {
            logger.error("Exception while trying to load fired event details, ${ex.message}, ${ex.stackTrace}")
        }
        finally {
            state.prevEventFireFromProvider = alertTime
        }

        return ret
    }

    // should return true if we have fired at new events, so UI should reload if it is open
    private fun manualFireProviderEventsAt(context: Context, state: CalendarMonitorState, nextEventFire: Long, prevEventFire: Long? = null): Boolean {

        var ret = false

        logger.debug("manualFireProviderEventsAt: ($nextEventFire, $prevEventFire)");

        var currentPrevFire = prevEventFire

        for (iteration in 0..MAX_ITERATIONS) {

            if (currentPrevFire == null || currentPrevFire == Long.MAX_VALUE)
                break;

            val nextSincePrev = calendarProvider.findNextAlarmTime(context.contentResolver, currentPrevFire + 1L)

            if (nextSincePrev == null || nextSincePrev >= nextEventFire)
                break

            logger.info("manualFireProviderEventsAt: called to fire at $nextEventFire, but found earlier unhandled alarm time $nextSincePrev")

            ret = ret || doManualFireProviderEventsAt(context, state, nextSincePrev)
            currentPrevFire = nextSincePrev
        }

        ret = ret || doManualFireProviderEventsAt(context, state, nextEventFire)

        return ret
    }

    // should return true if we have fired at new events, so UI should reload if it is open
    private fun manualFireManualEventsAt(context: Context, nextEventFire: Long, prevEventFire: Long? = null): Boolean {

        logger.debug("manualFireManualEventsAt - this is not implemented yet");

        return false
    }

    // should return true if we have fired at new events, so UI should reload if it is open
    private fun scanAndScheduleAlarms(context: Context): Boolean {

        logger.debug("scanAndScheduleAlarms");

        schedulePeriodicRescanAlarm(context)

        val state = CalendarMonitorState(context)

        val (nextAlarmFromProvider, firedEvents) = scanNextEventFromProvider(context, state)

        setOrCancelAlarm(context, nextAlarmFromProvider)

        return firedEvents
    }

    private val MAX_ITERATIONS = 1000L

    private fun scanNextEventFromProvider(context: Context, state: CalendarMonitorState): Pair<Long, Boolean> {

        logger.debug("scanNextEventFromProvider");

        val currentTime = System.currentTimeMillis()
        val processEventsTo = currentTime + Consts.ALARM_THRESHOLD

        var nextAlert = state.nextEventFireFromProvider

        var firedEvents = false;

        // First - scan past reminders that we have potentially missed,
        // this is for the case where nextAlert is in the past already (e.g. device was off
        // for a while)
        for (iteration in 0..MAX_ITERATIONS) {

            if (nextAlert < processEventsTo) {

                logger.info("scanNextEventFromProvider: nextAlert $nextAlert is already in the past, firing")

                firedEvents = firedEvents
                        || manualFireProviderEventsAt(context, state, nextAlert)

                nextAlert = calendarProvider.findNextAlarmTime(context.contentResolver, nextAlert + 1L) ?: Long.MAX_VALUE
                continue
            }

            if (nextAlert == Long.MAX_VALUE) {
                logger.info("scanNextEventFromProvider: no next alerts")
                break;
            } else if (nextAlert >= processEventsTo){
                logger.info("scanNextEventFromProvider: nextAlert=$nextAlert")
                break
            }
        }

        // Now - scan for the next alert since currentTime, so we can see future events
        val nextAlert2 = calendarProvider.findNextAlarmTime(context.contentResolver, processEventsTo )
                ?: Long.MAX_VALUE

        // just to be sure to be sure
        nextAlert = Math.min(nextAlert, nextAlert2)

        logger.info("scanNextEventFromProvider: nextAlert2=$nextAlert2, resulting nextAlert=$nextAlert")

        state.nextEventFireFromProvider = nextAlert

        return Pair(nextAlert, firedEvents)
    }

    private fun setOrCancelAlarm(context: Context, time: Long) {

        logger.debug("setOrCancelAlarm");

        val settings = Settings(context)

        if (time != Long.MAX_VALUE) {

            val now = System.currentTimeMillis()

            logger.info("Setting alarm at $time (${(time-now)/1000L/60L} mins from now)")

            val exactTime =
                    if (!settings.enableMonitorDebug)
                        time + Consts.ALARM_THRESHOLD / 2 // give calendar provider a little chance - schedule alarm to a bit after
                    else
                        time - Consts.ALARM_THRESHOLD / 2 // give calendar provider no chance! we want to handle it before it!

            context.alarmManager.setExactAndAlarm(
                    context,
                    settings,
                    exactTime,
                    ManualEventAlarmBroadcastReceiver::class.java, // ignored on KitKat and below
                    ManualEventExactAlarmBroadcastReceiver::class.java,
                    MainActivity::class.java, // alarm info intent
                    AlarmScheduler.logger)
        } else {
            logger.info("No next alerts, cancelling")
            context.alarmManager.cancelExactAndAlarm(
                    context,
                    settings,
                    ManualEventAlarmBroadcastReceiver::class.java, // ignored on KitKat and below
                    ManualEventExactAlarmBroadcastReceiver::class.java,
                    AlarmScheduler.logger)
        }
    }

    private fun schedulePeriodicRescanAlarm(context: Context) {

        val interval = Consts.CALENDAR_RESCAN_INTERVAL
        val next = System.currentTimeMillis() + interval

        logger.debug("schedulePeriodicRescanAlarm, interval: $interval");

        val intent = Intent(context, ManualEventAlarmPerioidicRescanBroadcastReceiver::class.java);
        val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)

        context.alarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP, next, interval, pendingIntent)
    }

//    override fun getAlertsAt(context: android.content.Context, time: Long, mayRescan: Boolean): List<ManualEventAlertEntry> {
//
//        if (mayRescan) {
//            performManualRescan(context)
//        }
//
//        val ret = com.github.quarck.calnotify.manualalertsstorage.ManualAlertsStorage(context).use {
//            db -> db.getAlertsAt(time)
//        }
//
//        return ret
//    }

//    override fun getAlertsAsEventAlertsAt(context: Context, time: Long, mayRescan: Boolean): List<EventAlertRecord> {
//
//        val rawAlerts = getAlertsAt(context, time, mayRescan)
//
//        val ret = arrayListOf<EventAlertRecord>()
//
//        val deadAlerts = arrayListOf<ManualEventAlertEntry>()
//
//        for (alert in rawAlerts) {
//            if (!alert.alertCreatedByUs) {
//                // can go and read it directly from the provider
//                val event = calendarProvider.getAlertByEventIdAndTime(context, alert.eventId, alert.alertTime)
//
//                if (event != null) {
//                    ret.add(event)
//                }
//                else {
//                    logger.error("Can't find event for $alert, was event removed? Dropping from DB");
//                    deadAlerts.add(alert)
//                }
//            }
//            else {
//                // this is manually added alert - load event and manually populate EventAlertRecord!
//                val calEvent = calendarProvider.getEvent(context, alert.eventId)
//                if (calEvent != null) {
//
//                    val event = EventAlertRecord(
//                            calendarId = calEvent.calendarId,
//                            eventId = calEvent.eventId,
//                            isAllDay = calEvent.isAllDay,
//                            isRepeating = false, // fixme
//                            alertTime = alert.alertTime,
//                            notificationId = 0,
//                            title = calEvent.title,
//                            startTime = calEvent.startTime,
//                            endTime = calEvent.endTime,
//                            instanceStartTime = alert.instanceStartTime,
//                            instanceEndTime = alert.instanceEndTime,
//                            location = calEvent.location,
//                            lastEventVisibility = 0,
//                            snoozedUntil = 0
//                    )
//                    ret.add(event)
//                }
//                else {
//                    logger.error("Cant find event id ${alert.eventId}")
//                    deadAlerts.add(alert)
//                }
//
//            }
//        }
//
//        // TODO: check this
////        ManualAlertsStorage(context).use {
////            db ->
////            for (dead in deadAlerts) {
////                db.deleteAlert(dead)
////            }
////        }
//
//        return ret;
//    }

    override fun setAlertWasHandled(context: Context, ev: EventAlertRecord, createdByUs: Boolean) {

        logger.info("setAlertWasHandled, ${ev.eventId}, ${ev.instanceStartTime}, ${ev.alertTime}");

        ManualAlertsStorage(context).use {
            db ->
            var alert: ManualEventAlertEntry? = db.getAlert(ev.eventId, ev.alertTime, ev.instanceStartTime)

            if (alert != null) {
                alert.wasHandled = true
                db.updateAlert(alert)
            } else {
                alert = ManualEventAlertEntry(
                        calendarId = ev.calendarId,
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

            // some housekeeping - remove events that has instart start time older than 3 days
            // (current design of this app is not expecting such events to have any outstanding alerts)
            val currentTime = System.currentTimeMillis()
            db.deleteAlertsForEventsOlderThan(currentTime - Consts.ALERTS_DB_REMOVE_AFTER)
        }
    }

    override fun getAlertWasHandled(db: ManualAlertsStorage, ev: EventAlertRecord): Boolean {
        logger.info("getAlertWasHandled, ${ev.eventId}, ${ev.instanceStartTime}, ${ev.alertTime}");
        return db.getAlert(ev.eventId, ev.alertTime, ev.instanceStartTime)?.wasHandled ?: false
    }

    override fun getAlertWasHandled(context: Context, ev: EventAlertRecord): Boolean {
        return ManualAlertsStorage(context).use {
            db ->
            getAlertWasHandled(db, ev)
        }
    }

    companion object {
        val logger = com.github.quarck.calnotify.logs.Logger("CalendarManualMonitor")
    }
}