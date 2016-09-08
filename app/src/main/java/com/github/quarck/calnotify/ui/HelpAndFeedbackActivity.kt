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
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.view.View
import android.widget.CheckBox
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.logs.LogcatProvider
import com.github.quarck.calnotify.logs.Logger
import com.github.quarck.calnotify.utils.find
import com.github.quarck.calnotify.utils.isKitkatOrAbove

class HelpAndFeedbackActivity : AppCompatActivity() {
    private var easterEggCount = 0;
    private var firstClick = 0L;


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_help_and_feedback)

        setSupportActionBar(find<Toolbar?>(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        val isKK = isKitkatOrAbove
        if (!isKK) {
            find<CheckBox>(R.id.checkboxIncludeLogs).visibility = View.GONE
            find<CheckBox>(R.id.textViewLogFileNote).visibility = View.GONE
        }

        logger.debug("onCreate")
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    fun OnButtonEmailDeveloper(v: View) {
        logger.debug("Emailing developer");

        var content = emailText

        val shouldAttachLogs = find<CheckBox>(R.id.checkboxIncludeLogs).isChecked
        if (shouldAttachLogs) {

            val logLines = LogcatProvider.getLog(this).joinToString(separator = "\n")

            content = "$content" +
                    "\n\n\n\n" +
                    "-------------------------- log messages ----------------------------" +
                    "\n" +
                    "$logLines"
        }

        val email =
                Intent(Intent.ACTION_SEND)
                        .putExtra(Intent.EXTRA_EMAIL, arrayOf(developerEmail))
                        .putExtra(Intent.EXTRA_SUBJECT, emailSubject)
                        .putExtra(Intent.EXTRA_TEXT, emailText)
                        .setType(content)

        try {
            startActivity(email);
        } catch (ex: Exception) {
            logger.error("cannot open email client", ex)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun OnButtonWiki(v: View) = startActivity(Intent.parseUri(wikiUri, 0))

    @Suppress("unused", "UNUSED_PARAMETER")
    fun OnButtonEasterEgg(v: View) {
        if (easterEggCount == 0) {
            firstClick = System.currentTimeMillis();
        }

        if (++easterEggCount > 13) {
            if (System.currentTimeMillis() - firstClick < 5000L) {
                startActivity(Intent(this, TestButtonsAndToDoActivity::class.java))
            } else {
                easterEggCount = 0;
                firstClick = 0L;
            }
        }
    }

    val emailText: String  by lazy {

        val pInfo = packageManager.getPackageInfo(packageName, 0);

        """Please describe your problem or suggestion below this text (English/Russian languages only)

If you are not reporting a problem, you could remove android and hardware details that were automatically added to this message.

Android version: ${Build.VERSION.RELEASE}
Device: ${Build.MANUFACTURER} ${Build.MODEL}
Android build: ${Build.DISPLAY}
App version: ${pInfo.versionName} (${pInfo.versionCode})

<type your feedback / request here>

"""
    }

    companion object {
        var logger = Logger("ActivityHelpAndFeedback")

        val developerEmail = "s.parshin.sc@gmail.com"
        val mimeType = "message/rfc822"
        val emailSubject = "Calendar Notification Plus Feedback"

        val wikiUri = "https://github.com/quarck/CalendarNotification/wiki"
    }
}
