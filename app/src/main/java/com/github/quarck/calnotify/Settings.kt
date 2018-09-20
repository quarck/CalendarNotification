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

import android.content.Context
import android.os.Build
import com.github.quarck.calnotify.prefs.PreferenceUtils
import com.github.quarck.calnotify.utils.PersistentStorageBase
import com.github.quarck.calnotify.utils.toIntOrNull


enum class NotificationSwipeBehavior(val code: Int)
{
    DismissEvent(0),
    SnoozeEvent(1),
    SwipeDisallowed(2);

    companion object {
        @JvmStatic
        fun fromInt(v: Int) = values()[v]
    }
}

data class NotificationSettingsSnapshot
(
        val notificationSwipeBehavior: NotificationSwipeBehavior,
        val groupNotificationSwipeBehavior: NotificationSwipeBehavior,
        val postGroupNotification: Boolean,
        val enableNotificationMute: Boolean,
        val appendEmptyAction: Boolean,
        val useAlarmStream: Boolean,
        val forwardReminersToPebble: Boolean,
        val showSnoozeButton: Boolean
) {
    val ongoingIndividual: Boolean
        get() = notificationSwipeBehavior == NotificationSwipeBehavior.SwipeDisallowed

    val ongoingGroup: Boolean
        get() = groupNotificationSwipeBehavior == NotificationSwipeBehavior.SwipeDisallowed

    val swipeSnoozeIndividual: Boolean
        get() = notificationSwipeBehavior == NotificationSwipeBehavior.SnoozeEvent

    val swipeSnoozeGroup: Boolean
        get() = groupNotificationSwipeBehavior == NotificationSwipeBehavior.SnoozeEvent
}


class Settings(context: Context) : PersistentStorageBase(context) {

    var devModeEnabled: Boolean
        get() = getBoolean(DEVELOPER_MODE_KEY, false)
        set(value) = setBoolean(DEVELOPER_MODE_KEY, value)

    var notificationAddEmptyAction: Boolean
        get() = getBoolean(NOTIFICATION_ADD_EMPTY_ACTION_KEY, false)
        set(value) = setBoolean(NOTIFICATION_ADD_EMPTY_ACTION_KEY, value)

    var viewAfterEdit: Boolean
        get() = getBoolean(VIEW_AFTER_EDIT_KEY, true)
        set(value) = setBoolean(VIEW_AFTER_EDIT_KEY, value)

    var snoozePresetsRaw: String
        get() = getString(SNOOZE_PRESET_KEY, DEFAULT_SNOOZE_PRESET)
        set(value) = setString(SNOOZE_PRESET_KEY, value)

    val snoozePresets: LongArray
        get() {
            var ret = PreferenceUtils.parseSnoozePresets(snoozePresetsRaw)

            if (ret == null)
                ret = PreferenceUtils.parseSnoozePresets(DEFAULT_SNOOZE_PRESET)

            if (ret == null || ret.size == 0)
                ret = Consts.DEFAULT_SNOOZE_PRESETS

            return ret;
        }

    val firstNonNegativeSnoozeTime: Long
        get() {
            val result = snoozePresets.firstOrNull { snoozeTimeInMillis -> snoozeTimeInMillis >= 0 }
            return result ?: Consts.DEFAULT_SNOOZE_TIME
        }

//    var notificationSwipeDoesSnooze: Boolean
//        get() = getBoolean(NOTIFICATION_SWIPE_DOES_SNOOZE_KEY, false)
//        set(value) = setBoolean(NOTIFICATION_SWIPE_DOES_SNOOZE_KEY, value)

    var notificationUseAlarmStream: Boolean
        get() = getBoolean(USE_ALARM_STREAM_FOR_NOTIFICATION_KEY, false)
        set(value) = setBoolean(USE_ALARM_STREAM_FOR_NOTIFICATION_KEY, value)

    var remindersEnabled: Boolean
        get() = getBoolean(ENABLE_REMINDERS_KEY, false)
        set(value) = setBoolean(ENABLE_REMINDERS_KEY, value)

    var remindersIntervalMillisPatternRaw
        get() = getString(REMINDER_INTERVAL_PATTERN_KEY, "")
        set(value) = setString(REMINDER_INTERVAL_PATTERN_KEY, value)

