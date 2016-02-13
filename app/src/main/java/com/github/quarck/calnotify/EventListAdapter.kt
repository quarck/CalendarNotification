package com.github.quarck.calnotify

import android.content.Context
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*

class EventListAdapter(context: Context, var events: Array<EventRecord>)
: RecyclerView.Adapter<EventListAdapter.ViewHolder>()
{
	inner class ViewHolder(itemView: View)
		: RecyclerView.ViewHolder(itemView)
	{
		var eventId: Long = 0;

		var eventHolder: RelativeLayout
		var eventTitle: TextView
		var eventDate: TextView
		var eventTime: TextView
		var eventLocation: TextView
		var actionLayout: View
		var snoozedUntil: TextView
		var reschedule: Button
		var dismiss: Button

		init
		{
			eventHolder = itemView.findViewById(R.id.card_view_main_holder) as RelativeLayout
			eventTitle = itemView.findViewById(R.id.card_view_event_name) as TextView
			eventDate = itemView.findViewById(R.id.card_view_event_date) as TextView
			eventTime = itemView.findViewById(R.id.card_view_event_time) as TextView
			eventLocation = itemView.findViewById(R.id.card_view_location) as TextView
			actionLayout = itemView.findViewById(R.id.card_view_event_action_layout) as View
			snoozedUntil = itemView.findViewById(R.id.card_view_snoozed_until) as TextView
			reschedule = itemView.findViewById(R.id.card_view_button_reschedule) as Button
			dismiss = itemView.findViewById(R.id.card_view_button_dismiss) as Button

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

			reschedule.setOnClickListener {
				var action = onItemReschedule;
				if (action != null)
				{
					action(itemView, adapterPosition, eventId);
				}
			}
		}
	}

	internal var context: Context

	public var onItemClick: ((View, Int, Long) -> Unit)? = null;
	public var onItemDismiss: ((View, Int, Long) -> Unit)? = null;
	public var onItemReschedule: ((View, Int, Long) -> Unit)? = null;

	init
	{
		this.context = context
	}

	override fun onBindViewHolder(holder: ViewHolder?, position: Int)
	{
		if (position >= 0 && position < events.size)
		{
			var event = events[position]

			holder?.eventTitle?.text = event.title
			var (date, time) = event.formatTime(context)
			holder?.eventDate?.text = date
			holder?.eventTime?.text = time
			holder?.eventLocation?.text = event.location
			if (event.location != "")
				holder?.eventLocation?.visibility = View.VISIBLE;
			else
				holder?.eventLocation?.visibility = View.GONE;

			if (event.snoozedUntil != 0L)
			{
				holder?.snoozedUntil?.text =
					context.resources.getString(R.string.snoozed_until_string) +
						event.formatSnoozedUntil(context);

				holder?.snoozedUntil?.visibility = View.VISIBLE;
			}
			else
			{
				holder?.snoozedUntil?.text = "";
				holder?.snoozedUntil?.visibility = View.GONE;
			}

			holder?.eventId = event.eventId;
		}
	}

	override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): ViewHolder?
	{
		var view = LayoutInflater.from(parent?.context).inflate(R.layout.event_row, parent, false);
		return ViewHolder(view);
	}

	override fun getItemCount(): Int
	{
		return events.size;
	}
}