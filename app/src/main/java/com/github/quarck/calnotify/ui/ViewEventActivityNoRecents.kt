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

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.support.design.widget.FloatingActionButton
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.text.format.DateUtils
import android.view.View
import android.widget.*
import com.github.quarck.calnotify.app.*
import com.github.quarck.calnotify.calendar.*
import com.github.quarck.calnotify.dismissedeventsstorage.EventDismissType
import com.github.quarck.calnotify.eventsstorage.EventsStorage
//import com.github.quarck.calnotify.logs.Logger
import com.github.quarck.calnotify.maps.MapsIntents
import com.github.quarck.calnotify.quiethours.QuietHoursManager
import com.github.quarck.calnotify.textutils.EventFormatter
import com.github.quarck.calnotify.textutils.EventFormatterInterface
import com.github.quarck.calnotify.utils.*
import java.util.*
import com.github.quarck.calnotify.*
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.permissions.PermissionsManager
import android.content.res.ColorStateList
import android.os.Handler
import android.support.v4.content.ContextCompat
import android.text.method.ScrollingMovementMethod

// TODO: add repeating rule and calendar name somewhere on the snooze activity

enum class ViewEventActivityStateCode(val code: Int) {
    Normal(0),
    CustomSnoozeOpened(1),
    SnoozeUntilOpenedDatePicker(2),
    SnoozeUntilOpenedTimePicker(3);

    companion object {
        fun fromInt(v: Int): ViewEventActivityStateCode {
            return values()[v];
        }
    }
}

data class ViewEventActivityState(
        var state: ViewEventActivityStateCode = ViewEventActivityStateCode.Normal,
        var timeAMillis: Long = 0L,
        var timeBMillis: Long = 0L
) {
    fun toBundle(bundle: Bundle) {
        bundle.putInt(KEY_STATE_CODE, state.code)
        bundle.putLong(KEY_TIME_A, timeAMillis)
        bundle.putLong(KEY_TIME_B, timeBMillis)
    }

    companion object {
        fun fromBundle(bundle: Bundle): ViewEventActivityState {

            val code = bundle.getInt(KEY_STATE_CODE, 0)
            val timeA = bundle.getLong(KEY_TIME_A, 0L)
            val timeB = bundle.getLong(KEY_TIME_B, 0L)

            return ViewEventActivityState(ViewEventActivityStateCode.fromInt(code), timeA, timeB)
        }

        const val KEY_STATE_CODE = "code"
        const val KEY_TIME_A = "timeA"
        const val KEY_TIME_B = "timeB"
    }
}

class ViewEventById(private val context: Context, internal var eventId: Long) : Runnable {
    override fun run() {
        CalendarIntents.viewCalendarEvent(context, eventId)
    }
}

class ViewEventByEvent(private val context: Context, internal var event: EventAlertRecord) : Runnable {
    override fun run() {
        CalendarIntents.viewCalendarEvent(context, event)
    }
}

open class ViewEventActivityNoRecents : AppCompatActivity() {

    var state = ViewEventActivityState()

    lateinit var event: EventAlertRecord

    lateinit var calendar: CalendarRecord

    lateinit var snoozePresets: LongArray

    lateinit var settings: Settings

    lateinit var formatter: EventFormatterInterface

    val calendarReloadManager: CalendarReloadManagerInterface = CalendarReloadManager
    val calendarProvider: CalendarProviderInterface = CalendarProvider

    val handler = Handler()

//    var snoozeAllIsChange = false

    var snoozeFromMainActivity = false

    val snoozePresetControlIds = intArrayOf(
            R.id.snooze_view_snooze_present1,
            R.id.snooze_view_snooze_present2,
            R.id.snooze_view_snooze_present3,
            R.id.snooze_view_snooze_present4,
            R.id.snooze_view_snooze_present5,
            R.id.snooze_view_snooze_present6
    )

    val snoozePresentQuietTimeReminderControlIds = intArrayOf(
            R.id.snooze_view_snooze_present1_quiet_time_notice,
            R.id.snooze_view_snooze_present2_quiet_time_notice,
            R.id.snooze_view_snooze_present3_quiet_time_notice,
            R.id.snooze_view_snooze_present4_quiet_time_notice,
            R.id.snooze_view_snooze_present5_quiet_time_notice,
            R.id.snooze_view_snooze_present6_quiet_time_notice
    )

