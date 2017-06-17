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


package com.github.quarck.calnotify.permissions

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat

object PermissionsManager {
    private fun Context.hasPermission(perm: String) =
            ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED;

    private fun Activity.shouldShowRationale(perm: String) =
            ActivityCompat.shouldShowRequestPermissionRationale(this, perm)

    fun hasWriteCalendarNoCache(context: Context)
            = context.hasPermission(Manifest.permission.WRITE_CALENDAR)

    fun hasReadCalendarNoCache(context: Context)
            = context.hasPermission(Manifest.permission.READ_CALENDAR)

    private var hasWriteCalendarCached: Boolean = false
    private var hasReadCalendarCached: Boolean = false

    fun hasWriteCalendar(context: Context): Boolean {
        if (!hasWriteCalendarCached)
            hasWriteCalendarCached = hasWriteCalendarNoCache(context)
        return hasWriteCalendarCached
    }

    fun hasReadCalendar(context: Context): Boolean {
        if (!hasReadCalendarCached)
            hasReadCalendarCached = hasReadCalendarNoCache(context)
        return hasReadCalendarCached
    }

    fun hasAllPermissions(context: Context) = hasWriteCalendar(context) && hasReadCalendar(context)

    fun hasAllPermissionsNoCache(context: Context) = hasWriteCalendarNoCache(context) && hasReadCalendarNoCache(context)

    fun shouldShowRationale(activity: Activity) =
            activity.shouldShowRationale(Manifest.permission.WRITE_CALENDAR)
                    || activity.shouldShowRationale(Manifest.permission.READ_CALENDAR)

    fun requestPermissions(activity: Activity) =
            ActivityCompat.requestPermissions(activity,
                    arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR), 0)
}