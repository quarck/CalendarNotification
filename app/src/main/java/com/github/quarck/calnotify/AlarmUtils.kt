package com.github.quarck.calnotify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

fun scheduleNextAlarmForEvents(context: Context)
{
	Logger.debug("scheduleEventAlarm called");

	var nextAlarm =
		EventsStorage(context)
			.events
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
