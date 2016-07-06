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
import android.content.SharedPreferences
import android.media.RingtoneManager
import android.net.Uri
import android.preference.PreferenceManager
import com.github.quarck.calnotify.prefs.PreferenceUtils
import com.github.quarck.calnotify.utils.toIntOrNull

fun SharedPreferences?.setBoolean(key: String, value: Boolean) {
    if (this != null) {
        val editor = this.edit()
        editor.putBoolean(key, value)
        editor.commit()
    }
}

fun SharedPreferences?.setLong(key: String, value: Long) {
    if (this != null) {
        val editor = this.edit()
        editor.putLong(key, value)
        editor.commit()
    }
}

data class NotificationSettingsSnapshot
(
        val showDismissButton: Boolean,
        val ringtoneUri: Uri?,
        val vibraOn: Boolean,
        val vibrationPattern: LongArray,
        val ledNotificationOn: Boolean,
        val ledColor: Int,
        val ledPattern: IntArray,
        val headsUpNotification: Boolean,
        val forwardToPebble: Boolean,
        val pebbleOldFirmware: Boolean
)

class Settings(ctx: Context) {
    private var context: Context
    private var prefs: SharedPreferences

    init {
        context = ctx
        prefs = PreferenceManager.getDefaultSharedPreferences(context)
    }

    var removeOriginal: Boolean
        get() = prefs.getBoolean(REMOVE_ORIGINAL_KEY, true)
        set(value) = prefs.setBoolean(REMOVE_ORIGINAL_KEY, value)

    var lastCustomSnoozeIntervalMillis: Long
        get() = prefs.getLong(LAST_CUSTOM_INTERVAL_KEY, Consts.HOUR_IN_SECONDS * 1000L)
        set(value) = prefs.setLong(LAST_CUSTOM_INTERVAL_KEY, value)

    val showDismissButton: Boolean
        get() = prefs.getBoolean(DISMISS_ENABLED_KEY, true)

    val vibraOn: Boolean
        get() = prefs.getBoolean(VIBRATION_ENABLED_KEY, true)

    val vibrationPattern: LongArray
        get() {
            val idx = prefs.getString(VIBRATION_PATTERN_KEY, "0").toInt()

            val patterns = Consts.VIBRATION_PATTERNS
            return if (idx < patterns.size && idx >= 0) patterns[idx] else patterns[0]
        }

    val ledNotificationOn: Boolean
        get() = prefs.getBoolean(LED_ENABLED_KEY, true)

    val ledColor: Int
        get() = prefs.getInt(LED_COLOR_KEY, Consts.DEFAULT_LED_COLOR)

    val ledPattern: IntArray
        get() =
            prefs.getString(LED_PATTERN_KEY, Consts.DEFAULT_LED_PATTERN)
                .split(",")
                .map { it.toInt() }
                .toIntArray()

    val forwardToPebble: Boolean
        get() = prefs.getBoolean(FORWARD_TO_PEBBLE_KEY, false)

    val pebbleOldFirmware: Boolean
        get() = prefs.getBoolean(PEBBLE_TEXT_IN_TITLE_KEY, false)

    val headsUpNotification: Boolean
        get() = prefs.getBoolean(HEADS_UP_NOTIFICATINO_KEY, true)

    val notificationWakeScreen: Boolean
        get() = prefs.getBoolean(NOTIFICATION_WAKE_SCREEN_KEY, false)

    val viewAfterEdit: Boolean
        get() = prefs.getBoolean(VIEW_AFTER_EDIT_KEY, true)

    var debugTransactionLogEnabled: Boolean
        get() = prefs.getBoolean(DEBUG_LOG_KEY, false)
        set(value) = prefs.setBoolean(DEBUG_LOG_KEY, value)

    val abortBroadcast: Boolean
        get() = prefs.getBoolean(ABORT_BROADCAST_KEY, false)

    val snoozePresets: LongArray
        get() {
            var ret = PreferenceUtils.parseSnoozePresets(prefs.getString(SNOOZE_PRESET_KEY, DEFAULT_SNOOZE_PRESET))

            if (ret == null)
                ret = PreferenceUtils.parseSnoozePresets(DEFAULT_SNOOZE_PRESET)

            if (ret == null)
                ret = Consts.DEFAULT_SNOOZE_PRESETS

            return ret;
        }

