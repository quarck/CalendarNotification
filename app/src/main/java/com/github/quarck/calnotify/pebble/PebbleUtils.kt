//
//   Calendar Notifications Plus  
//   Copyright (C) 2016 Sergey Parshin (s.parshin.sc@gmail.com)
//
//   This program is free software; you can redistribute it and/or modify
//   it under the terms of the GNU General Public License as published by
//   the Free Software Foundation; either version 3 of the License, or
//   (at your option) any later version.
// 
//   This program is distributed in the hope that it will be useful,
//   but WITHOUT ANY WARRANTY; without even the implied warranty of
//   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//   GNU General Public License for more details.
//
//   You should have received a copy of the GNU General Public License
//   along with this program; if not, write to the Free Software Foundation,
//   Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301  USA
//

package com.github.quarck.calnotify.pebble

import android.content.Context
import android.content.Intent
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.logs.Logger
import org.json.JSONArray
import org.json.JSONObject
import java.util.*

object PebbleUtils {
    val SEND_NOTIFICATION_ACTION = "com.getpebble.action.SEND_NOTIFICATION"
    val JSON_OBJECT_KEY_TITLE = "title"
    val JSON_OBJECT_KEY_BODY = "body"
    val INTENT_EXTRA_MESSAGE_TYPE_KEY = "messageType"
    val INTENT_EXTRA_MESSAGE_TYPE = "PEBBLE_ALERT"
    val INTENT_EXTRA_SENDER_KEY = "sender"
    val INTENT_EXTRA_SENDER = "Calendar Notification"
    val INTENT_EXTRA_NOTIFICATION_DATA_KEY = "notificationData"

    fun forwardNotificationToPebble(context: Context, title: String, text: String) {

        val i = Intent(SEND_NOTIFICATION_ACTION)

        val data = HashMap<String, String>()

        data.put(JSON_OBJECT_KEY_TITLE, context.resources.getString(R.string.pebble_notification_title))
        data.put(JSON_OBJECT_KEY_BODY, "${title}\n${text}")

        val jsonData = JSONObject(data)
        val notificationData = JSONArray().put(jsonData).toString()

        i.putExtra(INTENT_EXTRA_MESSAGE_TYPE_KEY, INTENT_EXTRA_MESSAGE_TYPE)
        i.putExtra(INTENT_EXTRA_SENDER_KEY, INTENT_EXTRA_SENDER)
        i.putExtra(INTENT_EXTRA_NOTIFICATION_DATA_KEY, notificationData)

        context.sendBroadcast(i)

        logger.info("Notification was forwarded to pebble")
    }

    private val logger = Logger("PebbleUtils")
}
