package com.github.quarck.calnotify

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.support.v7.widget.RecyclerView
import android.text.TextUtils
import android.util.Log
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
			eventHolder = itemView.findViewById(R.id.card_view_main_holder) as RelativeLayout
			eventTitle = itemView.findViewById(R.id.card_view_event_name) as TextView
			eventTitleLayout = itemView.findViewById(R.id.card_view_event_title_layout) as RelativeLayout
			eventDate = itemView.findViewById(R.id.card_view_event_date) as TextView
			eventTime = itemView.findViewById(R.id.card_view_event_time) as TextView
			eventLocation = itemView.findViewById(R.id.card_view_location) as TextView
			actionLayout = itemView.findViewById(R.id.card_view_event_action_layout) as View
			snoozedUntil = itemView.findViewById(R.id.card_view_snoozed_until) as TextView
			change = itemView.findViewById(R.id.card_view_button_reschedule) as Button
			dismiss = itemView.findViewById(R.id.card_view_button_dismiss) as Button
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

	public var onItemClick: ((View, Int, Long) -> Unit)? = null;
	public var onItemDismiss: ((View, Int, Long) -> Unit)? = null;
	public var onItemReschedule: ((View, Int, Long) -> Unit)? = null;

	private val primaryColor: Int

	init
	{
		this.context = context
		primaryColor = context.resources.getColor(R.color.primary)
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

				holder?.change?.text = context.resources.getString(R.string.card_view_btn_change);
			}
			else
			{
				holder?.snoozedUntil?.text = "";
				holder?.snoozedUntil?.visibility = View.GONE;

				holder?.change?.text = context.resources.getString(R.string.card_view_btn_snooze)
			}

			holder?.eventId = event.eventId;

			holder?.color?.color = if (event.color != 0) event.color else primaryColor
			holder?.eventTitleLayout?.background  = holder?.color

			Log.d("XXXX", "color= ${event.color}")
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