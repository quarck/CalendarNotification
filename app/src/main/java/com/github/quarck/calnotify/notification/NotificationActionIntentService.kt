package com.github.quarck.calnotify.notification

import android.app.IntentService
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.util.Log
import android.widget.Toast
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.dismissedeventsstorage.EventDismissType
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.textutils.EventFormatter
import com.github.quarck.calnotify.ui.UINotifier

class DisplayToast(private val context: Context, internal var text: String) : Runnable {
    override fun run() {
        Toast.makeText(context, text, Toast.LENGTH_LONG).show()
    }
}

open class NotificationActionIntentService  : IntentService("NotificationActionIntentService"){

    var handler = Handler()

    override fun onHandleIntent(intent: Intent?) {

        if (intent == null) {
            DevLog.error(this, LOG_TAG, "intent is null")
            return
        }

        val notificationAction = intent.getStringExtra(NOTIFICATION_ACTION_EXTRA)
        when (notificationAction) {
            NOTIFICATION_ACTION_SNOOZE ->
                onHandleSnooze(intent)

            NOTIFICATION_ACTION_DISMISS ->
                onHandleDismiss(intent)

            NOTIFICATION_ACTION_MUTE_TOGGLE ->
                onHandleMute(intent)

            else ->
                DevLog.error(this, LOG_TAG, "Unexpected intent extra $notificationAction")
        }
    }

    private fun onHandleSnooze(intent: Intent) {
        DevLog.debug(LOG_TAG, "onHandleSnooze")

        val isSnoozeAll = intent.getBooleanExtra(Consts.INTENT_SNOOZE_ALL_KEY, false)
        val isSnoozeAllCollapsed = intent.getBooleanExtra(Consts.INTENT_SNOOZE_ALL_COLLAPSED_KEY, false)

        if (isSnoozeAll){
            DevLog.info(this, LOG_TAG, "Snooze all from notification request")

            val snoozeDelay = intent.getLongExtra(Consts.INTENT_SNOOZE_PRESET, Settings(this).snoozePresets[0])

            if (ApplicationController.snoozeAllEvents(this, snoozeDelay, false, true) != null) {
                DevLog.info(this, LOG_TAG, "all visible snoozed by $snoozeDelay")
                onSnoozedBy(snoozeDelay)
            }

            UINotifier.notify(this, true)
        }
        else if (isSnoozeAllCollapsed) {
            DevLog.info(this, LOG_TAG, "Snooze all collapsed from notification request")

            val snoozeDelay = intent.getLongExtra(Consts.INTENT_SNOOZE_PRESET, Settings(this).snoozePresets[0])

            if (ApplicationController.snoozeAllCollapsedEvents(this, snoozeDelay, false, true) != null) {
                DevLog.info(this, LOG_TAG, "all collapsed snoozed by $snoozeDelay")
                onSnoozedBy(snoozeDelay)
            }

            UINotifier.notify(this, true)
        }
        else {
            val notificationId = intent.getIntExtra(Consts.INTENT_NOTIFICATION_ID_KEY, -1)
            val eventId = intent.getLongExtra(Consts.INTENT_EVENT_ID_KEY, -1)
            val instanceStartTime = intent.getLongExtra(Consts.INTENT_INSTANCE_START_TIME_KEY, -1)
            val snoozeDelay = intent.getLongExtra(Consts.INTENT_SNOOZE_PRESET, Settings(this).snoozePresets[0])

            if (notificationId != -1 && eventId != -1L && instanceStartTime != -1L) {
                if (ApplicationController.snoozeEvent(this, eventId, instanceStartTime, snoozeDelay) != null) {
                    DevLog.info(this, LOG_TAG, "event $eventId / $instanceStartTime snoozed by $snoozeDelay")
                    onSnoozedBy(snoozeDelay)
                }

                UINotifier.notify(this, true)
            } else {
                DevLog.error(this, LOG_TAG, "notificationId=$notificationId, eventId=$eventId, or type is null")
            }
        }

        ApplicationController.cleanupEventReminder(this)
    }

