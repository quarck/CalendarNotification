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

import android.os.Bundle
import android.app.Activity
import android.content.ContentUris
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.provider.CalendarContract
import android.view.View
import android.widget.RelativeLayout
import android.widget.TextView

class ActivitySnooze : Activity()
{
	var eventId: Long = -1;
	var notificationId: Int = -1;

	var storage: EventsStorage? = null

	override fun onCreate(savedInstanceState: Bundle?)
	{
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_snooze)

		storage = EventsStorage(this);

		notificationId = intent.getIntExtra(Consts.INTENT_NOTIFICATION_ID_KEY, -1)
		eventId = intent.getLongExtra(Consts.INTENT_EVENT_ID_KEY, -1)

		if (storage != null)
		{
			var event = storage!!.getEvent(eventId)

			if (event != null)
			{
				var title =
					if (event.title == "")
						this.resources.getString(R.string.empty_title)
					else
						event.title;

				var (date, time) = event.formatTime(this);

				var location = event.location;

				if (location != "")
				{
					findViewById(R.id.snooze_view_location_layout).visibility = View.VISIBLE;
					(findViewById(R.id.snooze_view_location) as TextView).text = location;
				}

				(this.findViewById(R.id.snooze_view_title) as TextView).text = title;
				(this.findViewById(R.id.snooze_view_event_date) as TextView).text = date;
				(this.findViewById(R.id.snooze_view_event_time) as TextView).text = time;

				var color: Int = event.color.adjustCalendarColor();
				if (color == 0)
					color = resources.getColor(R.color.primary)
				(this.findViewById(R.id.snooze_view_event_details_layout) as RelativeLayout).background = ColorDrawable(color)
			}
		}

	}

	fun onButtonCancelClick(v: View?)
	{
		finish();
	}

	fun OnButtonEventDetailsClick(v: View?)
	{
		var calendarIntentUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId);
		var calendarIntent = Intent(Intent.ACTION_VIEW).setData(calendarIntentUri);
		startActivity(calendarIntent)
	}

	private fun snoozeEvent(presetIdx: Int)
	{
		var snoozeDelay = Settings(this).getSnoozePreset(presetIdx);

		if (notificationId != -1 && eventId != -1L && storage != null)
		{
			logger.debug("Snoozing event id ${eventId}, snoozeDelay=${snoozeDelay/1000L}")

			var event = storage!!.getEvent(eventId)
			if (event != null)
			{
				EventsManager.snoozeEvent(this, event, snoozeDelay, storage);

				finish();
			}
			else
			{
				logger.error("Error: can't get event from DB");
			}
		}
	}

	fun OnButtonSnoozePresent1Click(v: View?) = snoozeEvent(0)// TODO - this is bad
	fun OnButtonSnoozePresent2Click(v: View?) = snoozeEvent(1)
	fun OnButtonSnoozePresent3Click(v: View?) = snoozeEvent(2)
	fun OnButtonSnoozePresent4Click(v: View?) = snoozeEvent(3)

	fun OnButtonRescheduleOneHourClick(v: View?) {}
	fun OnButtonRescheduleNextDayClick(v: View?) {}
	fun OnButtonRescheduleNextWeekClick(v: View?) {}
	fun OnButtonRescheduleCustomClick(v: View?) {}

	companion object
	{
		private val logger = Logger("ActivitySnooze");
	}

}
