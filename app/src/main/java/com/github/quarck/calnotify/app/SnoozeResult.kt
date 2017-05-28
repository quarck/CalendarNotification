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

package com.github.quarck.calnotify.app

import android.content.Context
import android.widget.Toast
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.textutils.dateToStr

enum class SnoozeType {
    Snoozed,
    Moved
}

data class SnoozeResult(val type: SnoozeType, val snoozedUntil: Long, val quietUntil: Long)

fun SnoozeResult.toast(context: Context) {

    var msg = ""

    val duration = Toast.LENGTH_LONG

    if (this.type == SnoozeType.Snoozed) {

        if (this.quietUntil != 0L) {

            val dateTime = dateToStr(context, this.quietUntil)
            val quietUntilFmt = context.resources.getString(R.string.snoozed_time_inside_quiet_hours)
            msg = String.Companion.format(quietUntilFmt, dateTime)
        }
        else {

            val dateTime = dateToStr(context, this.snoozedUntil)
            val snoozedUntil = context.resources.getString(R.string.snoozed_until_string)
            msg = "$snoozedUntil $dateTime"
        }
    }
    else if (this.type == SnoozeType.Moved) {
        val dateTime = dateToStr(context, this.snoozedUntil)
        val snoozedUntil = context.resources.getString(R.string.moved_to_string)
        msg = "$snoozedUntil $dateTime"
    }

    if (msg != "")
        Toast.makeText(context, msg, duration).show();
}
