//
//   Calendar Notifications Plus
//   Copyright (C) 2018 Sergey Parshin (s.parshin.sc@gmail.com)
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


package com.github.quarck.calnotify.prefs.components

import android.content.Context
import android.content.Intent
import android.content.res.TypedArray
import android.preference.DialogPreference
import android.util.AttributeSet
import android.view.View
import com.github.quarck.calnotify.logs.DevLog


class NotificationChannelActivityShortcutPreference(context: Context, val attrs: AttributeSet)
    : DialogPreference(context, attrs) {

    override fun onBindView(view: View) {
        super.onBindView(view)
    }

    override fun onBindDialogView(view: View) {
        super.onBindDialogView(view)

        val channel = attrs.getAttributeValue(XML_NS, XML_ATTR)
        if (channel != null) {
            DevLog.info(LOG_TAG, "Got channel $channel, launching activity")

            val intent = Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            intent.putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
            intent.putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, channel)
            context.startActivity(intent)
        }
        else {
            DevLog.error(LOG_TAG, "NotificationChannelActivityShortcutPreference without channel name")
        }
    }

    override fun onDialogClosed(positiveResult: Boolean) {
        super.onDialogClosed(positiveResult)
    }

    override fun onSetInitialValue(restorePersistedValue: Boolean, defaultValue: Any?) {
    }

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any {
        return ""
    }

    companion object {
        const val XML_NS = "android"
        const val XML_ATTR = "defaultValue"

        const val LOG_TAG = "NotificationChannelPref"
    }
}
