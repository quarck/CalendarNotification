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

package com.github.quarck.calnotify

import com.github.quarck.calnotify.eventsstorage.EventRecord
import com.github.quarck.calnotify.logs.Logger
import com.github.quarck.calnotify.ui.EventListAdapter


class EventsPresenter(var adapter: EventListAdapter) {
    private var events = arrayOf<EventRecord>()

    val size: Int
        get() = events.size

    fun setEventsToDisplay(newEvents: Array<EventRecord>) {
        logger.debug("setEventsToDisplay called for ${newEvents.size} events")

        synchronized(this) {
            events = newEvents;
            adapter.events = events;
            adapter.notifyDataSetChanged();
        }
    }

    fun getEventAtPosition(position: Int): EventRecord? {
        var event: EventRecord? = null

        synchronized(this) {
            if (position >= 0 && position < events.size) {
                event = events[position];
            }
        }

        return event;
    }


    fun removeAt(position: Int) {
        synchronized(this) {
            events = events.filterIndexed { idx, ev -> idx != position }.toTypedArray()
            adapter.events = events;
            adapter.notifyItemRemoved(position)
        }
    }

    companion object {
        private var logger = Logger("EventsPresenter")
    }
}
