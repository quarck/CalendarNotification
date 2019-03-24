package com.github.quarck.calnotify.bluetooth

import android.content.Context
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.PersistentState
import com.github.quarck.calnotify.utils.PersistentStorageBase

class BTCarModeStorage(private val ctx: Context) : PersistentStorageBase(ctx, PREFS_NAME) {

    private var carModeTriggerDevicesRaw by StringProperty("", "A")

    var carModeTriggerDevices: List<String>
        get() = carModeTriggerDevicesRaw.split(',').toList()
        set(value) {
            carModeTriggerDevicesRaw = value.joinToString (",")
        }

    companion object {
        const val PREFS_NAME: String = "car_mode_state"
    }
}

val Context.btCarModeSettings: BTCarModeStorage
    get() = BTCarModeStorage(this)

