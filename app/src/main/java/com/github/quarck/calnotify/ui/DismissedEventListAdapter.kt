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
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.helper.ItemTouchHelper
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.RelativeLayout
import android.widget.TextView
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.dismissedeventsstorage.DismissedEventAlertRecord
import com.github.quarck.calnotify.dismissedeventsstorage.EventDismissType
import com.github.quarck.calnotify.textutils.EventFormatter
import com.github.quarck.calnotify.utils.adjustCalendarColor
import com.github.quarck.calnotify.utils.find

private fun dateToStr(ctx: Context, time: Long) =
    DateUtils.formatDateTime(ctx, time, DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_DATE)

fun DismissedEventAlertRecord.formatReason(ctx: Context): String =
    when (this.dismissType) {
        EventDismissType.ManuallyDismissedFromNotification ->
            String.format(ctx.resources.getString(R.string.dismissed_from_notification), dateToStr(ctx, this.dismissTime))

        EventDismissType.ManuallyDismissedFromActivity ->
            String.format(ctx.resources.getString(R.string.dismissed_from_the_app), dateToStr(ctx, this.dismissTime))

        EventDismissType.AutoDismissedDueToCalendarMove ->
            String.format(ctx.resources.getString(R.string.event_moved_new_time), dateToStr(ctx, this.event.instanceStartTime))

        EventDismissType.EventMovedUsingApp ->
            String.format(ctx.resources.getString(R.string.event_moved_new_time), dateToStr(ctx, this.event.instanceStartTime))

        else ->
            String.format(ctx.resources.getString(R.string.dismissed_general), dateToStr(ctx, this.dismissTime))
    }

interface DismissedEventListCallback {
    fun onItemRestore(v: View, position: Int, eventId: Long): Unit
}

@Suppress("DEPRECATION")
class DismissedEventListAdapter(
        val context: Context,
        val cardVewResourceId: Int,
        val callback: DismissedEventListCallback)

