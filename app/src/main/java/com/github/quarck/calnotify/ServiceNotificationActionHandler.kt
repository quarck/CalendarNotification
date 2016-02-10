package com.github.quarck.calnotify

import android.app.AlarmManager
import android.app.IntentService
import android.app.PendingIntent
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract

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
					Logger.debug("Implement me....")
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

	fun scheduleEvent(eventId: Long, eventStart: Long, eventEnd: Long)
	{
		var PROJECTION = arrayOf(CalendarContract.CalendarAlerts.STATE);

		// Dismiss current alarm
		var uri = CalendarContract.CalendarAlerts.CONTENT_URI;

		// Add a new alarm
		var alarmTime = System.currentTimeMillis() + Consts.SNOOZE_DELAY;
		var values = makeContentValues(eventId, eventStart, eventEnd, alarmTime, 0);

		contentResolver.insert(uri, values);

		scheduleAlarm(this, alarmTime, false);
	}


	fun makeContentValues(eventId: Long, begin: Long, end: Long, alarmTime: Long, minutes: Int): ContentValues
	{
		var values = ContentValues();
		values.put(CalendarContract.CalendarAlerts.EVENT_ID, eventId);
		values.put(CalendarContract.CalendarAlerts.BEGIN, begin);
		values.put(CalendarContract.CalendarAlerts.END, end);
		values.put(CalendarContract.CalendarAlerts.ALARM_TIME, alarmTime);
		var currentTime = System.currentTimeMillis();
		values.put(CalendarContract.CalendarAlerts.CREATION_TIME, currentTime);
		values.put(CalendarContract.CalendarAlerts.RECEIVED_TIME, 0);
		values.put(CalendarContract.CalendarAlerts.NOTIFY_TIME, 0);
		values.put(CalendarContract.CalendarAlerts.STATE, CalendarContract.CalendarAlerts.STATE_SCHEDULED);
		values.put(CalendarContract.CalendarAlerts.MINUTES, minutes);
		return values;
	}

	private fun scheduleAlarm(
		context: Context,
		alarmTime: Long,
		quietUpdate: Boolean
	)
	{
		var alarmType = AlarmManager.RTC_WAKEUP;

		var intent = Intent(CalendarContract.ACTION_EVENT_REMINDER);
		intent.setClass(context, AlarmReceiver::class.java);

		if (quietUpdate)
		{
			alarmType = AlarmManager.RTC;
		}
		else
		{
			// Set data field so we get a unique PendingIntent instance per alarm or else alarms
			// may be dropped.
			var builder = CalendarContract.CalendarAlerts.CONTENT_URI.buildUpon();
			ContentUris.appendId(builder, alarmTime);
			intent.setData(builder.build());
		}

		intent.putExtra(CalendarContract.CalendarAlerts.ALARM_TIME, alarmTime);
		var pi = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

		var mgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager;
		mgr.set(alarmType, alarmTime, pi);
	}

	companion object
	{
		val TAG = "DiscardNotificationService"
	}
}
