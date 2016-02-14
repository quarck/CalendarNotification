package com.github.quarck.calnotify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BroadcastReceiverCalendarChanged: BroadcastReceiver()
{
	override fun onReceive(context: Context?, intent: Intent?)
	{
		logger.debug("onReceive: ${intent?.toUri(Intent.URI_INTENT_SCHEME) ?: ""}")

		EventsManager.onCalendarChanged(context, intent)
	}

	companion object
	{
		private val logger = Logger("BroadcastReceiverCalendarChanged");
	}
}