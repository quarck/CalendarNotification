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

package com.github.quarck.calnotify.notification

import android.app.IntentService
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.logs.DevLog
//import com.github.quarck.calnotify.logs.Logger
import com.github.quarck.calnotify.utils.powerManager
import com.github.quarck.calnotify.utils.wakeLocked


class TextToSpeechService : IntentService("TextToSpeechService") {

    val textToSpeechManager: TextToSpeechNotificationManagerInterface by lazy { TextToSpeechNotificationManager(this) }

    override fun onHandleIntent(intent: Intent?) {
        DevLog.debug(LOG_TAG, "onHandleIntent")

        wakeLocked(powerManager, PowerManager.PARTIAL_WAKE_LOCK, TTS_WAKE_LOCK_NAME) {
            if (intent != null) {
                val string = intent.getStringExtra(Consts.INTENT_TTS_TEXT)
                if (string != null)
                    textToSpeechManager.playText(string)
            }
        }
    }

    companion object {
        private const val LOG_TAG = "TextToSpeechService"
        private const val TTS_WAKE_LOCK_NAME = "TTS_WakeLock"

        fun playText(ctx: Context, text: String) {
            val intent = Intent(ctx, TextToSpeechService::class.java)
            intent.putExtra(Consts.INTENT_TTS_TEXT, text)
            ctx.startService(intent)
        }
    }
}