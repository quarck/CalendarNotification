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

package com.github.quarck.calnotify.dismissedeventsstorage

import com.github.quarck.calnotify.calendar.EventAlertRecord

enum class EventDismissType(val code: Int) {
    ManuallyDismissedFromNotification(0),
    ManuallyDismissedFromActivity(1),
    AutoDismissedDueToCalendarMove(2),
    EventMovedUsingApp(3);

    companion object {
        @JvmStatic
        fun fromInt(v: Int) = values()[v]
    }

    val shouldKeep: Boolean
        get() = this != EventMovedUsingApp

    val canBeRestored: Boolean
        get() = this != AutoDismissedDueToCalendarMove && this != EventMovedUsingApp
}

data class DismissedEventAlertRecord(
    val event: EventAlertRecord,        // actual event that was dismissed
    val dismissTime: Long,              // when dismissal happened
    val dismissType: EventDismissType  // type of dismiss
)