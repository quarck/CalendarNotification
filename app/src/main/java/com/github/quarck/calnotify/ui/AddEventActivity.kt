package com.github.quarck.calnotify.ui

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ContentUris
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.provider.CalendarContract
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.text.format.DateUtils
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.addevent.AddEventPersistentState
import com.github.quarck.calnotify.addevent.storage.NewEventRecord
import com.github.quarck.calnotify.addevent.storage.NewEventReminder
import com.github.quarck.calnotify.addevent.storage.NewEventsStorage
import com.github.quarck.calnotify.calendar.CalendarProvider
import com.github.quarck.calnotify.calendar.CalendarProviderInterface
import com.github.quarck.calnotify.calendar.CalendarRecord
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.textutils.EventFormatter
import com.github.quarck.calnotify.utils.*
import java.util.*

// FIXME: handle all day reminder creation
// FIXME: handle all day reminder creation
// FIXME: handle all day reminder creation
// FIXME: handle all day reminder creation
// FIXME: handle all day reminder creation
// FIXME: handle all day reminder creation

// FIXME: handle repeating events

// FIXME: handle timezones

fun NewEventReminder.toLocalizedString(ctx: Context, isAllDay: Boolean): String {

    val ret = StringBuilder()

    if (!isAllDay) {
        val duration = EventFormatter(ctx).formatTimeDuration(this.time, 60L)

        ret.append(
                ctx.resources.getString(R.string.add_event_fmt_before).format(duration)
        )
    }
    else {
        var timeOfDayMillis = 0L
        var fullDaysBefore = 0

        if (this.time < 0L) { // on the day of event
            timeOfDayMillis = -this.time
        }
        else {
            fullDaysBefore = ((this.time / Consts.DAY_IN_MILLISECONDS)).toInt() + 1

            val timeBefore2400 = this.time % Consts.DAY_IN_MILLISECONDS
            timeOfDayMillis = Consts.DAY_IN_MILLISECONDS - timeBefore2400
        }

        val timeOfDayMinutes = timeOfDayMillis.toInt() / 1000 / 60

        val cal = DateTimeUtils.createCalendarTime(System.currentTimeMillis())

        cal.minute = timeOfDayMinutes % 60
        cal.hourOfDay = timeOfDayMinutes / 60

        val time = DateUtils.formatDateTime(ctx, cal.timeInMillis, DateUtils.FORMAT_SHOW_TIME)

        when (fullDaysBefore) {
            0 ->
                ret.append(
                        ctx.resources.getString(R.string.add_event_zero_days_before).format(time)
                )
            1 ->
                ret.append(
                        ctx.resources.getString(R.string.add_event_one_day_before).format(time)
                )
            else ->
                ret.append(
                        ctx.resources.getString(R.string.add_event_n_days_before).format(fullDaysBefore, time)
                )
        }
    }

    if (this.isEmail) {
        ret.append(" ")
        ret.append(ctx.resources.getString(R.string.add_event_as_email_suffix))
    }

    return ret.toString()
}

class AddEventActivity : AppCompatActivity() {

    data class ReminderWrapper(val view: TextView, var reminder: NewEventReminder)

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
    private lateinit var notificationPrototype: TextView
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

    val reminders = mutableListOf<ReminderWrapper>()

