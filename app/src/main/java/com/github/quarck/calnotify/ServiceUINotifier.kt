package com.github.quarck.calnotify

import android.app.IntentService
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder

class ServiceUINotifier : IntentService("ServiceUINotifier")
{
	inner class ServiceBinder : Binder()
	{
		val sevice: ServiceUINotifier
			get() = this@ServiceUINotifier
	}

	private val binder = ServiceBinder()

	public var updateActivity: (() -> Unit)? = null

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
			logger.debug("calling action")
			action();
		}
		else
		{
			logger.debug("action is null")
		}
	}

	companion object
	{
		private val logger = Logger("ServiceUINotifier")

		public fun notifyUI(context: Context?)
		{
			if (context != null)
			{
				var serviceIntent = Intent(context, ServiceUINotifier::class.java)
				context.startService(serviceIntent)
			}
		}
	}
}
