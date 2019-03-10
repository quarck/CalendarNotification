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

object Consts {

//    object JobIDS {
//        const val CALENDAR_RESCAN = 0
//        const val CALENDAR_RESCAN_ONCE = 1
//    }
//
//    const val FILE_PROVIDER_ID = BuildConfig.APPLICATION_ID + ".fileprovider"

    const val DATA_UPDATED_BROADCAST = BuildConfig.APPLICATION_ID + ".dataupdated"

    const val DAY_IN_MILLISECONDS = 24L * 3600L * 1000L
    const val DAY_IN_SECONDS: Long = 3600L * 24
    const val DAY_IN_MINUTES: Int = 60*24
    const val HOUR_IN_SECONDS: Long = 3600L
    const val HOUR_IN_MILLISECONDS: Long = 3600L * 1000L
    const val MINUTE_IN_SECONDS: Long = 60L
    const val MINUTE_IN_MILLISECONDS: Long = 60L * 1000L

    const val DEFAULT_SNOOZE_TIME = 5L * 60L * 1000L

    const val NOTIFICATION_ID_COLLAPSED = 0
    const val NOTIFICATION_ID_DYNAMIC_FROM = 1

    const val REMINDER_ALARM_TIMEOUT = 15000L
    const val CALENDAR_RESCAN_TIMEOUT = 180L * 1000L // 3 mins

    const val DEFAULT_SNOOZE_TIME_IF_NONE = 15L * 60L * 1000L

    const val MAX_NOTIFICATION_IDS = Int.MAX_VALUE -1


    const val NOTIFICATION_ID_REMINDER = MAX_NOTIFICATION_IDS - 1
    const val NOTIFICATION_ID_BUNDLED_GROUP = MAX_NOTIFICATION_IDS - 2
    const val NOTIFICATION_ID_DEBUG0_AUTO_DISMISS = MAX_NOTIFICATION_IDS - 10
    const val NOTIFICATION_ID_DEBUG1_ALARM_DELAYS = MAX_NOTIFICATION_IDS - 11
    const val NOTIFICATION_ID_DEBUG2_SNOOZE_ALARM_DELAYS = MAX_NOTIFICATION_IDS - 12
    const val NOTIFICATION_ID_DEBUG3_NEARLY_MISS = MAX_NOTIFICATION_IDS - 13

    //
    const val INTENT_NOTIFICATION_ID_KEY = "notificationId"
    const val INTENT_EVENT_ID_KEY = "eventId"
    const val INTENT_INSTANCE_START_TIME_KEY = "instanceStartTime"
    const val INTENT_SNOOZE_ALL_IS_CHANGE = "snooze_all_is_change"
    const val INTENT_SNOOZE_FROM_MAIN_ACTIVITY = "snooze_by_main_activity"
    const val INTENT_SNOOZE_PRESET = "snooze_preset"
    const val INTENT_SNOOZE_ALL_KEY = "snooze_all"
    const val INTENT_SNOOZE_ALL_COLLAPSED_KEY = "snooze_all_collapsed"
    const val INTENT_DISMISS_ALL_KEY = "dismiss_all"

    const val INTENT_IS_USER_ACTION = "causedByUser"

    const val INTENT_TTS_TEXT = "text"

    const val INTENT_MUTE_ACTION = "muteAction"

    // max number of notifications displayed on the screen at all the times
    const val MAX_NOTIFICATIONS = 16

    const val DEFAULT_NOTIFICATIONS = 8

    //
    private const val VIBRATION_DURATION: Long = 1200

    val VIBRATION_PATTERNS by lazy {
        arrayOf(
                longArrayOf(0, VIBRATION_DURATION), // Single long
                longArrayOf(0, 2 * VIBRATION_DURATION),
                longArrayOf(0, VIBRATION_DURATION / 5, VIBRATION_DURATION / 5, VIBRATION_DURATION / 5, VIBRATION_DURATION / 5, VIBRATION_DURATION / 5),
                longArrayOf(0, VIBRATION_DURATION, VIBRATION_DURATION / 3, VIBRATION_DURATION / 5, VIBRATION_DURATION / 5, VIBRATION_DURATION / 5, VIBRATION_DURATION / 5, VIBRATION_DURATION / 5),
                longArrayOf(0, VIBRATION_DURATION / 5, VIBRATION_DURATION / 5, VIBRATION_DURATION / 5, VIBRATION_DURATION / 5, VIBRATION_DURATION / 5, VIBRATION_DURATION / 3, VIBRATION_DURATION)
        )
    }

    const val WAKE_SCREEN_DURATION = 100L

