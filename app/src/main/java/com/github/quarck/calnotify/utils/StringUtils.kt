package com.github.quarck.calnotify.utils

fun String.toLongOrNull(): Long? {

    var ret: Long? = null

    try {
        ret = this.toLong()
    } catch (ex: Exception) {
        ret = null
    }

    return ret;
}

fun String.toIntOrNull(): Int? {

    var ret: Int? = null

    try {
        ret = this.toInt()
    } catch (ex: Exception) {
        ret = null
    }

    return ret;
}
