package com.github.quarck.calnotify

import com.github.quarck.calnotify.eventsstorage.EventRecord
import com.github.quarck.calnotify.logs.Logger
import com.github.quarck.calnotify.ui.EventListAdapter


class EventsPresenter(var adapter: EventListAdapter) {
    private var events = arrayOf<EventRecord>()

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
