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
import android.content.DialogInterface
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
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


enum class SnoozeActivityStateCode(val code: Int) {
    Normal(0),
    CustomSnoozeOpened(1),
    SnoozeUntilOpenedDatePicker(2),
    SnoozeUntilOpenedTimePicker(3);

    companion object {
        fun fromInt(v: Int): SnoozeActivityStateCode {
            return values()[v];
        }
    }
}

data class SnoozeActivityState(
        var state: SnoozeActivityStateCode = SnoozeActivityStateCode.Normal,
        var timeAMillis: Long = 0L,
        var timeBMillis: Long = 0L
) {
    fun toBundle(bundle: Bundle) {
        bundle.putInt(KEY_STATE_CODE, state.code)
        bundle.putLong(KEY_TIME_A, timeAMillis)
        bundle.putLong(KEY_TIME_B, timeBMillis)
    }

    companion object {
        fun fromBundle(bundle: Bundle): SnoozeActivityState {

            val code = bundle.getInt(KEY_STATE_CODE, 0)
            val timeA = bundle.getLong(KEY_TIME_A, 0L)
            val timeB = bundle.getLong(KEY_TIME_B, 0L)

            return SnoozeActivityState(SnoozeActivityStateCode.fromInt(code), timeA, timeB)
        }

        const val KEY_STATE_CODE = "code"
        const val KEY_TIME_A = "timeA"
        const val KEY_TIME_B = "timeB"
    }
}

open class SnoozeActivityNoRecents : AppCompatActivity() {

    var state = SnoozeActivityState()

    // var originalEvent: EventAlertRecord? = null
    var event: EventAlertRecord? = null

    lateinit var snoozePresets: LongArray

    lateinit var settings: Settings

    lateinit var formatter: EventFormatterInterface

    val calendarReloadManager: CalendarReloadManagerInterface = CalendarReloadManager
    val calendarProvider: CalendarProviderInterface = CalendarProvider

    var snoozeAllIsChange = false

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


    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        if (!PermissionsManager.hasAllPermissions(this)) {
            finish()
            return
        }

        if (savedInstanceState != null)
            state = SnoozeActivityState.fromBundle(savedInstanceState)

        setContentView(R.layout.activity_snooze)

        val currentTime = System.currentTimeMillis()

        settings = Settings(this)
        formatter = EventFormatter(this)

        // Populate event details
        val eventId = intent.getLongExtra(Consts.INTENT_EVENT_ID_KEY, -1)
        val instanceStartTime = intent.getLongExtra(Consts.INTENT_INSTANCE_START_TIME_KEY, -1L)

        snoozeAllIsChange = intent.getBooleanExtra(Consts.INTENT_SNOOZE_ALL_IS_CHANGE, false)

        snoozeFromMainActivity = intent.getBooleanExtra(Consts.INTENT_SNOOZE_FROM_MAIN_ACTIVITY, false)

        val isSnoozeAll = (eventId == -1L)

        val toolbar = find<Toolbar?>(R.id.toolbar)
        if (isSnoozeAll) {
            setSupportActionBar(toolbar)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.setDisplayShowHomeEnabled(true)
        }
        else {
            toolbar?.visibility = View.GONE
        }

        // load event if it is not a "snooze all"
        if (!isSnoozeAll) {
            EventsStorage(this).use {
                db ->

                val eventFromDB = db.getEvent(eventId, instanceStartTime)
                if (eventFromDB != null) {

                    val eventReloaded = eventFromDB.copy()
                    calendarReloadManager.reloadSingleEvent(this, db, eventReloaded, calendarProvider, null) // would leave it for later for now
                    event = eventReloaded
                }
            }
        }

        val ev = event // so we can check it only once for null

        snoozePresets = settings.snoozePresets

        // remove "MM minutes before event" snooze presents for "Snooze All"
        // and when event time has passed already
        if (isSnoozeAll || ev != null && ev.displayedStartTime < currentTime)
            snoozePresets = snoozePresets.filter { it > 0L }.toLongArray()

        val isQuiet =
                QuietHoursManager.isInsideQuietPeriod(
                        settings,
                        snoozePresets.map { it -> currentTime + it }.toLongArray())

