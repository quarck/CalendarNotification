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

package com.github.quarck.calnotify.notification

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TaskStackBuilder
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.support.v4.app.NotificationCompat
import android.text.format.DateUtils
import com.github.quarck.calnotify.*
import com.github.quarck.calnotify.calendar.*
import com.github.quarck.calnotify.eventsstorage.EventsStorage
import com.github.quarck.calnotify.logs.Logger
import com.github.quarck.calnotify.pebble.PebbleUtils
import com.github.quarck.calnotify.prefs.PreferenceUtils
import com.github.quarck.calnotify.quiethours.QuietHoursManager
import com.github.quarck.calnotify.reminders.ReminderState
import com.github.quarck.calnotify.textutils.EventFormatter
import com.github.quarck.calnotify.textutils.EventFormatterInterface
import com.github.quarck.calnotify.ui.MainActivity
import com.github.quarck.calnotify.ui.SnoozeActivityNoRecents
import com.github.quarck.calnotify.utils.*

class EventNotificationManager : EventNotificationManagerInterface {

    override fun onEventAdded(ctx: Context, formatter: EventFormatterInterface, event: EventAlertRecord) {
        EventsStorage(ctx).use {
            // Update lastEventVisibility - we've just seen this event,
            // not using threshold when event is just added
            it.updateEvent(event,
                    lastEventVisibility = System.currentTimeMillis())
        }

        postEventNotifications(ctx, formatter, false, event.eventId)

        if (Settings(ctx).notificationPlayTts) {
            val text = "${event.title}\n${formatter.formatNotificationSecondaryText(event)}"
            TextToSpeechService.playText(ctx, text)
        }
    }

    override fun onEventRestored(context: Context, formatter: EventFormatterInterface, event: EventAlertRecord) {
        EventsStorage(context).use {
            it.updateEvent(event,
                    // do not update last event visibility, so preserve original sorting order in the activity
                    // lastEventVisibility = System.currentTimeMillis(),
                    displayStatus = EventDisplayStatus.Hidden)
        }

        postEventNotifications(context, formatter, true, event.eventId)
    }

    override fun onEventDismissed(context: Context, formatter: EventFormatterInterface, eventId: Long, notificationId: Int) {
        removeNotification(context, eventId, notificationId)
        postEventNotifications(context, formatter, false, null)
    }

    override fun onEventSnoozed(context: Context, formatter: EventFormatterInterface, eventId: Long, notificationId: Int) {
        removeNotification(context, eventId, notificationId)
        postEventNotifications(context, formatter, false, null)
    }

    override fun onAllEventsSnoozed(context: Context) {
        context.notificationManager.cancelAll()
    }

    @Suppress("DEPRECATION")
    fun wakeScreenIfRequired(ctx: Context, settings: Settings) {

        if (settings.notificationWakeScreen) {
            //
            backgroundWakeLocked(
                    ctx.powerManager,
                    PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    Consts.SCREEN_WAKE_LOCK_NAME) {
                // Screen would actually be turned on for a duration of screen timeout set by the user
                // So don't need to keep wakelock for too long
                Thread.sleep(Consts.WAKE_SCREEN_DURATION)
            }
        }

    }

    fun arrangeEvents(
            db: EventsStorage, currentTime: Long, settings: Settings): Pair<List<EventAlertRecord>, List<EventAlertRecord>> {

        // events with snoozedUntil == 0 are currently visible ones
        // events with experied snoozedUntil are the ones to beep about
        // everything else should be hidden and waiting for the next alarm

        val activeEvents =
                db.events
                        .filter {
                            (it.snoozedUntil == 0L)
                                    || (it.snoozedUntil < currentTime + Consts.ALARM_THRESHOLD)
                        }
                        .sortedBy { it.lastEventVisibility }

        val maxNotifications = settings.maxNotifications
        val collapseEverything = settings.collapseEverything

        var recentEvents = activeEvents.takeLast(maxNotifications - 1)
        var collapsedEvents = activeEvents.take(activeEvents.size - recentEvents.size)

        if (collapsedEvents.size == 1) {
            recentEvents += collapsedEvents
            collapsedEvents = listOf<EventAlertRecord>()
        } else if (collapseEverything && !collapsedEvents.isEmpty()) {
            collapsedEvents = recentEvents + collapsedEvents
            recentEvents = listOf<EventAlertRecord>()
        }

        return Pair(recentEvents, collapsedEvents)
    }

