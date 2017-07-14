package com.github.quarck.calnotify.ui

import android.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v7.widget.Toolbar
import android.view.Menu
import android.view.View
import android.widget.*

import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.utils.find

class AddEventActivity : AppCompatActivity() {

    lateinit var eventTitleText: EditText

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

        find<Button?>(R.id.add_event_save) ?: throw Exception("Can't find add_event_save")
        find<ImageView?>(R.id.add_event_view_cancel) ?: throw Exception("Can't find add_event_view_cancel")

        find<TextView?>(R.id.account_name) ?: throw Exception("Can't find account_name")

        find<Switch?>(R.id.switch_all_day) ?: throw Exception("Can't find switch_all_day")

        find<Button?>(R.id.add_event_date_from) ?: throw Exception("Can't find add_event_date_from")
        find<Button?>(R.id.add_event_time_from) ?: throw Exception("Can't find add_event_time_from")

        find<Button?>(R.id.add_event_date_to) ?: throw Exception("Can't find add_event_date_to")
        find<Button?>(R.id.add_event_time_to) ?: throw Exception("Can't find add_event_time_to")

        find<EditText?>(R.id.event_location) ?: throw Exception("Can't find event_location")

        find<LinearLayout?>(R.id.notifications) ?: throw Exception("Can't find notifications")
        find<TextView?>(R.id.notification1) ?: throw Exception("Can't find notification1")
        find<TextView?>(R.id.add_notification) ?: throw Exception("Can't find add_notification")

        find<EditText?>(R.id.event_note) ?: throw Exception("Can't find event_note")
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