    var remindersIntervalMillisPattern: LongArray
        get() {
            val raw = remindersIntervalMillisPatternRaw

            val ret: LongArray?

            if (!raw.isEmpty()) {
                ret = PreferenceUtils.parseSnoozePresets(raw)
            } else {
                val intervalSeconds = getInt(REMIND_INTERVAL_SECONDS_KEY, 0)
                if (intervalSeconds != 0) {
                    ret = longArrayOf(intervalSeconds * 1000L)
                }
                else {
                    val intervalMinutes = getInt(REMIND_INTERVAL_MINUTES_KEY, DEFAULT_REMINDER_INTERVAL_MINUTES)
                    ret = longArrayOf(intervalMinutes * 60L * 1000L)
                }
            }

            return ret ?: longArrayOf(DEFAULT_REMINDER_INTERVAL_SECONDS * 1000L)
        }
        set(value) {
            remindersIntervalMillisPatternRaw = PreferenceUtils.formatPattern(value)
        }

    fun reminderIntervalMillisForIndex(index: Int): Long {
        val pattern = remindersIntervalMillisPattern
        val value = pattern[index % pattern.size]
        return Math.max(value, Consts.MIN_REMINDER_INTERVAL_SECONDS * 1000L)
    }

    fun currentAndNextReminderIntervalsMillis(indexCurrent: Int): Pair<Long, Long> {
        val pattern = remindersIntervalMillisPattern
        val minInterval = Consts.MIN_REMINDER_INTERVAL_SECONDS * 1000L

        val current = Math.max(pattern[indexCurrent % pattern.size], minInterval)
        val next = Math.max(pattern[(indexCurrent + 1) % pattern.size], minInterval)

        return Pair(current, next)
    }

    var maxNumberOfReminders: Int
        get() = getString(MAX_REMINDERS_KEY, DEFAULT_MAX_REMINDERS).toIntOrNull() ?: 0
        set(value) = setString(MAX_REMINDERS_KEY, "$value")

    var quietHoursEnabled: Boolean
        get() = getBoolean(ENABLE_QUIET_HOURS_KEY, false)
        set(value) = setBoolean(ENABLE_QUIET_HOURS_KEY, value)

    var quietHoursFrom: Pair<Int, Int>
        get() = PreferenceUtils.unpackTime(getInt(QUIET_HOURS_FROM_KEY, 0))
        set(value) = setInt(QUIET_HOURS_FROM_KEY, PreferenceUtils.packTime(value))

    var quietHoursTo: Pair<Int, Int>
        get() = PreferenceUtils.unpackTime(getInt(QUIET_HOURS_TO_KEY, 0))
        set(value) = setInt(QUIET_HOURS_TO_KEY, PreferenceUtils.packTime(value))

    fun getCalendarIsHandled(calendarId: Long) =
            getBoolean("$CALENDAR_IS_HANDLED_KEY_PREFIX.$calendarId", true)

    fun setCalendarIsHandled(calendarId: Long, enabled: Boolean) =
            setBoolean("$CALENDAR_IS_HANDLED_KEY_PREFIX.$calendarId", enabled)

    var versionCodeFirstInstalled: Long
        get() = getLong(VERSION_CODE_FIRST_INSTALLED_KEY, 0L)
        set(value) = setLong(VERSION_CODE_FIRST_INSTALLED_KEY, value)

    var useSetAlarmClock: Boolean
        get() = getBoolean(BEHAVIOR_USE_SET_ALARM_CLOCK_KEY, true)
        set(value) = setBoolean(BEHAVIOR_USE_SET_ALARM_CLOCK_KEY, value)

    var useSetAlarmClockForFailbackEventPaths: Boolean
        get() = getBoolean(BEHAVIOR_USE_SET_ALARM_CLOCK_FOR_FAILBACK_KEY, false)
        set(value) = setBoolean(BEHAVIOR_USE_SET_ALARM_CLOCK_FOR_FAILBACK_KEY, value)

    var shouldRemindForEventsWithNoReminders: Boolean
        get() = getBoolean(SHOULD_REMIND_FOR_EVENTS_WITH_NO_REMINDERS_KEY, false)
        set(value) = setBoolean(SHOULD_REMIND_FOR_EVENTS_WITH_NO_REMINDERS_KEY, value)

    var defaultReminderTimeForEventWithNoReminderMinutes: Int
        get() = getInt(DEFAULT_REMINDER_TIME_FOR_EVENTS_WITH_NO_REMINDER_KEY, 15)
        set(value) = setInt(DEFAULT_REMINDER_TIME_FOR_EVENTS_WITH_NO_REMINDER_KEY, value)

    val defaultReminderTimeForEventWithNoReminderMillis: Long
        get() = defaultReminderTimeForEventWithNoReminderMinutes * 60L * 1000L

    var defaultReminderTimeForAllDayEventWithNoreminderMinutes: Int
        get() = getInt(DEFAULT_REMINDER_TIME_FOR_ALL_DAY_EVENTS_WITH_NO_REMINDER, -480)
        set(value) = setInt(DEFAULT_REMINDER_TIME_FOR_ALL_DAY_EVENTS_WITH_NO_REMINDER, value)

