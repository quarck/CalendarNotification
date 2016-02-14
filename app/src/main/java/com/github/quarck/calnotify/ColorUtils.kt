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