    override fun postEventNotifications(context: Context, formatter: EventFormatterInterface, force: Boolean, primaryEventId: Long?) {
        //
        val settings = Settings(context)

        val currentTime = System.currentTimeMillis()

        val isQuietPeriodActive = QuietHoursManager.getSilentUntil(settings) != 0L

        var updatedAnything = false

        EventsStorage(context).use {
            db ->

            val (recentEvents, collapsedEvents) = arrangeEvents(db, currentTime, settings)

            updatedAnything =
                    postDisplayedEventNotifications(
                            context, db, settings,
                            formatter,
                            recentEvents,
                            force, isQuietPeriodActive,
                            primaryEventId)

            if (!recentEvents.isEmpty())
                collapseDisplayedNotifications(context, db, collapsedEvents, settings, force, isQuietPeriodActive)
            else {
                if (postEverythingCollapsed(context, db, collapsedEvents, settings, null, force, isQuietPeriodActive, primaryEventId, false))
                    updatedAnything = true
            }
        }

        // If this is a new notification -- wake screen when required
        if (primaryEventId != null || updatedAnything)
            wakeScreenIfRequired(context, settings)
    }

    override fun fireEventReminder(context: Context) {

        val settings = Settings(context)
        val isQuietPeriodActive = QuietHoursManager.getSilentUntil(settings) != 0L

        EventsStorage(context).use {
            db ->

            val notificationSettings =
                    settings.notificationSettingsSnapshot.copy(
                            ringtoneUri = settings.reminderRingtoneURI,
                            vibrationOn = settings.reminderVibraOn,
                            vibrationPattern = settings.reminderVibrationPattern
                    )

            val currentTime = System.currentTimeMillis()

            val activeEvents =
                    db.events
                            .filter {
                                (it.snoozedUntil == 0L)
                                        || (it.snoozedUntil < currentTime + Consts.ALARM_THRESHOLD)
                            }

            val numActiveEvents = activeEvents.count()
            val lastVisibility = activeEvents.map { it.lastEventVisibility }.max() ?: 0L

            if (numActiveEvents > 0) {
                postReminderNotification(
                        context,
                        numActiveEvents,
                        lastVisibility,
                        notificationSettings,
                        isQuietPeriodActive
                )

                wakeScreenIfRequired(context, settings)

            } else {
                context.notificationManager.cancel(Consts.NOTIFICATION_ID_REMINDER)
            }
        }
    }

    override fun cleanupEventReminder(context: Context) {
        context.notificationManager.cancel(Consts.NOTIFICATION_ID_REMINDER)
    }

    private fun postEverythingCollapsed(
            context: Context, db: EventsStorage,
            events: List<EventAlertRecord>, settings: Settings,
            notificationsSettingsIn: NotificationSettingsSnapshot?,
            force: Boolean, isQuietPeriodActive: Boolean, primaryEventId: Long?, playReminderSound: Boolean): Boolean {

        if (events.isEmpty()) {
            hideCollapsedEventsNotification(context)
            return false
        }
        logger.info("Posting ${events.size} notifications in collapsed view")

        val notificationsSettings = notificationsSettingsIn ?: settings.notificationSettingsSnapshot

        var postedNotification = false

        var shouldPlayAndVibrate = false

        for (event in events) {
            // make sure we remove full notifications
            if ((event.displayStatus != EventDisplayStatus.Hidden) || force) {
                //logger.debug("Hiding notification id ${event.notificationId}, eventId ${event.eventId}")
                removeNotification(context, event.eventId, event.notificationId)
            }

            if (event.snoozedUntil != 0L || event.displayStatus != EventDisplayStatus.DisplayedCollapsed) {
                db.updateEvent(event,
                        snoozedUntil = 0L,
                        displayStatus = EventDisplayStatus.DisplayedCollapsed)
            }

            if (event.snoozedUntil == 0L) {

                if ((event.displayStatus != EventDisplayStatus.DisplayedCollapsed) || force) {
                    // currently not displayed or forced -- post notifications
                    logger.debug("Posting notification id ${event.notificationId}, eventId ${event.eventId}")

                    var shouldBeQuiet = false

                    if (force) {
                        // If forced to re-post all notifications - we only have to actually display notifications
                        // so not playing sound / vibration here
                        logger.debug("event ${event.eventId}: 'forced' notification - staying quiet")
                        shouldBeQuiet = true

                    } else if (event.displayStatus == EventDisplayStatus.DisplayedNormal) {

                        logger.debug("event ${event.eventId}: notification was displayed, not playing sound")
                        shouldBeQuiet = true

                    } else if (isQuietPeriodActive) {

                        // we are in a silent period, normally we should always be quiet, but there
                        // are a few exclusions
                        if (primaryEventId != null && event.eventId == primaryEventId) {
                            // this is primary event -- play based on use preference for muting
                            // primary event reminders
                            logger.debug("event ${event.eventId}: quiet period and this is primary notification - sound according to settings")
                            shouldBeQuiet = settings.quietHoursMutePrimary
                        } else {
                            // not a primary event -- always silent in silent period
                            logger.debug("event ${event.eventId}: quiet period and this is NOT primary notification quiet")
                            shouldBeQuiet = true
                        }
                    }

                    postedNotification = true
                    shouldPlayAndVibrate = shouldPlayAndVibrate || !shouldBeQuiet

                }
            } else {
                // This event is currently snoozed and switching to "Shown" state

                logger.debug("Posting snoozed notification id ${event.notificationId}, eventId ${event.eventId}, isQuietPeriodActive=$isQuietPeriodActive")

                postedNotification = true
                shouldPlayAndVibrate = shouldPlayAndVibrate || !isQuietPeriodActive
            }
        }

        if (playReminderSound)
            shouldPlayAndVibrate = shouldPlayAndVibrate || !isQuietPeriodActive


        // now build actual notification and notify
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, 0)

