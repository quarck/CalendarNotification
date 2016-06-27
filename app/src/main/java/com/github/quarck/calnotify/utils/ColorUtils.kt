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

package com.github.quarck.calnotify.utils

internal const val FULL_ALPHA = 0xff000000.toInt()

internal const val CALENDAR_COLOR_FADE_MIN = 1.17;
internal const val CALENDAR_COLOR_FADE_MED = 1.3;
internal const val CALENDAR_COLOR_FADE_MAX = 1.35;

internal const val CHANNELS_ARE_EQUAL_THRESHOLD = 0x10

internal const val RGB_TO_YUV_1_1 = 0.299
internal const val RGB_TO_YUV_1_2 = 0.587
internal const val RGB_TO_YUV_1_3 = 0.114
internal const val RGB_TO_YUV_2_1 = -0.14713
internal const val RGB_TO_YUV_2_2 = -0.28886
internal const val RGB_TO_YUV_2_3 = 0.436
internal const val RGB_TO_YUV_3_1 = 0.615
internal const val RGB_TO_YUV_3_2 = -0.51499
internal const val RGB_TO_YUV_3_3 = -0.10001

internal const val YUV_TO_RGB_1_1 = 1.0
internal const val YUV_TO_RGB_1_2 = 0
internal const val YUV_TO_RGB_1_3 = 1.13983
internal const val YUV_TO_RGB_2_1 = 1.0
internal const val YUV_TO_RGB_2_2 = -0.39465
internal const val YUV_TO_RGB_2_3 = -0.58060
internal const val YUV_TO_RGB_3_1 = 1.0
internal const val YUV_TO_RGB_3_2 = 2.03211
internal const val YUV_TO_RGB_3_3 = 0

internal const val Y_MIN = 16.0
internal const val Y_MAX = 235.0

data class RGB(val r: Int, val g: Int, val b: Int) {
    constructor(v: Int) : this((v.ushr(16)) and 0xff, (v.ushr(8)) and 0xff, (v.ushr(0)) and 0xff) { }
    fun toInt() = (FULL_ALPHA or (r shl 16) or (g shl 8) or (b shl 0))
}



fun Int.adjustCalendarColor(): Int {

    var (r, g, b) = RGB(this)

    val min = Math.min(r, Math.min(g, b))
    val max = Math.max(r, Math.max(g, b))

    if (max - min < CHANNELS_ARE_EQUAL_THRESHOLD) {
        r = (r / CALENDAR_COLOR_FADE_MED).toInt()
        g = (g / CALENDAR_COLOR_FADE_MED).toInt()
        b = (b / CALENDAR_COLOR_FADE_MED).toInt()
    } else {

        if (r > g && r > b) {
            //
            r = (r / CALENDAR_COLOR_FADE_MIN).toInt()
            if (g > b) {
                g = (g / CALENDAR_COLOR_FADE_MED).toInt()
                b = (b / CALENDAR_COLOR_FADE_MAX).toInt()
            } else {
                b = (b / CALENDAR_COLOR_FADE_MED).toInt()
                g = (g / CALENDAR_COLOR_FADE_MAX).toInt()
            }

        } else if ( g > r && g > b) {
            //
            g = (g / CALENDAR_COLOR_FADE_MIN).toInt()
            if (r > b) {
                r = (r / CALENDAR_COLOR_FADE_MED).toInt()
                b = (b / CALENDAR_COLOR_FADE_MAX).toInt()
            } else {
                b = (b / CALENDAR_COLOR_FADE_MED).toInt()
                r = (r / CALENDAR_COLOR_FADE_MAX).toInt()
            }
        } else {
            // b > r && b > g
            b = (b / CALENDAR_COLOR_FADE_MIN).toInt()
            if (r > g) {
                r = (r / CALENDAR_COLOR_FADE_MED).toInt()
                g = (g / CALENDAR_COLOR_FADE_MAX).toInt()
            } else {
                g = (g / CALENDAR_COLOR_FADE_MED).toInt()
                r = (r / CALENDAR_COLOR_FADE_MAX).toInt()
            }
        }
    }

    return RGB(r,g,b).toInt()
}


fun Int.scaleColorLuminosity(value: Float): Int {

    val (r, g, b) = RGB(this)

    var y = RGB_TO_YUV_1_1 * r + RGB_TO_YUV_1_2 * r + RGB_TO_YUV_1_3 * b
    val u = RGB_TO_YUV_2_1 * r + RGB_TO_YUV_2_2 * g + RGB_TO_YUV_2_3 * b
    val v = RGB_TO_YUV_3_1 * r + RGB_TO_YUV_3_2 * g + RGB_TO_YUV_3_3 * b

    y = Math.min(Y_MAX, Math.max(Y_MIN, y * value))

    var newR = (YUV_TO_RGB_1_1 * y + YUV_TO_RGB_1_2 * u + YUV_TO_RGB_1_3 * v).toInt()
    var newG = (YUV_TO_RGB_2_1 * y + YUV_TO_RGB_2_2 * u + YUV_TO_RGB_2_3 * v).toInt()
    var newB = (YUV_TO_RGB_3_1 * y + YUV_TO_RGB_3_2 * u + YUV_TO_RGB_3_3 * v).toInt()

    newR = Math.max(0, Math.min(newR, 255))
    newG = Math.max(0, Math.min(newG, 255))
    newB = Math.max(0, Math.min(newB, 255))

    return RGB(newR,newG,newB).toInt()
}