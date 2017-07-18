package com.github.quarck.calnotify.ui

import android.app.AlertDialog
import android.graphics.drawable.ColorDrawable
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v7.widget.Toolbar
import android.view.Menu
import android.view.View
import android.widget.*
import com.github.quarck.calnotify.Consts

import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.calendar.CalendarProvider
import com.github.quarck.calnotify.calendar.CalendarProviderInterface
import com.github.quarck.calnotify.calendar.CalendarRecord
import com.github.quarck.calnotify.utils.adjustCalendarColor
import com.github.quarck.calnotify.utils.find

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

    lateinit var calendar: CalendarRecord

    lateinit var settings: Settings

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


        settings = Settings(this)


        val calendars = calendarProvider.getCalendars(this)
        calendar = calendars.filter { it.isPrimary }.firstOrNull() ?: throw Exception("No primary calendar")

        accountName.text = calendar.name
        eventTitleText.background = ColorDrawable(calendar.color.adjustCalendarColor(settings.darkerCalendarColors))
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

    fun onButtonSaveClick(v: View) {

    }
}
