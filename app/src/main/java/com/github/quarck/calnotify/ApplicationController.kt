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

package com.github.quarck.calnotify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.github.quarck.calnotify.broadcastreceivers.AlarmBroadcastReceiver
import com.github.quarck.calnotify.calendar.CalendarUtils
import com.github.quarck.calnotify.eventsstorage.EventDisplayStatus
import com.github.quarck.calnotify.eventsstorage.EventRecord
import com.github.quarck.calnotify.eventsstorage.EventsStorage
import com.github.quarck.calnotify.logs.Logger
import com.github.quarck.calnotify.notification.EventNotificationManager
import com.github.quarck.calnotify.notification.IEventNotificationManager
import com.github.quarck.calnotify.notification.ReminderAlarm
import com.github.quarck.calnotify.quiethours.QuietHoursManager
import com.github.quarck.calnotify.ui.UINotifierService
import com.github.quarck.calnotify.utils.alarmManager
import com.github.quarck.calnotify.utils.setExactCompat

object ApplicationController {
    private val notificationManager: IEventNotificationManager = EventNotificationManager()

    private val logger = Logger("EventsManager");

    private fun scheduleNextAlarmForEvents(context: Context) {

        logger.debug("scheduleEventAlarm called");

        var nextAlarm =
                EventsStorage(context).use {
                    it.events
                            .filter { it.snoozedUntil != 0L }
                            .map { it.snoozedUntil }
                            .min()
                };

        val intent = Intent(context, AlarmBroadcastReceiver::class.java);
        val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)

        val alarmManager = context.alarmManager

