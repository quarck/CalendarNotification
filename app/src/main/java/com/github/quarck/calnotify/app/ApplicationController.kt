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
import com.github.quarck.calnotify.calendar.*
import com.github.quarck.calnotify.dismissedeventsstorage.DismissedEventsStorage
import com.github.quarck.calnotify.dismissedeventsstorage.EventDismissType
import com.github.quarck.calnotify.eventsstorage.EventsStorage
import com.github.quarck.calnotify.eventsstorage.EventsStorageInterface
import com.github.quarck.calnotify.globalState
import com.github.quarck.calnotify.logs.Logger
import com.github.quarck.calnotify.notification.EventNotificationManager
import com.github.quarck.calnotify.notification.EventNotificationManagerInterface
import com.github.quarck.calnotify.notification.TextToSpeechNotificationManager
import com.github.quarck.calnotify.notification.TextToSpeechNotificationManagerInterface
import com.github.quarck.calnotify.quiethours.QuietHoursManager
import com.github.quarck.calnotify.quiethours.QuietHoursManagerInterface
import com.github.quarck.calnotify.textutils.EventFormatter
import com.github.quarck.calnotify.ui.SnoozeActivityNoRecents
import com.github.quarck.calnotify.ui.UINotifierService

object ApplicationController : EventMovedHandler {

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

        logger.info("onEventAlarm at ${System.currentTimeMillis()} -- we need to remind about snoozed event");

