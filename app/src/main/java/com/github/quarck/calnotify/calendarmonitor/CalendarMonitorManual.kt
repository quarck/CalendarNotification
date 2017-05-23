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


//TODO: need manual rescan timer, to re-scan events fully every few hours (preferably when on battery)

class CalendarMonitorManual(
        val calendarProvider: CalendarProviderInterface,
        val calendarMonitor: CalendarMonitorInterface
) {

    // should return true if we have fired at new events, so UI should reload if it is open
    fun manualFireEventsAt_NoHousekeeping(context: Context, nextEventFire: Long, prevEventFire: Long? = null): Boolean {

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

        val firedAlerts = mutableListOf<Pair<MonitorEventAlertEntry, EventAlertRecord>>()

        logger.info("manualFireEventsAt: got ${alerts.size} alerts to fire at");

        if (!alerts.isEmpty()) {

            for (alert in alerts) {
                val event = registerFiredEventInDB(context, alert)
                if (event != null)
                    firedAlerts.add(Pair(alert, event))
            }
        }

        if (!firedAlerts.isEmpty()) {
            fired = true

            val settings = Settings(context)
            val markEventsAsHandledInProvider = settings.markEventsAsHandledInProvider

            try {
                ApplicationController.postEventNotifications(context, firedAlerts.map { it.second })
            }
            catch (ex: Exception) {
                logger.error("Got exception while posting notifications: $ex, ${ex.stackTrace}")
            }

            markAlertsAsHandledInDB(context, firedAlerts.map { it.first })

            for ((alert, event) in firedAlerts) {

                logger.info("Event ${alert.eventId} / ${alert.instanceStartTime} / ${alert.alertTime} is marked as handled in the DB (earlier)")

                if (markEventsAsHandledInProvider && !alert.alertCreatedByUs) {
                    logger.info("Dismissing original reminder - marking event as handled in the provider")
                    calendarProvider.dismissNativeEventAlert(context, event.eventId);
                }

                lastFiredAlert = Math.max(lastFiredAlert, alert.alertTime)
            }

            CalendarMonitorState(context).prevEventFireFromScan = lastFiredAlert
            logger.info("manualFireEventsAt: prevEventFireFromScan was set to $lastFiredAlert")
        }

        return fired
    }

    private fun registerFiredEventInDB(context: Context, alert: MonitorEventAlertEntry): EventAlertRecord? {

        var ret: EventAlertRecord? = null

        if (alert.wasHandled)
            return null

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

            if (ApplicationController.registerNewEvent(context, event))
                ret = event

        } else {
            logger.error("Alert: $alert, cant find neither alert nor event. Marking as handled and ignoring.")
            // all attempts failed - still, markt it as handled, so avoid repeated attempts all over again
            markAlertsAsHandledInDB(context, listOf(alert))
        }

        return ret
    }

    private fun markAlertsAsHandledInDB(context: Context, alerts: Collection<MonitorEventAlertEntry>) {
        MonitorStorage(context).use {
            db ->
            logger.info("marking ${alerts.size} alerts as handled in the manual alerts DB");

            for (alert in alerts)
                alert.wasHandled = true

            db.updateAlerts(alerts)
        }
    }

    private fun min3(v1: Long, v2: Long, v3: Long): Long {
        return Math.min(Math.min(v1, v2), v3);
    }

    fun scanNextEvent_NoHousekeping(context: Context, state: CalendarMonitorState): Pair<Long, Boolean> {

        var hasFiredAnything = false

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

        logger.info("scanNextEvent: merged count: ${alertsMerged.size}")

        // now we only need to simply fire at all missed events,
        // and pick the nearest future event,
        // don't forget to update state

        val fireAlertsUpTo = currentTime + Consts.ALARM_THRESHOLD

        val dueAlerts = alertsMerged.filter { !it.wasHandled && it.alertTime <= fireAlertsUpTo }

        val firedDueAlerts = mutableListOf<Pair<MonitorEventAlertEntry, EventAlertRecord>>()

        logger.info("scanNextEvent: ${dueAlerts.size} due alerts (we should have fired already!!!)")

        for (alert in dueAlerts) {
            val event = registerFiredEventInDB(context, alert)
            if (event != null) {
                firedDueAlerts.add(Pair(alert, event))
            }
        }

        if (!firedDueAlerts.isEmpty()) {

            hasFiredAnything = true

            try {
                val start = System.currentTimeMillis()
                ApplicationController.postEventNotifications(context, firedDueAlerts.map { it.second } )
                val end = System.currentTimeMillis()

                logger.debug("ApplicationController.postEventNotifications took ${end-start}ms");
            }
            catch (ex: Exception) {
                logger.error("Got exception while posting notifications: $ex, ${ex.stackTrace}")
            }

            var lastFiredAlert = 0L

            val markEventsAsHandledInProvider = settings.markEventsAsHandledInProvider

            markAlertsAsHandledInDB(context, firedDueAlerts.map { it.first } )

            for ((alert, event) in firedDueAlerts) {

                logger.info("Event ${alert.eventId} / ${alert.instanceStartTime} / ${alert.alertTime} is marked as handled in the DB (earlier)")

                if (markEventsAsHandledInProvider && !alert.alertCreatedByUs) {
                    logger.info("Dismissing original reminder - marking event as handled in the provider")
                    calendarProvider.dismissNativeEventAlert(context, event.eventId);
                }

                lastFiredAlert = Math.max(lastFiredAlert, alert.alertTime)
            }

            state.prevEventFireFromScan = lastFiredAlert
            logger.info("scanNextEvent: prevEventFireFromScan was set to $lastFiredAlert")
        }

        // Finally - find the next nearest alert
        val nextAlert = alertsMerged.filter { !it.wasHandled && it.alertTime > fireAlertsUpTo }
                .minBy { it.alertTime }

        // TODO TODO TODO
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

        val ts0 = System.currentTimeMillis()

        val providedAlerts = alerts.associateBy { it.key }

        val ts1 = System.currentTimeMillis()

        MonitorStorage(context).use {
            db ->
            val knownAlerts = db.getAlertsForInstanceStartRange(scanFrom, scanTo).associateBy { it.key }

            val ts2 = System.currentTimeMillis()

            val newAlerts = providedAlerts - knownAlerts.keys
            val disappearedAlerts = knownAlerts - providedAlerts.keys

            val ts3 = System.currentTimeMillis()

            logger.info("filterAndMergeAlerts: ${newAlerts.size} new alerts, ${disappearedAlerts.size} disappeared alerts")

            db.deleteAlerts(disappearedAlerts.values)
            db.addAlerts(newAlerts.values)

            val ts4 = System.currentTimeMillis()

            // Presumably this would be faster than re-reading SQLite again
            ret.addAll((knownAlerts - disappearedAlerts.keys + newAlerts).values)

            val ts5 = System.currentTimeMillis()

            logger.debug("filterAndMergeAlerts: performance: ${ts1-ts0}, ${ts2-ts1}, ${ts3-ts2}, ${ts4-ts3}, ${ts5-ts4}")
        }

        return ret
    }


    companion object {
        val logger = com.github.quarck.calnotify.logs.Logger("CalendarMonitorManual")
    }
}