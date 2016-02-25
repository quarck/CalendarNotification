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

object Consts
{
	const val NOTIFICATION_ID_COLLAPSED = 0;
	const val NOTIFICATION_ID_DYNAMIC_FROM = 1;

	//
	const val INTENT_NOTIFICATION_ID_KEY = "notificationId";
	const val INTENT_EVENT_ID_KEY = "eventId";

	const val INTENT_IS_USER_ACTION = "causedByUser"

	const val MAX_NOTIFICATIONS = 8;

	//
	const val VIBRATION_DURATION : Long = 1200;
	const val LED_DURATION_ON = 300;
	const val LED_DURATION_OFF = 2000;
	const val LED_COLOR = 0x7f0000ff;

	const val ALARM_THRESHOULD = 3*1000L;

	val SNOOZE_PRESETS = longArrayOf(15*60*1000, 60*60*1000, 4*60*60*1000, 24*60*60*1000);
}
