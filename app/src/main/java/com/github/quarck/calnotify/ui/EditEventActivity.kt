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
import com.github.quarck.calnotify.calendar.*
import com.github.quarck.calnotify.calendareditor.*
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.textutils.EventFormatter
import com.github.quarck.calnotify.textutils.dateToStr
import com.github.quarck.calnotify.utils.*
import java.util.*

// FIXME: on the snooze activity - show all the reminders, not just next

// FIXME: Bin - invalid 'moved to' after edit

// FIXME: handle repeating requests

// FIXME: handle timezones

// FIXME: Also handle colors


fun EventReminderRecord.toLocalizedString(ctx: Context, isAllDay: Boolean): String {

    val ret = StringBuilder()

    if (!isAllDay) {
        val duration = EventFormatter(ctx).formatTimeDuration(this.millisecondsBefore, 60L)

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

    when (this.method) {
        CalendarContract.Reminders.METHOD_EMAIL -> {
            ret.append(" ")
            ret.append(ctx.resources.getString(R.string.add_event_as_email_suffix))
        }
        CalendarContract.Reminders.METHOD_SMS -> {
            ret.append(" ")
            ret.append(ctx.resources.getString(R.string.add_event_as_sms_suffix))
        }
        CalendarContract.Reminders.METHOD_ALARM -> {
            ret.append(" ")
            ret.append(ctx.resources.getString(R.string.add_event_as_alarm_suffix))
        }
    }

    return ret.toString()
}

data class EditEventActivityState(
        var eventId: Long,
        var title: String,
        var location: String,
        var note: String,
        var from: Calendar,
        var to: Calendar,
        var isAllDay: Boolean,
        var reminders: List<EventReminderRecord>,
        var allDayReminders: List<EventReminderRecord>,
        var selectedCalendar: Long,
        var isMuted: Boolean,
        var isTask: Boolean,
        var isAlarm: Boolean
) {
    fun toBundle(bundle: Bundle) {
        bundle.putLong(KEY_EVENT_ID, eventId)
        bundle.putString(KEY_TITLE, title)
        bundle.putString(KEY_LOCATION, location)
        bundle.putString(KEY_NOTE, note)
        bundle.putLong(KEY_FROM, from.timeInMillis)
        bundle.putLong(KEY_TO, to.timeInMillis)
        bundle.putBoolean(KEY_IS_ALL_DAY, isAllDay)
        bundle.putString(KEY_REMINDERS, reminders.serialize())
        bundle.putString(KEY_ALL_DAY_REMINDERS, allDayReminders.serialize())
        bundle.putLong(KEY_SELECTED_CALENDAR, selectedCalendar)
        bundle.putBoolean(KEY_IS_ALARM, isAlarm)
        bundle.putBoolean(KEY_IS_TASK, isTask)
        bundle.putBoolean(KEY_IS_MUTED, isMuted)
    }

    companion object {
        fun fromBundle(bundle: Bundle): EditEventActivityState {

            val id = bundle.getLong(KEY_EVENT_ID, -1L)
            val title = bundle.getString(KEY_TITLE, "")
            val loc = bundle.getString(KEY_LOCATION, "")
            val note = bundle.getString(KEY_NOTE, "")

            val from = bundle.getLong(KEY_FROM)
            val to = bundle.getLong(KEY_TO)

            val isAllDay = bundle.getBoolean(KEY_IS_ALL_DAY, false)
            val reminders = bundle.getString(KEY_REMINDERS, "").deserializeCalendarEventReminders()
            val allDayReminders = bundle.getString(KEY_ALL_DAY_REMINDERS, "").deserializeCalendarEventReminders()
            val selectedCalendar = bundle.getLong(KEY_SELECTED_CALENDAR)
            val muted = bundle.getBoolean(KEY_IS_MUTED)
            val task = bundle.getBoolean(KEY_IS_TASK)
            val alarm = bundle.getBoolean(KEY_IS_ALARM)

            return EditEventActivityState(
                    id,
                    title,
                    loc,
                    note,
                    DateTimeUtils.createCalendarTime(from),
                    DateTimeUtils.createCalendarTime(to),
                    isAllDay,
                    reminders,
                    allDayReminders,
                    selectedCalendar,
                    muted,
                    task,
                    alarm
                    )
        }

        const val KEY_EVENT_ID = "eventId"
        const val KEY_TITLE = "title"
        const val KEY_LOCATION = "loc"
        const val KEY_NOTE = "note"
        const val KEY_FROM = "from"
        const val KEY_TO = "to"
        const val KEY_IS_ALL_DAY = "aday"
        const val KEY_REMINDERS = "reminders"
        const val KEY_ALL_DAY_REMINDERS = "adayreminders"
        const val KEY_SELECTED_CALENDAR = "cal"
        const val KEY_IS_MUTED = "isMuted"
        const val KEY_IS_TASK = "isTask"
        const val KEY_IS_ALARM = "isAlarm"

    }
}


class EditEventActivity : AppCompatActivity() {

    data class ReminderWrapper(val view: TextView, var reminder: EventReminderRecord, val isForAllDay: Boolean)

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

    private lateinit var muteTagButton: TextView
    private lateinit var taskTagButton: TextView
    private lateinit var alarmTagButton: TextView

    private var isMuted = false
    private var isTask = false
    private var isAlarm = false

    private lateinit var calendars: List<CalendarRecord>
    private lateinit var calendar: CalendarRecord

    private lateinit var settings: Settings

    private lateinit var from: Calendar
    private lateinit var to: Calendar
    private var isAllDay: Boolean = false

    private lateinit var persistentState: CalendarChangePersistentState

    var calendarProvider: CalendarProviderInterface = CalendarProvider

    val reminders = mutableListOf<ReminderWrapper>()

    var originalEvent: EventRecord? = null

    val anyChanges: Boolean
        get() {
            val details = originalEvent?.details

            if (details == null) {

                return eventTitleText.text.isNotEmpty() ||
                        eventLocation.text.isNotEmpty() ||
                        note.text.isNotEmpty()
            }

            if (eventTitleText.text.toString() != details.title ||
                    note.text.toString() != details.desc ||
                    eventLocation.text.toString() != details.location
                    ) {
                return true
            }

//            if (eventTimeZone.text.toString() != details.timezone)
//                return true

            if (isAllDay != details.isAllDay)
                return true

            var currentStartTime = from.timeInMillis
            var currentEndTime = to.timeInMillis

            if (isAllDay) {
                currentStartTime = DateTimeUtils.createUTCCalendarDate(from.year, from.month, from.dayOfMonth).timeInMillis
                currentEndTime = DateTimeUtils.createUTCCalendarDate(to.year, to.month, to.dayOfMonth).timeInMillis
            }

            if (currentStartTime != details.startTime || currentEndTime != details.endTime)
                return true

            val currentReminders = reminders.filter { it.isForAllDay == isAllDay }.map { it.reminder }

            if (currentReminders.size != details.reminders.size)
                return true

            if (!details.reminders.containsAll(currentReminders))
                return true

//            if (colorView.text.value != details.color)
//                return true

/*
        val repeatingRule: String = "", // empty if not repeating
        val repeatingRDate: String = "", // empty if not repeating
        val repeatingExRule: String = "", // empty if not repeating
        val repeatingExRDate: String = "", // empty if not repeating
*/
            return false;
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_add_event)

        val toolbar = find<Toolbar?>(R.id.toolbar)
        toolbar?.visibility = View.GONE

        persistentState = CalendarChangePersistentState(this)

        val eventId = intent.getLongExtra(EVENT_ID, -1)
        if (eventId != -1L) {
            originalEvent = CalendarProvider.getEvent(this, eventId)

            if (originalEvent == null) {
                Toast.makeText(this, R.string.event_not_found, Toast.LENGTH_LONG).show()
                finish()
                return
            }
        }
        else {
            find<LinearLayout>(R.id.layout_focus_catcher).visibility = View.GONE
        }

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

        taskTagButton = find<TextView?>(R.id.add_event_task_tag) ?: throw Exception("Can't find add_event_task_tag")
        muteTagButton = find<TextView?>(R.id.add_event_mute_tag) ?: throw Exception("Can't find add_event_mute_tag")
        alarmTagButton = find<TextView?>(R.id.add_event_alarm_tag) ?: throw Exception("Can't find add_event_alarm_tag")

        updateTags(settings, true)

        if (originalEvent == null && settings.enableTagButtons) {

            taskTagButton.setOnClickListener( {
                isTask = !isTask
                updateTags(settings, false)
            })

            muteTagButton.setOnClickListener( {
                isMuted = !isMuted
                updateTags(settings, false)
            })

            alarmTagButton.setOnClickListener( {
                isAlarm = !isAlarm
                updateTags(settings, false)
            })
        }

        // Default calendar
        calendars = calendarProvider
                .getCalendars(this)
                .filter {
                    !it.isReadOnly &&
                            it.isVisible &&
                            settings.getCalendarIsHandled(it.calendarId)
                }

        if (calendars.isEmpty()) {
            DevLog.error(this, LOG_TAG, "You have no enabled calendars")

            accountName.text = "" // remove debug mess

            AlertDialog.Builder(this)
                    .setMessage(R.string.no_active_calendars)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        finish()
                    }
                    .show()

            return
        }

        val lastCalendar = persistentState.lastCalendar
        if (lastCalendar != -1L) {
            calendar = calendars.filter { it.calendarId == lastCalendar }.firstOrNull() ?: calendars[0]
        } else {
            calendar = calendars.filter { it.isPrimary }.firstOrNull() ?: calendars[0]
        }

        // Set onClickListener-s
        buttonSave.setOnClickListener(this::onButtonSaveClick)
        buttonCancel.setOnClickListener(this::onButtonCancelClick)

        accountName.setOnClickListener(this::onAccountClick)

        switchAllDay.setOnClickListener(this::onSwitchAllDayClick)

        dateFrom.setOnClickListener(this::onDateFromClick)
        timeFrom.setOnClickListener(this::onTimeFromClick)

        dateTo.setOnClickListener(this::onDateToClick)
        timeTo.setOnClickListener(this::onTimeToClick)

        addNotification.setOnClickListener(this::onAddNotificationClick)


        // Set-up fields

        val eventToEdit = originalEvent

        if (savedInstanceState != null) {
            val state = EditEventActivityState.fromBundle(savedInstanceState)

            originalEvent =
                    if (state.eventId != -1L)
                        calendarProvider.getEvent(this, state.eventId)
                    else
                        null

            calendar = calendars.find { it.calendarId == state.selectedCalendar } ?: calendars[0]

            accountName.text = calendar.name

            val color = originalEvent?.color ?: calendar.color
            eventTitleText.background = ColorDrawable(color.adjustCalendarColor(settings.darkerCalendarColors))

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                window.statusBarColor = color.scaleColor(0.7f)
            }

            from = state.from
            to = state.to
            eventTitleText.setText(state.title)
            note.setText(state.note)
            eventLocation.setText(state.location)
            switchAllDay.isChecked = state.isAllDay
            isAllDay = state.isAllDay

            isMuted = state.isMuted
            isAlarm = state.isAlarm
            isTask = state.isTask

            for (reminder in state.reminders) {
                addReminder(reminder, false)
            }

            for (reminder in state.allDayReminders) {
                addReminder(reminder, true)
            }

            updateDateTimeUI();
            updateReminders()
            updateTags(settings, true)
        }
        else if (eventToEdit != null) {

            //val details = eventToEdit.details

            val cal = calendars.find { it.calendarId == eventToEdit.calendarId }
            if (cal == null) {
                Toast.makeText(this, R.string.calendar_not_found, Toast.LENGTH_LONG).show()
                finish()
                return
            }
            calendar = cal

            isAllDay = eventToEdit.isAllDay
            switchAllDay.isChecked = isAllDay
            switchAllDay.isEnabled = false

            accountName.text = calendar.name
            eventTitleText.background = ColorDrawable(eventToEdit.color.adjustCalendarColor(settings.darkerCalendarColors))

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                window.statusBarColor = eventToEdit.color.scaleColor(0.7f)
            }

            eventTitleText.setText(eventToEdit.title)
            note.setText(eventToEdit.desc)
            eventLocation.setText(eventToEdit.location)

