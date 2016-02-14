package com.github.quarck.calnotify

import android.os.Bundle
import android.app.Activity
import android.content.ContentUris
import android.content.Intent
import android.provider.CalendarContract
import android.view.View
import android.widget.TextView

class ActivitySnooze : Activity()
{
	var eventId: Long = -1;
	var notificationId: Int = -1;

	var storage: EventsStorage? = null
	var notificationsManager = EventNotificationManager()

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
				var currentTime = System.currentTimeMillis()

				event.snoozedUntil = currentTime + snoozeDelay;
				event.lastEventUpdate = currentTime;
				storage!!.updateEvent(event);

				AlarmUtils.scheduleNextAlarmForEvents(this);

				notificationsManager.onEventSnoozed(this, eventId, notificationId);

				logger.debug("alarm set -  called for ${event}, for ${(event.snoozedUntil-currentTime)/1000} seconds from now");

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
