package com.github.quarck.calnotify

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder

/**
 * Created by quarck on 14/02/16.
 */
class ServiceUINotifierClient
{
	private var isBound: Boolean = false
	private val serviceConnection = object : ServiceConnection
	{
		override fun onServiceConnected(name: ComponentName, service: IBinder)
		{
			logger.debug("onServiceConnected");

			val binder = service as ServiceUINotifier.ServiceBinder

			binder.sevice.updateActivity = {

				logger.debug("updateActivity called, forwarning");

				var action = updateActivity;

				if (action != null)
				{
					logger.debug("calling action");
					action();
				}
				else
				{
					logger.debug("action is null");
				}
			}

			isBound = true
		}

		override fun onServiceDisconnected(name: ComponentName)
		{
			logger.debug("onServiceDisconnected");
		}
	}

	public var updateActivity: (() -> Unit)? = null

	fun bindService(context: Context)
	{
		if (!isBound)
		{
			logger.debug("binding service");

			val intent = Intent(context, ServiceUINotifier::class.java)
			context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
		}
		else
		{
			logger.debug("Service is already bound")
		}
	}

	fun unbindService(context: Context)
	{
		if (isBound)
		{
			logger.debug("Unbinding service")

			context.unbindService(serviceConnection)
			isBound = false
		}
		else
		{
			logger.debug("Service is already unbound");
		}
	}

	companion object
	{
		private val logger = Logger("ServiceUINotifierClient")
	}
}

