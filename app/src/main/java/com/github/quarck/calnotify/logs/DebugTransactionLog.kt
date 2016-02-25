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

package com.github.quarck.calnotify.logs

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.github.quarck.calnotify.eventsstorage.EventRecord
import com.github.quarck.calnotify.Settings
import java.text.DateFormat
import java.util.*

class DebugTransactionLog(context: Context)
: SQLiteOpenHelper(context, Companion.DATABASE_NAME, null, Companion.DATABASE_VERSION)
{
	private val settings = Settings(context)

	override fun onCreate(db: SQLiteDatabase)
	{
		var CREATE_PKG_TABLE =
			"CREATE " +
				"TABLE ${TABLE_NAME} " +
				"( " +
				"${KEY_ENTRY_ID} INTEGER PRIMARY KEY, " +
				"${KEY_ENTRY_TIME} INTEGER, " +
				"${KEY_SRC} TEXT, " +
				"${KEY_TYPE} TEXT, " +
				"${KEY_MESSAGE} TEXT" +
				" )"

		logger.debug("Creating DB TABLE using query: " + CREATE_PKG_TABLE)

		db.execSQL(CREATE_PKG_TABLE)
	}

	override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int)
	{
		logger.debug("DROPPING table")

		if (oldVersion != newVersion)
		{
			db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
			onCreate(db);
		}
	}

	fun dropAll()
	{
		val db = this.writableDatabase
		db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
		onCreate(db);
	}


	fun log(src: String, type: String, message: String)
	{
		if (!settings.debugTransactionLogEnabled)
			return;

		val db = this.writableDatabase

		var values = ContentValues();

		values.put(KEY_ENTRY_TIME, System.currentTimeMillis());
		values.put(KEY_SRC, src);
		values.put(KEY_TYPE, type);
		values.put(KEY_MESSAGE, message);

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
			logger.debug("How can this happen??!?!?!?!?!?")
		}
	}



	fun getMessages(entrySeparator: String = "\t", lineSeparator: String = "\r\n"): String
	{
		var sb = StringBuffer();

		var timeFormatter = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)

		val ret = LinkedList<EventRecord>()

		val query = "SELECT * FROM " + TABLE_NAME

		val db = this.readableDatabase
		val cursor = db.rawQuery(query, null)

		if (cursor.moveToFirst())
		{
			do
			{
				sb.append(cursor.getLong(0));
				sb.append(entrySeparator)
				sb.append(timeFormatter.format(Date(cursor.getLong(1))));
				sb.append(entrySeparator)
				sb.append(cursor.getString(2));
				sb.append(entrySeparator)
				sb.append(cursor.getString(3));
				sb.append(entrySeparator)
				sb.append(cursor.getString(4));
				sb.append(lineSeparator)

			} while (cursor.moveToNext())

			cursor.close()
		}

		return sb.toString()
	}

	companion object
	{
		private val logger = Logger("DBGLog")

		private val DATABASE_VERSION = 1

		private val DATABASE_NAME = "Log"
		private val TABLE_NAME = "Log"

		private val KEY_ENTRY_ID = "id"
		private val KEY_ENTRY_TIME = "time"
		private val KEY_SRC = "src"
		private val KEY_TYPE = "type"
		private val KEY_MESSAGE = "msg"
	}
}
