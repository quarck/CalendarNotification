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

package com.github.quarck.calnotify

import android.app.Activity
import android.app.PendingIntent
import android.content.ContentUris
import android.content.Intent
import android.os.AsyncTask
import android.os.Bundle
import android.provider.CalendarContract
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.StaggeredGridLayoutManager
import android.support.v7.widget.StaggeredGridLayoutManager.VERTICAL
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.RelativeLayout
import android.widget.Toast

class ActivityMain : Activity()
{
	private var settings: Settings? = null;

	private var recyclerView: RecyclerView? = null;
	private var staggeredLayoutManager: StaggeredGridLayoutManager? = null;

	private var adapter: EventListAdapter? = null;
	private var presenter: EventsPresenter? = null;

//	private var events: Array<EventRecord>? = null
//	private val eventsLock = Any()

	private var svcClient = ServiceUINotifierClient();

	private var reloadLayout: RelativeLayout? = null

	override fun onCreate(savedInstanceState: Bundle?)
	{
		super.onCreate(savedInstanceState)

		logger.debug( "onCreateView")

		setContentView(R.layout.activity_main)

		settings = Settings(this)

		recyclerView = findViewById(R.id.listEvents) as RecyclerView;
		staggeredLayoutManager = StaggeredGridLayoutManager(1, VERTICAL);
		recyclerView?.layoutManager = staggeredLayoutManager;

		adapter = EventListAdapter(this, arrayOf<EventRecord>());
		presenter = EventsPresenter(adapter!!);

		recyclerView?.adapter = adapter;

		adapter?.onItemReschedule = { v, p, e -> onItemReschedule(v ,p, e); }
		adapter?.onItemDismiss = { v, p, e -> onItemDismiss(v ,p, e); }
		adapter?.onItemClick = { v, p, e -> onItemClick(v ,p, e); }

		reloadLayout = findViewById(R.id.activity_main_reload_layout) as RelativeLayout;
	}

	public override fun onStart()
	{
		logger.debug("onStart()")
		super.onStart()

		EventsManager.onAppStarted(applicationContext);
	}

	public override fun onStop()
	{
		logger.debug("onStop()")
		super.onStop()
	}

	public override fun onResume()
	{
		logger.debug("onResume")
		super.onResume()

		svcClient.bindService(this)
		svcClient.updateActivity =
			{
				isCausedByUser ->

				if (isCausedByUser)
				{
					reloadData()
				}
				else
				{
					runOnUiThread {  reloadLayout?.visibility = View.VISIBLE }
				}
			}

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
		}

		return super.onOptionsItemSelected(item)
	}

	inner private class ReloadOperation: AsyncTask<Void?, Void?, Void?>()
	{
		private var events: Array<EventRecord>? = null

		override fun doInBackground(vararg p0: Void?): Void?
		{
			events = EventsStorage(this@ActivityMain).events.toTypedArray();
			return null;
		}

		override fun onPostExecute(result: Void?)
		{
			if (presenter != null && events != null)
				presenter?.setEventsToDisplay(events!!);
			else
				logger.debug("presenter or events is null");
		}
	}

	private fun reloadData()
	{
		ReloadOperation().execute();
	}

	fun onReloadButtonClick(v: View)
	{
		reloadLayout?.visibility = View.GONE;
		reloadData();
	}

	private fun onItemClick(v: View, position: Int, eventId: Long)
	{
		logger.debug("onItemClick, pos=$position, eventId=$eventId");

		var event = presenter?.getEventAtPosition(position)
		if (event != null)
		{
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

		var event = presenter?.getEventAtPosition(position)
		if (event != null)
		{
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

		var event = presenter?.getEventAtPosition(position)
		if (event != null)
		{
			if (event.eventId == eventId)
			{
				logger.debug("Removing event id ${event.eventId} from DB and dismissing notification id ${event.notificationId}")

				EventsManager.dismissEvent(this, event);
				DebugTransactionLog(this).log("ActivityMain", "remove", "Event dismissed by user: ${event.title}")

				presenter?.removeAt(position)
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