    var baselineIds = intArrayOf(
            R.id.snooze_view_snooze_present1_quiet_time_notice_baseline,
            R.id.snooze_view_snooze_present2_quiet_time_notice_baseline,
            R.id.snooze_view_snooze_present3_quiet_time_notice_baseline,
            R.id.snooze_view_snooze_present4_quiet_time_notice_baseline,
            R.id.snooze_view_snooze_present5_quiet_time_notice_baseline,
            R.id.snooze_view_snooze_present6_quiet_time_notice_baseline
    )

    private val undoManager by lazy { UndoManager }

    // These dialog controls moved here so saveInstanceState could store current time selection
    var customSnooze_TimeIntervalPickerController: TimeIntervalPickerController? = null
    var snoozeUntil_DatePicker: DatePicker? = null
    var snoozeUntil_TimePicker: TimePicker? = null

    lateinit var calendarNameTextView: TextView
    lateinit var calendarAccountTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        if (!PermissionsManager.hasAllPermissions(this)) {
            finish()
            return
        }

        if (savedInstanceState != null)
            state = ViewEventActivityState.fromBundle(savedInstanceState)

        setContentView(R.layout.activity_view)

        val currentTime = System.currentTimeMillis()

        settings = Settings(this)
        formatter = EventFormatter(this)

        // Populate event details
        val eventId = intent.getLongExtra(Consts.INTENT_EVENT_ID_KEY, -1)
        val instanceStartTime = intent.getLongExtra(Consts.INTENT_INSTANCE_START_TIME_KEY, -1L)

        //snoozeAllIsChange = intent.getBooleanExtra(Consts.INTENT_SNOOZE_ALL_IS_CHANGE, false)

        snoozeFromMainActivity = intent.getBooleanExtra(Consts.INTENT_SNOOZE_FROM_MAIN_ACTIVITY, false)

        find<Toolbar?>(R.id.toolbar)?.visibility = View.GONE

        // load event if it is not a "snooze all"
        EventsStorage(this).use {
            db ->

            var dbEvent = db.getEvent(eventId, instanceStartTime)

            if (dbEvent != null) {
                val eventDidChange = calendarReloadManager.reloadSingleEvent(this, db, dbEvent, calendarProvider, null)
                if (eventDidChange) {
                    val newDbEvent = db.getEvent(eventId, instanceStartTime)
                    if (newDbEvent != null) {
                        dbEvent = newDbEvent
                    } else {
                        DevLog.error(this, LOG_TAG, "ViewActivity: cannot find event after calendar reload, event $eventId, inst $instanceStartTime")
                    }
                }
            }

            if (dbEvent == null) {
                DevLog.error(this, LOG_TAG, "ViewActivity started for non-existing eveng id $eventId, st $instanceStartTime")
                finish()
                return
            }

            event = dbEvent
        }

        calendar = calendarProvider.getCalendarById(this, event.calendarId)
                ?: calendarProvider.createCalendarNotFoundCal(this)

        calendarNameTextView = findOrThrow<TextView>(R.id.view_event_calendar_name)
        calendarNameTextView.text = calendar.displayName

        calendarAccountTextView = findOrThrow<TextView>(R.id.view_event_calendar_account)
        calendarAccountTextView.text = calendar.accountName

        snoozePresets = settings.snoozePresets

        // remove "MM minutes before event" snooze presents for "Snooze All"
        // and when event time has passed already
        if (event.displayedStartTime < currentTime)
            snoozePresets = snoozePresets.filter { it > 0L }.toLongArray()

        val isQuiet =
                QuietHoursManager.isInsideQuietPeriod(
                        settings,
                        snoozePresets.map { it -> currentTime + it }.toLongArray())

        // Populate snooze controls
        for ((idx, id) in snoozePresetControlIds.withIndex()) {
            val snoozeLable = findOrThrow<TextView>(id);
            val quietTimeNotice = findOrThrow<TextView>(snoozePresentQuietTimeReminderControlIds[idx])
            val quietTimeNoticeBaseline = findOrThrow<TextView>(baselineIds[idx])

            if (idx < snoozePresets.size) {
                snoozeLable.text = formatPreset(snoozePresets[idx])
                snoozeLable.visibility = View.VISIBLE;
                quietTimeNoticeBaseline.visibility = View.VISIBLE

                if (isQuiet[idx])
                    quietTimeNotice.visibility = View.VISIBLE
                else
                    quietTimeNotice.visibility = View.GONE
            }
            else {
                snoozeLable.visibility = View.GONE;
                quietTimeNotice.visibility = View.GONE
                quietTimeNoticeBaseline.visibility = View.GONE
            }
        }

