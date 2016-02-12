package com.github.quarck.calnotify

import android.os.Bundle
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.view.View

class ActivityHelpAndFeedback : Activity()
{
	override fun onCreate(savedInstanceState: Bundle?)
    {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_help_and_feedback)
	}

    public fun OnTextViewCreditsClick(v: View) = startActivity(Intent.parseUri(imageCreditUri, 0))

    public fun OnTextViewKotlinClick(v: View) = startActivity(Intent.parseUri(kotlinUri, 0))

    public fun OnButtonEmailDeveloper(v: View)
    {
        var email = Intent(Intent.ACTION_SEND);
        email.putExtra(Intent.EXTRA_EMAIL, arrayOf(developerEmail));
        email.putExtra(Intent.EXTRA_SUBJECT,emailSubject);
        email.putExtra(Intent.EXTRA_TEXT,emailText);
        email.setType(mimeType);
        startActivity(email);
    }

    companion object
    {
        var imageCreditUri = "http://cornmanthe3rd.deviantart.com/"
        var kotlinUri = "https://kotlinlang.org/"

        var developerEmail = "s.parshin.sc@gmail.com"
        var emailSubject = "Advanced Calendar Notification Feedback / Question"
        var emailText = "Please describe your question or feedback in English or Russian:\n\n"
        var mimeType = "message/rfc822"
    }
}
