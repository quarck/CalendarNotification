package com.github.quarck.calnotify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BroadcastReceiverAlarm : BroadcastReceiver()
{
	override fun onReceive(context: Context?, intent: Intent?)
	{
		logger.debug("onReceive");
		EventsManager.onAlarm(context, intent);
	}

	companion object
	{
		private val logger = Logger("BroadcastReceiverAlarm");
	}
}