        // need to hide these guys
        val showCustomSnoozeVisibility = View.VISIBLE
        findOrThrow<TextView>(R.id.snooze_view_snooze_custom).visibility = showCustomSnoozeVisibility
        val snoozeCustom = find<TextView?>(R.id.snooze_view_snooze_until)
        if (snoozeCustom != null)
            snoozeCustom.visibility = showCustomSnoozeVisibility

        val location = event.location;
        if (location != "") {
            findOrThrow<View>(R.id.snooze_view_location_layout).visibility = View.VISIBLE;
            val locationView = findOrThrow<TextView>(R.id.snooze_view_location)
            locationView.text = location;
            locationView.setOnClickListener { MapsIntents.openLocation(this, event.location) }
        }

        val title = findOrThrow<TextView>(R.id.snooze_view_title)
        title.text = if (event.title.isNotEmpty()) event.title else this.resources.getString(R.string.empty_title);

        val (line1, line2) = formatter.formatDateTimeTwoLines(event);

        val dateTimeFirstLine = findOrThrow<TextView>(R.id.snooze_view_event_date_line1)
        val dateTimeSecondLine = findOrThrow<TextView>(R.id.snooze_view_event_date_line2)

        dateTimeFirstLine.text = line1;

        if (line2.isEmpty())
            dateTimeSecondLine.visibility = View.GONE
        else
            dateTimeSecondLine.text = line2;

        dateTimeFirstLine.isClickable = false
        dateTimeSecondLine.isClickable = false
        title.isClickable = false

        title.setMovementMethod(ScrollingMovementMethod())
        title.post {
            val y = title.getLayout().getLineTop(0)
            title.scrollTo(0, y)
        }
        title.setTextIsSelectable(true)

        if (event.desc.isNotEmpty()) {
            // Show the event desc
            findOrThrow<RelativeLayout>(R.id.layout_event_description).visibility = View.VISIBLE
            findOrThrow<TextView>(R.id.snooze_view_event_description).text = event.desc
        }

        var color: Int = event.color.adjustCalendarColor()
        if (color == 0)
            color = ContextCompat.getColor(this, R.color.primary)

        val colorDrawable = ColorDrawable(color)
        findOrThrow<RelativeLayout>(R.id.snooze_view_event_details_layout).background = colorDrawable

        window.statusBarColor = color.scaleColor(0.7f)

//        val shouldOfferMove = (!event.isRepeating) && (DateTimeUtils.isUTCTodayOrInThePast(event.startTime))
        val shouldOfferMove = (DateTimeUtils.isUTCTodayOrInThePast(event.startTime))
        if (shouldOfferMove) {
            findOrThrow<RelativeLayout>(R.id.snooze_reschedule_layout).visibility = View.VISIBLE
            if (event.isRepeating) {
                findOrThrow<TextView>(R.id.snooze_reschedule_for).text = getString(R.string.change_event_time_repeating_event)
            }
        }
        else {
            find<View?>(R.id.snooze_view_inter_view_divider)?.visibility = View.GONE
        }

        if (event.snoozedUntil != 0L) {
            findOrThrow<TextView>(R.id.snooze_snooze_for).text = resources.getString(R.string.change_snooze_to)
        }

        val nextReminderLayout: RelativeLayout? = find<RelativeLayout>(R.id.layout_next_reminder)
        val nextReminderText: TextView? = find<TextView>(R.id.snooze_view_next_reminder)

        if (nextReminderLayout != null && nextReminderText != null) {

            val nextReminder = calendarProvider.getNextEventReminderTime(this, event)

            if (nextReminder != 0L) {
                nextReminderLayout.visibility = View.VISIBLE
                nextReminderText.visibility = View.VISIBLE

                val format = this.resources.getString(R.string.next_reminder_fmt)

                nextReminderText.text = format.format(formatter.formatTimePoint(nextReminder))
            }
        }


        val fab = findOrThrow<FloatingActionButton>(R.id.floating_edit_button)

