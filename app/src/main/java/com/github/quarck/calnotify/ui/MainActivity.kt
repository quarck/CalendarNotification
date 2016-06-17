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
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.StaggeredGridLayoutManager
import android.support.v7.widget.helper.ItemTouchHelper
import android.text.format.DateUtils
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.RelativeLayout
import android.widget.TextView
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.calendar.CalendarIntents
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.eventsstorage.EventsStorage
import com.github.quarck.calnotify.globalState
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

    private var undoSenseHistoricY: Float? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        logger.debug("onCreateView")

        setContentView(R.layout.activity_main)

        val useCompactView = settings.useCompactView

        adapter =
            EventListAdapter(
                this,
                useCompactView,
                if (useCompactView) R.layout.event_card_compact else R.layout.event_card,
                this)

        staggeredLayoutManager = StaggeredGridLayoutManager(1, StaggeredGridLayoutManager.VERTICAL)
        recyclerView = find<RecyclerView>(R.id.list_events)
        recyclerView.layoutManager = staggeredLayoutManager;
        recyclerView.adapter = adapter;
        adapter.recyclerView = recyclerView

        if (useCompactView) {
            setUpItemTouchHelper()
            setUpAnimationDecoratorHelper()
        } else {
            recyclerView.setOnTouchListener { view, motionEvent -> onMainListMotion(motionEvent) }
        }

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

    private fun onMainListMotion(motionEvent: MotionEvent): Boolean {

        val action = motionEvent.action

        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_UP) {
            if (undoSenseHistoricY == null) {
                undoSenseHistoricY = motionEvent.y
            } else if (Math.abs((undoSenseHistoricY ?: 0.0f) - motionEvent.y) > Consts.UNDO_PROMPT_DISAPPEAR_SENSITIVITY) {
                undoSenseHistoricY = null
                hideUndoDismiss()
            }
        }

        return false
    }

    // undoSenseHistoricY = -1.0f
    private fun hideUndoDismiss() {
        ApplicationController.undoManager.clear()
        undoLayout.visibility = View.GONE
    }

    private fun onNumEventsUpdated() {
        updateUndoVisibility()
        val hasEvents = adapter.itemCount > 0
        find<TextView>(R.id.empty_view).visibility = if (hasEvents) View.GONE else View.VISIBLE;
        this.invalidateOptionsMenu();
    }


    private fun setUpItemTouchHelper() {

        val simpleItemTouchCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {

            // we want to cache these and not allocate anything repeatedly in the onChildDraw method
            internal val background = ColorDrawable(Color.RED)
            internal var xMark = resources.getDrawable(R.drawable.ic_clear_white_24dp)
            internal var xMarkMargin = 20 // TODO: xMarkMargin = this@EventListAdapter.getResources().getDimension(R.dimen.ic_clear_margin) as Int

            init {
                xMark.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_ATOP)
            }

            // not important, we don't want drag & drop
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                return false
            }

            override fun getSwipeDirs(recyclerView: RecyclerView?, viewHolder: RecyclerView.ViewHolder?): Int {
                val position = viewHolder!!.adapterPosition

                val adapter = recyclerView?.adapter as EventListAdapter?

                if (adapter != null && !adapter.isPendingRemoval(position)) {
                    return  makeFlag(ItemTouchHelper.ACTION_STATE_IDLE, ItemTouchHelper.RIGHT) or
                        makeFlag(ItemTouchHelper.ACTION_STATE_SWIPE, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT)
                }
                return super.getSwipeDirs(recyclerView, viewHolder)
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, swipeDir: Int) {
                val swipedPosition = viewHolder.adapterPosition

                (recyclerView.adapter as EventListAdapter?)?.pendingRemoval(swipedPosition)
            }

            override fun onChildDraw(
                c: Canvas, recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float, dY: Float,
                actionState: Int, isCurrentlyActive: Boolean) {

                val itemView = viewHolder.itemView

                // not sure why, but this method get's called for viewholder that are already swiped away
                if (viewHolder.adapterPosition == -1) {
                    // not interested in those
                    return
                }

                // draw red background
                background.setBounds(itemView.right + dX.toInt(), itemView.top, itemView.right, itemView.bottom)
                background.draw(c)

                // draw x mark
                val itemHeight = itemView.bottom - itemView.top
                val intrinsicWidth = xMark.intrinsicWidth
                val intrinsicHeight = xMark.intrinsicWidth

                val xMarkLeft = itemView.right - xMarkMargin - intrinsicWidth
                val xMarkRight = itemView.right - xMarkMargin
                val xMarkTop = itemView.top + (itemHeight - intrinsicHeight) / 2
                val xMarkBottom = xMarkTop + intrinsicHeight
                xMark.setBounds(xMarkLeft, xMarkTop, xMarkRight, xMarkBottom)

                xMark.draw(c)

                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        }

        ItemTouchHelper(simpleItemTouchCallback).attachToRecyclerView(recyclerView)
    }

    private fun setUpAnimationDecoratorHelper() {

        recyclerView?.addItemDecoration(object : RecyclerView.ItemDecoration() {

            // we want to cache this and not allocate anything repeatedly in the onDraw method
            internal var background = ColorDrawable(Color.RED)

            override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State?) {

                // only if animation is in progress
                if (parent.itemAnimator.isRunning) {

                    // some items might be animating down and some items might be animating up to close the gap left by the removed item
                    // this is not exclusive, both movement can be happening at the same time
                    // to reproduce this leave just enough items so the first one and the last one would be just a little off screen
                    // then remove one from the middle

                    // find first child with translationY > 0
                    // and last one with translationY < 0
                    // we're after a rect that is not covered in recycler-view views at this point in time
                    var lastViewComingDown: View? = null
                    var firstViewComingUp: View? = null

                    // this is fixed
                    val left = 0
                    val right = parent.width

                    // this we need to find out
                    var top = 0
                    var bottom = 0

                    // find relevant translating views
                    val childCount = parent.layoutManager.childCount
                    for (i in 0..childCount - 1) {
                        val child = parent.layoutManager.getChildAt(i)
                        if (child.translationY < 0) {
                            // view is coming down
                            lastViewComingDown = child
                        } else if (child.translationY > 0) {
                            // view is coming up
                            if (firstViewComingUp == null) {
                                firstViewComingUp = child
                            }
                        }
                    }

                    if (lastViewComingDown != null && firstViewComingUp != null) {
                        // views are coming down AND going up to fill the void
                        top = lastViewComingDown.bottom + lastViewComingDown.translationY.toInt()
                        bottom = firstViewComingUp.top + firstViewComingUp.translationY.toInt()
                    } else if (lastViewComingDown != null) {
                        // views are going down to fill the void
                        top = lastViewComingDown.bottom + lastViewComingDown.translationY.toInt()
                        bottom = lastViewComingDown.bottom
                    } else if (firstViewComingUp != null) {
                        // views are coming up to fill the void
                        top = firstViewComingUp.top
                        bottom = firstViewComingUp.top + firstViewComingUp.translationY.toInt()
                    }

                    background.setBounds(left, top, right, bottom)
                    background.draw(c)

                }
                super.onDraw(c, parent, state)
            }

        })
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
                        .putExtra(Consts.INTENT_INSTANCE_START_TIME_KEY, event.instanceStartTime))

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
            ApplicationController.dismissEvent(this, event, enableUndo=true);
            adapter.removeAt(position)
            undoSenseHistoricY = null
            onNumEventsUpdated()
        }
        refreshReminderLastFired()
    }

    // Item was already removed from UI, we just have to dismiss it now
    override fun onItemRemoved(event: EventAlertRecord) {

        logger.debug("onItemRemoved, eventId=${event.eventId}");

        logger.debug("Removing event id ${event.eventId} from DB and dismissing notification id ${event.notificationId}")
        ApplicationController.dismissEvent(this, event, enableUndo=false); // Undo works different way for compact view
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
                    .putExtra(Consts.INTENT_INSTANCE_START_TIME_KEY, event.instanceStartTime))
        }
        refreshReminderLastFired()
    }

/*
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
            CalendarIntents.viewCalendarEvent(this, event)

        refreshReminderLastFired()
    }
*/



    companion object {
        private val logger = Logger("ActivityMain")
    }
}
