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

package com.github.quarck.calnotify.notification

import android.app.IntentService
import android.content.Intent
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.dismissedeventsstorage.EventDismissType
import com.github.quarck.calnotify.logs.Logger
import com.github.quarck.calnotify.ui.UINotifierService

class NotificationActionDismissService : IntentService("NotificationActionDismissService") {

    override fun onHandleIntent(intent: Intent?) {
        logger.debug("onHandleIntent")

        if (intent != null) {
            val notificationId = intent.getIntExtra(Consts.INTENT_NOTIFICATION_ID_KEY, -1)
            val eventId = intent.getLongExtra(Consts.INTENT_EVENT_ID_KEY, -1)
            val instanceStartTime = intent.getLongExtra(Consts.INTENT_INSTANCE_START_TIME_KEY, -1)

            if (notificationId != -1 && eventId != -1L && instanceStartTime != -1L) {
                ApplicationController.dismissEvent(
                        this,
                        EventDismissType.ManuallyDismissedFromNotification,
                        eventId,
                        instanceStartTime,
                        notificationId)
                logger.info("ServiceNotificationActionDismiss: event dismissed by user: $eventId")

                UINotifierService.notifyUI(this, true);
            }
            else {
                logger.error("notificationId=$notificationId, eventId=$eventId, instanceStartTime=$instanceStartTime, or type is null")
            }
        }
        else {
            logger.error("Intent is null!")
        }

        ApplicationController.cleanupEventReminder(this)
    }

    companion object {
        private val logger = Logger("NotificationActionDismissService")
    }
}
