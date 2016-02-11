package com.github.quarck.calnotify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BroadcastReceiverAlarm : BroadcastReceiver()
{
	override fun onReceive(context: Context?, intent: Intent?)
	{
		Logger.debug("BroadcastReceiverAlarm", "onReceive");

		if (context != null)
		{
			EventNotificationManager().postEventNotifications(context, true);
			scheduleNextAlarmForEvents(context);
		}
		else
		{
			Logger.error("BroadcastReceiverAlarm", "context is null");
		}

	}
}