        if (nextAlarm != null) {

            val currentTime = System.currentTimeMillis()

            if (nextAlarm < currentTime) {
                logger.error("CRITICAL: nextAlarm=$nextAlarm is less than currentTime $currentTime");
                nextAlarm = currentTime + Consts.MINUTE_IN_SECONDS * 5 * 1000L;
            }

            logger.info("Scheduling next alarm at ${nextAlarm}, in ${(nextAlarm - currentTime) / 1000L} seconds");

            alarmManager.setExactCompat(AlarmManager.RTC_WAKEUP, nextAlarm, pendingIntent);
        } else {
            logger.info("No next events, cancelling alarms");

            alarmManager.cancel(pendingIntent)
        }
    }

    private fun scheduleNextAlarmForReminders(context: Context) {

        val settings = Settings(context);

        if (!settings.remindersEnabled && !settings.quietHoursOneTimeReminderEnabled)
            return;

        val hasActiveNotifications =
                EventsStorage(context).use {
                    it.events
                            .filter { it.snoozedUntil == 0L }
                            .any()
                }

        if (hasActiveNotifications) {

            val remindInterval = settings.remindersIntervalMillis
            var nextFire = System.currentTimeMillis() + remindInterval

            if (settings.quietHoursOneTimeReminderEnabled)
                nextFire = System.currentTimeMillis() + Consts.ALARM_THRESHOULD

            val quietUntil = QuietHoursManager.getSilentUntil(settings, nextFire)

            if (quietUntil != 0L) {
                logger.info("Reminder alarm moved from $nextFire to ${quietUntil+15} due to silent period");

                // give a little extra delay, so if events would fire precisely at the quietUntil,
                // reminders would wait a bit longer
                nextFire = quietUntil + Consts.ALARM_THRESHOULD
            }

            ReminderAlarm.scheduleAlarmMillisAt(context, nextFire);
        }
    }

    fun hasActiveEvents(context: Context) =
        EventsStorage(context).use { it.events.filter { it.snoozedUntil == 0L }.any() }

    fun onAlarm(context: Context?, intent: Intent?) {
        if (context != null) {
            notificationManager.postEventNotifications(context, false, null);
            scheduleNextAlarmForEvents(context);
            scheduleNextAlarmForReminders(context);
        } else {
            logger.error("onAlarm: context is null");
        }
    }


    private fun reloadCalendar(context: Context): Boolean {

        var repostNotifications = false

        EventsStorage(context).use {
            db ->

            val events = db.events

            val currentTime = System.currentTimeMillis()

            for (event in events) {

                try {
                    var newEvent = CalendarUtils.getEvent(context, event.eventId, event.alertTime)

                    if (newEvent == null ) {
                        newEvent = CalendarUtils.getEvent(context, event.eventId)

                        if (newEvent != null
                            && (newEvent.startTime - event.startTime > Consts.EVENT_MOVED_THRESHOLD)
                            && (newEvent.startTime - currentTime > Consts.EVENT_MOVED_THRESHOLD) ) {
                            // Here we have a confirmation that event was re-scheduled by user
                            // to some time in the future and that's why original event instance has disappeared
                            // - we are good to go to dismiss event reminder automatically
                            dismissEvent(context, db, event.eventId, event.notificationId, true);
                            val movedSec = (newEvent.startTime - event.startTime) / 1000L
                            logger.debug("Event ${event.eventId} disappeared, event was moved further by $movedSec seconds");
                        } else {
                            // Here we can't confirm that event was moved into the future.
                            // Perhaps it was removed, but this is not what users usually do.
                            // Leave it for user to remove the notification
                            logger.debug("Event ${event.eventId} disappeared, but can't confirm it has been rescheduled. Not removing");
                        }
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

    fun onAppUpdated(context: Context?, intent: Intent?) {
        if (context != null) {
            val changes = reloadCalendar(context)
            notificationManager.postEventNotifications(context, true, null);
            scheduleNextAlarmForEvents(context);
            scheduleNextAlarmForReminders(context);

            if (changes)
                UINotifierService.notifyUI(context, false);
        }
    }

    fun onBootComplete(context: Context?, intent: Intent?) {
        if (context != null) {
            val changes = reloadCalendar(context);
            notificationManager.postEventNotifications(context, true, null);

            scheduleNextAlarmForEvents(context);
            scheduleNextAlarmForReminders(context);

            if (changes)
                UINotifierService.notifyUI(context, false);
        }
    }

    fun onCalendarChanged(context: Context?, intent: Intent?) {
        if (context != null) {
            val changes = reloadCalendar(context)
            if (changes) {
                notificationManager.postEventNotifications(context, true, null);

                scheduleNextAlarmForEvents(context);
                scheduleNextAlarmForReminders(context);

                UINotifierService.notifyUI(context, false);
            }
        }
    }

    fun onCalendarEventFired(context: Context, event: EventRecord): Boolean {

        var ret = false

        if (event.calendarId == -1L || Settings(context).getCalendarIsHandled(event.calendarId)) {
            EventsStorage(context).use { it.addEvent(event) }

            notificationManager.onEventAdded(context, event)

            scheduleNextAlarmForEvents(context);
            scheduleNextAlarmForReminders(context);

            ret = true

            UINotifierService.notifyUI(context, false);

            logger.info("event added: ${event.eventId} (cal id: ${event.calendarId}");

        } else {
            logger.info("Event ${event.eventId} belongs to calendar ${event.calendarId} which is not handled, skipping");
        }

        return ret
    }

    fun snoozeEvent(context: Context, eventId: Long, snoozeDelay: Long): Pair<Boolean, Long> {
        var ret = Pair(false, 0L)

        val currentTime = System.currentTimeMillis()

        val snoozedEvent =
            EventsStorage(context).use {
                db ->
                val event = db.getEvent(eventId)

                if (event != null) {
                    val snoozedUntil =
                        if (snoozeDelay > 0L) currentTime + snoozeDelay
                        else event.instanceStart - Math.abs(snoozeDelay) // same as "event.instanceStart + snoozeDelay" but a little bit more readable

                    db.updateEvent(event,
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

            scheduleNextAlarmForEvents(context);
            scheduleNextAlarmForReminders(context);

            val silentUntil = QuietHoursManager.getSilentUntil(Settings(context), snoozedEvent.snoozedUntil)
            if (silentUntil != 0L)
                ret = Pair(true, silentUntil)
            else
                ret = Pair(false, snoozedEvent.snoozedUntil)
        }

        return ret
    }

    fun snoozeAllEvents(context: Context, snoozeDelay: Long): Pair<Boolean, Long> {

        var ret = Pair(false, 0L)

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

            scheduleNextAlarmForEvents(context);
            scheduleNextAlarmForReminders(context);

            val silentUntil = QuietHoursManager.getSilentUntil(Settings(context), snoozedUntil)
            if (silentUntil != 0L)
                ret = Pair(true, silentUntil)
            else
                ret = Pair(false, snoozedUntil)
        }


        return ret
    }

    fun fireEventReminder(context: Context) {
        notificationManager.fireEventReminder(context);
    }

    fun onAppStarted(context: Context?) {

        if (context != null) {
            val settings = Settings(context)

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

            scheduleNextAlarmForEvents(context)
            scheduleNextAlarmForReminders(context)

            if (changes)
                UINotifierService.notifyUI(context, true);
        }
    }

    fun onTimeChanged(context: Context?) {
        if (context != null) {
            scheduleNextAlarmForEvents(context)
            scheduleNextAlarmForReminders(context)
        }
    }

    fun dismissEvent(context: Context?, db: EventsStorage, eventId: Long, notificationId: Int, notifyActivity: Boolean) {

        if (context != null) {
            logger.debug("Removing event id $eventId from DB, and dismissing notification")

            db.deleteEvent(eventId)

            notificationManager.onEventDismissed(context, eventId, notificationId);

            scheduleNextAlarmForEvents(context);
            scheduleNextAlarmForReminders(context);

            if (notifyActivity)
                UINotifierService.notifyUI(context, true);
        }
    }

    fun dismissEvent(context: Context?, event: EventRecord) {
        if (context != null) {
            EventsStorage(context).use {
                dismissEvent(context, it, event.eventId, event.notificationId, false)
            }
        }
    }

    fun dismissEvent(context: Context?, eventId: Long, notificationId: Int, notifyActivity: Boolean = true) {
        if (context != null) {
            EventsStorage(context).use {
                dismissEvent(context, it, eventId, notificationId, notifyActivity)
            }
        }
    }
}
