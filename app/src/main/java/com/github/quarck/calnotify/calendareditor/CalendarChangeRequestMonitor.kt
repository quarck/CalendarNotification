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
import android.content.Intent
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.calendareditor.storage.CalendarChangeRequestsStorage
import com.github.quarck.calnotify.calendar.CalendarProvider
import com.github.quarck.calnotify.calendar.CalendarProviderInterface
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.permissions.PermissionsManager
import com.github.quarck.calnotify.utils.detailed

class CalendarChangeRequestMonitor : CalendarChangeRequestMonitorInterface {

    enum class ValidationResultCommand {
        DeleteRequest,
        UpdateRequest,
        ReApplyRequest,
        JustSkipRequest
    }

    override fun onRescanFromService(context: Context, intent: Intent) {

        DevLog.debug(LOG_TAG, "onRescanFromService")

        if (!PermissionsManager.hasAllPermissionsNoCache(context)) {
            DevLog.error(context, LOG_TAG, "onRescanFromService - no calendar permission to proceed")
            return
        }

        val provider = CalendarProvider

        val currentTime = System.currentTimeMillis()
        val cleanupEventsTo = currentTime - Consts.NEW_EVENT_MONITOR_KEEP_DAYS * Consts.DAY_IN_MILLISECONDS

        CalendarChangeRequestsStorage(context).use {
            db ->

            val eventsToDelete = mutableListOf<CalendarChangeRequest>()
            val eventsToReApply = mutableListOf<CalendarChangeRequest>()
            val eventsToUpdate = mutableListOf<CalendarChangeRequest>()

            for (event in db.requests) {

                try {
                    val validation =
                            when (event.type) {
                                EventChangeRequestType.AddNewEvent ->
                                    validateCreationRequest(
                                            context,
                                            provider,
                                            event,
                                            cleanupEventsTo)
                                else ->
                                    validateMoveRequest(
                                            context,
                                            provider,
                                            event,
                                            cleanupEventsTo)
                            }

                    when (validation) {
                        ValidationResultCommand.DeleteRequest ->
                                eventsToDelete.add(event)

                        ValidationResultCommand.UpdateRequest ->
                                eventsToUpdate.add(event)

                        ValidationResultCommand.ReApplyRequest ->
                                eventsToReApply.add(event)

                        ValidationResultCommand.JustSkipRequest ->
                                Unit
                    }
                }
                catch (ex: Exception) {
                    DevLog.error(context, LOG_TAG, "Failed to validate ${event.eventId}, type ${event.type}, ${ex.detailed}")
                }

            }

            if (eventsToReApply.isNotEmpty()) {
                for (event in eventsToReApply) {
                    reApplyRequest(context, provider, event)
                }
                db.updateMany(eventsToReApply)
            }

            if (eventsToDelete.isNotEmpty())
                db.deleteMany(eventsToDelete)

            if (eventsToUpdate.isNotEmpty())
                db.updateMany(eventsToUpdate)
        }
    }

    private fun reApplyRequest(context: Context, provider: CalendarProvider, event: CalendarChangeRequest) {

        DevLog.info(context, LOG_TAG, "Re-Applying req, event id ${event.eventId}, type ${event.type}")

        try {
            when (event.type) {
                EventChangeRequestType.AddNewEvent ->
                    event.eventId = provider.createEvent(context, event)

                EventChangeRequestType.MoveExistingEvent -> {
                    provider.moveEvent(context, event.eventId, event.details.startTime, event.details.endTime)
                    event.status = EventChangeStatus.Dirty
                }
            }
        }
        catch (ex: Exception) {
            DevLog.error(context, LOG_TAG, "Failed: ${ex.detailed}")
        }
    }

    private fun validateCreationRequest(
            context: Context,
            provider: CalendarProviderInterface,
            event: CalendarChangeRequest,
            cleanupEventsTo: Long
    ): ValidationResultCommand {

        if (event.details.startTime < cleanupEventsTo && event.details.endTime < cleanupEventsTo) {
            DevLog.info(context, LOG_TAG, "Scheduling event creation request for ${event.eventId} ${event.type} for removal from DB")
            return ValidationResultCommand.DeleteRequest
        }

        if (event.eventId == -1L) {
            event.onValidated(false)
            DevLog.info(context, LOG_TAG, "Scheduling event creation request ${event.eventId} ${event.type} for re-creation: id is -1L")
            return ValidationResultCommand.ReApplyRequest
        }

        val calendarEvent = provider.getEvent(context, event.eventId)
        if (calendarEvent == null) {
            event.onValidated(false)
            DevLog.info(context, LOG_TAG, "Scheduling event creation request ${event.eventId} ${event.type} for re-creation: cant find provider event")
            return ValidationResultCommand.ReApplyRequest
        }

        var ret = ValidationResultCommand.JustSkipRequest

        val isDirty = provider.getEventIsDirty(context, event.eventId)
        DevLog.info(context, LOG_TAG, "Event ${event.eventId}, isDirty=$isDirty")
        if (isDirty != null) {

            event.onValidated(!isDirty)

            if (event.status == EventChangeStatus.Synced) {
                DevLog.info(context, LOG_TAG, "Scheduling event creation request ${event.eventId}  ${event.type} for removal: it is fully synced now")
                ret = ValidationResultCommand.DeleteRequest
            }
            else {
                DevLog.info(context, LOG_TAG, "Event creation request ${event.eventId}  ${event.type}: new status ${event.status}")
                ret = ValidationResultCommand.UpdateRequest
            }
        }

        return ret
    }

    private fun validateMoveRequest(
            context: Context,
            provider: CalendarProviderInterface,
            event: CalendarChangeRequest,
            cleanupEventsTo: Long
    ): ValidationResultCommand {

        if (event.details.startTime < cleanupEventsTo && event.details.endTime < cleanupEventsTo) {
            DevLog.info(context, LOG_TAG, "Scheduling event change request for ${event.eventId}  ${event.type} for removal from DB")
            return ValidationResultCommand.DeleteRequest
        }

        val calendarEvent = provider.getEvent(context, event.eventId)
        if (calendarEvent == null) {
            DevLog.info(context, LOG_TAG, "Scheduling event change request for ${event.eventId} ${event.type} for removal from DB -- no longer in the provider")
            return ValidationResultCommand.DeleteRequest
        }

        if (event.oldDetails.startTime == calendarEvent.startTime
                || event.oldDetails.endTime == calendarEvent.endTime) {
            DevLog.info(context, LOG_TAG, "Scheduling event change request for ${event.eventId} ${event.type} for re-apply")
            event.onValidated(false)
            return ValidationResultCommand.ReApplyRequest
        }

        var ret = ValidationResultCommand.JustSkipRequest

        val isDirty = provider.getEventIsDirty(context, event.eventId)
        DevLog.info(context, LOG_TAG, "Event ${event.eventId}, isDirty=$isDirty")
        if (isDirty != null) {

            event.onValidated(!isDirty)

            if (event.status == EventChangeStatus.Synced) {
                DevLog.info(context, LOG_TAG, "Scheduling event change request ${event.eventId} ${event.type} for removal: it is fully synced now")
                ret = ValidationResultCommand.DeleteRequest
            }
            else {
                DevLog.info(context, LOG_TAG, "Event change request ${event.eventId} ${event.type}: new status ${event.status}")
                ret = ValidationResultCommand.UpdateRequest
            }
        }

        return ret
    }

    companion object {
        private const val LOG_TAG = "CalendarChangeRequestMonitor"
    }
}