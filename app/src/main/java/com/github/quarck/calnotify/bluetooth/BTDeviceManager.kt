package com.github.quarck.calnotify.bluetooth

import android.bluetooth.*
import android.bluetooth.BluetoothProfile.GATT
import android.bluetooth.BluetoothProfile.STATE_CONNECTED
import android.content.Context

data class BTDeviceSummary(val name: String, val address: String, val currentlyConnected: Boolean)


class BTDeviceManager(val ctx: Context){

    val adapter: BluetoothAdapter? by lazy { BluetoothAdapter.getDefaultAdapter() }

    val manager: BluetoothManager? by lazy { ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager }

    val storage: BTCarModeStorage by lazy { ctx.btCarModeSettings }

    val pairedDevices: List<BTDeviceSummary>?
        get() = adapter
                ?.bondedDevices
                ?.map { BTDeviceSummary(it.name, it.address, isDeviceConnected(it))}
                ?.toList()

    fun isDeviceConnected(device: BluetoothDevice) =
            manager?.getConnectionState(device, android.bluetooth.BluetoothProfile.GATT) ==
                    android.bluetooth.BluetoothProfile.STATE_CONNECTED

    fun isDeviceConnected(address: String): Boolean =
            adapter?.getRemoteDevice(address)?.let { isDeviceConnected(it) } ?: false

    fun isDeviceConnected(dev: BTDeviceSummary): Boolean =
            adapter?.getRemoteDevice(dev.address)?.let { isDeviceConnected(it) } ?: false

    fun hasCarModeTriggersConnected(): Boolean =
            storage.carModeTriggerDevices.any { isDeviceConnected(it) }

    //

}