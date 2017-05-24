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
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.calendar.*
import com.github.quarck.calnotify.dismissedeventsstorage.EventDismissType
import com.github.quarck.calnotify.eventsstorage.*
import com.github.quarck.calnotify.logs.Logger

object CalendarReloadManager: CalendarReloadManagerInterface {

    val logger = Logger("CalendarReloadManager")

    // newInstanceStartTime/newInstanceEndTime:
    // for update with move: new instance start / end
    // for auto-dismissed: new time for event (used for dismissed storage only)

    enum class ReloadCalendarResultCode {
        NoChange,
        EventMovedShouldAutoDismiss,
        EventDetailsUpdatedShouldUpdate,
        EventInstanceMovedShouldUpdate
    }

    data class ReloadCalendarResult(
            val code: ReloadCalendarResultCode,
            val event: EventAlertRecord,
            val newInstanceStartTime: Long? = null,
            val newInstanceEndTime: Long? = null,
            val setDisplayStatusHidden: Boolean = true
    )

    fun reloadCalendarInternal(
            context: Context,
            db: EventsStorageInterface,
            events: List<EventAlertRecord>,
            calendar: CalendarProviderInterface,
            movedHandler: EventMovedHandler?
    ): Boolean {

        logger.debug("Reloading calendar")

        val currentTime = System.currentTimeMillis()

        val settings = Settings(context)

        val eventsToAutoDismiss = arrayListOf<ReloadCalendarResult>()
        val eventsToUpdate = arrayListOf<ReloadCalendarResult>()
        val eventsToUpdateWithTime = arrayListOf<ReloadCalendarResult>()

        for (event in events) {
            try {
                val reloadResult = reloadCalendarEvent(context, db, calendar, event, currentTime, movedHandler)

                when (reloadResult.code) {
                    // nothing required
                    ReloadCalendarResultCode.NoChange ->
                        Unit;

                    // Should auto-dismiss
                    ReloadCalendarResultCode.EventMovedShouldAutoDismiss ->
                        eventsToAutoDismiss.add(reloadResult)

                    // Simply update
                    ReloadCalendarResultCode.EventDetailsUpdatedShouldUpdate ->
                        eventsToUpdate.add(reloadResult)

                    // Update with moving instance time (another type of update as it involves DB key update)
                    ReloadCalendarResultCode.EventInstanceMovedShouldUpdate ->
                        eventsToUpdateWithTime.add(reloadResult)
                }

            } catch (ex: Exception) {
                logger.error("Got exception while trying to re-load event data for ${event.eventId}: ${ex.message}, ${ex.stackTrace}");
            }
        }

        var changedDetected = false

        if (!eventsToAutoDismiss.isEmpty()) {
            changedDetected = true

            ApplicationController.dismissEvents(
                    context,
                    db,
                    eventsToAutoDismiss.map { it.event },
                    EventDismissType.AutoDismissedDueToCalendarMove,
                    true
            )

            if (settings.debugNotificationAutoDismiss) {
                ApplicationController.postNotificationsAutoDismissedDebugMessage(context)
            }
        }

        if (!eventsToUpdate.isEmpty()) {
            changedDetected = true

            val newEvents =
                    eventsToUpdate.map {
                        res ->
                        if (res.setDisplayStatusHidden)
                            res.event.displayStatus = EventDisplayStatus.Hidden // so this will en-force event to be posted
                        res.event
                    }

            db.updateEvents(newEvents)
        }

        if (!eventsToUpdateWithTime.isEmpty()) {
            changedDetected = true

            val newEvents =
                    eventsToUpdateWithTime.map{
                        res ->

                        if (res.newInstanceStartTime == null || res.newInstanceEndTime == null) {
                            logger.error("ERROR[1]: if (change.newInstanceStartTime == null || change.newInstanceEndTime == null) in calendar rescan")
                            throw Exception("Internal error in CalendarReloadManager")
                        }

                        if (res.setDisplayStatusHidden)
                            res.event.displayStatus = EventDisplayStatus.Hidden // so this will en-force event to be posted

                        EventWithNewInstanceTime(res.event, res.newInstanceStartTime, res.newInstanceEndTime)
                    }

            db.updateEventsAndInstanceTimes(newEvents)
        }

        return changedDetected
    }


