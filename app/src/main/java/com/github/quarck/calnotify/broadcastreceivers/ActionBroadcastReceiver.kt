package com.github.quarck.calnotify.broadcastreceivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.logs.DevLog

open class ActionBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {

        if (context == null) {
            Log.e(LOG_TAG, "received onReceive() without context")
            return
        }

        if (intent == null) {
            Log.e(LOG_TAG, "received onReceive() without intent")
            return
        }

        when (intent.action) {
            Intent.ACTION_MY_PACKAGE_REPLACED ->
                ApplicationController.onAppUpdated(context)

            ACTION_EVENT_REMINDER, ACTION_EVENTEX_REMINDER ->
                ApplicationController.CalendarMonitor.onProviderReminderBroadcast(context, intent)

            Intent.ACTION_PROVIDER_CHANGED ->
                ApplicationController.onCalendarChanged(context)

            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED ->
                ApplicationController.onTimeChanged(context)
        }
        DevLog.debug(LOG_TAG, "onReceive");
    }

    companion object {
        private const val LOG_TAG = "ActionBroadcastReceiver";

        private const val ACTION_EVENT_REMINDER = "android.intent.action.EVENT_REMINDER"
        private const val ACTION_EVENTEX_REMINDER = "android.intent.action.EVENTEX_REMINDER"
    }
}