    val anyChanges: Boolean
        get() {
            return eventTitleText.text.isNotEmpty() ||
                    eventLocation.text.isNotEmpty() ||
                    note.text.isNotEmpty()
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
        notificationPrototype = find<TextView?>(R.id.notificationPrototype) ?: throw Exception("Can't find notificationPrototype")
        addNotification = find<TextView?>(R.id.add_notification) ?: throw Exception("Can't find add_notification")

        note = find<EditText?>(R.id.event_note) ?: throw Exception("Can't find event_note")

        notificationPrototype.visibility = View.GONE


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

        addNotification.setOnClickListener (this::onAddNotificationClick)

        // Set default date and time

        var currentTime = System.currentTimeMillis()
        currentTime = currentTime - (currentTime % 1000)

        from = DateTimeUtils.createCalendarTime(currentTime)
        from.addHours(4)
        from.minute = 0
        from.second = 0

        to = DateTimeUtils.createCalendarTime(from.timeInMillis)
        to.addHours(1)

        DevLog.debug(LOG_TAG, "${from.timeInMillis}, ${to.timeInMillis}, $from, $to")

        updateDateTimeUI();

        addReminder(NewEventReminder(Consts.DEFAULT_NEW_EVENT_REMINDER, false))
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

        val adapter = ArrayAdapter<String>(this, android.R.layout.select_dialog_singlechoice)

        val listCalendars = calendars.filter { !it.isReadOnly }.map { "${it.displayName} <${it.accountName}>" }.toList()

        adapter.addAll(listCalendars)


        builder.setCancelable(true)

        builder.setAdapter(adapter) {
            dialog, which ->
            if (which in 0..calendars.size-1) {

                calendar = calendars.get(which)

                persistentState.lastCalendar = calendar.calendarId

                accountName.text = calendar.name
                eventTitleText.background = ColorDrawable(
                        calendar.color.adjustCalendarColor(settings.darkerCalendarColors))
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
                timezone = calendar.timeZone,
                startTime = startTime,
                endTime = endTime,
                isAllDay = isAllDay,
                repeatingRule = "",
                colour = 0, // No specificed
                reminders =  reminders.map { it.reminder }.toList()
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

    fun onNotificationClick(v: View) {

        val wrapper = reminders.find { it.view == v }

        if (wrapper != null)
            showAddReminderListDialog(wrapper.reminder, wrapper.view)
    }

    fun showAddReminderCustomDialog(currentReminder: NewEventReminder, existingReminderView: View?) {

        val dialogView = this.layoutInflater.inflate(R.layout.dialog_add_event_notification, null);

        val timeIntervalPicker = TimeIntervalPickerController(dialogView, null)
        timeIntervalPicker.intervalMilliseconds = currentReminder.time

        val isEmailCb = dialogView.find<CheckBox?>(R.id.checkbox_as_email)

        val builder = AlertDialog.Builder(this)

        builder.setView(dialogView)

        builder.setPositiveButton(android.R.string.ok) {
            _: DialogInterface?, _: Int ->

            val intervalMilliseconds = timeIntervalPicker.intervalMilliseconds
            val isEmail = isEmailCb?.isChecked ?: false

            if (existingReminderView != null)
                modifyReminder(existingReminderView, NewEventReminder(intervalMilliseconds, isEmail))
            else
                addReminder(NewEventReminder(intervalMilliseconds, isEmail))
        }

        if (existingReminderView != null) {
            builder.setNegativeButton(R.string.remove_reminder) {
                _: DialogInterface?, _: Int ->
                removeReminder(existingReminderView)
            }
        }
        else {
            builder.setNegativeButton(android.R.string.cancel) {
                _: DialogInterface?, _: Int ->
            }
        }

        builder.create().show()
    }

    fun showAddReminderListDialog(currentReminder: NewEventReminder, existingReminderView: View?) {

        if (currentReminder.isEmail)
            return showAddReminderCustomDialog(currentReminder, existingReminderView)

        val intervalNames: Array<String> = this.resources.getStringArray(R.array.default_reminder_intervals)
        val intervalValues = this.resources.getIntArray(R.array.default_reminder_intervals_milliseconds_values)

        if (intervalValues.find { it.toLong() == currentReminder.time } == null) {
            // reminder is not one of standard ones - we have to show custom idalog
            return showAddReminderCustomDialog(currentReminder, existingReminderView)
        }

        val builder = AlertDialog.Builder(this)

        val adapter = ArrayAdapter<String>(this, android.R.layout.select_dialog_singlechoice)

        adapter.addAll(intervalNames.toMutableList())

        builder.setCancelable(true)

        builder.setAdapter(adapter) {
            dialog, which ->
            if (which in 0..intervalValues.size-1) {

                val intervalMillis = intervalValues[which].toLong()
                if (intervalMillis != -1L) {
                    if (existingReminderView != null)
                        modifyReminder(existingReminderView, NewEventReminder(intervalMillis, false))
                    else
                        addReminder(NewEventReminder(intervalMillis, false))
                } else {
                    showAddReminderCustomDialog(currentReminder, existingReminderView)
                }
            }
        }

        if (existingReminderView != null) {
            builder.setNegativeButton(R.string.remove_reminder) {
                _: DialogInterface?, _: Int ->
                removeReminder(existingReminderView)
            }
        }
        else {
            builder.setNegativeButton(android.R.string.cancel) {
                _: DialogInterface?, _: Int ->
            }
        }

        builder.show()
    }

    fun onAddNotificationClick(v: View) {
        showAddReminderListDialog(NewEventReminder(Consts.DEFAULT_NEW_EVENT_REMINDER, false), null)
    }


    private fun removeReminder(existingReminderView: View) {

        val wrapper = reminders.find { it.view == existingReminderView }
        if (wrapper != null) {
            reminders.remove(wrapper)
            notificationsLayout.removeView(wrapper.view)
        }
    }

    private fun modifyReminder(existingReminderView: View, newReminder: NewEventReminder) {

        if (reminders.find { it.reminder == newReminder && it.view != existingReminderView} != null) {
            // we have another reminder with the same params in the list -- remove this one (cruel!!)
            removeReminder(existingReminderView)
            return
        }

        val wrapper = reminders.find { it.view == existingReminderView }
        if (wrapper != null) {
            wrapper.reminder = newReminder
            wrapper.view.text = newReminder.toLocalizedString(this, isAllDay)
        }
    }

    private fun addReminder(reminder: NewEventReminder) {

        if (reminders.find { it.reminder == reminder} != null) {
            DevLog.warn(this, LOG_TAG, "Not adding reminder: already in the list")
            return
        }

        val textView = TextView(this)
        textView.text = reminder.toLocalizedString(this, isAllDay)

        textView.setOnClickListener (this::onNotificationClick)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            textView.setTextAppearance(android.R.style.TextAppearance_Medium)
        }
        else {
            textView.setTextAppearance(this, android.R.style.TextAppearance_Medium)
        }

        textView.setTextColor(notificationPrototype.textColors)


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            DevLog.debug("", "Current padding: ${notificationPrototype.paddingStart} ${notificationPrototype.paddingTop} " +
                    "${notificationPrototype.paddingEnd} ${notificationPrototype.paddingBottom}")
            textView.setPaddingRelative(
                    notificationPrototype.paddingStart,
                    notificationPrototype.paddingTop,
                    notificationPrototype.paddingEnd,
                    notificationPrototype.paddingBottom)
        }
        else {
            DevLog.debug("", "Current padding[-]: ${notificationPrototype.paddingLeft} ${notificationPrototype.paddingTop} " +
                    "${notificationPrototype.paddingRight} ${notificationPrototype.paddingBottom}")
            textView.setPadding(
                    notificationPrototype.paddingLeft,
                    notificationPrototype.paddingTop,
                    notificationPrototype.paddingRight,
                    notificationPrototype.paddingBottom)
        }

        textView.isClickable = true
        textView.background = notificationPrototype.background

        val lp = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        notificationsLayout.addView(textView, lp)

        reminders.add(ReminderWrapper(textView, reminder))
    }

    companion object {
        private const val LOG_TAG = "AddEventActivity"
    }
}
