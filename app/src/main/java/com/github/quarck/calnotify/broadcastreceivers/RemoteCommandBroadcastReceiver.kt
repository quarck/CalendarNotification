package com.github.quarck.calnotify.broadcastreceivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.app.ApplicationController

open class RemoteCommandBroadcastReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null)
            return

        /* Currently ignored - placeholder for the future */
    }
}