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
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.calendar.CalendarUtils
import com.github.quarck.calnotify.eventsstorage.*
import com.github.quarck.calnotify.logs.Logger
import com.github.quarck.calnotify.notification.EventNotificationManager
import com.github.quarck.calnotify.notification.IEventNotificationManager
import com.github.quarck.calnotify.quiethours.QuietHoursManager
import com.github.quarck.calnotify.quiethours.QuietHoursManagerInterface
import com.github.quarck.calnotify.ui.UINotifierService

object ApplicationController {
    private val notificationManager: IEventNotificationManager = EventNotificationManager()

    private val logger = Logger("EventsManager");


    private var settings: Settings? = null
    private fun getSettings(ctx: Context): Settings {
        synchronized(this) {
            if (settings == null)
                settings = Settings(ctx)
        }
        return settings!!
    }

    val undoManager: UndoManagerInterface = UndoManager

    val alarmScheduler: AlarmSchedulerInterface = AlarmScheduler

    val quietHoursManager: QuietHoursManagerInterface = QuietHoursManager


    fun hasActiveEvents(context: Context) =
        EventsStorage(context).use { it.events.filter { it.snoozedUntil == 0L }.any() }

    fun onEventAlarm(context: Context) {
        notificationManager.postEventNotifications(context, false, null);
        alarmScheduler.rescheduleAlarms(context, getSettings(context), quietHoursManager);
    }


    private fun reloadCalendar(context: Context): Boolean {

        var repostNotifications = false

        EventsStorage(context).use {
            db ->

            val events = db.events

            val currentTime = System.currentTimeMillis()

            for (event in events) {

                try {
                    val newEvent = CalendarUtils.getEventInstance(context, event.eventId, event.alertTime)

                    if (newEvent == null ) {
                        //newEvent = CalendarUtils.getEvent(context, event.eventId)

//                        if (newEvent != null
//                            && (newEvent.startTime - event.startTime > Consts.EVENT_MOVED_THRESHOLD)
//                            && (newEvent.startTime - currentTime > Consts.EVENT_MOVED_THRESHOLD) ) {
//                            // Here we have a confirmation that event was re-scheduled by user
//                            // to some time in the future and that's why original event instance has disappeared
//                            // - we are good to go to dismiss event reminder automatically
//                            dismissEvent(context, db, event.eventId, event.notificationId, notifyActivity = true, enableUndo = false);
//                            val movedSec = (newEvent.startTime - event.startTime) / 1000L
//                            logger.debug("Event ${event.eventId} disappeared, event was moved further by $movedSec seconds");
//                        } else {
//                            // Here we can't confirm that event was moved into the future.
//                            // Perhaps it was removed, but this is not what users usually do.
//                            // Leave it for user to remove the notification
//                            logger.debug("Event ${event.eventId} disappeared, but can't confirm it has been rescheduled. Not removing");
//                        }
                    } else {
                        logger.debug("Event ${event.eventId} is still here");

                        if (event.updateFrom(newEvent)) {
                            logger.debug("Event was updated, updating our copy");

                            db.updateEvent(
                                event,
                                displayStatus = EventDisplayStatus.Hidden) // so this will en-force event to be posted

                            repostNotifications = true
                        }
                    }
                } catch (ex: Exception) {
                    logger.error("Got exception while trying to re-load event data for ${event.eventId}: ${ex.message}, ${ex.stackTrace}");
                }
            }
        }

        return repostNotifications
    }

    fun onAppUpdated(context: Context) {

        val changes = reloadCalendar(context)
        notificationManager.postEventNotifications(context, true, null);

        alarmScheduler.rescheduleAlarms(context, getSettings(context), quietHoursManager);

        if (changes)
            UINotifierService.notifyUI(context, false);
    }

    fun onBootComplete(context: Context) {
        val changes = reloadCalendar(context);
        notificationManager.postEventNotifications(context, true, null);

        alarmScheduler.rescheduleAlarms(context, getSettings(context), quietHoursManager);

        if (changes)
            UINotifierService.notifyUI(context, false);
    }

    fun onCalendarChanged(context: Context) {

        val changes = reloadCalendar(context)
        if (changes) {
            notificationManager.postEventNotifications(context, true, null);

            alarmScheduler.rescheduleAlarms(context, getSettings(context), quietHoursManager);

            UINotifierService.notifyUI(context, false);
        }
    }

    fun onCalendarEventFired(context: Context, event: EventInstanceRecord): Boolean {

        var ret = false

        if (event.calendarId == -1L || getSettings(context).getCalendarIsHandled(event.calendarId)) {
            EventsStorage(context).use { it.addEvent(event) }

            notificationManager.onEventAdded(context, event)

            alarmScheduler.rescheduleAlarms(context, getSettings(context), quietHoursManager);

            ret = true

            UINotifierService.notifyUI(context, false);

            logger.info("event added: ${event.eventId} (cal id: ${event.calendarId}");

        } else {
            logger.info("Event ${event.eventId} belongs to calendar ${event.calendarId} which is not handled, skipping");
        }

        return ret
    }

