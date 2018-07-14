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

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.support.v4.app.NotificationCompat
import android.text.format.DateUtils
import com.github.quarck.calnotify.pebble.PebbleUtils
import com.github.quarck.calnotify.*
import com.github.quarck.calnotify.calendar.*
import com.github.quarck.calnotify.eventsstorage.EventsStorage
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.prefs.PreferenceUtils
import com.github.quarck.calnotify.quiethours.QuietHoursManager
import com.github.quarck.calnotify.reminders.ReminderState
import com.github.quarck.calnotify.textutils.EventFormatter
import com.github.quarck.calnotify.textutils.EventFormatterInterface
import com.github.quarck.calnotify.ui.MainActivity
import com.github.quarck.calnotify.ui.ViewEventActivityNoRecents
import com.github.quarck.calnotify.utils.*

@Suppress("VARIABLE_WITH_REDUNDANT_INITIALIZER")
class EventNotificationManager : EventNotificationManagerInterface {

    override fun onEventAdded(ctx: Context, formatter: EventFormatterInterface, event: EventAlertRecord) {
        postEventNotifications(ctx, formatter, isRepost = false, primaryEventId = event.eventId)
    }

    override fun onEventRestored(context: Context, formatter: EventFormatterInterface, event: EventAlertRecord) {

        if (event.displayStatus != EventDisplayStatus.Hidden) {
            EventsStorage(context).use {
                it.updateEvent(event, displayStatus = EventDisplayStatus.Hidden)
            }
        }

        postEventNotifications(context, formatter, isRepost = true)
    }

    override fun onEventDismissing(context: Context, eventId: Long, notificationId: Int) {
        removeNotification(context, notificationId)
    }

    override fun onEventsDismissing(context: Context, events: Collection<EventAlertRecord>) {
        removeNotifications(context, events)
    }

    override fun onEventDismissed(context: Context, formatter: EventFormatterInterface, eventId: Long, notificationId: Int) {
        removeNotification(context, notificationId)
        postEventNotifications(context, formatter)
    }

    override fun onEventsDismissed(context: Context, formatter: EventFormatterInterface, events: Collection<EventAlertRecord>, postNotifications: Boolean, hasActiveEvents: Boolean) {

        for (event in events) {
            removeNotification(context, event.notificationId)
        }

        if (!hasActiveEvents) {
            removeNotification(context, Consts.NOTIFICATION_ID_BUNDLED_GROUP)
        }

        if (postNotifications) {
            postEventNotifications(context, formatter)
        }
    }

    override fun onEventSnoozed(context: Context, formatter: EventFormatterInterface, eventId: Long, notificationId: Int) {
        removeNotification(context, notificationId)
        postEventNotifications(context, formatter)
    }

    override fun onEventMuteToggled(context: Context, formatter: EventFormatterInterface, event: EventAlertRecord) {

        if (event.displayStatus != EventDisplayStatus.DisplayedNormal)
            return

        val settings = Settings(context)

        val notificationSettings = settings.notificationSettingsSnapshot

        postNotification(
                ctx = context,
                formatter = formatter,
                event = event,
                notificationSettings = notificationSettings,
                alertOnlyOnce = true,
                snoozePresetsNotFiltered = settings.snoozePresets,
                soundState = NotificationChannelManager.SoundState.Silent,
                isReminder = false
        )

        context.notificationManager.cancel(Consts.NOTIFICATION_ID_REMINDER)
    }

    override fun onAllEventsSnoozed(context: Context) {
        context.notificationManager.cancelAll()
    }

    /**
     * @param events - events to sort
     * @returns pair of boolean and list, boolean means "all collapsed"
     */
    private fun sortEvents(
            events: List<EventAlertRecord>
    ): Pair<Boolean, List<EventAlertRecord>> {

        var allCollapsed = false

        if (events.size > Consts.MAX_UNCOLLAPSED_NOTIFICATIONS)
            allCollapsed = true
        else if (events.any {it.displayStatus == EventDisplayStatus.DisplayedCollapsed})
            allCollapsed = true

        return Pair(allCollapsed, events)
    }

    // NOTES:
    // requests with snoozedUntil == 0 are currently visible ones
    // requests with experied snoozedUntil are the ones to beep about
    // everything else should be hidden and waiting for the next alarm
    private fun getCurrentEvents(db: EventsStorage, currentTime: Long)
            = db.events.filter {
                ((it.snoozedUntil == 0L) || (it.snoozedUntil < currentTime + Consts.ALARM_THRESHOLD)) &&
                        it.isNotSpecial
            }

    /**
     * @returns pair of boolean and list, boolean means "all collapsed"
     */
    private fun processEvents(
            context: Context,
            db: EventsStorage
    ): Pair<Boolean, List<EventAlertRecord>> {

        //val events = getEventsAndUnSnooze(context, db)
        return sortEvents(getEventsAndUnSnooze(context, db))
    }

