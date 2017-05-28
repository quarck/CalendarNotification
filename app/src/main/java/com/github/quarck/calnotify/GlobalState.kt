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

package com.github.quarck.calnotify

import android.content.Context

// This storage is wiped every time app is restarted. Only keep variables
// that are instance-specific here
class GlobalState : android.app.Application() {
    var lastNotificationRePost: Long = 0
    var lastTimerBroadcastReceived: Long = 0
}

val Context.globalState: GlobalState
    get() = applicationContext as GlobalState