    val showCustomSnoozeAndUntil: Boolean
        get() = prefs.getBoolean(SHOW_CUSTOM_SNOOZE_TIMES_KEY, true)//

    val ringtoneURI: Uri?
        get() {
            var notification: Uri? = null

            try {
                val uriValue = prefs.getString(RINGTONE_KEY, "")

                if (uriValue != null && !uriValue.isEmpty())
                    notification = Uri.parse(uriValue)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            if (notification == null)
                notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            return notification
        }

    val maxNotifications: Int
        get() = prefs.getInt(NOTIFICATION_MAX_NOTIFICATIONS_KEY, Consts.MAX_NOTIFICATIONS)

    val collapseEverything: Boolean
        get() = prefs.getBoolean(NOTIFICATION_COLLAPSE_EVERYTHING_KEY, false)

    val remindersEnabled: Boolean
        get() = prefs.getBoolean(ENABLE_REMINDERS_KEY, false)

    val remindersIntervalMillis: Long
        get() = prefs.getInt(REMIND_INTERVAL_KEY, DEFAULT_REMINDER_INTERVAL) * 60L * 1000L;

    val maxNumberOfReminders: Int
        get() = prefs.getString(MAX_REMINDERS_KEY, DEFAULT_MAX_REMINDERS).toIntOrNull() ?: 0

    val quietHoursEnabled: Boolean
        get() = prefs.getBoolean(ENABLE_QUIET_HOURS_KEY, false)

    val quietHoursFrom: Pair<Int, Int>
        get() = PreferenceUtils.unpackTime( prefs.getInt(QUIET_HOURS_FROM_KEY, 0) )

    val quietHoursTo: Pair<Int, Int>
        get() = PreferenceUtils.unpackTime( prefs.getInt(QUIET_HOURS_TO_KEY, 0) )

    val quietHoursMutePrimary: Boolean
        get() = prefs.getBoolean(QUIET_HOURS_MUTE_PRIMARY_KEY, false)

    var quietHoursOneTimeReminderEnabled: Boolean
        get() = prefs.getBoolean(QUIET_HOURS_ONE_TIME_REMINDER_ENABLED_KEY, false)
        set(value) = prefs.setBoolean(QUIET_HOURS_ONE_TIME_REMINDER_ENABLED_KEY, value)

    fun getCalendarIsHandled(calendarId: Long) =
        prefs.getBoolean("$CALENDAR_IS_HANDLED_KEY_PREFIX.$calendarId", true)

    fun setCalendarIsHandled(calendarId: Long, enabled: Boolean) =
        prefs.setBoolean("$CALENDAR_IS_HANDLED_KEY_PREFIX.$calendarId", enabled)

    var useCompactView: Boolean
        get() = prefs.getBoolean(USE_COMPACT_LAYOUT_KEY, true)
        set(value) = prefs.setBoolean(USE_COMPACT_LAYOUT_KEY, value)

    val haloLightDatePicker: Boolean
        get() = prefs.getBoolean(HALO_LIGHT_DATE_PICKER_KEY, false)

    val haloLightTimePicker: Boolean
        get() = prefs.getBoolean(HALO_LIGHT_TIMER_PICKER_KEY, false)

    var powerOptimisationWarningShown: Boolean
        get() = prefs.getBoolean(POWER_OPTIMISATION_WARNING_SHOWN_KEY, false)
        set(value) = prefs.setBoolean(POWER_OPTIMISATION_WARNING_SHOWN_KEY, value)

    var versionCodeFirstInstalled: Long
        get() = prefs.getLong(VERSION_CODE_FIRST_INSTALLED_KEY, 0L)
        set(value) = prefs.setLong(VERSION_CODE_FIRST_INSTALLED_KEY, value)

    var showNewStyleMessage: Boolean
        get() = prefs.getBoolean(SHOW_NEW_STYLE_MSG_KEY, true)
        set(value) = prefs.setBoolean(SHOW_NEW_STYLE_MSG_KEY, value)

    var dontShowMarshmallowWarning: Boolean
        get() = prefs.getBoolean(HIDE_MARSHMALLOW_WARNING_KEY, false)
        set(value) = prefs.setBoolean(HIDE_MARSHMALLOW_WARNING_KEY, value)

    var dontShowMarshmallowWarningInSettings: Boolean
        get() = prefs.getBoolean(HIDE_MARSHMALLOW_WARNING_IN_SETTINGS_KEY, false)
        set(value) = prefs.setBoolean(HIDE_MARSHMALLOW_WARNING_IN_SETTINGS_KEY, value)


    val notificationSettingsSnapshot: NotificationSettingsSnapshot
        get() = NotificationSettingsSnapshot(
            showDismissButton = showDismissButton,
            ringtoneUri = ringtoneURI,
            vibraOn = vibraOn,
            vibrationPattern = vibrationPattern,
            ledNotificationOn = ledNotificationOn,
            ledColor = ledColor,
            ledPattern = ledPattern,
            headsUpNotification = headsUpNotification,
            forwardToPebble = forwardToPebble,
            pebbleOldFirmware = pebbleOldFirmware
        )

    companion object {

        // Preferences keys

        private const val USE_COMPACT_LAYOUT_KEY = "compact_layout"

        private const val REMOVE_ORIGINAL_KEY = "remove_original"
        private const val DISMISS_ENABLED_KEY = "pref_key_enable_dismiss_button"

        private const val RINGTONE_KEY = "pref_key_ringtone"
        private const val VIBRATION_ENABLED_KEY = "vibra_on"
        const val VIBRATION_PATTERN_KEY = "pref_vibration_pattern"
        private const val LED_ENABLED_KEY = "notification_led"
        private const val LED_COLOR_KEY = "notification_led_color"
        private const val LED_PATTERN_KEY = "notification_led_v2pattern"

        private const val FORWARD_TO_PEBBLE_KEY = "forward_to_pebble"
        private const val PEBBLE_TEXT_IN_TITLE_KEY = "pebble_text_in_title"
        private const val HEADS_UP_NOTIFICATINO_KEY = "heads_up_notification"
        private const val NOTIFICATION_WAKE_SCREEN_KEY = "notification_wake_screen"

        private const val NOTIFICATION_MAX_NOTIFICATIONS_KEY = "max_notifications_before_collapse"
        private const val NOTIFICATION_COLLAPSE_EVERYTHING_KEY = "max_notifications_collapse_everything"

        private const val SNOOZE_PRESET_KEY = "pref_snooze_presets"
        private const val SHOW_CUSTOM_SNOOZE_TIMES_KEY = "show_custom_snooze_and_until"

        private const val VIEW_AFTER_EDIT_KEY = "show_event_after_reschedule"

        private const val DEBUG_LOG_KEY = "debugLog"
        private const val ABORT_BROADCAST_KEY = "abort_broadcast"

        private const val ENABLE_REMINDERS_KEY = "enable_reminding_key"
        private const val REMIND_INTERVAL_KEY = "remind_interval_key2"
        private const val MAX_REMINDERS_KEY = "reminder_max_reminders"

        private const val ENABLE_QUIET_HOURS_KEY = "enable_quiet_hours"
        private const val QUIET_HOURS_FROM_KEY = "quiet_hours_from"
        private const val QUIET_HOURS_TO_KEY = "quiet_hours_to"
        private const val QUIET_HOURS_MUTE_PRIMARY_KEY = "quiet_hours_mute_primary"
        private const val QUIET_HOURS_ONE_TIME_REMINDER_ENABLED_KEY = "quiet_hours_one_time_reminder"

        private const val HALO_LIGHT_DATE_PICKER_KEY = "halo_light_date"
        private const val HALO_LIGHT_TIMER_PICKER_KEY = "halo_light_time"

        private const val LAST_CUSTOM_INTERVAL_KEY = "last_custom_snooze_interval"

        private const val CALENDAR_IS_HANDLED_KEY_PREFIX = "calendar_handled_"

        private const val POWER_OPTIMISATION_WARNING_SHOWN_KEY = "power_warning1_shown"

        private const val VERSION_CODE_FIRST_INSTALLED_KEY = "first_installed_ver"

        private const val SHOW_NEW_STYLE_MSG_KEY = "show_new_style_message"

        private const val HIDE_MARSHMALLOW_WARNING_KEY = "hide_m_doze_warning"
        private const val HIDE_MARSHMALLOW_WARNING_IN_SETTINGS_KEY = "hide_sttng_m_doze_warning"

        // Default values
        internal const val DEFAULT_SNOOZE_PRESET = "15m, 1h, 4h, 1d, -5m"
        internal const val DEFAULT_REMINDER_INTERVAL = 10
        internal const val DEFAULT_MAX_REMINDERS = "0"
    }
}
