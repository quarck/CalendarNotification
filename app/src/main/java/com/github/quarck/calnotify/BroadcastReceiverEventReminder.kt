package com.github.quarck.calnotify

import android.content.*
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract

class BroadcastReceiverEventReminder : BroadcastReceiver()
{
	override fun onReceive(context: Context?, intent: Intent?)
	{
		if (context == null || intent == null)
			return;

		var shouldAbortBroadcast = false;

		var removeOriginal = Settings(context).removeOriginal

		logger.debug("Event reminder received, ${intent.data}, ${intent.action}");

		var uri = intent.data;

		var alertTime: String? = uri.lastPathSegment;

		if (alertTime != null)
		{
			var events = CalendarUtils.getFiredEventsDetails(context, alertTime)

			for (event in events)
			{
				EventsManager.onCalendarEventFired(context, event);

				if (removeOriginal)
				{
					CalendarUtils.dismissNativeEventReminder(context, event.eventId);
					shouldAbortBroadcast = true;
				}
			}
		}

		if (shouldAbortBroadcast)
		{
			abortBroadcast();
		}
	}
	companion object
	{
		private val logger = Logger("BroadcastReceiverEventReminder");
	}
}
