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

package com.github.quarck.calnotify.ui

//import com.github.quarck.calnotify.logs.Logger
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.view.View
import android.widget.Toast
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.utils.find

class ReportABugActivity : AppCompatActivity() {
    private var easterEggCount = 0;
    private var firstClick = 0L;


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report_a_bug)

        setSupportActionBar(find<Toolbar?>(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        DevLog.debug(LOG_TAG, "onCreate")
    }

    override fun onResume() {
        super.onResume()
    }


//    @Suppress("unused", "UNUSED_PARAMETER")
//    fun OnButtonEmailDeveloper(v: View) {
//        DevLog.debug(LOG_TAG, "Emailing developer");
//
//        val shouldAttachLogs = findOrThrow<CheckBox>(R.id.checkboxIncludeLogs).isChecked
//
//        val email =
//                Intent(Intent.ACTION_SEND)
//                        .putExtra(Intent.EXTRA_EMAIL, arrayOf(DEVELOPER_EMAIL))
//                        .putExtra(Intent.EXTRA_SUBJECT, EMAIL_SUBJECT)
//                        .putExtra(Intent.EXTRA_TEXT, emailText)
//                        .setType(MIME_TYPE)
//
//        if (shouldAttachLogs) {
//
//            val logLines = LogcatProvider.getLog(this)
//
//            val devLogLines: String? =
//                    if (DevLoggerSettings(this).enabled)
//                        DevLog.getMessages(this)
//                    else
//                        null
//
//            val logsPath = File(cacheDir, Consts.LOGS_FOLDER)
//            logsPath.mkdir()
//
//            val newFile = File(logsPath, LOG_FILE_ATTACHMENT)
//
//            PrintWriter(newFile).use {
//                writer ->
//
//                if (devLogLines != null)
//                    writer.print(LOGCAT_HEADER)
//
//                for (line in logLines) {
//                    writer.print(line)
//                    writer.print("\n")
//                }
//
//                if (devLogLines != null) {
//                    writer.print(DEVLOG_HEADER)
//                    writer.print(devLogLines)
//                }
//            }
//
//            val contentUri = FileProvider.getUriForFile(this, Consts.FILE_PROVIDER_ID, newFile)
//
//            if (contentUri != null) {
//                email.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) // temp permission for receiving app to read this file
//                email.putExtra(Intent.EXTRA_STREAM, contentUri)
//            }
//        }
//
//        try {
//            startActivity(email);
//        }
//        catch (ex: Exception) {
//            DevLog.error(this, LOG_TAG, "cannot open email client: ${ex.detailed}")
//        }
//    }

    @Suppress("unused", "UNUSED_PARAMETER")
    fun OnButtonEasterEgg(v: View) {
        if (easterEggCount == 0) {
            firstClick = System.currentTimeMillis();
        }

        if (++easterEggCount > 13) {
            if (System.currentTimeMillis() - firstClick < 5000L) {
                Settings(this).devModeEnabled = true
                Toast.makeText(this, "Developer Mode Enabled", Toast.LENGTH_LONG).show()
            }
            else {
                easterEggCount = 0;
                firstClick = 0L;
            }
        }
    }

//    val emailText: String  by lazy {
//
//        val pInfo = packageManager.getPackageInfo(packageName, 0);
//
//        """Please describe your problem or suggestion below this text (in English)
//
//If you are not reporting a problem, you could remove device details that were automatically added to this message.
//
//Android version: ${Build.VERSION.RELEASE}
//Device: ${Build.MANUFACTURER} ${Build.MODEL}
//Android build: ${Build.DISPLAY}
//App version: ${pInfo.versionName} (${pInfo.versionCode})
//
//<type your feedback / request here>
//
//"""
//    }

    companion object {
        private const val LOG_TAG = "ActivityHelpAndFeedback"
//
//        const val DEVELOPER_EMAIL = "quarck@gmail.com"
//        const val MIME_TYPE = "message/rfc822"
//        const val EMAIL_SUBJECT = "Calendar Notification Plus Feedback"
//
//        const val WIKI_URL = "https://github.com/quarck/CalendarNotification/wiki"
//
//        const val LOG_FILE_ATTACHMENT = "calnotify.log"
//
//        const val LOGCAT_HEADER = """
//
//==========================================================================
//LOGCAT LOGS:
//==========================================================================
//
//"""
//
//        const val DEVLOG_HEADER = """
//
//==========================================================================
//DEV LOGS:
//==========================================================================
//
//"""
    }
}