        // Populate snooze controls
        for ((idx, id) in snoozePresetControlIds.withIndex()) {
            val snoozeLable = find<TextView>(id);
            val quietTimeNotice = find<TextView>(snoozePresentQuietTimeReminderControlIds[idx])
            val quietTimeNoticeBaseline = find<TextView>(baselineIds[idx])

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
        val showCustomSnoozeVisibility = if (settings.showCustomSnoozeAndUntil) View.VISIBLE else View.GONE
        find<TextView>(R.id.snooze_view_snooze_custom).visibility = showCustomSnoozeVisibility
        val snoozeCustom = find<TextView?>(R.id.snooze_view_snooze_until)
        if (snoozeCustom != null)
            snoozeCustom.visibility = showCustomSnoozeVisibility

        if (!isSnoozeAll && ev != null) {
            val location = ev.location;
            if (location != "") {
                find<View>(R.id.snooze_view_location_layout).visibility = View.VISIBLE;
                val locationView = find<TextView>(R.id.snooze_view_location)
                locationView.text = location;
                locationView.setOnClickListener { MapsIntents.openLocation(this, ev.location) }
            }

            val title =
                    if (ev.title == "")
                        this.resources.getString(R.string.empty_title)
                    else
                        ev.title;

            find<TextView>(R.id.snooze_view_title).text = title;

            val (line1, line2) = formatter.formatDateTimeTwoLines(ev);

            val dateTimeFirstLine = find<TextView>(R.id.snooze_view_event_date_line1)
            val dateTimeSecondLine = find<TextView>(R.id.snooze_view_event_date_line2)

            dateTimeFirstLine.text = line1;

            if (line2.isEmpty())
                dateTimeSecondLine.visibility = View.GONE
            else
                dateTimeSecondLine.text = line2;

            val onClick = View.OnClickListener { CalendarIntents.viewCalendarEvent(this, ev) }

            dateTimeFirstLine.setOnClickListener(onClick)
            dateTimeSecondLine.setOnClickListener(onClick)

            var color: Int = ev.color.adjustCalendarColor(settings.darkerCalendarColors)
            if (color == 0)
                color = resources.getColor(R.color.primary)

            val colorDrawable = ColorDrawable(color)
            find<RelativeLayout>(R.id.snooze_view_event_details_layout).background = colorDrawable

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                window.statusBarColor = color.scaleColor(0.7f)
            }

            if (!ev.isRepeating) {
                find<RelativeLayout>(R.id.snooze_reschedule_layout).visibility = View.VISIBLE
            }
            else {
                find<View?>(R.id.snooze_view_inter_view_divider)?.visibility = View.GONE
            }

            if (ev.snoozedUntil != 0L) {
                find<TextView>(R.id.snooze_snooze_for).text = resources.getString(R.string.change_snooze_to)
            }

            val nextReminderLayout: RelativeLayout? = find<RelativeLayout>(R.id.layout_next_reminder)
            val nextReminderText: TextView? = find<TextView>(R.id.snooze_view_next_reminder)

            if (nextReminderLayout != null && nextReminderText != null) {

                val nextReminder = calendarProvider.getNextEventReminderTime(this, ev)

                if (nextReminder != 0L) {
                    nextReminderLayout.visibility = View.VISIBLE
                    nextReminderText.visibility = View.VISIBLE

                    val format = this.resources.getString(R.string.next_reminder_fmt)

                    nextReminderText.text = format.format(formatter.formatTimePoint(nextReminder))
                }
            }

        }
        else if (isSnoozeAll) {

            find<TextView>(R.id.snooze_snooze_for).text =
                    if (!snoozeAllIsChange)
                        this.resources.getString(R.string.snooze_all_events)
                    else
                        this.resources.getString(R.string.change_all_events)

            find<RelativeLayout>(R.id.layout_snooze_time).visibility = View.GONE
            find<View>(R.id.view_snooze_divider).visibility = View.GONE
            find<TextView>(R.id.snooze_view_event_date_line1).text = ""
            find<TextView>(R.id.snooze_view_event_date_line2).text = ""

            find<ImageView?>(R.id.snooze_view_img_custom_period)?.visibility = View.VISIBLE
            find<ImageView?>(R.id.snooze_view_img_until)?.visibility = View.VISIBLE


            this.title =
                    if (!snoozeAllIsChange)
                        resources.getString(R.string.snooze_all_title)
                    else
                        resources.getString(R.string.change_all_title)

            find<ImageView>(R.id.snooze_view_cancel).visibility = View.GONE
            find<RelativeLayout>(R.id.snooze_view_event_details_layout).visibility = View.GONE

            find<View?>(R.id.snooze_view_inter_view_divider)?.visibility = View.GONE
        }
        else {
            // not a single event and not snooze all - some mess
            finish()
        }

