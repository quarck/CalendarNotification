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

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.support.design.widget.CoordinatorLayout
import android.support.design.widget.Snackbar
import android.support.v4.widget.SwipeRefreshLayout
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.StaggeredGridLayoutManager
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.text.format.DateUtils
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.RelativeLayout
import android.widget.TextView
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.app.UndoManager
import com.github.quarck.calnotify.app.UndoState
import com.github.quarck.calnotify.calendar.CalendarIntents
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.calendar.displayedStartTime
import com.github.quarck.calnotify.eventsstorage.EventsStorage
import com.github.quarck.calnotify.globalState
import com.github.quarck.calnotify.logs.Logger
import com.github.quarck.calnotify.permissions.PermissionsManager
import com.github.quarck.calnotify.quiethours.QuietHoursManager
import com.github.quarck.calnotify.utils.background
import com.github.quarck.calnotify.utils.find
import org.jetbrains.annotations.NotNull
import java.util.*

class MainActivity : AppCompatActivity(), EventListCallback {

    private val settings: Settings by lazy { Settings(this) }

    private lateinit var staggeredLayoutManager: StaggeredGridLayoutManager
    private lateinit var recyclerView: RecyclerView
    private lateinit var reloadLayout: RelativeLayout

    private lateinit var newStyleMessageLayout: View
    private lateinit var quietHoursLayout: RelativeLayout
    private lateinit var quietHoursTextView: TextView
    private lateinit var refreshLayout: SwipeRefreshLayout

    private lateinit var adapter: EventListAdapter

    private var shouldShowPowerOptimisationWarning = false

    private val svcClient by lazy { UINotifierServiceClient() }

    private var lastEventDismissalScrollPosition: Int? = null

    private var useCompactView = true

    private var shouldForceRepost = false

    private val undoDisappearSensitivity: Float by lazy  {
        resources.getDimension(R.dimen.undo_dismiss_sensitivity)
    }

    private val undoManager by lazy { UndoManager() }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        logger.debug("onCreateView")

        ApplicationController.onMainActivityCreate(this);

        setContentView(R.layout.activity_main)
        setSupportActionBar(find<Toolbar?>(R.id.toolbar))
        //supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        shouldForceRepost = (System.currentTimeMillis() - globalState.lastNotificationRePost) > Consts.MIN_FORCE_REPOST_INTERVAL

        refreshLayout = find<SwipeRefreshLayout>(R.id.cardview_refresh_layout)

        refreshLayout.setOnRefreshListener {
            reloadLayout.visibility = View.GONE;
            refreshReminderLastFired()
            reloadData()
        }

        useCompactView = settings.useCompactView

        adapter =
            EventListAdapter(
                this,
                useCompactView,
                if (useCompactView)
                    R.layout.event_card_compact
                else
                    R.layout.event_card,
                this)

        staggeredLayoutManager = StaggeredGridLayoutManager(1, StaggeredGridLayoutManager.VERTICAL)
        recyclerView = find<RecyclerView>(R.id.list_events)
        recyclerView.layoutManager = staggeredLayoutManager;
        recyclerView.adapter = adapter;
        adapter.recyclerView = recyclerView

        reloadLayout = find<RelativeLayout>(R.id.activity_main_reload_layout)

        quietHoursLayout = find<RelativeLayout>(R.id.activity_main_quiet_hours_info_layout)
        quietHoursTextView = find<TextView>(R.id.activity_main_quiet_hours)

        newStyleMessageLayout = find<View>(R.id.activity_main_new_style_message_layout)

        shouldShowPowerOptimisationWarning =
            (Build.MANUFACTURER.indexOf(Consts.SAMSUNG_KEYWORD, ignoreCase=true) != -1) &&
                !Settings(this).powerOptimisationWarningShown

