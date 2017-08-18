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
import com.github.quarck.calnotify.addevent.AddEventManager
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

// FIXME: it worth nothing until intergrated with other parts of the app and until we track event creation

// FIXME: test 'notification' button layout on 4.2.x devices - my little samsung was doing shite

// FIXME: correct UI icons

// FIXME: only show handled calendars

// FIXME: handle repeating events

// FIXME: handle timezones

// FIXME: needs custom nice layout for account selection

// FIXME: configure default event duration in the settings


fun NewEventReminder.toLocalizedString(ctx: Context, isAllDay: Boolean): String {

    val ret = StringBuilder()

    if (!isAllDay) {
        val duration = EventFormatter(ctx).formatTimeDuration(this.time, 60L)

        ret.append(
                ctx.resources.getString(R.string.add_event_fmt_before).format(duration)
        )
    }
    else {
        val fullDaysBefore = allDayDaysBefore
        val (hr, min) = allDayHourOfDayAndMinute

        val cal = DateTimeUtils.createCalendarTime(System.currentTimeMillis(), hr, min)

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

    data class ReminderWrapper(val view: TextView, var reminder: NewEventReminder, val isForAllDay: Boolean)

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
        from.addHours(Consts.NEW_EVENT_DEFAULT_ADD_HOURS)
        from.minute = 0
        from.second = 0

        to = DateTimeUtils.createCalendarTime(from.timeInMillis)
        to.addMinutes(Consts.NEW_EVENT_DEFAULT_EVENT_DURATION_MINUTES)

        DevLog.debug(LOG_TAG, "${from.timeInMillis}, ${to.timeInMillis}, $from, $to")

        updateDateTimeUI();

        addReminder(NewEventReminder(Consts.NEW_EVENT_DEFAULT_NEW_EVENT_REMINDER, false), false)
        addReminder(NewEventReminder(Consts.NEW_EVENT_DEFAULT_ALL_DAY_REMINDER, false), true)

        updateReminders()
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

    fun updateReminders() {

        for (reminder in reminders) {
            if (reminder.isForAllDay == isAllDay) {
                reminder.view.visibility = View.VISIBLE
            }
            else {
                reminder.view.visibility = View.GONE
            }
        }
    }


    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.add_event, menu)
        return true
    }

    override fun onBackPressed() {
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

    fun onButtonCancelClick(v: View) {
        onBackPressed()
    }

    fun onAccountClick(v: View) {

        val builder = AlertDialog.Builder(this)

        val adapter = ArrayAdapter<String>(this, R.layout.simple_list_item_medium)

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

        val remindersToAdd = reminders.filter { it.isForAllDay == isAllDay }.map { it.reminder }.toList()

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
                colour = 0, // Not specified
                reminders =  remindersToAdd
        )

        val added = AddEventManager(CalendarProvider).createEvent(this, newEvent)
        if (added) {
            DevLog.debug(this, LOG_TAG, "Event created: id=${newEvent.eventId}")

            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, newEvent.eventId);
            val intent = Intent(Intent.ACTION_VIEW).setData(uri)

            intent.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startTime)
            intent.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endTime)

            startActivity(intent)
            finish()

        } else {
            DevLog.error(this, LOG_TAG, "Failed to create event")

            AlertDialog.Builder(this)
                    .setMessage(R.string.new_event_failed_to_create_event)
                    .setPositiveButton(android.R.string.ok) { _, _ -> }
                    .show()
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
        updateReminders()
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

        if (wrapper != null) {
            if (wrapper.isForAllDay)
                showAddReminderListAllDayDialog(wrapper.reminder, wrapper.view)
            else
                showAddReminderListDialog(wrapper.reminder, wrapper.view)
        }
    }

    fun showAddReminderCustomDialog(currentReminder: NewEventReminder, existingReminderView: View?) {

        val dialogView = this.layoutInflater.inflate(R.layout.dialog_add_event_notification, null);

        val timeIntervalPicker = TimeIntervalPickerController(dialogView, null,
                Consts.NEW_EVENT_MAX_REMINDER_MILLISECONDS_BEFORE)
        timeIntervalPicker.intervalMilliseconds = currentReminder.time

        val isEmailCb = dialogView.find<CheckBox?>(R.id.checkbox_as_email)

        val builder = AlertDialog.Builder(this)

        builder.setView(dialogView)

        builder.setPositiveButton(android.R.string.ok) {
            _: DialogInterface?, _: Int ->

            var intervalMilliseconds = timeIntervalPicker.intervalMilliseconds
            val isEmail = isEmailCb?.isChecked ?: false

            if (intervalMilliseconds > Consts.NEW_EVENT_MAX_REMINDER_MILLISECONDS_BEFORE) {
                intervalMilliseconds = Consts.NEW_EVENT_MAX_REMINDER_MILLISECONDS_BEFORE
                Toast.makeText(this, R.string.new_event_max_reminder_is_28_days, Toast.LENGTH_LONG).show()
            }

            if (existingReminderView != null)
                modifyReminder(existingReminderView, NewEventReminder(intervalMilliseconds, isEmail))
            else
                addReminder(NewEventReminder(intervalMilliseconds, isEmail), isForAllDay = false)
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

        val adapter = ArrayAdapter<String>(this, R.layout.simple_list_item_medium)

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
                        addReminder(NewEventReminder(intervalMillis, false), isForAllDay = false)
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

    fun showAddReminderCustomAllDayDialog(currentReminder: NewEventReminder, existingReminderView: View?) {

        val dialogView = this.layoutInflater.inflate(R.layout.dialog_add_event_allday_notification, null);

        val numberPicker = dialogView.find<NumberPicker>(R.id.number_picker_days_before)
        val timePicker = dialogView.find<TimePicker>(R.id.time_picker_notification_time_of_day)
        val isEmailCb = dialogView.find<CheckBox>(R.id.checkbox_as_email)

        numberPicker.minValue = 0
        numberPicker.maxValue = Consts.NEW_EVENT_MAX_ALL_DAY_REMINDER_DAYS_BEFORE
        numberPicker.value = currentReminder.allDayDaysBefore

        timePicker.setIs24HourView(android.text.format.DateFormat.is24HourFormat(this))

        val (hr, min) = currentReminder.allDayHourOfDayAndMinute

        timePicker.hourCompat = hr
        timePicker.minuteCompat = min


        val builder = AlertDialog.Builder(this)

        builder.setView(dialogView)

        builder.setPositiveButton(android.R.string.ok) {
            _: DialogInterface?, _: Int ->

            numberPicker.clearFocus()
            timePicker.clearFocus()

            val daysBefore = numberPicker.value
            val hr = timePicker.hourCompat
            val min = timePicker.minuteCompat

            val daysInMilliseconds = daysBefore * Consts.DAY_IN_MILLISECONDS
            val hrMinInMilliseconds = hr * Consts.HOUR_IN_MILLISECONDS + min * Consts.MINUTE_IN_MILLISECONDS
            val reminderTimeMilliseconds = daysInMilliseconds - hrMinInMilliseconds

            val isEmail = isEmailCb.isChecked

            if (existingReminderView != null)
                modifyReminder(existingReminderView, NewEventReminder(reminderTimeMilliseconds, isEmail))
            else
                addReminder(NewEventReminder(reminderTimeMilliseconds, isEmail), isForAllDay = true)
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

    fun showAddReminderListAllDayDialog(currentReminder: NewEventReminder, existingReminderView: View?) {

        if (currentReminder.isEmail)
            return showAddReminderCustomAllDayDialog(currentReminder, existingReminderView)

        val reminderNames: Array<String> = this.resources.getStringArray(R.array.default_reminder_intervals_all_day)
        val reminderValues = this.resources.getIntArray(R.array.default_reminder_intervals_all_day_seconds_values)

        val enterManuallyValue = -2147483648

        if (reminderValues.find { it.toLong() == currentReminder.time / 1000L } == null) {
            // reminder is not one of standard ones - we have to show custom idalog
            return showAddReminderCustomAllDayDialog(currentReminder, existingReminderView)
        }

        val builder = AlertDialog.Builder(this)

        val adapter = ArrayAdapter<String>(this, R.layout.simple_list_item_medium)

        adapter.addAll(reminderNames.toMutableList())

        builder.setCancelable(true)

        builder.setAdapter(adapter) {
            dialog, which ->
            if (which in 0..reminderValues.size-1) {

                val reminderSeconds = reminderValues[which]
                if (reminderSeconds != enterManuallyValue) {

                    val reminderTimeMillis = reminderSeconds.toLong() * 1000L

                    if (existingReminderView != null)
                        modifyReminder(existingReminderView, NewEventReminder(reminderTimeMillis, false))
                    else
                        addReminder(NewEventReminder(reminderTimeMillis, false), isForAllDay = true)
                } else {
                    showAddReminderCustomAllDayDialog(currentReminder, existingReminderView)
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
        if (!isAllDay) {
            showAddReminderListDialog(NewEventReminder(Consts.NEW_EVENT_DEFAULT_NEW_EVENT_REMINDER, false), null)
        }
        else {
            showAddReminderListAllDayDialog(NewEventReminder(Consts.NEW_EVENT_DEFAULT_ALL_DAY_REMINDER, false), null)
        }
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

    private fun addReminder(reminder: NewEventReminder, isForAllDay: Boolean) {

        if (reminders.find { it.reminder == reminder} != null) {
            DevLog.warn(this, LOG_TAG, "Not adding reminder: already in the list")
            return
        }

        val textView = TextView(this)
        textView.text = reminder.toLocalizedString(this, isForAllDay)

        textView.setOnClickListener (this::onNotificationClick)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            textView.setTextAppearance(android.R.style.TextAppearance_Medium)
        }
        else {
            textView.setTextAppearance(this, android.R.style.TextAppearance_Medium)
        }

        textView.setTextColor(notificationPrototype.textColors)


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            textView.setPaddingRelative(
                    notificationPrototype.paddingStart,
                    notificationPrototype.paddingTop,
                    notificationPrototype.paddingEnd,
                    notificationPrototype.paddingBottom)
        }
        else {
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

        reminders.add(ReminderWrapper(textView, reminder, isForAllDay))
    }

    companion object {
        private const val LOG_TAG = "AddEventActivity"
    }
}