    override fun postEventNotifications(
            context: Context,
            formatter: EventFormatterInterface?,
            isRepost: Boolean,
            primaryEventId: Long?,
            isReminder: Boolean
    ) {

        val formatterLocal = formatter ?: EventFormatter(context)

        val settings = Settings(context)

        //val currentTime = System.currentTimeMillis()

        val isQuietPeriodActive = QuietHoursManager.getSilentUntil(settings) != 0L

        //var updatedAnything = false

        EventsStorage(context).use {
            db ->

            val (allCollapsed, events) = processEvents(context, db)

            val notificationRecords = generateNotificationRecords(
                    context = context,
                    events = events,
                    primaryEventId = primaryEventId,
                    settings = settings,
                    isQuietPeriodActive = isQuietPeriodActive,
                    isReminder = isReminder,
                    isRepost = isRepost
            )

            if (!allCollapsed) {
                hideCollapsedEventsNotification(context)

                if (events.isNotEmpty()) {
                    postDisplayedEventNotifications(
                            context = context,
                            db = db,
                            settings = settings,
                            formatter = formatterLocal,
                            notificationRecords = notificationRecords,
                            isRepost = isRepost,
                            isQuietPeriodActive = isQuietPeriodActive,
                            primaryEventId = primaryEventId,
                            isReminder = isReminder
                    )
                } else {
                    removeNotification(context, Consts.NOTIFICATION_ID_BUNDLED_GROUP)
                }
            }
            else {
                removeNotification(context, Consts.NOTIFICATION_ID_BUNDLED_GROUP)

                postEverythingCollapsed(
                        context = context,
                        db = db,
                        notificationRecords = notificationRecords,
                        settings = settings,
                        isQuietPeriodActive = isQuietPeriodActive,
                        isReminder = isReminder
                )
            }
        }
    }

    override fun fireEventReminder(
            context: Context, itIsAfterQuietHoursReminder: Boolean,
            hasActiveAlarms: Boolean) {

        val settings = Settings(context)
        //val isQuietPeriodActive = !hasActiveAlarms && (QuietHoursManager.getSilentUntil(settings) != 0L)

        EventsStorage(context).use {
            db ->

            //val notificationSettings =
             //       settings.notificationSettingsSnapshot

            val activeEvents = db.events.filter { it.isNotSnoozed && it.isNotSpecial && !it.isTask  && !it.isMuted}

            //val lastStatusChange = activeEvents.map { it.lastStatusChangeTime }.max() ?: 0L

            if (activeEvents.count() > 0) {
                postEventNotifications(context, isReminder = true)
            }
            else {
                context.notificationManager.cancel(Consts.NOTIFICATION_ID_REMINDER)
            }
        }
    }

    override fun cleanupEventReminder(context: Context) {
        context.notificationManager.cancel(Consts.NOTIFICATION_ID_REMINDER)
    }

    data class EventAlertNotificationRecord(
            val event: EventAlertRecord,
            val soundState: NotificationChannelManager.SoundState,
            val isPrimary: Boolean,
            val newNotification: Boolean, // un-snoozed or primary
            val isReminder: Boolean,
            val alertOnlyOnce: Boolean
    )

    private fun getEventsAndUnSnooze(
            context: Context,
            db: EventsStorage
    ): List<EventAlertRecord> {

        var currentTime = System.currentTimeMillis()

        val events = getCurrentEvents(db, currentTime)

        val eventsToUpdate = mutableListOf<EventAlertRecord>()

        for (event in events) {
            if (event.snoozedUntil == 0L)
                continue

            DevLog.info(context, LOG_TAG, "Snoozed notification id ${event.notificationId}, eventId ${event.eventId}, switching to un-snoozed state")

            // Update this time before posting notification as this is now used as a sort-key
            currentTime++ // so last change times are not all the same
            event.lastStatusChangeTime = currentTime
            event.snoozedUntil = 0
            event.displayStatus = EventDisplayStatus.Hidden // so we need to show it

            eventsToUpdate.add(event)
        }

        db.updateEvents(eventsToUpdate)

        return events
    }

