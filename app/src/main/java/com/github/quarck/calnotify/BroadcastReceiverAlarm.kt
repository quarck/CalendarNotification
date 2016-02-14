package com.github.quarck.calnotify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BroadcastReceiverAlarm : BroadcastReceiver()
{
	override fun onReceive(context: Context?, intent: Intent?)
	{
		logger.debug("onReceive");

		if (context != null)
		{
			EventNotificationManager().postEventNotifications(context, false);
			AlarmUtils.scheduleNextAlarmForEvents(context);

			ServiceUINotifier.notifyUI(context, false);
		}
		else
		{
			logger.error("context is null");
		}
	}

	companion object
	{
		private val logger = Logger("BroadcastReceiverAlarm");
	}
}

