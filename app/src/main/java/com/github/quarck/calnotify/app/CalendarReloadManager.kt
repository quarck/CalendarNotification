//
//   Calendar Notifications Plus
//   Copyright (C) 2016 Sergey Parshin (s.parshin.sc@gmail.com)
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
import com.github.quarck.calnotify.calendar.*
import com.github.quarck.calnotify.eventsstorage.*
import com.github.quarck.calnotify.logs.Logger

object CalendarReloadManager: CalendarReloadManagerInterface {

    val logger = Logger("CalendarReloadManager")

    override fun reloadCalendar(context: Context, db: EventsStorageInterface, calendar: CalendarProviderInterface): Boolean {

        logger.debug("Reloading calendar")

        var rePostNotifications = false

        val currentTime = System.currentTimeMillis()

        for (event in db.events) {
            try {
                rePostNotifications =  rePostNotifications ||
                    reloadCalendarEvent(context, db, calendar, event, currentTime)

            } catch (ex: Exception) {
                logger.error("Got exception while trying to re-load event data for ${event.eventId}: ${ex.message}, ${ex.stackTrace}");
            }
        }

        return rePostNotifications
    }

    fun reloadCalendarEvent(
        context: Context, db: EventsStorageInterface,
        calendarProvider: CalendarProviderInterface,
        event: EventAlertRecord, currentTime: Long): Boolean {

        logger.debug("reloading event ${event.eventId} / ${event.instanceStartTime}")

        val newEventInstance = calendarProvider.getAlertByEventIdAndTime(context, event.eventId, event.alertTime)

        val rePostNotifications =
            if (newEventInstance != null )
                reloadCalendarEventInstanceFound(context, db, calendarProvider, event, newEventInstance, currentTime)
            else
                reloadCalendarEventInstanceNotFound(context, db, calendarProvider, event, currentTime)

        return rePostNotifications
    }

    fun reloadCalendarEventInstanceFound(
        context: Context, db: EventsStorageInterface,
        calendar: CalendarProviderInterface,
        event: EventAlertRecord, newEventAlert: EventAlertRecord,
        currentTime: Long): Boolean {

        logger.debug("event ${event.eventId} / ${event.instanceStartTime} - instance found")

        var rePostNotifications = false

        // Things to remember:
        // multiple instances for same eventId
        // reload 'isRepeating' (for upgrade)

        if (!newEventAlert.isRepeating) {

            if (event.updateFrom(newEventAlert)) {
                if (event.instanceStartTime == newEventAlert.instanceStartTime) {

                    logger.debug("Non-repeating event ${event.eventId} / ${event.instanceStartTime} was updated");

                    db.updateEvent(
                        event,
                        displayStatus = EventDisplayStatus.Hidden) // so this will en-force event to be posted
                } else {

                    logger.debug("Non-repeating event ${event.eventId} / ${event.instanceStartTime} was updated, new instance start ${newEventAlert.instanceStartTime}");

                    db.updateEventAndInstanceTimes(
                        event.copy(displayStatus = EventDisplayStatus.Hidden), // so this will en-force event to be posted
                        instanceStart = newEventAlert.instanceStartTime,
                        instanceEnd = newEventAlert.instanceEndTime)
                }

                rePostNotifications = true

            } else {
                logger.debug("Non-repeating event ${event.eventId} / ${event.instanceStartTime} hasn't changed");
            }

        } else {
            if (event.updateFrom(newEventAlert)) {
                // ignore updated instance times for repeating events - they are unpredictable
                logger.debug("Repeating event ${event.eventId} / ${event.instanceStartTime} was updated");

                db.updateEvent(
                    event,
                    displayStatus = EventDisplayStatus.Hidden) // so this will en-force event to be posted

                rePostNotifications = true

            } else {
                logger.debug("Repeating event ${event.eventId} / ${event.instanceStartTime} hasn't changed");
            }
        }

        return rePostNotifications
    }

    fun reloadCalendarEventInstanceNotFound(
        context: Context, db: EventsStorageInterface,
        calendar: CalendarProviderInterface,
        event: EventAlertRecord,
        currentTime: Long): Boolean {

        logger.debug("event ${event.eventId} / ${event.instanceStartTime} - instance NOT found")

        var rePostNotifications = false

        if (!event.isRepeating) {

            val instances = calendar.getEventAlerts(context, event.eventId, event.alertTime, 2)

            if (instances.size == 1) {
                logger.debug("Non-repeating event ${event.eventId} was found at new alert time ${instances[0].alertTime}, instance start ${instances[0].instanceStartTime}");
                rePostNotifications = reloadCalendarEventInstanceFound(context, db, calendar, event, instances[0], currentTime)
            } else {
                logger.debug("No instances of event ${event.eventId} were found in the future")

                // Try loading at least basic params from "event"
                val newEvent = calendar.getEvent(context, event.eventId)
                if (newEvent != null) {
                    val newAlertTime = newEvent.nextAlarmTime(currentTime)
                    if (event.updateFrom(newEvent) || event.alertTime != newAlertTime) {
                        logger.debug("Event ${event.eventId} for lost instance ${event.instanceStartTime} was updated, new start time ${newEvent.startTime}, alert time ${event.alertTime} -> $newAlertTime");
                        event.alertTime = newAlertTime
                        event.displayStatus = EventDisplayStatus.Hidden
                        db.updateEventAndInstanceTimes(event, newEvent.startTime, newEvent.endTime)
                        rePostNotifications = true
                    } else {
                        logger.debug("Event instance ${event.eventId} / ${event.instanceStartTime} disappeared, actual event is still exactly the same");
                    }
                } else {
                    // Here we can't confirm that event was moved into the future.
                    // Perhaps it was removed, but this is not what users usually do.
                    // Leave it for user to remove the notification
                    logger.debug("Event ${event.eventId} disappeared completely (Known instance ${event.instanceStartTime})");
                }
            }
        } else {
            // This is repeating event -- can't do anything, we can't match new instances to the current one
            logger.debug("Repeating event ${event.eventId} instance ${event.instanceStartTime} disappeared");
        }

       return rePostNotifications;
    }
}