package com.github.quarck.calnotify

import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject
import java.util.*

object PebbleUtils
{
	val SEND_NOTIFICATION_ACTION = "com.getpebble.action.SEND_NOTIFICATION"
	val JSON_OBJECT_KEY_TITLE = "title"
	val JSON_OBJECT_KEY_BODY = "body"
	val INTENT_EXTRA_MESSAGE_TYPE_KEY = "messageType"
	val INTENT_EXTRA_MESSAGE_TYPE = "PEBBLE_ALERT"
	val INTENT_EXTRA_SENDER_KEY = "sender"
	val INTENT_EXTRA_SENDER = "Calendar Notification"
	val INTENT_EXTRA_NOTIFICATION_DATA_KEY = "notificationData"

	fun forwardNotificationToPebble(context: Context, title: String, text: String)
	{
		val i = Intent(SEND_NOTIFICATION_ACTION)

		val data = HashMap<String, String>()
		data.put(JSON_OBJECT_KEY_TITLE, title)
		data.put(JSON_OBJECT_KEY_BODY, text)

		val jsonData = JSONObject(data)
		val notificationData = JSONArray().put(jsonData).toString()

		i.putExtra(INTENT_EXTRA_MESSAGE_TYPE_KEY, INTENT_EXTRA_MESSAGE_TYPE)
		i.putExtra(INTENT_EXTRA_SENDER_KEY, INTENT_EXTRA_SENDER)
		i.putExtra(INTENT_EXTRA_NOTIFICATION_DATA_KEY, notificationData)

		context.sendBroadcast(i)
	}
}
