package com.github.quarck.calnotify.ui

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.text.format.DateUtils
import android.view.Menu
import android.view.View
import android.widget.*
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.calendar.CalendarProvider
import com.github.quarck.calnotify.calendar.CalendarProviderInterface
import com.github.quarck.calnotify.calendar.CalendarRecord
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.utils.*
import java.util.*


class AddEventActivity : AppCompatActivity() {

    lateinit var eventTitleText: EditText

    lateinit var buttonSave: Button
    lateinit var buttonCancel: ImageView

    lateinit var accountName: TextView

    lateinit var switchAllDay: Switch

    lateinit var dateFrom: Button
    lateinit var timeFrom: Button

    lateinit var dateTo: Button
    lateinit var timeTo: Button

    lateinit var eventLocation: EditText

    lateinit var notificationsLayout: LinearLayout
    lateinit var notification1: TextView
    lateinit var addNotification: TextView

    lateinit var note: EditText

    lateinit var calendars: List<CalendarRecord>
    lateinit var calendar: CalendarRecord

    lateinit var settings: Settings

    lateinit var from: Calendar
    lateinit var to: Calendar

    var calendarProvider: CalendarProviderInterface = CalendarProvider

    val anyChanges: Boolean
        get() {
            return eventTitleText.text.isNotEmpty()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_event)

        val toolbar = find<Toolbar?>(R.id.toolbar)
        toolbar?.visibility = View.GONE

        // get all the objects first
        eventTitleText = find<EditText?>(R.id.add_event_title) ?: throw Exception("Can't find add_event_title")

        buttonSave = find<Button?>(R.id.add_event_save) ?: throw Exception("Can't find add_event_save")
        buttonCancel = find<ImageView?>(R.id.add_event_view_cancel) ?: throw Exception("Can't find add_event_view_cancel")

        accountName = find<TextView?>(R.id.account_name) ?: throw Exception("Can't find account_name")

        switchAllDay = find<Switch?>(R.id.switch_all_day) ?: throw Exception("Can't find switch_all_day")

        dateFrom = find<Button?>(R.id.add_event_date_from) ?: throw Exception("Can't find add_event_date_from")
        timeFrom = find<Button?>(R.id.add_event_time_from) ?: throw Exception("Can't find add_event_time_from")

        dateTo = find<Button?>(R.id.add_event_date_to) ?: throw Exception("Can't find add_event_date_to")
        timeTo = find<Button?>(R.id.add_event_time_to) ?: throw Exception("Can't find add_event_time_to")

        eventLocation = find<EditText?>(R.id.event_location) ?: throw Exception("Can't find event_location")

        notificationsLayout = find<LinearLayout?>(R.id.notifications) ?: throw Exception("Can't find notifications")
        notification1 = find<TextView?>(R.id.notification1) ?: throw Exception("Can't find notification1")
        addNotification = find<TextView?>(R.id.add_notification) ?: throw Exception("Can't find add_notification")

        note = find<EditText?>(R.id.event_note) ?: throw Exception("Can't find event_note")


        // settings
        settings = Settings(this)


        // Default calendar
        calendars = calendarProvider.getCalendars(this).filter { !it.isReadOnly }

        if (calendars.isEmpty()) {
            DevLog.error(this, LOG_TAG, "You have no calendars")
            finish()
        }

        calendar = calendars.filter { it.isPrimary }.firstOrNull() ?: calendars[0]


        // Initialize default values
        accountName.text = calendar.name
        eventTitleText.background = ColorDrawable(calendar.color.adjustCalendarColor(settings.darkerCalendarColors))

        // Set onClickListener-s
        buttonSave.setOnClickListener (this::onButtonSaveClick)
        buttonCancel.setOnClickListener (this::onButtonCancelClick)

        accountName.setOnClickListener (this::onAccountClick)

        switchAllDay.setOnClickListener (this::onSwitchAllDayClick)

        dateFrom.setOnClickListener (this::onDateFromClick)
        timeFrom.setOnClickListener (this::onTimeFromClick)

        dateTo.setOnClickListener (this::onDateToClick)
        timeTo.setOnClickListener (this::onTimeToClick)

        notification1.setOnClickListener (this::onNotificationOneClick)
        addNotification.setOnClickListener (this::onAddNotificationClick)


        // Set default date and time