    private fun generateNotificationRecords(
            context: Context,
            events: List<EventAlertRecord>,
            primaryEventId: Long?,
            settings: Settings,
            isQuietPeriodActive: Boolean,
            isReminder: Boolean,
            isRepost: Boolean
    ): MutableList<EventAlertNotificationRecord> {

        val ret = mutableListOf<EventAlertNotificationRecord>()

        val notificationsSettings = settings.notificationSettingsSnapshot

        val eventsSorted = events.sortedByDescending { it.instanceStartTime }

        var firstReminder = isReminder
        var needNotifyPostQuietHours = false
        var didAnySound = false

        for (event in eventsSorted) {
            // currently not displayed or forced -- post notifications
            val isPrimary = event.eventId == primaryEventId
            val isNew = isPrimary || (event.displayStatus == EventDisplayStatus.Hidden)

            var soundState = NotificationChannelManager.SoundState.Normal

            if (firstReminder) {
                if (events.any { it.isUnmutedAlarm } )
                    soundState = NotificationChannelManager.SoundState.Alarm
            }
            else if (!isQuietPeriodActive) {
                if (event.isUnmutedAlarm)
                    soundState = NotificationChannelManager.SoundState.Alarm
                else if (event.isMuted)
                    soundState = NotificationChannelManager.SoundState.Silent
            }
            else {
                // ignoring notificationSettings.useAlarmStream
                // ignoring event.isMuted
                if (event.isUnmutedAlarm)
                    soundState = NotificationChannelManager.SoundState.Alarm
                else
                    soundState = NotificationChannelManager.SoundState.Silent
            }

            DevLog.info(context, LOG_TAG, "Notification id ${event.notificationId}, eventId ${event.eventId}: primary=$isPrimary, new=$isNew, " +
                    "reminder=$isReminder, soundState=$soundState")

            ret.add(EventAlertNotificationRecord(
                    event,
                    soundState,
                    isPrimary,
                    isNew,
                    firstReminder,
                    alertOnlyOnce = !firstReminder || isRepost))

            firstReminder = false

            if ((isNew || isPrimary) && isQuietPeriodActive && soundState != NotificationChannelManager.SoundState.Alarm) {
                needNotifyPostQuietHours = true
            }

            if (soundState != NotificationChannelManager.SoundState.Silent)
                didAnySound = true
        }

        if (!isRepost) {
            val reminderState = ReminderState(context)

            if (didAnySound) {
                context.persistentState.notificationLastFireTime = System.currentTimeMillis()
                if (!isReminder)
                    reminderState.numRemindersFired = 0
            } else if (needNotifyPostQuietHours && !didAnySound) {
                DevLog.info(context, LOG_TAG, "Was quiet due to quiet hours - would remind after snooze period")

                if (!reminderState.quietHoursOneTimeReminderEnabled)
                    reminderState.quietHoursOneTimeReminderEnabled = true
            }
        }

        return ret;
    }