        if (useCompactView && settings.showNewStyleMessage) {
            newStyleMessageLayout.visibility = View.VISIBLE
            if (settings.versionCodeFirstInstalled >= Consts.COMPACT_VIEW_DEFAULT_SINCE_VER) {
                find<Button>(R.id.activity_main_button_hate_new_style).visibility = View.GONE
                find<TextView>(R.id.text_view_style_message).text = resources.getString(R.string.usage_hints)
            }
        }
    }

    public override fun onStart() {
        logger.debug("onStart()")
        super.onStart()

        ApplicationController.onMainActivityStarted(this);
    }

    private fun refreshReminderLastFired() {
        // avoid firing reminders when UI is active and user is interacting with it
        applicationContext.globalState.reminderLastFireTime = System.currentTimeMillis()
    }

    public override fun onStop() {
        logger.debug("onStop()")
        super.onStop()
    }

    public override fun onResume() {
        logger.debug("onResume")
        super.onResume()

        if (settings.useCompactView != useCompactView) {
            val intent = intent
            finish()
            startActivity(intent)
        }

        svcClient.bindService(this) {
            // Service callback on data update
            causedByUser ->
            if (causedByUser)
                reloadData()
            else
                runOnUiThread { reloadLayout.visibility = View.VISIBLE }
        }

        reloadData()

        refreshReminderLastFired()

        if (shouldShowPowerOptimisationWarning)
            showPowerOptimisationWarning()

        checkPermissions()

        background {
            ApplicationController.onMainActivityResumed(this, shouldForceRepost)
            shouldForceRepost = false
        }
    }

    private fun showPowerOptimisationWarning() {
        shouldShowPowerOptimisationWarning = false

        AlertDialog.Builder(this)
            .setMessage(R.string.power_optimisation_warning)
            .setCancelable(false)
            .setPositiveButton(R.string.dismiss) {
                x, y ->
            }
            .setNegativeButton(R.string.never_show_again) {
                x, y ->
                Settings(this@MainActivity).powerOptimisationWarningShown = true
            }
            .create()
            .show()
    }

    private fun checkPermissions() {
        val hasPermissions = PermissionsManager.hasAllPermissions(this)

        find<TextView>(R.id.no_permissions_view).visibility =
                if (hasPermissions) View.GONE else View.VISIBLE;

        if (!hasPermissions) {
            if (PermissionsManager.shouldShowRationale(this)) {

                AlertDialog.Builder(this)
                    .setMessage(R.string.application_has_no_access)
                    .setCancelable(false)
                    .setPositiveButton(android.R.string.ok) {
                        x, y ->
                        PermissionsManager.requestPermissions(this)
                    }
                    .setNegativeButton(R.string.cancel) {
                        x, y ->
                    }
                    .create()
                    .show()
            } else {
                PermissionsManager.requestPermissions(this)
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, @NotNull permissions: Array<out String>, @NotNull grantResults: IntArray) {

        var granted = true

        for (result in grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED)
                granted = false
        }

        find<TextView>(R.id.no_permissions_view).visibility = if (granted) View.GONE else View.VISIBLE;
    }

    public override fun onPause() {
        logger.debug("onPause")

        svcClient.unbindService(this)

        refreshReminderLastFired()

        undoManager.clearUndoState()

        super.onPause()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)

        val menuItem = menu.findItem(R.id.action_snooze_all)
        if (menuItem != null) {
            menuItem.isEnabled = adapter.itemCount > 0
            menuItem.title =
                resources.getString(
                    if (adapter.hasActiveEvents) R.string.snooze_all else R.string.change_all)
        }

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        refreshReminderLastFired()

        when (item.itemId) {
            R.id.action_snooze_all ->
                startActivity(
                    Intent(this, SnoozeActivity::class.java)
                        .putExtra(Consts.INTENT_SNOOZE_ALL_IS_CHANGE, !adapter.hasActiveEvents)
                        .putExtra(Consts.INTENT_SNOOZE_FROM_MAIN_ACTIVITY, true))

            R.id.action_settings -> {
                shouldForceRepost = true // so onResume would re-post everything
                startActivity(Intent(this, SettingsActivity::class.java))
            }

            R.id.action_feedback ->
                startActivity(Intent(this, HelpAndFeedbackActivity::class.java))

            R.id.action_about ->
                startActivity(Intent(this, AboutActivity::class.java))
        }

        return super.onOptionsItemSelected(item)
    }

    private fun reloadData() {

        background {

            val events =
                EventsStorage(this).use {

                    db -> db.events.sortedWith(
                        Comparator<EventAlertRecord> {
                            lhs, rhs ->

                            if (lhs.snoozedUntil < rhs.snoozedUntil)
                                return@Comparator -1;
                            else if (lhs.snoozedUntil > rhs.snoozedUntil)
                                return@Comparator 1;

                            if (lhs.lastEventVisibility > rhs.lastEventVisibility)
                                return@Comparator -1;
                            else if (lhs.lastEventVisibility < rhs.lastEventVisibility)
                                return@Comparator 1;

                            return@Comparator 0;

                        }).toTypedArray()
                }

            val quietPeriodUntil = QuietHoursManager.getSilentUntil(settings)

            runOnUiThread {
                adapter.setEventsToDisplay(events);
                onNumEventsUpdated()

                if (quietPeriodUntil > 0L) {
                    quietHoursTextView.text =
                        String.format(resources.getString(R.string.quiet_hours_main_activity_status),
                                DateUtils.formatDateTime(this, quietPeriodUntil,
                                    if (DateUtils.isToday(quietPeriodUntil))
                                       DateUtils.FORMAT_SHOW_TIME
                                    else
                                        DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_DATE))

                    quietHoursLayout.visibility = View.VISIBLE;
                } else {
                    quietHoursLayout.visibility = View.GONE;
                }

                refreshLayout?.isRefreshing = false
            }
        }
    }
    @Suppress("unused", "UNUSED_PARAMETER")
    fun onUndoButtonClick(v: View?) {
        undoManager.undo()
        refreshReminderLastFired()
        reloadData()
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    fun onReloadButtonClick(v: View) {
        reloadLayout.visibility = View.GONE;
        reloadData();
        refreshReminderLastFired()
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    fun onRevertToCardViewClick(v: View) {
        settings.useCompactView = false
        settings.showNewStyleMessage = false

        val intent = intent
        finish()
        startActivity(intent)
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    fun onOKWithNewStyleClick(v: View) {
        settings.showNewStyleMessage = false
        newStyleMessageLayout.visibility = View.GONE
    }

    override fun onScrollPositionChange(scrollPosition: Int) {

        val undoSense = lastEventDismissalScrollPosition
        if (undoSense != null) {
            if (Math.abs(undoSense - scrollPosition) > undoDisappearSensitivity) {
                lastEventDismissalScrollPosition = null
                if (useCompactView)
                    adapter.clearUndoState()
            }
        }
    }

    private fun onNumEventsUpdated() {
        val hasEvents = adapter.itemCount > 0
        find<TextView>(R.id.empty_view).visibility = if (hasEvents) View.GONE else View.VISIBLE;
        this.invalidateOptionsMenu();
    }



    override fun onItemClick(v: View, position: Int, eventId: Long) {
        logger.debug("onItemClick, pos=$position, eventId=$eventId")

        val event = adapter.getEventAtPosition(position, eventId)
        if (event != null) {

            if (settings.useCompactView) {
                startActivity(
                    Intent(this, SnoozeActivity::class.java)
                        .putExtra(Consts.INTENT_NOTIFICATION_ID_KEY, event.notificationId)
                        .putExtra(Consts.INTENT_EVENT_ID_KEY, event.eventId)
                        .putExtra(Consts.INTENT_INSTANCE_START_TIME_KEY, event.instanceStartTime)
                        .putExtra(Consts.INTENT_SNOOZE_FROM_MAIN_ACTIVITY, true))

            } else {
                CalendarIntents.viewCalendarEvent(this, event)
            }
        }

        refreshReminderLastFired()
    }

    // user clicks on 'dismiss' button, item still in the list
    override fun onItemDismiss(v: View, position: Int, eventId: Long) {
        logger.debug("onItemDismiss, pos=$position, eventId=$eventId");

        val event = adapter.getEventAtPosition(position, eventId)

        if (event != null) {
            logger.debug("Removing event id ${event.eventId} from DB and dismissing notification id ${event.notificationId}")
            ApplicationController.dismissEvent(this, event);

            undoManager.addUndoState(
                UndoState(
                    undo = Runnable { ApplicationController.restoreEvent(this, event) }))

            adapter.removeEvent(event)
            lastEventDismissalScrollPosition = adapter.scrollPosition

            onNumEventsUpdated()

            val coordinatorLayout = find<CoordinatorLayout>(R.id.main_activity_coordinator)

            Snackbar.make(coordinatorLayout, resources.getString(R.string.event_dismissed), Snackbar.LENGTH_LONG)
                    .setAction(resources.getString(R.string.undo)) { onUndoButtonClick(null) }
                    .show()
        }
        refreshReminderLastFired()
    }

    // Item was already removed from UI, we just have to dismiss it now
    override fun onItemRemoved(event: EventAlertRecord) {

        logger.debug("onItemRemoved, eventId=${event.eventId}")

        logger.debug("Removing event id ${event.eventId} from DB and dismissing notification id ${event.notificationId}")
        ApplicationController.dismissEvent(this, event)
        lastEventDismissalScrollPosition = adapter.scrollPosition
        onNumEventsUpdated()

        refreshReminderLastFired()
    }

    override fun onItemRestored(event: EventAlertRecord) {
        logger.debug("onItemRestored, eventId=${event.eventId}")
        ApplicationController.restoreEvent(this, event)

        onNumEventsUpdated()

        refreshReminderLastFired()
    }

    override fun onItemSnooze(v: View, position: Int, eventId: Long) {
        logger.debug("onItemSnooze, pos=$position, eventId=$eventId");

        val event = adapter.getEventAtPosition(position, eventId)

        if (event != null) {
            startActivity(
                Intent(this, SnoozeActivity::class.java)
                    .putExtra(Consts.INTENT_NOTIFICATION_ID_KEY, event.notificationId)
                    .putExtra(Consts.INTENT_EVENT_ID_KEY, event.eventId)
                    .putExtra(Consts.INTENT_INSTANCE_START_TIME_KEY, event.instanceStartTime)
                    .putExtra(Consts.INTENT_SNOOZE_FROM_MAIN_ACTIVITY, true))
        }
        refreshReminderLastFired()
    }

    companion object {
        private val logger = Logger("ActivityMain")
    }
}