: RecyclerView.Adapter<DismissedEventListAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View)
    : RecyclerView.ViewHolder(itemView) {
        var eventId: Long = 0;

        var eventHolder: RelativeLayout?
        var eventTitleText: TextView
        var eventTitleLayout: RelativeLayout?
        var eventDateText: TextView
        var eventTimeText: TextView

        var snoozedUntilText: TextView?
        val compactViewCalendarColor: View?

        val compactViewContentLayout: RelativeLayout?
        var undoLayout: RelativeLayout?

        var calendarColor: ColorDrawable

        init {
            eventHolder = itemView.find<RelativeLayout>(R.id.card_view_main_holder)
            eventTitleText = itemView.find<TextView>(R.id.card_view_event_name)
            eventTitleLayout = itemView.find<RelativeLayout?>(R.id.card_view_event_title_layout)

            eventDateText = itemView.find<TextView>(R.id.card_view_event_date)
            eventTimeText = itemView.find<TextView>(R.id.card_view_event_time)
            snoozedUntilText = itemView.find<TextView>(R.id.card_view_snoozed_until)

            undoLayout = itemView.find<RelativeLayout?>(R.id.event_card_undo_layout)

            compactViewContentLayout = itemView.find<RelativeLayout?>(R.id.compact_view_content_layout)
            compactViewCalendarColor = itemView.find<View?>(R.id.compact_view_calendar_color)

            calendarColor = ColorDrawable(0)

/*
            val itemClickListener = View.OnClickListener {
                callback.onItemClick(itemView, adapterPosition, eventId);
            }
*/

/*            eventHolder?.setOnClickListener(itemClickListener)
            eventLocatoinText?.setOnClickListener(itemClickListener)
            eventDateText.setOnClickListener(itemClickListener)
            eventTimeText.setOnClickListener(itemClickListener)

            dismissButton?.setOnClickListener {
                callback.onItemDismiss(itemView, adapterPosition, eventId);
            }

            snoozeButton?.setOnClickListener {
                callback.onItemSnooze(itemView, adapterPosition, eventId);
            }*/
        }
    }

    private var events = arrayOf<DismissedEventAlertRecord>();

    private var _recyclerView: RecyclerView? = null
    var recyclerView: RecyclerView?
        get() = _recyclerView
        set(value) {
            _recyclerView = value
            onRecycleViewRegistered(_recyclerView)
        }

    private val primaryColor: Int
    private val changeString: String
    private val snoozeString: String

    private val eventFormatter = EventFormatter(context)

    init {
        primaryColor = context.resources.getColor(R.color.primary)
        changeString = context.resources.getString(R.string.card_view_btn_change);
        snoozeString = context.resources.getString(R.string.card_view_btn_snooze);
    }

    private fun onRecycleViewRegistered(_recyclerView: RecyclerView?) {
        setUpItemTouchHelper(_recyclerView, context)
    }

    private fun setUpItemTouchHelper(_recyclerView: RecyclerView?, context: Context) {
/*

        val itemTouchCallback =
                object: ItemTouchHelper.Callback() {

                    internal val lightweightSwipe = Settings(context).lightweightSwipe
                    internal val escapeVelocityMultiplier = if (lightweightSwipe) 2.0f else 5.0f

                    internal val background = ColorDrawable(context.resources.getColor(R.color.material_red))
                    internal var xMark = context.resources.getDrawable(R.drawable.ic_clear_white_24dp)
                    internal var xMarkMargin = context.resources.getDimension(R.dimen.ic_clear_margin).toInt()

                    init {
                        xMark.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_ATOP)
                    }

                    override fun getMovementFlags(recyclerView: RecyclerView?, viewHolder: RecyclerView.ViewHolder?): Int {
                        val position = viewHolder!!.adapterPosition
                        val adapter = recyclerView?.adapter as EventListAdapter?

                        if (adapter == null)
                            return 0

                        if (adapter.isPendingRemoval(position))
                            return 0

                        return  makeFlag(ItemTouchHelper.ACTION_STATE_IDLE, ItemTouchHelper.RIGHT or ItemTouchHelper.LEFT) or
                                makeFlag(ItemTouchHelper.ACTION_STATE_SWIPE, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT)
                    }

                    override fun onMove(recyclerView: RecyclerView?, viewHolder: RecyclerView.ViewHolder?, target: RecyclerView.ViewHolder?): Boolean {
                        return false
                    }

                    override fun onSwiped(viewHolder: RecyclerView.ViewHolder?, direction: Int) {
                        val swipedPosition = viewHolder?.adapterPosition
                        if (swipedPosition != null) {
                            _recyclerView?.itemAnimator?.changeDuration = 0;

                            val event = getEventAtPosition(swipedPosition)

                            if (event != null) {
                                removeWithUndo(event)
                                callback.onItemRemoved(event)
                            }
                        }
                    }

                    override fun isLongPressDragEnabled() = false

                    override fun isItemViewSwipeEnabled() = true

                    */
/* From documentation:
                     * Defines the minimum velocity which will be considered as a swipe action by the user.
                     * You can increase this value to make it harder to swipe or decrease it to make
                     * it easier. *//*

                    override fun getSwipeEscapeVelocity(defaultValue: Float) = defaultValue * escapeVelocityMultiplier

                    */
/* From documentation:
                     * Defines the maximum velocity ItemTouchHelper will ever calculate for pointer
                     * movements.
                     * If you increase the value, it will be easier for the user to swipe diagonally and
                     * if you decrease the value, user will need to make a rather straight finger movement
                     * to trigger a swipe.*//*

                    override fun getSwipeVelocityThreshold(defaultValue: Float) = defaultValue / 3.0f

                    */
/* From documentation:
                     * Default value is .5f, which means, to swipe a View, user must move the View at
                     * least half of RecyclerView's width or height, depending on the swipe direction. *//*

//                override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder) = 0.5f

                    override fun onChildDraw(
                            c: Canvas, recyclerView: RecyclerView,
                            viewHolder: RecyclerView.ViewHolder,
                            dX: Float, dY: Float,
                            actionState: Int, isCurrentlyActive: Boolean) {

                        val itemView = viewHolder.itemView

                        if (viewHolder.adapterPosition == -1)
                            return

                        if (dX < 0)
                            background.setBounds(itemView.right + dX.toInt(), itemView.top, itemView.right, itemView.bottom)
                        else
                            background.setBounds(itemView.left, itemView.top, itemView.left + dX.toInt(), itemView.bottom)

                        background.draw(c)

                        val itemHeight = itemView.bottom - itemView.top
                        val intrinsicWidth = xMark.intrinsicWidth
                        val intrinsicHeight = xMark.intrinsicWidth


                        if (dX < 0) {
                            val xMarkLeft = itemView.right - xMarkMargin - intrinsicWidth
                            val xMarkRight = itemView.right - xMarkMargin
                            val xMarkTop = itemView.top + (itemHeight - intrinsicHeight) / 2
                            val xMarkBottom = xMarkTop + intrinsicHeight
                            xMark.setBounds(xMarkLeft, xMarkTop, xMarkRight, xMarkBottom)
                        } else {
                            val xMarkLeft = itemView.left + xMarkMargin
                            val xMarkRight = itemView.left + xMarkMargin + intrinsicWidth
                            val xMarkTop = itemView.top + (itemHeight - intrinsicHeight) / 2
                            val xMarkBottom = xMarkTop + intrinsicHeight
                            xMark.setBounds(xMarkLeft, xMarkTop, xMarkRight, xMarkBottom)
                        }

                        xMark.draw(c)

                        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
                    }
                }

        if (_recyclerView != null) {
            val touchHelper = ItemTouchHelper(itemTouchCallback)
            touchHelper.attachToRecyclerView(_recyclerView)
        }
*/
    }

    override fun onBindViewHolder(holder: ViewHolder?, position: Int) {
        //
        if (position < 0 || position >= events.size || holder == null)
            return

        val event = events[position]

         if (true){
             holder.eventId = event.event.eventId;

             holder.eventTitleText.text = event.event.title

             holder.undoLayout?.visibility = View.GONE
             holder.compactViewContentLayout?.visibility = View.VISIBLE

             val time = eventFormatter.formatDateTimeOneLine(event.event)
             holder.eventDateText.text = time
             holder.eventTimeText.text = ""

             holder.snoozedUntilText?.text = event.formatReason(context)
             holder.snoozedUntilText?.visibility = View.VISIBLE;

             holder.calendarColor.color = if (event.event.color != 0) event.event.color.adjustCalendarColor() else primaryColor
             holder.compactViewCalendarColor?.background = holder.calendarColor
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): ViewHolder? {
        val view = LayoutInflater.from(parent?.context).inflate(cardVewResourceId, parent, false);
        return ViewHolder(view);
    }

    override fun getItemCount(): Int = events.size

    fun setEventsToDisplay(newEvents: Array<DismissedEventAlertRecord>)
            = synchronized(this) {
        events = newEvents;
        notifyDataSetChanged();
    }

    fun getEventAtPosition(position: Int, expectedEventId: Long): DismissedEventAlertRecord?
            = synchronized(this) {
        if (position >= 0 && position < events.size && events[position].event.eventId == expectedEventId)
            events[position];
        else
            null
    }

    private fun getEventAtPosition(position: Int): DismissedEventAlertRecord?
            = synchronized(this) {
        if (position >= 0 && position < events.size)
            events[position];
        else
            null
    }


    fun removeEvent(event: DismissedEventAlertRecord)
            = synchronized(this) {
        val idx = events.indexOf(event)
        events = events.filter { ev -> ev != event }.toTypedArray()
        notifyItemRemoved(idx)
    }
}
