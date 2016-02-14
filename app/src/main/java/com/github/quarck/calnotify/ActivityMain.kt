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

	private var svcClient = ServiceUINotifierClient();

	override fun onCreate(savedInstanceState: Bundle?)
	{
		super.onCreate(savedInstanceState)

		logger.debug( "onCreateView")

		setContentView(R.layout.activity_main)

		settings = Settings(this)

		recyclerView = findViewById(R.id.listEvents) as RecyclerView;
		staggeredLayoutManager = StaggeredGridLayoutManager(1, VERTICAL);
		recyclerView?.layoutManager = staggeredLayoutManager;

		events = arrayOf<EventRecord>(); // EventsStorage(this).events.toTypedArray(); // TODO: THIS IS BAD

		adapter = EventListAdapter(this, events!!);
		recyclerView?.adapter = adapter;

		adapter?.onItemReschedule = { v, p, e -> onItemReschedule(v ,p, e); }
		adapter?.onItemDismiss = { v, p, e -> onItemDismiss(v ,p, e); }
		adapter?.onItemClick = { v, p, e -> onItemClick(v ,p, e); }

		this.title = this.resources.getString(R.string.main_activity_title);
	}

	public override fun onStart()
	{
		logger.debug("onStart()")
		super.onStart()

		EventNotificationManager().postEventNotifications(applicationContext, true)
		AlarmUtils.scheduleNextAlarmForEvents(applicationContext)
	}

	public override fun onStop()
	{
		logger.debug("onStop()")
		super.onStop()
	}

	private fun reloadData()
	{
		events = EventsStorage(this).events.toTypedArray();
		adapter?.events = events;
		adapter?.notifyDataSetChanged()
	}

	public override fun onResume()
	{
		logger.debug("onResume")
		super.onResume()

		svcClient.bindService(this)
		svcClient.updateActivity = { runOnUiThread { reloadData() } }

		reloadData()
	}
	public override fun onPause()
	{
		logger.debug("onPause")

		svcClient.unbindService(this)

		super.onPause()
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

	private fun onItemClick(v: View, position: Int, eventId: Long)
	{
		logger.debug("onItemClick, pos=$position, eventId=$eventId");

		if (position >= 0 && position < events!!.size)
		{
			var event = events!![position];

			if (event.eventId == eventId)
			{
				var calendarIntentUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.eventId);
				var calendarIntent = Intent(Intent.ACTION_VIEW).setData(calendarIntentUri);

				startActivity(calendarIntent);
			}
			else
			{
				Toast.makeText(this, "ERROR: Sanity check failed, id mismatch", Toast.LENGTH_LONG).show();
			}
		}
	}

	private fun onItemReschedule(v: View, position: Int, eventId: Long)
	{
		logger.debug("onItemReschedule, pos=$position, eventId=$eventId");

		if (position >= 0 && position < events!!.size)
		{
			var event = events!![position];

			if (event.eventId == eventId)
			{
				var intent = Intent(this, ActivitySnooze::class.java)

				intent.putExtra(Consts.INTENT_NOTIFICATION_ID_KEY, event.notificationId);
				intent.putExtra(Consts.INTENT_EVENT_ID_KEY, event.eventId);

				startActivity(intent);
			}
			else
			{
				Toast.makeText(this, "ERROR: Sanity check failed, id mismatch", Toast.LENGTH_LONG).show();
				logger.error("Sanity check failed: id mismatch for event at position, expected id ${event.eventId}");
			}
		}
	}

	private fun onItemDismiss(v: View, position: Int, eventId: Long)
	{
		logger.debug("onItemDismiss, pos=$position, eventId=$eventId");

		if (position >= 0 && position < events!!.size)
		{
			var event = events!![position];

			if (event.eventId == eventId)
			{
				logger.debug("Removing event id ${event.eventId} from DB and dismissing notification id ${event.notificationId}")

				var db = EventsStorage(this);
				db.deleteEvent(event.eventId);

				EventNotificationManager().onEventDismissed(this, event.eventId, event.notificationId);

				AlarmUtils.scheduleNextAlarmForEvents(this);

				events = events?.filterIndexed { idx, ev -> idx != position }?.toTypedArray()
				adapter?.events = events;
				//adapter?.notifyDataSetChanged()
				adapter?.notifyItemRemoved(position)
			}
			else
			{
				Toast.makeText(this, "ERROR: Sanity check failed, id mismatch", Toast.LENGTH_LONG).show();
				logger.error("Sanity check failed: id mismatch for event at position, expected id ${event.eventId}");
			}
		}
	}

	companion object
	{
		private val logger = Logger("ActivityMain")
	}
}