        if (event != null) {
            val menuButton = find<ImageView?>(R.id.snooze_view_menu)
            menuButton?.setOnClickListener { showDismissEditPopup(menuButton) }
        }

        ApplicationController.cleanupEventReminder(this)


        restoreState(state)
    }

    fun showDismissEditPopup(v: View) {
        val popup = PopupMenu(this, v)
        val inflater = popup.menuInflater
        inflater.inflate(R.menu.snooze, popup.menu)

        popup.setOnMenuItemClickListener {
            item ->

            val ev = event

            when (item.itemId) {
                R.id.action_dismiss_event -> {
                    if (ev != null) {
                        ApplicationController.dismissEvent(this, EventDismissType.ManuallyDismissedFromActivity, ev)
                        undoManager.addUndoState(
                                UndoState(undo = Runnable { ApplicationController.restoreEvent(applicationContext, ev) }))
                    }
                    finish()
                    true
                }
/*                R.id.action_edit_event -> {
                    if (ev != null)
                        CalendarIntents.editCalendarEvent(this, ev)
                    finish()
                    true
                } */
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
        val ev = event
        if (ev != null)
            CalendarIntents.viewCalendarEvent(this, ev)
    }

    private fun checkForMarshmallowWarningAndContinueWith(
            res: SnoozeResult,
            cont: () -> Unit) {

        if (!isMarshmallowOrAbove || res.quietUntil != 0L || res.snoozedUntil == 0L) {
            cont()
            return
        }

        val lastAlarm = globalState?.lastTimerBroadcastReceived ?: 0L
        val currentTime = System.currentTimeMillis()

        val untilNextAlarmFromLastAlarm = res.snoozedUntil - lastAlarm

        if (untilNextAlarmFromLastAlarm >= Consts.MARSHMALLOW_MIN_REMINDER_INTERVAL_USEC) {
            cont()
            return
        }

        if (settings.dontShowMarshmallowWarning) {
            cont()
            return
        }

        val expectedNextFire = lastAlarm + Consts.MARSHMALLOW_MIN_REMINDER_INTERVAL_USEC
        val untilNextFire = expectedNextFire - currentTime

        val mmWarning =
                String.format(
                        resources.getString(R.string.reminders_not_accurate),
                        (untilNextFire / 1000L + 59L) / 60L)

        AlertDialog.Builder(this)
                .setMessage(mmWarning).setCancelable(false)
                .setPositiveButton(android.R.string.ok) {
                    _, _ ->
                    cont()
                }
                .setNegativeButton(R.string.never_show_again) {
                    _, _ ->
                    settings.dontShowMarshmallowWarning = true
                    cont()
                }
                .create()
                .show()
    }

    private fun snoozeEvent(snoozeDelay: Long) {
        val ev = event
        if (ev != null) {
            DevLog.debug(LOG_TAG, "Snoozing event id ${ev.eventId}, snoozeDelay=${snoozeDelay / 1000L}")

            val result = ApplicationController.snoozeEvent(this, ev.eventId, ev.instanceStartTime, snoozeDelay);
            if (result != null) {
                checkForMarshmallowWarningAndContinueWith(result) {
                    result.toast(this)
                    finish()
                }
            }
            else {
                finish()
            }

        }
        else {
            AlertDialog.Builder(this)
                    .setMessage(
                            if (snoozeAllIsChange)
                                R.string.change_all_notification
                            else
                                R.string.snooze_all_confirmation)
                    .setCancelable(false)
                    .setPositiveButton(android.R.string.yes) {
                        _, _ ->

                        DevLog.debug(LOG_TAG, "Snoozing (change=$snoozeAllIsChange) all events, snoozeDelay=${snoozeDelay / 1000L}")

                        val result = ApplicationController.snoozeAllEvents(this, snoozeDelay, snoozeAllIsChange, false);
                        if (result != null) {
                            checkForMarshmallowWarningAndContinueWith(result) {
                                result.toast(this)
                                finish()
                            }
                        }
                        else
                            finish()
                    }
                    .setNegativeButton(R.string.cancel) {
                        _, _ ->
                    }
                    .create()
                    .show()
        }
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
            SnoozeActivityStateCode.Normal -> {
            }
            SnoozeActivityStateCode.CustomSnoozeOpened -> {
                state.timeAMillis = customSnooze_TimeIntervalPickerController?.intervalMilliseconds ?: 0L
            }
            SnoozeActivityStateCode.SnoozeUntilOpenedDatePicker -> {

                val datePicker = snoozeUntil_DatePicker
                if (datePicker != null) {
                    datePicker.clearFocus()

                    val date = Calendar.getInstance()
                    date.set(datePicker.year, datePicker.month, datePicker.dayOfMonth, 0, 0, 0)
                    state.timeAMillis = date.timeInMillis
                    state.timeBMillis = event?.snoozedUntil ?: 0L
                }
            }
            SnoozeActivityStateCode.SnoozeUntilOpenedTimePicker -> {

                val timePicker = snoozeUntil_TimePicker
                if (timePicker != null) {
                    timePicker.clearFocus()

                    val time = Calendar.getInstance()
                    time.timeInMillis = state.timeAMillis
                    time.set(Calendar.HOUR_OF_DAY, timePicker.hourCompat)
                    time.set(Calendar.MINUTE, timePicker.minuteCompat)

                    state.timeBMillis = time.timeInMillis
                }
            }
        }
        // val intervalMilliseconds = customSnooze_TimeIntervalPickerController?.intervalMilliseconds ?: 0L
        state.toBundle(outState)
    }

    private fun restoreState(state: SnoozeActivityState) {

        when (state.state) {
            SnoozeActivityStateCode.Normal -> {

            }
            SnoozeActivityStateCode.CustomSnoozeOpened -> {
                customSnoozeShowDialog(state.timeAMillis)
            }
            SnoozeActivityStateCode.SnoozeUntilOpenedDatePicker -> {
                snoozeUntilShowDatePickerDialog(state.timeAMillis, state.timeBMillis)
            }
            SnoozeActivityStateCode.SnoozeUntilOpenedTimePicker -> {
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

        val adapter = ArrayAdapter<String>(this, android.R.layout.select_dialog_singlechoice)

        adapter.addAll(intervalNames.toMutableList())

        builder.setCancelable(true)

        builder.setAdapter(adapter) {
            dialog, which ->
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

        val timeIntervalPicker = TimeIntervalPickerController(dialogView, R.string.snooze_for)
        timeIntervalPicker.intervalMilliseconds = initialTimeValue

        state.state = SnoozeActivityStateCode.CustomSnoozeOpened
        customSnooze_TimeIntervalPickerController = timeIntervalPicker

        val builder = AlertDialog.Builder(this)

        builder.setView(dialogView)

        builder.setPositiveButton(R.string.snooze) {
            _: DialogInterface?, _: Int ->

            val intervalMilliseconds = timeIntervalPicker.intervalMilliseconds
            this.persistentState.lastCustomSnoozeIntervalMillis = intervalMilliseconds

            snoozeEvent(intervalMilliseconds)

            state.state = SnoozeActivityStateCode.Normal
            customSnooze_TimeIntervalPickerController = null
        }

        builder.setNegativeButton(R.string.cancel) {
            _: DialogInterface?, _: Int ->

            state.state = SnoozeActivityStateCode.Normal
            customSnooze_TimeIntervalPickerController = null
        }

        builder.create().show()
    }

    @Suppress("unused", "UNUSED_PARAMETER", "DEPRECATION")
    fun OnButtonSnoozeUntilClick(v: View?) {
        val currentlySnoozedUntil = event?.snoozedUntil ?: 0L
        snoozeUntilShowDatePickerDialog(currentlySnoozedUntil, currentlySnoozedUntil)
    }

    fun inflateDatePickerDialog() =
            layoutInflater?.inflate(
                    if (settings.haloLightDatePicker)
                        R.layout.dialog_date_picker_halo_light
                    else
                        R.layout.dialog_date_picker,
                    null)

    fun inflateTimePickerDialog() =
            layoutInflater?.inflate(
                    if (settings.haloLightTimePicker)
                        R.layout.dialog_time_picker_halo_light
                    else
                        R.layout.dialog_time_picker, null)

    @SuppressLint("NewApi")
    fun snoozeUntilShowDatePickerDialog(initialValueForDate: Long, initialValueForTime: Long) {

        val dialogDate = inflateDatePickerDialog() ?: return

        val datePicker = dialogDate.find<DatePicker>(R.id.datePickerCustomSnooze)

        state.state = SnoozeActivityStateCode.SnoozeUntilOpenedDatePicker
        snoozeUntil_DatePicker = datePicker

        val firstDayOfWeek = Settings(this).firstDayOfWeek
        if (firstDayOfWeek != -1 && isLollipopOrAbove)
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

            state.state = SnoozeActivityStateCode.Normal
            snoozeUntil_DatePicker = null
        }

        builder.create().show()

    }

    fun snoozeUntilShowTimePickerDialog(currentDateSelection: Long, initialTimeValue: Long) {

        val date = Calendar.getInstance()
        date.timeInMillis = currentDateSelection

        val dialogTime = inflateTimePickerDialog() ?: return

        val timePicker: TimePicker = dialogTime.find<TimePicker>(R.id.timePickerCustomSnooze)
        timePicker.setIs24HourView(android.text.format.DateFormat.is24HourFormat(this))

        state.state = SnoozeActivityStateCode.SnoozeUntilOpenedTimePicker
        state.timeAMillis = currentDateSelection
        snoozeUntil_TimePicker = timePicker
        snoozeUntil_DatePicker = null

        if (initialTimeValue != 0L) {
            val cal = Calendar.getInstance()
            cal.timeInMillis = initialTimeValue

            timePicker.hourCompat = cal.get(Calendar.HOUR_OF_DAY)
            timePicker.minuteCompat = cal.get(Calendar.MINUTE)
        }

        val title = dialogTime.find<TextView>(R.id.textViewSnoozeUntilDate)
        title.text =
                String.format(
                        resources.getString(R.string.choose_time),
                        DateUtils.formatDateTime(this, date.timeInMillis, DateUtils.FORMAT_SHOW_DATE))

        val builder = AlertDialog.Builder(this)
        builder.setView(dialogTime)
        builder.setPositiveButton(R.string.snooze) {
            _: DialogInterface?, _: Int ->

            state.state = SnoozeActivityStateCode.Normal
            snoozeUntil_TimePicker = null

            timePicker.clearFocus()

            // grab time from timePicker + date picker

            date.set(Calendar.HOUR_OF_DAY, timePicker.hourCompat)
            date.set(Calendar.MINUTE, timePicker.minuteCompat)

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

            state.state = SnoozeActivityStateCode.Normal
            snoozeUntil_TimePicker = null
        }

        builder.create().show()
    }

    fun reschedule(addTime: Long) {

        val ev = event

        if (ev != null) {
            DevLog.info(this, LOG_TAG, "Moving event ${ev.eventId} by ${addTime / 1000L} seconds");

            val moved = ApplicationController.moveEvent(this, ev, addTime)

            if (moved) {
                // Show
                if (Settings(this).viewAfterEdit)
                    CalendarIntents.viewCalendarEvent(this, ev)
                else
                    SnoozeResult(SnoozeType.Moved, ev.startTime, 0L).toast(this)

                // terminate ourselves
                finish();
            }
            else {
                DevLog.info(this, LOG_TAG, "snooze: Failed to move event ${ev.eventId} by ${addTime / 1000L} seconds")
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
