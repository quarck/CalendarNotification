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

fun Int.adjustCalendarColor(): Int {

    val channelsAreEqualThreshold = 0x10
    val colorFadeMin = 1.17;
    val colorFadeMed = 1.3;
    val colorFadeMax = 1.35;

    val fullAlpha = 0xff000000.toInt()

    var r = (this.ushr(16)) and 0xff
    var g = (this.ushr(8)) and 0xff
    var b = (this.ushr(0)) and 0xff

    val min = Math.min(r, Math.min(g, b))
    val max = Math.max(r, Math.max(g, b))

    if (max - min < channelsAreEqualThreshold) {
        r = (r / colorFadeMed).toInt()
        g = (g / colorFadeMed).toInt()
        b = (b / colorFadeMed).toInt()
    } else {

        if (r > g && r > b) {
            //
            r = (r / colorFadeMin).toInt()
            if (g > b) {
                g = (g / colorFadeMed).toInt()
                b = (b / colorFadeMax).toInt()
            } else {
                b = (b / colorFadeMed).toInt()
                g = (g / colorFadeMax).toInt()
            }

        } else if ( g > r && g > b) {
            //
            g = (g / colorFadeMin).toInt()
            if (r > b) {
                r = (r / colorFadeMed).toInt()
                b = (b / colorFadeMax).toInt()
            } else {
                b = (b / colorFadeMed).toInt()
                r = (r / colorFadeMax).toInt()
            }
        } else {
            // b > r && b > g
            b = (b / colorFadeMin).toInt()
            if (r > g) {
                r = (r / colorFadeMed).toInt()
                g = (g / colorFadeMax).toInt()
            } else {
                g = (g / colorFadeMed).toInt()
                r = (r / colorFadeMax).toInt()
            }
        }

    }

    return fullAlpha or (r shl 16) or (g shl 8) or (b shl 0)
}