    val defaultReminderTimeForAllDayEventWithNoreminderMillis: Long
        get() = defaultReminderTimeForAllDayEventWithNoreminderMinutes * 60L * 1000L

    val manualCalWatchScanWindow: Long
        get() = getLong(CALENDAR_MANUAL_WATCH_RELOAD_WINDOW_KEY, 30L * 24L * 3600L * 1000L) // 1 month by default

    var dontShowDeclinedEvents: Boolean
        get() = getBoolean(DONT_SHOW_DECLINED_EVENTS_KEY, false)
        set(value) = setBoolean(DONT_SHOW_DECLINED_EVENTS_KEY, value)

    var dontShowCancelledEvents: Boolean
        get() = getBoolean(DONT_SHOW_CANCELLED_EVENTS_KEY, false)
        set(value) = setBoolean(DONT_SHOW_CANCELLED_EVENTS_KEY, value)

    var dontShowAllDayEvents: Boolean
        get() = getBoolean(DONT_SHOW_ALL_DAY_EVENTS_KEY, false)
        set(value) = setBoolean(DONT_SHOW_ALL_DAY_EVENTS_KEY, value)

    var enableMonitorDebug: Boolean
        get() = getBoolean(ENABLE_MONITOR_DEBUGGING_KEY, false)
        set(value) = setBoolean(ENABLE_MONITOR_DEBUGGING_KEY, value)

    var firstDayOfWeek: Int
        get() = getInt(FIRST_DAY_OF_WEEK_KEY, 1)
        set(value) = setInt(FIRST_DAY_OF_WEEK_KEY, value)

    var shouldKeepLogs: Boolean
        get() = getBoolean(KEEP_APP_LOGS_KEY, false)
        set(value) = setBoolean(KEEP_APP_LOGS_KEY, value)

    var enableCalendarRescan: Boolean
        get() = getBoolean(ENABLE_CALENDAR_RESCAN_KEY, true)
        set(value) = setBoolean(ENABLE_CALENDAR_RESCAN_KEY, value)

    val rescanCreatedEvent: Boolean
        get() = true

    var notifyOnEmailOnlyEvents: Boolean
        get() = getBoolean(NOTIFY_ON_EMAIL_ONLY_EVENTS_KEY, false)
        set(value) = setBoolean(NOTIFY_ON_EMAIL_ONLY_EVENTS_KEY, value)


    var forwardReminersToPebble: Boolean
        get() = getBoolean(FORWARD_REMINDERS_TO_PEBBLE, false)
        set(value) = setBoolean(FORWARD_REMINDERS_TO_PEBBLE, value)

    var notificationSwipeBehavior: NotificationSwipeBehavior
        get() = NotificationSwipeBehavior.fromInt(getInt(NOTIFICATION_SWIPE_BEHAVIOR, NotificationSwipeBehavior.SwipeDisallowed.code))
        set(value) = setInt(NOTIFICATION_SWIPE_BEHAVIOR, value.code)

    var groupNotificationSwipeBehavior: NotificationSwipeBehavior
        get() = NotificationSwipeBehavior.fromInt(getInt(GROUP_NOTIFICATION_SWIPE_BEHAVIOR, NotificationSwipeBehavior.SwipeDisallowed.code))
        set(value) = setInt(GROUP_NOTIFICATION_SWIPE_BEHAVIOR, value.code)

    var postGroupNotification: Boolean
        get() = getBoolean(GROUP_NOTIFICAITONS, false)
        set(value) = setBoolean(GROUP_NOTIFICAITONS, value)

    val allowMuteAndAlarm: Boolean
        get() = (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) || !postGroupNotification

    var doNotShowBatteryOptimisationWarning: Boolean
        get() = getBoolean(DO_NOT_SHOW_BATTERY_OPTIMISATION, false)
        set(value) = setBoolean(DO_NOT_SHOW_BATTERY_OPTIMISATION, value)

    var showSnoozeButton: Boolean
        get() = getBoolean(SHOW_SNOOZE_BUTTON, false)
        set(value) = setBoolean(SHOW_SNOOZE_BUTTON, value)

