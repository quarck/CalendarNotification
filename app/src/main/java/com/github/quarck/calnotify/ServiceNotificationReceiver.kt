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
import android.content.Context
import android.content.Intent
import android.os.*
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.lang.reflect.Method
import java.util.*

class ServiceNotificationReceiver : NotificationListenerService(), Handler.Callback
{
	private val messenger = Messenger(Handler(this))

	private var settings: Settings? = null

	private var db: EventsStorage? = null

	private var handler: Handler? = null

	override fun handleMessage(msg: Message): Boolean
	{
		Logger.debug(TAG, "handleMessage, msg=${msg.what}")

		var ret = true

		if (msg.what == MSG_CHECK_PERMISSIONS)
			ret = handleCheckPermissions(msg)
		else if (msg.what == MSG_CHECK_PERMISSIONS_AFTER_UPDATE)
			ret = handleCheckPermissionsAndNotification(msg)

		return ret
	}


	private fun handleCheckPermissions(msg: Message): Boolean
	{
		if (!checkPermissions())
			reply(msg, Message.obtain(null, MSG_NO_PERMISSIONS, 0, 0))

		return true
	}

	private fun handleCheckPermissionsAndNotification(msg: Message): Boolean
	{
		if (!checkPermissions() && settings!!.isServiceEnabled)
		{
			notificationMgr!!.onAccessToNotificationsLost(this)
		}

		return true
	}

	private fun reply(msgIn: Message, msgOut: Message)
	{
		try
		{
			msgIn.replyTo.send(msgOut)
		}
		catch (e: RemoteException)
		{
			e.printStackTrace()
		}
	}

	private fun checkPermissions(): Boolean
	{
		var ret = false
		Logger.debug(TAG, "checkPermissions")
		try
		{
			var notifications = activeNotifications
			Logger.error(TAG, "Got ${notifications.size} notifications during check")
			ret = true
		}
		catch (ex: NullPointerException)
		{
			Logger.error(TAG, "Got exception, have no permissions!")
		}

		return ret
	}


	override fun onCreate()
	{
		super.onCreate()
		settings = Settings(this)
		db = EventsStorage(this)
		handler = Handler()
	}

	override fun onDestroy()
	{
		super.onDestroy()
	}

	override fun onBind(intent: Intent): IBinder?
	{
		if (intent.getBooleanExtra(configServiceExtra, false))
			return messenger.binder

		return super.onBind(intent)
	}

	override fun onNotificationPosted(notification: StatusBarNotification)
	{
	}

	override fun onNotificationRemoved(notification: StatusBarNotification)
	{
	}

	companion object
	{
		val TAG = "Service"

		val configServiceExtra = "configService"

		val MSG_CHECK_PERMISSIONS = 1
		val MSG_NO_PERMISSIONS = 2
		var MSG_CHECK_PERMISSIONS_AFTER_UPDATE = 3

		var notificationMgr = NotificationViewManager()
	}
}
