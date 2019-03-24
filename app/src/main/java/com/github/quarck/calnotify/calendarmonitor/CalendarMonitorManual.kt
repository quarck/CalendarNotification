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
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.monitorstorage.MonitorStorage
import com.github.quarck.calnotify.monitorstorage.WasHandledCache
import com.github.quarck.calnotify.permissions.PermissionsManager
import com.github.quarck.calnotify.utils.detailed
import java.util.*


class CalendarMonitorManual(
        val calendarProvider: CalendarProviderInterface,
        val calendarMonitor: CalendarMonitorInterface
) {

    private fun manualFireAlertList(context: Context, alerts: List<MonitorEventAlertEntry>): Boolean {

        var fired = false

        val firedAlerts = registerFiredEventsInDB(context, alerts)

        if (!firedAlerts.isEmpty()) {

            fired = true

            try {
                ApplicationController.postEventNotifications(context, firedAlerts.map { it.second })
            }
            catch (ex: Exception) {
                DevLog.error(LOG_TAG, "Got exception while posting notifications: ${ex.detailed}")
            }

            markAlertsAsHandledInDB(context, firedAlerts.map { it.first })

            val lastFiredAlert = firedAlerts.map { (alert, _) -> alert.alertTime }.max() ?: 0L

            val state = CalendarMonitorState(context)
            if (state.prevEventFireFromScan < lastFiredAlert)
                state.prevEventFireFromScan = lastFiredAlert

            DevLog.info(LOG_TAG, "${firedAlerts.size} requests were marked in DB as handled, prevEventFireFromScan was set to $lastFiredAlert")
        }

        return fired
    }

    // should return true if we have fired at new requests, so UI should reload if it is open
    fun manualFireEventsAt_NoHousekeeping(context: Context, nextEventFire: Long, prevEventFire: Long? = null): Boolean {

        if (!PermissionsManager.hasAllCalendarPermissions(context)) {
            DevLog.error(LOG_TAG, "manualFireEventsAt_NoHousekeeping: no permissions");
            return false
        }

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

        DevLog.info(LOG_TAG, "manualFireEventsAt: got ${alerts.size} alerts to fire at");

        return manualFireAlertList(context, alerts)
    }

    private fun registerFiredEventsInDB(
            context: Context,
            alerts: Collection<MonitorEventAlertEntry>
    ): List<Pair<MonitorEventAlertEntry, EventAlertRecord>> {

        val pairs = arrayListOf<Pair<MonitorEventAlertEntry, EventAlertRecord>>()

        var numAlertsFound = 0
        var numAlertsNotFound = 0
        var numErrors = 0

        WasHandledCache(context).use { wasHandledCache ->

            for (alert in alerts) {
                if (alert.wasHandled)
                    continue

                DevLog.info(LOG_TAG, "registerFiredEventsInDB: $alert")

                var event: EventAlertRecord? = null

                if (!alert.alertCreatedByUs) {
                    // not manually created -- can read directly from the provider!
                    DevLog.info(LOG_TAG, "Alert was not created by the app, so trying to read alert off the provider")
                    event = calendarProvider.getAlertByEventIdAndTime(context, alert.eventId, alert.alertTime)

                    if (event != null)
                        numAlertsFound++
                    else
                        numAlertsNotFound++
                }

                if (event == null) {
                    DevLog.warn(LOG_TAG, "Alert not found - reading event by ID for details")

                    val calEvent = calendarProvider.getEvent(context, alert.eventId)
                    if (calEvent != null) {
                        event = EventAlertRecord(
                                calendarId = calEvent.calendarId,
                                eventId = calEvent.eventId,
                                isAllDay = calEvent.isAllDay,
                                isRepeating = calendarProvider.isRepeatingEvent(context, alert.eventId)
                                        ?: false,
                                alertTime = alert.alertTime,
                                notificationId = 0,
                                title = calEvent.title,
                                desc = calEvent.desc,
                                startTime = calEvent.startTime,
                                endTime = calEvent.endTime,
                                instanceStartTime = alert.instanceStartTime,
                                instanceEndTime = alert.instanceEndTime,
                                location = calEvent.location,
                                color = calEvent.color,
                                lastStatusChangeTime = 0,
                                snoozedUntil = 0
                        )
                    }
                }

                if (event != null) {
                    event.origin = EventOrigin.FullManual
                    event.timeFirstSeen = System.currentTimeMillis()

                    if (!wasHandledCache.getAlertWasHandled(event)) {
                        pairs.add(Pair(alert, event))
                    }
                } else {
                    DevLog.error(LOG_TAG, "Alert: $alert, cant find neither alert nor event. Marking as handled and ignoring.")
                    // all attempts failed - still, markt it as handled, so avoid repeated attempts all over again
                    markAlertsAsHandledInDB(context, listOf(alert))
                    numErrors++
                }
            }

            if (numAlertsNotFound != 0 || numErrors != 0)
                DevLog.info(LOG_TAG, "Got ${pairs.size} pairs, num found alerts: $numAlertsFound, not found: $numAlertsNotFound, errors: $numErrors")

            val pairsToAdd = pairs.filter { (_, event) ->
                !ApplicationController.shouldMarkEventAsHandledAndSkip(context, event)
            }

            return ApplicationController.registerNewEvents(context, wasHandledCache, pairsToAdd)
        }
    }


    private fun markAlertsAsHandledInDB(context: Context, alerts: Collection<MonitorEventAlertEntry>) {
        MonitorStorage(context).use {
            db ->
            DevLog.info(LOG_TAG, "marking ${alerts.size} alerts as handled in the manual alerts DB");

            for (alert in alerts)
                alert.wasHandled = true

            db.updateAlerts(alerts)
        }
    }

    fun scanForSingleEvent(context: Context, event: EventRecord): Boolean {

        if (!PermissionsManager.hasAllCalendarPermissions(context)) {
            DevLog.error(LOG_TAG, "scanForSingleEvent: no permissions");
            return false
        }

        var hasFiredAnything = false

        val currentTime = System.currentTimeMillis()

        val alerts = calendarProvider.getEventAlertsForEvent(context, event).associateBy { it.key }

        val alertsToVerify = mutableListOf<MonitorEventAlertEntry>()

        var numUpdatedAlerts = 0
        var numAddedAlerts = 0

        MonitorStorage(context).use {
            db ->
            val knownAlerts = db.getInstanceAlerts(event.eventId, event.startTime).associateBy { it.key }

            // Add new alerts into DB
            for ((key, alert) in alerts) {

                val knownAlert = knownAlerts.get(key)

                if (knownAlert == null) {
                    alertsToVerify.add(alert)
                    db.addAlert(alert)
                    ++numAddedAlerts
                }
                else if (knownAlert.detailsChanged(alert)) {
                    alertsToVerify.add(alert)
                    db.updateAlert(alert)
                    ++numUpdatedAlerts
                }
                else if (!knownAlert.wasHandled) {
                    alertsToVerify.add(knownAlert)
                }
            }
        }

        DevLog.info(LOG_TAG, "scanForSingleEvent: ${event.eventId}, num alerts: ${alerts.size}," +
                " new alerts: $numAddedAlerts, updated alerts: $numUpdatedAlerts")

        // Check what we should have already fired at

        val fireAlertsUpTo = currentTime + Consts.ALARM_THRESHOLD

        val dueAlerts = alertsToVerify.filter { !it.wasHandled && it.alertTime <= fireAlertsUpTo }

        if (dueAlerts.isNotEmpty()) {
            DevLog.warn(LOG_TAG, "scanForSingleEvent: ${dueAlerts.size} due alerts - nearly missed these")
            hasFiredAnything = manualFireAlertList(context, dueAlerts)
        }

        return hasFiredAnything
    }

    fun scanNextEvent(context: Context, state: CalendarMonitorState): Pair<Long, Boolean> {

        if (!PermissionsManager.hasAllCalendarPermissions(context)) {
            DevLog.error(LOG_TAG, "scanNextEvent: no permissions");
            return Pair(Long.MAX_VALUE, false)
        }

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
            DevLog.info(LOG_TAG, "scan from capped from $scanFrom to $monthAgo")
            scanFrom = monthAgo
        }

        val scanTo = currentTime + scanWindow

        val firstScanEver = state.firstScanEver

        state.prevEventScanTo = currentTime + scanWindow

        val alerts =
                calendarProvider.getEventAlertsForInstancesInRange(context, scanFrom, scanTo)

        val alertsMerged = filterAndMergeAlerts(context, alerts, scanFrom, scanTo)
                .sortedBy { it.alertTime }

        DevLog.info(LOG_TAG, "scanNextEvent: scan range: $scanFrom, $scanTo (${Date(scanFrom)} - ${Date(scanTo)})," +
                " got ${alerts.size} requests off the provider, merged count: ${alertsMerged.size}")

        // now we only need to simply fire at all missed requests,
        // and pick the nearest future event,
        // don't forget to update state

        val fireAlertsUpTo = currentTime + Consts.ALARM_THRESHOLD

        var dueAlerts = alertsMerged.filter { !it.wasHandled && it.alertTime <= fireAlertsUpTo }

        if (firstScanEver) {
            state.firstScanEver = false
            DevLog.info(LOG_TAG, "This is a first deep scan ever, not posting 'due' requests")
            markAlertsAsHandledInDB(context, dueAlerts)

        }
        else if (dueAlerts.isNotEmpty()) {
            DevLog.warn(LOG_TAG, "scanNextEvent: ${dueAlerts.size} due alerts - nearly missed these")

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

            if (manualFireAlertList(context, dueAlerts))
                hasFiredAnything = true

            if (special != null)
                ApplicationController.registerNewEvent(context, special) // not posting it in the notifications
        }

        // Finally - find the next nearest alert
        val nextAlert = alertsMerged.filter { !it.wasHandled && it.alertTime > fireAlertsUpTo }
                .minBy { it.alertTime }

        val nextAlertTime = nextAlert?.alertTime ?: Long.MAX_VALUE
        state.nextEventFireFromScan = nextAlertTime

        DevLog.info(LOG_TAG, "scanNextEvent: next alert $nextAlertTime");

        // Very finally - delete requests that we are no longer interested in:
        // * requests that were handled already
        // * and old enough (before this iteration's 'scanFrom'
        MonitorStorage(context).use {
            it.deleteAlertsMatching {
                alert ->
                alert.instanceStartTime < scanFrom && alert.wasHandled
            }
        }

        return Pair(nextAlertTime, hasFiredAnything)
    }

    private fun filterAndMergeAlerts(context: Context, alerts: List<MonitorEventAlertEntry>, scanFrom: Long, scanTo: Long): List<MonitorEventAlertEntry> {

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

            DevLog.info(LOG_TAG, "filterAndMergeAlerts: ${newAlerts.size} new alerts, ${disappearedAlerts.size} disappeared alerts")

            db.deleteAlerts(disappearedAlerts.values)
            db.addAlerts(newAlerts.values)

            val ts4 = System.currentTimeMillis()

            // Presumably this would be faster than re-reading SQLite again
            ret.addAll((knownAlerts - disappearedAlerts.keys + newAlerts).values)

            val ts5 = System.currentTimeMillis()

            DevLog.debug(LOG_TAG, "filterAndMergeAlerts: performance: ${ts1 - ts0}, ${ts2 - ts1}, ${ts3 - ts2}, ${ts4 - ts3}, ${ts5 - ts4}")
        }

        return ret
    }


    companion object {
        private const val LOG_TAG = "CalendarMonitorManual"
    }
}