    ///
    /// Post events in collapsed state
    ///
    private fun postEverythingCollapsed(
            context: Context,
            db: EventsStorage,
            notificationRecords: MutableList<EventAlertNotificationRecord>,
            settings: Settings,
            isQuietPeriodActive: Boolean,
            isReminder: Boolean
    ) {
        if (notificationRecords.isEmpty()) {
            hideCollapsedEventsNotification(context)
            return
        }

        DevLog.info(context, LOG_TAG, "Posting ${notificationRecords.size} notifications in collapsed view")

        val notificationsSettings = settings.notificationSettingsSnapshot

        val events = notificationRecords.map{ it.event }

        // make sure we remove full notifications
        removeNotifications(context, events)

        val eventsToUpdate = events.filter { it.displayStatus != EventDisplayStatus.DisplayedCollapsed }
        if (eventsToUpdate.isNotEmpty()) {
            db.updateEvents(
                    eventsToUpdate,
                    snoozedUntil = 0L,
                    displayStatus = EventDisplayStatus.DisplayedCollapsed
            )
        }

        // now build actual notification and notify
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = pendingActivityIntent(context, intent, MAIN_ACTIVITY_NUM_NOTIFICATIONS_COLLAPSED_CODE)

        val numEvents = events.size

        var soundState = NotificationChannelManager.SoundState.Normal

        if (isReminder) {
            if (notificationRecords.any {it.event.isUnmutedAlarm})
                soundState = NotificationChannelManager.SoundState.Alarm
        }
        else if (!isQuietPeriodActive) {
            if (notificationRecords.any { it.event.isUnmutedAlarm && it.newNotification })
                soundState = NotificationChannelManager.SoundState.Alarm
            else if (notificationRecords.all { it.event.isMuted && it.newNotification})
                soundState = NotificationChannelManager.SoundState.Silent
        }
        else {
            // ignoring notificationSettings.useAlarmStream
            // ignoring event.isMuted
            if (notificationRecords.any { it.event.isUnmutedAlarm && it.newNotification})
                soundState = NotificationChannelManager.SoundState.Alarm
            else
                soundState = NotificationChannelManager.SoundState.Silent
        }

        val activeSoundState = // force Alarm if we have it enabled
                if (soundState == NotificationChannelManager.SoundState.Normal && settings.notificationUseAlarmStream)
                    NotificationChannelManager.SoundState.Alarm
                else
                    soundState

        val channel = NotificationChannelManager.createNotificationChannel(context, activeSoundState, isReminder)

        val notificationStyle = NotificationCompat.InboxStyle()

        val eventsSorted = events.sortedByDescending { it.instanceStartTime }

        val appendPlusMoreLine = eventsSorted.size > 5
        val lines = eventsSorted.take(if (appendPlusMoreLine) 4 else 5).map {
                    ev ->
                    val flags =
                            if (DateUtils.isToday(ev.displayedStartTime))
                                DateUtils.FORMAT_SHOW_TIME
                            else
                                DateUtils.FORMAT_SHOW_DATE
                    "${DateUtils.formatDateTime(context, ev.displayedStartTime, flags)}: ${ev.title}"
                }.toMutableList()

        if (appendPlusMoreLine) {
            lines.add(context.getString(R.string.plus_more).format(events.size - 4))
        }

        lines.forEach { notificationStyle.addLine(it) }
        notificationStyle.setBigContentTitle("")

        var contentTitle = context.getString(R.string.multiple_events_single_notification).format(events.size)

        if (isReminder) {
            val currentTime = System.currentTimeMillis()
            contentTitle += context.getString(R.string.reminder_at).format(
                    DateUtils.formatDateTime(context, currentTime, DateUtils.FORMAT_SHOW_TIME)
            )
        }

        val alertOnlyOnce = notificationRecords.all{it.alertOnlyOnce}
        val contentText = if (lines.size > 0) lines[0] else ""

        val builder =
                NotificationCompat.Builder(context, channel)
                        .setContentTitle(contentTitle)
                        .setContentText(contentText)
                        .setSmallIcon(R.drawable.stat_notify_calendar_multiple)
                        .setContentIntent(pendingIntent)
                        .setAutoCancel(false)
                        .setOngoing(false)
                        .setStyle(notificationStyle)
                        .setNumber(numEvents)
                        .setShowWhen(false)
                        .setOnlyAlertOnce(alertOnlyOnce)

        DevLog.info(context, LOG_TAG, "Building collapsed notification: alertOnlyOnce=$alertOnlyOnce, contentTitle=$contentTitle, number=$numEvents, channel=$channel")

        val snoozeIntent = Intent(context, NotificationActionSnoozeService::class.java)
        snoozeIntent.putExtra(Consts.INTENT_SNOOZE_PRESET, Consts.DEFAULT_SNOOZE_TIME_IF_NONE)
        snoozeIntent.putExtra(Consts.INTENT_SNOOZE_ALL_KEY, true)

        val pendingSnoozeIntent = pendingServiceIntent(context, snoozeIntent, EVENT_CODE_DEFAULT_SNOOOZE0_OFFSET)
        builder.setDeleteIntent(pendingSnoozeIntent)

        val notification = builder.build()

        try {
            context.notificationManager.notify(Consts.NOTIFICATION_ID_COLLAPSED, notification) // would update if already exists
        }
        catch (ex: Exception) {
            DevLog.error(context, LOG_TAG, "Error posting notification: $ex, ${ex.stackTrace}")
        }

        if (isReminder && settings.forwardReminersToPebble) {
            PebbleUtils.forwardNotificationToPebble(context, contentTitle, contentText, false)
        }
    }

//    private fun collapseDisplayedNotifications(
//            context: Context, db: EventsStorage,
//            events: List<EventAlertRecord>, settings: Settings,
//            force: Boolean,
//            isQuietPeriodActive: Boolean) {
//
//        DevLog.debug(LOG_TAG, "Hiding notifications for ${events.size} notification")
//
//        if (events.isEmpty()) {
//            hideCollapsedEventsNotification(context)
//            return
//        }
//
//        for (event in events) {
//            if ((event.displayStatus != EventDisplayStatus.Hidden) || force) {
//                //DevLog.debug(LOG_TAG, "Hiding notification id ${event.notificationId}, eventId ${event.eventId}")
//                removeNotification(context, event.notificationId)
//            }
//            else {
//                //DevLog.debug(LOG_TAG, "Skipping collapsing notification id ${event.notificationId}, eventId ${event.eventId} - already collapsed")
//            }
//
//            if (event.snoozedUntil != 0L || event.displayStatus != EventDisplayStatus.DisplayedCollapsed) {
//                db.updateEvent(event,
//                        snoozedUntil = 0L,
//                        displayStatus = EventDisplayStatus.DisplayedCollapsed)
//            }
//        }
//
//        postNumNotificationsCollapsed(context, db, settings, events, isQuietPeriodActive)
//    }

    // isRepost - if true - would re-post all active notifications. Normally only new notifications are posted to
    // avoid excessive blinking in the notifications area. Forced notifications are posted without sound or vibra
    @Suppress("UNUSED_PARAMETER")
    private fun postDisplayedEventNotifications(
            context: Context,
            db: EventsStorage,
            settings: Settings,
            formatter: EventFormatterInterface,
            notificationRecords: MutableList<EventAlertNotificationRecord>,
            isRepost: Boolean,
            isQuietPeriodActive: Boolean,
            primaryEventId: Long?,
            isReminder: Boolean
    ) {
        DevLog.debug(context, LOG_TAG, "Posting ${notificationRecords.size} notifications")

        val notificationsSettings = settings.notificationSettingsSnapshot

        //val hasAlarms = events.any { it.isUnmutedAlarm }

//        var playedAnySound = false

        val snoozePresets = settings.snoozePresets

        val events = notificationRecords.map {it.event}

        val eventsToUpdate = events.filter { it.displayStatus != EventDisplayStatus.DisplayedNormal }
        if (eventsToUpdate.isNotEmpty()) {
            db.updateEvents(
                    eventsToUpdate,
                    snoozedUntil = 0L,
                    displayStatus = EventDisplayStatus.DisplayedNormal
            )
        }

        postGroupNotification(
                context,
                notificationRecords.size
        )


//        var currentTime = System.currentTimeMillis()

        for (ntf in notificationRecords) {

            if (!isRepost && !ntf.isReminder && !ntf.newNotification) {
                // not a reminder and not a new notification
                // not need to post already visible notification, unless it is a repost where
                // we want to post everything
                continue
            }

            postNotification(
                    ctx = context,
                    formatter = formatter,
                    event = ntf.event,
                    notificationSettings = notificationsSettings,
                    snoozePresetsNotFiltered = snoozePresets,
                    isReminder = ntf.isReminder,
                    alertOnlyOnce = ntf.alertOnlyOnce,
                    soundState = ntf.soundState
            )

        }
   }

