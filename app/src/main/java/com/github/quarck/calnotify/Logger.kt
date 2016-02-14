package com.github.quarck.calnotify

import android.util.Log

class Logger(val tag: String)
{
	private val TAG_PREFIX = "CalendarNotification:"

	fun debug(message: String)
	{
		Log.d(TAG_PREFIX + tag, "" + System.currentTimeMillis() + " " + message)
	}

	fun info(message: String)
	{
		Log.i(TAG_PREFIX + tag, "" + System.currentTimeMillis() + " " + message)
	}

	fun error(message: String)
	{
		Log.e(TAG_PREFIX + tag, "" + System.currentTimeMillis() + " " + message)
	}
}
