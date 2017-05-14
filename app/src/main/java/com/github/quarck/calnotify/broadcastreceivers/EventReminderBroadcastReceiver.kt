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

package com.github.quarck.calnotify.broadcastreceivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.app.CalendarManualMonitor
import com.github.quarck.calnotify.app.CalendarManualMonitorInterface
import com.github.quarck.calnotify.calendar.CalendarProvider
import com.github.quarck.calnotify.logs.Logger
import com.github.quarck.calnotify.utils.toLongOrNull

class EventReminderBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {

        if (context == null || intent == null)
            return;

//        var shouldAbortBroadcast = false;

        val calendarMonitor = ApplicationController.CalendarMonitorService

        val removeOriginal = Settings(context).removeOriginal

        logger.debug("Event reminder received, ${intent.data}, ${intent.action}");

        val uri = intent.data;

        val alertTime = uri?.lastPathSegment?.toLongOrNull()
        if (alertTime != null) {
            try {
                val events = CalendarProvider.getAlertByTime(context, alertTime, skipDismissed = false)

                for (event in events) {

                    if (calendarMonitor.getAlertWasHandled(context, event)) {
                        logger.info("Seen event ${event.eventId} / ${event.instanceStartTime} - it was handled already, skipping")
                        continue
                    }

                    logger.info("Seen event ${event.eventId} / ${event.instanceStartTime}")

                    val wasHandled = ApplicationController.onCalendarEventFired(context, event);

                    if (wasHandled) {
                        calendarMonitor.setAlertWasHandled(context, event, createdByUs = false)
                        logger.info("Event ${event.eventId} / ${event.instanceStartTime} is marked as handled in the DB")
                    }

                    if (wasHandled && removeOriginal) {
                        logger.info("Dismissing original reminder")

                        CalendarProvider.dismissNativeEventAlert(context, event.eventId);
//                        shouldAbortBroadcast = true;
                    }
                }

            } catch (ex: Exception) {
                logger.error("Exception while trying to load fired event details, ${ex.message}, ${ex.stackTrace}")
            }
        } else {
            logger.error("ERROR alertTime is null!")
        }

//        if (shouldAbortBroadcast && Settings(context).abortBroadcast) {
//            logger.info("Aborting broadcast")
//            abortBroadcast();
//        }
    }

    companion object {
        private val logger = Logger("BroadcastReceiverEventReminder");
    }
}
