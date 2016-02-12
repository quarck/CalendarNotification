package com.github.quarck.calnotify

import android.content.Context
import java.text.DateFormat
import java.util.*


fun EventRecord.formatText(ctx: Context): String
{
	var sb = StringBuilder()

	if (this.startTime != 0L)
	{
		var currentTime = System.currentTimeMillis();

		var today = Date(currentTime)
		var start = Date(this.startTime)
		var end = Date(this.endTime)

		val oneDay = 24L*3600L*1000L;

		var currentDay: Long = currentTime / oneDay;
		var startDay: Long = this.startTime / oneDay;
		var endDay: Long = this.endTime / oneDay;


		var dateFormatter = DateFormat.getDateInstance(DateFormat.SHORT)
		var timeFormatter = DateFormat.getTimeInstance(DateFormat.SHORT)

		if (currentDay != startDay)
		{
			sb.append(dateFormatter.format(start));
			sb.append(" ");
		}

		sb.append(timeFormatter.format(start));

		if (endDay != 0L)
		{
			sb.append(" - ");

			if (endDay != startDay)
			{
				sb.append(dateFormatter.format(end))
				sb.append(" ");
			}

			sb.append(timeFormatter.format(end))
		}
	}

	if (this.location != "")
	{
		sb.append("\n")
		sb.append(ctx.resources.getString(R.string.location));
		sb.append(this.location)
	}

	return sb.toString()
}

fun EventRecord.formatTime(ctx: Context): Pair<String, String>
{
	var sbDay = StringBuilder()
	var sbTime = StringBuilder();

	if (this.startTime != 0L)
	{
		var currentTime = System.currentTimeMillis();

		val oneDay = 24L*3600L*1000L;
		var currentDay: Long = currentTime / oneDay;
		var startDay: Long = this.startTime / oneDay;
		var endDay: Long = this.endTime / oneDay;

		var timeFormatter = DateFormat.getTimeInstance(DateFormat.SHORT)

		sbTime.append(timeFormatter.format(Date(this.startTime)));

		if (endDay != 0L && endDay == startDay)
		{
			sbTime.append(" - ");
			sbTime.append(timeFormatter.format(Date(this.endTime)))
		}

		if (currentDay == startDay)
		{
			sbDay.append(ctx.resources.getString(R.string.today));
		}
		else if (startDay == currentDay + 1L)
		{
			sbDay.append(ctx.resources.getString(R.string.tomorrow));
		}
		else
		{
			var dateFormatter = DateFormat.getDateInstance(DateFormat.FULL)
			sbDay.append(dateFormatter.format(Date(this.startTime)));
		}
	}

	return Pair(sbDay.toString(), sbTime.toString());
}
