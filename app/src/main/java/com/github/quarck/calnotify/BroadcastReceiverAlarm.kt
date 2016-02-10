package com.github.quarck.calnotify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BroadcastReceiverAlarm : BroadcastReceiver()
{
	override fun onReceive(context: Context?, intent: Intent?)
	{
		if (context != null)
		{
			postEventNotifications(context)
			scheduleEventsAlarm(context)
		}
		else
		{
			Logger.error("AlarmReceiver", "context is null");
		}

	}
}