    fun snoozeEvent(context: Context, eventId: Long, instanceStartTime: Long, snoozeDelay: Long): SnoozeResult? {

        var ret: SnoozeResult? = null

        val currentTime = System.currentTimeMillis()

        val snoozedEvent =
            EventsStorage(context).use {
                db ->
                var event = db.getEvent(eventId, instanceStartTime)

                if (event != null) {
                    val snoozedUntil =
                        if (snoozeDelay > 0L) currentTime + snoozeDelay
                        else event.displayedStartTime - Math.abs(snoozeDelay) // same as "event.instanceStart + snoozeDelay" but a little bit more readable

                    event = db.updateEvent(event,
                        snoozedUntil = snoozedUntil,
                        lastEventVisibility = currentTime,
                        displayStatus = EventDisplayStatus.Hidden)
                } else {
                    logger.error("Error: can't get event from DB");
                }

                event;
            }

        if (snoozedEvent != null) {
            notificationManager.onEventSnoozed(context, snoozedEvent.eventId, snoozedEvent.notificationId);

            alarmScheduler.rescheduleAlarms(context, getSettings(context), quietHoursManager);

            val silentUntil = QuietHoursManager.getSilentUntil(getSettings(context), snoozedEvent.snoozedUntil)

            ret = SnoozeResult(SnoozeType.Snoozed, snoozedEvent.snoozedUntil, silentUntil)
        }

        return ret
    }

    fun snoozeAllEvents(context: Context, snoozeDelay: Long): SnoozeResult? {

        var ret: SnoozeResult? = null

        val currentTime = System.currentTimeMillis()

        val snoozedUntil =
            EventsStorage(context).use {
                db ->
                val events = db.events

                // Don't allow events to have exactly the same "snoozedUntil", so to have
                // predicted sorting order, so add a tiny (0.001s per event) adjust to each
                // snoozed time

                var snoozeAdjust = 0

                for (event in events) {
                    db.updateEvent(event,
                        snoozedUntil = currentTime + snoozeDelay + snoozeAdjust,
                        lastEventVisibility = currentTime)

                    ++snoozeAdjust
                }

                events.lastOrNull()?.snoozedUntil
            }

        if (snoozedUntil != null) {

            notificationManager.onAllEventsSnoozed(context)

            alarmScheduler.rescheduleAlarms(context, getSettings(context), quietHoursManager);

            val silentUntil = QuietHoursManager.getSilentUntil(getSettings(context), snoozedUntil)

            ret = SnoozeResult(SnoozeType.Snoozed, snoozedUntil, silentUntil)
        }

        return ret
    }

    fun fireEventReminder(context: Context) {
        notificationManager.fireEventReminder(context);
    }

    fun onAppStarted(context: Context?) {

        if (context != null) {
            val settings = getSettings(context)

            if (settings.versionCodeFirstInstalled == 0L) {
                val pInfo = context.packageManager.getPackageInfo(context.packageName, 0);
                settings.versionCodeFirstInstalled = pInfo.versionCode.toLong();
            }
        }
    }

    fun onAppResumed(context: Context?) {
        if (context != null) {
            val changes = reloadCalendar(context);
            notificationManager.postEventNotifications(context, true, null)

            alarmScheduler.rescheduleAlarms(context, getSettings(context), quietHoursManager);

            if (changes)
                UINotifierService.notifyUI(context, true);
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun onAppPause(context: Context) {
        undoManager.clear()
    }

    fun onTimeChanged(context: Context) {
        alarmScheduler.rescheduleAlarms(context, getSettings(context), quietHoursManager);
    }

    fun dismissEvent(context: Context, db: EventsStorage, eventId: Long, instanceStartTime: Long, notificationId: Int, notifyActivity: Boolean, enableUndo: Boolean) {

        logger.debug("Removing event id $eventId from DB, and dismissing notification")

        if (enableUndo) {
            val event = db.getEvent(eventId, instanceStartTime)
            if (event != null)
                undoManager.push(event)
        }

        db.deleteEvent(eventId, instanceStartTime)

        notificationManager.onEventDismissed(context, eventId, notificationId);

        alarmScheduler.rescheduleAlarms(context, getSettings(context), quietHoursManager);

        if (notifyActivity)
            UINotifierService.notifyUI(context, true);
    }

    fun dismissEvent(context: Context, event: EventInstanceRecord, enableUndo: Boolean = false) {
        EventsStorage(context).use {
            if (enableUndo)
                undoManager.push(event)
            dismissEvent(context, it, event.eventId, event.instanceStartTime, event.notificationId, false, false)
        }
    }

    fun dismissEvent(context: Context, eventId: Long, instanceStartTime: Long, notificationId: Int, notifyActivity: Boolean = true, enableUndo: Boolean = false) {
        EventsStorage(context).use {
            dismissEvent(context, it, eventId, instanceStartTime, notificationId, notifyActivity, enableUndo)
        }
    }

    fun undoDismiss(context: Context) {
        val event = undoManager.pop()
        if (event != null) {
            EventsStorage(context).use { it.addEvent(event) }
            notificationManager.onEventRestored(context, event)
        }
    }
}
