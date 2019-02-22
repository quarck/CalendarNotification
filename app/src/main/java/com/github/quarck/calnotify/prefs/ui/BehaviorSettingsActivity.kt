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

package com.github.quarck.calnotify.prefs.ui

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.prefs.components.DefaultManualAllDayNotificationPreference
import com.github.quarck.calnotify.prefs.components.DefaultManualNotificationPreference
import com.github.quarck.calnotify.prefs.preferences

class BehaviorSettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val settings = Settings(this)

        preferences(this) {
            header(R.string.calendar_handling_options)

            switch(R.string.manual_event_rescan_title, R.string.manual_event_rescan_summary) {
                initial(settings.enableCalendarRescan)
                onChange{settings.enableCalendarRescan = it}

                depending {
                    switch(R.string.handle_email_only_events_title, R.string.handle_email_only_events_summary) {
                        initial(settings.notifyOnEmailOnlyEvents)
                        onChange{settings.notifyOnEmailOnlyEvents = it}
                    }

                    switch(R.string.handle_events_with_no_reminders, R.string.handle_events_with_no_reminders_summary) {
                        initial(settings.shouldRemindForEventsWithNoReminders)
                        onChange{settings.shouldRemindForEventsWithNoReminders = it}

                        depending {

                            item(R.string.default_reminder_time, R.string.default_reminder_time_summary_short) {
                                DefaultManualNotificationPreference(
                                        this@BehaviorSettingsActivity,
                                        layoutInflater,
                                        settings.defaultReminderTimeForEventWithNoReminderMinutes,
                                        { settings.defaultReminderTimeForEventWithNoReminderMinutes = it }
                                ).create().show()
                            }

                            item(R.string.default_all_day_reminder_time, R.string.default_all_day_reminder_time_summary_short) {
                                DefaultManualAllDayNotificationPreference(
                                        this@BehaviorSettingsActivity,
                                        layoutInflater,
                                        settings.defaultReminderTimeForAllDayEventWithNoreminderMinutes,
                                        { settings.defaultReminderTimeForAllDayEventWithNoreminderMinutes = it }
                                ).create().show()
                            }
                        }
                    }
                }
            }

            switch (R.string.ignore_declined_events, R.string.ignore_declined_events_summary) {
                initial(settings.dontShowDeclinedEvents)
                onChange{settings.dontShowDeclinedEvents = it}
            }

            switch (R.string.ignore_cancelled_events, R.string.ignore_cancelled_events_summary) {
                initial(settings.dontShowCancelledEvents)
                onChange{settings.dontShowCancelledEvents = it}
            }

            switch (R.string.ignore_all_day_events, R.string.ignore_all_day_events_summary) {
                initial(settings.dontShowAllDayEvents)
                onChange{settings.dontShowAllDayEvents = it}
            }
        }
    }
}
