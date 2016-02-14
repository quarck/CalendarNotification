package com.github.quarck.calnotify

import android.content.*
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract

/**
 * Created by quarck on 07/02/16.
 */

class BroadcastReceiverEventReminder : BroadcastReceiver()
{
	override fun onReceive(context: Context?, intent: Intent?)
	{
		if (context == null || intent == null)
			return;

		var shouldAbortBroadcast = false;

		if (intent.action.equals(CalendarContract.ACTION_EVENT_REMINDER, true))
		{
			var settings = Settings(context)

			logger.debug("EVENT_REMINDER received, ${intent.data}");

			var uri = intent.data;

			var alertTime = uri.lastPathSegment;

			var selection = CalendarContract.CalendarAlerts.ALARM_TIME + "=?";

			var cursor =
				context.contentResolver.query(
					CalendarContract.CalendarAlerts.CONTENT_URI_BY_INSTANCE,
					arrayOf(CalendarContract.CalendarAlerts.EVENT_ID,
							CalendarContract.CalendarAlerts.STATE,
							CalendarContract.CalendarAlerts.TITLE,
							CalendarContract.CalendarAlerts.DESCRIPTION,
							CalendarContract.CalendarAlerts.DTSTART,
							CalendarContract.CalendarAlerts.DTEND,
							CalendarContract.CalendarAlerts.EVENT_LOCATION,
							CalendarContract.CalendarAlerts.DISPLAY_COLOR
						),
					selection,
					arrayOf(alertTime),
					null
				);

			if (cursor != null && cursor.moveToFirst())
			{
				do
				{
					var eventId = cursor.getLong(0)
					var state = cursor.getInt(1)
					var title = cursor.getString(2)
					var desc = cursor.getString(3)
					var start = cursor.getLong(4)
					var end = cursor.getLong(5)
					var location = cursor.getString(6)
					var color = cursor.getInt(7)

					logger.info("Received event details: ${eventId}, st ${state}, from ${start} to ${end}")

					if (state != CalendarContract.CalendarAlerts.STATE_DISMISSED)
					{
						var event =
							EventRecord(
								eventId = eventId,
								notificationId = 0,
								title = title,
								description = desc,
								startTime = start,
								endTime = end,
								location = location,
								lastEventUpdate = System.currentTimeMillis(),
								isDisplayed = false,
								color = color
							);

						EventsManager.onCalendarEventFired(context, intent, event);

						if (settings.removeOriginal)
						{
							dismissNativeReminder(context, eventId);
							shouldAbortBroadcast = true;
						}
					}
					else
					{
						logger.info("Ignored dismissed event $eventId")
					}

				} while (cursor.moveToNext())
			}
			else
			{
				logger.error("Failed to parse event")
			}

			cursor?.close()
		}
		else
		{
			logger.debug("Unsupported action ${intent.action}")
		}

		if (shouldAbortBroadcast)
		{
			abortBroadcast();
		}
	}

	fun dismissNativeReminder(context: Context, eventId: Long)
	{
		try
		{
			var uri = CalendarContract.CalendarAlerts.CONTENT_URI;

			var selection =
				"(" +
					"${CalendarContract.CalendarAlerts.STATE}=${CalendarContract.CalendarAlerts.STATE_FIRED}" +
					" OR " +
					"${CalendarContract.CalendarAlerts.STATE}=${CalendarContract.CalendarAlerts.STATE_SCHEDULED}" +
				")" +
				" AND ${CalendarContract.CalendarAlerts.EVENT_ID}=$eventId";

			var dismissValues = ContentValues();
			dismissValues.put(
				CalendarContract.CalendarAlerts.STATE,
				CalendarContract.CalendarAlerts.STATE_DISMISSED
			);

			context.contentResolver.update(uri, dismissValues, selection, null);
		}
		catch (ex: Exception)
		{
			logger.debug("dismissNativeReminder failed")
		}
	}

	companion object
	{
		private val logger = Logger("BroadcastReceiverEventReminder");
	}
}
