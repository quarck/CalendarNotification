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

package com.github.quarck.calnotify.app

import android.content.Context
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.calendar.*
import com.github.quarck.calnotify.logs.Logger
import com.github.quarck.calnotify.manualalertsstorage.ManualAlertsStorage

class CalendarManualMonitor(val calendarProvider: CalendarProviderInterface): CalendarManualMonitorInterface {

    override fun getNextAlertTime(context: Context, since: Long): Long? {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getAlertsAt(context: Context, time: Long, mayRescan: Boolean): List<ManualEventAlertEntry> {

        if (mayRescan) {
            performManualRescan(context)
        }

        val ret = ManualAlertsStorage(context).use {
            db -> db.getAlertsAt(time)
        }

        return ret
    }

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
        return db.getAlert(ev.eventId, ev.alertTime, ev.instanceStartTime)?.wasHandled ?: false
    }

    override fun getAlertWasHandled(context: Context, ev: EventAlertRecord): Boolean {
        return ManualAlertsStorage(context).use { db -> getAlertWasHandled(db, ev) }
    }

    override fun performManualRescan(context: Context) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun scheduleNextManualAlert(context: Context, alertsSince: Long) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    companion object {
        val logger = Logger("CalendarManualMonitor")
    }
}