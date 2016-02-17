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

package com.github.quarck.calnotify


fun Int.adjustCalendarColor(): Int
{
	val colorFadeR = 1.2;
	val colorFadeG = 1.3;
	val colorFadeB = 1.2;

	var r = (this.ushr(16)) and 0xff
	var g = (this.ushr(8)) and 0xff
	var b = (this.ushr(0)) and 0xff

	r = (r / colorFadeR).toInt()
	g = (g / colorFadeG).toInt()
	b = (b / colorFadeB).toInt()

	return 0xff000000.toInt() or (r shl 16) or (g shl 8) or (b shl 0)
}
