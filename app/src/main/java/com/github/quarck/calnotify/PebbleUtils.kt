package com.github.quarck.calnotify

import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject
import java.util.*

/**
 * Created by quarck on 03/02/16.
 */

fun forwardNotificationToPebble(context: Context, title: String, text: String)
{
	val i = Intent("com.getpebble.action.SEND_NOTIFICATION")

	val data = HashMap<String, String>()
	data.put("title", title)
	data.put("body", text)

	val jsonData = JSONObject(data)
	val notificationData = JSONArray().put(jsonData).toString()

	i.putExtra("messageType", "PEBBLE_ALERT")
	i.putExtra("sender", "Calendar Notification")
	i.putExtra("notificationData", notificationData)

	context.sendBroadcast(i)
}
