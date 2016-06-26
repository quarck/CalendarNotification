/*
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
package com.github.quarck.calnotify.prefs

import android.app.Activity
import android.app.Fragment
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
import android.widget.*
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.calendar.CalendarRecord
import com.github.quarck.calnotify.calendar.CalendarProvider
import com.github.quarck.calnotify.logs.Logger
import com.github.quarck.calnotify.utils.background
import com.github.quarck.calnotify.utils.find

enum class CalendarListEntryType {Header, Calendar, Divider }

class CalendarListEntry(
    val type: CalendarListEntryType,
    val headerTitle: String? = null,
    val calendar: CalendarRecord? = null,
    var isHandled: Boolean = true
)


class CalendarListAdapter(val context: Context, var entries: Array<CalendarListEntry>)
: RecyclerView.Adapter<CalendarListAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View)
    : RecyclerView.ViewHolder(itemView) {

        var entry: CalendarListEntry? = null
        lateinit var view: LinearLayout
        lateinit var calendarOwner: TextView
        lateinit var checkboxCalendarName: CheckBox
        lateinit var colorView: View
        lateinit var calendarEntryLayout: LinearLayout
        lateinit var spacingView: View

        init {
            view = itemView.find<LinearLayout>(R.id.linearLyaoutCalendarView)

            calendarOwner = view.find<TextView>(R.id.textViewCalendarOwner)
            checkboxCalendarName = view.find<CheckBox>(R.id.checkBoxCalendarSelection)
            colorView = view.find<View>(R.id.viewCalendarColor)
            calendarEntryLayout = view.find<LinearLayout>(R.id.linearLayoutCalendarEntry)
            spacingView = view.find<View>(R.id.viewCalendarsSpacing)

            checkboxCalendarName.setOnClickListener {
                view ->
                val action = onItemChanged
                val ent = entry

                if (ent != null && ent.calendar != null) {
                    ent.isHandled = checkboxCalendarName.isChecked
                    if (action != null)
                        action(view, ent.calendar.calendarId, ent.isHandled)
                }
            }
        }
    }

    var onItemChanged: ((View, Long, Boolean) -> Unit)? = null;

    override fun onBindViewHolder(holder: ViewHolder?, position: Int) {

        if (position >= 0 && position < entries.size && holder != null) {

            val entry = entries[position]

            holder.entry = entry

            when (entry.type) {
                CalendarListEntryType.Header -> {
                    holder.calendarOwner.text = entry.headerTitle
                    holder.calendarOwner.visibility = View.VISIBLE
                    holder.calendarEntryLayout.visibility = View.GONE
                    holder.spacingView.visibility = View.GONE
                }

                CalendarListEntryType.Calendar -> {
                    holder.checkboxCalendarName.text = entry.calendar?.name ?: ""
                    holder.calendarOwner.visibility = View.GONE
                    holder.calendarEntryLayout.visibility = View.VISIBLE
                    holder.colorView.background = ColorDrawable(entry.calendar?.color ?: Consts.DEFAULT_CALENDAR_EVENT_COLOR)
                    holder.checkboxCalendarName.isChecked = entry.isHandled
                    holder.spacingView.visibility = View.GONE
                }

                CalendarListEntryType.Divider -> {
                    holder.calendarEntryLayout.visibility = View.GONE
                    holder.calendarOwner.visibility = View.GONE
                    holder.spacingView.visibility = View.VISIBLE
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


class CalendarsFragment: Fragment() {

    private lateinit var adapter: CalendarListAdapter
    private lateinit var staggeredLayoutManager: StaggeredGridLayoutManager
    private lateinit var recyclerView: RecyclerView

    private lateinit var settings: Settings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        logger.debug("onCreate")

        settings = Settings(activity)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {

        logger.debug("onCreateView")

        val view = inflater.inflate(R.layout.activity_calendars, container, false);

        adapter = CalendarListAdapter(activity, arrayOf<CalendarListEntry>())

        adapter.onItemChanged = {
            view, calendarId, isEnabled ->
            logger.debug("Item has changed: $calendarId $isEnabled");

            settings.setCalendarIsHandled(calendarId, isEnabled)
        }

        staggeredLayoutManager = StaggeredGridLayoutManager(1, StaggeredGridLayoutManager.VERTICAL)
        recyclerView = view.find<RecyclerView>(R.id.list_calendars)
        recyclerView.layoutManager = staggeredLayoutManager;
        recyclerView.adapter = adapter;

        return view
    }



    override fun onResume() {
        super.onResume()

        background {
            // load the data here
            val calendars = CalendarProvider.getCalendars(activity).toTypedArray()

            val entries = mutableListOf<CalendarListEntry>()

            // Arrange entries by owner calendar
            for ((owner, type) in calendars.map { Pair(it.owner, it.accountType) }.toSet()) {

                // Add group title
                entries.add(CalendarListEntry(type = CalendarListEntryType.Header, headerTitle = owner))

                // Add all the calendars for this owner
                entries.addAll(
                    calendars
                        .filter { it.owner == owner && it.accountType == type }
                        .sortedBy { it.calendarId }
                        .map {
                            CalendarListEntry(
                                type = CalendarListEntryType.Calendar,
                                calendar = it,
                                isHandled = settings.getCalendarIsHandled(it.calendarId))
                        })

                // Add a divider
                entries.add(CalendarListEntry(type = CalendarListEntryType.Divider))
            }

            // remove last divider
            if (entries[entries.size - 1].type == CalendarListEntryType.Divider)
                entries.removeAt(entries.size - 1)

            val entriesFinal = entries.toTypedArray()

            activity.runOnUiThread {
                // update activity finally
                adapter.entries = entriesFinal
                adapter.notifyDataSetChanged();
            }
        }
    }

    companion object {
        val logger = Logger("CalendarsActivity")
    }
}*/