    val notificationSettingsSnapshot: NotificationSettingsSnapshot
        get() = NotificationSettingsSnapshot(
                notificationSwipeBehavior = notificationSwipeBehavior,
                groupNotificationSwipeBehavior = groupNotificationSwipeBehavior,
                postGroupNotification = postGroupNotification,
                enableNotificationMute = remindersEnabled && allowMuteAndAlarm,
                appendEmptyAction = notificationAddEmptyAction,
                useAlarmStream = notificationUseAlarmStream,
                forwardReminersToPebble = forwardReminersToPebble,
                showSnoozeButton = showSnoozeButton
        )
    var alwaysUseExternalEditor: Boolean
        get() = getBoolean(ALWAYS_USE_EXTERNAL_EDITOR, false)
        set(value) = setBoolean(ALWAYS_USE_EXTERNAL_EDITOR, value)

    companion object {

        // Preferences keys
        private const val SNOOZE_PRESET_KEY = "pref_snooze_presets"

        private const val VIEW_AFTER_EDIT_KEY = "show_event_after_reschedule"

        private const val ENABLE_REMINDERS_KEY = "enable_reminding_key"
        private const val REMIND_INTERVAL_MINUTES_KEY = "remind_interval_key2"
        private const val REMIND_INTERVAL_SECONDS_KEY = "remind_interval_key_seconds"
        private const val REMINDER_INTERVAL_PATTERN_KEY = "remind_interval_key_pattern"

        private const val MAX_REMINDERS_KEY = "reminder_max_reminders"

        private const val ENABLE_QUIET_HOURS_KEY = "enable_quiet_hours"
        private const val QUIET_HOURS_FROM_KEY = "quiet_hours_from"
        private const val QUIET_HOURS_TO_KEY = "quiet_hours_to"

        private const val CALENDAR_IS_HANDLED_KEY_PREFIX = "calendar_handled_"

        private const val VERSION_CODE_FIRST_INSTALLED_KEY = "first_installed_ver"

        private const val BEHAVIOR_USE_SET_ALARM_CLOCK_KEY = "use_set_alarm_clock"
        private const val BEHAVIOR_USE_SET_ALARM_CLOCK_FOR_FAILBACK_KEY = "use_set_alarm_clock_for_failback"

        private const val SHOULD_REMIND_FOR_EVENTS_WITH_NO_REMINDERS_KEY = "remind_events_no_rmdnrs"
        private const val DEFAULT_REMINDER_TIME_FOR_EVENTS_WITH_NO_REMINDER_KEY = "default_rminder_time"
        private const val DEFAULT_REMINDER_TIME_FOR_ALL_DAY_EVENTS_WITH_NO_REMINDER = "default_all_day_rminder_time"

        private const val CALENDAR_MANUAL_WATCH_RELOAD_WINDOW_KEY = "manual_watch_reload_window"

        private const val DONT_SHOW_DECLINED_EVENTS_KEY = "dont_show_declined_events"
        private const val DONT_SHOW_CANCELLED_EVENTS_KEY = "dont_show_cancelled_events"
        private const val DONT_SHOW_ALL_DAY_EVENTS_KEY = "dont_show_all_day_events"

        private const val ENABLE_MONITOR_DEBUGGING_KEY = "enableMonitorDebug"

        private const val FIRST_DAY_OF_WEEK_KEY = "first_day_of_week_2"

        private const val USE_ALARM_STREAM_FOR_NOTIFICATION_KEY = "use_alarm_stream_for_notification"

        private const val KEEP_APP_LOGS_KEY = "keep_logs"

        private const val ENABLE_CALENDAR_RESCAN_KEY = "enable_manual_calendar_rescan"
        private const val NOTIFY_ON_EMAIL_ONLY_EVENTS_KEY = "notify_on_email_only_events"

        private const val DEVELOPER_MODE_KEY = "dev"

        private const val NOTIFICATION_ADD_EMPTY_ACTION_KEY = "add_empty_action_to_the_end"

        private const val FORWARD_REMINDERS_TO_PEBBLE = "forward_reminders_to_pebble"

        private const val NOTIFICATION_SWIPE_BEHAVIOR = "notification_swipe_behavior"
        private const val GROUP_NOTIFICATION_SWIPE_BEHAVIOR = "group_notification_swipe_behavior"
        private const val GROUP_NOTIFICAITONS = "group_notifications_001"

        private const val SHOW_SNOOZE_BUTTON = "show_snooze_btn_0001"

        private const val ALWAYS_USE_EXTERNAL_EDITOR = "always_use_external_editor_0001"

        private const val DO_NOT_SHOW_BATTERY_OPTIMISATION = "dormi_mi_volas"

        // Default values
        internal const val DEFAULT_SNOOZE_PRESET = "15m, 1h, 4h, 1d, -5m"
        internal const val DEFAULT_REMINDER_INTERVAL_MINUTES = 10
        internal const val DEFAULT_REMINDER_INTERVAL_SECONDS = 600
        internal const val DEFAULT_MAX_REMINDERS = "0"
    }
}