    const val LED_DURATION_ON = 300
    const val LED_DURATION_OFF = 2000
    const val DEFAULT_LED_PATTERN = "$LED_DURATION_ON,$LED_DURATION_OFF"
    const val LED_MIN_DURATION = 100
    const val LED_MAX_DURATION = 2500
    const val LED_DURATION_GRANULARITY = 100

//    val VIBRATION_PATTERN_DEFAULT = longArrayOf(0, VIBRATION_DURATION) // Single Long
//    val VIBRATION_PATTERN_ALARM = longArrayOf(0, 2 * VIBRATION_DURATION) // Single Very Long
//    val VIBRATION_PATTERN_REMINDER = longArrayOf(0, VIBRATION_DURATION / 5, VIBRATION_DURATION / 5, VIBRATION_DURATION / 5, VIBRATION_DURATION / 5, VIBRATION_DURATION / 5)
//    val VIBRATION_PATTERN_ALARM_REMINDER = longArrayOf(0, VIBRATION_DURATION / 5, VIBRATION_DURATION / 5, VIBRATION_DURATION / 5, VIBRATION_DURATION / 5, VIBRATION_DURATION / 5, VIBRATION_DURATION / 3, VIBRATION_DURATION)
//
//    val VIBRATION_PATTERN_REAL_ALARM = longArrayOf(0,
//            VIBRATION_DURATION / 5, VIBRATION_DURATION / 3,
//            VIBRATION_DURATION / 4, VIBRATION_DURATION / 1,
//            VIBRATION_DURATION / 3, VIBRATION_DURATION / 5,
//            VIBRATION_DURATION / 1, VIBRATION_DURATION / 4,
//            VIBRATION_DURATION / 5, VIBRATION_DURATION / 3,
//            VIBRATION_DURATION / 3, VIBRATION_DURATION / 5,
//            VIBRATION_DURATION / 3, VIBRATION_DURATION / 5,
//            VIBRATION_DURATION / 4, VIBRATION_DURATION / 1,
//            VIBRATION_DURATION / 4, VIBRATION_DURATION / 1,
//            VIBRATION_DURATION / 4, VIBRATION_DURATION / 1,
//            VIBRATION_DURATION / 3, VIBRATION_DURATION / 5,
//            VIBRATION_DURATION / 4, VIBRATION_DURATION / 1)

    const val DEFAULT_LED_COLOR = 0x7f0000ff

    const val ALARM_THRESHOLD = 24 * 1000L // multiple of both, 2 and 3

    const val DISMISS_ALL_THRESHOLD = 3000L

    val DEFAULT_SNOOZE_PRESETS = longArrayOf(15 * 60 * 1000, 60 * 60 * 1000, 4 * 60 * 60 * 1000, 24 * 60 * 60 * 1000)

    const val MAX_SUPPORTED_PRESETS = 6

    const val DEFAULT_CALENDAR_EVENT_COLOR = 0xff0000ff.toInt()

    // Only auto-dismisss event notification if we can confirm that event was moved into the future
    // by at least 1hr
    const val EVENT_MOVED_THRESHOLD = 60 * 60 * 1000L

    const val MIN_FORCE_REPOST_INTERVAL = 60L * 60L * 1000L // minimum once an hour
    const val MARSHMALLOW_MIN_REMINDER_INTERVAL_USEC = 10 * 60 * 1000L


    const val EVENT_MOVE_THRESHOLD = 15 * 60 * 1000L

    const val LOGS_FOLDER = "logs"

    const val ALERTS_DB_REMOVE_AFTER = 3L * 24L * 3600L * 1000L

    const val CALENDAR_RESCAN_INTERVAL = 30L * 60L * 1000L // 30 mins

    const val MAX_NUM_EVENTS_BEFORE_COLLAPSING_EVERYTHING = 50
    //const val MAX_UNCOLLAPSED_NOTIFICATIONS= 12

    const val MAX_DUE_ALERTS_FOR_MANUAL_SCAN = 512
    const val MAX_SCAN_BACKWARD_DAYS = 31L

    const val MIN_INTERVAL_BETWEEN_SOUNDS = 1000L
    const val MIN_INTERVAL_BETWEEN_VIBRATIONS = 1000L

    const val WAS_HANDLED_CACHE_MAX_AGE_MILLIS = (3 * MAX_SCAN_BACKWARD_DAYS + 10) * DAY_IN_MILLISECONDS
    const val WAS_HANDLED_CACHE_CLEANUP_INTERVALS = MAX_SCAN_BACKWARD_DAYS / 2 * DAY_IN_MILLISECONDS

    const val FAILBACK_SHORT_SNOOZE = 60000L
    const val LOG_CLEANUP_INTERVAL: Long = 3600L * 24L * 3L * 1000L // every 3 days

    const val MAX_USER_ACTION_DELAY: Long = 3500L

    const val NEW_EVENT_DEFAULT_NEW_EVENT_REMINDER: Long = 15*60000L
    const val NEW_EVENT_DEFAULT_ALL_DAY_REMINDER: Long = 6*60*60000L // 18:00 on the day before
    const val NEW_EVENT_DEFAULT_ADD_HOURS: Int = 4
    const val NEW_EVENT_MAX_ALL_DAY_REMINDER_DAYS_BEFORE: Int = 28
    const val NEW_EVENT_MAX_REMINDER_MILLISECONDS_BEFORE: Long = 28L * DAY_IN_MILLISECONDS
    const val NEW_EVENT_MONITOR_KEEP_DAYS: Long = 3
    const val NEW_EVENT_MIN_STATUS_STEP_MILLIS: Long = 1*60*1000L
    const val NEW_EVENT_MIN_MONITOR_RETRY_MILLISECONDS: Long = 10*60L*1000L // 10 minutes
    const val NEW_EVENT_MONITOR_MAX_RETRIES: Int = 10
    const val MIN_REMINDER_INTERVAL_SECONDS: Int = 15

    const val DEFAULT_NEW_EVENT_DURATION_MINUTES: Int = 30

    const val BIN_KEEP_HISTORY_DAYS = 3L
    const val BIN_KEEP_HISTORY_MILLISECONDS = BIN_KEEP_HISTORY_DAYS * DAY_IN_MILLISECONDS

}
