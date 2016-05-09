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
import com.github.quarck.calnotify.broadcastreceivers.BroadcastReceiverAlarm
import com.github.quarck.calnotify.calendar.CalendarUtils
import com.github.quarck.calnotify.eventsstorage.EventRecord
import com.github.quarck.calnotify.eventsstorage.EventsStorage
import com.github.quarck.calnotify.logs.DebugTransactionLog
import com.github.quarck.calnotify.logs.Logger
import com.github.quarck.calnotify.notification.EventNotificationManager
import com.github.quarck.calnotify.notification.IEventNotificationManager
import com.github.quarck.calnotify.notification.ReminderAlarm
import com.github.quarck.calnotify.ui.ServiceUINotifier
import com.github.quarck.calnotify.utils.setExactCompat

object EventsManager {
    private val notificationManager: IEventNotificationManager = EventNotificationManager()

    private val logger = Logger("EventsManager");

    private fun scheduleNextAlarmForEvents(context: Context) {
        logger.debug("scheduleEventAlarm called");

        var nextAlarm =
                EventsStorage(context)
                        .events
                        .filter { it.snoozedUntil != 0L }
                        .map { it.snoozedUntil }
                        .min();

        var intent = Intent(context, BroadcastReceiverAlarm::class.java);
        var pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)

        var alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager;

