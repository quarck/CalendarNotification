package com.github.quarck.calnotify

import android.app.Activity
import android.app.PendingIntent
import android.content.ContentUris
import android.content.Intent
import android.os.Bundle
import android.provider.CalendarContract
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.StaggeredGridLayoutManager
import android.support.v7.widget.StaggeredGridLayoutManager.VERTICAL
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast

class ActivityMain : Activity()
{
	private var settings: Settings? = null;

	private var recyclerView: RecyclerView? = null;
	private var staggeredLayoutManager: StaggeredGridLayoutManager? = null;

	private var adapter: EventListAdapter? = null;

	private var events: Array<EventRecord>? = null

	override fun onCreate(savedInstanceState: Bundle?)
	{
		super.onCreate(savedInstanceState)

		Logger.debug(TAG, "onCreateView")

		setContentView(R.layout.activity_main)

		settings = Settings(this)

		recyclerView = findViewById(R.id.listEvents) as RecyclerView;
		staggeredLayoutManager = StaggeredGridLayoutManager(1, VERTICAL);
		recyclerView?.layoutManager = staggeredLayoutManager;

		events = arrayOf<EventRecord>(); // EventsStorage(this).events.toTypedArray(); // TODO: THIS IS BAD

		adapter = EventListAdapter(this, events!!);
		recyclerView?.adapter = adapter;

		adapter?.onItemReschedule = { v, p -> onItemReschedule(v ,p); }
		adapter?.onItemDismiss = { v, p -> onItemDismiss(v ,p); }
		adapter?.onItemClick = { v, p -> onItemClick(v ,p); }
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

		events = EventsStorage(this).events.toTypedArray();
		adapter?.events = events;
		adapter?.notifyDataSetChanged()
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

	private fun onItemClick(v: View, position: Int)
	{
		Toast.makeText(this, "Clicked ${position}", Toast.LENGTH_SHORT).show();

		if (position >= 0 && position < events!!.size)
		{
			var event = events!![position];

			var calendarIntentUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.eventId);
			var calendarIntent = Intent(Intent.ACTION_VIEW).setData(calendarIntentUri);

			startActivity(calendarIntent);
		}
	}

	private fun onItemReschedule(v: View, position: Int)
	{
		Toast.makeText(this, "Clicked ${position}", Toast.LENGTH_SHORT).show();

		if (position >= 0 && position < events!!.size)
		{
			var event = events!![position];

			var intent = Intent(this, ActivitySnooze::class.java)

			intent.putExtra(Consts.INTENT_NOTIFICATION_ID_KEY, event.notificationId);
			intent.putExtra(Consts.INTENT_EVENT_ID_KEY, event.eventId);
			intent.putExtra(Consts.INTENT_TYPE, Consts.INTENT_TYPE_SNOOZE);

			startActivity(intent);
		}
	}

	private fun onItemDismiss(v: View, position: Int)
	{
		Toast.makeText(this, "Clicked ${position}", Toast.LENGTH_SHORT).show();

		if (position >= 0 && position < events!!.size)
		{
			var event = events!![position];

			Logger.debug("Removing event id ${event.eventId} from DB and dismissing notification id ${event.notificationId}")

			var db = EventsStorage(this);
			db.deleteEvent(event.eventId);

			EventNotificationManager().onEventDismissed(this, event.eventId, event.notificationId);

			events = db.events.toTypedArray();
			adapter?.events = events;
			adapter?.notifyDataSetChanged()
		}
	}

	companion object
	{
		private val TAG = "MainActivity"
	}
}
