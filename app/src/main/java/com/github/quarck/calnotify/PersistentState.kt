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

fun SharedPreferences?.setBoolean(key: String, value: Boolean) {
    if (this != null) {
        val editor = this.edit()
        editor.putBoolean(key, value)
        editor.apply()
    }
}

fun SharedPreferences?.setInt(key: String, value: Int) {
    if (this != null) {
        val editor = this.edit()
        editor.putInt(key, value)
        editor.apply()
    }
}

fun SharedPreferences?.setLong(key: String, value: Long) {
    if (this != null) {
        val editor = this.edit()
        editor.putLong(key, value)
        editor.apply()
    }
}


class PersistentState(private val ctx: Context) {

	private var state: SharedPreferences

	init {
		state = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
	}

	var reminderLastFireTime: Long
        get() = state.getLong(REMINDER_LAST_FIRE_TIME_KEY, 0)
        set(value) = state.setLong(REMINDER_LAST_FIRE_TIME_KEY, value)

	var notificationLastFireTime: Long
        get() = state.getLong(NOTIFICATION_LAST_FIRE_TIME_KEY, 0)
        set(value) = state.setLong(NOTIFICATION_LAST_FIRE_TIME_KEY, value)

    var numRemindersFired: Int
        get() = state.getInt(NUM_REMINDERS_FIRED_KEY, 0)
        set(value) = state.setInt(NUM_REMINDERS_FIRED_KEY, value)

    fun updateNotificationLastFiredTime() {
        state?.let {
            val editor = it.edit()
            editor.putLong(NOTIFICATION_LAST_FIRE_TIME_KEY, System.currentTimeMillis())
            editor.putInt(NUM_REMINDERS_FIRED_KEY, 0)
            editor.apply()
        }
    }

	var lastNotificationRePost: Long
        get() = state.getLong(LAST_NOTIFICATION_RE_POST_KEY, 0)
        set(value) = state.setLong(LAST_NOTIFICATION_RE_POST_KEY, value)

    var lastTimerBroadcastReceived: Long
        get() = state.getLong(LAST_TIMER_BROADCAST_RECEIVED_KEY, 0)
        set(value) = state.setLong(LAST_TIMER_BROADCAST_RECEIVED_KEY, value)

    val sinceLastTimerBroadcast: Long
		get() = System.currentTimeMillis() - lastTimerBroadcastReceived

	companion object {
		const val PREFS_NAME: String = "PersistentState"

        const val REMINDER_LAST_FIRE_TIME_KEY = "reminderLastFireTime"
        const val NOTIFICATION_LAST_FIRE_TIME_KEY = "notificationLastFireTime"
        const val NUM_REMINDERS_FIRED_KEY = "numRemindersFired"
        const val LAST_NOTIFICATION_RE_POST_KEY = "lastNotificationRePost"
        const val LAST_TIMER_BROADCAST_RECEIVED_KEY = "lastTimerBroadcastReceived"
    }
}

val Context.persistentState: PersistentState
	get() =  PersistentState(this)

