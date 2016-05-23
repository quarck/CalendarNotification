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
import com.github.quarck.calnotify.EventsManager
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.calendar.CalendarUtils
import com.github.quarck.calnotify.logs.Logger

class EventReminderBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null)
            return;

        var shouldAbortBroadcast = false;

        var removeOriginal = Settings(context).removeOriginal

        logger.debug("Event reminder received, ${intent.data}, ${intent.action}");

        var uri = intent.data;

        var alertTime: String? = uri.lastPathSegment;

        if (alertTime != null) {
            var events = CalendarUtils.getFiredEventsDetails(context, alertTime)

            if (events != null) {
                for (event in events) {
                    logger.info("broadcastreceiver: Seen event ${event.eventId} / ${event.title}")

                    EventsManager.onCalendarEventFired(context, event);

                    if (removeOriginal) {
                        logger.info("Dismissing original reminder")

                        CalendarUtils.dismissNativeEventReminder(context, event.eventId);
                        shouldAbortBroadcast = true;
                    }
                }
            } else {
                logger.info("broadcastreceiver: ERROR: Fired but no events")
            }
        } else {
            logger.error("broadcastreceiver ERROR alertTime is null!")
        }

        if (shouldAbortBroadcast && Settings(context).abortBroadcast) {
            logger.info("broadcastreceiver: Aborting broadcast")
            abortBroadcast();
        }
    }

    companion object {
        private val logger = Logger("BroadcastReceiverEventReminder");
    }
}
