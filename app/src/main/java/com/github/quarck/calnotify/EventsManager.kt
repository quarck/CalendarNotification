package com.github.quarck.calnotify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

class EventsManager
{
	companion object
	{
		private val notificationManager: IEventNotificationManager = EventNotificationManager()

		private val logger = Logger("EventsManager");

		private fun scheduleNextAlarmForEvents(context: Context)
		{
			logger.debug("scheduleEventAlarm called");

			var nextAlarm =
				EventsStorage(context)
					.events
					.filter { it.snoozedUntil != 0L }
					.map { it.snoozedUntil }
					.min();

			if (nextAlarm != null)
			{
			}

			var intent = Intent(context, BroadcastReceiverAlarm::class.java);
			var pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)

			var alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager;

			if (nextAlarm != null)
			{
				logger.info("Next alarm at ${nextAlarm}, in ${(nextAlarm - System.currentTimeMillis()) / 1000L} seconds");

				alarmManager.set(AlarmManager.RTC_WAKEUP, nextAlarm, pendingIntent);
			}
			else
			{
				logger.info("Cancelling alarms");

				alarmManager.cancel(pendingIntent)
			}
		}

		fun onAlarm(context: Context?, intent: Intent?)
		{
			if (context != null)
			{
				notificationManager.postEventNotifications(context, false);
				scheduleNextAlarmForEvents(context);
			}
			else
			{
				logger.error("onAlarm: context is null");
			}
		}

		fun onAppUpdated(context: Context?, intent: Intent?)
		{
			if (context != null)
			{
				notificationManager.postEventNotifications(context, true);
				scheduleNextAlarmForEvents(context);

				ServiceUINotifier.notifyUI(context, false);
			}
		}

		fun onBootComplete(context: Context?, intent: Intent?)
		{
			if (context != null)
			{
				notificationManager.postEventNotifications(context, true);
				scheduleNextAlarmForEvents(context);

				ServiceUINotifier.notifyUI(context, false);
			}
		}

		fun onCalendarChanged(context: Context?, intent: Intent?)
		{
			ServiceUINotifier.notifyUI(context, false);
			// TODO - implement me
		}

		fun onCalendarEventFired(context: Context, intent: Intent, event: EventRecord)
		{
			EventsStorage(context).addEvent(event);

			notificationManager.onEventAdded(context, event)

			ServiceUINotifier.notifyUI(context, false);
		}

		fun snoozeEvent(context: Context, event: EventRecord, snoozeDelay: Long, eventsStorage: EventsStorage?)
		{
			var storage = eventsStorage ?: EventsStorage(context)

			var currentTime = System.currentTimeMillis()

			event.snoozedUntil = currentTime + snoozeDelay;
			event.lastEventUpdate = currentTime;
			storage.updateEvent(event);

			scheduleNextAlarmForEvents(context);

			notificationManager.onEventSnoozed(context, event.eventId, event.notificationId);

			logger.debug("alarm set -  called for ${event.eventId}, for ${(event.snoozedUntil-currentTime)/1000} seconds from now");
		}

		fun onAppStarted(context: Context?)
		{
			if (context != null)
			{
				notificationManager.postEventNotifications(context, true)
				scheduleNextAlarmForEvents(context)
			}
		}

		fun dismissEvent(context: Context?, event: EventRecord)
		{
			if (context != null)
			{
				logger.debug("Removing[1] event id ${event.eventId} from DB, and dismissing notification id ${event.notificationId}")

				var db = EventsStorage(context);
				db.deleteEvent(event.eventId);

				notificationManager.onEventDismissed(context, event.eventId, event.notificationId);

				scheduleNextAlarmForEvents(context);
			}
		}

		fun dismissEvent(context: Context?, eventId: Long, notificationId: Int, notifyActivity: Boolean = true)
		{
			if (context != null)
			{
				logger.debug("Removing event id ${eventId} from DB, and dismissing notification id ${notificationId}")

				EventsStorage(context).deleteEvent(eventId);

				notificationManager.onEventDismissed(context, eventId, notificationId);

				scheduleNextAlarmForEvents(context);

				if (notifyActivity)
					ServiceUINotifier.notifyUI(context, true);
			}
		}
	}
}