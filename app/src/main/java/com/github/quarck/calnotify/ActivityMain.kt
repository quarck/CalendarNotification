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
	private var serviceClient: ServiceClient? = null

	private var settings: Settings? = null

	private var toggleButtonEnableService : ToggleButton? = null
	private var toggleButtonHandlePebble : ToggleButton? = null

	private var easterFirstClick : Long = 0
	private var easterNumClicks = 0

	override fun onCreate(savedInstanceState: Bundle?)
	{
		super.onCreate(savedInstanceState)

		Logger.debug("main activity created")

		Logger.debug(TAG, "onCreateView")

		settings = Settings(this)

		setContentView(R.layout.activity_main)

		toggleButtonEnableService = findViewById(R.id.toggleButtonEnableService) as ToggleButton
		toggleButtonEnableService!!.isChecked = settings!!.isServiceEnabled

		toggleButtonHandlePebble = findViewById(R.id.toggleButtonHandlePebble) as ToggleButton
		toggleButtonHandlePebble!!.isChecked = settings!!.forwardToPebble
	}

	public fun OnBtnEnableServiceClick(v: View)
	{
		Logger.debug("saveSettingsOnClickListener.onClick()")

		saveSettings()

		(getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
			.cancel(Consts.NOTIFICATION_ID_UPDATED)

		serviceClient!!.checkPermissions()
	}

	public fun OnBtnHandlePebbleClick(v: View)
	{
		settings!!.forwardToPebble = toggleButtonHandlePebble!!.isChecked
		Toast.makeText(this, "Pebble enabled is is now ${settings!!.forwardToPebble}", 3).show()
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
				toggleButtonHandlePebble!!.visibility = View.VISIBLE
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

		var btnIdx : Long = if (v == findViewById(R.id.buttonTest)) 1 else 2;

		var dbNotification =
			db.addEvent(
			btnIdx + 232323232,
			"Test Notification ${btnIdx}",
			"This is a test notificaiton",
			0, 0, "")

		NotificationViewManager().postNotification(
			this,
			dbNotification,
			settings!!.notificationSettingsSnapshot
		)
	}

	private fun onNoPermissions()
	{
		Logger.debug(TAG, "onNoPermissions()!!!")

		toggleButtonEnableService!!.isChecked = false
		settings!!.isServiceEnabled = false

		val builder = AlertDialog.Builder(this)
		builder
			.setMessage(R.string.application_has_no_access)
			.setCancelable(false)
			.setPositiveButton(R.string.open_settings) {
				x, y ->
					val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
					startActivity(intent)
				}
			.setNegativeButton(R.string.cancel) {
				DialogInterface, Int -> finish()
			}

		// Create the AlertDialog object and return it
		builder.create().show()
	}

	private fun saveSettings()
	{
		Logger.debug(TAG, "Saving current settings")

		settings!!.isServiceEnabled = toggleButtonEnableService!!.isChecked
		settings!!.removeOriginal = true
	}

	public override fun onStart()
	{
		Logger.debug(TAG, "onStart()")
		super.onStart()

		serviceClient = ServiceClient({onNoPermissions()})

		if (serviceClient != null)
		{
			Logger.debug(TAG, "binding service")
			serviceClient!!.bindService(applicationContext)
		}
		else
		{
			Logger.debug(TAG, "onStart(): failed to create ServiceClient()")
		}

		postCachedNotifications(applicationContext)
	}

	public override fun onStop()
	{
		Logger.debug(TAG, "onStop()")
		serviceClient!!.unbindService(applicationContext)
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
