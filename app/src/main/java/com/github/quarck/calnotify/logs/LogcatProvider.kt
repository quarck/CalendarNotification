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
//   Based on source code from aLogcat project (https://f-droid.org/repository/browse/?fdid=org.jtb.alogcat)
//

package com.github.quarck.calnotify.logs

import android.content.Context
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

object LogcatProvider {
    fun getLog(context: Context): List<String> {

        var ret = listOf<String>()

        try {
            // -d - don't block
            // -b xxx - all buffers
            // -v threadtime - format
            // *:V - filter, everything verbose
            val args = arrayOf(
                    "logcat",
                    "-v", "threadtime",
                    "-d",
                    "-b", "main",
                    "-b", "events",
                    "-b", "system",
                    "*:V")

            val logcatProc: Process? = Runtime.getRuntime().exec(args)

            val inputStream = logcatProc?.inputStream

            if (inputStream != null)
                BufferedReader(InputStreamReader(inputStream), 1024).use {
                    reader ->
                    ret = reader.readLines().filter { it.length > 0 }.toList()
                }

        }
        catch (e: IOException) {

            DevLog.error(context, "LogcatProvider", "Error reading logs, $e, ${e.message}")
        }

        return ret
    }
}
