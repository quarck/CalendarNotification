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

import android.annotation.TargetApi
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
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
import android.widget.ArrayAdapter
import android.widget.RelativeLayout
import android.widget.TextView
import com.github.quarck.calnotify.*
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.app.UndoManager
import com.github.quarck.calnotify.app.UndoState
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.calendar.isSpecial
import com.github.quarck.calnotify.calendarmonitor.CalendarMonitorState
import com.github.quarck.calnotify.dismissedeventsstorage.DismissedEventsStorage
import com.github.quarck.calnotify.dismissedeventsstorage.EventDismissType
import com.github.quarck.calnotify.eventsstorage.EventsStorage
import com.github.quarck.calnotify.logs.DevLog
//import com.github.quarck.calnotify.logs.Logger
import com.github.quarck.calnotify.permissions.PermissionsManager
import com.github.quarck.calnotify.quiethours.QuietHoursManager
import com.github.quarck.calnotify.reminders.ReminderState
import com.github.quarck.calnotify.utils.background
import com.github.quarck.calnotify.utils.find
import com.github.quarck.calnotify.utils.findOrThrow
import com.github.quarck.calnotify.utils.powerManager
import org.jetbrains.annotations.NotNull
import java.util.*

class DataUpdatedReceiver(val activity: MainActivity): BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {

