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

import android.content.Context
import android.text.format.DateUtils
import java.util.*


fun Calendar.dayEquals(other: Calendar)
        = this.get(Calendar.YEAR) == other.get(Calendar.YEAR) &&
        this.get(Calendar.DAY_OF_YEAR) == other.get(Calendar.DAY_OF_YEAR);

fun calendarWithTimeMillisHourAndMinute(timeMillis: Long, hour: Int, minute: Int): Calendar {

    var ret = Calendar.getInstance()
    ret.timeInMillis = timeMillis
    ret.set(Calendar.HOUR_OF_DAY, hour)
    ret.set(Calendar.MINUTE, minute)
    ret.set(Calendar.SECOND, 0)
    return ret
}