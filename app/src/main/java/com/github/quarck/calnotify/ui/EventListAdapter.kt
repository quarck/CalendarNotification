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
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.helper.ItemTouchHelper
import android.support.v7.widget.helper.ItemTouchHelper.*
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.RelativeLayout
import android.widget.TextView
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.calendar.displayedEndTime
import com.github.quarck.calnotify.calendar.displayedStartTime
import com.github.quarck.calnotify.textutils.formatSnoozedUntil
import com.github.quarck.calnotify.textutils.formatTime
import com.github.quarck.calnotify.textutils.dateRangeOneLine
import com.github.quarck.calnotify.utils.*
import java.util.*

interface EventListCallback {
    fun onItemClick(v: View, position: Int, eventId: Long): Unit
    fun onItemDismiss(v: View, position: Int, eventId: Long): Unit
    fun onItemSnooze(v: View, position: Int, eventId: Long): Unit
//    fun onItemLocation(v: View, position: Int, eventId: Long): Unit
//    fun onItemDateTime(v: View, position: Int, eventId: Long): Unit
    fun onItemRemoved(event: EventAlertRecord)
}

@Suppress("DEPRECATION")
class EventListAdapter(
    val context: Context,
    val useCompactView: Boolean,
    val cardVewResourceId: Int,
    val callback: EventListCallback)

