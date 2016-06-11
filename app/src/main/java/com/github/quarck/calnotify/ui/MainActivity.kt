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
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.StaggeredGridLayoutManager
import android.support.v7.widget.StaggeredGridLayoutManager.VERTICAL
import android.text.format.DateUtils
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import com.github.quarck.calnotify.*
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.calendar.CalendarIntents
import com.github.quarck.calnotify.calendar.CalendarProvider
import com.github.quarck.calnotify.eventsstorage.EventAlertRecord
import com.github.quarck.calnotify.eventsstorage.EventsStorage
import com.github.quarck.calnotify.logs.Logger
import com.github.quarck.calnotify.maps.MapsIntents
import com.github.quarck.calnotify.permissions.PermissionsManager
import com.github.quarck.calnotify.quiethours.QuietHoursManager
import com.github.quarck.calnotify.utils.background
import com.github.quarck.calnotify.utils.find
import java.util.*
import kotlin.concurrent.schedule

class MainActivity : Activity(), EventListCallback {
    private val settings: Settings by lazy { Settings(this) }

    private lateinit var staggeredLayoutManager: StaggeredGridLayoutManager
    private lateinit var recyclerView: RecyclerView
    private lateinit var reloadLayout: RelativeLayout
    private lateinit var undoLayout: RelativeLayout
    private lateinit var quietHoursLayout: RelativeLayout
    private lateinit var quietHoursTextView: TextView

    private lateinit var adapter: EventListAdapter

    private var shouldShowPowerOptimisationWarning = false

    private val svcClient by lazy { UINotifierServiceClient() }

    private var undoTimer: Timer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        logger.debug("onCreateView")

        setContentView(R.layout.activity_main)

        adapter = EventListAdapter(this, R.layout.event_card, this)

        staggeredLayoutManager = StaggeredGridLayoutManager(1, StaggeredGridLayoutManager.VERTICAL)
        recyclerView = find<RecyclerView>(R.id.list_events)
        recyclerView.layoutManager = staggeredLayoutManager;
        recyclerView.adapter = adapter;

        reloadLayout = find<RelativeLayout>(R.id.activity_main_reload_layout)

        quietHoursLayout = find<RelativeLayout>(R.id.activity_main_quiet_hours_info_layout)
        quietHoursTextView = find<TextView>(R.id.activity_main_quiet_hours)

        undoLayout = find<RelativeLayout>(R.id.activity_main_undo_layout)

        shouldShowPowerOptimisationWarning =
            (Build.MANUFACTURER.indexOf(Consts.SAMSUNG_KEYWORD, ignoreCase=true) != -1) &&
                !Settings(this).powerOptimisationWarningShown
    }

    public override fun onStart() {
        logger.debug("onStart()")
        super.onStart()

        ApplicationController.onAppStarted(applicationContext);
    }

    private fun refreshReminderLastFired() {
        // avoid firing reminders when UI is active and user is interacting with it
        this.globalState.reminderLastFireTime = System.currentTimeMillis()
    }

    public override fun onStop() {
        logger.debug("onStop()")
        super.onStop()
    }

    public override fun onResume() {
        logger.debug("onResume")
        super.onResume()

        svcClient.bindService(this) {
            // Service callback on data update
            causedByUser ->
            if (causedByUser)
                reloadData()
            else
                runOnUiThread { reloadLayout.visibility = View.VISIBLE }
        }

        reloadData()

        updateUndoVisibility()

        refreshReminderLastFired()

        if (shouldShowPowerOptimisationWarning)
            showPowerOptimisationWarning()

        checkPermissions()

        background {
            ApplicationController.onAppResumed(this)
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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>?, grantResults: IntArray?) {
        if (grantResults == null)
            return;

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

        undoTimer?.cancel()
        undoTimer = null

        refreshReminderLastFired()

        ApplicationController.onAppPause(this)

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
                        .putExtra(Consts.INTENT_SNOOZE_ALL_IS_CHANGE, !adapter.hasActiveEvents))

            R.id.action_settings ->
                startActivity(Intent(this, SettingsActivity::class.java))

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
            }
        }
    }
    @Suppress("unused", "UNUSED_PARAMETER")
    fun onUndoButtonClick(v: View) {
        ApplicationController.undoDismiss(this);
        updateUndoVisibility()
        refreshReminderLastFired()
        reloadData()
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    fun onReloadButtonClick(v: View) {
        reloadLayout.visibility = View.GONE;
        reloadData();
        refreshReminderLastFired()
    }

    private fun updateUndoVisibility() {

        val canUndo = ApplicationController.undoManager.canUndo
        undoLayout.visibility = if (canUndo) View.VISIBLE else View.GONE

        if (canUndo) {
            if (undoTimer == null)
                undoTimer = Timer()

            undoTimer?.schedule(Consts.UNDO_TIMEOUT) {
                ApplicationController.undoManager.clearIfTimeout()
                runOnUiThread {
                    undoLayout.visibility = if (ApplicationController.undoManager.canUndo) View.VISIBLE else View.GONE
                }
            }
        }
    }

    private fun onNumEventsUpdated() {
        updateUndoVisibility()
        val hasEvents = adapter.itemCount > 0
        find<TextView>(R.id.empty_view).visibility = if (hasEvents) View.GONE else View.VISIBLE;
        this.invalidateOptionsMenu();
    }


    override fun onItemClick(v: View, position: Int, eventId: Long) {
        logger.debug("onItemClick, pos=$position, eventId=$eventId");

        val event = adapter.getEventAtPosition(position, eventId)
        if (event != null)
            CalendarIntents.viewCalendarEvent(this, event.eventId, event.instanceStartTime, event.instanceEndTime)

        refreshReminderLastFired()
    }

    override fun onItemDismiss(v: View, position: Int, eventId: Long) {
        logger.debug("onItemDismiss, pos=$position, eventId=$eventId");

        val event = adapter.getEventAtPosition(position, eventId)

        if (event != null) {
            logger.debug("Removing event id ${event.eventId} from DB and dismissing notification id ${event.notificationId}")
            ApplicationController.dismissEvent(this, event, enableUndo=true);
            adapter.removeAt(position)
            onNumEventsUpdated()
        }
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
                    .putExtra(Consts.INTENT_INSTANCE_START_TIME_KEY, event.instanceStartTime))
        }
        refreshReminderLastFired()
    }

    override fun onItemLocation(v: View, position: Int, eventId: Long) {
        logger.debug("onItemLocation, pos=$position, eventId=$eventId");

        val event = adapter.getEventAtPosition(position, eventId)

        if (event != null)
            MapsIntents.openLocation(this, event.location)

        refreshReminderLastFired()
    }

    override fun onItemDateTime(v: View, position: Int, eventId: Long) {
        logger.debug("onItemDateTime, pos=$position, eventId=$eventId");

        val event = adapter.getEventAtPosition(position, eventId)

        if (event != null)
            CalendarIntents.viewCalendarEvent(this, event.eventId, event.instanceStartTime, event.instanceEndTime)

        refreshReminderLastFired()
    }



    companion object {
        private val logger = Logger("ActivityMain")
    }
}
