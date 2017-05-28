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


package com.github.quarck.calnotify.reminders

import android.annotation.SuppressLint
import android.content.Context
import com.github.quarck.calnotify.utils.PersistentStorageBase

class ReminderState(ctx: Context) : PersistentStorageBase(ctx, PREFS_NAME) {

    var reminderLastFireTime by LongProperty(0, REMINDER_LAST_FIRE_TIME_KEY)

    var numRemindersFired by IntProperty(0, NUM_REMINDERS_FIRED_KEY)

    var quietHoursOneTimeReminderEnabled by BooleanProperty(false, QUIET_HOURS_ONE_TIME_REMINDER_KEY)

    var nextFireExpectedAt by LongProperty(0, NEXT_FIRE_EXPECTED_AT_KEY)


    @SuppressLint("CommitPrefEdits")
    fun onReminderFired(currentTime: Long) {

        val quietHoursOneTime = quietHoursOneTimeReminderEnabled
        val numReminders = numRemindersFired

        val editor = edit()

        if (quietHoursOneTime)
            editor.putBoolean(QUIET_HOURS_ONE_TIME_REMINDER_KEY, false)
        else
            editor.putInt(NUM_REMINDERS_FIRED_KEY, numReminders + 1)

        editor.putLong(REMINDER_LAST_FIRE_TIME_KEY, currentTime)

        editor.commit()
    }


    companion object {
        const val PREFS_NAME: String = "reminder_state"

        const val REMINDER_LAST_FIRE_TIME_KEY = "A"
        const val NUM_REMINDERS_FIRED_KEY = "B"
        const val QUIET_HOURS_ONE_TIME_REMINDER_KEY = "C"
        const val NEXT_FIRE_EXPECTED_AT_KEY = "D"
    }
}