        if (!calendar.isReadOnly) {
            if (!event.isRepeating) {

                fab.setOnClickListener { _ ->
                    val intent = Intent(this, EditEventActivity::class.java)
                    intent.putExtra(EditEventActivity.EVENT_ID, event.eventId)
                    startActivity(intent)
                    finish()
                }

            } else {
                fab.setOnClickListener { _ ->
                    CalendarIntents.viewCalendarEvent(this, event)
                    finish()
                }
            }

            val states = arrayOf(intArrayOf(android.R.attr.state_enabled), // enabled
                    intArrayOf(android.R.attr.state_pressed)  // pressed
            )

            val colors = intArrayOf(
                    event.color.adjustCalendarColor(false),
                    event.color.adjustCalendarColor(true)
            )

            fab.backgroundTintList = ColorStateList(states, colors)
        }
        else  {
            fab.visibility = View.GONE
        }

        val menuButton = find<ImageView?>(R.id.snooze_view_menu)
        menuButton?.setOnClickListener { showDismissEditPopup(menuButton) }

        ApplicationController.cleanupEventReminder(this)

        restoreState(state)
    }

    fun showDismissEditPopup(v: View) {
        val popup = PopupMenu(this, v)
        val inflater = popup.menuInflater
        inflater.inflate(R.menu.snooze, popup.menu)

        val menuItem = popup.menu.findItem(R.id.action_delete_event)
        if (menuItem != null) {
            menuItem.isVisible = !event.isRepeating
        }

        val menuItemMute = popup.menu.findItem(R.id.action_mute_event)
        if (menuItemMute != null) {
            menuItemMute.isVisible = !event.isMuted && !event.isTask
        }

        val menuItemUnMute = popup.menu.findItem(R.id.action_unmute_event)
        if (menuItemUnMute != null) {
            menuItemUnMute.isVisible = event.isMuted
        }

        if (event.isTask) {
            val menuItemDismiss = popup.menu.findItem(R.id.action_dismiss_event)
            val menuItemDone = popup.menu.findItem(R.id.action_done_event)
            if (menuItemDismiss != null && menuItemDone != null) {
                menuItemDismiss.isVisible = false
                menuItemDone.isVisible = true
            }
        }

        /*    <item
        android:id="@+id/action_mute_event"
        android:title="@string/mute_notification"
        android:visible="false"
        />

    <item
        android:id="@+id/action_unmute_event"
        android:title="@string/un_mute_notification"
        android:visible="false"
        />*/

        popup.setOnMenuItemClickListener {
            item ->

            when (item.itemId) {
                R.id.action_dismiss_event, R.id.action_done_event -> {
                    ApplicationController.dismissEvent(this, EventDismissType.ManuallyDismissedFromActivity, event)
                    undoManager.addUndoState(
                            UndoState(undo = Runnable { ApplicationController.restoreEvent(applicationContext, event) }))
                    finish()
                    true
                }

                R.id.action_delete_event -> {

                    AlertDialog.Builder(this)
                            .setMessage(getString(R.string.delete_event_question))
                            .setCancelable(false)
                            .setPositiveButton(android.R.string.yes) { _, _ ->

                                DevLog.info(this, LOG_TAG, "Deleting event ${event.eventId} per user request")

                                val success = ApplicationController.dismissAndDeleteEvent(
                                        this, EventDismissType.ManuallyDismissedFromActivity, event
                                )
                                if (success) {
                                    undoManager.addUndoState(
                                            UndoState(undo = Runnable { ApplicationController.restoreEvent(applicationContext, event) }))
                                }
                                finish()
                            }
                            .setNegativeButton(R.string.cancel) { _, _ ->
                            }
                            .create()
                            .show()

                    true
                }

                R.id.action_mute_event -> {
                    ApplicationController.toggleMuteForEvent(this, event.eventId, event.instanceStartTime, 0)
                    event.isMuted = true

                    true
                }

                R.id.action_unmute_event -> {
                    ApplicationController.toggleMuteForEvent(this, event.eventId, event.instanceStartTime, 1)
                    event.isMuted = false
                    true
                }

                R.id.action_open_in_calendar -> {
                    CalendarIntents.viewCalendarEvent(this, event)
                    finish()
                    true
                }
                else -> false
            }
        }

        popup.show()
    }

    private fun formatPreset(preset: Long): String {
        val num: Long
        val unit: String
        val presetSeconds = preset / 1000L;

        if (presetSeconds == 0L)
            return resources.getString(R.string.until_event_time)

        if (presetSeconds % Consts.DAY_IN_SECONDS == 0L) {
            num = presetSeconds / Consts.DAY_IN_SECONDS;
            unit =
                    if (num != 1L)
                        resources.getString(R.string.days)
                    else
                        resources.getString(R.string.day)
        }
        else if (presetSeconds % Consts.HOUR_IN_SECONDS == 0L) {
            num = presetSeconds / Consts.HOUR_IN_SECONDS;
            unit =
                    if (num != 1L)
                        resources.getString(R.string.hours)
                    else
                        resources.getString(R.string.hour)
        }
        else {
            num = presetSeconds / Consts.MINUTE_IN_SECONDS;
            unit =
                    if (num != 1L)
                        resources.getString(R.string.minutes)
                    else
                        resources.getString(R.string.minute)
        }

        if (num <= 0) {
            val beforeEventString = resources.getString(R.string.before_event)
            return "${-num} $unit $beforeEventString"
        }
        return "$num $unit"
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    fun onButtonCancelClick(v: View?) {
        finish();
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    fun OnButtonEventDetailsClick(v: View?) {
        CalendarIntents.viewCalendarEvent(this, event)
    }

    private fun snoozeEvent(snoozeDelay: Long) {
        DevLog.debug(LOG_TAG, "Snoozing event id ${event.eventId}, snoozeDelay=${snoozeDelay / 1000L}")

        val result = ApplicationController.snoozeEvent(this, event.eventId, event.instanceStartTime, snoozeDelay);
        if (result != null) {
            result.toast(this)
        }
        finish()
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    fun OnButtonSnoozeClick(v: View?) {
        if (v == null)
            return

        for ((idx, id) in snoozePresetControlIds.withIndex()) {
            if (id == v.id) {
                snoozeEvent(snoozePresets[idx]);
                break;
            }
        }
    }

    public override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        when (state.state) {
            ViewEventActivityStateCode.Normal -> {
            }
            ViewEventActivityStateCode.CustomSnoozeOpened -> {
                state.timeAMillis = customSnooze_TimeIntervalPickerController?.intervalMilliseconds ?: 0L
            }
            ViewEventActivityStateCode.SnoozeUntilOpenedDatePicker -> {

                val datePicker = snoozeUntil_DatePicker
                if (datePicker != null) {
                    datePicker.clearFocus()

                    val date = Calendar.getInstance()
                    date.set(datePicker.year, datePicker.month, datePicker.dayOfMonth, 0, 0, 0)
                    state.timeAMillis = date.timeInMillis
                    state.timeBMillis = event?.snoozedUntil ?: 0L
                }
            }
            ViewEventActivityStateCode.SnoozeUntilOpenedTimePicker -> {

                val timePicker = snoozeUntil_TimePicker
                if (timePicker != null) {
                    timePicker.clearFocus()

                    val time = Calendar.getInstance()
                    time.timeInMillis = state.timeAMillis
                    time.set(Calendar.HOUR_OF_DAY, timePicker.hour)
                    time.set(Calendar.MINUTE, timePicker.minute)

                    state.timeBMillis = time.timeInMillis
                }
            }
        }
        // val intervalMilliseconds = customSnooze_TimeIntervalPickerController?.intervalMilliseconds ?: 0L
        state.toBundle(outState)
    }

    private fun restoreState(state: ViewEventActivityState) {

        when (state.state) {
            ViewEventActivityStateCode.Normal -> {

            }
            ViewEventActivityStateCode.CustomSnoozeOpened -> {
                customSnoozeShowDialog(state.timeAMillis)
            }
            ViewEventActivityStateCode.SnoozeUntilOpenedDatePicker -> {
                snoozeUntilShowDatePickerDialog(state.timeAMillis, state.timeBMillis)
            }
            ViewEventActivityStateCode.SnoozeUntilOpenedTimePicker -> {
                snoozeUntilShowTimePickerDialog(state.timeAMillis, state.timeBMillis)
            }
        }
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    fun OnButtonCustomSnoozeClick(v: View?) {
        customSnoozeShowSimplifiedDialog(persistentState.lastCustomSnoozeIntervalMillis)
    }

    fun customSnoozeShowSimplifiedDialog(initialTimeValue: Long) {

        val intervalNames: Array<String> = this.resources.getStringArray(R.array.default_snooze_intervals)
        val intervalValues = this.resources.getIntArray(R.array.default_snooze_intervals_seconds_values)

        val builder = AlertDialog.Builder(this)

        val adapter = ArrayAdapter<String>(this, R.layout.simple_list_item_medium)

        adapter.addAll(intervalNames.toMutableList())

        builder.setCancelable(true)

        builder.setAdapter(adapter) {
            _, which ->
            if (which in 0..intervalValues.size-1) {

                val intervalSeconds = intervalValues[which].toLong()
                if (intervalSeconds != -1L) {
                    snoozeEvent(intervalSeconds * 1000L)
                } else {
                    customSnoozeShowDialog(initialTimeValue)
                }
            }
        }

        builder.show()
    }

    fun customSnoozeShowDialog(initialTimeValue: Long) {

        val dialogView = this.layoutInflater.inflate(R.layout.dialog_interval_picker, null);

        val timeIntervalPicker = TimeIntervalPickerController(dialogView, R.string.snooze_for, 0, false)
        timeIntervalPicker.intervalMilliseconds = initialTimeValue

        state.state = ViewEventActivityStateCode.CustomSnoozeOpened
        customSnooze_TimeIntervalPickerController = timeIntervalPicker

        val builder = AlertDialog.Builder(this)

        builder.setView(dialogView)

        builder.setPositiveButton(R.string.snooze) {
            _: DialogInterface?, _: Int ->

            val intervalMilliseconds = timeIntervalPicker.intervalMilliseconds
            this.persistentState.lastCustomSnoozeIntervalMillis = intervalMilliseconds

            snoozeEvent(intervalMilliseconds)

            state.state = ViewEventActivityStateCode.Normal
            customSnooze_TimeIntervalPickerController = null
        }

        builder.setNegativeButton(R.string.cancel) {
            _: DialogInterface?, _: Int ->

            state.state = ViewEventActivityStateCode.Normal
            customSnooze_TimeIntervalPickerController = null
        }

        builder.create().show()
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    fun OnButtonSnoozeUntilClick(v: View?) {
        snoozeUntilShowDatePickerDialog(event.snoozedUntil, event.snoozedUntil)
    }

    fun inflateDatePickerDialog() = layoutInflater?.inflate(R.layout.dialog_date_picker, null)

    fun inflateTimePickerDialog() = layoutInflater?.inflate(R.layout.dialog_time_picker, null)

    fun snoozeUntilShowDatePickerDialog(initialValueForDate: Long, initialValueForTime: Long) {

        val dialogDate = inflateDatePickerDialog() ?: return

        val datePicker = dialogDate.findOrThrow<DatePicker>(R.id.datePickerCustomSnooze)

        state.state = ViewEventActivityStateCode.SnoozeUntilOpenedDatePicker
        snoozeUntil_DatePicker = datePicker

        val firstDayOfWeek = Settings(this).firstDayOfWeek
        if (firstDayOfWeek != -1)
            snoozeUntil_DatePicker?.firstDayOfWeek = firstDayOfWeek

        if (initialValueForDate != 0L) {
            val cal = Calendar.getInstance()
            cal.timeInMillis = initialValueForDate

            val year = cal.get(Calendar.YEAR)
            val month = cal.get(Calendar.MONTH)
            val day = cal.get(Calendar.DAY_OF_MONTH)

            snoozeUntil_DatePicker?.updateDate(year, month, day)
        }

        val builder = AlertDialog.Builder(this)

        builder.setView(dialogDate)

        builder.setPositiveButton(R.string.next) {
            _: DialogInterface?, _: Int ->

            datePicker.clearFocus()

            val date = Calendar.getInstance()
            date.set(datePicker.year, datePicker.month, datePicker.dayOfMonth, 0, 0, 0)

            snoozeUntilShowTimePickerDialog(date.timeInMillis, initialValueForTime)
        }

        builder.setNegativeButton(R.string.cancel) {
            _: DialogInterface?, _: Int ->

            state.state = ViewEventActivityStateCode.Normal
            snoozeUntil_DatePicker = null
        }

        builder.create().show()

    }

    fun snoozeUntilShowTimePickerDialog(currentDateSelection: Long, initialTimeValue: Long) {

        val date = Calendar.getInstance()
        date.timeInMillis = currentDateSelection

        val dialogTime = inflateTimePickerDialog() ?: return

        val timePicker: TimePicker = dialogTime.findOrThrow<TimePicker>(R.id.timePickerCustomSnooze)
        timePicker.setIs24HourView(android.text.format.DateFormat.is24HourFormat(this))

        state.state = ViewEventActivityStateCode.SnoozeUntilOpenedTimePicker
        state.timeAMillis = currentDateSelection
        snoozeUntil_TimePicker = timePicker
        snoozeUntil_DatePicker = null

        if (initialTimeValue != 0L) {
            val cal = Calendar.getInstance()
            cal.timeInMillis = initialTimeValue

            timePicker.hour = cal.get(Calendar.HOUR_OF_DAY)
            timePicker.minute = cal.get(Calendar.MINUTE)
        }

        val title = dialogTime.findOrThrow<TextView>(R.id.textViewSnoozeUntilDate)
        title.text =
                String.format(
                        resources.getString(R.string.choose_time),
                        DateUtils.formatDateTime(this, date.timeInMillis, DateUtils.FORMAT_SHOW_DATE))

        val builder = AlertDialog.Builder(this)
        builder.setView(dialogTime)
        builder.setPositiveButton(R.string.snooze) {
            _: DialogInterface?, _: Int ->

            state.state = ViewEventActivityStateCode.Normal
            snoozeUntil_TimePicker = null

            timePicker.clearFocus()

            // grab time from timePicker + date picker

            date.set(Calendar.HOUR_OF_DAY, timePicker.hour)
            date.set(Calendar.MINUTE, timePicker.minute)

            val snoozeFor = date.timeInMillis - System.currentTimeMillis() + Consts.ALARM_THRESHOLD

            if (snoozeFor > 0L) {
                snoozeEvent(snoozeFor)
            }
            else {
                // Selected time is in the past
                AlertDialog.Builder(this)
                        .setTitle(R.string.selected_time_is_in_the_past)
                        .setNegativeButton(R.string.cancel) {
                            _: DialogInterface?, _: Int ->
                        }
                        .create()
                        .show()
            }

        }

        builder.setNegativeButton(R.string.cancel) {
            _: DialogInterface?, _: Int ->

            state.state = ViewEventActivityStateCode.Normal
            snoozeUntil_TimePicker = null
        }

        builder.create().show()
    }

    fun reschedule(addTime: Long) {

        DevLog.info(this, LOG_TAG, "Moving event ${event.eventId} by ${addTime / 1000L} seconds, isRepeating = ${event.isRepeating}");

        if (!event.isRepeating) {
            val moved = ApplicationController.moveEvent(this, event, addTime)

            if (moved) {
                // Show
                if (Settings(this).viewAfterEdit) {
                    handler.postDelayed({
                        CalendarIntents.viewCalendarEvent(this, event)
                        finish()
                    }, 100)
                }
                else {
                    SnoozeResult(SnoozeType.Moved, event.startTime, 0L).toast(this)
                    // terminate ourselves
                    finish();
                }
            } else {
                DevLog.info(this, LOG_TAG, "snooze: Failed to move event ${event.eventId} by ${addTime / 1000L} seconds")
            }
        }
        else {
            val newEventId = ApplicationController.moveAsCopy(this, calendar, event, addTime)

            if (newEventId != -1L) {
                // Show
                if (Settings(this).viewAfterEdit) {
                    handler.postDelayed({
                        CalendarIntents.viewCalendarEvent(this, newEventId)
                        finish()
                    }, 100)
                }
                else {
                    SnoozeResult(SnoozeType.Moved, event.startTime, 0L).toast(this)
                    // terminate ourselves
                    finish();
                }
            } else {
                DevLog.info(this, LOG_TAG, "snooze: Failed to move event ${event.eventId} by ${addTime / 1000L} seconds")
            }
        }
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    fun OnButtonRescheduleClick(v: View?) {
        if (v == null)
            return

        when (v.id) {
            R.id.snooze_view_reschedule_present1 ->
                reschedule(Consts.HOUR_IN_SECONDS * 1000L)
            R.id.snooze_view_reschedule_present2 ->
                reschedule(Consts.DAY_IN_SECONDS * 1000L)
            R.id.snooze_view_reschedule_present3 ->
                reschedule(Consts.DAY_IN_SECONDS * 7L * 1000L)
            R.id.snooze_view_reschedule_present4 ->
                reschedule(Consts.DAY_IN_SECONDS * 28L * 1000L)
        }
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    fun OnButtonRescheduleCustomClick(v: View?) {
    }

    companion object {
        private const val LOG_TAG = "ActivitySnooze"

        const val CUSTOM_SNOOZE_SNOOZE_FOR_IDX = 0
        const val CUSTOM_SNOOZE_SNOOZE_UNTIL_IDX = 1

    }

}
