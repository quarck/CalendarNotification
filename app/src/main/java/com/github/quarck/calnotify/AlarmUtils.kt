package com.github.quarck.calnotify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

object AlarmUtils
{
	fun scheduleNextAlarmForEvents(context: Context)
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

	private val logger = Logger("AlarmUtils");
}