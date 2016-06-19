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

import android.app.Activity
import android.content.ContentUris
import android.content.Intent
import android.os.Bundle
import android.provider.CalendarContract
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.ToggleButton
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.calendar.CalendarIntents
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.calendar.EventDisplayStatus
import com.github.quarck.calnotify.utils.find
import com.github.quarck.calnotify.utils.toLongOrNull
import java.util.*

class TestButtonsAndToDoActivity : Activity() {

    private val settings by lazy { Settings(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test_buttons_and_to_do)

        find<TextView>(R.id.todo).visibility = View.VISIBLE;
        find<TextView>(R.id.log).text = "-- DISABLED - FOREVER --"
        find<ToggleButton>(R.id.debug_logging_toggle).isChecked = settings.debugTransactionLogEnabled;
        find<ToggleButton>(R.id.remove_original_event).isChecked = settings.removeOriginal
    }


    @Suppress("unused", "UNUSED_PARAMETER")
    fun OnDebugLoggingToggle(v: View) {
        if (v is ToggleButton) {
            settings.debugTransactionLogEnabled = v.isChecked;
  //          if (!v.isChecked)
//                DebugTransactionLog(this).dropAll();
        }
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    fun OnRemoveOriginalEventToggle(v: View) {
        if (v is ToggleButton) {
            settings.removeOriginal = v.isChecked
        }
    }


    @Suppress("unused", "UNUSED_PARAMETER")
    fun OnButtonTestActivityClick(v: View) {
        startActivity(Intent(applicationContext, SnoozeActivityNoRecents::class.java));
    }

    fun randomTitle(currentTime: Long): String {

        val wikipediaQuote =
            """ Some predictions of general relativity differ significantly from those of classical
            physics, especially concerning the passage of time, the geometry of space, the motion of
            bodies in free fall, and the propagation of light. Examples of such differences include
            gravitational time dilation, gravitational lensing, the gravitational redshift of light,
            and the gravitational time delay. The predictions of general relativity have been
            confirmed in all observations and experiments to date. Although general relativity is
            not the only relativistic theory of gravity, it is the simplest theory that is consistent
            with experimental data. However, unanswered questions remain, the most fundamental being
            how general relativity can be reconciled with the laws of quantum physics to produce a
            complete and self-consistent theory of quantum gravity."""

        val dict =
            wikipediaQuote.split(' ', '\r', '\n', '\t').filter { !it.isEmpty() }

        val sb = StringBuilder();
        val r = Random(currentTime);

        val len = r.nextInt(10);

        var prev = -1;
        for (i in 0..len) {
            var new: Int
            do { new = r.nextInt(dict.size) } while (new == prev)
            sb.append(dict[new])
            sb.append(" ")
            prev = new
        }

        return sb.toString();
    }

    private var cnt = 0;

    private val filterText: String
        get() = find<EditText>(R.id.edittext_debug_event_id).text.toString()

    @Suppress("unused", "UNUSED_PARAMETER")
    fun OnButtonFilterClick(v: View) {
        find<TextView>(R.id.log).text = " -- DISABLED --- FOREVER -- "
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    fun OnButtonViewClick(v: View) {
        val id = filterText.toLongOrNull()
        if (id != null) {
            startActivity(
                Intent(Intent.ACTION_VIEW).setData(
                    ContentUris.withAppendedId(
                        CalendarContract.Events.CONTENT_URI,
                        id)))
        }
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    fun OnButtonTestClick(v: View) {

        val first = (v.id == R.id.buttonTest);

        if (first) {
            settings.quietHoursOneTimeReminderEnabled = true
            ApplicationController.onMainActivityResumed(this, true)
        } else {
            val currentTime = System.currentTimeMillis();

            val eventId = if (first) 101010101L else 10000000L + (currentTime % 1000L)

            val event = EventAlertRecord(
                -1L,
                eventId,
                false,
                System.currentTimeMillis(),
                0,
                if (first) "Test Notification" else randomTitle(currentTime) + " " + ((currentTime / 100) % 10000).toString(),
                currentTime + 3600L * 1000L,
                currentTime + 2 * 3600L * 1000L,
                currentTime + 3600L * 1000L,
                currentTime + 2 * 3600L * 1000L,
                if ((cnt % 2) == 0) "" else "Hawthorne, California, U.S.",
                System.currentTimeMillis(),
                0L,
                EventDisplayStatus.Hidden,
                0xff660066.toInt()
            )

            cnt++;

            ApplicationController.onCalendarEventFired(this, event)
        }
    }
}
