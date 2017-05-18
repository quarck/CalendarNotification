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

package com.github.quarck.calnotify.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import kotlin.reflect.KProperty


open class PersistentStorageBase(ctx: Context, prefName: String? = null) {
    protected var state: SharedPreferences

    init {
        state =
                if (prefName != null)
                    ctx.getSharedPreferences(prefName, Context.MODE_PRIVATE)
                else
                    PreferenceManager.getDefaultSharedPreferences(ctx)
    }

    fun edit(): SharedPreferences.Editor {
        return state.edit()
    }

    @SuppressLint("CommitPrefEdits")
    fun setBoolean(key: String, value: Boolean) {
        val editor = state.edit()
        editor.putBoolean(key, value)
        editor.commit()
    }

    @SuppressLint("CommitPrefEdits")
    fun setInt(key: String, value: Int) {
        val editor = state.edit()
        editor.putInt(key, value)
        editor.commit()
    }

    @SuppressLint("CommitPrefEdits")
    fun setLong(key: String, value: Long) {
        val editor = state.edit()
        editor.putLong(key, value)
        editor.commit()
    }

    @SuppressLint("CommitPrefEdits")
    fun setFloat(key: String, value: Float) {
        val editor = state.edit()
        editor.putFloat(key, value)
        editor.commit()
    }

    @SuppressLint("CommitPrefEdits")
    fun setString(key: String, value: String) {
        val editor = state.edit()
        editor.putString(key, value)
        editor.commit()
    }

    @SuppressLint("CommitPrefEdits")
    fun setStringSet(key: String, value: Set<String>) {
        val editor = state.edit()
        editor.putStringSet(key, value)
        editor.commit()
    }

    fun getBoolean(key: String, default: Boolean): Boolean = state.getBoolean(key, default)

    fun getInt(key: String, default: Int): Int = state.getInt(key, default)

    fun getLong(key: String, default: Long): Long = state.getLong(key, default)

    fun getFloat(key: String, default: Float): Float = state.getFloat(key, default)

    fun getString(key: String, default: String): String = state.getString(key, default)

    fun getStringSet(key: String, default: Set<String>): Set<String> = state.getStringSet(key, default)


    class BooleanProperty(val defaultValue: Boolean, val storageName: String? = null) {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): Boolean {
            val _this = (thisRef as PersistentStorageBase);
            val key = storageName ?: property.name
            return _this.getBoolean(key, defaultValue)
        }

        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
            val _this = (thisRef as PersistentStorageBase);
            val key = storageName ?: property.name
            _this.setBoolean(key, value)
        }
    }

    class IntProperty(val defaultValue: Int, val storageName: String? = null) {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): Int {
            val _this = (thisRef as PersistentStorageBase);
            val key = storageName ?: property.name
            return _this.getInt(key, defaultValue)
        }

        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
            val _this = (thisRef as PersistentStorageBase);
            val key = storageName ?: property.name
            _this.setInt(key, value)
        }
    }

    class LongProperty(val defaultValue: Long, val storageName: String? = null) {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): Long {
            val _this = (thisRef as PersistentStorageBase);
            val key = storageName ?: property.name
            return _this.getLong(key, defaultValue)
        }

        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Long) {
            val _this = (thisRef as PersistentStorageBase);
            val key = storageName ?: property.name
            _this.setLong(key, value)
        }
    }

    class FloatProperty(val defaultValue: Float, val storageName: String? = null) {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): Float {
            val _this = (thisRef as PersistentStorageBase);
            val key = storageName ?: property.name
            return _this.getFloat(key, defaultValue)
        }

        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Float) {
            val _this = (thisRef as PersistentStorageBase);
            val key = storageName ?: property.name
            _this.setFloat(key, value)
        }
    }

    class StringProperty(val defaultValue: String, val storageName: String? = null) {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): String {
            val _this = (thisRef as PersistentStorageBase);
            val key = storageName ?: property.name
            return _this.getString(key, defaultValue)
        }

        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: String) {
            val _this = (thisRef as PersistentStorageBase);
            val key = storageName ?: property.name
            _this.setString(key, value)
        }
    }

    class StringSetProperty(val defaultValue: Set<String>, val storageName: String? = null) {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): Set<String> {
            val _this = (thisRef as PersistentStorageBase);
            val key = storageName ?: property.name
            return _this.getStringSet(key, defaultValue)
        }

        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Set<String>) {
            val _this = (thisRef as PersistentStorageBase);
            val key = storageName ?: property.name
            _this.setStringSet(key, value)
        }
    }

}

