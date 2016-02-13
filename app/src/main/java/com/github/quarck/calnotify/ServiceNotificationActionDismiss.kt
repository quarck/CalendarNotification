package com.github.quarck.calnotify

import android.app.IntentService
import android.content.Intent
import android.widget.Toast
import java.util.*

class ServiceNotificationActionDismiss : IntentService("ServiceNotificationActionDismiss")
{
	override fun onHandleIntent(intent: Intent?)
	{
		Logger.debug(TAG, "onHandleIntent")

		if (intent != null)
		{
			var notificationId = intent.getIntExtra(Consts.INTENT_NOTIFICATION_ID_KEY, -1)
			var eventId = intent.getLongExtra(Consts.INTENT_EVENT_ID_KEY, -1)

			if (notificationId != -1 && eventId != -1L)
			{
				Logger.debug("Removing event id ${eventId} from DB, and dismissing notification id ${notificationId}")

				EventsStorage(this).deleteEvent(eventId);
				EventNotificationManager().onEventDismissed(this, eventId, notificationId);

				scheduleNextAlarmForEvents(this);
			}
			else
			{
				Logger.error(TAG, "notificationId=${notificationId}, eventId=${eventId}, or type is null")
			}
		}
		else
		{
			Logger.error(TAG, "Intent is null!")
		}
	}

	companion object
	{
		val TAG = "DiscardNotificationService"
	}
}