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
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.logs.DevLog
//import com.github.quarck.calnotify.logs.Logger
import com.github.quarck.calnotify.ui.UINotifierService

class NotificationActionSnoozeService : IntentService("NotificationActionSnoozeService") {

    override fun onHandleIntent(intent: Intent?) {
        DevLog.debug(LOG_TAG, "onHandleIntent")

        if (intent != null) {

            val isSnoozeAll = intent.getBooleanExtra(Consts.INTENT_SNOOZE_ALL_KEY, false)

            if (!isSnoozeAll) {
                val notificationId = intent.getIntExtra(Consts.INTENT_NOTIFICATION_ID_KEY, -1)
                val eventId = intent.getLongExtra(Consts.INTENT_EVENT_ID_KEY, -1)
                val instanceStartTime = intent.getLongExtra(Consts.INTENT_INSTANCE_START_TIME_KEY, -1)
                val snoozeDelay = intent.getLongExtra(Consts.INTENT_SNOOZE_PRESET, Settings(this).snoozePresets[0])

                if (notificationId != -1 && eventId != -1L && instanceStartTime != -1L) {
                    if (ApplicationController.snoozeEvent(this, eventId, instanceStartTime, snoozeDelay) != null)
                        DevLog.info(this, LOG_TAG, "event $eventId / $instanceStartTime snoozed by $snoozeDelay")

                    UINotifierService.notifyUI(this, true);
                } else {
                    DevLog.error(this, LOG_TAG, "notificationId=$notificationId, eventId=$eventId, or type is null")
                }
            }
            else {
                DevLog.info(this, LOG_TAG, "Snooze all from notification request")

                val snoozeDelay = intent.getLongExtra(Consts.INTENT_SNOOZE_PRESET, Settings(this).snoozePresets[0])

                if (ApplicationController.snoozeAllEvents(this, snoozeDelay, false, true) != null)
                    DevLog.info(this, LOG_TAG, "all visible snoozed by $snoozeDelay")

                UINotifierService.notifyUI(this, true);
            }
        }
        else {
            DevLog.error(this, LOG_TAG, "Intent is null!")
        }

        ApplicationController.cleanupEventReminder(this)
    }

    companion object {
        private const val LOG_TAG = "NotificationActionSnoozeService"
    }
}