    private fun onHandleDismiss(intent: Intent) {
        DevLog.debug(LOG_TAG, "onHandleDismiss")

        val isDismissAll = intent.getBooleanExtra(Consts.INTENT_DISMISS_ALL_KEY, false)

        if (!isDismissAll) {
            val notificationId = intent.getIntExtra(Consts.INTENT_NOTIFICATION_ID_KEY, -1)
            val eventId = intent.getLongExtra(Consts.INTENT_EVENT_ID_KEY, -1)
            val instanceStartTime = intent.getLongExtra(Consts.INTENT_INSTANCE_START_TIME_KEY, -1)

            if (notificationId != -1 && eventId != -1L && instanceStartTime != -1L) {
                ApplicationController.dismissEvent(
                        this,
                        EventDismissType.ManuallyDismissedFromNotification,
                        eventId,
                        instanceStartTime,
                        notificationId)
                DevLog.info(this, LOG_TAG, "ServiceNotificationActionDismiss: event dismissed by user: $eventId")

                UINotifier.notify(this, true)
            } else {
                DevLog.error(this, LOG_TAG, "notificationId=$notificationId, eventId=$eventId, instanceStartTime=$instanceStartTime, or type is null")
            }
        }
        else {
            DevLog.info(this, LOG_TAG, "ServiceNotificationActionDismiss: dismiss all")

            ApplicationController.dismissAllButRecentAndSnoozed(
                    this,
                    EventDismissType.ManuallyDismissedFromNotification)

            UINotifier.notify(this, true)
        }

        ApplicationController.cleanupEventReminder(this)

    }

    private fun onHandleMute(intent: Intent) {
        DevLog.debug(LOG_TAG, "onHandleMute")

        val notificationId = intent.getIntExtra(Consts.INTENT_NOTIFICATION_ID_KEY, -1)
        val eventId = intent.getLongExtra(Consts.INTENT_EVENT_ID_KEY, -1)
        val instanceStartTime = intent.getLongExtra(Consts.INTENT_INSTANCE_START_TIME_KEY, -1)
        val muteAction = intent.getIntExtra(Consts.INTENT_MUTE_ACTION, -1)

        if (notificationId != -1 && eventId != -1L && instanceStartTime != -1L) {
            if (ApplicationController.toggleMuteForEvent(this, eventId, instanceStartTime, muteAction))
                DevLog.info(this, LOG_TAG, "event $eventId / $instanceStartTime mute toggled from $muteAction")

            UINotifier.notify(this, true)
        } else {
            DevLog.error(this, LOG_TAG, "notificationId=$notificationId, eventId=$eventId, or type is null")
        }
    }

    private fun onSnoozedBy(duration: Long) {
        val formatter = EventFormatter(this)
        val format = getString(R.string.event_snoozed_by)
        val text = String.format(format, formatter.formatTimeDuration(duration, 60L))
        handler.post(DisplayToast(this, text))
    }


    companion object {

        const val LOG_TAG = "NotificationActionintentService"

        const val NOTIFICATION_ACTION_EXTRA = "act"
        const val NOTIFICATION_ACTION_SNOOZE = "snooze"
        const val NOTIFICATION_ACTION_DISMISS = "dismiss"
        const val NOTIFICATION_ACTION_MUTE_TOGGLE = "mute"

        fun getIntentForSnooze(ctx: Context)
                = Intent(ctx, NotificationActionIntentService::class.java)
                    .putExtra(NOTIFICATION_ACTION_EXTRA, NOTIFICATION_ACTION_SNOOZE)

        fun getIntentForDismiss(ctx: Context)
                = Intent(ctx, NotificationActionIntentService::class.java)
                .putExtra(NOTIFICATION_ACTION_EXTRA, NOTIFICATION_ACTION_DISMISS)

        fun getIntentForMute(ctx: Context)
                = Intent(ctx, NotificationActionIntentService::class.java)
                .putExtra(NOTIFICATION_ACTION_EXTRA, NOTIFICATION_ACTION_MUTE_TOGGLE)
    }
}