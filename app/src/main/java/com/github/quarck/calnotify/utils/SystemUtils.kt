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
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.media.AudioManager
import android.os.PowerManager
import android.os.Vibrator
import android.provider.Settings


@Suppress("UNCHECKED_CAST")
fun<T> Context.service(svc: String) =  getSystemService(svc) as T

val Context.alarmManager: AlarmManager
    get() = service(Context.ALARM_SERVICE)

val Context.audioManager: AudioManager
    get() = service(Context.AUDIO_SERVICE)

val Context.powerManager: PowerManager
    get() = service(Context.POWER_SERVICE)

val Context.vibratorService: Vibrator
    get() = service(Context.VIBRATOR_SERVICE)

val Context.notificationManager: NotificationManager
    get() = service(Context.NOTIFICATION_SERVICE)

fun wakeLocked(pm: PowerManager, levelAndFlags: Int, tag: String, fn: () -> Unit) {

    val wakeLock = pm.newWakeLock(levelAndFlags, tag);
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

@Suppress("NOTHING_TO_INLINE")
inline fun backgroundWakeLocked(pm: PowerManager, levelAndFlags: Int, tag: String, noinline fn: () -> Unit) {

    val wakeLock = pm.newWakeLock(levelAndFlags, tag);
    if (wakeLock == null)
        throw Exception("Failed to acquire wakelock")

    wakeLock.acquire()

    background {
        try {
            fn();
        }
        finally {
            wakeLock.release()
        }
    }
}

val isMarshmallow: Boolean
    get() = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M

fun AlarmManager.setExactCompat(type: Int, triggerAtMillis: Long, operation: PendingIntent) {

    val build = android.os.Build.VERSION.SDK_INT

    if (build >= android.os.Build.VERSION_CODES.M) {
        // Marshmallow way of doing this
        return this.setExactAndAllowWhileIdle(type, triggerAtMillis, operation);
    }

    // KitKat way
    return this.setExact(type, triggerAtMillis, operation);
}

fun Notification.Builder.setShowWhenCompat(value: Boolean): Notification.Builder {
    val build = android.os.Build.VERSION.SDK_INT
    if (build >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) {
        setShowWhen(value)
    }
    return this
}

fun Notification.Builder.setSortKeyCompat(value: String): Notification.Builder {
    val build = android.os.Build.VERSION.SDK_INT
    if (build >= android.os.Build.VERSION_CODES.KITKAT_WATCH) {
        setSortKey(value)
    }
    return this
}

fun Notification.Builder.setEventCategoryCompat(): Notification.Builder {
    val build = android.os.Build.VERSION.SDK_INT
    if (build >= android.os.Build.VERSION_CODES.LOLLIPOP) {
        setCategory(Notification.CATEGORY_EVENT)
    }
    return this
}

fun isMarshmallowOrAbove(): Boolean {
    return android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M
}