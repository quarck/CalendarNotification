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
import com.github.quarck.calnotify.calendar.*
import com.github.quarck.calnotify.monitorstorage.MonitorStorage
import java.util.*


//TODO: need manual rescan timer, to re-scan events fully every few hours (preferably when on battery)

class CalendarMonitorManual(
        val calendarProvider: CalendarProviderInterface,
        val calendarMonitor: CalendarMonitorInterface
) {

    private fun manualFireAlertList_NoHousekeeping(context: Context, alerts: List<MonitorEventAlertEntry>): Boolean {

        var fired = false

        val firedAlerts = registerFiredEventsInDB(context, alerts)

        if (!firedAlerts.isEmpty()) {

            fired = true

            try {
                ApplicationController.postEventNotifications(context, firedAlerts.map { it.second })
            }
            catch (ex: Exception) {
                logger.error("Got exception while posting notifications: $ex, ${ex.stackTrace}")
            }

            markAlertsAsHandledInDB(context, firedAlerts.map { it.first })

            val lastFiredAlert = firedAlerts.map { (alert, _) -> alert.alertTime }.max() ?: 0L

            val state = CalendarMonitorState(context)
            if (state.prevEventFireFromScan < lastFiredAlert)
                state.prevEventFireFromScan = lastFiredAlert

            logger.info("${firedAlerts.size} events were marked in DB as handled, not dismissing original " +
                    "reminder for manual fired alerts - provider didn't know about these alerts (otherwise we would not be here)" +
                    ", prevEventFireFromScan was set to $lastFiredAlert")
        }

        return fired
    }

    // should return true if we have fired at new events, so UI should reload if it is open
    fun manualFireEventsAt_NoHousekeeping(context: Context, nextEventFire: Long, prevEventFire: Long? = null): Boolean {

        var alerts =
                MonitorStorage(context).use {
                    db ->
                    if (prevEventFire == null)
                        db.getAlertsAt(nextEventFire)
                    else
                        db.getAlertsForAlertRange(prevEventFire, nextEventFire)
                }

        alerts = alerts
                .filter { !it.wasHandled }
                .sortedBy { it.alertTime }

        logger.info("manualFireEventsAt: got ${alerts.size} alerts to fire at");

        return manualFireAlertList_NoHousekeeping(context, alerts)
    }

//    private fun registerFiredEventInDB(context: Context, alert: MonitorEventAlertEntry): EventAlertRecord? {
//
//        var ret: EventAlertRecord? = null
//
//        if (alert.wasHandled)
//            return null
//
//        logger.info("manualFireAlert: firing at alert $alert")
//
//        var event: EventAlertRecord? = null
//
//        if (!alert.alertCreatedByUs) {
//            // not manually created -- can read directly from the provider!
//            logger.debug("Alert was not created by the app, so trying to read alert off the provider")
//            event = calendarProvider.getAlertByEventIdAndTime(context, alert.eventId, alert.alertTime)
//        }
//
//        if (event == null) {
//            logger.debug("Still has no alert info from provider - reading event off the provider")
//
//            val calEvent = calendarProvider.getEvent(context, alert.eventId)
//            if (calEvent != null) {
//                event = EventAlertRecord(
//                        calendarId = calEvent.calendarId,
//                        eventId = calEvent.eventId,
//                        isAllDay = calEvent.isAllDay,
//                        isRepeating = calendarProvider.isRepeatingEvent(context, alert.eventId) ?: false,
//                        alertTime = alert.alertTime,
//                        notificationId = 0,
//                        title = calEvent.title,
//                        startTime = calEvent.startTime,
//                        endTime = calEvent.endTime,
//                        instanceStartTime = alert.instanceStartTime,
//                        instanceEndTime = alert.instanceEndTime,
//                        location = calEvent.location,
//                        lastEventVisibility = 0,
//                        snoozedUntil = 0
//                )
//            }
//        }
//
//        if (event != null) {
//
//            logger.info("Full manual scan: seen event ${event.eventId} / ${event.instanceStartTime} / ${event.alertTime}")
//
//            // found an event
//            event.origin = EventOrigin.FullManual
//            event.timeFirstSeen = System.currentTimeMillis()
//
//            if (ApplicationController.registerNewEvent(context, event))
//                ret = event
//
//        } else {
//            logger.error("Alert: $alert, cant find neither alert nor event. Marking as handled and ignoring.")
//            // all attempts failed - still, markt it as handled, so avoid repeated attempts all over again
//            markAlertsAsHandledInDB(context, listOf(alert))
//        }
//
//        return ret
//    }

    private fun registerFiredEventsInDB(
            context: Context,
            alerts: Collection<MonitorEventAlertEntry>
    ): List<Pair<MonitorEventAlertEntry, EventAlertRecord>> {

        val pairs = arrayListOf<Pair<MonitorEventAlertEntry, EventAlertRecord>>()

        var numAlertsFound = 0
        var numAlertsNotFound = 0
        var numErrors = 0

        for (alert in alerts) {
            if (alert.wasHandled)
                continue

            logger.info("registerFiredEventsInDB: processing at alert $alert")

            var event: EventAlertRecord? = null

            if (!alert.alertCreatedByUs) {
                // not manually created -- can read directly from the provider!
                logger.debug("Alert was not created by the app, so trying to read alert off the provider")
                event = calendarProvider.getAlertByEventIdAndTime(context, alert.eventId, alert.alertTime)

                if (event != null)
                    numAlertsFound ++
                else
                    numAlertsNotFound ++
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
                            color = calEvent.color,
                            lastEventVisibility = 0,
                            snoozedUntil = 0
                    )
                }
            }

            if (event != null) {
                event.origin = EventOrigin.FullManual
                event.timeFirstSeen = System.currentTimeMillis()

                pairs.add(Pair(alert, event))
            } else {
                logger.error("Alert: $alert, cant find neither alert nor event. Marking as handled and ignoring.")
                // all attempts failed - still, markt it as handled, so avoid repeated attempts all over again
                markAlertsAsHandledInDB(context, listOf(alert))
                numErrors ++
            }
        }

        logger.info("Got ${pairs.size} pairs, num found alerts: $numAlertsFound, not found: $numAlertsNotFound, errors: $numErrors")

        return ApplicationController.registerNewEvents(context, pairs)
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

    fun scanNextEvent_NoHousekeping(context: Context, state: CalendarMonitorState): Pair<Long, Boolean> {

        // TODO: Dismiss all while scan is in progress????

//        MonitorStorage(context).use { it.deleteAlertsMatching { _ -> true } } ; state.firstScanEver = false
//        state.prevEventScanTo = System.currentTimeMillis() - 145L*24L*3600L*1000L // YAHOOOOO!!!!

        var hasFiredAnything = false

        val settings = Settings(context)

        val scanWindow = settings.manualCalWatchScanWindow

        val prevScanTo = state.prevEventScanTo
        val currentTime = System.currentTimeMillis()

        var scanFrom = Math.min(
                currentTime - Consts.ALERTS_DB_REMOVE_AFTER, // look backwards a little to make sure nothing is missing
                prevScanTo // we we didn't scan for long time - do a full re-scan since last 'scanned to'
                )

        // cap scan from range to 1 month back only
        val monthAgo = currentTime - Consts.MAX_SCAN_BACKWARD_DAYS * Consts.DAY_IN_MILLISECONDS
        if (scanFrom < monthAgo) {
            logger.info("scan from capped from $scanFrom to $monthAgo")
            scanFrom = monthAgo
        }

        val scanTo = currentTime + scanWindow

        val firstScanEver = state.firstScanEver

        state.prevEventScanTo = currentTime + scanWindow

        val alerts =
                calendarProvider.getEventAlertsForInstancesInRange(context, scanFrom, scanTo)

        val alertsMerged = filterAndMergeAlerts(context, alerts, scanFrom, scanTo)
                .sortedBy { it.alertTime }

        logger.info("scanNextEvent: scan range: $scanFrom, $scanTo (${Date(scanFrom)} - ${Date(scanTo)})," +
                " got ${alerts.size} events off the provider, merged count: ${alertsMerged.size}")

        // now we only need to simply fire at all missed events,
        // and pick the nearest future event,
        // don't forget to update state

        val fireAlertsUpTo = currentTime + Consts.ALARM_THRESHOLD

        var dueAlerts = alertsMerged.filter { !it.wasHandled && it.alertTime <= fireAlertsUpTo }

        if (firstScanEver) {
            state.firstScanEver = false
            logger.info("This is a first deep scan ever, not posting 'due' events as these are reminders for past events")
            markAlertsAsHandledInDB(context, dueAlerts )

        } else {
            logger.info("scanNextEvent: ${dueAlerts.size} due alerts (we should have fired already!!!)")

            var special: EventAlertRecord? = null

            if (dueAlerts.size > Consts.MAX_DUE_ALERTS_FOR_MANUAL_SCAN) {

                special = CreateEventAlertSpecialScanOverHundredEvents(
                        context,
                        dueAlerts.size - Consts.MAX_DUE_ALERTS_FOR_MANUAL_SCAN
                )

                dueAlerts = dueAlerts
                        .sortedBy { it.instanceStartTime }
                        .takeLast(Consts.MAX_DUE_ALERTS_FOR_MANUAL_SCAN)
            }

            if (manualFireAlertList_NoHousekeeping(context, dueAlerts))
                hasFiredAnything = true

            if (special != null)
                ApplicationController.registerNewEvent(context, special) // not posting it in the notifications
        }

        // Finally - find the next nearest alert
        val nextAlert = alertsMerged.filter { !it.wasHandled && it.alertTime > fireAlertsUpTo }
                .minBy { it.alertTime }

        // TODO TODO TODO First - need settings for this
        // TODO TODO TODO
        // TODO TODO TODO
        // TODO TODO TODO
        // TODO TODO TODO
        // TODO TODO TODO
        // TODO TODO TODO
        // TODO: below: -- calendar provider handles most of it, we need to
        // make sure we re-scan on setting change, to update alert times.
        // most likely we are re-scanning on onResume, then just make sure!!
        val shouldRemindForEventsWithNoReminders = settings.shouldRemindForEventsWithNoReminders


        val nextAlertTime = nextAlert?.alertTime ?: Long.MAX_VALUE
        state.nextEventFireFromScan = nextAlertTime

        logger.info("scanNextEvent: next alert $nextAlertTime");

        // Very finally - delete events that we are no longer interested in:
        // * events that were handled already
        // * and old enough (before this iteration's 'scanFrom'
        MonitorStorage(context).use {
            it.deleteAlertsMatching {
                alert ->
                    alert.instanceStartTime < scanFrom && alert.wasHandled
            }
        }

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