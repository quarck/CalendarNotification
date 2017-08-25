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

package com.github.quarck.calnotify.calendareditor

import android.content.Context
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.calendar.*
import com.github.quarck.calnotify.calendareditor.storage.*
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.permissions.PermissionsManager

class CalendarChangeManager(val provider: CalendarProviderInterface): CalendarChangeManagerInterface {

    override fun createEvent(context: Context, calendarId: Long, details: CalendarEventDetails): Long {

        var eventId = -1L

        DevLog.info(context, LOG_TAG, "Request to create an event")

        if (!PermissionsManager.hasAllPermissions(context)) {
            DevLog.error(context, LOG_TAG, "createEvent: no permissions");
            return -1L;
        }

        val event = CalendarChangeRequest(
                id = -1L,
                type = EventChangeRequestType.AddNewEvent,
                eventId = -1L,
                calendarId = calendarId,
                details = details,
                oldDetails = CalendarEventDetails.createEmpty()
        )

        CalendarChangeRequestsStorage(context).use {
            db ->

            db.add(event)
            DevLog.info(context, LOG_TAG, "Event creation request logged")

            eventId = provider.createEvent(context, event.calendarId, event.details)

            if (eventId != -1L) {
                DevLog.info(context, LOG_TAG, "Created new event, id $eventId")

                event.eventId = eventId
                db.update(event)
            }
            else {
                DevLog.info(context, LOG_TAG, "Failed to create a new event, will retry later")
            }
        }

        return eventId
    }

    override fun moveEvent(context: Context, event: EventAlertRecord, addTimeMillis: Long): Boolean {

        var ret = false

        if (!PermissionsManager.hasAllPermissions(context)) {
            DevLog.error(context, LOG_TAG, "moveEvent: no permissions");
            return false;
        }

        CalendarChangeRequestsStorage(context).use {
            db ->

            db.deleteForEventId(event.eventId)

            // Get full event details from the provider, if failed - construct a failback version
            val oldDetails =
                    provider.getEvent(context, event.eventId)?.details
                            ?: CalendarEventDetails(
                            title = event.title,
                            desc = "",
                            location = event.location,
                            timezone = "",
                            startTime = event.startTime,
                            endTime = event.endTime,
                            isAllDay = event.isAllDay,
                            reminders = listOf<EventReminderRecord>(EventReminderRecord.minutes(15)),
                            color = event.color
                    )

            val newStartTime: Long
            val newEndTime: Long

            val currentTime = System.currentTimeMillis()

            val numSecondsInThePast = currentTime + Consts.ALARM_THRESHOLD - event.startTime

            if (numSecondsInThePast > 0) {
                val addUnits = numSecondsInThePast / addTimeMillis + 1

                newStartTime = event.startTime + addTimeMillis * addUnits
                newEndTime = event.endTime + addTimeMillis * addUnits

                DevLog.warn(context, LOG_TAG, "Requested time is already in the past, total added time: ${addTimeMillis * addUnits}")
            }
            else {
                newStartTime = event.startTime + addTimeMillis
                newEndTime = event.endTime + addTimeMillis
            }

            DevLog.info(context, LOG_TAG, "Moving event ${event.eventId} from ${event.startTime} / ${event.endTime} to $newStartTime / $newEndTime")

            ret = provider.moveEvent(context, event.eventId, newStartTime, newEndTime)
            event.startTime = newStartTime
            event.endTime = newEndTime

            DevLog.info(context, LOG_TAG, "Provider move event for ${event.eventId} result: $ret")

            val newDetails = oldDetails.copy(startTime = newStartTime, endTime = newEndTime)

            DevLog.info(context, LOG_TAG, "Adding move request into DB: move: ${event.eventId} ${oldDetails.startTime} / ${oldDetails.endTime} -> ${newDetails.startTime} / ${newDetails.endTime}")

            db.add(
                    CalendarChangeRequest(
                            id = -1L,
                            type = EventChangeRequestType.MoveExistingEvent,
                            eventId = event.eventId,
                            calendarId = event.calendarId,
                            status = EventChangeStatus.Dirty,
                            details = newDetails,
                            oldDetails = oldDetails
                    )
            )
        }

        return ret
    }

    override fun updateEvent(context: Context, eventToEdit: EventRecord, details: CalendarEventDetails): Boolean {

        if (!PermissionsManager.hasAllPermissions(context)) {
            DevLog.error(context, LOG_TAG, "updateEvent: no permissions");
            return false;
        }

        var ret = false

        CalendarChangeRequestsStorage(context).use {
            db ->

            db.deleteForEventId(eventToEdit.eventId)

            ret = provider.updateEvent(context, eventToEdit, details)

            if (ret) {
                DevLog.info(context, LOG_TAG, "Successfully updated provider, event ${eventToEdit.eventId}")
            }
            else {
                DevLog.error(context, LOG_TAG, "Failed to updated provider, event ${eventToEdit.eventId}")
            }

            DevLog.info(context, LOG_TAG, "Adding edit request into DB: ${eventToEdit.eventId} ")

            db.add(
                    CalendarChangeRequest(
                            id = -1L,
                            type = EventChangeRequestType.EditExistingEvent,
                            eventId = eventToEdit.eventId,
                            calendarId = eventToEdit.calendarId,
                            status = EventChangeStatus.Dirty,
                            details = details,
                            oldDetails = eventToEdit.details
                    )
            )
        }

        if (ret && (eventToEdit.startTime != details.startTime)) {

            DevLog.info(context, LOG_TAG, "Event ${eventToEdit.eventId} was moved, ${eventToEdit.startTime} != ${details.startTime}, checking for notification auto-dismissal")

            val newEvent = provider.getEvent(context, eventToEdit.eventId)

            if (newEvent != null) {
                ApplicationController.onCalendarEventMovedWithinApp(
                        context,
                        eventToEdit,
                        newEvent
                )
            }
        }

        return ret
    }

    companion object {
        const val LOG_TAG = "CalendarChangeMonitor"
    }
}