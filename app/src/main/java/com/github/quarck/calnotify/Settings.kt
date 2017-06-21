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
import android.media.RingtoneManager
import android.net.Uri
import com.github.quarck.calnotify.prefs.PreferenceUtils
import com.github.quarck.calnotify.utils.PersistentStorageBase
import com.github.quarck.calnotify.utils.toIntOrNull


data class NotificationSettingsSnapshot
(
        val showDismissButton: Boolean,
        val notificationSwipeDoesSnooze: Boolean,
        val ringtoneUri: Uri?,
        val vibrationOn: Boolean,
        val vibrationPattern: LongArray,
        val ledNotificationOn: Boolean,
        val ledColor: Int,
        val ledPattern: IntArray,
        val headsUpNotification: Boolean,
        val forwardEventToPebble: Boolean,
        val pebbleOldFirmware: Boolean,
        val forwardReminderToPebble: Boolean,
        val showColorInNotification: Boolean,
        val notificationOpensSnooze: Boolean,
        val quietHoursMuteLED: Boolean,
        val useAlarmStream: Boolean
)

class Settings(context: Context) : PersistentStorageBase(context) {

    val showDismissButton: Boolean
        get() = getBoolean(DISMISS_ENABLED_KEY, true)

    val notificationSwipeDoesSnooze: Boolean
        get() = getBoolean(NOTIFICATION_SWIPE_DOES_SNOOZE_KEY, false)

    val vibraOn: Boolean
        get() = getBoolean(VIBRATION_ENABLED_KEY, true)

    val vibrationPattern: LongArray
        get() {
            val idx = getString(VIBRATION_PATTERN_KEY, "0").toInt()

            val patterns = Consts.VIBRATION_PATTERNS
            return if (idx < patterns.size && idx >= 0) patterns[idx] else patterns[0]
        }

    val notificationOpensSnooze: Boolean
        get() = getBoolean(NOTIFICATION_OPENS_SNOOZE_KEY, false)

    val notificationAutoDismissOnReschedule: Boolean
        get() = getBoolean(NOTIFICATION_AUTO_DISMISS_KEY, false)

    var debugNotificationAutoDismiss: Boolean
        get() = getBoolean(NOTIFICATION_AUTO_DISMISS_DEBUG_KEY, false)
        set(value) = setBoolean(NOTIFICATION_AUTO_DISMISS_DEBUG_KEY, value)

    var debugAlarmDelays: Boolean
        get() = getBoolean(NOTIFICATION_ALARM_DELAYS_DEBUG_KEY, false)
        set(value) = setBoolean(NOTIFICATION_ALARM_DELAYS_DEBUG_KEY, value)

    val ledNotificationOn: Boolean
        get() = getBoolean(LED_ENABLED_KEY, true)

    val ledColor: Int
        get() = getInt(LED_COLOR_KEY, Consts.DEFAULT_LED_COLOR)

    val ledPattern: IntArray
        get() =
        getString(LED_PATTERN_KEY, Consts.DEFAULT_LED_PATTERN)
                .split(",")
                .map { it.toInt() }
                .toIntArray()

    val forwardEventToPebble: Boolean
        get() = getBoolean(FORWARD_TO_PEBBLE_KEY, false)

    val pebbleOldFirmware: Boolean
        get() = getBoolean(PEBBLE_TEXT_IN_TITLE_KEY, false)

    val forwardReminderToPebble: Boolean
        get() = getBoolean(PEBBLE_FORWARD_REMINDERS_KEY, false)

    val headsUpNotification: Boolean
        get() = getBoolean(HEADS_UP_NOTIFICATINO_KEY, true)

    val notificationWakeScreen: Boolean
        get() = getBoolean(NOTIFICATION_WAKE_SCREEN_KEY, false)

    val showColorInNotification: Boolean
        get() = getBoolean(NOTIFICATION_CALENDAR_COLOR_KEY, true)

    val notificationPlayTts: Boolean
        get() = getBoolean(NOTIFICATION_TTS_KEY, false)

    val viewAfterEdit: Boolean
        get() = getBoolean(VIEW_AFTER_EDIT_KEY, true)

