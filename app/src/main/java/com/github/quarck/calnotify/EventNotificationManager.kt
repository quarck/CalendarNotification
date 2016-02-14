/*
 * Copyright (c) 2015, Sergey Parshin, s.parshin@outlook.com
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of developer (Sergey Parshin) nor the
 *       names of other project contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */


package com.github.quarck.calnotify

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TaskStackBuilder
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import java.text.DateFormat
import java.util.*

interface IEventNotificationManager
{
	fun onEventAdded(ctx: Context, event: EventRecord);

	fun onEventDismissed(ctx: Context, eventId: Long, notificationId: Int);

	fun onEventSnoozed(ctx: Context, eventId: Long, notificationId: Int);

	fun postEventNotifications(context: Context, force: Boolean);
}

class EventNotificationManager: IEventNotificationManager
{
	override fun onEventAdded(
		ctx: Context,
		event: EventRecord
	)
	{
//		postNotification(ctx, event, Settings(ctx).notificationSettingsSnapshot);
		postEventNotifications(ctx, false);
	}

	override fun onEventDismissed(ctx: Context, eventId: Long, notificationId: Int)
	{
		removeNotification(ctx, eventId, notificationId);

		postEventNotifications(ctx, false);
	}

	override fun onEventSnoozed(ctx: Context, eventId: Long, notificationId: Int)
	{
		removeNotification(ctx, eventId, notificationId);

		postEventNotifications(ctx, false);
	}

	override fun postEventNotifications(context: Context, force: Boolean)
	{
		var db = EventsStorage(context)
		var settings = Settings(context)

		var currentTime = System.currentTimeMillis()

		var eventsToUpdate =
			db.events.filter {
				// events with snoozedUntil == 0 are currently visible ones
				// events with experied snoozedUntil are the ones to beep about
				// everything else should be hidden and waiting for the next alarm
				(it.snoozedUntil == 0L)
					|| (it.snoozedUntil < currentTime + Consts.ALARM_THRESHOULD)
			}

		if (eventsToUpdate.size <= Consts.MAX_NOTIFICATIONS)
		{
			hideNumNotificationsCollapsed(context);

			postRegularEvents(context, db, settings, eventsToUpdate, force)
		}
		else
		{
			var sortedEvents = eventsToUpdate.sortedBy { it.lastEventUpdate }

			var recent = sortedEvents.takeLast( Consts.MAX_NOTIFICATIONS -1);
			var older = sortedEvents.take( sortedEvents.size - recent.size)

			hideCollapsedNotifications(context, db, older, force);
			postRegularEvents(context, db, settings, recent, force);

			postNumNotificationsCollapsed(context, older.size);
		}
	}

	private fun hideCollapsedNotifications(context: Context, db: EventsStorage, events: List<EventRecord>, force: Boolean)
	{
		logger.debug("Hiding notifications for ${events.size} notification")

		for (event in events)
		{
			if (event.isDisplayed || force)
			{
				logger.debug("Hiding notification id ${event.notificationId}, eventId ${event.eventId}")
				removeNotification(context, event.eventId, event.notificationId);

				event.isDisplayed = false;
				db.updateEvent(event);
			}
			else
			{
				logger.debug("Skipping hiding of notification id ${event.notificationId}, eventId ${event.eventId} - already hidden");
			}
		}
	}