: RecyclerView.Adapter<EventListAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View)
    : RecyclerView.ViewHolder(itemView) {
        var eventId: Long = 0;

        var eventHolder: RelativeLayout?
        var eventTitleText: TextView
        var eventTitleLayout: RelativeLayout?
        var eventDateText: TextView
        var eventTimeText: TextView
        var eventLocatoinText: TextView?
        var snoozedUntilText: TextView?
        val compactViewCalendarColor: View?

        val compactViewContentLayout: RelativeLayout?
        var undoLayout: RelativeLayout?

        var snoozeButton: Button?
        var dismissButton: Button?
        var undoButton: Button?

        var calendarColor: ColorDrawable

        init {
            eventHolder = itemView.find<RelativeLayout>(R.id.card_view_main_holder)
            eventTitleText = itemView.find<TextView>(R.id.card_view_event_name)
            eventTitleLayout = itemView.find<RelativeLayout?>(R.id.card_view_event_title_layout)

            eventDateText = itemView.find<TextView>(R.id.card_view_event_date)
            eventTimeText = itemView.find<TextView>(R.id.card_view_event_time)
            snoozedUntilText = itemView.find<TextView>(R.id.card_view_snoozed_until)

            eventLocatoinText = itemView.find<TextView?>(R.id.card_view_location)

            snoozeButton = itemView.find<Button?>(R.id.card_view_button_reschedule)
            dismissButton = itemView.find<Button?>(R.id.card_view_button_dismiss)

            undoLayout = itemView.find<RelativeLayout?>(R.id.event_card_undo_layout)

            compactViewContentLayout = itemView.find<RelativeLayout?>(R.id.compact_view_content_layout)
            compactViewCalendarColor = itemView.find<View?>(R.id.compact_view_calendar_color)

            undoButton = itemView.find<Button?>(R.id.card_view_button_undo)

            calendarColor = ColorDrawable(0)

            val itemClickListener = View.OnClickListener {
                callback.onItemClick(itemView, adapterPosition, eventId);
            }

            eventHolder?.setOnClickListener(itemClickListener)
            eventLocatoinText?.setOnClickListener(itemClickListener)
            eventDateText.setOnClickListener(itemClickListener)
            eventTimeText.setOnClickListener(itemClickListener)

            dismissButton?.setOnClickListener {
                callback.onItemDismiss(itemView, adapterPosition, eventId);
            }

            snoozeButton?.setOnClickListener {
                callback.onItemSnooze(itemView, adapterPosition, eventId);
            }
        }
    }

    private var events = arrayOf<EventAlertRecord>();
    private var eventsPendingRemoval = mutableListOf<EventAlertRecord>()

    var recyclerView: RecyclerView? = null

    private val primaryColor: Int
    private val changeString: String
    private val snoozeString: String

    private val handler = Handler()
    private val pendingRunnables = mutableMapOf<EventAlertRecord, Runnable>()

    init {
        primaryColor = context.resources.getColor(R.color.primary)
        changeString = context.resources.getString(R.string.card_view_btn_change);
        snoozeString = context.resources.getString(R.string.card_view_btn_snooze);
    }

    override fun onBindViewHolder(holder: ViewHolder?, position: Int) {
        if (position >= 0 && position < events.size && holder != null) {
            val event = events[position]

            if (eventsPendingRemoval.contains(event)) {
                // we need to show the "undo" state of the row
                holder.undoLayout?.visibility = View.VISIBLE
                holder.compactViewContentLayout?.visibility = View.GONE

                holder.undoButton?.setOnClickListener {
                    v ->
                    val runnable = pendingRunnables[event];
                    pendingRunnables.remove(event);
                    if (runnable != null)
                        handler.removeCallbacks(runnable);

                    eventsPendingRemoval.remove(event);

                    notifyItemChanged(events.indexOf(event));
                }

            } else {
                holder.eventId = event.eventId;

                holder.eventTitleText.text = event.title

                if (useCompactView) {
                    holder.undoLayout?.visibility = View.GONE
                    holder.compactViewContentLayout?.visibility = View.VISIBLE

                    val time = event.dateRangeOneLine(context)
                    holder.eventDateText.text = time
                    holder.eventTimeText.text = ""

                } else {

                    val (date, time) = event.formatTime(context)

                    holder.eventDateText.text = date
                    holder.eventTimeText.text = time

                    holder.eventLocatoinText?.text = event.location

                    if (event.location != "")
                        holder.eventLocatoinText?.visibility = View.VISIBLE;
                    else
                        holder.eventLocatoinText?.visibility = View.GONE;
                }

                if (event.snoozedUntil != 0L) {
                    holder.snoozedUntilText?.text =
                        context.resources.getString(R.string.snoozed_until_string) + " " +
                            event.formatSnoozedUntil(context);

                    holder.snoozedUntilText?.visibility = View.VISIBLE;
                    holder.snoozeButton?.text = changeString
                } else {
                    holder.snoozedUntilText?.text = "";
                    holder.snoozedUntilText?.visibility = View.GONE;
                    holder.snoozeButton?.text = snoozeString
                }

                holder.calendarColor.color = if (event.color != 0) event.color.adjustCalendarColor() else primaryColor
                if (useCompactView)
                    holder.compactViewCalendarColor?.background = holder.calendarColor
                else
                    holder.eventTitleLayout?.background = holder.calendarColor
            }
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

    private fun remove(position: Int) {

        var event: EventAlertRecord? = null

        synchronized(this) {
            if (position >= 0 && position < events.size)
                event = events[position]
            events = events.filterIndexed { idx, ev -> idx != position }.toTypedArray()
            notifyItemRemoved(position)
        }

        if (event != null)
            callback.onItemRemoved(event!!)
    }

    private fun remove(event: EventAlertRecord) {

        synchronized(this) {
            val idx = events.indexOf(event)
            events = events.filter { ev -> ev != event }.toTypedArray()
            notifyItemRemoved(idx)
        }

        callback.onItemRemoved(event)
    }

    fun pendingRemoval(position: Int) {
        val event = events[position]

        if (!eventsPendingRemoval.contains(event)) {
            eventsPendingRemoval.add(event);

            notifyItemChanged(position);

            val runnable = Runnable() { remove(event) };

            handler.postDelayed(runnable, Consts.UNDO_TIMEOUT);
            pendingRunnables.put(event, runnable);
        }
    }

    fun isPendingRemoval(position: Int): Boolean {
        val event = events[position]
        return eventsPendingRemoval.contains(event)
    }
}