    val snoozePresets: LongArray
        get() {
            var ret = PreferenceUtils.parseSnoozePresets(getString(SNOOZE_PRESET_KEY, DEFAULT_SNOOZE_PRESET))

            if (ret == null)
                ret = PreferenceUtils.parseSnoozePresets(DEFAULT_SNOOZE_PRESET)

            if (ret == null || ret.size == 0)
                ret = Consts.DEFAULT_SNOOZE_PRESETS

            return ret;
        }

    val showCustomSnoozeAndUntil: Boolean
        get() = getBoolean(SHOW_CUSTOM_SNOOZE_TIMES_KEY, true)//

    private fun loadRingtoneUri(settingsKey: String): Uri? {
        var ringtone: Uri?

        val ringtoneNotSetValue = "--ringtone-not-set-value--"

        val uriValue = getString(settingsKey, ringtoneNotSetValue)

        if (uriValue.isEmpty()) {
            // Silent mode -- string is empty
            ringtone = null
        }
        else if (uriValue == ringtoneNotSetValue) {
            // use default -- not set
            ringtone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        }
        else {
            // parse URL - custom ringtone
            try {
                ringtone = Uri.parse(uriValue)
            }
            catch (e: Exception) {
                e.printStackTrace()
                ringtone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            }
        }

        return ringtone
    }

    val ringtoneURI: Uri?
        get() = loadRingtoneUri(RINGTONE_KEY)

    val reminderRingtoneURI: Uri?
        get() = loadRingtoneUri(if (reminderCustomRingtone) REMINDERS_RINGTONE_KEY else RINGTONE_KEY)

    val notificationUseAlarmStream: Boolean
        get() = getBoolean(USE_ALARM_STREAM_FOR_NOTIFICATION_KEY, false)

    val reminderCustomRingtone: Boolean
        get() = getBoolean(REMINDERS_CUSTOM_RINGTONE_KEY, false)

    val reminderCustomVibra: Boolean
        get() = getBoolean(REMINDERS_CUSTOM_VIBRATION_KEY, false)

    val reminderVibraOn: Boolean
        get() = getBoolean(if (reminderCustomVibra) REMINDERS_VIBRATION_ENABLED_KEY else VIBRATION_ENABLED_KEY, true)

    val reminderVibrationPattern: LongArray
        get() {
            val idx = getString(
                    if (reminderCustomVibra) REMINDERS_VIBRATION_PATTERN_KEY else VIBRATION_PATTERN_KEY, "0").toInt()

            val patterns = Consts.VIBRATION_PATTERNS
            return if (idx < patterns.size && idx >= 0) patterns[idx] else patterns[0]
        }

    val maxNotifications: Int
        get() = getInt(NOTIFICATION_MAX_NOTIFICATIONS_KEY, Consts.DEFAULT_NOTIFICATIONS)

    val collapseEverything: Boolean
        get() = getBoolean(NOTIFICATION_COLLAPSE_EVERYTHING_KEY, false)

    val remindersEnabled: Boolean
        get() = getBoolean(ENABLE_REMINDERS_KEY, false)

    val remindersIntervalMillis: Long
        get() = getInt(REMIND_INTERVAL_KEY, DEFAULT_REMINDER_INTERVAL) * 60L * 1000L;

    val maxNumberOfReminders: Int
        get() = getString(MAX_REMINDERS_KEY, DEFAULT_MAX_REMINDERS).toIntOrNull() ?: 0

    val quietHoursEnabled: Boolean
        get() = getBoolean(ENABLE_QUIET_HOURS_KEY, false)

    val quietHoursFrom: Pair<Int, Int>
        get() = PreferenceUtils.unpackTime(getInt(QUIET_HOURS_FROM_KEY, 0))

    val quietHoursTo: Pair<Int, Int>
        get() = PreferenceUtils.unpackTime(getInt(QUIET_HOURS_TO_KEY, 0))

    val quietHoursMutePrimary: Boolean
        get() = getBoolean(QUIET_HOURS_MUTE_PRIMARY_KEY, false)

    val quietHoursMuteLED: Boolean
        get() = getBoolean(QUIET_HOURS_MUTE_LED, false)

    fun getCalendarIsHandled(calendarId: Long) =
            getBoolean("$CALENDAR_IS_HANDLED_KEY_PREFIX.$calendarId", true)

