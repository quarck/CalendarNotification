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