    override fun reloadCalendar(
            context: Context,
            db: EventsStorageInterface,
            calendar: CalendarProviderInterface,
            movedHandler: EventMovedHandler?
    ): Boolean {

        // don't rescan manually created events - we won't find most of them
        val events = db.events.filter { event -> event.origin != EventOrigin.FullManual }
        return reloadCalendarInternal(context, db, events, calendar, movedHandler)
    }
    // returns true if event has changed. Event is updated in place
    override fun reloadSingleEvent(
            context: Context,
            db: EventsStorageInterface,
            event: EventAlertRecord,
            calendar: CalendarProviderInterface,
            movedHandler: EventMovedHandler?
    ): Boolean {
        return reloadCalendarInternal(context, db, listOf(event), calendar, movedHandler)
    }

    fun reloadCalendarEvent(
        context: Context,
        db: EventsStorageInterface,
        calendarProvider: CalendarProviderInterface,
        event: EventAlertRecord,
        currentTime: Long,
        movedHandler: EventMovedHandler?
    ): ReloadCalendarResult {

        //logger.info("reloading event ${event.eventId} / ${event.instanceStartTime}")

        // check if event was moved
        if (movedHandler != null && !event.isRepeating) {

            val newEvent = calendarProvider.getEvent(context, event.eventId)

            if (newEvent != null) {
                val newAlertTime = newEvent.nextAlarmTime(currentTime)

                if (event.startTime != newEvent.startTime) {

                    logger.info("Event ${event.eventId} - move detected, ${event.startTime} != ${newEvent.startTime}")

                    val shouldAutoDismiss = movedHandler.checkShouldRemoveMovedEvent(context, event, newEvent, newAlertTime)

                    // a bit ugly here with all these multiple-returns
                    if (shouldAutoDismiss) {
                        return ReloadCalendarResult(
                                ReloadCalendarResultCode.EventMovedShouldAutoDismiss,
                                event.copy(startTime = newEvent.startTime, endTime = newEvent.endTime)
                        )
                    }
                }
            }
        }

        val newEventInstance = calendarProvider.getAlertByEventIdAndTime(context, event.eventId, event.alertTime)
        if (newEventInstance != null)
            return checkCalendarEventHasChanged(event, newEventInstance)

        return reloadCalendarEventInstanceNotFound(context, db, calendarProvider, event, currentTime)
    }

    fun checkCalendarEventHasChanged(
            event: EventAlertRecord,
            newEventAlert: EventAlertRecord
    ): ReloadCalendarResult {

//        logger.debug("event ${event.eventId} / ${event.instanceStartTime} - instance found")

        // Things to remember:
        // multiple instances for same eventId
        // reload 'isRepeating' (for upgrade)

        if (!newEventAlert.isRepeating) {

            if (event.updateFrom(newEventAlert)) {

                if (event.instanceStartTime == newEventAlert.instanceStartTime) {
                    logger.info("Non-repeating event ${event.eventId} / ${event.instanceStartTime} was updated");

                    return ReloadCalendarResult(
                            ReloadCalendarResultCode.EventDetailsUpdatedShouldUpdate,
                            event
                    )

//                    db.updateEvent(
//                        event,
//                        displayStatus = EventDisplayStatus.Hidden) // so this will en-force event to be posted
                } else {
                    logger.info("Non-repeating event ${event.eventId} / ${event.instanceStartTime} was updated, new instance start ${newEventAlert.instanceStartTime} -- event was moved");

                    return ReloadCalendarResult(
                            ReloadCalendarResultCode.EventInstanceMovedShouldUpdate,
                            event,
                            newEventAlert.instanceStartTime,
                            newEventAlert.instanceEndTime
                    )

//                    db.updateEventAndInstanceTimes(
//                            event.copy(displayStatus = EventDisplayStatus.Hidden), // so this will en-force event to be posted
//                            instanceStart = newEventAlert.instanceStartTime,
//                            instanceEnd = newEventAlert.instanceEndTime)
                }

            } /*else {
                logger.info("Non-repeating event ${event.eventId} / ${event.instanceStartTime} hasn't changed");
            }*/

        } else {
            if (event.updateFrom(newEventAlert)) {
                // ignore updated instance times for repeating events - they are unpredictable
                logger.info("Repeating event ${event.eventId} / ${event.instanceStartTime} was updated");

                return ReloadCalendarResult(
                        ReloadCalendarResultCode.EventDetailsUpdatedShouldUpdate,
                        event
                )

//                db.updateEvent(
//                    event,
//                    displayStatus = EventDisplayStatus.Hidden) // so this will en-force event to be posted


            } /*else {
                logger.info("Repeating event ${event.eventId} / ${event.instanceStartTime} hasn't changed");
            }*/
        }

        return ReloadCalendarResult(
                ReloadCalendarResultCode.NoChange,
                event
        )
    }