        val numEvents = events.size

        val title = java.lang.String.format(
                context.getString(R.string.multiple_events_single_notification),
                numEvents)

        val text = context.getString(com.github.quarck.calnotify.R.string.multiple_events_details)

        val bigText =
                events
                        .fold(
                                StringBuilder(), {
                            sb, ev ->

                            val flags =
                                    if (DateUtils.isToday(ev.displayedStartTime))
                                        DateUtils.FORMAT_SHOW_TIME
                                    else
                                        DateUtils.FORMAT_SHOW_DATE

                            sb.append("${DateUtils.formatDateTime(context, ev.displayedStartTime, flags)}: ${ev.title}\n")
                        })
                        .toString()

        val builder =
                Notification.Builder(context)
                        .setContentTitle(title)
                        .setContentText(text)
                        .setSmallIcon(com.github.quarck.calnotify.R.drawable.stat_notify_calendar)
                        .setPriority(
                                if (notificationsSettings.headsUpNotification && shouldPlayAndVibrate)
                                    Notification.PRIORITY_HIGH
                                else
                                    Notification.PRIORITY_DEFAULT
                        )
                        .setContentIntent(pendingIntent)
                        .setAutoCancel(false)
                        .setOngoing(true)
                        .setStyle(Notification.BigTextStyle().bigText(bigText))
                        .setNumber(numEvents)
                        .setShowWhenCompat(false)

        if (shouldPlayAndVibrate) {
            if (notificationsSettings.ringtoneUri != null) {
                builder.setSound(notificationsSettings.ringtoneUri)
            }

            if (notificationsSettings.vibrationOn) {
                builder.setVibrate(notificationsSettings.vibrationPattern)
            } else {
                builder.setVibrate(longArrayOf(0))
            }

            if (notificationsSettings.ledNotificationOn && (!isQuietPeriodActive || !settings.quietHoursMuteLED)) {
                if (notificationsSettings.ledPattern.size == 2)
                    builder.setLights(notificationsSettings.ledColor, notificationsSettings.ledPattern[0], notificationsSettings.ledPattern[1])
                else
                    builder.setLights(notificationsSettings.ledColor, Consts.LED_DURATION_ON, Consts.LED_DURATION_OFF)
            }
        }

        val notification = builder.build()

        context.notificationManager.notify(Consts.NOTIFICATION_ID_COLLAPSED, notification) // would update if already exists

        val reminderState = ReminderState(context)

        // FIXME: should be ignoring reminderState.quietHoursOneTimeReminderEnabled here
        // as we are now reminding using a different approach

        if (shouldPlayAndVibrate) {
            context.persistentState.notificationLastFireTime = System.currentTimeMillis()
            reminderState.numRemindersFired = 0;
        }


        if (isQuietPeriodActive
                && events.isNotEmpty()
                && !shouldPlayAndVibrate
                && !reminderState.quietHoursOneTimeReminderEnabled) {

            logger.debug("Would remind after snooze period")

            reminderState.quietHoursOneTimeReminderEnabled = true
        }

        if (shouldPlayAndVibrate && notificationsSettings.forwardToPebble)
            PebbleUtils.forwardNotificationToPebble(context, title, bigText, true)