        val isUserCaused = intent?.getBooleanExtra(Consts.INTENT_IS_USER_ACTION, false) ?: false
        activity.onDataUpdated(causedByUser = isUserCaused)
    }
}

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

    private var lastEventDismissalScrollPosition: Int? = null

    private var calendarRescanEnabled = true

    private var shouldRemindForEventsWithNoReminders = true

    private var shouldForceRepost = false

    private val undoDisappearSensitivity: Float by lazy {
        resources.getDimension(R.dimen.undo_dismiss_sensitivity)
    }

    private val dataUpdatedReceiver = DataUpdatedReceiver(this)

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

        shouldRemindForEventsWithNoReminders = settings.shouldRemindForEventsWithNoReminders

        adapter =
                EventListAdapter(this, this)

        staggeredLayoutManager = StaggeredGridLayoutManager(1, StaggeredGridLayoutManager.VERTICAL)
        recyclerView = findOrThrow<RecyclerView>(R.id.list_events)
        recyclerView.layoutManager = staggeredLayoutManager;
        recyclerView.adapter = adapter;
        adapter.recyclerView = recyclerView

        reloadLayout = findOrThrow<RelativeLayout>(R.id.activity_main_reload_layout)

        quietHoursLayout = findOrThrow<RelativeLayout>(R.id.activity_main_quiet_hours_info_layout)
        quietHoursTextView = findOrThrow<TextView>(R.id.activity_main_quiet_hours)

        newStyleMessageLayout = findOrThrow<View>(R.id.activity_main_new_style_message_layout)

        calendarRescanEnabled = settings.enableCalendarRescan

        floatingAddEvent = findOrThrow<FloatingActionButton>(R.id.action_btn_add_event)

        //floatingAddEvent.visibility = if (settings.enableAddEvent) View.VISIBLE else View.GONE

        floatingAddEvent.setOnClickListener {
            startActivity(
                    Intent(this, EditEventActivity::class.java)
                            .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
        }
    }

    public override fun onStart() {
        DevLog.info(this, LOG_TAG, "onStart()")
        super.onStart()

        ApplicationController.onMainActivityStarted(this);
    }

    private fun refreshReminderLastFired() {
        // avoid firing reminders when UI is active and user is interacting with it
        ReminderState(applicationContext).reminderLastFireTime = System.currentTimeMillis()
    }

    public override fun onStop() {
        DevLog.info(this, LOG_TAG, "onStop()")
        super.onStop()
    }

    public override fun onResume() {
        DevLog.info(this, LOG_TAG, "onResume")
        super.onResume()

        checkPermissions()

        registerReceiver(dataUpdatedReceiver, IntentFilter(Consts.DATA_UPDATED_BROADCAST));

        DevLog.refreshIsEnabled(context = this);
        if (!settings.shouldKeepLogs) {
            DevLog.clear(context = this)
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
            val coordinatorLayout = findOrThrow<CoordinatorLayout>(R.id.main_activity_coordinator)

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
        else {
            // if we have essential permissions - now check for power manager optimisations
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !settings.doNotShowBatteryOptimisationWarning) {
                if (!powerManager.isIgnoringBatteryOptimizations(BuildConfig.APPLICATION_ID)) {

                    AlertDialog.Builder(this)
                            .setTitle(getString(R.string.battery_optimisation_title))
                            .setMessage(getString(R.string.battery_optimisation_details))
                            .setPositiveButton(getString(R.string.you_can_do_it)) @TargetApi(Build.VERSION_CODES.M) {
                                _, _ ->

                                val intent = Intent()
                                        .setAction(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                                        .setData(Uri.parse("package:" + BuildConfig.APPLICATION_ID))
                                startActivity(intent)
                            }
                            .setNeutralButton(getString(R.string.you_can_do_it_later)) {
                                _, _ ->
                            }
                            .setNegativeButton(getString(R.string.you_cannot_do_it)) {
                                _, _ ->
                                settings.doNotShowBatteryOptimisationWarning = true
                            }
                            .create()
                            .show()
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, @NotNull permissions: Array<out String>, @NotNull grantResults: IntArray) {

//        var granted = true
//
        for (result in grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                DevLog.error(this, LOG_TAG, "Permission is not granted!")
            }
        }

        //find<TextView>(R.id.no_permissions_view).visibility = if (granted) View.GONE else View.VISIBLE;
    }

    public override fun onPause() {
        DevLog.info(this, LOG_TAG, "onPause")

        refreshReminderLastFired()

        undoManager.clearUndoState()

        unregisterReceiver(dataUpdatedReceiver)

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

    private fun onCustomQuietHours() {

        if (!ApplicationController.isCustomQuietHoursActive(this)) {

            val intervalValues: IntArray = resources.getIntArray(R.array.custom_quiet_hours_interval_values)
            val intervalNames: Array<String> = resources.getStringArray(R.array.custom_quiet_hours_interval_names)

            val builder = AlertDialog.Builder(this)
            val adapter = ArrayAdapter<String>(this, R.layout.simple_list_item_large)

            builder.setTitle(getString(R.string.start_quiet_hours_dialog_title))
            adapter.addAll(intervalNames.toMutableList())
            builder.setCancelable(true)
            builder.setAdapter(adapter) { _, which ->
                if (which in 0 until intervalValues.size) {
                    ApplicationController.applyCustomQuietHoursForSeconds(this, intervalValues[which])
                    reloadData()
                }
            }

            builder.show()

        } else {
            ApplicationController.applyCustomQuietHoursForSeconds(this, 0)
            reloadData()
        }
    }

    private fun onMuteAll() {
        AlertDialog.Builder(this)
                .setMessage(R.string.mute_all_events_question)
                .setCancelable(false)
                .setPositiveButton(android.R.string.yes) {
                    _, _ ->
                    doMuteAll()
                }
                .setNegativeButton(R.string.cancel) {
                    _, _ ->
                }
                .create()
                .show()
    }

    private fun doDismissAll() {

        ApplicationController.dismissAllButRecentAndSnoozed(
                this, EventDismissType.ManuallyDismissedFromActivity)

        reloadData()
        lastEventDismissalScrollPosition = null

        onNumEventsUpdated()
    }

    private fun doMuteAll() {
        ApplicationController.muteAllVisibleEvents(this);

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

        val muteAllMenuItem = menu.findItem(R.id.action_mute_all)
        if (muteAllMenuItem != null) {
            muteAllMenuItem.isVisible = true
            muteAllMenuItem.isEnabled = adapter.anyForMute
        }

        val dismissedEventsMenuItem = menu.findItem(R.id.action_dismissed_events)
        if (dismissedEventsMenuItem != null) {
            dismissedEventsMenuItem.isEnabled = true
            dismissedEventsMenuItem.isVisible = true
        }

        val dismissAll = menu.findItem(R.id.action_dismiss_all)
        if (dismissAll != null) {
            dismissAll.isEnabled = adapter.anyForDismissAllButRecentAndSnoozed
        }

        val customQuiet = menu.findItem(R.id.action_custom_quiet_interval)
        if (customQuiet != null) {
            customQuiet.title =
                    resources.getString(
                        if (ApplicationController.isCustomQuietHoursActive(this))
                            R.string.stop_quiet_hours
                        else
                            R.string.start_quiet_hours)
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
                        Intent(this, SnoozeAllActivity::class.java)
                                .putExtra(Consts.INTENT_SNOOZE_ALL_IS_CHANGE, !adapter.hasActiveEvents)
                                .putExtra(Consts.INTENT_SNOOZE_FROM_MAIN_ACTIVITY, true)
                                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))

            R.id.action_mute_all ->
                onMuteAll()

            R.id.action_dismissed_events ->
                startActivity(
                        Intent(this, DismissedEventsActivity::class.java)
                                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))

            R.id.action_settings -> {
                shouldForceRepost = true // so onResume would re-post everything
                startActivity(
                        Intent(this, SettingsActivityNew::class.java)
                        .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
            }

            R.id.action_report_a_bug ->
                startActivity(
                        Intent(this, ReportABugActivity::class.java)
                                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))

            R.id.action_about ->
                startActivity(
                        Intent(this, AboutActivity::class.java)
                                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))

            R.id.action_dismiss_all ->
                onDismissAll()

            R.id.action_custom_quiet_interval ->
                onCustomQuietHours()

            R.id.action_test_page ->
                startActivity(
                        Intent(this, TestActivity::class.java)
                                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        }

        return super.onOptionsItemSelected(item)
    }

    private fun reloadData() {

        background {
            DismissedEventsStorage(this).use { it.purgeOld(System.currentTimeMillis(), Consts.BIN_KEEP_HISTORY_MILLISECONDS) }

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

    override fun onScrollPositionChange(newPos: Int) {

        val undoSense = lastEventDismissalScrollPosition
        if (undoSense != null) {
            if (Math.abs(undoSense - newPos) > undoDisappearSensitivity) {
                lastEventDismissalScrollPosition = null
                adapter.clearUndoState()
            }
        }
    }

    private fun onNumEventsUpdated() {
        val hasEvents = adapter.itemCount > 0
        findOrThrow<TextView>(R.id.empty_view).visibility = if (hasEvents) View.GONE else View.VISIBLE;
        this.invalidateOptionsMenu();
    }


    override fun onItemClick(v: View, position: Int, eventId: Long) {
        DevLog.info(this, LOG_TAG, "onItemClick, pos=$position, eventId=$eventId")

        val event = adapter.getEventAtPosition(position, eventId)

        if (event != null && !event.isSpecial) {
           startActivity(
                    Intent(this, ViewEventActivity::class.java)
                            .putExtra(Consts.INTENT_NOTIFICATION_ID_KEY, event.notificationId)
                            .putExtra(Consts.INTENT_EVENT_ID_KEY, event.eventId)
                            .putExtra(Consts.INTENT_INSTANCE_START_TIME_KEY, event.instanceStartTime)
                            .putExtra(Consts.INTENT_SNOOZE_FROM_MAIN_ACTIVITY, true)
                            .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))

        }
    }

    // user clicks on 'dismiss' button, item still in the list
    override fun onItemDismiss(v: View, position: Int, eventId: Long) {
        DevLog.info(this, LOG_TAG, "onItemDismiss, pos=$position, eventId=$eventId");

        val event = adapter.getEventAtPosition(position, eventId)

        if (event != null) {
            DevLog.info(this, LOG_TAG, "Removing event id ${event.eventId} from DB and dismissing notification id ${event.notificationId}")
            ApplicationController.dismissEvent(this, EventDismissType.ManuallyDismissedFromActivity, event)

            undoManager.addUndoState(
                    UndoState(
                            undo = Runnable { ApplicationController.restoreEvent(this, event) }))

            adapter.removeEvent(event)
            lastEventDismissalScrollPosition = adapter.scrollPosition

            onNumEventsUpdated()

            val coordinatorLayout = findOrThrow<CoordinatorLayout>(R.id.main_activity_coordinator)

            Snackbar.make(coordinatorLayout, resources.getString(R.string.event_dismissed), Snackbar.LENGTH_LONG)
                    .setAction(resources.getString(R.string.undo)) { onUndoButtonClick(null) }
                    .show()
        }
    }

    // Item was already removed from UI, we just have to dismiss it now
    override fun onItemRemoved(event: EventAlertRecord) {

        DevLog.info(this, LOG_TAG, "onItemRemoved: Removing event id ${event.eventId} from DB and dismissing notification id ${event.notificationId}")
        ApplicationController.dismissEvent(this, EventDismissType.ManuallyDismissedFromActivity, event)
        lastEventDismissalScrollPosition = adapter.scrollPosition
        onNumEventsUpdated()
    }

    override fun onItemRestored(event: EventAlertRecord) {
        DevLog.info(this, LOG_TAG, "onItemRestored, eventId=${event.eventId}")
        ApplicationController.restoreEvent(this, event)

        onNumEventsUpdated()
    }

    override fun onItemSnooze(v: View, position: Int, eventId: Long) {
        DevLog.info(this, LOG_TAG, "onItemSnooze, pos=$position, eventId=$eventId");

        val event = adapter.getEventAtPosition(position, eventId)

        if (event != null) {
            startActivity(
                    Intent(this, ViewEventActivity::class.java)
                            .putExtra(Consts.INTENT_NOTIFICATION_ID_KEY, event.notificationId)
                            .putExtra(Consts.INTENT_EVENT_ID_KEY, event.eventId)
                            .putExtra(Consts.INTENT_INSTANCE_START_TIME_KEY, event.instanceStartTime)
                            .putExtra(Consts.INTENT_SNOOZE_FROM_MAIN_ACTIVITY, true)
                            .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        }
    }

    fun onDataUpdated(causedByUser: Boolean) {
        if (causedByUser)
            reloadData()
        else
            runOnUiThread { reloadLayout.visibility = View.VISIBLE }
    }

    companion object {
        private const val LOG_TAG = "MainActivity"
    }
}
