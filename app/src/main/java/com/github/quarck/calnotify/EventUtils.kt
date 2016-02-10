package com.github.quarck.calnotify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract

fun scheduleEventsAlarm(context: Context)
{
	Logger.debug("scheduleEventAlarm called");

	var events = EventsStorage(context).events;


	var nextAlarm =
		events
			.filter { it.snoozedUntil != 0L }
			.map { it.snoozedUntil }
			.min();

	if (nextAlarm != null)
	{
		Logger.debug("Next alarm at ${nextAlarm}, in ${(nextAlarm-System.currentTimeMillis())/1000L} seconds");

		var intent = Intent(context, BroadcastReceiverAlarm::class.java);

		var alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager;

		alarmManager.set(
			AlarmManager.RTC_WAKEUP,
			nextAlarm,
			PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
		);
	}
}

fun postEventNotifications(context: Context)
{
	var db = EventsStorage(context)
	var mgr = NotificationViewManager()
	var settings = Settings(context)

	var currentTime = System.currentTimeMillis()

	for (event in db.events)
	{
		if (event.snoozedUntil == 0L)
		{
			// Event was already "beeped", need to make sure it is shown, quietly...
			mgr.postNotification(
				context,
				event,
				NotificationSettingsSnapshot(
					settings.showDismissButton,
					null,
					false,
					settings.ledNotificationOn,
					false
				)
			)
		}
		else
		{
			if (event.snoozedUntil < currentTime + Consts.ALARM_THRESHOULD)
			{
				// it is time to show this event after a snooze or whatever,
				// turn on all the bells and whistles
				mgr.postNotification(
					context,
					event,
					settings.notificationSettingsSnapshot
				)

				event.snoozedUntil = 0; // Since it is displayed now -- it is no longer snoozed
				db.updateEvent(event);
			}
			else
			{
				// this will be covered by 'scheduleEventsAlarm' call
			}
		}
	}
}
