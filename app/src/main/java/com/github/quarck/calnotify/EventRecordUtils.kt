package com.github.quarck.calnotify

import android.content.Context
import java.text.DateFormat
import java.util.*

object EventRecordUtils
{
	val oneDay = 24L*3600L*1000L;

	fun dayName(ctx: Context, time: Long, currentTime: Long, formatter: DateFormat): String
	{
		var ret: String = "";

		var currentDay: Long = currentTime / oneDay;
		var day: Long = time / oneDay;

		if (currentDay == day)
		{
			ret = ctx.resources.getString(R.string.today);
		}
		else if (day == currentDay + 1L)
		{
			ret = ctx.resources.getString(R.string.tomorrow);
		}
		else
		{
			ret = formatter.format(Date(time));
		}

		return ret;
	}
}




fun EventRecord.formatText(ctx: Context): String
{
	var sb = StringBuilder()

	if (this.startTime != 0L)
	{
		var currentTime = System.currentTimeMillis();

		var today = Date(currentTime)
		var start = Date(this.startTime)
		var end = Date(this.endTime)

		val oneDay = EventRecordUtils.oneDay;

		var currentDay: Long = currentTime / oneDay;
		var startDay: Long = this.startTime / oneDay;
		var endDay: Long = this.endTime / oneDay;


		var dateFormatter = DateFormat.getDateInstance(DateFormat.SHORT)
		var timeFormatter = DateFormat.getTimeInstance(DateFormat.SHORT)

		if (currentDay != startDay)
		{
			if (startDay == endDay)
			{
				sb.append(EventRecordUtils.dayName(ctx, startTime, currentTime, dateFormatter));
			}
			else
			{
				sb.append(dateFormatter.format(start));
			}
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

		val oneDay = EventRecordUtils.oneDay;
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

		sbDay.append(
			EventRecordUtils.dayName(
				ctx,
				startTime, currentTime,
				DateFormat.getDateInstance(DateFormat.FULL)
			)
		);
	}

	return Pair(sbDay.toString(), sbTime.toString());
}

fun EventRecord.formatSnoozedUntil(ctx: Context): String
{
	var sb = StringBuilder();

	if (snoozedUntil != 0L)
	{
		var currentTime = System.currentTimeMillis();

		val oneDay = EventRecordUtils.oneDay;

		var currentDay: Long = currentTime / oneDay;
		var snoozedDay: Long = this.snoozedUntil / oneDay;

		var timeFormatter = DateFormat.getTimeInstance(DateFormat.SHORT)

		if (snoozedDay != currentDay)
		{
			sb.append(
				EventRecordUtils.dayName(
					ctx,
					snoozedUntil, currentTime,
					DateFormat.getDateInstance(DateFormat.SHORT)
				)
			);
			sb.append(" ");
		}

		sb.append(timeFormatter.format(Date(snoozedUntil)));
	}

	return sb.toString();
}