    fun setCalendarIsHandled(calendarId: Long, enabled: Boolean) =
            setBoolean("$CALENDAR_IS_HANDLED_KEY_PREFIX.$calendarId", enabled)

    var useCompactView: Boolean
        get() = getBoolean(USE_COMPACT_LAYOUT_KEY, true)
        set(value) = setBoolean(USE_COMPACT_LAYOUT_KEY, value)

    val haloLightDatePicker: Boolean
        get() = getBoolean(HALO_LIGHT_DATE_PICKER_KEY, false)

    val haloLightTimePicker: Boolean
        get() = getBoolean(HALO_LIGHT_TIMER_PICKER_KEY, false)

    val keepHistory: Boolean
        get() = getBoolean(KEEP_HISTORY_KEY, true)

    val keepHistoryDays: Int
        get() = getString(KEEP_HISTORY_DAYS_KEY, "").toIntOrNull() ?: 3

    val keepHistoryMilliseconds: Long
        get() = keepHistoryDays.toLong() * Consts.DAY_IN_MILLISECONDS

    var versionCodeFirstInstalled: Long
        get() = getLong(VERSION_CODE_FIRST_INSTALLED_KEY, 0L)
        set(value) = setLong(VERSION_CODE_FIRST_INSTALLED_KEY, value)

    var showNewStyleMessage: Boolean
        get() = getBoolean(SHOW_NEW_STYLE_MSG_KEY, true)
        set(value) = setBoolean(SHOW_NEW_STYLE_MSG_KEY, value)

    var dontShowMarshmallowWarning: Boolean
        get() = getBoolean(HIDE_MARSHMALLOW_WARNING_KEY, false)
        set(value) = setBoolean(HIDE_MARSHMALLOW_WARNING_KEY, value)

    var dontShowMarshmallowWarningInSettings: Boolean
        get() = getBoolean(HIDE_MARSHMALLOW_WARNING_IN_SETTINGS_KEY, false)
        set(value) = setBoolean(HIDE_MARSHMALLOW_WARNING_IN_SETTINGS_KEY, value)

    val useSetAlarmClock: Boolean
        get() = getBoolean(BEHAVIOR_USE_SET_ALARM_CLOCK_KEY, true)

    val useSetAlarmClockForFailbackEventPaths: Boolean
        get() = getBoolean(BEHAVIOR_USE_SET_ALARM_CLOCK_FOR_FAILBACK_KEY, false)

    val shouldRemindForEventsWithNoReminders: Boolean
        get() = getBoolean(SHOULD_REMIND_FOR_EVENTS_WITH_NO_REMINDERS_KEY, false)

    val defaultReminderTimeForEventWithNoReminder: Long
        get() = getInt(DEFAULT_REMINDER_TIME_FOR_EVENTS_WITH_NO_REMINDER_KEY, 15) * 60L * 1000L

    val defaultReminderTimeForAllDayEventWithNoreminder: Long
        get() = getInt(DEFAULT_REMINDER_TIME_FOR_ALL_DAY_EVENTS_WITH_NO_REMINDER, -480) * 60L * 1000L

    val manualCalWatchScanWindow: Long
        get() = getLong(CALENDAR_MANUAL_WATCH_RELOAD_WINDOW_KEY, 30L * 24L * 3600L * 1000L) // 1 month by default

    val dontShowDeclinedEvents: Boolean
        get() = getBoolean(DONT_SHOW_DECLINED_EVENTS_KEY, false)

    val dontShowCancelledEvents: Boolean
        get() = getBoolean(DONT_SHOW_CANCELLED_EVENTS_KEY, false)

    val dontShowAllDayEvents: Boolean
        get() = getBoolean(DONT_SHOW_ALL_DAY_EVENTS_KEY, false)

    var enableMonitorDebug: Boolean
        get() = getBoolean(ENABLE_MONITOR_DEBUGGING_KEY, false)
        set(value) = setBoolean(ENABLE_MONITOR_DEBUGGING_KEY, value)

    val firstDayOfWeek: Int
        get() = getString(FIRST_DAY_OF_WEEK_KEY, "-1").toIntOrNull() ?: -1

    val darkerCalendarColors: Boolean
        get() = getBoolean(DARKER_CALENDAR_COLORS_KEY, false)

