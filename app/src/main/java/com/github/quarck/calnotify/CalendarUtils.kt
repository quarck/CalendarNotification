package com.github.quarck.calnotify

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.provider.CalendarContract
import java.util.*

object CalendarUtils
{
	private val logger = Logger("CalendarUtils");

	private val eventFields =
		arrayOf(
			CalendarContract.CalendarAlerts.EVENT_ID,
			CalendarContract.CalendarAlerts.STATE,
			CalendarContract.CalendarAlerts.TITLE,
			CalendarContract.CalendarAlerts.DESCRIPTION,
			CalendarContract.CalendarAlerts.DTSTART,
			CalendarContract.CalendarAlerts.DTEND,
			CalendarContract.CalendarAlerts.EVENT_LOCATION,
			CalendarContract.CalendarAlerts.DISPLAY_COLOR
		)

	private fun cursorToEventRecord(cursor: Cursor, alertTime: Long): Pair<Int, EventRecord>
	{
		var eventId = cursor.getLong(0)
		var state = cursor.getInt(1)
		var title = cursor.getString(2)
		var desc = cursor.getString(3)
		var start = cursor.getLong(4)
		var end = cursor.getLong(5)
		var location = cursor.getString(6)
		var color = cursor.getInt(7)

		var event =
			EventRecord(
				eventId = eventId,
				notificationId = 0,
				alertTime = alertTime,
				title = title,
				description = desc,
				startTime = start,
				endTime = end,
				location = location,
				lastEventUpdate = System.currentTimeMillis(),
				isDisplayed = false,
				color = color
			);

		return Pair(state, event)
	}

	fun getFiredEventsDetails(context: Context, alertTime: String): List<EventRecord>
	{
		var ret = arrayListOf<EventRecord>()

		var selection = CalendarContract.CalendarAlerts.ALARM_TIME + "=?";

		var cursor: Cursor? =
			context.contentResolver.query(
				CalendarContract.CalendarAlerts.CONTENT_URI_BY_INSTANCE,
				eventFields,
				selection,
				arrayOf(alertTime),
				null
			);

		if (cursor != null && cursor.moveToFirst())
		{
			do
			{
				var (state, event) = cursorToEventRecord(cursor, alertTime.toLong());

				logger.info("Received event details: ${event.eventId}, st ${state}, from ${event.startTime} to ${event.endTime}")

				if (state != CalendarContract.CalendarAlerts.STATE_DISMISSED)
				{
					ret.add(event)
				}
				else
				{
					logger.info("Ignored dismissed event ${event.eventId}")
				}

			} while (cursor.moveToNext())
		}
		else
		{
			logger.error("Failed to parse event - no events at $alertTime")
		}

		cursor?.close()

		return ret
	}

	fun dismissNativeEventReminder(context: Context, eventId: Long)
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

			logger.debug("dismissNativeEventReminder: eventId $eventId");
		}
		catch (ex: Exception)
		{
			logger.debug("dismissNativeReminder failed")
		}
	}

	fun getEvent(context: Context, eventId: Long, alertTime: Long): EventRecord?
	{
		var ret: EventRecord? = null

		var selection = CalendarContract.CalendarAlerts.ALARM_TIME + "=?";

		var cursor: Cursor? =
			context.contentResolver.query(
				CalendarContract.CalendarAlerts.CONTENT_URI_BY_INSTANCE,
				eventFields,
				selection,
				arrayOf(alertTime.toString()),
				null
			);

		if (cursor != null && cursor.moveToFirst())
		{
			do
			{
				var (state, event) = cursorToEventRecord(cursor, alertTime);

				if (event.eventId == eventId)
				{
					ret = event;
					break;
				}

			} while (cursor.moveToNext())
		}
		else
		{
			logger.error("Event $eventId not found")
		}

		cursor?.close()

		return ret
	}
}