        return postedNotification
    }


    private fun collapseDisplayedNotifications(
            context: Context, db: EventsStorage,
            events: List<EventAlertRecord>, settings: Settings,
            force: Boolean,
            isQuietPeriodActive: Boolean) {

        logger.debug("Hiding notifications for ${events.size} notification")

        if (events.isEmpty()) {
            hideCollapsedEventsNotification(context)
            return
        }

        for (event in events) {
            if ((event.displayStatus != EventDisplayStatus.Hidden) || force) {
                //logger.debug("Hiding notification id ${event.notificationId}, eventId ${event.eventId}")
                removeNotification(context, event.eventId, event.notificationId)
            } else {
                //logger.debug("Skipping collapsing notification id ${event.notificationId}, eventId ${event.eventId} - already collapsed")
            }

            if (event.snoozedUntil != 0L || event.displayStatus != EventDisplayStatus.DisplayedCollapsed) {
                db.updateEvent(event,
                        snoozedUntil = 0L,
                        displayStatus = EventDisplayStatus.DisplayedCollapsed)
            }
        }

        postNumNotificationsCollapsed(context, db, settings, events, isQuietPeriodActive)
    }

    // force - if true - would re-post all active notifications. Normally only new notifications are posted to
    // avoid excessive blinking in the notifications area. Forced notifications are posted without sound or vibra
    private fun postDisplayedEventNotifications(
            context: Context,
            db: EventsStorage,
            settings: Settings,
            formatter: EventFormatterInterface,
            events: List<EventAlertRecord>,
            force: Boolean,
            isQuietPeriodActive: Boolean,
            primaryEventId: Long?
    ): Boolean {

        logger.info("Posting ${events.size} notifications")

        val notificationsSettings = settings.notificationSettingsSnapshot

        val notificationsSettingsQuiet =
                notificationsSettings.copy(ringtoneUri = null, vibrationOn = false, forwardToPebble = false)

        var postedNotification = false
        var playedAnySound = false

        for (event in events) {
            if (event.snoozedUntil == 0L) {
                // snooze zero could mean
                // - this is a new event -- we have to display it, it would have displayStatus == hidden
                // - this is an old event returning from "collapsed" state
                // - this is currently potentially displayed event but we are doing "force re-post" to
                //   ensure all events are displayed (like at boot or after app upgrade

                var wasCollapsed = false

                if ((event.displayStatus != EventDisplayStatus.DisplayedNormal) || force) {
                    // currently not displayed or forced -- post notifications
                    logger.info("Posting notification id ${event.notificationId}, eventId ${event.eventId}")

                    var shouldBeQuiet = false

                    if (force) {
                        // If forced to re-post all notifications - we only have to actually display notifications
                        // so not playing sound / vibration here
                        logger.info("event ${event.eventId}: 'forced' notification - staying quiet")
                        shouldBeQuiet = true
                    } else if (event.displayStatus == EventDisplayStatus.DisplayedCollapsed) {
                        // This event was already visible as "collapsed", user just removed some other notification
                        // and so we automatically expanding some of the events, this one was lucky.
                        // No sound / vibration should be played here
                        logger.info("event ${event.eventId}: notification was collapsed, not playing sound")
                        shouldBeQuiet = true
                        wasCollapsed = true

                    } else if (isQuietPeriodActive) {
                        // we are in a silent period, normally we should always be quiet, but there
                        // are a few exclusions
                        if (primaryEventId != null && event.eventId == primaryEventId) {
                            // this is primary event -- play based on use preference for muting
                            // primary event reminders
                            logger.info("event ${event.eventId}: quiet period and this is primary notification - sound according to settings")
                            shouldBeQuiet = settings.quietHoursMutePrimary
                        } else {
                            // not a primary event -- always silent in silent period
                            logger.info("event ${event.eventId}: quiet period and this is NOT primary notification quiet")
                            shouldBeQuiet = true
                        }
                    }

                    logger.debug("event ${event.eventId}: shouldBeQuiet = $shouldBeQuiet")

                    postNotification(
                            context,
                            settings,
                            formatter,
                            event,
                            if (shouldBeQuiet) notificationsSettingsQuiet else notificationsSettings,
                            force,
                            wasCollapsed,
                            settings.snoozePresets,
                            isQuietPeriodActive)

                    // Update db to indicate that this event is currently actively displayed
                    db.updateEvent(event, displayStatus = EventDisplayStatus.DisplayedNormal)

                    postedNotification = true
                    playedAnySound = playedAnySound || !shouldBeQuiet

                } else {
                    logger.info("Not re-posting notification id ${event.notificationId}, eventId ${event.eventId} - already on the screen")
                }
            } else {
                // This event is currently snoozed and switching to "Shown" state

                logger.info("Posting snoozed notification id ${event.notificationId}, eventId ${event.eventId}, isQuietPeriodActive=$isQuietPeriodActive")

                postNotification(
                        context,
                        settings,
                        formatter,
                        event,
                        if (isQuietPeriodActive) notificationsSettingsQuiet else notificationsSettings,
                        force,
                        false,
                        settings.snoozePresets,
                        isQuietPeriodActive)

                val currentTime = System.currentTimeMillis()

                if (event.snoozedUntil + Consts.ALARM_THRESHOLD < currentTime) {

                    val warningMessage = "snooze alarm is very late: expected at ${event.snoozedUntil}, " +
                            "received at $currentTime, late by ${currentTime - event.snoozedUntil} us"

                    logger.error("WARNING: $warningMessage")

                    if (settings.debugAlarmDelays)
                        postNotificationsAlarmDelayDebugMessage(context,
                                "Snooze alarm was late!", "Late by ${(currentTime - event.snoozedUntil)/1000L}s")

                }
                // Update Db to indicate that event is currently displayed and no longer snoozed
                // Since it is displayed now -- it is no longer snoozed, set snoozedUntil to zero
                // also update 'lastVisible' time since event just re-appeared
                db.updateEvent(event,
                        snoozedUntil = 0,
                        displayStatus = EventDisplayStatus.DisplayedNormal,
                        lastEventVisibility = currentTime)

                postedNotification = true
                playedAnySound = playedAnySound || !isQuietPeriodActive
            }
        }

        val reminderState = ReminderState(context)

        // FIXME: should be ignoring reminderState.quietHoursOneTimeReminderEnabled here
        // as we are now reminding using a different approach

        if (playedAnySound) {
            context.persistentState.notificationLastFireTime = System.currentTimeMillis()
            reminderState.numRemindersFired = 0;
        }

        if (isQuietPeriodActive
                && events.isNotEmpty()
                && !playedAnySound
                && !reminderState.quietHoursOneTimeReminderEnabled) {

            logger.info("Was quiet due to quiet hours - would remind after snooze period")

            reminderState.quietHoursOneTimeReminderEnabled = true
        }

        return postedNotification
    }

    @Suppress("DEPRECATION")
    private fun postReminderNotification(
            ctx: Context,
            numActiveEvents: Int,
            lastVisibility: Long,
            notificationSettings: NotificationSettingsSnapshot,
            isQuietPeriodActive: Boolean
    ) {
        val notificationManager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(ctx, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(ctx, 0, intent, 0)

        val persistentState = ctx.persistentState

        val currentTime = System.currentTimeMillis()
        val msAgo: Long = (currentTime - persistentState.notificationLastFireTime)

        val resources = ctx.resources

        val title =
                if (numActiveEvents == 1)
                    resources.getString(R.string.reminder_you_have_missed_event)
                else
                    resources.getString(R.string.reminder_you_have_missed_events)

        val textTemplate = resources.getString(R.string.last_notification_s_ago)

        val text = String.format(textTemplate,
                EventFormatter(ctx).formatTimeDuration(msAgo))

        val builder = NotificationCompat.Builder(ctx)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.drawable.stat_notify_calendar)
                .setPriority(
                        if (notificationSettings.headsUpNotification)
                            Notification.PRIORITY_HIGH
                        else
                            Notification.PRIORITY_DEFAULT
                )
                .setContentIntent(
                        pendingIntent
                )
                .setAutoCancel(true)
                .setOngoing(false)
                .setStyle(
                        NotificationCompat.BigTextStyle().bigText(text)
                )
                .setWhen(
                        lastVisibility
                )
                .setShowWhen(false)
                .setSortKey(
                        "${Long.MAX_VALUE - lastVisibility}" // hack to inverse it
                )
                .setCategory(
                        NotificationCompat.CATEGORY_EVENT
                )
                .setOnlyAlertOnce(false)

        if (notificationSettings.ringtoneUri != null) {
            builder.setSound(notificationSettings.ringtoneUri)
        }

        if (notificationSettings.vibrationOn) {
            builder.setVibrate(notificationSettings.vibrationPattern)
        } else {
            builder.setVibrate(longArrayOf(0))
        }

        if (notificationSettings.ledNotificationOn && (!isQuietPeriodActive || !notificationSettings.quietHoursMuteLED)) {
            if (notificationSettings.ledPattern.size == 2)
                builder.setLights(notificationSettings.ledColor, notificationSettings.ledPattern[0], notificationSettings.ledPattern[1])
            else
                builder.setLights(notificationSettings.ledColor, Consts.LED_DURATION_ON, Consts.LED_DURATION_OFF)
        }

        val notification = builder.build()

        try {
            logger.info("adding reminder notification")

            notificationManager.notify(
                    Consts.NOTIFICATION_ID_REMINDER,
                    notification
            )
        } catch (ex: Exception) {
            logger.error("Exception: ${ex.toString()}, reminder notification stack:")
            ex.printStackTrace()
        }

        if (notificationSettings.forwardToPebble) {
            PebbleUtils.forwardNotificationToPebble(ctx, title, text, notificationSettings.pebbleOldFirmware)
        }
    }

    private fun isNotificationVisible(ctx: Context, event: EventAlertRecord) : Boolean {

        val intent = snoozeIntent(ctx, event.eventId, event.instanceStartTime, event.notificationId)
        val id = event.notificationId * EVENT_CODES_TOTAL + EVENT_CODE_SNOOOZE_OFFSET
        val pendingIntent: PendingIntent? = PendingIntent.getActivity(ctx, id, intent, PendingIntent.FLAG_NO_CREATE)
        return pendingIntent != null
    }

    private fun isCollapsedNotificationVisible(ctx: Context): Boolean {
        val intent = Intent(ctx, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(ctx, 0, intent, PendingIntent.FLAG_NO_CREATE)

        return pendingIntent != null
    }

    private fun postNotification(
            ctx: Context,
            settings: Settings,
            formatter: EventFormatterInterface,
            event: EventAlertRecord,
            notificationSettings: NotificationSettingsSnapshot,
            isForce: Boolean,
            wasCollapsed: Boolean,
            snoozePresets: LongArray,
            isQuietPeriodActive: Boolean
    ) {
        val notificationManager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val calendarIntent = CalendarIntents.getCalendarViewIntent(event)

        val calendarPendingIntent =
                TaskStackBuilder.create(ctx)
                        .addNextIntentWithParentStack(calendarIntent)
                        .getPendingIntent(
                                event.notificationId * EVENT_CODES_TOTAL + EVENT_CODE_OPEN_OFFSET,
                                PendingIntent.FLAG_UPDATE_CURRENT)

        val snoozeActivityIntent =
                pendingActivityIntent(ctx,
                        snoozeIntent(ctx, event.eventId, event.instanceStartTime, event.notificationId),
                        event.notificationId * EVENT_CODES_TOTAL + EVENT_CODE_SNOOOZE_OFFSET
                )

        val dismissPendingIntent =
                pendingServiceIntent(ctx,
                        dismissOrDeleteIntent(ctx, event.eventId, event.instanceStartTime, event.notificationId),
                        event.notificationId * EVENT_CODES_TOTAL + EVENT_CODE_DISMISS_OFFSET
                )

        val notificationText = formatter.formatNotificationSecondaryText(event)

        var title = event.title

        if (settings.enableMonitorDebug
                && event.origin != EventOrigin.ProviderBroadcast
                && event.timeFirstSeen != 0L) {
            title = "#${event.origin},${(event.timeFirstSeen - event.alertTime) / 60000L}m# ${event.title}"
        }

        val builder = NotificationCompat.Builder(ctx)
                .setContentTitle(title)
                .setContentText(notificationText)
                .setSmallIcon(R.drawable.stat_notify_calendar)
                .setPriority(
                        if (notificationSettings.headsUpNotification && !isForce && !wasCollapsed)
                            Notification.PRIORITY_HIGH
                        else
                            Notification.PRIORITY_DEFAULT
                )
                .setContentIntent(
                        if (notificationSettings.notificationOpensSnooze)
                            snoozeActivityIntent
                        else
                            calendarPendingIntent
                )
                .setAutoCancel(
                        false // !notificationSettings.showDismissButton // let user swipe to dismiss even if dismiss button is disabled - otherwise we would not receive any notification on dismiss when user clicks event
                )
                .setOngoing(
                        notificationSettings.showDismissButton && !notificationSettings.allowSwipeToSnooze
                )
                .setStyle(
                        NotificationCompat.BigTextStyle().bigText(notificationText)
                )
                .setWhen(
                        event.lastEventVisibility
                )
                .setShowWhen(false)
                .setSortKey(
                        "${Long.MAX_VALUE - event.lastEventVisibility}" // hack to inverse it
                )
                .setCategory(
                        NotificationCompat.CATEGORY_EVENT
                )

        val defaultSnooze0PendingIntent =
                pendingServiceIntent(ctx,
                        defaultSnoozeIntent(ctx, event.eventId, event.instanceStartTime, event.notificationId, snoozePresets[0]),
                        event.notificationId * EVENT_CODES_TOTAL + EVENT_CODE_DEFAULT_SNOOOZE0_OFFSET
                )

        val snoozeAction =
                NotificationCompat.Action.Builder(
                        R.drawable.ic_update_white_24dp,
                        ctx.getString(com.github.quarck.calnotify.R.string.snooze),
                        snoozeActivityIntent
                ).build()

        val dismissAction =
                NotificationCompat.Action.Builder(
                        R.drawable.ic_clear_white_24dp,
                        ctx.getString(com.github.quarck.calnotify.R.string.dismiss),
                        dismissPendingIntent
                ).build()

        val defaultSnooze0Action =
                NotificationCompat.Action.Builder(
                        R.drawable.ic_update_white_24dp,
                        ctx.getString(com.github.quarck.calnotify.R.string.snooze) + " " +
                                PreferenceUtils.formatSnoozePreset(snoozePresets[0]),
                        defaultSnooze0PendingIntent
                ).build()

        if (!notificationSettings.notificationOpensSnooze) {
            logger.debug("adding pending intent for snooze, event id ${event.eventId}, notificationId ${event.notificationId}")
            builder.addAction(snoozeAction)
        }

        if (notificationSettings.showDismissButton) {
            builder.addAction(dismissAction)
            if (notificationSettings.allowSwipeToSnooze)
                builder.setDeleteIntent(defaultSnooze0PendingIntent)
        } else {
            builder.setDeleteIntent(dismissPendingIntent)
        }

        val extender =
                NotificationCompat.WearableExtender()
                        .addAction(defaultSnooze0Action)

        for ((idx, snoozePreset) in snoozePresets.withIndex()) {
            if (idx == 0)
                continue;

            if (idx >= EVENT_CODE_DEFAULT_SNOOOZE_MAX_ITEMS)
                break;

            if (snoozePreset <= 0L) {
                val targetTime = event.displayedStartTime - Math.abs(snoozePreset)
                if (targetTime - System.currentTimeMillis() < 5 * 60 * 1000L) // at least minutes left until target
                    continue;
            }

            val snoozeIntent =
                    pendingServiceIntent(ctx,
                            defaultSnoozeIntent(ctx, event.eventId, event.instanceStartTime, event.notificationId, snoozePreset),
                            event.notificationId * EVENT_CODES_TOTAL + EVENT_CODE_DEFAULT_SNOOOZE0_OFFSET + idx
                    )

            val action =
                    NotificationCompat.Action.Builder(
                            R.drawable.ic_update_white_24dp,
                            ctx.getString(com.github.quarck.calnotify.R.string.snooze) + " " +
                                    PreferenceUtils.formatSnoozePreset(snoozePreset),
                            snoozeIntent
                    ).build()

            extender.addAction(action)
        }

        if (notificationSettings.showDismissButton && notificationSettings.allowSwipeToSnooze) {
            // in this case regular "dismiss" would actually snooze
            val dismissEventAction =
                    NotificationCompat.Action.Builder(
                            R.drawable.ic_clear_white_24dp,
                            ctx.getString(com.github.quarck.calnotify.R.string.full_dismiss),
                            dismissPendingIntent
                    ).build()

            extender.addAction(dismissEventAction)
        }

        builder.extend(extender)

        if (notificationSettings.ringtoneUri != null) {
            builder.setSound(notificationSettings.ringtoneUri)
        }

        if (notificationSettings.vibrationOn) {
            builder.setVibrate(notificationSettings.vibrationPattern)
        } else {
            builder.setVibrate(longArrayOf(0))
        }

        if (notificationSettings.ledNotificationOn && (!isQuietPeriodActive || !notificationSettings.quietHoursMuteLED)) {
            if (notificationSettings.ledPattern.size == 2)
                builder.setLights(notificationSettings.ledColor, notificationSettings.ledPattern[0], notificationSettings.ledPattern[1])
            else
                builder.setLights(notificationSettings.ledColor, Consts.LED_DURATION_ON, Consts.LED_DURATION_OFF)
        }

        if (notificationSettings.showColorInNotification) {
            builder.setColor(event.color.adjustCalendarColor())
        }

        val notification = builder.build()

        try {
            logger.info("adding: notificationId=${event.notificationId}")

            notificationManager.notify(
                    event.notificationId,
                    notification
            )
        } catch (ex: Exception) {
            logger.error("Exception: ${ex.toString()}, notificationId=${event.notificationId}, stack:")
            ex.printStackTrace()
        }

        if (notificationSettings.forwardToPebble && !notificationSettings.pebbleForwardRemindersOnly) {
            PebbleUtils.forwardNotificationToPebble(ctx, event.title, notificationText, notificationSettings.pebbleOldFirmware)
        }
    }


    private fun snoozeIntent(ctx: Context, eventId: Long, instanceStartTime: Long, notificationId: Int): Intent {

        val intent = Intent(ctx, SnoozeActivityNoRecents::class.java)
        intent.putExtra(Consts.INTENT_NOTIFICATION_ID_KEY, notificationId)
        intent.putExtra(Consts.INTENT_EVENT_ID_KEY, eventId)
        intent.putExtra(Consts.INTENT_INSTANCE_START_TIME_KEY, instanceStartTime)

        intent.action = Intent.ACTION_MAIN
        intent.addCategory(Intent.CATEGORY_LAUNCHER)

        return intent
    }

    private fun dismissOrDeleteIntent(ctx: Context, eventId: Long, instanceStartTime: Long, notificationId: Int): Intent {

        val intent = Intent(ctx, NotificationActionDismissService::class.java)
        intent.putExtra(Consts.INTENT_NOTIFICATION_ID_KEY, notificationId)
        intent.putExtra(Consts.INTENT_EVENT_ID_KEY, eventId)
        intent.putExtra(Consts.INTENT_INSTANCE_START_TIME_KEY, instanceStartTime)
        return intent
    }

    private fun defaultSnoozeIntent(ctx: Context, eventId: Long, instanceStartTime: Long, notificationId: Int, snoozePreset: Long): Intent {

        val intent = Intent(ctx, NotificationActionSnoozeService::class.java)
        intent.putExtra(Consts.INTENT_NOTIFICATION_ID_KEY, notificationId)
        intent.putExtra(Consts.INTENT_EVENT_ID_KEY, eventId)
        intent.putExtra(Consts.INTENT_INSTANCE_START_TIME_KEY, instanceStartTime)
        intent.putExtra(Consts.INTENT_SNOOZE_PRESET, snoozePreset)
        return intent
    }

    private fun pendingServiceIntent(ctx: Context, intent: Intent, id: Int): PendingIntent
            = PendingIntent.getService(ctx, id, intent, PendingIntent.FLAG_CANCEL_CURRENT)

    private fun pendingActivityIntent(ctx: Context, intent: Intent, id: Int): PendingIntent {

/*      intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

        val pendingIntent =
            TaskStackBuilder.create(context)
                .addNextIntentWithParentStack(intent)
                .getPendingIntent(id, PendingIntent.FLAG_UPDATE_CURRENT);

        return pendingIntent */

        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        return PendingIntent.getActivity(ctx, id, intent, PendingIntent.FLAG_UPDATE_CURRENT)

    }

    @Suppress("UNUSED_PARAMETER")
    private fun removeNotification(ctx: Context, eventId: Long, notificationId: Int) {
        val notificationManager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(notificationId)
    }

    @Suppress("UNUSED_PARAMETER")
    private fun postNumNotificationsCollapsed(
            context: Context,
            db: EventsStorage,
            settings: Settings,
            events: List<EventAlertRecord>,
            isQuietPeriodActive: Boolean
    ) {
        logger.debug("Posting collapsed view notification for ${events.size} events")

        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, 0)

        val numEvents = events.size

        val title = java.lang.String.format(context.getString(R.string.multiple_events), numEvents)

        val text = context.getString(com.github.quarck.calnotify.R.string.multiple_events_details)

        val bigText =
                events
                        .fold(
                                StringBuilder(), {
                            sb, ev ->

                            val flags =
                                    if (DateUtils.isToday(ev.displayedStartTime))
                                        DateUtils.FORMAT_SHOW_TIME
                                    else
                                        DateUtils.FORMAT_SHOW_DATE

                            sb.append("${DateUtils.formatDateTime(context, ev.displayedStartTime, flags)}: ${ev.title}\n")
                        })
                        .toString()

        val builder =
                Notification.Builder(context)
                        .setContentTitle(title)
                        .setContentText(text)
                        .setSmallIcon(com.github.quarck.calnotify.R.drawable.stat_notify_calendar)
                        .setPriority(Notification.PRIORITY_LOW) // always LOW regardless of other settings for regular notifications, so it is always last
                        .setContentIntent(pendingIntent)
                        .setAutoCancel(false)
                        .setOngoing(true)
                        .setStyle(Notification.BigTextStyle().bigText(bigText))
                        .setNumber(numEvents)
                        .setShowWhenCompat(false)

        if (settings.ledNotificationOn && (!isQuietPeriodActive || !settings.quietHoursMuteLED)) {
            if (settings.ledPattern.size == 2)
                builder.setLights(settings.ledColor, settings.ledPattern[0], settings.ledPattern[1])
            else
                builder.setLights(settings.ledColor, Consts.LED_DURATION_ON, Consts.LED_DURATION_OFF)
        }

        val notification = builder.build()

        context.notificationManager.notify(Consts.NOTIFICATION_ID_COLLAPSED, notification) // would update if already exists
    }

    private fun hideCollapsedEventsNotification(context: Context) {
        logger.debug("Hiding collapsed view notification")
        context.notificationManager.cancel(Consts.NOTIFICATION_ID_COLLAPSED)
    }

    fun postDebugNotification(context: Context, notificationId: Int, title: String, text: String) {

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val appPendingIntent = pendingActivityIntent(context,
                Intent(context, MainActivity::class.java), notificationId)

        val builder = NotificationCompat.Builder(context)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.drawable.stat_notify_calendar)
                .setPriority(Notification.PRIORITY_HIGH)
                .setContentIntent(appPendingIntent)
                .setAutoCancel(false)
                .setShowWhen(false)
                .setCategory(NotificationCompat.CATEGORY_ERROR)
                .setLights(Consts.DEFAULT_LED_COLOR, Consts.LED_DURATION_ON, Consts.LED_DURATION_OFF)

        val notification = builder.build()

        try {
            notificationManager.notify(notificationId, notification)
        } catch (ex: Exception) {
            logger.error("Exception: ${ex.toString()}, stack:")
            ex.printStackTrace()
        }
    }

    override fun postNotificationsAutoDismissedDebugMessage(context: Context) {

        postDebugNotification(
                context,
                Consts.NOTIFICATION_ID_DEBUG0_AUTO_DISMISS,
                "DEBUG: Events dismissed",
                "DEBUG: Some events were auto-dismissed due to calendar move"
            )

        PebbleUtils.forwardNotificationToPebble(context, "DEBUG:", "Events auto-dismissed", false)
    }

    override fun postNotificationsAlarmDelayDebugMessage(context: Context, title: String, text: String) {

        postDebugNotification(
                context,
                Consts.NOTIFICATION_ID_DEBUG1_ALARM_DELAYS,
                title,
                text
        )

        PebbleUtils.forwardNotificationToPebble(context, title, text, false)
    }

    override fun postNotificationsSnoozeAlarmDelayDebugMessage(context: Context, title: String, text: String) {

        postDebugNotification(
                context,
                Consts.NOTIFICATION_ID_DEBUG2_SNOOZE_ALARM_DELAYS,
                title,
                text
        )

        PebbleUtils.forwardNotificationToPebble(context, title, text, false)
    }


    companion object {
        private val logger = Logger("EventNotificationManager")

        const val EVENT_CODE_SNOOOZE_OFFSET = 0
        const val EVENT_CODE_DISMISS_OFFSET = 1
        const val EVENT_CODE_DELETE_OFFSET = 2
        const val EVENT_CODE_OPEN_OFFSET = 3
        const val EVENT_CODE_DEFAULT_SNOOOZE0_OFFSET = 4
        const val EVENT_CODE_DEFAULT_SNOOOZE_MAX_ITEMS = 10
        const val EVENT_CODES_TOTAL = 16
    }
}
