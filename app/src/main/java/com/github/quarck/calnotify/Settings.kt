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

fun SharedPreferences?.setBoolean(key: String, value: Boolean)
{
	if (this != null)
	{
		val editor = this.edit()
		editor.putBoolean(key, value)
		editor.commit()
	}
}

data class NotificationSettingsSnapshot
(
	val showDismissButton: Boolean,
	val ringtoneUri: Uri?,
	val vibraOn: Boolean,
	val ledNotificationOn: Boolean,
	val forwardToPebble: Boolean
)

class Settings(ctx: Context)
{
	private var context: Context
	private var prefs: SharedPreferences

	var removeOriginal: Boolean
		get() = prefs.getBoolean(REMOVE_ORIGINAL_KEY, true)
		set(value) = prefs.setBoolean(REMOVE_ORIGINAL_KEY, value)

	var showDismissButton: Boolean
		get() = prefs.getBoolean(DISMISS_ENABLED_KEY, true)
		set(value) = prefs.setBoolean(DISMISS_ENABLED_KEY, value)

	var vibraOn: Boolean
		get() = prefs.getBoolean(VIBRATION_ENABLED_KEY, true)
		set(value) = prefs.setBoolean(VIBRATION_ENABLED_KEY, value)

	var ledNotificationOn: Boolean
		get() = prefs.getBoolean(LED_ENABLED_KEY, true)
		set(value) = prefs.setBoolean(LED_ENABLED_KEY, value)

	var forwardToPebble: Boolean
		get() = prefs.getBoolean(FORWARD_TO_PEBBLE_KEY, false)
		set(value) = prefs.setBoolean(FORWARD_TO_PEBBLE_KEY, value)

	var debugTransactionLogEnabled: Boolean
		get() = prefs.getBoolean(DEBUG_LOG_KEY, false)
		set(value) = prefs.setBoolean(DEBUG_LOG_KEY, value)

	fun getSnoozePreset(idx: Int): Long
	{
		// Currently hard-coded to use consts instead of dynamic params

		if (idx >= 0 && idx < Consts.SNOOZE_PRESETS.size)
			return Consts.SNOOZE_PRESETS[idx];

		return Consts.SNOOZE_PRESETS[0];
	}

	val ringtoneURI: Uri?
		get()
		{
			var notification: Uri? = null

			try
			{
				val uriValue = prefs.getString(RINGTONE_KEY, "")

				if (uriValue != null && !uriValue.isEmpty())
					notification = Uri.parse(uriValue)
			}
			catch (e: Exception)
			{
				e.printStackTrace()
			}

			if (notification == null)
				notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

			return notification
		}

	val notificationSettingsSnapshot: NotificationSettingsSnapshot
		get() = NotificationSettingsSnapshot(showDismissButton, ringtoneURI, vibraOn, ledNotificationOn, forwardToPebble)

	init
	{
		context = ctx
		prefs = PreferenceManager.getDefaultSharedPreferences(context)
	}

	companion object
	{
		private const val REMOVE_ORIGINAL_KEY = "remove_original"
		private const val DISMISS_ENABLED_KEY = "pref_key_enable_dismiss_button"
		private const val RINGTONE_KEY = "pref_key_ringtone"
		private const val VIBRATION_ENABLED_KEY = "vibra_on"
		private const val LED_ENABLED_KEY = "notification_led"
		private const val FORWARD_TO_PEBBLE_KEY = "forward_to_pebble"
		private const val DEBUG_LOG_KEY = "debugLog"
	}
}
