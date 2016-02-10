package com.github.quarck.calnotify

import android.app.IntentService
import android.content.Intent
import android.widget.Toast
import java.util.*

/**
 * Created by quarck on 19/01/16.
 */
class ServiceNotificationActionHandler : IntentService("ServiceNotificationActionHandler")
{
	override fun onHandleIntent(intent: Intent?)
	{
		Logger.debug(TAG, "onHandleIntent")

		if (intent != null)
		{
			var notificationId = intent.getIntExtra(Consts.INTENT_NOTIFICATION_ID_KEY, -1)
			var eventId = intent.getLongExtra(Consts.INTENT_EVENT_ID_KEY, -1)
			var type = intent.getStringExtra(Consts.INTENT_TYPE)

			var db = EventsStorage(this)
			var mgr = NotificationViewManager()

			if (notificationId != -1 && eventId != -1L && type != null)
			{
				if (type == Consts.INTENT_TYPE_DELETE || type == Consts.INTENT_TYPE_DISMISS)
				{
					Logger.debug("Removing event id ${eventId} from DB, intent type =${type} and dismissing notification id ${notificationId}")
					db.deleteEvent(eventId)
					mgr.removeNotification(this, eventId, notificationId)
				}
				else if (type == Consts.INTENT_TYPE_SNOOZE)
				{
					Logger.debug("Snoozing event id ${eventId}, intent type =${type}")

					var event = db.getEvent(eventId)
					if (event != null)
					{
						var currentTime = System.currentTimeMillis()

						event.snoozedUntil = currentTime + Consts.SNOOZE_DELAY;
						db.updateEvent(event);

						scheduleEventsAlarm(this);

						mgr.removeNotification(this, eventId, notificationId);

						Toast.makeText(this, "Snoozed until ${Date(event.snoozedUntil).toLocaleString()}", 3);

						Logger.debug("alarm set -  called for ${event}, for ${(event.snoozedUntil-currentTime)/1000} seconds from now");
					}
					else
					{
						Toast.makeText(this, "Error: can't get event from DB", 3);
					}
				}
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
