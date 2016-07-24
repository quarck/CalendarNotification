package com.github.quarck.calnotify.ui

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.design.widget.CoordinatorLayout
import android.support.design.widget.Snackbar
import android.support.v4.widget.SwipeRefreshLayout
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.StaggeredGridLayoutManager
import android.support.v7.widget.Toolbar
import android.text.format.DateUtils
import android.view.View
import android.widget.RelativeLayout
import android.widget.TextView
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.dismissedeventsstorage.DismissedEventAlertRecord
import com.github.quarck.calnotify.dismissedeventsstorage.DismissedEventsStorage
import com.github.quarck.calnotify.dismissedeventsstorage.EventDismissType
import com.github.quarck.calnotify.eventsstorage.EventsStorage
import com.github.quarck.calnotify.logs.Logger
import com.github.quarck.calnotify.quiethours.QuietHoursManager
import com.github.quarck.calnotify.utils.background
import com.github.quarck.calnotify.utils.find
import java.util.*

class DismissedEventsActivity : AppCompatActivity(), DismissedEventListCallback {

    private val settings: Settings by lazy { Settings(this) }

    private lateinit var staggeredLayoutManager: StaggeredGridLayoutManager
    private lateinit var recyclerView: RecyclerView

    private lateinit var adapter: DismissedEventListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dismissed_events)

        setSupportActionBar(find<Toolbar?>(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        adapter =
                DismissedEventListAdapter(
                        this,
                        R.layout.event_card_compact,
                        this)

        staggeredLayoutManager = StaggeredGridLayoutManager(1, StaggeredGridLayoutManager.VERTICAL)
        recyclerView = find<RecyclerView>(R.id.list_events)
        recyclerView.layoutManager = staggeredLayoutManager;
        recyclerView.adapter = adapter;
        adapter.recyclerView = recyclerView

    }

    public override fun onResume() {
        logger.debug("onResume")
        super.onResume()
        reloadData()
    }

    private fun reloadData() {
        background {
            val events =
                DismissedEventsStorage(this).use {
                    db -> db.events.sortedByDescending { it.dismissTime }.toTypedArray()
                }
            runOnUiThread {
                adapter.setEventsToDisplay(events);
            }
        }
    }

    override fun onItemRestore(v: View, position: Int, eventId: Long) {
    }

    companion object {
        val logger = Logger("DismissedEventsActivity")
    }
}
