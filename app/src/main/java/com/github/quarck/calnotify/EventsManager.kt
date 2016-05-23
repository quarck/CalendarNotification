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
import com.github.quarck.calnotify.eventsstorage.EventRecord
import com.github.quarck.calnotify.eventsstorage.EventsStorage
import com.github.quarck.calnotify.logs.Logger
import com.github.quarck.calnotify.notification.EventNotificationManager
import com.github.quarck.calnotify.notification.IEventNotificationManager
import com.github.quarck.calnotify.notification.ReminderAlarm
import com.github.quarck.calnotify.quiethours.QuietHoursManager
import com.github.quarck.calnotify.ui.UINotifierService
import com.github.quarck.calnotify.utils.setExactCompat

object EventsManager {
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

        var intent = Intent(context, AlarmBroadcastReceiver::class.java);
        var pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)

        var alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager;

        if (nextAlarm != null) {

            var currentTime = System.currentTimeMillis()

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

        var settings = Settings(context);

        if (!settings.remindersEnabled || !settings.quietHoursOneTimeReminderEnabled)
            return;

        val hasActiveNotifications =
                EventsStorage(context).use {
                    it.events
                            .filter { it.snoozedUntil == 0L }
                            .any()
                }

        if (hasActiveNotifications) {
            ReminderAlarm.cancelAlarm(context); // cancel existing to re-schedule

            val remindInterval = settings.remindersIntervalMillis
            var nextFire = System.currentTimeMillis() + remindInterval

            val quietUntil = QuietHoursManager.getSilentUntil(settings, nextFire)

            if (quietUntil != 0L) {
                logger.info("Reminder alarm moved from $nextFire to $quietUntil+15s due to silent period");

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
                        dismissEvent(context, db, event.eventId, event.notificationId, true);
                        var movedSec = (newEvent.startTime - event.startTime) / 1000L
                        logger.debug("Event ${event.eventId} disappeared, event was moved further by $movedSec seconds");
                    } else {
                        // Here we can't confrim that event was moved into the future.
                        // Perhaps it was removed, but this is not what users usually do.
                        // Leave it for user to remove the notification
                        logger.debug("Event ${event.eventId} disappeared, but can't confirm it has been rescheduled. Not removing");
                    }

                } else {
                    logger.debug("Event ${event.eventId} is still here");

                    if (event.updateFrom(newEvent)) {
                        logger.debug("Event was updated, updating our copy");

                        db.updateEvent(event)
                        repostNotifications = true
                    }
                }
            }
        }

        return repostNotifications
    }

    fun onAppUpdated(context: Context?, intent: Intent?) {
        if (context != null) {
            var changes = reloadCalendar(context)
            notificationManager.postEventNotifications(context, true, null);
            scheduleNextAlarmForEvents(context);
            scheduleNextAlarmForReminders(context);

            if (changes)
                UINotifierService.notifyUI(context, false);
        }
    }

    fun onBootComplete(context: Context?, intent: Intent?) {
        if (context != null) {
            var changes = reloadCalendar(context);
            notificationManager.postEventNotifications(context, true, null);
            scheduleNextAlarmForEvents(context);
            scheduleNextAlarmForReminders(context);

            if (changes)
                UINotifierService.notifyUI(context, false);
        }
    }

    fun onCalendarChanged(context: Context?, intent: Intent?) {
        if (context != null) {
            var changes = reloadCalendar(context)
            if (changes) {
                notificationManager.postEventNotifications(context, true, null);
                UINotifierService.notifyUI(context, false);
            }
        }
    }

    fun onCalendarEventFired(context: Context, event: EventRecord) {

        EventsStorage(context).use { it.addEvent(event) }
        notificationManager.onEventAdded(context, event)

        scheduleNextAlarmForEvents(context);
        scheduleNextAlarmForReminders(context);

        UINotifierService.notifyUI(context, false);

        logger.info("event added: ${event.eventId}");
    }

    fun snoozeEvent(context: Context,
                    event: EventRecord,
                    snoozeDelay: Long,
                    eventsStorage: EventsStorage?,
                    onHitQuietHours: (Long) -> Unit
                    ) {

        var currentTime = System.currentTimeMillis()

        event.snoozedUntil = currentTime + snoozeDelay;
        event.lastEventVisibility = currentTime;

        if (eventsStorage != null)
            eventsStorage.updateEvent(event)
        else
            EventsStorage(context).use { it.updateEvent(event) }

        notificationManager.onEventSnoozed(context, event.eventId, event.notificationId);

        scheduleNextAlarmForEvents(context);
        scheduleNextAlarmForReminders(context);

        var seconds = (event.snoozedUntil - currentTime) / 1000
        logger.debug("alarm set -  called for ${event.eventId}, for $seconds seconds from now");

        val silentUntil = QuietHoursManager.getSilentUntil(Settings(context), event.snoozedUntil)
        if (silentUntil != 0L)
            onHitQuietHours(silentUntil)
    }

    fun fireEventReminder(context: Context) {
        notificationManager.fireEventReminder(context);
    }

    fun onAppStarted(context: Context?) {
        if (context != null) {
            notificationManager.postEventNotifications(context, true, null)
        }
    }

    fun onAppResumed(context: Context?) {
        if (context != null) {
            scheduleNextAlarmForEvents(context)
            scheduleNextAlarmForReminders(context)
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
            logger.debug("Removing event id ${eventId} from DB, and dismissing notification id ${notificationId}")

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
