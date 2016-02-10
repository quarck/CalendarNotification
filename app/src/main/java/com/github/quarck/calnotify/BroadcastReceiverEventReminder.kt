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
	private val TAG = "BroadcastReceiverEventReminder"

	override fun onReceive(context: Context?, intent: Intent?)
	{
		if (context == null || intent == null)
			return;

		var shouldAbortBroadcast = false;

		if (intent.action.equals(CalendarContract.ACTION_EVENT_REMINDER, true))
		{
			var settings = Settings(context)

			Logger.debug(TAG, "EVENT_REMINDER received, ${intent.data}");

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
							CalendarContract.CalendarAlerts.EVENT_LOCATION
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

					Logger.debug(TAG,
						"Event Details: ${eventId}, st ${state}, title ${title}, desc ${desc}, from ${start} to ${end}, loc ${location}")

					if (state != CalendarContract.CalendarAlerts.STATE_DISMISSED)
					{
						var notification =
							EventsStorage(context).addEvent(
								eventId = eventId,
								title = title,
								description = desc,
								startTime = start,
								endTime = end,
								location = location
							);

						NotificationViewManager().postNotification(
							context,
							notification,
							settings.notificationSettingsSnapshot
						);

						if (settings.removeOriginal)
						{
							dismissNativeReminder(context, eventId);
							shouldAbortBroadcast = true;
						}
					}
					else
					{
						Logger.debug(TAG, "Ignored dismissed event ${eventId}")
					}

				} while (cursor.moveToNext())
			}
			else
			{
				Logger.error(TAG, "Failed to parse event")
			}

			cursor?.close()
		}
		else
		{
			Logger.debug(TAG, "Unsupported action ${intent.action}")
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
			Logger.debug(TAG, "dismissNativeReminder failed")
		}
	}
}
