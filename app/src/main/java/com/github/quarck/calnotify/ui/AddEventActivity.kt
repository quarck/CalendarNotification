package com.github.quarck.calnotify.ui

import android.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v7.widget.Toolbar
import android.view.Menu
import android.view.View
import android.widget.EditText

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
