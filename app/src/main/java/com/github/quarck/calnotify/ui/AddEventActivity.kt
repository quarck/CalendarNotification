package com.github.quarck.calnotify.ui

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ContentUris
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.provider.CalendarContract
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.text.format.DateUtils
import android.text.format.Time
import android.view.Menu
import android.view.View
import android.widget.*
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.addevent.AddEventPersistentState
import com.github.quarck.calnotify.addevent.storage.NewEventRecord
import com.github.quarck.calnotify.addevent.storage.NewEventReminder
import com.github.quarck.calnotify.addevent.storage.NewEventsStorage
import com.github.quarck.calnotify.calendar.CalendarIntents
import com.github.quarck.calnotify.calendar.CalendarProvider
import com.github.quarck.calnotify.calendar.CalendarProviderInterface
import com.github.quarck.calnotify.calendar.CalendarRecord
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.utils.*
import java.util.*


class AddEventActivity : AppCompatActivity() {

    private lateinit var eventTitleText: EditText

    private lateinit var buttonSave: Button
    private lateinit var buttonCancel: ImageView

    private lateinit var accountName: TextView

    private lateinit var switchAllDay: Switch

    private lateinit var dateFrom: Button
    private lateinit var timeFrom: Button

    private lateinit var dateTo: Button
    private lateinit var timeTo: Button

    private lateinit var eventLocation: EditText

    private lateinit var notificationsLayout: LinearLayout
    private lateinit var notification1: TextView
    private lateinit var addNotification: TextView

    private lateinit var note: EditText

    private lateinit var calendars: List<CalendarRecord>
    private lateinit var calendar: CalendarRecord

    private lateinit var settings: Settings

    private lateinit var from: Calendar
    private lateinit var to: Calendar
    private var isAllDay: Boolean = false

    private lateinit var persistentState: AddEventPersistentState

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

        persistentState = AddEventPersistentState(this)

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

        val lastCalendar = persistentState.lastCalendar
        if (lastCalendar != -1L) {
            calendar = calendars.filter { it.calendarId == lastCalendar }.firstOrNull() ?: calendars[0]
        }
        else {
            calendar = calendars.filter { it.isPrimary }.firstOrNull() ?: calendars[0]
        }

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

//        val is24hr = android.text.format.DateFormat.is24HourFormat(this)

        val dateFormat =
                DateUtils.FORMAT_SHOW_DATE or
                        DateUtils.FORMAT_SHOW_YEAR or
                        DateUtils.FORMAT_SHOW_WEEKDAY or
                        DateUtils.FORMAT_ABBREV_MONTH or
                        DateUtils.FORMAT_ABBREV_WEEKDAY

        val timeFormat =
                DateUtils.FORMAT_SHOW_TIME

        dateFrom.text = DateUtils.formatDateTime(this, from.timeInMillis, dateFormat)
        timeFrom.text = DateUtils.formatDateTime(this, from.timeInMillis, timeFormat)

        dateTo.text = DateUtils.formatDateTime(this, to.timeInMillis, dateFormat)
        timeTo.text = DateUtils.formatDateTime(this, to.timeInMillis, timeFormat)
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
                calendars.filter { true }.map { "${it.name} <${it.accountName}>" }.toList()
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

                persistentState.lastCalendar = calendar.calendarId

                accountName.text = calendar.name
                eventTitleText.background = ColorDrawable(calendar.color.adjustCalendarColor(settings.darkerCalendarColors))
            }
        }
        builder.show()
    }

    fun onButtonSaveClick(v: View) {

        var startTime = from.timeInMillis
        var endTime = to.timeInMillis

        if (isAllDay) {
            startTime = DateTimeUtils.createUTCCalendarDate(from.year, from.month, from.dayOfMonth).timeInMillis
            endTime = DateTimeUtils.createUTCCalendarDate(to.year, to.month, to.dayOfMonth).timeInMillis
        }

        val newEvent = NewEventRecord(
                id = -1L,
                eventId = -1L,
                calendarId = calendar.calendarId,
                title = eventTitleText.text.toString(),
                desc = note.text.toString(),
                location = eventLocation.text.toString(),
                timezone = Time.getCurrentTimezone(),
                startTime = startTime,
                endTime = endTime,
                isAllDay = isAllDay,
                repeatingRule = "",
                colour = 0, // No specificed
                reminders =  listOf(NewEventReminder(15*60000L, false))
        )

        val storage = NewEventsStorage(this)

        storage.use { it.addEvent(newEvent) }

        val id = CalendarProvider.createEvent(this, newEvent)

        if (id > 0) {
            DevLog.debug(this, LOG_TAG, "Event created: id=$id")

            newEvent.eventId = id
            storage.use { it.updateEvent(newEvent) }

            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id);
            val intent = Intent(Intent.ACTION_VIEW).setData(uri)

            intent.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startTime)
            intent.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endTime)

            startActivity(intent)
            finish()

        } else {
            DevLog.error(this, LOG_TAG, "Failed to create event")
        }

    }

    fun onSwitchAllDayClick(v: View) {
        isAllDay = switchAllDay.isChecked

        if (isAllDay) {
            timeTo.visibility = View.GONE
            timeFrom.visibility = View.GONE
        }
        else {
            timeTo.visibility = View.VISIBLE
            timeFrom.visibility = View.VISIBLE
        }

        updateDateTimeUI()
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

        val firstDayOfWeek = Settings(this).firstDayOfWeek
        if (firstDayOfWeek != -1 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            dialog.datePicker.firstDayOfWeek = firstDayOfWeek
        }

        dialog.show()
        //builder.setIcon(R.drawable.ic_launcher)
    }

    fun onTimeFromClick(v: View) {

        val durationMinutes = (to.timeInMillis - from.timeInMillis) / Consts.MINUTE_IN_MILLISECONDS

        val dialog = TimePickerDialog(
                this,
                {
                    picker, hour, min ->

                    from.hourOfDay = hour
                    from.minute = min

                    to = DateTimeUtils.createCalendarTime(from.timeInMillis)
                    to.addMinutes(durationMinutes.toInt())

                    updateDateTimeUI()
                },
                from.hourOfDay,
                from.minute,
                android.text.format.DateFormat.is24HourFormat(this)
        )

        dialog.show()
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
        val firstDayOfWeek = Settings(this).firstDayOfWeek
        if (firstDayOfWeek != -1 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            dialog.datePicker.firstDayOfWeek = firstDayOfWeek
        }

        dialog.show()
    }

    fun onTimeToClick(v: View) {

        val durationMinutes = (to.timeInMillis - from.timeInMillis) / Consts.MINUTE_IN_MILLISECONDS

        val dialog = TimePickerDialog(
                this,
                {
                    picker, hour, min ->

                    to.hourOfDay = hour
                    to.minute = min

                    if (to.before(from)) {
                        Toast.makeText(this, getString(R.string.end_time_before_start_time), Toast.LENGTH_LONG).show()
                        to = DateTimeUtils.createCalendarTime(from.timeInMillis)
                        to.addMinutes(durationMinutes.toInt())
                    }

                    updateDateTimeUI()
                },
                to.hourOfDay,
                to.minute,
                android.text.format.DateFormat.is24HourFormat(this)
        )

        dialog.show()
    }

    fun onNotificationOneClick(v: View) {

    }

    fun onAddNotificationClick(v: View) {

    }

    companion object {
        private const val LOG_TAG = "AddEventActivity"
    }
}