	private fun postRegularEvents(
		context: Context,
		db: EventsStorage,
		settings: Settings,
		events: List<EventRecord>, force: Boolean
	)
	{
		logger.debug("Posting ${events.size} notifications");

		for (event in events)
		{
			if (event.snoozedUntil == 0L)
			{
				// Event was already "beeped", need to make sure it is shown, quietly...
				if (!event.isDisplayed || force)
				{
					logger.debug("Posting notification id ${event.notificationId}, eventId ${event.eventId}");

					postNotification(
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

					event.isDisplayed = true;
					db.updateEvent(event);
				}
				else
				{
					logger.debug("Not re-posting notification id ${event.notificationId}, eventId ${event.eventId} - already on the screen");
				}
			}
			else
			{
				logger.debug("Initial posting notification id ${event.notificationId}, eventId ${event.eventId}");

				// it is time to show this event after a snooze or whatever,
				// turn on all the bells and whistles
				postNotification(
					context,
					event,
					settings.notificationSettingsSnapshot
				)

				event.isDisplayed = true;
				event.snoozedUntil = 0; // Since it is displayed now -- it is no longer snoozed
				db.updateEvent(event);
			}
		}
	}

	private fun postNotification(
		ctx: Context,
		event: EventRecord,
		notificationSettings: NotificationSettingsSnapshot
	)
	{
		var notificationManager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

		var calendarIntentUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.eventId);
		var calendarIntent = Intent(Intent.ACTION_VIEW).setData(calendarIntentUri);
		var calendarPendingIntent = PendingIntent.getActivity(ctx, 0, calendarIntent, 0)

		var notificationText = event.formatText(ctx);

		var builder = Notification
			.Builder(ctx)
			.setContentTitle(event.title)
			.setContentText(notificationText)
			.setSmallIcon(R.drawable.stat_notify_calendar)
			.setPriority(Notification.PRIORITY_HIGH)
			.setContentIntent(calendarPendingIntent)
			.setAutoCancel(!notificationSettings.showDismissButton)
			.setOngoing(notificationSettings.showDismissButton)
			.setStyle(Notification.BigTextStyle()
				.bigText(notificationText))

		logger.debug("adding pending intent for snooze, event id ${event.eventId}, notificationId ${event.notificationId}")

		builder.addAction(
			android.R.drawable.ic_menu_rotate,
			ctx.getString(R.string.snooze) ?: "SNOOZE",
			pendingActivityIntent(ctx,
				snoozeIntent(ctx, event.eventId, event.notificationId),
				event.notificationId * 3 + 0
			)
		)

		if (notificationSettings.showDismissButton)
		{
			builder.addAction(
				android.R.drawable.ic_menu_close_clear_cancel,
				ctx.getString(R.string.dismiss) ?: "DISMISS",
				pendingServiceIntent(ctx,
					dismissOrDeleteIntent(ctx, event.eventId, event.notificationId),
					event.notificationId * 3 + 1
				)
			)
		}
		else
		{
			builder.setDeleteIntent(
				pendingServiceIntent(ctx,
					dismissOrDeleteIntent(ctx, event.eventId, event.notificationId),
					event.notificationId * 3 + 2
				)
			)
		}

		if (notificationSettings.ringtoneUri != null)
		{
			builder.setSound(notificationSettings.ringtoneUri)
		}

		if (notificationSettings.vibraOn)
		{
			builder.setVibrate(longArrayOf(Consts.VIBRATION_DURATION));
		}

		if (notificationSettings.ledNotificationOn)
		{
			builder.setLights(Consts.LED_COLOR, Consts.LED_DURATION_ON, Consts.LED_DURATION_OFF);
		}

		var notification = builder.build()

		try
		{
			logger.debug(
				"adding: notificationId=${event.notificationId}, notification is ${notification}, stack:")

			notificationManager.notify(
				event.notificationId,
				notification
			)
		}
		catch (ex: Exception)
		{
			logger.error(
				"Exception: ${ex.toString()}, notificationId=${event.notificationId}, notification is ${if (notification != null) 1 else 0}, stack:")
			ex.printStackTrace()
		}

		if (notificationSettings.forwardToPebble)
			PebbleUtils.forwardNotificationToPebble(ctx, event.title, notificationText)
	}

	private fun snoozeIntent(ctx: Context, eventId: Long, notificationId: Int): Intent
	{
		var intent = Intent(ctx, ActivitySnooze::class.java)
		intent.putExtra(Consts.INTENT_NOTIFICATION_ID_KEY, notificationId);
		intent.putExtra(Consts.INTENT_EVENT_ID_KEY, eventId);
		return intent;
	}

	private fun dismissOrDeleteIntent(ctx: Context, eventId: Long, notificationId: Int): Intent
	{
		var intent = Intent(ctx, ServiceNotificationActionDismiss::class.java)
		intent.putExtra(Consts.INTENT_NOTIFICATION_ID_KEY, notificationId);
		intent.putExtra(Consts.INTENT_EVENT_ID_KEY, eventId);
		return intent;
	}

	private fun pendingServiceIntent(ctx: Context, intent: Intent, id: Int): PendingIntent
		= PendingIntent.getService(ctx, id, intent, PendingIntent.FLAG_CANCEL_CURRENT)

	private fun pendingActivityIntent(ctx: Context, intent: Intent, id: Int): PendingIntent
	{
		var pendingIntent =
				TaskStackBuilder.create(ctx)
					.addNextIntentWithParentStack(intent)
					.getPendingIntent(id, PendingIntent.FLAG_UPDATE_CURRENT);

		return pendingIntent;
	}

	private fun removeNotification(ctx: Context, eventId: Long, notificationId: Int)
	{
		var notificationManager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
		notificationManager.cancel(notificationId);
	}

	private fun postNumNotificationsCollapsed(context: Context, numCollapsed: Int)
	{
		logger.debug("Posting 'collapsed view' notification");

		var intent = Intent(context, ActivityMain::class.java);
		val pendingIntent = PendingIntent.getActivity(context, 0, intent, 0)

		var title = java.lang.String.format(context.getString(R.string.multiple_events), numCollapsed);

		val notification =
			Notification
				.Builder(context)
				.setContentTitle(title)
				.setContentText(context.getString(R.string.multiple_events_details))
				.setSmallIcon(R.drawable.stat_notify_calendar)
				.setPriority(Notification.PRIORITY_DEFAULT)
				.setContentIntent(pendingIntent)
				.setAutoCancel(false)
				.setOngoing(true)
				.setDefaults(Notification.DEFAULT_SOUND)
				.setVibrate(longArrayOf(Consts.VIBRATION_DURATION))
				.setLights(Consts.LED_COLOR, Consts.LED_DURATION_ON, Consts.LED_DURATION_OFF)
				.build()

		var notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
		notificationManager.notify(Consts.NOTIFICATION_ID_COLLAPSED, notification) // would update if already exists
	}

	private fun hideNumNotificationsCollapsed(context: Context)
	{
		logger.debug("Hiding 'collapsed view' notification");

		var notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
		notificationManager.cancel(Consts.NOTIFICATION_ID_COLLAPSED);
	}

	companion object
	{
		private val logger = Logger("EventNotificationManager")
	}
}
