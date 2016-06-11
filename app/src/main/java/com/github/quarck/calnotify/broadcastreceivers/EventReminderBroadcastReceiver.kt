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
import com.github.quarck.calnotify.calendar.CalendarProvider
import com.github.quarck.calnotify.logs.Logger

class EventReminderBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null)
            return;

        var shouldAbortBroadcast = false;

        val removeOriginal = Settings(context).removeOriginal

        logger.debug("Event reminder received, ${intent.data}, ${intent.action}");

        val uri = intent.data;

        val alertTime: String? = uri.lastPathSegment;

        if (alertTime != null) {
            try {
                val events = CalendarProvider.getAlertByTime(context, alertTime.toLong())

                for (event in events) {
                    logger.info("Seen event ${event.eventId} / ${event.instanceStartTime}")

                    val shouldRemove = ApplicationController.onCalendarEventFired(context, event);

                    if (shouldRemove && removeOriginal) {
                        logger.info("Dismissing original reminder")

                        CalendarProvider.dismissNativeEventAlert(context, event.eventId);
                        shouldAbortBroadcast = true;
                    }
                }

            } catch (ex: Exception) {
                logger.error("Exception while trying to load fired event details, ${ex.message}, ${ex.stackTrace}")
            }
        } else {
            logger.error("ERROR alertTime is null!")
        }

        if (shouldAbortBroadcast && Settings(context).abortBroadcast) {
            logger.info("Aborting broadcast")
            abortBroadcast();
        }
    }

    companion object {
        private val logger = Logger("BroadcastReceiverEventReminder");
    }
}
