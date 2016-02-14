package com.github.quarck.calnotify

import android.content.Context
import java.text.DateFormat
import java.util.*

data class EventRecord(
	val eventId: Long,
	val alertTime: Long,
	var notificationId: Int,
	var title: String,
	var description: String,
	var startTime: Long,
	var endTime: Long,
	var location: String,
	var lastEventUpdate: Long,
	var snoozedUntil: Long = 0,
	var isDisplayed: Boolean = false,
	var color: Int = 0
)
{
	fun updateFrom(newEvent: EventRecord): Boolean
	{
		var ret = false

		if (title != newEvent.title)
		{
			title = newEvent.title
			ret = true
		}

		if (description != newEvent.description)
		{
			description = newEvent.description
			ret = true
		}

		if (startTime != newEvent.startTime)
		{
			startTime = newEvent.startTime
			ret = true
		}

		if (endTime != newEvent.endTime)
		{
			endTime = newEvent.endTime
			ret = true
		}

		if (location != newEvent.location)
		{
			location = newEvent.location
			ret = true
		}

		if (color != newEvent.color)
		{
			color = newEvent.color
			ret = true
		}

		if (ret)
			lastEventUpdate = System.currentTimeMillis()

		return ret
	}
}