    private fun lastStatusChangeToSortingKey(lastStatusChangeTime: Long, eventId: Long): String {

        val sb = StringBuffer(20)

        var temp = eventId % (24 * 3)

        for (i in 0..3) {

            val chr = 24 - temp % 24
            temp /= 24

            sb.append(('A'.toInt() + chr).toChar())
        }

        temp = lastStatusChangeTime - 1500000000000L

        while (temp > 0) {

            val chr = 24 - temp % 24
            temp /= 24

            sb.append(('A'.toInt() + chr).toChar())
        }

        return sb.reverse().toString()
    }

    @Suppress("unused")
    private fun isNotificationVisible(ctx: Context, event: EventAlertRecord): Boolean {

        val intent = snoozeIntent(ctx, event.eventId, event.instanceStartTime, event.notificationId)
        val id = event.notificationId * EVENT_CODES_TOTAL + EVENT_CODE_SNOOOZE_OFFSET
        val pendingIntent: PendingIntent? = PendingIntent.getActivity(ctx, id, intent, PendingIntent.FLAG_NO_CREATE)
        return pendingIntent != null
    }

    private fun postGroupNotification(
            ctx: Context,
            numTotalEvents: Int
    ) {
        val notificationManager = ctx.notificationManager

        val intent = Intent(ctx, MainActivity::class.java)
        val pendingIntent = pendingActivityIntent(ctx, intent, MAIN_ACTIVITY_GROUP_NOTIFICATION_CODE)

        val text = ctx.resources.getString(R.string.N_calendar_events).format(numTotalEvents)

        val channel = NotificationChannelManager.createNotificationChannel(ctx,
                NotificationChannelManager.SoundState.Silent,
                false)

        val groupBuilder = NotificationCompat.Builder(ctx, channel)
                .setContentTitle(ctx.resources.getString(R.string.calendar))
                .setContentText(text)
                .setSubText(text)
                .setGroupSummary(true)
                .setGroup(NOTIFICATION_GROUP)
                .setContentIntent(pendingIntent)
                .setCategory(
                        NotificationCompat.CATEGORY_EVENT
                )
                .setWhen(System.currentTimeMillis())
                .setShowWhen(false)
                .setNumber(numTotalEvents)
                .setOnlyAlertOnce(true)

        if (numTotalEvents > 1) {
            groupBuilder.setSmallIcon(R.drawable.stat_notify_calendar_multiple)
        }
        else {
            groupBuilder.setSmallIcon(R.drawable.stat_notify_calendar)
        }

        // swipe does snooze
        val snoozeIntent = Intent(ctx, NotificationActionSnoozeService::class.java)
        snoozeIntent.putExtra(Consts.INTENT_SNOOZE_PRESET, Consts.DEFAULT_SNOOZE_TIME_IF_NONE)
        snoozeIntent.putExtra(Consts.INTENT_SNOOZE_ALL_KEY, true)

        val pendingSnoozeIntent =
                pendingServiceIntent(ctx, snoozeIntent, EVENT_CODE_DEFAULT_SNOOOZE0_OFFSET)

        groupBuilder.setDeleteIntent(pendingSnoozeIntent)

        try {
            notificationManager.notify(
                    Consts.NOTIFICATION_ID_BUNDLED_GROUP,
                    groupBuilder.build()
            )
        }
        catch (ex: Exception) {
            DevLog.error(ctx, LOG_TAG, "Exception: ${ex.detailed}")
        }
    }

