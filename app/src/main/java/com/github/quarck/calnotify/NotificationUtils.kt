package com.github.quarck.calnotify

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.service.notification.StatusBarNotification

fun Notification.getTitleAndText(): Pair<String, String>
{
	var extras = this.extras;

	var title: String = "";
	var text: String = "";

	if (extras != null)
	{
		if ((extras.get(Notification.EXTRA_TITLE) != null || extras.get(Notification.EXTRA_TITLE_BIG) != null)
			&&
			(extras.get(Notification.EXTRA_TEXT) != null || extras.get(Notification.EXTRA_TEXT_LINES) != null))
		{
			if (extras.get(Notification.EXTRA_TITLE_BIG) != null)
			{
				var bigTitle = extras.getCharSequence(Notification.EXTRA_TITLE_BIG) as CharSequence;
				if (bigTitle.length < 40 || extras.get(Notification.EXTRA_TITLE) == null)
					title = bigTitle.toString();
				else
					title = extras.getCharSequence(Notification.EXTRA_TITLE).toString();
			}
			else
				title = extras.getCharSequence(Notification.EXTRA_TITLE).toString();

			if (extras.get(Notification.EXTRA_TEXT_LINES) != null)
			{
				for (line in extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES))
				{
					text += line;
					text += "\n\n";
				}
				text = text.trim();
			}
			else
			{
				text = extras.getCharSequence(Notification.EXTRA_TEXT).toString();
			}
		}
	}

	return Pair(title, text)
}

fun PendingIntent.getIntent(): Intent
{
	try
	{
		var getIntent = PendingIntent::class.java.getDeclaredMethod("getIntent");
		return getIntent.invoke(this) as Intent;
	}
	catch (e: Exception)
	{
		throw IllegalStateException(e);
	}
}

fun Notification.getGooleCalendarEventId(): Long?
{
	var ret: Long? = null;

	try
	{
		var originalIntent = this.contentIntent.getIntent()

		Logger.debug(ServiceNotificationReceiver.TAG, "Got original intent: url=${originalIntent.toUri(Intent.URI_INTENT_SCHEME)}")

		ret =
			originalIntent
				.toUri(Intent.URI_INTENT_SCHEME)
				.split(';')
				.filter { x -> x.contains("l.eventid=") }
				.first()
				.split("l.eventid=")
				.last()
				.toLong()

	}
	catch (ex: Exception)
	{
		Logger.debug("NUtils:", "exception in getGooleCalendarEventId: ${ex.message}")
		ret = null
	}

	return ret;
}

fun Notification.isGoogleCalendarReminder(): Boolean
{
	var ret = false;

	try
	{
		var originalIntent = this.contentIntent.getIntent()

		Logger.debug(ServiceNotificationReceiver.TAG, "Got original intent: url=${originalIntent.toUri(Intent.URI_INTENT_SCHEME)}")

		var uri = originalIntent.toUri(Intent.URI_INTENT_SCHEME)
		ret = uri.contains("intent://com.google.android.timely/alerts")

		Logger.debug("NUtils: ret=$ret, uri=${uri}")
	}
	catch (ex: Exception)
	{
		Logger.debug("NUtils:", "exception in isGoogleCalendarReminder: ${ex.message}")
		ret = false
	}

	return ret;
}

fun StatusBarNotification.getOurNotificationEventId(): Long?
{
	var ret: Long? = null;

	try
	{
		var tag = this.tag;
		if (tag != null)
		{
			ret = tag.split(';').last().toLong()
		}
	}
	catch (ex: Exception)
	{
		Logger.debug("NUtils:", "exception in getOurNotificationEventId: ${ex.message}")
		ret = null
	}
	return ret;
}

fun postCachedNotifications(context: Context)
{
	var db = EventsStorage(context)
	var mgr = NotificationViewManager()
	var settings = Settings(context)

	var ringtoneUri = settings!!.ringtoneURI
	var vibrate = settings!!.vibraOn

	for (notification in db.events)
	{
		// Play only one notification with sound
		mgr.postNotification(
			context,
			notification,
			NotificationSettingsSnapshot(settings!!.showDismissButton, ringtoneUri, vibrate, settings!!.ledNotificationOn, false))

		ringtoneUri = null
		vibrate = false
	}
}
