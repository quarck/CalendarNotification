package com.github.quarck.calnotify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BroadcastReceiverCalendarChanged: BroadcastReceiver()
{
	override fun onReceive(context: Context?, intent: Intent?)
	{
		Logger.debug("BroadcastReceiverCalendarChanged", "onReceive: ${intent?.toUri(Intent.URI_INTENT_SCHEME) ?: ""}")
	}
}