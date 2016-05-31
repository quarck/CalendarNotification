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
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.calendar.CalendarRecord
import com.github.quarck.calnotify.calendar.CalendarUtils
import com.github.quarck.calnotify.logs.Logger
import com.github.quarck.calnotify.utils.background
import com.github.quarck.calnotify.utils.find


class CalendarListAdapter(val context: Context, var calendars: Array<CalendarRecord>)
: RecyclerView.Adapter<CalendarListAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View)
    : RecyclerView.ViewHolder(itemView) {

        var calendarId: Long = -1L
        lateinit var view: LinearLayout
        lateinit var calendarOwner: TextView
        lateinit var checkboxCalendarName: CheckBox

        init {
            view = itemView.find<LinearLayout>(R.id.linearLyaoutCalendarView)

            calendarOwner = view.find<TextView>(R.id.textViewCalendarOwner)
            checkboxCalendarName = view.find<CheckBox>(R.id.checkBoxCalendarSelection)

            checkboxCalendarName.setOnClickListener {
                view ->
                val action = onItemChanged
                if (action != null)
                    action(view, calendarId, checkboxCalendarName.isChecked ?: true)
            }
        }
    }

    var onItemChanged: ((View, Long, Boolean) -> Unit)? = null;

    override fun onBindViewHolder(holder: ViewHolder?, position: Int) {

        if (position >= 0 && position < calendars.size && holder != null) {

            val calendar = calendars[position]

            holder.calendarOwner.background = ColorDrawable(calendar.color)
            holder.calendarId = calendar.calendarId
            holder.checkboxCalendarName.text = calendar.name
            holder.calendarOwner.text = calendar.owner
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): ViewHolder? {
        val view = LayoutInflater.from(parent?.context).inflate(R.layout.calendar_view, parent, false);
        return ViewHolder(view);
    }

    override fun getItemCount(): Int {
        return calendars.size;
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


        adapter = CalendarListAdapter(this, arrayOf<CalendarRecord>())

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

            runOnUiThread {
                // update activity finally
                adapter.calendars = calendars;
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