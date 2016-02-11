package com.github.quarck.calnotify

import android.content.Context
import java.text.DateFormat
import java.util.*

data class EventRecord(
	val eventId: Long,
	var notificationId: Int,
	var title: String,
	var description: String,
	var startTime: Long,
	var endTime: Long,
	var location: String,
	var lastEventUpdate: Long,
	var snoozedUntil: Long = 0,
	var isDisplayed: Boolean = false
)


fun EventRecord.formatText(ctx: Context): String
{
	var sb = StringBuilder()

	if (this.startTime != 0L)
	{
		var today = Date(System.currentTimeMillis())
		var start = Date(this.startTime)

		var dateFormatter = DateFormat.getDateInstance(DateFormat.SHORT)
		var timeFormatter = DateFormat.getTimeInstance(DateFormat.SHORT)

		if (today.day != start.day
			&& today.month != start.month
			&& today.year != start.year)
		{
			sb.append(dateFormatter.format(start));
			sb.append(" ");
		}

		sb.append(timeFormatter.format(start));

		if (this.endTime != 0L)
		{
			sb.append(" - ");

			var end = Date(this.endTime)

			if (end.day != start.day
				&& end.month != start.month
				&& end.year != start.year)
			{
				sb.append(dateFormatter.format(end))
				sb.append(" ");
			}

			sb.append(timeFormatter.format(end))
		}
	}

	if (this.location != "")
	{
		sb.append("\nLocation: ")
		sb.append(this.location)
	}

	return sb.toString()
}