    fun reloadCalendarEventInstanceNotFound(
        context: Context, db: EventsStorageInterface,
        calendar: CalendarProviderInterface,
        event: EventAlertRecord,
        currentTime: Long
    ): ReloadCalendarResult {

        logger.debug("event ${event.eventId} / ${event.instanceStartTime} - instance NOT found")

        if (event.isRepeating) {
            // This is repeating event -- can't do anything, we can't match new instances to the current one
            logger.info("Repeating event ${event.eventId} instance ${event.instanceStartTime} disappeared");
            // can't do anything about it - just ignore, assuming no change
            return ReloadCalendarResult(ReloadCalendarResultCode.NoChange, event)
        }

        val instances = calendar.getEventAlerts(context, event.eventId, event.alertTime, 2)
        if (instances.size == 1) {
            logger.info("Non-repeating event ${event.eventId} was found at new alert time ${instances[0].alertTime}, instance start ${instances[0].instanceStartTime}");
            return checkCalendarEventHasChanged(event, instances[0])
        }

        logger.info("No instances of event ${event.eventId} were found in the future")

        var changesDetected = false

        // Try loading at least basic params from "event"
        val newEvent = calendar.getEvent(context, event.eventId)
        if (newEvent == null) {
            // Here we can't confirm that event was moved into the future.
            // Perhaps it was removed, but this is not what users usually do.
            // Leave it for user to remove the notification
            logger.info("Event ${event.eventId} disappeared completely (Known instance ${event.instanceStartTime})");
            // can't do anything about it - just ignore, assuming no change
            return ReloadCalendarResult(ReloadCalendarResultCode.NoChange, event)
        }

        val newAlertTime = newEvent.nextAlarmTime(currentTime)

        if (event.startTime != newEvent.startTime) {
            logger.debug("Event ${event.eventId} - move detected, ${event.startTime} != ${newEvent.startTime}")
        }

        if (event.updateFrom(newEvent) || event.alertTime != newAlertTime) {
            logger.debug("Event ${event.eventId} for lost instance ${event.instanceStartTime} was updated, new start time ${newEvent.startTime}, alert time ${event.alertTime} -> $newAlertTime");
            event.alertTime = newAlertTime
            event.displayStatus = EventDisplayStatus.Hidden

            return ReloadCalendarResult(
                    ReloadCalendarResultCode.EventInstanceMovedShouldUpdate,
                    event,
                    newInstanceStartTime = newEvent.startTime,
                    newInstanceEndTime = newEvent.endTime,
                    setDisplayStatusHidden = false
            )
        } else {
            logger.info("Event instance ${event.eventId} / ${event.instanceStartTime} disappeared, actual event is still exactly the same");
        }

        return ReloadCalendarResult(ReloadCalendarResultCode.NoChange, event)
    }
}