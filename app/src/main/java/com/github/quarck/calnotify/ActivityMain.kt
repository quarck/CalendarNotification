package com.github.quarck.calnotify

import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.View.OnClickListener
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import android.widget.ToggleButton

class ActivityMain : Activity()
{
	private var settings: Settings? = null

	private var easterFirstClick : Long = 0
	private var easterNumClicks = 0

	override fun onCreate(savedInstanceState: Bundle?)
	{
		super.onCreate(savedInstanceState)

		Logger.debug(TAG, "onCreateView")

		settings = Settings(this)

		setContentView(R.layout.activity_main)
	}

	public fun OnButtonTestActivityClick(v: View)
	{
		startActivity(Intent(applicationContext, ActivitySnooze::class.java));
	}

	public fun OnEasterEggClick(v: View)
	{
		var currentTime = System.currentTimeMillis()

		if (easterFirstClick == 0L)
			easterFirstClick = currentTime

		easterNumClicks ++

		if (easterNumClicks >= 20)
		{
			if (currentTime - easterFirstClick < 10000)
			{
				(findViewById(R.id.buttonTest) as Button).visibility = View.VISIBLE
				(findViewById(R.id.buttonTest2) as Button).visibility = View.VISIBLE
				(findViewById(R.id.textViewALotOfSpaceForTest) as TextView).visibility = View.VISIBLE

				Toast.makeText(this, "Yeeeeeaaaa, hidden buttons are now active. Use them wisely!", 1).show()
			}

			easterNumClicks = 0
			easterFirstClick = 0L
		}
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

	public override fun onStart()
	{
		Logger.debug(TAG, "onStart()")
		super.onStart()

		EventNotificationManager().postEventNotifications(applicationContext, true)
		scheduleNextAlarmForEvents(applicationContext)
	}

	public override fun onStop()
	{
		Logger.debug(TAG, "onStop()")
		super.onStop()
	}

	public override fun onPause()
	{
		Logger.debug(TAG, "onPause")
		super.onPause()
	}

	public override fun onResume()
	{
		Logger.debug(TAG, "onResume")
		super.onResume()
	}

	override fun onCreateOptionsMenu(menu: Menu): Boolean
	{
		menuInflater.inflate(R.menu.main, menu)
		return true
	}

	override fun onOptionsItemSelected(item: MenuItem): Boolean
	{
		when (item.itemId)
		{
			R.id.action_settings ->
				startActivity(Intent(this, SettingsActivity::class.java))

			R.id.action_feedback ->
				startActivity(Intent(this, ActivityHelpAndFeedback::class.java))
		}

		return super.onOptionsItemSelected(item)
	}

	companion object
	{
		private val TAG = "MainActivity"
	}
}
