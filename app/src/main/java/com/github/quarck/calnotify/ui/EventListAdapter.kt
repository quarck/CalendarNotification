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

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.RelativeLayout
import android.widget.TextView
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.textutils.formatSnoozedUntil
import com.github.quarck.calnotify.textutils.formatTime
import com.github.quarck.calnotify.utils.adjustCalendarColor
import com.github.quarck.calnotify.utils.find

interface EventListCallback {
    fun onItemClick(v: View, position: Int, eventId: Long): Unit
    fun onItemDismiss(v: View, position: Int, eventId: Long): Unit
    fun onItemSnooze(v: View, position: Int, eventId: Long): Unit
    fun onItemLocation(v: View, position: Int, eventId: Long): Unit
    fun onItemDateTime(v: View, position: Int, eventId: Long): Unit
}

@Suppress("DEPRECATION")
class EventListAdapter(
    val context: Context,
    val cardVewResourceId: Int,
    val callback: EventListCallback)

: RecyclerView.Adapter<EventListAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View)
    : RecyclerView.ViewHolder(itemView) {
        var eventId: Long = 0;

        var eventHolder: RelativeLayout
        var eventTitle: TextView
        var eventTitleLayout: RelativeLayout
        var eventDate: TextView
        var eventTime: TextView
        var eventLocation: TextView
        var actionLayout: View
        var snoozedUntil: TextView
        var change: Button
        var dismiss: Button
        var color: ColorDrawable

        init {
            eventHolder = itemView.find<RelativeLayout>(R.id.card_view_main_holder)
            eventTitle = itemView.find<TextView>(R.id.card_view_event_name)
            eventTitleLayout = itemView.find<RelativeLayout>(R.id.card_view_event_title_layout)
            eventDate = itemView.find<TextView>(R.id.card_view_event_date)
            eventTime = itemView.find<TextView>(R.id.card_view_event_time)
            eventLocation = itemView.find<TextView>(R.id.card_view_location)
            actionLayout = itemView.find<View>(R.id.card_view_event_action_layout)
            snoozedUntil = itemView.find<TextView>(R.id.card_view_snoozed_until)
            change = itemView.find<Button>(R.id.card_view_button_reschedule)
            dismiss = itemView.find<Button>(R.id.card_view_button_dismiss)

            color = ColorDrawable(0)

            eventHolder.setOnClickListener {
                callback.onItemClick(itemView, adapterPosition, eventId);
            };

            dismiss.setOnClickListener {
                callback.onItemDismiss(itemView, adapterPosition, eventId);
            }

            change.setOnClickListener {
                callback.onItemSnooze(itemView, adapterPosition, eventId);
            }

            eventLocation.setOnClickListener {
                callback.onItemLocation(itemView, adapterPosition, eventId);
            }

            val dateTimeLisneter = View.OnClickListener {
                callback.onItemDateTime(itemView, adapterPosition, eventId);
            }

            eventDate.setOnClickListener(dateTimeLisneter)
            eventTime.setOnClickListener(dateTimeLisneter)

        }
    }


    private var events = arrayOf<EventAlertRecord>();

    private val primaryColor: Int
    private val changeString: String
    private val snoozeString: String

    init {
        primaryColor = context.resources.getColor(R.color.primary)
        changeString = context.resources.getString(R.string.card_view_btn_change);
        snoozeString = context.resources.getString(R.string.card_view_btn_snooze);
    }

    override fun onBindViewHolder(holder: ViewHolder?, position: Int) {
        if (position >= 0 && position < events.size && holder != null) {
            val event = events[position]

            holder.eventTitle.text = event.title

            val (date, time) = event.formatTime(context)

            holder.eventDate.text = date
            holder.eventTime.text = time
            holder.eventLocation.text = event.location

            if (event.location != "")
                holder.eventLocation.visibility = View.VISIBLE;
            else
                holder.eventLocation.visibility = View.GONE;

            if (event.snoozedUntil != 0L) {
                holder.snoozedUntil.text =
                        context.resources.getString(R.string.snoozed_until_string) + " " +
                                event.formatSnoozedUntil(context);

                holder.snoozedUntil.visibility = View.VISIBLE;
                holder.change.text = changeString
            } else {
                holder.snoozedUntil.text = "";
                holder.snoozedUntil.visibility = View.GONE;
                holder.change.text = snoozeString
            }

            holder.eventId = event.eventId;

            holder.color.color = if (event.color != 0) event.color.adjustCalendarColor() else primaryColor
            holder.eventTitleLayout.background = holder.color
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): ViewHolder? {
        val view = LayoutInflater.from(parent?.context).inflate(cardVewResourceId, parent, false);
        return ViewHolder(view);
    }

    override fun getItemCount(): Int = events.size

    val hasActiveEvents: Boolean
        get() = events.any { it.snoozedUntil == 0L }

    fun setEventsToDisplay(newEvents: Array<EventAlertRecord>)
        = synchronized(this) {
            events = newEvents;
            notifyDataSetChanged();
        }

    fun getEventAtPosition(position: Int): EventAlertRecord?
        = synchronized(this) {
            if (position >= 0 && position < events.size)
                events[position];
            else
                null
        }

    fun getEventAtPosition(position: Int, expectedEventId: Long): EventAlertRecord?
        = synchronized(this) {
            if (position >= 0 && position < events.size && events[position].eventId == expectedEventId)
                events[position];
            else
                null
        }


    fun removeAt(position: Int)
        = synchronized(this) {
            events = events.filterIndexed { idx, ev -> idx != position }.toTypedArray()
            notifyItemRemoved(position)
        }
}
