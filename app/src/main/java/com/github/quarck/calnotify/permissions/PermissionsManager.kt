package com.github.quarck.calnotify.permissions

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat

object PermissionsManager
{
    private fun Context.hasPermission(perm: String) =
            ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED;

    private fun Activity.shouldShowRationale(perm: String) =
            ActivityCompat.shouldShowRequestPermissionRationale(this, perm)

    fun hasWriteCalendar(context: Context) = context.hasPermission(Manifest.permission.WRITE_CALENDAR)

    fun hasReadCalendar(context: Context) = context.hasPermission(Manifest.permission.READ_CALENDAR)

    fun hasAllPermissions(context: Context) = hasWriteCalendar(context) && hasReadCalendar(context)

    fun shouldShowRationale(activity: Activity) =
            activity.shouldShowRationale(Manifest.permission.WRITE_CALENDAR)
                    || activity.shouldShowRationale(Manifest.permission.READ_CALENDAR)

    fun requestPermissions(activity: Activity) =
        ActivityCompat.requestPermissions(activity,
                arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CALENDAR),
                0)
}