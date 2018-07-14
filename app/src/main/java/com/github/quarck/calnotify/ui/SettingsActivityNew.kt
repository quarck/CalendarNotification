package com.github.quarck.calnotify.ui

import android.content.Intent
import android.os.Bundle
import android.os.PersistableBundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.view.View
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.prefs.CalendarsActivity
import com.github.quarck.calnotify.prefs.activities.*
import com.github.quarck.calnotify.utils.find

class SettingsActivityNew : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_settings)

        DevLog.info(this, LOG_TAG, "onCreate")
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    fun onButtonHandledCalendars(v: View?) =
        startActivity(
                Intent(this, CalendarsActivity::class.java)
                        .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))

    @Suppress("unused", "UNUSED_PARAMETER")
    fun onButtonNotificaitonSettings(v: View?) =
            startActivity(
                    Intent(this, NotificationSettingsActivity::class.java)
                            .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))

    @Suppress("unused", "UNUSED_PARAMETER")
    fun onButtonSnoozeSettings(v: View?) =
            startActivity(
                    Intent(this, SnoozeSettingsActivity::class.java)
                            .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))

    @Suppress("unused", "UNUSED_PARAMETER")
    fun onButtonQuietHoursSettings(v: View?) =
            startActivity(
                    Intent(this, QuietHoursSettingsActivity::class.java)
                            .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))

    @Suppress("unused", "UNUSED_PARAMETER")
    fun onButtonBehaviourSettings(v: View?) =
            startActivity(
                    Intent(this, BehaviorSettingsActivity::class.java)
                            .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))

    @Suppress("unused", "UNUSED_PARAMETER")
    fun onButtonMiscSettings(v: View?) =
            startActivity(
                    Intent(this, MiscSettingsActivity::class.java)
                            .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))

    companion object {
        private const val LOG_TAG = "SettingsActivityNew"
    }

}