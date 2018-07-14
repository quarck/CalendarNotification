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

package com.github.quarck.calnotify.prefs.activities

import android.os.Bundle
import android.preference.PreferenceActivity
import android.preference.PreferenceFragment
import android.support.v7.app.AppCompatActivity
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.prefs.preferences

class MiscSettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val settings = Settings(this)

        preferences(this) {

            val daysOfWeek: Array<String> = context.resources.getStringArray(R.array.days_of_week_entries)
            val daysOfWeekValues: Array<Int> = context.resources.getIntArray(R.array.days_of_week_values).toTypedArray()

            list(
                    R.string.first_day_of_week,
                    R.string.first_day_of_week_summary,
                    daysOfWeek,
                    daysOfWeekValues,
                    settings.firstDayOfWeek,
                    true
            ) {
                name, value ->
                settings.firstDayOfWeek = value
            }

            switch(R.string.use_set_alarm_clock_title, R.string.use_set_alarm_clock_summary) {
                initial(settings.useSetAlarmClock)
                onChange{settings.useSetAlarmClock = it}
            }

            switch(R.string.use_set_alarm_clock_for_events, R.string.use_set_alarm_clock_for_events_summary) {
                initial(settings.useSetAlarmClockForFailbackEventPaths)
                onChange{settings.useSetAlarmClockForFailbackEventPaths = it}
            }

            switch(R.string.keep_logs_title, R.string.keep_logs_summary) {
                initial(settings.shouldKeepLogs)
                onChange{settings.shouldKeepLogs = it}
            }

            header(R.string.other)

            switch(R.string.pebble_forward_reminders, R.string.pebble_forward_reminders_summary) {
                initial(settings.forwardReminersToPebble)
                onChange{settings.forwardReminersToPebble = it}
            }
        }
    }
}