    val shouldKeepLogs: Boolean
        get() = getBoolean(KEEP_APP_LOGS_KEY, false)

    val enableCalendarRescan: Boolean
        get() = getBoolean(ENABLE_CALENDAR_RESCAN_KEY, true)

    val notifyOnEmailOnlyEvents: Boolean
        get() = getBoolean(NOTIFY_ON_EMAIL_ONLY_EVENTS_KEY, false)

    val useBundledNotifications: Boolean
        get() = getBoolean(ENABLE_BUNDLED_NOTIFICATIONS_KEY, true)

    val notificationSettingsSnapshot: NotificationSettingsSnapshot
        get() = NotificationSettingsSnapshot(
                showDismissButton = showDismissButton,
                notificationSwipeDoesSnooze = notificationSwipeDoesSnooze,
                ringtoneUri = ringtoneURI,
                vibrationOn = vibraOn,
                vibrationPattern = vibrationPattern,
                ledNotificationOn = ledNotificationOn,
                ledColor = ledColor,
                ledPattern = ledPattern,
                headsUpNotification = headsUpNotification,
                forwardEventToPebble = forwardEventToPebble,
                pebbleOldFirmware = pebbleOldFirmware,
                forwardReminderToPebble = forwardReminderToPebble,
                showColorInNotification = showColorInNotification,
                notificationOpensSnooze = notificationOpensSnooze,
                quietHoursMuteLED = quietHoursMuteLED,
                useAlarmStream = notificationUseAlarmStream
        )

