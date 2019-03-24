//
//   Calendar Notifications Plus
//   Copyright (C) 2019 Sergey Parshin (s.parshin.sc@gmail.com)
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

import android.bluetooth.BluetoothManager
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.StaggeredGridLayoutManager
import android.support.v7.widget.Toolbar
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.bluetooth.BTDeviceManager
import com.github.quarck.calnotify.bluetooth.BTDeviceSummary
import com.github.quarck.calnotify.calendar.CalendarProvider
import com.github.quarck.calnotify.calendar.CalendarRecord
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.permissions.PermissionsManager
import com.github.quarck.calnotify.utils.background
import com.github.quarck.calnotify.utils.find
import com.github.quarck.calnotify.utils.findOrThrow


class BlueboothDeviceListEntry(
        var isHandled: Boolean,
        val dev: BTDeviceSummary
)


class BlueboothDeviceListAdapter(val context: Context, var entries: Array<BlueboothDeviceListEntry>)
    : RecyclerView.Adapter<BlueboothDeviceListAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.bluetooth_device_view, parent, false)
        return ViewHolder(view)
    }

    inner class ViewHolder(itemView: View)
        : RecyclerView.ViewHolder(itemView) {

        var entry: BlueboothDeviceListEntry? = null
        var view: LinearLayout
        var deviceName: CheckBox
        var deviceAddr: TextView

        init {
            view = itemView.findOrThrow<LinearLayout>(R.id.linearLayoutBluetoothDeviceListEntry)

            deviceAddr = view.findOrThrow<TextView>(R.id.textViewDeviceMac)
            deviceName = view.findOrThrow<CheckBox>(R.id.checkBoxBTDeviceSelection)

            deviceName.setOnClickListener {
                view ->
                val action = onListHandledUpdated
                entry?.isHandled = deviceName.isChecked
                action?.invoke(view, entries.filter { it.isHandled }.map { it.dev.address }.toList() )
            }
        }
    }

    var onListHandledUpdated: ((View, List<String>) -> Unit)? = null

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {

        if (position >= 0 && position < entries.size) {

            val entry = entries[position]

            holder.entry = entry

            holder.deviceName.text = entry.dev.name //  + " " + entry.dev.currentlyConnected
            holder.deviceAddr.text = entry.dev.address
            holder.deviceName.isChecked = entry.isHandled
        }
    }

    override fun getItemCount(): Int {
        return entries.size
    }
}


class CarModeActivity : AppCompatActivity() {

    private lateinit var adapter: BlueboothDeviceListAdapter
    private lateinit var staggeredLayoutManager: StaggeredGridLayoutManager
    private lateinit var recyclerView: RecyclerView
    private lateinit var noDevicesText: TextView

    private lateinit var settings: Settings
    private lateinit var bluetoothManager: BTDeviceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        DevLog.debug(LOG_TAG, "onCreate")

        setContentView(R.layout.activity_car_mode)
        setSupportActionBar(find<Toolbar?>(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        settings = Settings(this)

        adapter = BlueboothDeviceListAdapter(this, arrayOf<BlueboothDeviceListEntry>())

        bluetoothManager = BTDeviceManager(this)

        adapter.onListHandledUpdated = {
            _, newList ->
            DevLog.info(LOG_TAG, "New triggers: ${newList.joinToString { ", " }}")
            bluetoothManager.storage.carModeTriggerDevices = newList
        }

        staggeredLayoutManager = StaggeredGridLayoutManager(1, StaggeredGridLayoutManager.VERTICAL)
        recyclerView = findOrThrow<RecyclerView>(R.id.list_bt_devices)
        recyclerView.layoutManager = staggeredLayoutManager;
        recyclerView.adapter = adapter;

        noDevicesText = findOrThrow<TextView>(R.id.no_devices_text)

    }

    override fun onResume() {
        super.onResume()

        if (!PermissionsManager.hasAccessCoarseLocation(this)) {
            PermissionsManager.requestLocationPermissions(this)
        }

        background {

            val triggers = bluetoothManager.storage.carModeTriggerDevices

            DevLog.info(LOG_TAG, "Known triggers: ${triggers.joinToString { ", " }}")

            val triggersHash = triggers.toHashSet()
            val devices = bluetoothManager.pairedDevices?.map { BlueboothDeviceListEntry(triggersHash.contains(it.address), it)}?.toList()

            runOnUiThread {
                // update activity finally

                if (devices != null) {
                    noDevicesText.visibility = if (devices.isNotEmpty()) View.GONE else View.VISIBLE

                    adapter.entries = devices.toTypedArray()
                    adapter.notifyDataSetChanged()
                } else {
                    noDevicesText.visibility = View.VISIBLE
                }
            }
        }
    }

    companion object {
        private const val LOG_TAG = "CarModeActivity"
    }
}