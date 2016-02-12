package com.github.quarck.calnotify

import android.os.Bundle
import android.app.Activity
import android.content.Intent
import android.view.View

class ActivityTestButtonsAndToDo : Activity()
{

	override fun onCreate(savedInstanceState: Bundle?)
	{
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_test_buttons_and_to_do)
	}


	public fun OnButtonTestActivityClick(v: View)
	{
		startActivity(Intent(applicationContext, ActivitySnooze::class.java));
	}

	public fun OnButtonTestClick(v: View)
	{
		var db = EventsStorage(this)

		var first = (v == findViewById(R.id.buttonTest));

		var currentTime = System.currentTimeMillis();

		var dbNotification =
			db.addEvent(
				if (first) 101010101L else currentTime,
				"Test Notification ${if (first) "first" else ((currentTime/100) % 10000).toString()}",
				"This is a test notificaiton",
				0,
				0,
				"",
				System.currentTimeMillis(),
				false
			)

		EventNotificationManager().postEventNotifications(applicationContext, false);
	}


}
