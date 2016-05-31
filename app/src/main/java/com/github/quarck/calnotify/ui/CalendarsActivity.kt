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

import android.app.Activity
import android.app.ListActivity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.StaggeredGridLayoutManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.calendar.CalendarRecord
import com.github.quarck.calnotify.calendar.CalendarUtils
import com.github.quarck.calnotify.logs.Logger
import com.github.quarck.calnotify.utils.background
import com.github.quarck.calnotify.utils.find

enum class CalendarListEntryType {Header, Calendar, Divider }

class CalendarListEntry(val type: CalendarListEntryType, val headerTitle: String? = null, val calendar: CalendarRecord? = null)
{
}

class CalendarListAdapter(val context: Context, var entries: Array<CalendarListEntry>)
: RecyclerView.Adapter<CalendarListAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View)
    : RecyclerView.ViewHolder(itemView) {

        var calendarId: Long? = null
        lateinit var view: LinearLayout
        lateinit var calendarOwner: TextView
        lateinit var checkboxCalendarName: CheckBox
        lateinit var colorView: View
        lateinit var calendarEntryLayout: LinearLayout

        init {
            view = itemView.find<LinearLayout>(R.id.linearLyaoutCalendarView)

            calendarOwner = view.find<TextView>(R.id.textViewCalendarOwner)
            checkboxCalendarName = view.find<CheckBox>(R.id.checkBoxCalendarSelection)
            colorView = view.find<View>(R.id.viewCalendarColor)
            calendarEntryLayout = view.find<LinearLayout>(R.id.linearLayoutCalendarEntry)

            checkboxCalendarName.setOnClickListener {
                view ->
                val action = onItemChanged
                val calId = calendarId

                if (action != null && calId != null)
                    action(view, calId, checkboxCalendarName.isChecked)
            }
        }
    }

    var onItemChanged: ((View, Long, Boolean) -> Unit)? = null;

    override fun onBindViewHolder(holder: ViewHolder?, position: Int) {

        if (position >= 0 && position < entries.size && holder != null) {

            val entry = entries[position]

            when (entry.type) {
                CalendarListEntryType.Header -> {
                    holder.calendarId = null
                    holder.calendarOwner.text = entry.headerTitle
                    holder.calendarOwner.visibility = View.VISIBLE
                    holder.calendarEntryLayout.visibility = View.GONE
                }

                CalendarListEntryType.Calendar -> {
                    holder.calendarId = entry.calendar?.calendarId ?: -1L;
                    holder.checkboxCalendarName.text = entry.calendar?.name ?: ""
                    holder.calendarOwner.visibility = View.GONE
                    holder.calendarEntryLayout.visibility = View.VISIBLE
                    holder.colorView.background = ColorDrawable(entry.calendar?.color ?: Consts.DEFAULT_CALENDAR_EVENT_COLOR)
                }

                CalendarListEntryType.Divider -> {
                    holder.calendarId = null
                    holder.calendarEntryLayout.visibility = View.GONE
                    holder.calendarOwner.visibility = View.GONE
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): ViewHolder? {
        val view = LayoutInflater.from(parent?.context).inflate(R.layout.calendar_view, parent, false);
        return ViewHolder(view);
    }

    override fun getItemCount(): Int {
        return entries.size;
    }
}


class CalendarsActivity: Activity() {

    private lateinit var adapter: CalendarListAdapter
    private lateinit var staggeredLayoutManager: StaggeredGridLayoutManager
    private lateinit var recyclerView: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        logger.debug("onCreate")

        setContentView(R.layout.activity_calendars)


        adapter = CalendarListAdapter(this, arrayOf<CalendarListEntry>())

        adapter.onItemChanged = {
            view, calendarId, isEnabled ->
            logger.debug("Item has changed: $calendarId $isEnabled");
        }

        staggeredLayoutManager = StaggeredGridLayoutManager(1, StaggeredGridLayoutManager.VERTICAL)
        recyclerView = find<RecyclerView>(R.id.list_calendars)
        recyclerView.layoutManager = staggeredLayoutManager;
        recyclerView.adapter = adapter;
    }

    override fun onResume() {
        super.onResume()

        background {
            // load the data here
            val calendars = CalendarUtils.getCalendars(this).toTypedArray()

            val entries = mutableListOf<CalendarListEntry>()

            for (owner in calendars.map { it.owner }.toSet()) {

                entries.add(CalendarListEntry(type= CalendarListEntryType.Header, headerTitle = owner) )

                entries.addAll(
                    calendars
                        .filter { it.owner == owner }
                        .map{ CalendarListEntry(type= CalendarListEntryType.Calendar, calendar=it)})

                entries.add(CalendarListEntry(type= CalendarListEntryType.Divider) )
            }

            val entriesFinal = entries.toTypedArray()

            runOnUiThread {
                // update activity finally
                adapter.entries = entriesFinal
                adapter.notifyDataSetChanged();
            }
        }
    }

    private fun onItemChanged(v: View, on: Boolean) {
        logger.debug("Item has changed");
    }

    companion object {
        val logger = Logger("CalendarsActivity")
    }

}