    companion object {

        // Preferences keys

        private const val USE_COMPACT_LAYOUT_KEY = "compact_layout"

        private const val DISMISS_ENABLED_KEY = "pref_key_enable_dismiss_button"

        private const val NOTIFICATION_SWIPE_DOES_SNOOZE_KEY = "pref_key_enable_swipe_to_snooze"

        private const val RINGTONE_KEY = "pref_key_ringtone"
        private const val VIBRATION_ENABLED_KEY = "vibra_on"
        const val VIBRATION_PATTERN_KEY = "pref_vibration_pattern"
        private const val LED_ENABLED_KEY = "notification_led"
        private const val LED_COLOR_KEY = "notification_led_color"
        private const val LED_PATTERN_KEY = "notification_led_v2pattern"

        private const val NOTIFICATION_OPENS_SNOOZE_KEY = "notification_opens_snooze"
        private const val NOTIFICATION_AUTO_DISMISS_KEY = "notification_auto_dismiss"
        private const val NOTIFICATION_AUTO_DISMISS_DEBUG_KEY = "auto_dismiss_debug"
        private const val NOTIFICATION_ALARM_DELAYS_DEBUG_KEY = "alarm_delays_debug"

        private const val FORWARD_TO_PEBBLE_KEY = "forward_to_pebble"
        private const val PEBBLE_TEXT_IN_TITLE_KEY = "pebble_text_in_title"
        private const val HEADS_UP_NOTIFICATINO_KEY = "heads_up_notification"
        private const val NOTIFICATION_WAKE_SCREEN_KEY = "notification_wake_screen"
        private const val NOTIFICATION_TTS_KEY = "notification_tts"
        private const val NOTIFICATION_CALENDAR_COLOR_KEY = "notification_cal_color"

        private const val NOTIFICATION_MAX_NOTIFICATIONS_KEY = "max_notifications_before_collapse"
        private const val NOTIFICATION_COLLAPSE_EVERYTHING_KEY = "max_notifications_collapse_everything"

        private const val SNOOZE_PRESET_KEY = "pref_snooze_presets"
        private const val SHOW_CUSTOM_SNOOZE_TIMES_KEY = "show_custom_snooze_and_until"

        private const val VIEW_AFTER_EDIT_KEY = "show_event_after_reschedule"

        private const val ENABLE_REMINDERS_KEY = "enable_reminding_key"
        private const val REMIND_INTERVAL_KEY = "remind_interval_key2"
        private const val MAX_REMINDERS_KEY = "reminder_max_reminders"

        private const val PEBBLE_FORWARD_REMINDERS_KEY = "pebble_forward_reminders"

        private const val REMINDERS_CUSTOM_RINGTONE_KEY = "reminders_custom_ringtone"
        private const val REMINDERS_CUSTOM_VIBRATION_KEY = "reminders_custom_vibration"
        private const val REMINDERS_RINGTONE_KEY = "reminder_pref_key_ringtone"
        private const val REMINDERS_VIBRATION_ENABLED_KEY = "reminder_vibra_on"
        private const val REMINDERS_VIBRATION_PATTERN_KEY = "reminder_pref_vibration_pattern"


        private const val ENABLE_QUIET_HOURS_KEY = "enable_quiet_hours"
        private const val QUIET_HOURS_FROM_KEY = "quiet_hours_from"
        private const val QUIET_HOURS_TO_KEY = "quiet_hours_to"
        private const val QUIET_HOURS_MUTE_PRIMARY_KEY = "quiet_hours_mute_primary"
        private const val QUIET_HOURS_ONE_TIME_REMINDER_ENABLED_KEY = "quiet_hours_one_time_reminder"
        private const val QUIET_HOURS_MUTE_LED = "quiet_hours_mute_led"

        private const val HALO_LIGHT_DATE_PICKER_KEY = "halo_light_date"
        private const val HALO_LIGHT_TIMER_PICKER_KEY = "halo_light_time"

        private const val CALENDAR_IS_HANDLED_KEY_PREFIX = "calendar_handled_"

        private const val VERSION_CODE_FIRST_INSTALLED_KEY = "first_installed_ver"

        private const val SHOW_NEW_STYLE_MSG_KEY = "show_new_style_message"

        private const val HIDE_MARSHMALLOW_WARNING_KEY = "hide_m_doze_warning"
        private const val HIDE_MARSHMALLOW_WARNING_IN_SETTINGS_KEY = "hide_sttng_m_doze_warning"

        private const val BEHAVIOR_USE_SET_ALARM_CLOCK_KEY = "use_set_alarm_clock"
        private const val BEHAVIOR_USE_SET_ALARM_CLOCK_FOR_FAILBACK_KEY = "use_set_alarm_clock_for_failback"

        const val KEEP_HISTORY_KEY = "keep_history"
        private const val KEEP_HISTORY_DAYS_KEY = "keep_history_days"

        private const val SHOULD_REMIND_FOR_EVENTS_WITH_NO_REMINDERS_KEY = "remind_events_no_rmdnrs"
        private const val DEFAULT_REMINDER_TIME_FOR_EVENTS_WITH_NO_REMINDER_KEY = "default_rminder_time"
        private const val DEFAULT_REMINDER_TIME_FOR_ALL_DAY_EVENTS_WITH_NO_REMINDER = "default_all_day_rminder_time"

        private const val CALENDAR_MANUAL_WATCH_RELOAD_WINDOW_KEY = "manual_watch_reload_window"

        private const val DONT_SHOW_DECLINED_EVENTS_KEY = "dont_show_declined_events"
        private const val DONT_SHOW_CANCELLED_EVENTS_KEY = "dont_show_cancelled_events"
        private const val DONT_SHOW_ALL_DAY_EVENTS_KEY = "dont_show_all_day_events"

        private const val ENABLE_MONITOR_DEBUGGING_KEY = "enableMonitorDebug"

        private const val FIRST_DAY_OF_WEEK_KEY = "first_day_of_week"

        private const val USE_ALARM_STREAM_FOR_NOTIFICATION_KEY = "use_alarm_stream_for_notification"

        private const val DARKER_CALENDAR_COLORS_KEY = "darker_calendar_colors"

        private const val KEEP_APP_LOGS_KEY = "keep_logs"

        private const val ENABLE_CALENDAR_RESCAN_KEY = "enable_manual_calendar_rescan"
        private const val NOTIFY_ON_EMAIL_ONLY_EVENTS_KEY = "notify_on_email_only_events"

        private const val ENABLE_BUNDLED_NOTIFICATIONS_KEY = "pref_enable_bundled_notifications"

        // Default values
        internal const val DEFAULT_SNOOZE_PRESET = "15m, 1h, 4h, 1d, -5m"
        internal const val DEFAULT_REMINDER_INTERVAL = 10
        internal const val DEFAULT_MAX_REMINDERS = "0"
    }
}