        var currentTime = System.currentTimeMillis()
        currentTime = currentTime - (currentTime % 1000)

        from = DateTimeUtils.createCalendarTime(currentTime, 0, 0)
        if (from.timeInMillis < currentTime)
            from.addDays(1)

        to = DateTimeUtils.createCalendarTime(from.timeInMillis)
        to.addHours(1)

        DevLog.debug(LOG_TAG, "${from.timeInMillis}, ${to.timeInMillis}, $from, $to")

        updateDateTimeUI();
    }

    fun updateDateTimeUI() {

        dateFrom.text = DateUtils.formatDateTime(this, from.timeInMillis, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR)
        timeFrom.text = DateUtils.formatDateTime(this, from.timeInMillis, DateUtils.FORMAT_SHOW_TIME)

        dateTo.text = DateUtils.formatDateTime(this, to.timeInMillis, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR)
        timeTo.text = DateUtils.formatDateTime(this, to.timeInMillis, DateUtils.FORMAT_SHOW_TIME)
    }


    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.add_event, menu)
        return true
    }

    fun onButtonCancelClick(v: View) {
        if (anyChanges) {

            AlertDialog.Builder(this)
                    .setMessage(R.string.discard_new_event)
                    .setCancelable(false)
                    .setPositiveButton(android.R.string.yes) {
                        _, _ ->
                        this@AddEventActivity.finish()
                    }
                    .setNegativeButton(R.string.cancel) {
                        _, _ ->
                    }
                    .create()
                    .show()
        }
        else {
            finish()
        }
    }

    fun onAccountClick(v: View) {

        // FIXME: needs custom nice layout instead of this
        val builder = AlertDialog.Builder(this)
        //builder.setIcon(R.drawable.ic_launcher)

        val adapter = ArrayAdapter<String>(this, android.R.layout.select_dialog_singlechoice)

        adapter.addAll(
                calendars.filter { true }.map { it.name }.toList()
        )

//        builderSingle.setNegativeButton(R.string.cancel) {
//            dialog, which ->
//            dialog.dismiss()
//        }

        builder.setCancelable(true)

        builder.setAdapter(adapter) {
            dialog, which ->
            val name = adapter.getItem(which)
            val newCalendar = calendars.find { it.name == name }
            if (newCalendar != null) {
                calendar = newCalendar

                accountName.text = calendar.name
                eventTitleText.background = ColorDrawable(calendar.color.adjustCalendarColor(settings.darkerCalendarColors))
            }
        }
        builder.show()
    }

    fun onButtonSaveClick(v: View) {

    }

    fun onSwitchAllDayClick(v: View) {

    }

    fun onDateFromClick(v: View) {

        val durationMinutes = (to.timeInMillis - from.timeInMillis) / Consts.MINUTE_IN_MILLISECONDS

        val dialog = DatePickerDialog(
                this,
                {
                    picker, year, month, day ->

                    from.year = year
                    from.month = month
                    from.dayOfMonth = day

                    to = DateTimeUtils.createCalendarTime(from.timeInMillis)
                    to.addMinutes(durationMinutes.toInt())

                    updateDateTimeUI()

                },
                from.year,
                from.month,
                from.dayOfMonth
        )
        dialog.show()
        //builder.setIcon(R.drawable.ic_launcher)
    }

    fun onTimeFromClick(v: View) {

    }

    fun onDateToClick(v: View) {

        val durationMinutes = (to.timeInMillis - from.timeInMillis) / Consts.MINUTE_IN_MILLISECONDS

        val dialog = DatePickerDialog(
                this,
                {
                    picker, year, month, day ->

                    to.year = year
                    to.month = month
                    to.dayOfMonth = day

                    if (to.before(from)) {
                        Toast.makeText(this, getString(R.string.end_time_before_start_time), Toast.LENGTH_LONG).show()
                        to = DateTimeUtils.createCalendarTime(from.timeInMillis)
                        to.addMinutes(durationMinutes.toInt())
                    }

                    updateDateTimeUI()

                },
                to.year,
                to.month,
                to.dayOfMonth
        )
        dialog.show()    }

    fun onTimeToClick(v: View) {

    }

    fun onNotificationOneClick(v: View) {

    }

    fun onAddNotificationClick(v: View) {

    }

    companion object {
        private const val LOG_TAG = "AddEventActivity"
    }
}