        context.globalState.lastTimerBroadcastReceived = System.currentTimeMillis()
        notificationManager.postEventNotifications(context, EventFormatter(context), false, null);
        alarmScheduler.rescheduleAlarms(context, getSettings(context), quietHoursManager);
    }

    fun onAppUpdated(context: Context) {

        logger.info("Application updated, reloading calendar")

        val changes = EventsStorage(context).use {
            calendarReloadManager.reloadCalendar(context, it, calendarProvider, this) }
        notificationManager.postEventNotifications(context, EventFormatter(context), true, null);

        alarmScheduler.rescheduleAlarms(context, getSettings(context), quietHoursManager);

        if (changes)
            UINotifierService.notifyUI(context, false);
    }

    fun onBootComplete(context: Context) {

        logger.info("System rebooted - reloading calendar")

        val changes = EventsStorage(context).use { calendarReloadManager.reloadCalendar(context, it, calendarProvider, this) };
        notificationManager.postEventNotifications(context, EventFormatter(context), true, null);

        alarmScheduler.rescheduleAlarms(context, getSettings(context), quietHoursManager);

        if (changes)
            UINotifierService.notifyUI(context, false);
    }

    fun onCalendarChanged(context: Context) {

        logger.info("Calendar changed notification received")

        val changes = EventsStorage(context).use { calendarReloadManager.reloadCalendar(context, it, calendarProvider, this) }
        if (changes) {
            notificationManager.postEventNotifications(context, EventFormatter(context), true, null);

            alarmScheduler.rescheduleAlarms(context, getSettings(context), quietHoursManager);

            UINotifierService.notifyUI(context, false);
        } else {
            logger.info("No caclendar changes detected")
        }
    }

    fun onCalendarEventFired(context: Context, event: EventAlertRecord): Boolean {

        var ret = false

        if (event.calendarId == -1L || getSettings(context).getCalendarIsHandled(event.calendarId)) {

            logger.info("Calendar event fired, calendar id ${event.calendarId}, eventId ${event.eventId}, instance start time ${event.instanceStartTime}, alertTime=${event.alertTime}")

            // 1st step - save event into DB
            EventsStorage(context).use {
                db ->

                if (event.isRepeating) {
                    // repeating event - always simply add
                    db.addEvent(event)
                    notificationManager.onEventAdded(context, EventFormatter(context), event)
                } else {
                    // non-repeating event - make sure we don't create two records with the same eventId
                    val oldEvents
                        = db.getEventInstances(event.eventId)

                    logger.info("Non-repeating event, already have ${oldEvents.size} old events with same event id ${event.eventId}, removing old")

                    try {
                        // delete old instances for the same event id (should be only one, but who knows)
                        for (oldEvent in oldEvents) {
                            db.deleteEvent(oldEvent)
                            notificationManager.onEventDismissed(context, EventFormatter(context), oldEvent.eventId, oldEvent.notificationId)
                        }
                    } catch (ex: Exception) {
                        logger.error("exception while removing old events: ${ex.message}");
                    }

                    // add newly fired event
                    db.addEvent(event)
                    notificationManager.onEventAdded(context, EventFormatter(context), event)
                }
            }

            // 2nd step - re-open new DB instance and make sure that event:
            // * is there
            // * is not set as visible
            // * is not snoozed
            EventsStorage(context).use {
                db ->

                if (event.isRepeating) {
                    // return true only if we can confirm, by reading event again from DB
                    // that it is there
                    // Caller is using our return value as "safeToRemoveOriginalReminder" flag
                    val dbEvent = db.getEvent(event.eventId, event.instanceStartTime)
                    ret = dbEvent != null && dbEvent.snoozedUntil == 0L

                } else {
                    // return true only if we can confirm, by reading event again from DB
                    // that it is there
                    // Caller is using our return value as "safeToRemoveOriginalReminder" flag
                    val dbEvents = db.getEventInstances(event.eventId)
                    ret = dbEvents.size == 1 && dbEvents[0].snoozedUntil == 0L
                }
            }

            if (!ret)
                logger.error("Error adding event with id ${event.eventId}, cal id ${event.calendarId}, " +
                        "instance st ${event.instanceStartTime}, repeating: " +
                        "${event.isRepeating}, allDay: ${event.isAllDay}, alertTime=${event.alertTime}");

            // reload all the other events - check if there are any changes yet
            val changes = EventsStorage(context).use { calendarReloadManager.reloadCalendar(context, it, calendarProvider, this) }

            //
            alarmScheduler.rescheduleAlarms(context, getSettings(context), quietHoursManager);

            UINotifierService.notifyUI(context, false);

            logger.info("event added: ${event.eventId} (cal id: ${event.calendarId}");

        } else {
            logger.info("Event ${event.eventId} belongs to calendar ${event.calendarId} which is not handled, skipping");
        }

        return ret
    }

    override fun onEventMoved(
            context: Context,
            db: EventsStorageInterface,
            oldEvent: EventAlertRecord,
            newEvent: EventRecord,
            newAlertTime: Long
    ): Boolean {

        var ret = false

        if (!getSettings(context).notificationAutoDismissOnReschedule)
            return false

        val oldTime = oldEvent.displayedStartTime
        val newTime = newEvent.startTime

        if (newTime - oldTime > Consts.EVENT_MOVE_THRESHOLD) {
            logger.info("Event ${oldEvent.eventId} moved by ${newTime - oldTime} ms")

            if (newAlertTime > System.currentTimeMillis() + Consts.ALARM_THRESHOULD) {

                logger.info("Event ${oldEvent.eventId} - alarm in the future confirmed, at $newAlertTime, auto-dismissing notification")

                dismissEvent(
                        context,
                        db,
                        oldEvent.copy(startTime = newEvent.startTime, endTime =  newEvent.endTime),
                        EventDismissType.AutoDismissedDueToCalendarMove,
                        true)

                ret = true

                if (getSettings(context).debugNotificationAutoDismiss)
                    notificationManager.postNotificationsAutoDismissedDebugMessage(context)
            }
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
            notificationManager.onEventSnoozed(context, EventFormatter(context), snoozedEvent.eventId, snoozedEvent.notificationId);

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
        notificationManager.fireEventReminder(context, EventFormatter(context));
    }

    fun onMainActivityCreate(context: Context?) {
        if (context != null) {
            val settings = getSettings(context)

            if (settings.versionCodeFirstInstalled == 0L) {
                val pInfo = context.packageManager.getPackageInfo(context.packageName, 0);
                settings.versionCodeFirstInstalled = pInfo.versionCode.toLong();
            }
        }
    }

    fun onMainActivityStarted(context: Context?) {
    }

    fun onMainActivityResumed(context: Context?, shouldRepost: Boolean) {
        if (context != null) {
            val changes = EventsStorage(context).use { calendarReloadManager.reloadCalendar(context, it, calendarProvider, this) };

            if (shouldRepost || changes) {
                notificationManager.postEventNotifications(context, EventFormatter(context), true, null)
                context.globalState.lastNotificationRePost = System.currentTimeMillis()
            }

            alarmScheduler.rescheduleAlarms(context, getSettings(context), quietHoursManager);

            if (changes)
                UINotifierService.notifyUI(context, true);
        }
    }

    fun onTimeChanged(context: Context) {
        alarmScheduler.rescheduleAlarms(context, getSettings(context), quietHoursManager);
    }

    fun dismissEvent(
            context: Context,
            db: EventsStorageInterface,
            event: EventAlertRecord,
            dismissType: EventDismissType,
            notifyActivity: Boolean) {

        logger.debug("Removing event id ${event.eventId} / instance ${event.instanceStartTime}")

        if (dismissType.shouldKeep && Settings(context).keepHistory) {
            DismissedEventsStorage(context).use {
                db ->
                db.addEvent(dismissType, event)
            }
        }

        db.deleteEvent(event.eventId, event.instanceStartTime)

        notificationManager.onEventDismissed(context, EventFormatter(context), event.eventId, event.notificationId);

        alarmScheduler.rescheduleAlarms(context, getSettings(context), quietHoursManager);

        if (notifyActivity)
            UINotifierService.notifyUI(context, true);
    }

    fun dismissEvent(context: Context, dismissType: EventDismissType, event: EventAlertRecord) {
        EventsStorage(context).use {
            db -> dismissEvent(context,  db,  event,  dismissType, false)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun dismissEvent(
            context: Context,
            dismissType: EventDismissType,
            eventId: Long,
            instanceStartTime: Long,
            notificationId: Int,
            notifyActivity: Boolean = true) {

        EventsStorage(context).use {
            db ->
            val event = db.getEvent(eventId, instanceStartTime)
            if (event != null)
                dismissEvent(context, db, event, dismissType, notifyActivity)
        }
    }

    fun restoreEvent(context: Context, event: EventAlertRecord) {

        val toRestore =
                event.copy(
                        notificationId = 0, // re-assign new notification ID since old one might already in use
                        displayStatus = EventDisplayStatus.Hidden ) // ensure correct visibility is set

        EventsStorage(context).use {
            db -> db.addEvent(toRestore)
        }

        notificationManager.onEventRestored(context, EventFormatter(context), toRestore)

        DismissedEventsStorage(context).use {
            db -> db.deleteEvent(event)
        }
    }

    fun moveEvent(context: Context, event: EventAlertRecord, addTime: Long): Boolean {

        val moved = calendarProvider.moveEvent(context, event, addTime)

        if (moved) {
            logger.info("moveEvent: Moved event ${event.eventId} by ${addTime / 1000L} seconds")

            EventsStorage(context).use {
                db ->
                dismissEvent(
                    context,
                        db,
                        event,
                        EventDismissType.EventMovedUsingApp,
                        true)
            }
        }

        return moved
    }
}
