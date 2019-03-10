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
// import android.os.Bundle
// import android.support.v7.app.AppCompatActivity
// import com.github.quarck.calnotify.R
// import com.github.quarck.calnotify.Settings
// import com.github.quarck.calnotify.prefs.preferences
// 
// class QuietHoursSettingsActivity : AppCompatActivity() {
// 
//     lateinit var settings: Settings
// 
//     override fun onCreate(savedInstanceState: Bundle?) {
//         super.onCreate(savedInstanceState);
// 
//         settings = Settings(this)
// 
//         preferences(this) {
//             switch(R.string.enable_quiet_time, R.string.quiet_hours_summary) {
//                 initial(settings.quietHoursEnabled)
//                 onChange { settings.quietHoursEnabled = it }
// 
//                 depending {
// 
//                     if (!settings.allowMuteAndAlarm) {
//                         red_notice(R.string.group_notifications_android_oreo_gore)
//                     }
// 
//                     timeOfDay(
//                             R.string.quiet_time_from,
//                             settings.quietHoursFrom) {
//                         settings.quietHoursFrom = it
//                     }
// 
//                     timeOfDay(
//                             R.string.quiet_time_to,
//                             settings.quietHoursTo) {
//                         settings.quietHoursTo = it
//                     }
//                 }
//             }
//         }
//     }
// }