        if (nextAlarm != null) {
            var seconds = (nextAlarm - System.currentTimeMillis()) / 1000L

            logger.info("Next alarm at ${nextAlarm}, in ${seconds} seconds");

            alarmManager.setExactCompat(AlarmManager.RTC_WAKEUP, nextAlarm, pendingIntent);
        } else {
            logger.info("Cancelling alarms");

            alarmManager.cancel(pendingIntent)
        }
    }

    private fun scheduleAlarmForReminders(context: Context) {

        var settings = Settings(context);

        if (!settings.remindersEnabled)
            return;

        val hasActiveNotifications =
                EventsStorage(context)
                        .events
                        .filter { it.snoozedUntil == 0L }
                        .any();

        if (hasActiveNotifications) {
            ReminderAlarm.cancelAlarm(context); // cancel existing to re-schedule
            ReminderAlarm.scheduleAlarmMillis(context, Settings(context).remindersIntervalMillis);
        }
    }

    fun updateReminderAlarm(context: Context) {
        scheduleAlarmForReminders(context)
    }

    fun hasActiveEvents(context: Context) =
        EventsStorage(context).events.filter { it.snoozedUntil == 0L }.any()

    fun onAlarm(context: Context?, intent: Intent?) {
        if (context != null) {
            notificationManager.postEventNotifications(context, false);
            scheduleNextAlarmForEvents(context);
            scheduleAlarmForReminders(context);
        } else {
            logger.error("onAlarm: context is null");
        }
    }

    private fun reloadCalendar(context: Context): Boolean {
        var repostNotifications = false

        var db = EventsStorage(context)

        var events = db.events

        for (event in events) {

            var newEvent = CalendarUtils.getEvent(context, event.eventId, event.alertTime)

            if (newEvent == null ) {

                newEvent = CalendarUtils.getEvent(context, event.eventId)

                if (newEvent != null
                        && newEvent.startTime > event.startTime
                        && newEvent.alertTime >= System.currentTimeMillis()) {
                    // Here we have a confirmation that event was re-scheduled by user
                    // to some time in the future and that's why original event instance has disappeared
                    // - we are good to go to dismiss event reminder automatically
                    dismissEvent(context, event.eventId, event.notificationId, true);
                    var movedSec = (newEvent.startTime - event.startTime) / 1000L
                    logger.debug("Event ${event.eventId} disappeared, event was moved further by $movedSec seconds");
                    DebugTransactionLog(context).log("EventsManager", "remove", "Event ${event.eventId} disappeared from calendar, moved further by $movedSec seconds")
                } else {
                    // Here we can't confrim that event was moved into the future.
                    // Perhaps it was removed, but this is not what users usually do.
                    // Leave it for user to remove the notification
                    logger.debug("Event ${event.eventId} disappeared, but can't confirm it has been rescheduled. Not removing");
                    DebugTransactionLog(context).log("EventsManager", "remove", "Event ${event.eventId} disappeared but reschedule confirmation has failed, not removing")
                }

            } else {
                logger.debug("Event ${event.eventId} is still here");

                if (event.updateFrom(newEvent)) {
                    logger.debug("Event was updated, updating our copy");

                    EventsStorage(context).updateEvent(event);
                    repostNotifications = true

                    DebugTransactionLog(context).log("EventsManager", "update", "Event ${event.eventId} updated in db, title: ${event.title}")
                }
            }
        }

        return repostNotifications
    }

    fun onAppUpdated(context: Context?, intent: Intent?) {
        if (context != null) {
            var changes = reloadCalendar(context)
            notificationManager.postEventNotifications(context, true);
            scheduleNextAlarmForEvents(context);
            scheduleAlarmForReminders(context);

            if (changes)
                ServiceUINotifier.notifyUI(context, false);
        }
    }

    fun onBootComplete(context: Context?, intent: Intent?) {
        if (context != null) {
            var changes = reloadCalendar(context);
            notificationManager.postEventNotifications(context, true);
            scheduleNextAlarmForEvents(context);
            scheduleAlarmForReminders(context);

            if (changes)
                ServiceUINotifier.notifyUI(context, false);
        }
    }

    fun onCalendarChanged(context: Context?, intent: Intent?) {
        if (context != null) {
            var changes = reloadCalendar(context)
            if (changes) {
                notificationManager.postEventNotifications(context, true);
                ServiceUINotifier.notifyUI(context, false);
            }
        }
    }

    fun onCalendarEventFired(context: Context, event: EventRecord) {
        EventsStorage(context).addEvent(event);
        notificationManager.onEventAdded(context, event)

        scheduleAlarmForReminders(context);

        DebugTransactionLog(context).log("EventsManager", "add", "event added: ${event.eventId} ${event.title}")

        ServiceUINotifier.notifyUI(context, false);
    }

    fun snoozeEvent(context: Context, event: EventRecord, snoozeDelay: Long, eventsStorage: EventsStorage?) {
        var storage = eventsStorage ?: EventsStorage(context)

        var currentTime = System.currentTimeMillis()

        event.snoozedUntil = currentTime + snoozeDelay;
        event.lastEventUpdate = currentTime;
        storage.updateEvent(event);

        scheduleNextAlarmForEvents(context);

        notificationManager.onEventSnoozed(context, event.eventId, event.notificationId);

        scheduleAlarmForReminders(context);

        var seconds = (event.snoozedUntil - currentTime) / 1000
        logger.debug("alarm set -  called for ${event.eventId}, for $seconds seconds from now");

        DebugTransactionLog(context).log("EventsManager", "snooze", "event snoozed for $seconds, id: ${event.eventId}, title: ${event.title}")
    }

    fun fireEventReminder(context: Context) {
        notificationManager.fireEventReminder(context);
    }

    fun onAppStarted(context: Context?) {
        if (context != null) {
            notificationManager.postEventNotifications(context, true)
            scheduleNextAlarmForEvents(context)
        }
    }

    fun dismissEvent(context: Context?, event: EventRecord) {
        if (context != null) {
            logger.debug("Removing[1] event id ${event.eventId} from DB, and dismissing notification id ${event.notificationId}")

            var db = EventsStorage(context);
            db.deleteEvent(event.eventId);

            notificationManager.onEventDismissed(context, event.eventId, event.notificationId);

            scheduleNextAlarmForEvents(context);

            scheduleAlarmForReminders(context);
        }
    }

    fun dismissEvent(context: Context?, eventId: Long, notificationId: Int, notifyActivity: Boolean = true) {
        if (context != null) {
            logger.debug("Removing event id ${eventId} from DB, and dismissing notification id ${notificationId}")

            EventsStorage(context).deleteEvent(eventId);

            notificationManager.onEventDismissed(context, eventId, notificationId);

            scheduleNextAlarmForEvents(context);
            scheduleAlarmForReminders(context);

            if (notifyActivity)
                ServiceUINotifier.notifyUI(context, true);
        }
    }
}
