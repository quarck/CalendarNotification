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

	override fun onCreate(savedInstanceState: Bundle?)
	{
		super.onCreate(savedInstanceState)

		Logger.debug(TAG, "onCreateView")

		settings = Settings(this)

		setContentView(R.layout.activity_main)
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

			R.id.activity_test ->
				startActivity(Intent(this, ActivityTestButtonsAndToDo::class.java))
		}

		return super.onOptionsItemSelected(item)
	}

	companion object
	{
		private val TAG = "MainActivity"
	}
}
