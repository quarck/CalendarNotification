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
import android.os.Bundle
import android.support.design.widget.CoordinatorLayout
import android.support.design.widget.FloatingActionButton
import android.support.design.widget.Snackbar
import android.support.v4.widget.SwipeRefreshLayout
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.StaggeredGridLayoutManager
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
import com.github.quarck.calnotify.calendar.isSpecial
import com.github.quarck.calnotify.calendarmonitor.CalendarMonitorState
import com.github.quarck.calnotify.dismissedeventsstorage.DismissedEventsStorage
import com.github.quarck.calnotify.dismissedeventsstorage.EventDismissType
import com.github.quarck.calnotify.eventsstorage.EventsStorage
import com.github.quarck.calnotify.globalState
import com.github.quarck.calnotify.logs.DevLog
//import com.github.quarck.calnotify.logs.Logger
import com.github.quarck.calnotify.permissions.PermissionsManager
import com.github.quarck.calnotify.quiethours.QuietHoursManager
import com.github.quarck.calnotify.reminders.ReminderState
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
    private var refreshLayout: SwipeRefreshLayout? = null

    private lateinit var floatingAddEvent: FloatingActionButton

    private lateinit var adapter: EventListAdapter

    private val svcClient by lazy { UINotifierServiceClient() }

    private var lastEventDismissalScrollPosition: Int? = null

    private var useCompactView = true
    private var calendarRescanEnabled = true

    private var shouldRemindForEventsWithNoReminders = true

    private var shouldForceRepost = false

    private val undoDisappearSensitivity: Float by lazy {
        resources.getDimension(R.dimen.undo_dismiss_sensitivity)
    }

    private val undoManager by lazy { UndoManager }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        DevLog.debug(LOG_TAG, "onCreateView")

        ApplicationController.onMainActivityCreate(this);

        setContentView(R.layout.activity_main)
        setSupportActionBar(find<Toolbar?>(R.id.toolbar))
        //supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        shouldForceRepost = (System.currentTimeMillis() - (globalState?.lastNotificationRePost ?: 0L)) > Consts.MIN_FORCE_REPOST_INTERVAL

        refreshLayout = find<SwipeRefreshLayout?>(R.id.cardview_refresh_layout)

        refreshLayout?.setOnRefreshListener {
            reloadLayout.visibility = View.GONE;
            reloadData()
        }

        useCompactView = settings.useCompactView
        shouldRemindForEventsWithNoReminders = settings.shouldRemindForEventsWithNoReminders

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

        if (useCompactView && settings.showNewStyleMessage) {
            newStyleMessageLayout.visibility = View.VISIBLE
            if (settings.versionCodeFirstInstalled >= Consts.COMPACT_VIEW_DEFAULT_SINCE_VER) {
                find<Button>(R.id.activity_main_button_hate_new_style).visibility = View.GONE
                find<TextView>(R.id.text_view_style_message).text = resources.getString(R.string.usage_hints)
            }
        }

        calendarRescanEnabled = settings.enableCalendarRescan

        floatingAddEvent = find<FloatingActionButton>(R.id.action_btn_add_event)

        floatingAddEvent.visibility = if (settings.enableAddEvent) View.VISIBLE else View.GONE

        floatingAddEvent.setOnClickListener {
            startActivity(Intent(this, EditEventActivity::class.java))
        }

        if (settings.versionCodeFirstInstalled < Consts.NEW_NOTIFICATION_SWIPE_SETTINGS_VER) {

            if (!settings.notificationSettingsMigrated) {
                // Previous behavior:
                // Show Dismiss On & Swipe does snooze Off
                // - Don't allow swiping
                // Show Dismiss Off & Swipe does snooze Off
                // - Swipe would dismiss
                // Show Dismiss Off & Swipe does snooze On
                // - Swipe would snooze

                if (settings.showDismissButtonDepricated && settings.notificationSwipeDoesSnooze) {

                    settings.allowNotificationSwipe = true
                    settings.notificationSwipeDoesSnooze = true
                } else if (settings.showDismissButtonDepricated && !settings.notificationSwipeDoesSnooze) {

                    settings.allowNotificationSwipe = false
                    settings.notificationSwipeDoesSnooze = false
                } else {
                    settings.allowNotificationSwipe = true
                    settings.notificationSwipeDoesSnooze = false
                }

                settings.notificationSettingsMigrated = true;
            }
        }
    }

    public override fun onStart() {
        DevLog.debug(LOG_TAG, "onStart()")
        super.onStart()

        ApplicationController.onMainActivityStarted(this);
    }

    private fun refreshReminderLastFired() {
        // avoid firing reminders when UI is active and user is interacting with it
        ReminderState(applicationContext).reminderLastFireTime = System.currentTimeMillis()
    }

    public override fun onStop() {
        DevLog.debug(LOG_TAG, "onStop()")
        super.onStop()
    }

    public override fun onResume() {
        DevLog.debug(LOG_TAG, "onResume")
        super.onResume()

        checkPermissions()

        if (settings.useCompactView != useCompactView) {
            val intent = intent
            finish()
            startActivity(intent)
        }

        floatingAddEvent.visibility = if (settings.enableAddEvent) View.VISIBLE else View.GONE

        DevLog.refreshIsEnabled(context = this);
        if (!settings.shouldKeepLogs) {
            DevLog.clear(context = this)
        }

        svcClient.bindService(this) {
            // Service callback on data update
            causedByUser ->
            if (causedByUser)
                reloadData()
            else
                runOnUiThread { reloadLayout.visibility = View.VISIBLE }
        }

        if (calendarRescanEnabled != settings.enableCalendarRescan) {
            calendarRescanEnabled = settings.enableCalendarRescan

            if (!calendarRescanEnabled) {
                CalendarMonitorState(this).firstScanEver = true
            }
        }

        reloadData()

        refreshReminderLastFired()

        var monitorSettingsChanged = false
        if (settings.shouldRemindForEventsWithNoReminders != shouldRemindForEventsWithNoReminders) {
            shouldRemindForEventsWithNoReminders = settings.shouldRemindForEventsWithNoReminders;
            monitorSettingsChanged = true
        }

        background {
            ApplicationController.onMainActivityResumed(this, shouldForceRepost, monitorSettingsChanged)
            shouldForceRepost = false
        }

        if (undoManager.canUndo) {
            val coordinatorLayout = find<CoordinatorLayout>(R.id.main_activity_coordinator)

            Snackbar.make(coordinatorLayout, resources.getString(R.string.event_dismissed), Snackbar.LENGTH_LONG)
                    .setAction(resources.getString(R.string.undo)) { onUndoButtonClick(null) }
                    .show()
        }

        invalidateOptionsMenu();
    }

    private fun checkPermissions() {
        val hasPermissions = PermissionsManager.hasAllPermissions(this)

        //find<TextView>(R.id.no_permissions_view).visibility = if (hasPermissions) View.GONE else View.VISIBLE;

        if (!hasPermissions) {
            if (PermissionsManager.shouldShowRationale(this)) {

                AlertDialog.Builder(this)
                        .setMessage(R.string.application_has_no_access)
                        .setCancelable(false)
                        .setPositiveButton(android.R.string.ok) {
                            _, _ ->
                            PermissionsManager.requestPermissions(this)
                        }
                        .setNegativeButton(R.string.exit) {
                            _, _ ->
                            this@MainActivity.finish()
                        }
                        .create()
                        .show()
            }
            else {
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

        //find<TextView>(R.id.no_permissions_view).visibility = if (granted) View.GONE else View.VISIBLE;
    }

    public override fun onPause() {
        DevLog.debug(LOG_TAG, "onPause")

        refreshReminderLastFired()

        svcClient.unbindService(this)

        undoManager.clearUndoState()

        super.onPause()
    }

    private fun onDismissAll() {
        AlertDialog.Builder(this)
                .setMessage(R.string.dismiss_all_events_confirmation)
                .setCancelable(false)
                .setPositiveButton(android.R.string.yes) {
                    _, _ ->
                    doDismissAll()
                }
                .setNegativeButton(R.string.cancel) {
                    _, _ ->
                }
                .create()
                .show()
    }

    private fun doDismissAll() {

        ApplicationController.dismissAllButRecentAndSnoozed(this, EventDismissType.ManuallyDismissedFromActivity);

        reloadData()
        lastEventDismissalScrollPosition = null

        onNumEventsUpdated()
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

        val dismissedEventsMenuItem = menu.findItem(R.id.action_dismissed_events)
        if (dismissedEventsMenuItem != null) {
            dismissedEventsMenuItem.isEnabled = settings.keepHistory
            dismissedEventsMenuItem.isVisible = settings.keepHistory
        }

        val dismissAll = menu.findItem(R.id.action_dismiss_all)
        if (dismissAll != null) {
            dismissAll.isEnabled = adapter.anyForDismissAllButRecentAndSnoozed
        }

        if (settings.devModeEnabled) {
            menu.findItem(R.id.action_test_page)?.isVisible = true
//            menu.findItem(R.id.action_add_event)?.isVisible = true
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

            R.id.action_dismissed_events ->
                startActivity(Intent(this, DismissedEventsActivity::class.java))

            R.id.action_settings -> {
                shouldForceRepost = true // so onResume would re-post everything
                startActivity(Intent(this, SettingsActivity::class.java))
            }

            R.id.action_feedback ->
                startActivity(Intent(this, HelpAndFeedbackActivity::class.java))

            R.id.action_about ->
                startActivity(Intent(this, AboutActivity::class.java))

            R.id.action_dismiss_all ->
                onDismissAll()

            R.id.action_test_page ->
                startActivity(Intent(this, TestActivity::class.java))
        }

        return super.onOptionsItemSelected(item)
    }

    private fun reloadData() {

        background {
            if (!settings.keepHistory) {
                DismissedEventsStorage(this).use { it.clearHistory() }
            }
            else {
                DismissedEventsStorage(this).use { it.purgeOld(System.currentTimeMillis(), settings.keepHistoryMilliseconds) }
            }

            val events =
                    EventsStorage(this).use {

                        db ->
                        db.events.sortedWith(
                                Comparator<EventAlertRecord> {
                                    lhs, rhs ->

                                    if (lhs.snoozedUntil < rhs.snoozedUntil)
                                        return@Comparator -1;
                                    else if (lhs.snoozedUntil > rhs.snoozedUntil)
                                        return@Comparator 1;

                                    if (lhs.lastStatusChangeTime > rhs.lastStatusChangeTime)
                                        return@Comparator -1;
                                    else if (lhs.lastStatusChangeTime < rhs.lastStatusChangeTime)
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
                }
                else {
                    quietHoursLayout.visibility = View.GONE;
                }

                refreshLayout?.isRefreshing = false
            }
        }
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    fun onUndoButtonClick(v: View?) {
        undoManager.undo()
        reloadData()
    }

//    @Suppress("unused", "UNUSED_PARAMETER")
//    fun onReloadButtonClick(v: View) {
//        reloadLayout.visibility = View.GONE;
//        reloadData();
//    }

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

    override fun onScrollPositionChange(newPos: Int) {

        val undoSense = lastEventDismissalScrollPosition
        if (undoSense != null) {
            if (Math.abs(undoSense - newPos) > undoDisappearSensitivity) {
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
        DevLog.debug(LOG_TAG, "onItemClick, pos=$position, eventId=$eventId")

        val event = adapter.getEventAtPosition(position, eventId)

        if (event != null && !event.isSpecial) {

            if (settings.useCompactView) {
                startActivity(
                        Intent(this, SnoozeActivity::class.java)
                                .putExtra(Consts.INTENT_NOTIFICATION_ID_KEY, event.notificationId)
                                .putExtra(Consts.INTENT_EVENT_ID_KEY, event.eventId)
                                .putExtra(Consts.INTENT_INSTANCE_START_TIME_KEY, event.instanceStartTime)
                                .putExtra(Consts.INTENT_SNOOZE_FROM_MAIN_ACTIVITY, true))

            }
            else {
                CalendarIntents.viewCalendarEvent(this, event)
            }
        }
    }

    // user clicks on 'dismiss' button, item still in the list
    override fun onItemDismiss(v: View, position: Int, eventId: Long) {
        DevLog.debug(LOG_TAG, "onItemDismiss, pos=$position, eventId=$eventId");

        val event = adapter.getEventAtPosition(position, eventId)

        if (event != null) {
            DevLog.debug(LOG_TAG, "Removing event id ${event.eventId} from DB and dismissing notification id ${event.notificationId}")
            ApplicationController.dismissEvent(this, EventDismissType.ManuallyDismissedFromActivity, event);

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
    }

    // Item was already removed from UI, we just have to dismiss it now
    override fun onItemRemoved(event: EventAlertRecord) {

        DevLog.debug(LOG_TAG, "onItemRemoved, eventId=${event.eventId}")

        DevLog.debug(LOG_TAG, "Removing event id ${event.eventId} from DB and dismissing notification id ${event.notificationId}")
        ApplicationController.dismissEvent(this, EventDismissType.ManuallyDismissedFromActivity, event)
        lastEventDismissalScrollPosition = adapter.scrollPosition
        onNumEventsUpdated()
    }

    override fun onItemRestored(event: EventAlertRecord) {
        DevLog.debug(LOG_TAG, "onItemRestored, eventId=${event.eventId}")
        ApplicationController.restoreEvent(this, event)

        onNumEventsUpdated()
    }

    override fun onItemSnooze(v: View, position: Int, eventId: Long) {
        DevLog.debug(LOG_TAG, "onItemSnooze, pos=$position, eventId=$eventId");

        val event = adapter.getEventAtPosition(position, eventId)

        if (event != null) {
            startActivity(
                    Intent(this, SnoozeActivity::class.java)
                            .putExtra(Consts.INTENT_NOTIFICATION_ID_KEY, event.notificationId)
                            .putExtra(Consts.INTENT_EVENT_ID_KEY, event.eventId)
                            .putExtra(Consts.INTENT_INSTANCE_START_TIME_KEY, event.instanceStartTime)
                            .putExtra(Consts.INTENT_SNOOZE_FROM_MAIN_ACTIVITY, true))
        }
    }

    companion object {
        private const val LOG_TAG = "MainActivity"
    }
}
