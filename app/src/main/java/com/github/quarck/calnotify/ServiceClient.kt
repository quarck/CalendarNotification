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

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.RemoteException

class ServiceClient(val onNoPermissions: ()-> Unit ) : Handler.Callback
{
	private var mService: Messenger? = null

	private var mIsBound: Boolean = false

	private val mMessenger = Messenger(Handler(this))

	override fun handleMessage(msg: Message): Boolean
	{
		Logger.debug(TAG, "handleMessage: " + msg.what)

		when (msg.what)
		{
			ServiceNotificationReceiver.MSG_NO_PERMISSIONS -> onNoPermissions()
		}

		return true
	}

	private val mConnection = object : ServiceConnection
	{
		override fun onServiceConnected(className: ComponentName, service: IBinder)
		{
			Logger.debug(TAG, "Got connection to the service")

			mService = Messenger(service)
		}

		override fun onServiceDisconnected(className: ComponentName)
		{
			Logger.debug(TAG, "Service disconnected")

			mService = null
		}
	}

	fun bindService(ctx: Context)
	{
		Logger.debug(TAG, "Binding service")

		val it = Intent(ctx, ServiceNotificationReceiver::class.java)
		it.putExtra(ServiceNotificationReceiver.configServiceExtra, true)

		ctx.bindService(it, mConnection, Context.BIND_AUTO_CREATE)
		mIsBound = true
	}

	fun unbindService(ctx: Context)
	{
		if (mIsBound)
		{
			Logger.debug(TAG, "unbinding service")
			ctx.unbindService(mConnection)
			mIsBound = false
		}
		else
		{
			Logger.error(TAG, "unbind request, but service is not bound!")
		}
	}

	private fun sendServiceReq(code: Int)
	{
		Logger.debug(TAG, "Sending request $code to the service")

		if (mIsBound)
		{
			if (mService != null)
			{
				try
				{
					val msg = Message.obtain(null, code)
					msg.replyTo = mMessenger
					mService!!.send(msg)
				}
				catch (e: RemoteException)
				{
					Logger.error(TAG, "Failed to send req - got exception " + e)
				}
			}
		}
		else
		{
			Logger.error(TAG, "Failed to send req - service is not bound!")
		}
	}

	fun checkPermissions()
	{
		sendServiceReq(ServiceNotificationReceiver.MSG_CHECK_PERMISSIONS)
	}

	fun checkPermissionsAndShowNotification()
	{
		sendServiceReq(ServiceNotificationReceiver.MSG_CHECK_PERMISSIONS_AFTER_UPDATE)

	}

	companion object
	{
		private val TAG = "ServiceClient"
	}
}
