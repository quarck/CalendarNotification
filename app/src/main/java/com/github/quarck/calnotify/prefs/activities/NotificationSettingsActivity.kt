// //
// //   Calendar Notifications Plus
// //   Copyright (C) 2016 Sergey Parshin (s.parshin.sc@gmail.com)
// //
// //   This program is free software; you can redistribute it and/or modify
// //   it under the terms of the GNU General Public License as published by
// //   the Free Software Foundation; either version 3 of the License, or
// //   (at your option) any later version.
// //
// //   This program is distributed in the hope that it will be useful,
// //   but WITHOUT ANY WARRANTY; without even the implied warranty of
// //   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// //   GNU General Public License for more details.
// //
// //   You should have received a copy of the GNU General Public License
// //   along with this program; if not, write to the Free Software Foundation,
// //   Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301  USA
// //
// 
// package com.github.quarck.calnotify.prefs.activities
// 
// import android.os.Build
// import android.os.Bundle
// import android.support.v7.app.AppCompatActivity
// import com.github.quarck.calnotify.R
// import com.github.quarck.calnotify.Settings
// import com.github.quarck.calnotify.prefs.MaxRemindersPreference
// import com.github.quarck.calnotify.prefs.ReminderPatternPreference
// import com.github.quarck.calnotify.prefs.preferences
// 
// class NotificationSettingsActivity : AppCompatActivity(){
// 
//     lateinit var settings: Settings
// 
//     override fun onCreate(savedInstanceState: Bundle?) {
//         super.onCreate(savedInstanceState);
// 
//         settings = Settings(this)
// 
//         preferences(this) {
// 
//             header(R.string.main_notifications)
// 
//             red_notice(R.string.pre_oreo_notification_channels_explanation_v2)
// 
// 
//             header(R.string.reminder_notifications)
// 
//             switch(R.string.enable_reminders, R.string.enable_reminders_summary) {
// 
//                 initial(settings.remindersEnabled)
// 
//                 onChange{settings.remindersEnabled = it}
// 
//                 depending {
// 
//                     item(R.string.remind_interval) {
//                         ReminderPatternPreference(this@NotificationSettingsActivity, settings,
//                                 this@NotificationSettingsActivity.layoutInflater).create().show()
//                     }
// 
//                     item(R.string.max_reminders) {
//                         MaxRemindersPreference(this@NotificationSettingsActivity, settings,
//                                 this@NotificationSettingsActivity.layoutInflater).create().show()
//                     }
//                 }
//             }
// 
//             header(R.string.notification_behavior)
// 
//             notificationBehavior(settings.notificationSwipeBehavior) {
//                 settings.notificationSwipeBehavior = it
//             }
// 
//             switch(R.string.show_snooze_button,
//                     R.string.show_snooze_button_desc) {
//                 initial(settings.showSnoozeButton)
//                 onChange{ settings.showSnoozeButton = it }
//             }
// 
//             separator()
// 
//             switch (R.string.group_notifications, R.string.group_notifications_summary) {
//                 initial (settings.postGroupNotification)
//                 onChange { settings.postGroupNotification = it }
//                 depending {
// 
//                     groupNotificationBehavior(settings.groupNotificationSwipeBehavior) {
//                         settings.groupNotificationSwipeBehavior = it
//                     }
//                     separator()
//                 }
//             }
// 
//             header(R.string.other)
// 
//             switch(R.string.add_empty_action_to_the_end_title,
//                     R.string.add_empty_action_to_the_end_summary) {
// 
//                 initial(settings.notificationAddEmptyAction)
//                 onChange{ settings.notificationAddEmptyAction = it }
//             }
// 
//             switch(R.string.always_collapse, R.string.always_collapse_detail) {
//                 initial(settings.notificationsAlwaysCollapsed)
//                 onChange{settings.notificationsAlwaysCollapsed = it }
//             }
// 
//             if (settings.allowMuteAndAlarm) {
//                 switch(R.string.use_alarm_stream, R.string.use_alarm_stream_summary) {
//                     initial(settings.notificationUseAlarmStream)
//                     onChange { settings.notificationUseAlarmStream = it }
//                 }
//             }
//         }
//     }
// }
