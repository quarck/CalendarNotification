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
import android.os.Bundle
import android.view.View
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.logs.Logger

class ActivityHelpAndFeedback : Activity() {
    private var easterEggCount = 0;
    private var firstClick = 0L;


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_help_and_feedback)

        logger.debug("onCreate")
    }

    fun OnTextViewCreditsClick(v: View) = startActivity(Intent.parseUri(imageCreditUri, 0))

    fun OnTextViewKotlinClick(v: View) = startActivity(Intent.parseUri(kotlinUri, 0))

    fun OnTextViewGitHubClick(v: View) = startActivity(Intent.parseUri(githubUri, 0))

    fun OnButtonEmailDeveloper(v: View) {
        logger.debug("Emailing developer");

        var email = Intent(Intent.ACTION_SEND);
        email.putExtra(Intent.EXTRA_EMAIL, arrayOf(developerEmail));
        email.putExtra(Intent.EXTRA_SUBJECT, emailSubject);
        email.putExtra(Intent.EXTRA_TEXT, emailText);
        email.setType(mimeType);
        startActivity(email);
    }

    public fun OnButtonEasterEgg(v: View) {
        if (easterEggCount == 0) {
            firstClick = System.currentTimeMillis();
        }

        if (++easterEggCount > 13) {
            if (System.currentTimeMillis() - firstClick < 5000L) {
                startActivity(Intent(this, ActivityTestButtonsAndToDo::class.java))
            } else {
                easterEggCount = 0;
                firstClick = 0L;
            }
        }
    }

    companion object {
        var logger = Logger("ActivityHelpAndFeedback")

        var imageCreditUri = "http://cornmanthe3rd.deviantart.com/"
        var kotlinUri = "https://kotlinlang.org/"
        var githubUri = "https://github.com/quarck/CalendarNotification/issues"

        var developerEmail = "s.parshin.sc@gmail.com"
        var mimeType = "message/rfc822"
        var emailSubject = "Calendar Notification Plus Feedback"
        var emailText =
"""Please describe your problem or suggestion below this text (English/Russian languages only)
If submitting problem report, please include the following details:
 * Your device model
 * Android version
 * Stock / custom ROM? (leave blank if not sure)

<type your feedback / request here>
"""

    }
}
