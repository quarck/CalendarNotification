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

package com.github.quarck.calnotify.UI

import android.os.Bundle
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.view.View
import android.widget.TextView
import com.github.quarck.calnotify.Logs.Logger
import com.github.quarck.calnotify.R

class ActivityHelpAndFeedback : Activity()
{
    private var easterEggCount = 0;
    private var firstClick = 0L;


	override fun onCreate(savedInstanceState: Bundle?)
    {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_help_and_feedback)

        logger.debug("onCreate")
	}

    public fun OnTextViewCreditsClick(v: View) = startActivity(Intent.parseUri(imageCreditUri, 0))

    public fun OnTextViewKotlinClick(v: View) = startActivity(Intent.parseUri(kotlinUri, 0))

    public fun OnButtonEmailDeveloper(v: View)
    {
        logger.debug("Emailing developer");

        var email = Intent(Intent.ACTION_SEND);
        email.putExtra(Intent.EXTRA_EMAIL, arrayOf(developerEmail));
        email.putExtra(Intent.EXTRA_SUBJECT, emailSubject);
        email.putExtra(Intent.EXTRA_TEXT, emailText);
        email.setType(mimeType);
        startActivity(email);
    }

    public fun OnButtonEasterEgg(v: View)
    {
        if (easterEggCount == 0)
        {
            firstClick = System.currentTimeMillis();
        }

        if (++easterEggCount > 13)
        {
            if (System.currentTimeMillis() - firstClick < 5000L)
            {
                startActivity(Intent(this, ActivityTestButtonsAndToDo::class.java))
            }
            else
            {
                easterEggCount = 0;
                firstClick = 0L;
            }
        }
    }

    companion object
    {
        var imageCreditUri = "http://cornmanthe3rd.deviantart.com/"
        var kotlinUri = "https://kotlinlang.org/"

        var developerEmail = "s.parshin.sc@gmail.com"
        var emailSubject = "Calendar Notification Plus Feedback"
        var emailText = "Please write your question or feedback below: (English/Russian languages only)\n\n"
        var mimeType = "message/rfc822"

        var logger = Logger("ActivityHelpAndFeedback")
    }
}
