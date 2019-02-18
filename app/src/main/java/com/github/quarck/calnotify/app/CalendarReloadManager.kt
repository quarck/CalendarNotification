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
import com.github.quarck.calnotify.dismissedeventsstorage.EventDismissType
import com.github.quarck.calnotify.eventsstorage.EventWithNewInstanceTime
import com.github.quarck.calnotify.eventsstorage.EventsStorageInterface
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.utils.detailed

object CalendarReloadManager : CalendarReloadManagerInterface {

    private const val LOG_TAG = "CalendarReloadManager"

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

    private fun reloadCalendarInternal(
            context: Context,
            db: EventsStorageInterface,
            events: List<EventAlertRecord>,
            calendar: CalendarProviderInterface,
            movedHandler: EventMovedHandler?
    ): Boolean {

        DevLog.debug(LOG_TAG, "Reloading calendar")

        val currentTime = System.currentTimeMillis()

        //val settings = Settings(context)

        val eventsToAutoDismiss = arrayListOf<ReloadCalendarResult>()
        val eventsToUpdate = arrayListOf<ReloadCalendarResult>()
        val eventsToUpdateWithTime = arrayListOf<ReloadCalendarResult>()

        for (event in events) {

            try {
                val reloadResult = reloadCalendarEventAlert(context, calendar, event, currentTime, movedHandler)

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

            }
            catch (ex: Exception) {
                DevLog.error(LOG_TAG, "Got exception while trying to re-load event data for ${event.eventId}: ${ex.detailed}");
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

            db.updateEvents(newEvents) // nothing major would happen if this fails - just ignore
        }

        if (!eventsToUpdateWithTime.isEmpty()) {
            changedDetected = true

            val newEvents =
                    eventsToUpdateWithTime.map {
                        res ->

                        if (res.newInstanceStartTime == null || res.newInstanceEndTime == null) {
                            DevLog.error(LOG_TAG, "ERROR[1]: if (change.newInstanceStartTime == null || change.newInstanceEndTime == null) in calendar rescan")
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
        // don't rescan manually created requests - we won't find most of them
        val events = db.events.filter { event -> event.origin != EventOrigin.FullManual && event.isNotSpecial }
        return reloadCalendarInternal(context, db, events, calendar, movedHandler)
    }

    override fun rescanForRescheduledEvents(
            context: Context,
            db: EventsStorageInterface,
            calendar: CalendarProviderInterface,
            movedHandler: EventMovedHandler
    ): Boolean {

        //val settings = Settings(context)

        val events = db.events.filter {
            event ->
            event.origin != EventOrigin.FullManual
                    && event.isNotSpecial
                    && event.snoozedUntil != 0L
                    && !event.isRepeating
        }

        val currentTime = System.currentTimeMillis()

        var autoDismissEvents = mutableListOf<EventAlertRecord>()

        for (event in events) {

            val newEvent = calendar.getEvent(context, event.eventId)

            if (newEvent == null)
                continue

            val newAlertTime = newEvent.nextAlarmTime(currentTime)

            if (event.startTime != newEvent.startTime) {

                DevLog.info(LOG_TAG, "Event ${event.eventId} - move detected, ${event.startTime} != ${newEvent.startTime}")

                val shouldAutoDismiss = movedHandler.checkShouldRemoveMovedEvent(context, event, newEvent, newAlertTime)
                if (shouldAutoDismiss)
                    autoDismissEvents.add(event)
            }
        }

        var changedDetected = false

        if (!autoDismissEvents.isEmpty()) {
            changedDetected = true

            ApplicationController.dismissEvents(
                    context,
                    db,
                    autoDismissEvents,
                    EventDismissType.AutoDismissedDueToCalendarMove,
                    true
            )

        }

        return changedDetected
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

    fun reloadCalendarEventAlert(
            context: Context,
            calendarProvider: CalendarProviderInterface,
            event: EventAlertRecord,
            currentTime: Long,
            movedHandler: EventMovedHandler?
    ): ReloadCalendarResult {

        // Quick short-cut for non-repeating requests: quickly check if instance time is different now
        // - can't use the same for repeating requests
        if (movedHandler != null &&
                !event.isRepeating) {

            val newEvent = calendarProvider.getEvent(context, event.eventId)

            if (newEvent != null) {
                val newAlertTime = newEvent.nextAlarmTime(currentTime)

                if (event.startTime != newEvent.startTime) {

                    DevLog.info(LOG_TAG, "Event ${event.eventId} - move detected, ${event.startTime} != ${newEvent.startTime}")

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
            return checkCalendarAlertHasChanged(context, event, newEventInstance)

        return reloadCalendarEventAlertFromEvent(context, calendarProvider, event, currentTime)
    }

    fun checkCalendarAlertHasChanged(
            context: Context,
            event: EventAlertRecord,
            newEventAlert: EventAlertRecord
    ): ReloadCalendarResult {

        if (!newEventAlert.isRepeating) {

            if (event.updateFrom(newEventAlert)) {

                if (event.instanceStartTime == newEventAlert.instanceStartTime) {
                    DevLog.info(LOG_TAG, "Non-repeating event ${event.eventId} / ${event.instanceStartTime} was updated");

                    return ReloadCalendarResult(
                            ReloadCalendarResultCode.EventDetailsUpdatedShouldUpdate,
                            event
                    )

                }
                else {
                    DevLog.info(LOG_TAG, "Non-repeating event ${event.eventId} / ${event.instanceStartTime} was updated, new instance start ${newEventAlert.instanceStartTime} -- event was moved");

                    return ReloadCalendarResult(
                            ReloadCalendarResultCode.EventInstanceMovedShouldUpdate,
                            event,
                            newEventAlert.instanceStartTime,
                            newEventAlert.instanceEndTime
                    )
                }

            } /*else {
                DevLog.info(context, LOG_TAG, "Non-repeating event ${event.eventId} / ${event.instanceStartTime} hasn't changed");
            }*/
        }
        else {
            if (event.updateFrom(newEventAlert)) {
                // ignore updated instance times for repeating requests - they are unpredictable
                DevLog.info(LOG_TAG, "Repeating event ${event.eventId} / ${event.instanceStartTime} was updated");

                return ReloadCalendarResult(
                        ReloadCalendarResultCode.EventDetailsUpdatedShouldUpdate,
                        event
                )
            } /*else {
                DevLog.info(context, LOG_TAG, "Repeating event ${event.eventId} / ${event.instanceStartTime} hasn't changed");
            }*/
        }

        return ReloadCalendarResult(
                ReloadCalendarResultCode.NoChange,
                event
        )
    }

    fun reloadCalendarEventAlertFromEvent(
            context: Context,
            calendar: CalendarProviderInterface,
            event: EventAlertRecord,
            currentTime: Long
    ): ReloadCalendarResult {

        DevLog.debug(LOG_TAG, "event ${event.eventId} / ${event.instanceStartTime} - instance NOT found")

        if (event.isRepeating) {
            // This is repeating event -- can't do anything, we can't match new instances to the current one
            DevLog.info(LOG_TAG, "Repeating event ${event.eventId} instance ${event.instanceStartTime} disappeared");
            // can't do anything about it - just ignore, assuming no change
            return ReloadCalendarResult(ReloadCalendarResultCode.NoChange, event)
        }

        // Try loading at least basic params from "event"
        val newEvent = calendar.getEvent(context, event.eventId)
        if (newEvent == null) {
            // Here we can't confirm that event was moved into the future.
            // Perhaps it was removed, but this is not what users usually do.
            // Leave it for user to remove the notification
            DevLog.info(LOG_TAG, "Event ${event.eventId} disappeared completely (Known instance ${event.instanceStartTime})");
            // can't do anything about it - just ignore, assuming no change
            return ReloadCalendarResult(ReloadCalendarResultCode.NoChange, event)
        }

        val newAlertTime = newEvent.nextAlarmTime(currentTime)

        if (event.startTime != newEvent.startTime) {
            DevLog.info(LOG_TAG, "Event ${event.eventId} - move detected, ${event.startTime} != ${newEvent.startTime}")
        }

        if (event.updateFrom(newEvent) || event.alertTime != newAlertTime) {
            DevLog.info(LOG_TAG, "Event ${event.eventId} for lost instance ${event.instanceStartTime} was updated, new start time ${newEvent.startTime}, alert time ${event.alertTime} -> $newAlertTime");
            event.alertTime = newAlertTime
            event.displayStatus = EventDisplayStatus.Hidden

            return ReloadCalendarResult(
                    ReloadCalendarResultCode.EventInstanceMovedShouldUpdate,
                    event,
                    newInstanceStartTime = newEvent.startTime,
                    newInstanceEndTime = newEvent.endTime,
                    setDisplayStatusHidden = false
            )
        }
        else {
            DevLog.info(LOG_TAG, "Event instance ${event.eventId} / ${event.instanceStartTime} disappeared, actual event is still exactly the same");
        }

        return ReloadCalendarResult(ReloadCalendarResultCode.NoChange, event)
    }
}