//            eventTimeSonze.setText(eventToEdit.timezone)

            from = DateTimeUtils.createCalendarTime(eventToEdit.startTime)
            to = DateTimeUtils.createCalendarTime(eventToEdit.endTime)

            if (eventToEdit.isAllDay) {
                val fromUtc = DateTimeUtils.createUTCCalendarTime(eventToEdit.startTime)
                val toUtc = DateTimeUtils.createUTCCalendarTime(eventToEdit.endTime)

                from.year = fromUtc.year
                from.month = fromUtc.month
                from.dayOfMonth = fromUtc.dayOfMonth

                to.year = toUtc.year
                to.month = toUtc.month
                to.dayOfMonth = toUtc.dayOfMonth
            }

            eventTitleText.clearFocus()

            updateDateTimeUI()

            for (reminder in eventToEdit.reminders) {
                addReminder(reminder, isAllDay)
            }

            updateReminders()

        }
        else {
            // Initialize default values
            accountName.text = calendar.name
            eventTitleText.background = ColorDrawable(calendar.color.adjustCalendarColor(settings.darkerCalendarColors))

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                window.statusBarColor = calendar.color.scaleColor(0.7f)
            }

            // Set default date and time
            var currentTime = System.currentTimeMillis()
            currentTime -= (currentTime % 1000)  // Drop millis

            from = DateTimeUtils.createCalendarTime(currentTime)
            from.addHours(Consts.NEW_EVENT_DEFAULT_ADD_HOURS)
            from.minute = 0
            from.second = 0

            to = DateTimeUtils.createCalendarTime(from.timeInMillis)
            to.addMinutes(settings.defaultNewEventDurationMinutes)

            DevLog.debug(LOG_TAG, "${from.timeInMillis}, ${to.timeInMillis}, $from, $to")

            updateDateTimeUI();

            addReminder(EventReminderRecord(Consts.NEW_EVENT_DEFAULT_NEW_EVENT_REMINDER), false)
            addReminder(EventReminderRecord(Consts.NEW_EVENT_DEFAULT_ALL_DAY_REMINDER), true)

            updateReminders()
        }
    }

    public override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        val regularReminders = reminders.filter { it.isForAllDay == false }.map { it.reminder }.toList();
        val allDayReminders = reminders.filter { it.isForAllDay == true }.map { it.reminder }.toList()

        val state =
                EditEventActivityState(
                        originalEvent?.eventId ?: -1L,
                        eventTitleText.text.toString(),
                        eventLocation.text.toString(),
                        note.text.toString(),
                        from,
                        to,
                        isAllDay,
                        regularReminders,
                        allDayReminders,
                        calendar.calendarId,
                        isMuted,
                        isTask,
                        isAlarm
                )

        state.toBundle(outState)
    }

    fun updateDateTimeUI() {

        if (isAllDay) {
            timeTo.visibility = View.GONE
            timeFrom.visibility = View.GONE
        }
        else {
            timeTo.visibility = View.VISIBLE
            timeFrom.visibility = View.VISIBLE
        }

        val dateFormat =
                DateUtils.FORMAT_SHOW_DATE or
                        DateUtils.FORMAT_SHOW_YEAR or
                        DateUtils.FORMAT_SHOW_WEEKDAY or
                        DateUtils.FORMAT_ABBREV_MONTH or
                        DateUtils.FORMAT_ABBREV_WEEKDAY

        val timeFormat =
                DateUtils.FORMAT_SHOW_TIME

        if (!isAllDay) {
            dateFrom.text = DateUtils.formatDateTime(this, from.timeInMillis, dateFormat)
            timeFrom.text = DateUtils.formatDateTime(this, from.timeInMillis, timeFormat)

            dateTo.text = DateUtils.formatDateTime(this, to.timeInMillis, dateFormat)
            timeTo.text = DateUtils.formatDateTime(this, to.timeInMillis, timeFormat)
        }
        else {
            val fromClean = DateTimeUtils.createCalendarTime(from.timeInMillis)
            fromClean.hourOfDay = 0
            fromClean.minute = 0

            val toClean = DateTimeUtils.createCalendarTime(to.timeInMillis)
            toClean.hourOfDay = 0
            toClean.minute = 0

            dateFrom.text = DateUtils.formatDateTime(this, fromClean.timeInMillis, dateFormat)
            dateTo.text = DateUtils.formatDateTime(this,
                    Math.max(toClean.timeInMillis-1000L, fromClean.timeInMillis),
                    dateFormat)
        }
    }

    fun updateTags(settings: Settings, updateLayouts: Boolean) {

        val enableTags = originalEvent == null && settings.enableTagButtons

        if (updateLayouts) {
            val layout = find<LinearLayout?>(R.id.add_event_layout_buttons)
            if (layout != null) {
                layout.visibility = if (enableTags) View.VISIBLE else View.GONE
            }
        }

        if (enableTags) {

            if (updateLayouts) {
                taskTagButton.visibility = if (settings.enableNotificationTaskTags) View.VISIBLE else View.GONE
                muteTagButton.visibility = if (settings.enableNotificationMuteTags) View.VISIBLE else View.GONE
                alarmTagButton.visibility = if (settings.enableNotificationAlarmTags) View.VISIBLE else View.GONE
            }

            if (isTask)
                taskTagButton.setTextColor(resources.getColor(R.color.event_selected_tag_color))
            else
                taskTagButton.setTextColor(resources.getColor(R.color.event_unselected_tag_color))

            if (isMuted)
                muteTagButton.setTextColor(resources.getColor(R.color.event_selected_tag_color))
            else
                muteTagButton.setTextColor(resources.getColor(R.color.event_unselected_tag_color))

            if (isAlarm)
                alarmTagButton.setTextColor(resources.getColor(R.color.event_selected_tag_color))
            else
                alarmTagButton.setTextColor(resources.getColor(R.color.event_unselected_tag_color))
        }
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
                        this@EditEventActivity.finish()
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

    @Suppress("UNUSED_PARAMETER")
    fun onButtonCancelClick(v: View) {
        onBackPressed()
    }

    @Suppress("UNUSED_PARAMETER")
    fun onAccountClick(v: View) {

        if (originalEvent != null)
            return // not editable anymore

        val builder = AlertDialog.Builder(this)

        val adapter = ArrayAdapter<String>(this, R.layout.simple_list_item_medium)

        val listCalendars = calendars.filter { !it.isReadOnly }.map { "${it.displayName} <${it.accountName}>" }.toList()

        adapter.addAll(listCalendars)


        builder.setCancelable(true)

        builder.setAdapter(adapter) {
            _, which ->
            if (which in 0..calendars.size-1) {

                calendar = calendars.get(which)

                persistentState.lastCalendar = calendar.calendarId

                accountName.text = calendar.name
                eventTitleText.background = ColorDrawable(
                        calendar.color.adjustCalendarColor(settings.darkerCalendarColors))

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    window.statusBarColor = calendar.color.scaleColor(0.7f)
                }

                eventTitleText.background = ColorDrawable(calendar.color.adjustCalendarColor(settings.darkerCalendarColors))
            }
        }
        builder.show()
    }

    @Suppress("UNUSED_PARAMETER")
    fun onButtonSaveClick(v: View) {

        var startTime = from.timeInMillis
        var endTime = to.timeInMillis

        if (isAllDay) {
            startTime = DateTimeUtils.createUTCCalendarDate(from.year, from.month, from.dayOfMonth).timeInMillis
            endTime = DateTimeUtils.createUTCCalendarDate(to.year, to.month, to.dayOfMonth).timeInMillis
        }

        val remindersToAdd = reminders.filter { it.isForAllDay == isAllDay }.map { it.reminder }.toList()

        var appendTags = ""
        if (originalEvent == null) {
            if (isMuted)
                appendTags += " #mute"
            if (isTask)
                appendTags += " #task"
            if (isAlarm)
                appendTags += " #alarm"
        }

        val details = CalendarEventDetails(
                        title = eventTitleText.text.toString() + appendTags,
                        desc = note.text.toString(),
                        location = eventLocation.text.toString(),
                        timezone = originalEvent?.timezone ?: calendar.timeZone,
                        startTime = startTime,
                        endTime = endTime,
                        isAllDay = isAllDay,
                        repeatingRule = originalEvent?.repeatingRule ?: "",
                        repeatingRDate = originalEvent?.repeatingRDate ?: "",
                        repeatingExRule = originalEvent?.repeatingExRule ?: "",
                        repeatingExRDate = originalEvent?.repeatingExRDate ?: "",
                        color = originalEvent?.color ?: 0,
                        reminders = remindersToAdd
        )

        val eventToEdit = originalEvent

        if (eventToEdit == null) {
            val eventId = CalendarChangeManager(CalendarProvider).createEvent(this, calendar.calendarId, calendar.owner, details)
            if (eventId != -1L) {
                DevLog.debug(this, LOG_TAG, "Event created: id=${eventId}")

                val nextReminder = calendarProvider.getNextEventReminderTime(this, eventId, startTime)
                if (nextReminder != 0L) {
                    Toast.makeText(
                            this,
                            resources.getString(R.string.event_was_created_reminder_at).format(dateToStr(this, nextReminder)),
                            Toast.LENGTH_LONG
                    ).show()
                }
                else {
                    Toast.makeText(this, R.string.event_was_created, Toast.LENGTH_LONG).show()
                }
                finish()

            } else {
                DevLog.error(this, LOG_TAG, "Failed to create event")

                AlertDialog.Builder(this)
                        .setMessage(R.string.new_event_failed_to_create_event)
                        .setPositiveButton(android.R.string.ok) { _, _ -> }
                        .show()
            }
        }
        else {
            val success = CalendarChangeManager(CalendarProvider).updateEvent(this, eventToEdit, details)

            if (success) {
                val nextReminder = calendarProvider.getNextEventReminderTime(this, eventToEdit.eventId, details.startTime)
                if (nextReminder != 0L) {
                    Toast.makeText(
                            this,
                            resources.getString(R.string.event_was_updated_next_reminder).format(dateToStr(this, nextReminder)),
                            Toast.LENGTH_LONG
                    ).show()
                }
                else {
                    Toast.makeText(this, getString(R.string.event_was_updated), Toast.LENGTH_LONG).show()
                }

                finish()
            }
            else {
                Toast.makeText(this, R.string.failed_to_update_event_details, Toast.LENGTH_LONG).show()
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun onSwitchAllDayClick(v: View) {
        isAllDay = switchAllDay.isChecked

        if (isAllDay) {
            to = DateTimeUtils.createCalendarTime(from.timeInMillis)
            to.addDays(1)
        }
        else {
            to = DateTimeUtils.createCalendarTime(from.timeInMillis)
            to.addMinutes(settings.defaultNewEventDurationMinutes)
        }

        updateDateTimeUI()
        updateReminders()
    }

    @Suppress("UNUSED_PARAMETER")
    fun onDateFromClick(v: View) {

        val durationMinutes = (to.timeInMillis - from.timeInMillis) / Consts.MINUTE_IN_MILLISECONDS

        val dialog = DatePickerDialog(
                this,
                {
                    _, year, month, day ->

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

    @Suppress("UNUSED_PARAMETER")
    fun onTimeFromClick(v: View) {

        val durationMinutes = (to.timeInMillis - from.timeInMillis) / Consts.MINUTE_IN_MILLISECONDS

        val dialog = TimePickerDialog(
                this,
                {
                    _, hour, min ->

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

    @Suppress("UNUSED_PARAMETER")
    fun onDateToClick(v: View) {

        val durationMinutes = (to.timeInMillis - from.timeInMillis) / Consts.MINUTE_IN_MILLISECONDS

        val dialog = DatePickerDialog(
                this,
                {
                    _, year, month, day ->

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

    @Suppress("UNUSED_PARAMETER")
    fun onTimeToClick(v: View) {

        val durationMinutes = (to.timeInMillis - from.timeInMillis) / Consts.MINUTE_IN_MILLISECONDS

        val dialog = TimePickerDialog(
                this,
                {
                    _, hour, min ->

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

    @Suppress("UNUSED_PARAMETER")
    fun onNotificationClick(v: View) {

        val wrapper = reminders.find { it.view == v }

        if (wrapper != null) {
            if (wrapper.isForAllDay)
                showAddReminderListAllDayDialog(wrapper.reminder, wrapper.view)
            else
                showAddReminderListDialog(wrapper.reminder, wrapper.view)
        }
    }

    fun showAddReminderCustomDialog(currentReminder: EventReminderRecord, existingReminderView: View?) {

        val dialogView = this.layoutInflater.inflate(R.layout.dialog_add_event_notification, null);

        val timeIntervalPicker = TimeIntervalPickerController(dialogView, null,
                Consts.NEW_EVENT_MAX_REMINDER_MILLISECONDS_BEFORE, false)
        timeIntervalPicker.intervalMilliseconds = currentReminder.millisecondsBefore

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

            val reminder = EventReminderRecord(
                    intervalMilliseconds,
                    if (isEmail) CalendarContract.Reminders.METHOD_EMAIL
                    else CalendarContract.Reminders.METHOD_DEFAULT
            )

            if (existingReminderView != null)
                modifyReminder(existingReminderView, reminder)
            else
                addReminder(reminder, isForAllDay = false)
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

    fun showAddReminderListDialog(currentReminder: EventReminderRecord, existingReminderView: View?) {

        if (currentReminder.method != CalendarContract.Reminders.METHOD_DEFAULT)
            return showAddReminderCustomDialog(currentReminder, existingReminderView)

        val intervalNames: Array<String> = this.resources.getStringArray(R.array.default_reminder_intervals)
        val intervalValues = this.resources.getIntArray(R.array.default_reminder_intervals_milliseconds_values)

        if (intervalValues.find { it.toLong() == currentReminder.millisecondsBefore } == null) {
            // reminder is not one of standard ones - we have to show custom idalog
            return showAddReminderCustomDialog(currentReminder, existingReminderView)
        }

        val builder = AlertDialog.Builder(this)

        val adapter = ArrayAdapter<String>(this, R.layout.simple_list_item_medium)

        adapter.addAll(intervalNames.toMutableList())

        builder.setCancelable(true)

        builder.setAdapter(adapter) {
            _, which ->
            if (which in 0..intervalValues.size-1) {

                val intervalMillis = intervalValues[which].toLong()
                if (intervalMillis != -1L) {
                    if (existingReminderView != null)
                        modifyReminder(existingReminderView, EventReminderRecord(intervalMillis))
                    else
                        addReminder(EventReminderRecord(intervalMillis), isForAllDay = false)
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

    fun showAddReminderCustomAllDayDialog(currentReminder: EventReminderRecord, existingReminderView: View?) {

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
            val pickerHr = timePicker.hourCompat
            val pickerMin = timePicker.minuteCompat

            val daysInMilliseconds = daysBefore * Consts.DAY_IN_MILLISECONDS
            val hrMinInMilliseconds = pickerHr * Consts.HOUR_IN_MILLISECONDS + pickerMin * Consts.MINUTE_IN_MILLISECONDS
            val reminderTimeMilliseconds = daysInMilliseconds - hrMinInMilliseconds

            val isEmail = isEmailCb.isChecked

            val reminder = EventReminderRecord(
                    reminderTimeMilliseconds,
                    if (isEmail) CalendarContract.Reminders.METHOD_EMAIL
                    else CalendarContract.Reminders.METHOD_DEFAULT
            )

            if (existingReminderView != null)
                modifyReminder(existingReminderView, reminder)
            else
                addReminder(reminder, isForAllDay = true)
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

    fun showAddReminderListAllDayDialog(currentReminder: EventReminderRecord, existingReminderView: View?) {

        if (currentReminder.method != CalendarContract.Reminders.METHOD_DEFAULT)
            return showAddReminderCustomAllDayDialog(currentReminder, existingReminderView)

        val reminderNames: Array<String> = this.resources.getStringArray(R.array.default_reminder_intervals_all_day)
        val reminderValues = this.resources.getIntArray(R.array.default_reminder_intervals_all_day_seconds_values)

        val enterManuallyValue = -2147483648

        if (reminderValues.find { it.toLong() == currentReminder.millisecondsBefore / 1000L } == null) {
            // reminder is not one of standard ones - we have to show custom idalog
            return showAddReminderCustomAllDayDialog(currentReminder, existingReminderView)
        }

        val builder = AlertDialog.Builder(this)

        val adapter = ArrayAdapter<String>(this, R.layout.simple_list_item_medium)

        adapter.addAll(reminderNames.toMutableList())

        builder.setCancelable(true)

        builder.setAdapter(adapter) {
            _, which ->
            if (which in 0..reminderValues.size-1) {

                val reminderSeconds = reminderValues[which]
                if (reminderSeconds != enterManuallyValue) {

                    val reminderTimeMillis = reminderSeconds.toLong() * 1000L

                    if (existingReminderView != null)
                        modifyReminder(existingReminderView, EventReminderRecord(reminderTimeMillis))
                    else
                        addReminder(EventReminderRecord(reminderTimeMillis), isForAllDay = true)
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


    @Suppress("UNUSED_PARAMETER")
    fun onAddNotificationClick(v: View) {
        if (!isAllDay) {
            showAddReminderListDialog(EventReminderRecord(Consts.NEW_EVENT_DEFAULT_NEW_EVENT_REMINDER), null)
        }
        else {
            showAddReminderListAllDayDialog(EventReminderRecord(Consts.NEW_EVENT_DEFAULT_ALL_DAY_REMINDER), null)
        }
    }

    private fun removeReminder(existingReminderView: View) {

        val wrapper = reminders.find { it.view == existingReminderView }
        if (wrapper != null) {
            reminders.remove(wrapper)
            notificationsLayout.removeView(wrapper.view)
        }
    }

    private fun modifyReminder(existingReminderView: View, newReminder: EventReminderRecord) {

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

    private fun addReminder(reminder: EventReminderRecord, isForAllDay: Boolean) {

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
            @Suppress("DEPRECATION")
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
        private const val LOG_TAG = "EditEventActivity"
        const val EVENT_ID = "event_id"
    }
}
