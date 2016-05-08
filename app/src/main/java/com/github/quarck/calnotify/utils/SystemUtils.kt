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

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.media.AudioManager
import android.os.PowerManager
import android.os.Vibrator

fun<T> Context.service(svc: String) =  getSystemService(svc) as T

val Context.alarmManager: AlarmManager
    get() = service(Context.ALARM_SERVICE)

val Context.audioManager: AudioManager
    get() = service(Context.AUDIO_SERVICE)

val Context.powerManager: PowerManager
    get() = service(Context.POWER_SERVICE)

val Context.vibratorService: Vibrator
    get() = service(Context.VIBRATOR_SERVICE)

fun wakeLocked(pm: PowerManager, levelAndFlags: Int, tag: String, fn: () -> Unit) {

    var wakeLock = pm.newWakeLock(levelAndFlags, tag);
    if (wakeLock == null)
        throw Exception("Failed to acquire wakelock")

    try {
        wakeLock.acquire()
        fn();
    }
    finally {
        wakeLock.release()
    }
}

fun AlarmManager.setExactCompat(type: Int, triggerAtMillis: Long, operation: PendingIntent) {

    var build = android.os.Build.VERSION.SDK_INT

    if (build >= android.os.Build.VERSION_CODES.M) {
        // Marshmallow way of doing this
        return this.setExactAndAllowWhileIdle(type, triggerAtMillis, operation);
    } else if (build >= android.os.Build.VERSION_CODES.KITKAT) {
        // KitKat way
        return this.setExact(type, triggerAtMillis, operation);
    }
    // Old way
    return this.set(type, triggerAtMillis, operation);
}
