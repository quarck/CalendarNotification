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
import android.os.Handler
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.calendar.*
import com.github.quarck.calnotify.calendarmonitor.CalendarMonitor
import com.github.quarck.calnotify.calendarmonitor.CalendarMonitorInterface
import com.github.quarck.calnotify.dismissedeventsstorage.DismissedEventsStorage
import com.github.quarck.calnotify.dismissedeventsstorage.EventDismissType
import com.github.quarck.calnotify.eventsstorage.EventsStorage
import com.github.quarck.calnotify.eventsstorage.EventsStorageInterface
import com.github.quarck.calnotify.globalState
import com.github.quarck.calnotify.logs.Logger
import com.github.quarck.calnotify.notification.EventNotificationManager
import com.github.quarck.calnotify.notification.EventNotificationManagerInterface
import com.github.quarck.calnotify.persistentState
import com.github.quarck.calnotify.quiethours.QuietHoursManager
import com.github.quarck.calnotify.quiethours.QuietHoursManagerInterface
import com.github.quarck.calnotify.textutils.EventFormatter
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

    private val calendarMonitor: CalendarMonitorInterface by lazy { CalendarMonitor(calendarProvider) }

    val CalendarMonitorService: CalendarMonitorInterface
        get() = calendarMonitor

    fun hasActiveEvents(context: Context) =
            EventsStorage(context).use { it.events.filter { it.snoozedUntil == 0L && it.isNotSpecial }.any() }

    fun onEventAlarm(context: Context) {

        logger.info("onEventAlarm at ${System.currentTimeMillis()} -- we need to remind about snoozed event");

        val alarmWasExpectedAt = context.persistentState.nextSnoozeAlarmExpectedAt
        val currentTime = System.currentTimeMillis()

        context.globalState.lastTimerBroadcastReceived = System.currentTimeMillis()
        notificationManager.postEventNotifications(context, EventFormatter(context), false, null);
        alarmScheduler.rescheduleAlarms(context, getSettings(context), quietHoursManager);


        if (currentTime > alarmWasExpectedAt + Consts.ALARM_THRESHOLD) {
            this.onSnoozeAlarmLate(context, currentTime, alarmWasExpectedAt)
        }
    }

    fun onAppUpdated(context: Context) {

        logger.info("Application updated, reloading calendar")

        val changes = EventsStorage(context).use {
            calendarReloadManager.reloadCalendar(context, it, calendarProvider, this, Consts.MAX_CAL_RELOAD_TIME_ON_UPDATE_MILLIS)
        }
        // this will post event notifications for existing known events
        notificationManager.postEventNotifications(context, EventFormatter(context), true, null);

        // this might fire new notifications
        val monitorChanges = calendarMonitor.onUpgrade(context)

        alarmScheduler.rescheduleAlarms(context, getSettings(context), quietHoursManager);

        if (changes || monitorChanges)
            UINotifierService.notifyUI(context, false);
    }

    fun onBootComplete(context: Context) {

        logger.info("System rebooted - reloading calendar")

        val changes = EventsStorage(context).use {
            calendarReloadManager.reloadCalendar(context, it, calendarProvider, this, Consts.MAX_CAL_RELOAD_TIME_ON_BOOT_MILLIS)
        };
        // this will post event notifications for existing known events
        notificationManager.postEventNotifications(context, EventFormatter(context), true, null);

        // this might fire new notifications
        val monitorChanges = calendarMonitor.onBoot(context)

        alarmScheduler.rescheduleAlarms(context, getSettings(context), quietHoursManager);

        if (changes || monitorChanges)
            UINotifierService.notifyUI(context, false);
    }

    fun handleOnCalendarChanged(context: Context) {

        val changes = EventsStorage(context).use {
            db ->
            calendarReloadManager.reloadCalendar(context, db, calendarProvider, this, Consts.MAX_CAL_RELOAD_TIME_ON_CALENDAR_CHANGED_MILLIS)
        }

        if (changes) {
            notificationManager.postEventNotifications(context, EventFormatter(context), true, null);

            alarmScheduler.rescheduleAlarms(context, getSettings(context), quietHoursManager);

            UINotifierService.notifyUI(context, false);
        }
        else {
            logger.info("No caclendar changes detected")
        }
    }

    fun onCalendarChanged(context: Context) {

        logger.info("Calendar changed notification received")

        calendarMonitor.onCalendarChange(context)

        val delayInMilliseconds = 2000L
        Handler().postDelayed({ handleOnCalendarChanged(context) }, delayInMilliseconds)
    }

    // some housekeeping that we have to do after firing calendar event
    fun afterCalendarEventFired(context: Context, reloadCalendar: Boolean = true) {

        if (reloadCalendar) {
            // reload all the other events - check if there are any changes yet
            EventsStorage(context).use {
                calendarReloadManager.reloadCalendar(context, it, calendarProvider, this, Consts.MAX_CAL_RELOAD_TIME_ON_AFTER_FIRE_MILLIS)
            }
        }

        alarmScheduler.rescheduleAlarms(context, getSettings(context), quietHoursManager);

        UINotifierService.notifyUI(context, false);
    }

    fun postEventNotifications(context: Context, events: Collection<EventAlertRecord>) {

        if (events.size == 1)
            notificationManager.onEventAdded(context, EventFormatter(context), events.first())
        else
            notificationManager.postEventNotifications(context, EventFormatter(context), false, null)

    }

    fun shouldMarkEventAsHandledAndSkip(context: Context, event: EventAlertRecord): Boolean {

        val settings = getSettings(context)

        if (event.eventStatus == EventStatus.Cancelled && settings.dontShowCancelledEvents) {
            // indicate that we should mark as handled in the provider and skip
            logger.info("Event ${event.eventId} has status Cancelled and user requested to not show canelled events")
            return true
        }

        if (event.attendanceStatus == AttendanceStatus.Declined && settings.dontShowDeclinedEvents) {
            // indicate that we should mark as handled in the provider and skip
            logger.info("Event ${event.eventId} has status Declined and user requested to not show declined events")
            return true
        }

        return false
    }


    fun registerNewEvent(context: Context, event: EventAlertRecord): Boolean {

        var ret = false

        val settings = getSettings(context)

        if (event.calendarId != -1L && !settings.getCalendarIsHandled(event.calendarId)) {
            logger.info("Event ${event.eventId} belongs to calendar ${event.calendarId} which is not handled, skipping");
            return ret;
        }

        logger.info("Calendar event fired, calendar id ${event.calendarId}, eventId ${event.eventId}, instance start time ${event.instanceStartTime}, alertTime=${event.alertTime}")

        // 1st step - save event into DB
        EventsStorage(context).use {
            db ->

            if (event.isNotSpecial)
                event.lastEventVisibility = System.currentTimeMillis()
            else
                event.lastEventVisibility = Long.MAX_VALUE

            if (event.isRepeating) {
                // repeating event - always simply add
                db.addEvent(event) // ignoring result as we are using other way of validating
                //notificationManager.onEventAdded(context, EventFormatter(context), event)
            }
            else {
                // non-repeating event - make sure we don't create two records with the same eventId
                val oldEvents = db.getEventInstances(event.eventId)

                logger.info("Non-repeating event, already have ${oldEvents.size} old events with same event id ${event.eventId}, removing old")

                try {
                    // delete old instances for the same event id (should be only one, but who knows)
                    for (oldEvent in oldEvents) {
                        db.deleteEvent(oldEvent)
                        notificationManager.onEventDismissed(context, EventFormatter(context), oldEvent.eventId, oldEvent.notificationId)
                    }
                }
                catch (ex: Exception) {
                    logger.error("exception while removing old events: ${ex.message}");
                }

                // add newly fired event
                db.addEvent(event)
                //notificationManager.onEventAdded(context, EventFormatter(context), event)
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

            }
            else {
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
        else
            logger.info("event added: ${event.eventId} (cal id: ${event.calendarId})");

        return ret
    }

    fun registerNewEvents(
            context: Context,
            pairs: List<Pair<MonitorEventAlertEntry, EventAlertRecord>>
    ): ArrayList<Pair<MonitorEventAlertEntry, EventAlertRecord>> {

        val settings = getSettings(context)

        val handledCalendars = calendarProvider.getHandledCalendarsIds(context, settings)

        val handledPairs = pairs.filter {
            (_, event) ->
            handledCalendars.contains(event.calendarId) || event.calendarId == -1L
        }

        val pairsToAdd = arrayListOf<Pair<MonitorEventAlertEntry, EventAlertRecord>>()
        val eventsToDismiss = arrayListOf<EventAlertRecord>()

        // 1st step - save event into DB
        EventsStorage(context).use {
            db ->

            for ((alert, event) in handledPairs) {

                logger.info("Calendar event fired, calendar id ${event.calendarId}, eventId ${event.eventId}, instance start time ${event.instanceStartTime}, alertTime=${event.alertTime}")

                if (event.isRepeating) {
                    // repeating event - always simply add
                    pairsToAdd.add(Pair(alert, event))
                }
                else {
                    // non-repeating event - make sure we don't create two records with the same eventId
                    val oldEvents = db.getEventInstances(event.eventId)

                    logger.info("Non-repeating event, already have ${oldEvents.size} old events with same event id ${event.eventId}, removing old")

                    try {
                        // delete old instances for the same event id (should be only one, but who knows)
                        eventsToDismiss.addAll(oldEvents)
                    }
                    catch (ex: Exception) {
                        logger.error("exception while removing old events: ${ex.message}");
                    }

                    // add newly fired event
                    pairsToAdd.add(Pair(alert, event))
                }
            }

            if (!eventsToDismiss.isEmpty()) {
                // delete old instances for the same event id (should be only one, but who knows)
                db.deleteEvents(eventsToDismiss)

                notificationManager.onEventsDismissed(
                        context,
                        EventFormatter(context),
                        eventsToDismiss,
                        postNotifications = false   // don't repost notifications at this stage, but only dismiss currently active
                )
            }

            if (!pairsToAdd.isEmpty()) {

                var currentTime = System.currentTimeMillis()
                for ((_, event) in pairsToAdd)
                    event.lastEventVisibility = currentTime++

                db.addEvents(pairsToAdd.map { it.second }) // ignoring result of add - here we are using another way to validate succesfull add
            }
        }

        // 2nd step - re-open new DB instance and make sure that event:
        // * is there
        // * is not set as visible
        // * is not snoozed

        val validPairs = arrayListOf<Pair<MonitorEventAlertEntry, EventAlertRecord>>()

        EventsStorage(context).use {
            db ->

            for ((alert, event) in pairsToAdd) {

                if (event.isRepeating) {
                    // return true only if we can confirm, by reading event again from DB
                    // that it is there
                    // Caller is using our return value as "safeToRemoveOriginalReminder" flag
                    val dbEvent = db.getEvent(event.eventId, event.instanceStartTime)

                    if (dbEvent != null && dbEvent.snoozedUntil == 0L) {
                        validPairs.add(Pair(alert, event))
                    }
                    else {
                        logger.error("Failed to add event ${event.eventId} ${event.alertTime} ${event.instanceStartTime} into DB properly")
                    }
                }
                else {
                    // return true only if we can confirm, by reading event again from DB
                    // that it is there
                    // Caller is using our return value as "safeToRemoveOriginalReminder" flag
                    val dbEvents = db.getEventInstances(event.eventId)

                    if (dbEvents.size == 1 && dbEvents[0].snoozedUntil == 0L) {
                        validPairs.add(Pair(alert, event))
                    }
                    else {
                        logger.error("Failed to add event ${event.eventId} ${event.alertTime} ${event.instanceStartTime} into DB properly")
                    }
                }
            }
        }

        logger.info("registerNewEvents: Added ${validPairs.size} events out of ${pairs.size}")

        return validPairs

    }


//    override fun onEventMoved(
//            context: Context,
//            db: EventsStorageInterface,
//            oldEvent: EventAlertRecord,
//            newEvent: EventRecord,
//            newAlertTime: Long
//    ): Boolean {
//
//        var ret = false
//
//        if (!getSettings(context).notificationAutoDismissOnReschedule)
//            return false
//
//        val oldTime = oldEvent.displayedStartTime
//        val newTime = newEvent.newInstanceStartTime
//
//        if (newTime - oldTime > Consts.EVENT_MOVE_THRESHOLD) {
//            logger.info("Event ${oldEvent.eventId} moved by ${newTime - oldTime} ms")
//
//            if (newAlertTime > System.currentTimeMillis() + Consts.ALARM_THRESHOLD) {
//
//                logger.info("Event ${oldEvent.eventId} - alarm in the future confirmed, at $newAlertTime, auto-dismissing notification")
//
//                dismissEvent(
//                        context,
//                        db,
//                        oldEvent.copy(newInstanceStartTime = newEvent.newInstanceStartTime, newInstanceEndTime =  newEvent.newInstanceEndTime),
//                        EventDismissType.AutoDismissedDueToCalendarMove,
//                        true)
//
//                ret = true
//
//                if (getSettings(context).debugNotificationAutoDismiss)
//                    notificationManager.postNotificationsAutoDismissedDebugMessage(context)
//            }
//        }
//
//        return ret
//    }

    override fun checkShouldRemoveMovedEvent(
            context: Context,
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
            if (newAlertTime > System.currentTimeMillis() + Consts.ALARM_THRESHOLD) {

                logger.info("Event ${oldEvent.eventId} - alarm in the future confirmed, at $newAlertTime, marking for auto-dismissal")

//                dismissEvent(
//                        context,
//                        db,
//                        oldEvent.copy(newInstanceStartTime = newEvent.newInstanceStartTime, newInstanceEndTime =  newEvent.newInstanceEndTime),
//                        EventDismissType.AutoDismissedDueToCalendarMove,
//                        true)

                ret = true

//                if (getSettings(context).debugNotificationAutoDismiss)
//                    notificationManager.postNotificationsAutoDismissedDebugMessage(context)
            }
            else {
                logger.info("Event ${oldEvent.eventId} moved by ${newTime - oldTime} ms - not enought to auto-dismiss")
            }
        }

        return ret
    }


    fun onReminderAlarmLate(context: Context, currentTime: Long, alarmWasExpectedAt: Long) {

        if (getSettings(context).debugAlarmDelays) {

            val warningMessage = "Expected: $alarmWasExpectedAt, " +
                    "received: $currentTime, ${(currentTime - alarmWasExpectedAt) / 1000L}s late"

            notificationManager.postNotificationsAlarmDelayDebugMessage(context, "Reminder alarm was late!", warningMessage)
        }
    }

    fun onSnoozeAlarmLate(context: Context, currentTime: Long, alarmWasExpectedAt: Long) {

        if (getSettings(context).debugAlarmDelays) {

            val warningMessage = "Expected: $alarmWasExpectedAt, " +
                    "received: $currentTime, ${(currentTime - alarmWasExpectedAt) / 1000L}s late"

            notificationManager.postNotificationsSnoozeAlarmDelayDebugMessage(context, "Snooze alarm was late!", warningMessage)
        }
    }

    fun snoozeEvent(context: Context, eventId: Long, instanceStartTime: Long, snoozeDelay: Long): SnoozeResult? {

        var ret: SnoozeResult? = null

        val currentTime = System.currentTimeMillis()

        val snoozedEvent: EventAlertRecord? =
                EventsStorage(context).use {
                    db ->
                    var event = db.getEvent(eventId, instanceStartTime)

                    if (event != null) {
                        val snoozedUntil =
                                if (snoozeDelay > 0L)
                                    currentTime + snoozeDelay
                                else
                                    event.displayedStartTime - Math.abs(snoozeDelay) // same as "event.instanceStart + snoozeDelay" but a little bit more readable

                        val (success, newEvent) = db.updateEvent(event,
                                snoozedUntil = snoozedUntil,
                                lastEventVisibility = currentTime,
                                displayStatus = EventDisplayStatus.Hidden)

                        event = if (success) newEvent else null
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

        var allSuccess = true

        EventsStorage(context).use {
            db ->
            val events = db.events.filter { it.isNotSpecial }

            // Don't allow events to have exactly the same "snoozedUntil", so to have
            // predicted sorting order, so add a tiny (0.001s per event) adjust to each
            // snoozed time

            var snoozeAdjust = 0

            for (event in events) {

                val newSnoozeUntil = currentTime + snoozeDelay + snoozeAdjust

                if (isChange || event.snoozedUntil == 0L || event.snoozedUntil < newSnoozeUntil) {
                    val (success, _) = db.updateEvent(event,
                            snoozedUntil = newSnoozeUntil,
                            lastEventVisibility = currentTime)

                    allSuccess = allSuccess && success;

                    ++snoozeAdjust

                    snoozedUntil = newSnoozeUntil
                }
            }
        }

        if (allSuccess && snoozedUntil != 0L) {

            notificationManager.onAllEventsSnoozed(context)

            alarmScheduler.rescheduleAlarms(context, getSettings(context), quietHoursManager);

            val silentUntil = QuietHoursManager.getSilentUntil(getSettings(context), snoozedUntil)

            ret = SnoozeResult(SnoozeType.Snoozed, snoozedUntil, silentUntil)
        }

        return ret
    }

    fun fireEventReminder(context: Context, itIsAfterQuietHoursReminder: Boolean) {
        notificationManager.fireEventReminder(context, itIsAfterQuietHoursReminder);
    }

    fun cleanupEventReminder(context: Context) {
        notificationManager.cleanupEventReminder(context);
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

    @Suppress("UNUSED_PARAMETER")
    fun onMainActivityStarted(context: Context?) {
    }

    fun onMainActivityResumed(
            context: Context?,
            shouldRepost: Boolean,
            monitorSettingsChanged: Boolean
    ) {
        if (context != null) {

            cleanupEventReminder(context)

            val changes = EventsStorage(context).use {
                calendarReloadManager.reloadCalendar(context, it, calendarProvider, this, Consts.MAX_CAL_RELOAD_TIME_ON_UI_START_MILLIS)
            };

            // this might fire new notifications
            val monitorChanges = calendarMonitor.onAppResumed(context, monitorSettingsChanged)

            if (shouldRepost || changes || monitorChanges) {
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
        calendarMonitor.onSystemTimeChange(context)
    }

    fun dismissEvents(
            context: Context,
            db: EventsStorageInterface,
            events: Collection<EventAlertRecord>,
            dismissType: EventDismissType,
            notifyActivity: Boolean) {

        logger.debug("Dismissing ${events.size}  events")

        if (dismissType.shouldKeep && Settings(context).keepHistory) {
            DismissedEventsStorage(context).use {
                db ->
                db.addEvents(dismissType, events)
            }
        }

        if (db.deleteEvents(events) == events.size) {

            notificationManager.onEventsDismissed(context, EventFormatter(context), events);

            alarmScheduler.rescheduleAlarms(context, getSettings(context), quietHoursManager);

            if (notifyActivity)
                UINotifierService.notifyUI(context, true);
        }
    }

    fun anyForDismissAllButRecentAndSnoozed(events: Array<EventAlertRecord>): Boolean {

        val currentTime = System.currentTimeMillis()

        val ret = events.any {
            event ->
            (event.lastEventVisibility < currentTime - Consts.DISMISS_ALL_THRESHOLD) &&
                    (event.snoozedUntil == 0L)
        }

        return ret
    }

    fun dismissAllButRecentAndSnoozed(context: Context, dismissType: EventDismissType) {

        val currentTime = System.currentTimeMillis()

        EventsStorage(context).use {
            db ->
            val eventsToDismiss = db.events.filter {
                event ->
                (event.lastEventVisibility < currentTime - Consts.DISMISS_ALL_THRESHOLD) &&
                        (event.snoozedUntil == 0L) &&
                        event.isNotSpecial
            }
            dismissEvents(context, db, eventsToDismiss, dismissType, false)
        }
    }

    fun dismissEvent(
            context: Context,
            db: EventsStorageInterface,
            event: EventAlertRecord,
            dismissType: EventDismissType,
            notifyActivity: Boolean) {

        logger.debug("Removing event id ${event.eventId} / instance ${event.instanceStartTime}")

        if (dismissType.shouldKeep && Settings(context).keepHistory && event.isNotSpecial) {
            DismissedEventsStorage(context).use {
                db ->
                db.addEvent(dismissType, event)
            }
        }

        if (db.deleteEvent(event.eventId, event.instanceStartTime)) {

            notificationManager.onEventDismissed(context, EventFormatter(context), event.eventId, event.notificationId);

            alarmScheduler.rescheduleAlarms(context, getSettings(context), quietHoursManager);

            if (notifyActivity)
                UINotifierService.notifyUI(context, true);
        }
    }

    fun dismissEvent(context: Context, dismissType: EventDismissType, event: EventAlertRecord) {
        EventsStorage(context).use {
            db ->
            dismissEvent(context, db, event, dismissType, false)
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
            if (event != null) {
                logger.debug("Dismissing event ${event.eventId} / ${event.instanceStartTime}")
                dismissEvent(context, db, event, dismissType, notifyActivity)
            }
            else {
                logger.error("dismissEvent: can't find event $eventId, $instanceStartTime")
            }
        }
    }

    fun restoreEvent(context: Context, event: EventAlertRecord) {

        val toRestore =
                event.copy(
                        notificationId = 0, // re-assign new notification ID since old one might already in use
                        displayStatus = EventDisplayStatus.Hidden) // ensure correct visibility is set

        val successOnAdd =
                EventsStorage(context).use {
                    db ->
                    val ret = db.addEvent(toRestore)
                    calendarReloadManager.reloadSingleEvent(context, db, toRestore, calendarProvider, null)
                    ret
                }

        if (successOnAdd) {
            notificationManager.onEventRestored(context, EventFormatter(context), toRestore)

            DismissedEventsStorage(context).use {
                db ->
                db.deleteEvent(event)
            }
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

    // used for debug purpose
    @Suppress("unused")
    fun forceRepostNotifications(context: Context) {
        notificationManager.postEventNotifications(context, EventFormatter(context), true, null);
    }

    // used for debug purpose
    @Suppress("unused")
    fun postNotificationsAutoDismissedDebugMessage(context: Context) {
        notificationManager.postNotificationsAutoDismissedDebugMessage(context)
    }
}
