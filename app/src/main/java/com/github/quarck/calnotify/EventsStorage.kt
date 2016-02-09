package com.github.quarck.calnotify

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.*

data class EventRecord(
	val eventId: Long,
	var notificationId: Int,
	var title: String,
	var description: String,
	var startTime: Long,
	var endTime: Long,
	var location: String,
	var snoozedUntil: Long = 0
)

public class EventsStorage(context: Context)
	: SQLiteOpenHelper(context, EventsStorage.DATABASE_NAME, null, EventsStorage.DATABASE_VERSION)
{
	fun addEvent(
		eventId: Long,
		title: String,
		description: String,
		startTime: Long, endTime: Long,
		location: String
	): EventRecord
	{
		var ret =
			EventRecord(
				eventId = eventId,
				notificationId = 0,
				title = title,
				description = description,
				startTime = startTime,
				endTime = endTime,
				location = location
			)

		synchronized (EventsStorage::class.java) {
			ret.notificationId = nextNotificationId()
			addEvent(ret)
		}

		return ret
	}

	fun addEvent(event: EventRecord)
		= synchronized (EventsStorage::class.java) {
				if (event.notificationId == 0)
					event.notificationId = nextNotificationId()
				addEventImpl(event)
			}

	fun updateEvent(event: EventRecord)
	 	= synchronized(EventsStorage::class.java) { updateEventImpl(event) }

	fun getEvent(eventId: Long): EventRecord?
		= synchronized(EventsStorage::class.java) { return getEventImpl(eventId)}

	fun deleteEvent(eventId: Long)
		= synchronized(EventsStorage::class.java) { deleteEventImpl(eventId) }

	fun deleteEvent(ev: EventRecord)
		= synchronized(EventsStorage::class.java) { deleteEventImpl(ev.eventId) }

	val events: List<EventRecord>
		get() = synchronized(EventsStorage::class.java) { return eventsImpl }


	////////////////////////// Implementations for DB operations //////////////////////
	///// TODO: move into *Impl class

	override fun onCreate(db: SQLiteDatabase)
	{
		var CREATE_PKG_TABLE =
			"CREATE " +
				"TABLE $TABLE_NAME " +
				"( " +
				"$KEY_EVENTID INTEGER PRIMARY KEY, " +
				"$KEY_NOTIFICATIONID INTEGER, " +
				"$KEY_TITLE TEXT, " +
				"$KEY_DESC TEXT, " +
				"$KEY_START INTEGER, " +
				"$KEY_END INTEGER, " +
				"$KEY_LOCATION LOCATION, " +
				"$KEY_SNOOZED_UNTIL INTEGER, " +
				"$KEY_RESERVED_STR1 TEXT, " +
				"$KEY_RESERVED_STR2 TEXT, " +
				"$KEY_RESERVED_STR3 TEXT, " +
				"$KEY_RESERVED_INT1 INTEGER, " +
				"$KEY_RESERVED_INT2 INTEGER, " +
				"$KEY_RESERVED_INT3 INTEGER" +
				" )"

		Logger.debug(TAG, "Creating DB TABLE using query: " + CREATE_PKG_TABLE)

		db.execSQL(CREATE_PKG_TABLE)

		val CREATE_INDEX = "CREATE UNIQUE INDEX $INDEX_NAME ON $TABLE_NAME ($KEY_EVENTID)"

		Logger.debug(TAG, "Creating DB INDEX using query: " + CREATE_INDEX)

		db.execSQL(CREATE_INDEX)
	}

	override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int)
	{
		Logger.debug(TAG, "DROPPING table and index")

		if (oldVersion != newVersion)
		{
			TODO("This has to be implemented whenever you are going to extend the database")
		}
	}

	private fun addEventImpl(event: EventRecord)
	{
		Logger.debug(TAG, "addEvent " + event.toString())

		val db = this.writableDatabase

		val values = eventRecordToContentValues(event, true)

		try {
			db.insertOrThrow(TABLE_NAME, // table
				null, // nullColumnHack
				values) // key/value -> keys = column names/ values = column
			// values
			db.close()
		}
		catch (ex: android.database.sqlite.SQLiteConstraintException)
		{
			// Close Db before attempting to open it again from another method
			db.close()

			Logger.debug(TAG, "This entry (${event.eventId}) is already in the DB, updating!")
			updateEventImpl(event)
		}
	}

	private fun updateEventImpl(event: EventRecord)
	{
		val db = this.writableDatabase

		val values = eventRecordToContentValues(event)

		db.update(TABLE_NAME, // table
			values, // column/value
			KEY_EVENTID + " = ?", // selections
			arrayOf<String>(event.eventId.toString())) // selection args

		db.close()
	}

	private fun nextNotificationId(): Int
	{
		var ret = 0;

		val db = this.readableDatabase

		Logger.debug(TAG, "nextNotificationId")

		val query = "SELECT MAX($KEY_NOTIFICATIONID) FROM " + TABLE_NAME

		val cursor = db.rawQuery(query, null)

		if (cursor != null && cursor.moveToFirst())
		{
			try
			{
				ret = cursor.getString(0).toInt() + 1
			}
			catch (ex: Exception)
			{
				ret = 0;
			}
		}

		cursor?.close()

		if (ret == 0)
			ret = Consts.NOTIFICATION_ID_DYNAMIC_FROM;

		return ret
	}

	private fun getEventImpl(eventId: Long): EventRecord?
	{
		val db = this.readableDatabase

		val cursor = db.query(TABLE_NAME, // a. table
			SELECT_COLUMNS, // b. column names
			" $KEY_EVENTID = ?", // c. selections
			arrayOf<String>(eventId.toString()), // d. selections args
			null, // e. group by
			null, // f. h aving
			null, // g. order by
			null) // h. limit

		var event: EventRecord? = null

		if (cursor != null)
		{
			if (cursor.moveToFirst())
				event = cursorToEventRecord(cursor)

			cursor.close()
		}

		return event
	}


	private val eventsImpl: List<EventRecord>
		get()
		{
			val ret = LinkedList<EventRecord>()

			val query = "SELECT * FROM " + TABLE_NAME

			val db = this.readableDatabase
			val cursor = db.rawQuery(query, null)

			if (cursor.moveToFirst())
			{
				do
				{
					ret.add(cursorToEventRecord(cursor))

				} while (cursor.moveToNext())

				cursor.close()
			}

			return ret
		}

	private fun deleteEventImpl(eventId: Long)
	{
		val db = this.writableDatabase

		db.delete(TABLE_NAME, // table name
			KEY_EVENTID + " = ?", // selections
			arrayOf<String>(eventId.toString())) // selections args

		db.close()

		Logger.debug(TAG, "deleteNotification ${eventId}")
	}

	private fun eventRecordToContentValues(event: EventRecord, includeId: Boolean = false): ContentValues
	{
		var values = ContentValues()

		if (includeId)
			values.put(KEY_EVENTID, event.eventId)

		values.put(KEY_NOTIFICATIONID, event.notificationId)
		values.put(KEY_TITLE, event.title)
		values.put(KEY_DESC, event.description)
		values.put(KEY_START, event.startTime)
		values.put(KEY_END, event.endTime)
		values.put(KEY_LOCATION, event.location)
		values.put(KEY_SNOOZED_UNTIL, event.snoozedUntil)

		return values;
	}

	private fun cursorToEventRecord(cursor: Cursor): EventRecord
	{

		return EventRecord(
			eventId = cursor.getLong(0),
			notificationId = cursor.getInt(1),
			title = cursor.getString(2),
			description = cursor.getString(3),
			startTime = cursor.getLong(4),
			endTime = cursor.getLong(5),
			location = cursor.getString(6),
			snoozedUntil = cursor.getLong(7)
		)
	}

	companion object
	{
		private val TAG = "DB"

		private val DATABASE_VERSION = 1

		private val DATABASE_NAME = "Events"

		private val TABLE_NAME = "events"
		private val INDEX_NAME = "eventsIdx"

		private val KEY_EVENTID = "eventId"
		private val KEY_NOTIFICATIONID = "notificationId"
		private val KEY_TITLE = "title"
		private val KEY_DESC = "desc"
		private val KEY_START = "dtstart"
		private val KEY_END = "dtend"
		private val KEY_LOCATION = "location"
		private val KEY_SNOOZED_UNTIL = "snoozeUntil"
		private val KEY_RESERVED_STR1 = "resstr1"
		private val KEY_RESERVED_STR2 = "resstr2"
		private val KEY_RESERVED_STR3 = "resstr3"
		private val KEY_RESERVED_INT1 = "resint1"
		private val KEY_RESERVED_INT2 = "resint2"
		private val KEY_RESERVED_INT3 = "resint3"

		private val SELECT_COLUMNS = arrayOf<String>(
			KEY_EVENTID, KEY_NOTIFICATIONID,
			KEY_TITLE, KEY_DESC,
			KEY_START, KEY_END,
			KEY_LOCATION,
			KEY_SNOOZED_UNTIL
		)
	}
}
