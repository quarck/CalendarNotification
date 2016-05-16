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

package com.github.quarck.calnotify.prefs

import com.github.quarck.calnotify.Consts

object PreferenceUtils {

    private const val MINUTES_IN_HOUR = 60
    private const val MULTIPLIER = 0x1000


    fun packTime(time: Pair<Int, Int>)
            = time.component1() * MINUTES_IN_HOUR + time.component2()

    fun unpackTime(tm: Int)
            = Pair(tm / MINUTES_IN_HOUR, tm % MINUTES_IN_HOUR)

    fun packQuietHours(enabled: Boolean, from: Pair<Int,Int>, to: Pair<Int, Int>)
            = (if (enabled) MULTIPLIER*MULTIPLIER else 0) or packTime(from) * MULTIPLIER + packTime(to)

    fun unpackQuietHoursIsEnabled(value: Int)
            = (value and (MULTIPLIER*MULTIPLIER)) != 0

    fun unpackQuietHoursFrom(value: Int)
            = unpackTime((value and (MULTIPLIER*MULTIPLIER-1)) / MULTIPLIER)

    fun unpackQuietHoursTo(value: Int)
            = unpackTime((value and (MULTIPLIER*MULTIPLIER-1)) % MULTIPLIER)

    internal fun parseSnoozePresets(value: String): LongArray? {
        var ret: LongArray? = null;

        try {
            ret = value
                    .split(",")
                    .map { it.trim() }
                    .filter { !it.isEmpty() }
                    .map {
                        str ->
                        var unit = str.takeLast(1)
                        var num = str.dropLast(1).toLong()
                        var seconds =
                                when (unit) {
                                    "m" -> num * Consts.MINUTE_IN_SECONDS;
                                    "h" -> num * Consts.HOUR_IN_SECONDS;
                                    "d" -> num * Consts.DAY_IN_SECONDS;
                                    else -> throw Exception("Unknown unit ${unit}")
                                }
                        seconds * 1000L
                    }
                    .toLongArray()
        } catch (ex: Exception) {
            ret = null;
        }

        return ret;
    }

    fun parsePattern(pattern: String): LongArray? {

        var ret: LongArray?

        try {
            ret = pattern
                    .split(",")
                    .map { it.trim() }
                    .filter { !it.isEmpty()}
                    .map { it.toLong() }
                    .toLongArray()

        } catch (ex: Exception) {
            ret = null
        }

        return ret
    }
}