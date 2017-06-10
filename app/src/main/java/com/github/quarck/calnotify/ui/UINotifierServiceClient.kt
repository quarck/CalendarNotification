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

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.github.quarck.calnotify.logs.DevLog
//import com.github.quarck.calnotify.logs.Logger

/**
 * Created by quarck on 14/02/16.
 */
class UINotifierServiceClient {
    private var isBound: Boolean = false
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            DevLog.debug(LOG_TAG, "onServiceConnected");

            val binder = service as UINotifierService.ServiceBinder

            binder.service.updateActivity = {

                isUserAction ->

                DevLog.debug(LOG_TAG, "updateActivity called, forwarning");

                var action = updateActivity;

                if (action != null) {
                    DevLog.debug(LOG_TAG, "calling action");
                    action(isUserAction);
                }
                else {
                    DevLog.debug(LOG_TAG, "action is null");
                }
            }

            isBound = true
        }

        override fun onServiceDisconnected(name: ComponentName) {
            DevLog.debug(LOG_TAG, "onServiceDisconnected");
        }
    }

    var updateActivity: ((Boolean) -> Unit)? = null

    fun bindService(context: Context, updateAct: ((Boolean) -> Unit)) {
        if (!isBound) {
            DevLog.debug(LOG_TAG, "binding service");

            val intent = Intent(context, UINotifierService::class.java)
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
        else {
            DevLog.debug(LOG_TAG, "Service is already bound")
        }

        updateActivity = updateAct
    }

    fun unbindService(context: Context) {
        if (isBound) {
            DevLog.debug(LOG_TAG, "Unbinding service")

            context.unbindService(serviceConnection)
            isBound = false
        }
        else {
            DevLog.debug(LOG_TAG, "Service is already unbound");
        }
    }

    companion object {
        private const val LOG_TAG = "ServiceUINotifierClient"
    }
}

