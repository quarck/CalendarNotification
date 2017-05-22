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
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.calendar.CalendarProviderInterface
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.calendar.EventOrigin
import com.github.quarck.calnotify.calendar.MonitorEventAlertEntry
import com.github.quarck.calnotify.monitorstorage.MonitorStorage


//TODO: when seeing a new event with alarm time in the past -- notify immediately

//TODO: need manual rescan timer, to re-scan events fully every few hours (preferably when on battery)

//TODO: need to skip not handled calendars! (Full scan only)


class CalendarMonitorManual(
        val calendarProvider: CalendarProviderInterface,
        val calendarMonitor: CalendarMonitorInterface
) {

    // should return true if we have fired at new events, so UI should reload if it is open
    fun manualFireEventsAt(context: Context, nextEventFire: Long, prevEventFire: Long? = null): Boolean {

        var fired = false

        var lastFiredAlert = 0L

        var alerts =
                MonitorStorage(context).use {
                    db ->
                    if (prevEventFire == null)
                        db.getAlertsAt(nextEventFire)
                    else
                        db.getAlertsForAlertRange(prevEventFire, nextEventFire)
                }

        alerts = alerts.filter { !it.wasHandled }
                .sortedBy { it.alertTime }

        logger.info("manualFireEventsAt: got ${alerts.size} alerts to fire at");

        if (!alerts.isEmpty()) {

            val settings = Settings(context)
            val state = CalendarMonitorState(context)

            for (alert in alerts) {
                if (manualFireAlert(context, settings, state, alert)) {
                    fired = true
                    lastFiredAlert = Math.max(lastFiredAlert, alert.alertTime)
                }
            }

            state.prevEventFireFromScan = lastFiredAlert
            logger.info("manualFireEventsAt: prevEventFireFromScan was set to $lastFiredAlert")
        }

        return fired
    }

    private fun manualFireAlert(context: Context, settings: Settings, state: CalendarMonitorState, alert: MonitorEventAlertEntry): Boolean {

        if (alert.wasHandled)
            return false

        logger.info("manualFireAlert: firing at alert $alert")

        var event: EventAlertRecord? = null

        if (!alert.alertCreatedByUs) {
            // not manually created -- can read directly from the provider!
            logger.debug("Alert was not created by the app, so trying to read alert off the provider")
            event = calendarProvider.getAlertByEventIdAndTime(context, alert.eventId, alert.alertTime)
        }

        if (event == null) {
            logger.debug("Still has no alert info from provider - reading event off the provider")

            val calEvent = calendarProvider.getEvent(context, alert.eventId)
            if (calEvent != null) {
                event = EventAlertRecord(
                        calendarId = calEvent.calendarId,
                        eventId = calEvent.eventId,
                        isAllDay = calEvent.isAllDay,
                        isRepeating = calendarProvider.isRepeatingEvent(context, alert.eventId) ?: false,
                        alertTime = alert.alertTime,
                        notificationId = 0,
                        title = calEvent.title,
                        startTime = calEvent.startTime,
                        endTime = calEvent.endTime,
                        instanceStartTime = alert.instanceStartTime,
                        instanceEndTime = alert.instanceEndTime,
                        location = calEvent.location,
                        lastEventVisibility = 0,
                        snoozedUntil = 0
                )
            }
        }

        if (event != null) {

            logger.info("Full manual scan: seen event ${event.eventId} / ${event.instanceStartTime} / ${event.alertTime}")

            // found an event
            event.origin = EventOrigin.FullManual
            event.timeFirstSeen = System.currentTimeMillis()

            val wasHandled = ApplicationController.onCalendarEventFired(context, event);

            if (wasHandled) {
                markAlertAsHandledInDB(context, alert)

                logger.info("Event ${event.eventId} / ${event.instanceStartTime} is marked as handled in the DB")

                if (settings.markEventsAsHandledInProvider && !alert.alertCreatedByUs) {
                    logger.info("Dismissing original reminder - marking event as handled in the provider")
                    calendarProvider.dismissNativeEventAlert(context, event.eventId);
                }
            }
        } else {
            logger.error("Alert: $alert, cant find neither alert nor event. Marking as handled and ignoring.")
            // all attempts failed - still, markt it as handled, so avoid repeated attempts all over again
            markAlertAsHandledInDB(context, alert)
        }

        return false
    }

    private fun markAlertAsHandledInDB(context: Context, alert: MonitorEventAlertEntry) {
        MonitorStorage(context).use {
            db ->
            logger.info("marking as handled alert: $alert");
            alert.wasHandled = true
            db.updateAlert(alert)
        }
    }

    private fun min3(v1: Long, v2: Long, v3: Long): Long {
        return Math.min(Math.min(v1, v2), v3);
    }

    fun scanNextEvent(context: Context, state: CalendarMonitorState): Pair<Long, Boolean> {

        var hasFiredAnything = false

        // TODO: timestamp, how long it takes!!

        val settings = Settings(context)

        val scanWindow = settings.manualCalWatchScanWindow
        val scanWindowBackward = Consts.ALERTS_DB_REMOVE_AFTER

        val prevScanTo = state.prevEventScanTo
        val prevFire = state.prevEventFireFromScan
        val currentTime = System.currentTimeMillis()

        val scanFrom = min3(currentTime, prevScanTo, prevFire) - scanWindowBackward
        val scanTo = scanFrom + scanWindow

        state.prevEventScanTo = scanTo

        logger.info("scanNextEvent: scan range: $scanFrom, $scanTo")

        val alerts = calendarProvider.getEventAlertsManually(context, scanFrom, scanTo)

        logger.info("scanNextEvent: got ${alerts.size} events off the provider")

        val alertsMerged = filterAndMergeAlerts(context, alerts, scanFrom, scanTo)
                .sortedBy { it.alertTime }

        logger.info("scanNextEvent: merged conut: $alertsMerged")

        // now we only need to simply fire at all missed events,
        // and pick the nearest future event,
        // don't forget to update state

        val fireAlertsUpTo = currentTime + Consts.ALARM_THRESHOLD

        val dueAlerts = alertsMerged.filter { !it.wasHandled && it.alertTime <= fireAlertsUpTo }

        logger.info("scanNextEvent: ${dueAlerts.size} due alerts (we should have fired already!!!)")

        for (alert in dueAlerts) {
            if (manualFireAlert(context, settings, state, alert)) {
                hasFiredAnything = true
            }
        }

        // Finally - find the next nearest alert
        val nextAlert = alertsMerged.filter { !it.wasHandled && it.alertTime > fireAlertsUpTo }
                .minBy { it.alertTime }

        // TODO: below: -- calendar provider handles most of it, we need to
        // make sure we re-scan on setting change, to update alert times
        val shouldRemindForEventsWithNoReminders = settings.shouldRemindForEventsWithNoReminders


        val nextAlertTime = nextAlert?.alertTime ?: Long.MAX_VALUE
        state.nextEventFireFromScan = nextAlertTime

        logger.info("scanNextEvent: next alert $nextAlertTime");

        return Pair(nextAlertTime, hasFiredAnything)
    }

    fun filterAndMergeAlerts(context: Context, alerts: List<MonitorEventAlertEntry>, scanFrom: Long, scanTo: Long): List<MonitorEventAlertEntry> {

        val ret = arrayListOf<MonitorEventAlertEntry>()

        val providedAlerts = alerts.associateBy { it.key }

        MonitorStorage(context).use {
            db ->
            val knownAlerts = db.getAlertsForInstanceStartRange(scanFrom, scanTo).associateBy { it.key }

            val newAlerts = providedAlerts - knownAlerts.keys
            val disappearedAlerts = knownAlerts - providedAlerts.keys

            logger.info("filterAndMergeAlerts: ${newAlerts.size} new alerts, ${disappearedAlerts.size} disappeared alerts")

            db.deleteAlerts(disappearedAlerts.values)
            db.addAlerts(newAlerts.values)

            // Presumably this would be faster than re-reading SQLite again
            ret.addAll((knownAlerts - disappearedAlerts.keys + newAlerts).values)
        }

        return ret
    }


    companion object {
        val logger = com.github.quarck.calnotify.logs.Logger("CalendarMonitorManual")
    }
}