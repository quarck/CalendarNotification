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

package com.github.quarck.calnotify.UI

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.support.v7.widget.RecyclerView
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.github.quarck.calnotify.EventsStorage.EventRecord
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.Utils.adjustCalendarColor
import com.github.quarck.calnotify.Utils.find
import com.github.quarck.calnotify.EventsStorage.formatSnoozedUntil
import com.github.quarck.calnotify.EventsStorage.formatTime

class EventListAdapter(context: Context, var events: Array<EventRecord>)
: RecyclerView.Adapter<EventListAdapter.ViewHolder>()
{
	inner class ViewHolder(itemView: View)
		: RecyclerView.ViewHolder(itemView)
	{
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

		init
		{
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
				var action = onItemClick;
				if (action != null)
					action(itemView, adapterPosition, eventId);
			};

			dismiss.setOnClickListener {
				var action = onItemDismiss;
				if (action != null)
					action(itemView, adapterPosition, eventId);
			}

			change.setOnClickListener {
				var action = onItemReschedule;
				if (action != null)
				{
					action(itemView, adapterPosition, eventId);
				}
			}
		}
	}

	internal var context: Context

	var onItemClick: ((View, Int, Long) -> Unit)? = null;
	var onItemDismiss: ((View, Int, Long) -> Unit)? = null;
	var onItemReschedule: ((View, Int, Long) -> Unit)? = null;

	private val primaryColor: Int
	private val changeString: String
	private val snoozeString: String

	init
	{
		this.context = context
		primaryColor = context.resources.getColor(R.color.primary)
		changeString = context.resources.getString(R.string.card_view_btn_change);
		snoozeString = context.resources.getString(R.string.card_view_btn_snooze);
	}

	override fun onBindViewHolder(holder: ViewHolder?, position: Int)
	{
		if (position >= 0 && position < events.size && holder != null)
		{
			var event = events[position]

			holder.eventTitle?.text = event.title

			var (date, time) = event.formatTime(context)

			holder.eventDate.text = date
			holder.eventTime.text = time
			holder.eventLocation.text = event.location

			if (event.location != "")
				holder.eventLocation.visibility = View.VISIBLE;
			else
				holder.eventLocation.visibility = View.GONE;

			if (event.snoozedUntil != 0L)
			{
				holder.snoozedUntil.text =
					context.resources.getString(R.string.snoozed_until_string) + " " +
						event.formatSnoozedUntil(context);

				holder.snoozedUntil.visibility = View.VISIBLE;
				holder.change.text = changeString
			}
			else
			{
				holder.snoozedUntil.text = "";
				holder.snoozedUntil.visibility = View.GONE;
				holder.change.text = snoozeString
			}

			holder.eventId = event.eventId;

			holder.color.color = if (event.color != 0) event.color.adjustCalendarColor() else primaryColor
			holder.eventTitleLayout.background  = holder.color
		}
	}

	override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): ViewHolder?
	{
		var view = LayoutInflater.from(parent?.context).inflate(R.layout.event_card, parent, false);
		return ViewHolder(view);
	}

	override fun getItemCount(): Int
	{
		return events.size;
	}
}
