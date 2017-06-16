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
import com.github.quarck.calnotify.calendar.CalendarProvider
import com.github.quarck.calnotify.calendar.CalendarProviderInterface
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.calendar.EventOrigin
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.permissions.PermissionsManager

class CalendarMonitorProvider(
        val calendarProvider: CalendarProviderInterface,
        val calendarMonitor: CalendarMonitorInterface
) {

    private val MAX_ITERATIONS = 1000L

    private fun registerFiredEventsInDB(
            context: Context,
            state: CalendarMonitorState,
            alertTime: Long
    ): Collection<EventAlertRecord> {

        DevLog.debug(LOG_TAG, "registerFiredEventsInDB, alertTime=$alertTime");

        val ret = mutableListOf<EventAlertRecord>()

        try {
            val events = CalendarProvider.getAlertByTime(context, alertTime, skipDismissed = false)

            for (event in events) {

                if (calendarMonitor.getAlertWasHandled(context, event)) {
                    DevLog.info(context, LOG_TAG, "Event ${event.eventId} / ${event.instanceStartTime} was handled already")
                    continue
                }

                DevLog.warn(context, LOG_TAG, "Calendar Provider didn't handle this: event ${event.eventId} / ${event.instanceStartTime}")

                event.origin = EventOrigin.ProviderManual
                event.timeFirstSeen = System.currentTimeMillis()

                if (ApplicationController.shouldMarkEventAsHandledAndSkip(context, event)) {
                    // silently drop, provider already has no idea about this event
                }
                else if (ApplicationController.registerNewEvent(context, event)) {
                    ret.add(event)
                }
            }
        }
        catch (ex: Exception) {
            DevLog.error(context, LOG_TAG, "Exception while trying to load fired event details, ${ex.message}, ${ex.stackTrace}")
        }
        finally {
            state.prevEventFireFromProvider = alertTime
        }

        return ret
    }


    // should return true if we have fired at new events, so UI should reload if it is open
    fun manualFireEventsAt_NoHousekeeping(context: Context, state: CalendarMonitorState, nextEventFire: Long, prevEventFire: Long? = null): Boolean {

        if (!PermissionsManager.hasReadCalendar(context)) {
            DevLog.error(context, LOG_TAG, "manualFireEventsAt_NoHousekeeping: no permissions");
            return false;
        }

        var ret = false

        DevLog.debug(LOG_TAG, "manualFireEventsAt: ($nextEventFire, $prevEventFire)");

        var currentPrevFire = prevEventFire ?: Long.MAX_VALUE

        val eventsToPost = mutableListOf<EventAlertRecord>()

        if (currentPrevFire != Long.MAX_VALUE) {

            for (iteration in 0..MAX_ITERATIONS) {

                val nextSincePrev = calendarProvider.findNextAlarmTime(context.contentResolver, currentPrevFire + 1L)

                if (nextSincePrev == null || nextSincePrev >= nextEventFire)
                    break

                DevLog.info(context, LOG_TAG, "manualFireEventsAt: called to fire at $nextEventFire, but found earlier unhandled alarm time $nextSincePrev")

                val newFires = registerFiredEventsInDB(context, state, nextSincePrev)
                eventsToPost.addAll(newFires)
                //    ret = true

                currentPrevFire = nextSincePrev
            }
        }

        val newFires = registerFiredEventsInDB(context, state, nextEventFire)
        eventsToPost.addAll(newFires)

        if (!newFires.isEmpty()) {
            ret = true

            val settings = Settings(context)

            try {
                ApplicationController.postEventNotifications(context, newFires)

                for (event in newFires) {
                    calendarMonitor.setAlertWasHandled(context, event, createdByUs = false)
                    CalendarProvider.dismissNativeEventAlert(context, event.eventId);

                    DevLog.info(context, LOG_TAG, "Event ${event.eventId} / ${event.instanceStartTime} is marked as handled in the DB and in the provider")
                }
            }
            catch (ex: Exception) {
                DevLog.error(context, LOG_TAG, "Exception occured while posting notifications / firing events: $ex, ${ex.stackTrace}")
            }
        }

        return ret
    }

    fun scanNextEvent_NoHousekeping(context: Context, state: CalendarMonitorState): Pair<Long, Boolean> {

        if (!PermissionsManager.hasReadCalendar(context)) {
            DevLog.error(context, LOG_TAG, "scanNextEvent_NoHousekeping: no permissions");
            return Pair(0L, false);
        }

        DevLog.debug(LOG_TAG, "scanNextEvent");

        val currentTime = System.currentTimeMillis()
        val processEventsTo = currentTime + Consts.ALARM_THRESHOLD

        var nextAlert = state.nextEventFireFromProvider

        var firedEvents = false;

        // First - scan past reminders that we have potentially missed,
        // this is for the case where nextAlert is in the past already (e.g. device was off
        // for a while)
        for (iteration in 0..MAX_ITERATIONS) {

            if (nextAlert >= processEventsTo) {
                DevLog.info(context, LOG_TAG, "scanNextEvent: nextAlert=$nextAlert >= processEventsTo=$processEventsTo, good to proceed next")
                break
            }

            DevLog.info(context, LOG_TAG, "scanNextEvent: nextAlert $nextAlert is already in the past, firing at it")

            if (manualFireEventsAt_NoHousekeeping(context, state, nextAlert))
                firedEvents = true

            nextAlert = calendarProvider.findNextAlarmTime(context.contentResolver, nextAlert + 1L) ?: Long.MAX_VALUE
        }

        // Now - scan for the next alert since currentTime, so we can see any newly added events, if any
        val nextAlertSinceCurrent = calendarProvider.findNextAlarmTime(context.contentResolver, processEventsTo)
                ?: Long.MAX_VALUE

        // finally - pick the first one
        nextAlert = Math.min(nextAlert, nextAlertSinceCurrent)

        DevLog.info(context, LOG_TAG, "scanNextEvent: nextAlertSinceCurrent=$nextAlertSinceCurrent, resulting nextAlert=$nextAlert")

        state.nextEventFireFromProvider = nextAlert

        return Pair(nextAlert, firedEvents)
    }


    companion object {
        private const val LOG_TAG = "CalendarMonitorProvider"
    }
}