    private fun postNotification(
            ctx: Context,
            formatter: EventFormatterInterface,
            event: EventAlertRecord,
            notificationSettings: NotificationSettingsSnapshot,
            snoozePresetsNotFiltered: LongArray,
            isReminder: Boolean,
            alertOnlyOnce: Boolean,
            soundState: NotificationChannelManager.SoundState
    ) {
        val notificationManager = ctx.notificationManager

//        val calendarIntent = CalendarIntents.getCalendarViewIntent(event)
//
//        val calendarPendingIntent =
//                TaskStackBuilder.create(ctx)
//                        .addNextIntentWithParentStack(calendarIntent)
//                        .getPendingIntent(
//                                event.notificationId * EVENT_CODES_TOTAL + EVENT_CODE_OPEN_OFFSET,
//                                PendingIntent.FLAG_UPDATE_CURRENT)

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


        val currentTime = System.currentTimeMillis()

        val notificationTextString = formatter.formatNotificationSecondaryText(event)
        var title = event.title

        if (isReminder) {
            title += ctx.getString(R.string.reminder_at).format(
                    DateUtils.formatDateTime(ctx, currentTime, DateUtils.FORMAT_SHOW_TIME)
            )
        }

        val sortKey = lastStatusChangeToSortingKey(event.lastStatusChangeTime, event.eventId)

        DevLog.info(ctx, LOG_TAG, "SortKey: ${event.eventId} -> ${event.lastStatusChangeTime} -> $sortKey")

        val primaryPendingIntent = snoozeActivityIntent
//                if (notificationSettings.notificationOpensSnooze)
//                    snoozeActivityIntent
//                else
//                    calendarPendingIntent

        var iconId = R.drawable.stat_notify_calendar
        if (event.isTask)
            iconId = R.drawable.ic_event_available_white_24dp
        else if (event.isMuted)
            iconId = R.drawable.stat_notify_calendar_muted

        val activeSoundState = // force Alarm if we have it enabled
                if (soundState == NotificationChannelManager.SoundState.Normal && notificationSettings.useAlarmStream)
                    NotificationChannelManager.SoundState.Alarm
                else
                    soundState

        val channel = NotificationChannelManager.createNotificationChannel(
                ctx,
                soundState = activeSoundState,
                isReminder = isReminder
        )

        val builder = NotificationCompat.Builder(ctx, channel)
                .setContentTitle(title)
                .setContentText(notificationTextString)
                .setSmallIcon(iconId)
                .setContentIntent(primaryPendingIntent)
                .setAutoCancel(false)
                .setOngoing(false)
                .setStyle(NotificationCompat.BigTextStyle().bigText(notificationTextString))
                .setWhen(event.lastStatusChangeTime)
                .setShowWhen(false)
                .setSortKey(sortKey)
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                .setOnlyAlertOnce(alertOnlyOnce)

        builder.setGroup(NOTIFICATION_GROUP)

        var snoozePresets =
                snoozePresetsNotFiltered
                        .filter {
                            snoozeTimeInMillis ->
                            snoozeTimeInMillis >= 0 ||
                                    (event.instanceStartTime + snoozeTimeInMillis + Consts.ALARM_THRESHOLD) > currentTime
                        }
                        .toLongArray()

        if (snoozePresets.isEmpty())
            snoozePresets = longArrayOf(Consts.DEFAULT_SNOOZE_TIME_IF_NONE)

//        val snoozeAction =
//                NotificationCompat.Action.Builder(
//                        Icon.createWithResource(ctx, R.drawable.ic_update_white_24dp),
//                        ctx.getString(com.github.quarck.calnotify.R.string.snooze),
//                        snoozeActivityIntent
//                ).build()

        val dismissAction =
                NotificationCompat.Action.Builder(
                        R.drawable.ic_clear_white_24dp,
                        ctx.getString(if (event.isTask) R.string.done else R.string.dismiss),
                        dismissPendingIntent
                ).build()


//        if (!notificationSettings.notificationOpensSnooze) {
//            DevLog.debug(LOG_TAG, "adding pending intent for snooze, event id ${event.eventId}, notificationId ${event.notificationId}")
//            builder.addAction(snoozeAction)
//        }

        val extender = NotificationCompat.WearableExtender()

        if ((notificationSettings.enableNotificationMute && !event.isTask) || event.isMuted) {
            // build and append

            val muteTogglePendingIntent =
                    pendingServiceIntent(ctx,
                            defaultMuteToggleIntent(
                                    ctx,
                                    event.eventId,
                                    event.instanceStartTime,
                                    event.notificationId,
                                    if (event.isMuted) 1 else 0
                            ),
                            event.notificationId * EVENT_CODES_TOTAL + EVENT_CODE_MUTE_TOGGLE_OFFSET
                    )

            val actionBuilder =
                    if (event.isMuted) {
                        NotificationCompat.Action.Builder(
                                R.drawable.ic_volume_off_white_24dp,
                                ctx.getString(R.string.un_mute_notification),
                                muteTogglePendingIntent
                        )

                    }
                    else {
                        NotificationCompat.Action.Builder(
                                R.drawable.ic_volume_up_white_24dp,
                                ctx.getString(R.string.mute_notification),
                                muteTogglePendingIntent
                        )
                    }

            val action = actionBuilder.build()
            builder.addAction(action)
            extender.addAction(action)
        }

        // swipe does snooze
        val defaultSnooze0PendingIntent =
                pendingServiceIntent(ctx,
                        defaultSnoozeIntent(ctx,
                                event.eventId,
                                event.instanceStartTime,
                                event.notificationId,
                                Consts.DEFAULT_SNOOZE_TIME_IF_NONE),
                        event.notificationId * EVENT_CODES_TOTAL + EVENT_CODE_DEFAULT_SNOOOZE0_OFFSET
                )

        builder.setDeleteIntent(defaultSnooze0PendingIntent)
        builder.addAction(dismissAction)

        if (notificationSettings.appendEmptyAction) {
            builder.addAction(
                    NotificationCompat.Action.Builder(
                            R.drawable.ic_empty,
                            "",
                            primaryPendingIntent
                    ).build())
        }

        for ((idx, snoozePreset) in snoozePresets.withIndex()) {
            if (idx == 0)
                continue

            if (idx >= EVENT_CODE_DEFAULT_SNOOOZE_MAX_ITEMS)
                break

            if (snoozePreset <= 0L) {
                val targetTime = event.displayedStartTime - Math.abs(snoozePreset)
                if (targetTime - System.currentTimeMillis() < 5 * 60 * 1000L) // at least minutes left until target
                    continue
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

        // In this combination of settings dismissing the notification would actually snooze it, so
        // add another "Dismiss Event" wearable action so to make it possible to actually dismiss
        // the event form wearable
        val dismissEventAction =
                NotificationCompat.Action.Builder(
                        R.drawable.ic_clear_white_24dp,
                        ctx.getString(com.github.quarck.calnotify.R.string.dismiss_event),
                        dismissPendingIntent
                ).build()

        extender.addAction(dismissEventAction)

        builder.extend(extender)

        builder.setColor(event.color.adjustCalendarColor(false))

        try {
            DevLog.info(ctx, LOG_TAG, "adding: notificationId=${event.notificationId}")

            notificationManager.notify(
                    event.notificationId,
                    builder.build()
            )
        }
        catch (ex: Exception) {
            DevLog.error(ctx, LOG_TAG, "Exception on notificationId=${event.notificationId}: ${ex.detailed}")
        }

        if (isReminder && notificationSettings.forwardReminersToPebble) {
            PebbleUtils.forwardNotificationToPebble(ctx, title, notificationTextString, false)
        }
    }

    private fun snoozeIntent(ctx: Context, eventId: Long, instanceStartTime: Long, notificationId: Int): Intent {

        val intent = Intent(ctx, ViewEventActivityNoRecents::class.java)
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

    private fun defaultMuteToggleIntent(ctx: Context, eventId: Long, instanceStartTime: Long, notificationId: Int, muteAction: Int): Intent {
        val intent = Intent(ctx, NotificationActionMuteToggleService::class.java)
        intent.putExtra(Consts.INTENT_NOTIFICATION_ID_KEY, notificationId)
        intent.putExtra(Consts.INTENT_EVENT_ID_KEY, eventId)
        intent.putExtra(Consts.INTENT_INSTANCE_START_TIME_KEY, instanceStartTime)
        intent.putExtra(Consts.INTENT_MUTE_ACTION, muteAction)
        return intent
    }

    private fun pendingServiceIntent(ctx: Context, intent: Intent, id: Int): PendingIntent
            = PendingIntent.getService(ctx, id, intent, PendingIntent.FLAG_CANCEL_CURRENT)

    private fun pendingActivityIntent(ctx: Context, intent: Intent, id: Int): PendingIntent {

        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        return PendingIntent.getActivity(ctx, id, intent, PendingIntent.FLAG_UPDATE_CURRENT)

    }

    private fun removeNotification(ctx: Context, notificationId: Int) {
        val notificationManager = ctx.notificationManager
        notificationManager.cancel(notificationId)
    }

    private fun removeNotifications(context: Context, events: Collection<EventAlertRecord>) {
        val notificationManager = context.notificationManager

        DevLog.info(context, LOG_TAG, "Removing 'full' notifications for  ${events.size} events")

        for (event in events)
            notificationManager.cancel(event.notificationId)
    }

    private fun removeVisibleNotifications(ctx: Context, events: Collection<EventAlertRecord>) {
        val notificationManager = ctx.notificationManager

        events.filter { it.displayStatus != EventDisplayStatus.Hidden }
                .forEach { notificationManager.cancel(it.notificationId) }
    }

//    private fun postNumNotificationsCollapsed(
//            context: Context,
//            db: EventsStorage,
//            settings: Settings,
//            events: List<EventAlertRecord>,
//            isQuietPeriodActive: Boolean
//    ) {
//        DevLog.debug(LOG_TAG, "Posting collapsed view notification for ${events.size} requests")
//
//        val intent = Intent(context, MainActivity::class.java)
//        val pendingIntent = pendingActivityIntent(context, intent, MAIN_ACTIVITY_NUM_NOTIFICATIONS_COLLAPSED_CODE)
//
//        val numEvents = events.size
//
//        val title = java.lang.String.format(context.getString(R.string.multiple_events), numEvents)
//
//        val text = context.getString(com.github.quarck.calnotify.R.string.multiple_events_details)
//
//        val bigText =
//                events
//                        .sortedByDescending { it.instanceStartTime }
//                        .take(30)
//                        .fold(
//                                StringBuilder(), {
//                            sb, ev ->
//
//                            val flags =
//                                    if (DateUtils.isToday(ev.displayedStartTime))
//                                        DateUtils.FORMAT_SHOW_TIME
//                                    else
//                                        DateUtils.FORMAT_SHOW_DATE
//
//                            sb.append("${DateUtils.formatDateTime(context, ev.displayedStartTime, flags)}: ${ev.title}\n")
//                        })
//                        .toString()
//
//        val channel = NotificationChannelManager.createNotificationChannelForPurpose(
//                context,
//                isReminder = false,
//                soundState = NotificationChannelManager.SoundState.Silent
//        )
//
//        val builder =
//                NotificationCompat.Builder(context, channel)
//                        .setContentTitle(title)
//                        .setContentText(text)
//                        .setSmallIcon(com.github.quarck.calnotify.R.drawable.stat_notify_calendar)
//                        .setContentIntent(pendingIntent)
//                        .setAutoCancel(false)
//                        .setOngoing(true)
//                        .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
//                        .setShowWhen(false)
//                        .setOnlyAlertOnce(true)
//
//
//        builder.setGroup(NOTIFICATION_GROUP)
//
//        val notification = builder.build()
//
//        context.notificationManager.notify(Consts.NOTIFICATION_ID_COLLAPSED, notification) // would update if already exists
//    }

    private fun hideCollapsedEventsNotification(context: Context) {
        DevLog.debug(LOG_TAG, "Hiding collapsed view notification")
        context.notificationManager.cancel(Consts.NOTIFICATION_ID_COLLAPSED)
    }

    private fun postDebugNotification(context: Context, notificationId: Int, title: String, text: String) {

        val notificationManager = context.notificationManager

        val appPendingIntent = pendingActivityIntent(context,
                Intent(context, MainActivity::class.java), notificationId)

        val builder = NotificationCompat.Builder(context, NotificationChannelManager.createDefaultNotificationChannelDebug(context))
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.drawable.stat_notify_calendar)
                .setContentIntent(appPendingIntent)
                .setAutoCancel(false)
                .setShowWhen(false)
                .setCategory(NotificationCompat.CATEGORY_ERROR)
                .setOnlyAlertOnce(true)

        builder.setGroup(NOTIFICATION_GROUP)

        val notification = builder.build()

        try {
            notificationManager.notify(notificationId, notification)
        }
        catch (ex: Exception) {
            DevLog.error(context, LOG_TAG, "Exception: ${ex.detailed}")
        }
    }

    override fun postNotificationsAutoDismissedDebugMessage(context: Context) {

        postDebugNotification(
                context,
                Consts.NOTIFICATION_ID_DEBUG0_AUTO_DISMISS,
                "DEBUG: Events dismissed",
                "DEBUG: Some requests were auto-dismissed due to calendar move"
        )
    }

    override fun postNearlyMissedNotificationDebugMessage(context: Context) {

        postDebugNotification(
                context,
                Consts.NOTIFICATION_ID_DEBUG3_NEARLY_MISS,
                "DEBUG: Nearly missed event",
                "DEBUG: Some events has fired later than expeted"
        )
    }

    override fun postNotificationsAlarmDelayDebugMessage(context: Context, title: String, text: String) {

        postDebugNotification(
                context,
                Consts.NOTIFICATION_ID_DEBUG1_ALARM_DELAYS,
                title,
                text
        )
    }

    override fun postNotificationsSnoozeAlarmDelayDebugMessage(context: Context, title: String, text: String) {

        postDebugNotification(
                context,
                Consts.NOTIFICATION_ID_DEBUG2_SNOOZE_ALARM_DELAYS,
                title,
                text
        )

    }

    companion object {
        private const val LOG_TAG = "EventNotificationManager"

        private const val NOTIFICATION_GROUP = "GROUP_1"

        const val EVENT_CODE_SNOOOZE_OFFSET = 0
        const val EVENT_CODE_DISMISS_OFFSET = 1
        @Suppress("unused")
        const val EVENT_CODE_DELETE_OFFSET = 2
        const val EVENT_CODE_OPEN_OFFSET = 3
        const val EVENT_CODE_DEFAULT_SNOOOZE0_OFFSET = 4
        const val EVENT_CODE_MUTE_TOGGLE_OFFSET = 5
        const val EVENT_CODE_DEFAULT_SNOOOZE_MAX_ITEMS = 10
        const val EVENT_CODES_TOTAL = 16

        const val MAIN_ACTIVITY_EVERYTHING_COLLAPSED_CODE = 0
        const val MAIN_ACTIVITY_NUM_NOTIFICATIONS_COLLAPSED_CODE = 1
        const val MAIN_ACTIVITY_REMINDER_CODE = 2
        const val MAIN_ACTIVITY_GROUP_NOTIFICATION_CODE = 3
    }
}
