package com.github.quarck.calnotify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent


class BroadcastReceiverCalendarChanged: BroadcastReceiver()
{
	override fun onReceive(context: Context?, intent: Intent?)
	{
		Logger.debug(TAG, "onReceive: ${intent?.toUri(Intent.URI_INTENT_SCHEME) ?: ""}")
	}

	companion object
	{
		val TAG = "BroadcastReceiverCalendarChanged"
	}
}