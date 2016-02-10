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
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.provider.CalendarContract
import java.text.DateFormat
import java.util.*

class NotificationViewManager
{
	public fun postNotification(
		ctx: Context,
		event: EventRecord,
		notificationSettings: NotificationSettingsSnapshot
	)
	{
		var notificationManager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

		var calendarIntentUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.eventId);
		var calendarIntent = Intent(Intent.ACTION_VIEW).setData(calendarIntentUri);
		var calendarPendingIntent = PendingIntent.getActivity(ctx, 0, calendarIntent, 0)

		var notificationText = formatNotificationText(event)

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

		Logger.debug("NotificationViewManager: adding pending intent for snooze, event id ${event.eventId}, notificationId ${event.notificationId}")

		builder.addAction(
			android.R.drawable.ic_menu_rotate,
			ctx.getString(R.string.snooze) ?: "SNOOZE",
			pendingServiceIntent(ctx,
				serviceIntent(ctx, Consts.INTENT_TYPE_SNOOZE, event.eventId, event.notificationId),
				event.notificationId * 3 + 0
			)
		)

		if (notificationSettings.showDismissButton)
		{
			builder.addAction(
				android.R.drawable.ic_menu_close_clear_cancel,
				ctx.getString(R.string.dismiss) ?: "DISMISS",
				pendingServiceIntent(ctx,
					serviceIntent(ctx, Consts.INTENT_TYPE_DISMISS, event.eventId, event.notificationId),
					event.notificationId * 3 + 1
				)
			)
		}
		else
		{
			builder.setDeleteIntent(
				pendingServiceIntent(ctx,
					serviceIntent(ctx, Consts.INTENT_TYPE_DELETE, event.eventId, event.notificationId),
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

		var newNotification = builder.build()

		notificationManager.notify(
			"${Consts.NOTIFICATION_TAG};${event.eventId}",
			event.notificationId,
			newNotification)

		if (notificationSettings.forwardToPebble)
			forwardNotificationToPebble(ctx, event.title, notificationText)
	}

	private fun serviceIntent(ctx: Context, type: String, eventId: Long, notificationId: Int): Intent
	{
		var intent = Intent(ctx, ServiceNotificationActionHandler::class.java)
		intent.putExtra(Consts.INTENT_NOTIFICATION_ID_KEY, notificationId);
		intent.putExtra(Consts.INTENT_EVENT_ID_KEY, eventId);
		intent.putExtra(Consts.INTENT_TYPE, type)
		return intent;
	}

	private fun pendingServiceIntent(ctx: Context, intent: Intent, id: Int): PendingIntent
		= PendingIntent.getService(ctx, id, intent, PendingIntent.FLAG_CANCEL_CURRENT)

	private fun formatNotificationText(event: EventRecord): String
	{
		var sb = StringBuilder()

		if (event.startTime != 0L)
		{
			var today = Date(System.currentTimeMillis())
			var start = Date(event.startTime)

			var dateFormatter = DateFormat.getDateInstance(DateFormat.SHORT)
			var timeFormatter = DateFormat.getTimeInstance(DateFormat.SHORT)

			if (today.day != start.day
				&& today.month != start.month
				&& today.year != start.year)
			{
				sb.append(dateFormatter.format(start));
				sb.append(" ");
			}

			sb.append(timeFormatter.format(start));

			if (event.endTime != 0L)
			{
				sb.append(" - ");

				var end = Date(event.endTime)

				if (end.day != start.day
					&& end.month != start.month
					&& end.year != start.year)
				{
					sb.append(dateFormatter.format(end))
					sb.append(" ");
				}

				sb.append(timeFormatter.format(end))
			}
		}

		if (event.location != "")
		{
			sb.append("\nLocation: ")
			sb.append(event.location)
		}

		return sb.toString()
	}

	fun removeNotification(ctx: Context, eventId: Long, notificationId: Int)
	{
		var notificationManager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
		notificationManager.cancel("${Consts.NOTIFICATION_TAG};${eventId}", notificationId);
	}

	fun onInternalError(context: Context)
	{
		var builder = CalendarContract.CONTENT_URI.buildUpon();
		builder.appendPath("time");
		ContentUris.appendId(builder, Calendar.getInstance().timeInMillis);
		var intent = Intent(Intent.ACTION_VIEW).setData(builder.build());
		val pendingIntent = PendingIntent.getActivity(context, 0, intent, 0)

		val notification =
			Notification
				.Builder(context)
				.setContentTitle(context.getString(R.string.internal_error))
				.setContentText(context.getString(R.string.internal_error_text))
				.setSmallIcon(R.drawable.ic_launcher)
				.setPriority(Notification.PRIORITY_HIGH)
				.setContentIntent(pendingIntent)
				.setAutoCancel(true)
				.setDefaults(Notification.DEFAULT_SOUND)
				.setVibrate(longArrayOf(1000))
				.setLights(0x7fffffff, 300, 300)
				.build()

		var notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
		notificationManager.notify(Consts.NOTIFICATION_ID_ERROR, notification) // would update if already exists
	}
}
