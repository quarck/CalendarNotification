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

package com.github.quarck.calnotify.ui

import android.app.IntentService
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.logs.Logger

class ServiceUINotifier : IntentService("ServiceUINotifier")
{
	inner class ServiceBinder : Binder()
	{
		val sevice: ServiceUINotifier
			get() = this@ServiceUINotifier
	}

	private val binder = ServiceBinder()

	public var updateActivity: ((Boolean) -> Unit)? = null

	override fun onBind(intent: Intent): IBinder?
	{
		logger.debug("onBind")
		return binder
	}

	override fun onUnbind(intent: Intent): Boolean
	{
		logger.debug("onUnbind")
		updateActivity = null
		return true
	}

	override fun onRebind(intent: Intent)
	{
		logger.debug("onRebind")
		super.onRebind(intent)
	}

	override fun onHandleIntent(intent: Intent)
	{
		logger.debug("onHandleIntent")

		var action = updateActivity
		if (action != null)
		{
			var isUserAction = intent.getBooleanExtra(Consts.INTENT_IS_USER_ACTION, true)
			logger.debug("calling action, isUserAction=$isUserAction")
			action(isUserAction);
		}
		else
		{
			logger.debug("action is null")
		}
	}

	companion object
	{
		private val logger = Logger("ServiceUINotifier")

		fun notifyUI(context: Context?, isUserAction: Boolean)
		{
			logger.debug("notifyUI called, isUserAction=$isUserAction")

			if (context != null)
			{
				var serviceIntent = Intent(context, ServiceUINotifier::class.java)
				serviceIntent.putExtra(Consts.INTENT_IS_USER_ACTION, isUserAction);
				context.startService(serviceIntent)
			}
		}
	}
}
