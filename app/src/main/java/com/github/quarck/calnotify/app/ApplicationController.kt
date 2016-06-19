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
import com.github.quarck.calnotify.eventsstorage.EventsStorage
import com.github.quarck.calnotify.logs.Logger
import com.github.quarck.calnotify.notification.EventNotificationManager
import com.github.quarck.calnotify.notification.EventNotificationManagerInterface
import com.github.quarck.calnotify.quiethours.QuietHoursManager
import com.github.quarck.calnotify.quiethours.QuietHoursManagerInterface
import com.github.quarck.calnotify.ui.SnoozeActivityNoRecents
import com.github.quarck.calnotify.ui.UINotifierService

object ApplicationController {
    private val logger = Logger("EventsManager");

    private var settings: Settings? = null
    private fun getSettings(ctx: Context): Settings {
        synchronized(this) {
            if (settings == null)
                settings = Settings(ctx)
        }
        return settings!!
    }

    private val notificationManager: EventNotificationManagerInterface = EventNotificationManager()

    private val alarmScheduler: AlarmSchedulerInterface = AlarmScheduler

    private val quietHoursManager: QuietHoursManagerInterface = QuietHoursManager

    private val calendarReloadManager: CalendarReloadManagerInterface = CalendarReloadManager

    private val calendarProvider: CalendarProviderInterface = CalendarProvider

    fun hasActiveEvents(context: Context) =
        EventsStorage(context).use { it.events.filter { it.snoozedUntil == 0L }.any() }

    fun onEventAlarm(context: Context) {
        notificationManager.postEventNotifications(context, false, null);
        alarmScheduler.rescheduleAlarms(context, getSettings(context), quietHoursManager);
    }


    fun onAppUpdated(context: Context) {

        val changes = EventsStorage(context).use { calendarReloadManager.reloadCalendar(context, it, calendarProvider) }
        notificationManager.postEventNotifications(context, true, null);

        alarmScheduler.rescheduleAlarms(context, getSettings(context), quietHoursManager);

        if (changes)
            UINotifierService.notifyUI(context, false);
    }

    fun onBootComplete(context: Context) {
        val changes = EventsStorage(context).use { calendarReloadManager.reloadCalendar(context, it, calendarProvider) };
        notificationManager.postEventNotifications(context, true, null);

        alarmScheduler.rescheduleAlarms(context, getSettings(context), quietHoursManager);

        if (changes)
            UINotifierService.notifyUI(context, false);
    }

    fun onCalendarChanged(context: Context) {

        val changes = EventsStorage(context).use { calendarReloadManager.reloadCalendar(context, it, calendarProvider) }
        if (changes) {
            notificationManager.postEventNotifications(context, true, null);

            alarmScheduler.rescheduleAlarms(context, getSettings(context), quietHoursManager);

            UINotifierService.notifyUI(context, false);
        }
    }

    fun onCalendarEventFired(context: Context, event: EventAlertRecord): Boolean {

        var ret = false

        if (event.calendarId == -1L || getSettings(context).getCalendarIsHandled(event.calendarId)) {

            EventsStorage(context).use {
                db ->

                if (event.isRepeating) {
                    // repeating event - always simply add
                    db.addEvent(event)
                    notificationManager.onEventAdded(context, event)
                } else {
                    // non-repeating event - make sure we don't create two records with the same eventId
                    val oldEvents

                        = db.getEventInstances(event.eventId)

                    logger.info("Non-repeating event, already have ${oldEvents.size} old events with same event id ${event.eventId}, removing old")

                    try {
                        // delete old instances for the same event id (should be only one, but who knows)
                        for (oldEvent in oldEvents) {
                            db.deleteEvent(oldEvent)
                            notificationManager.onEventDismissed(context, oldEvent.eventId, oldEvent.notificationId)
                        }
                    } catch (ex: Exception) {
                        logger.error("exception while removing old events: ${ex.message}");
                    }

                    // add newly fired event
                    db.addEvent(event)
                    notificationManager.onEventAdded(context, event)
                }
            }

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
                        if (snoozeDelay > 0L)
                            currentTime + snoozeDelay
                        else
                            event.displayedStartTime - Math.abs(snoozeDelay) // same as "event.instanceStart + snoozeDelay" but a little bit more readable

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

    fun snoozeAllEvents(context: Context, snoozeDelay: Long, isChange: Boolean): SnoozeResult? {

        var ret: SnoozeResult? = null

        val currentTime = System.currentTimeMillis()

        var snoozedUntil = 0L

        EventsStorage(context).use {
            db ->
            val events = db.events

            // Don't allow events to have exactly the same "snoozedUntil", so to have
            // predicted sorting order, so add a tiny (0.001s per event) adjust to each
            // snoozed time

            var snoozeAdjust = 0

            for (event in events) {

                val newSnoozeUntil = currentTime + snoozeDelay + snoozeAdjust

                if (isChange || event.snoozedUntil == 0L || event.snoozedUntil < newSnoozeUntil) {
                    db.updateEvent(event,
                        snoozedUntil = newSnoozeUntil,
                        lastEventVisibility = currentTime)
                    ++snoozeAdjust

                    snoozedUntil = newSnoozeUntil
                }
            }
        }

        if (snoozedUntil != 0L) {

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
            val changes = EventsStorage(context).use { calendarReloadManager.reloadCalendar(context, it, calendarProvider) };
            notificationManager.postEventNotifications(context, true, null)

            alarmScheduler.rescheduleAlarms(context, getSettings(context), quietHoursManager);

            if (changes)
                UINotifierService.notifyUI(context, true);
        }
    }

    fun onTimeChanged(context: Context) {
        alarmScheduler.rescheduleAlarms(context, getSettings(context), quietHoursManager);
    }

    fun dismissEvent(context: Context, db: EventsStorage, eventId: Long, instanceStartTime: Long, notificationId: Int, notifyActivity: Boolean) {

        logger.debug("Removing event id $eventId from DB, and dismissing notification")

        db.deleteEvent(eventId, instanceStartTime)

        notificationManager.onEventDismissed(context, eventId, notificationId);

        alarmScheduler.rescheduleAlarms(context, getSettings(context), quietHoursManager);

        if (notifyActivity)
            UINotifierService.notifyUI(context, true);
    }

    fun dismissEvent(context: Context, event: EventAlertRecord) {
        EventsStorage(context).use {
            dismissEvent(context, it, event.eventId, event.instanceStartTime, event.notificationId, false)
        }
    }

    fun dismissEvent(context: Context, eventId: Long, instanceStartTime: Long, notificationId: Int, notifyActivity: Boolean = true) {
        EventsStorage(context).use {
            dismissEvent(context, it, eventId, instanceStartTime, notificationId, notifyActivity)
        }
    }

    fun restoreEvent(context: Context, event: EventAlertRecord) {
        EventsStorage(context).use { it.addEvent(event) }
        notificationManager.onEventRestored(context, event)
    }

    fun moveEvent(context: Context, event: EventAlertRecord, addTime: Long): Boolean {

        val moved = calendarProvider.moveEvent(context, event, addTime)

        if (moved) {
            logger.info("moveEvent: Moved event ${event.eventId} by ${addTime / 1000L} seconds")
            dismissEvent(context, event.eventId, event.instanceStartTime, event.notificationId)
        }

        return moved
    }
}
