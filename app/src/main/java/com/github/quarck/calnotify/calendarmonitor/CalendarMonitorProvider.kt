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
import com.github.quarck.calnotify.calendar.EventOrigin

class CalendarMonitorProvider(
        val calendarProvider: CalendarProviderInterface,
        val calendarMonitor: CalendarMonitorInterface
) {

    private val MAX_ITERATIONS = 1000L

    // returns true if has fired any single new event
    fun doManualFireProviderEventsAt(context: Context, state: CalendarMonitorState, alertTime: Long): Boolean {

        var ret = false

        logger.debug("doManualFireProviderEventsAt, alertTime=$alertTime");

        val settings = Settings(context)

        val markEventsAsHandledInProvider = settings.markEventsAsHandledInProvider

        try {
            val events = CalendarProvider.getAlertByTime(context, alertTime, skipDismissed = false)

            for (event in events) {

                if (calendarMonitor.getAlertWasHandled(context, event)) {
                    logger.info("Seen event ${event.eventId} / ${event.instanceStartTime} - it was handled already")
                    continue
                }

                logger.info("WARNING: Calendar Provider didn't handle this: Seen event ${event.eventId} / ${event.instanceStartTime}")

                ret = true

                event.origin = EventOrigin.ProviderManual
                event.timeFirstSeen = System.currentTimeMillis()
                val wasHandled = ApplicationController.onCalendarEventFired(context, event);

                if (wasHandled) {
                    calendarMonitor.setAlertWasHandled(context, event, createdByUs = false)

                    logger.info("Event ${event.eventId} / ${event.instanceStartTime} is marked as handled in the DB")

                    if (markEventsAsHandledInProvider) {
                        logger.info("Dismissing original reminder - marking event as handled in the provider")
                        CalendarProvider.dismissNativeEventAlert(context, event.eventId);
                    }
                }
            }
        }
        catch (ex: Exception) {
            logger.error("Exception while trying to load fired event details, ${ex.message}, ${ex.stackTrace}")
        }
        finally {
            state.prevEventFireFromProvider = alertTime
        }

        return ret
    }


    // should return true if we have fired at new events, so UI should reload if it is open
    fun manualFireEventsAt(context: Context, state: CalendarMonitorState, nextEventFire: Long, prevEventFire: Long? = null): Boolean {

        var ret = false

        logger.debug("manualFireEventsAt: ($nextEventFire, $prevEventFire)");

        var currentPrevFire = prevEventFire ?: Long.MAX_VALUE

        if (currentPrevFire != Long.MAX_VALUE) {

            for (iteration in 0..MAX_ITERATIONS) {

                val nextSincePrev = calendarProvider.findNextAlarmTime(context.contentResolver, currentPrevFire + 1L)

                if (nextSincePrev == null || nextSincePrev >= nextEventFire)
                    break

                logger.info("manualFireEventsAt: called to fire at $nextEventFire, but found earlier unhandled alarm time $nextSincePrev")

                if (doManualFireProviderEventsAt(context, state, nextSincePrev))
                    ret = true

                currentPrevFire = nextSincePrev
            }
        }

        if (doManualFireProviderEventsAt(context, state, nextEventFire))
            ret = true

        return ret
    }

    fun scanNextEvent(context: Context, state: CalendarMonitorState): Pair<Long, Boolean> {

        logger.debug("scanNextEvent");

        val currentTime = System.currentTimeMillis()
        val processEventsTo = currentTime + Consts.ALARM_THRESHOLD

        var nextAlert = state.nextEventFireFromProvider

        var firedEvents = false;

        // First - scan past reminders that we have potentially missed,
        // this is for the case where nextAlert is in the past already (e.g. device was off
        // for a while)
        for (iteration in 0..MAX_ITERATIONS) {

            if (nextAlert >= processEventsTo) {
                logger.info("scanNextEvent: nextAlert=$nextAlert >= processEventsTo=$processEventsTo, good to proceed next")
                break
            }

            logger.info("scanNextEvent: nextAlert $nextAlert is already in the past, firing at it")

            if (manualFireEventsAt(context, state, nextAlert))
                firedEvents = true

            nextAlert = calendarProvider.findNextAlarmTime(context.contentResolver, nextAlert + 1L) ?: Long.MAX_VALUE
        }

        // Now - scan for the next alert since currentTime, so we can see any newly added events, if any
        val nextAlertSinceCurrent = calendarProvider.findNextAlarmTime(context.contentResolver, processEventsTo )
                ?: Long.MAX_VALUE

        // finally - pick the first one
        nextAlert = Math.min(nextAlert, nextAlertSinceCurrent)

        logger.info("scanNextEvent: nextAlertSinceCurrent=$nextAlertSinceCurrent, resulting nextAlert=$nextAlert")

        state.nextEventFireFromProvider = nextAlert

        return Pair(nextAlert, firedEvents)
    }


    companion object {
        val logger = com.github.quarck.calnotify.logs.Logger("CalendarMonitorProvider")
    }
}