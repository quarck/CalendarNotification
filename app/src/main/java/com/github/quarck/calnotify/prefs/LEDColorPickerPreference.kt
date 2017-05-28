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

import android.content.Context
import android.content.res.TypedArray
import android.graphics.drawable.ColorDrawable
import android.preference.DialogPreference
import android.util.AttributeSet
import android.view.View
import android.view.ViewParent
import android.widget.Button
import android.widget.LinearLayout
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.logs.Logger
import com.github.quarck.calnotify.utils.find

class LEDColorPickerPreference(context: Context, attrs: AttributeSet) : DialogPreference(context, attrs) {

    internal var colorValue = 0

    internal var widgetView: View? = null

    private val colorButtonIds =
            intArrayOf(
                    R.id.button_color_picker_clr1,
                    R.id.button_color_picker_clr2,
                    R.id.button_color_picker_clr3,
                    R.id.button_color_picker_clr4,
                    R.id.button_color_picker_clr5,
                    R.id.button_color_picker_clr6,
                    R.id.button_color_picker_clr7,
                    R.id.button_color_picker_clr8)

    private var originalColors = mutableListOf<Pair<LinearLayout, ColorDrawable>>()
    private var primaryColor: ColorDrawable? = null

    init {
        dialogLayoutResource = R.layout.dialog_color_picker
        widgetLayoutResource = R.layout.dialog_color_picker_widget

        setPositiveButtonText(android.R.string.ok)
        setNegativeButtonText(android.R.string.cancel)

        dialogIcon = null
    }

    override fun onBindView(view: View) {
        super.onBindView(view)

        widgetView = view.find<View?>(R.id.dialog_color_picker_widget)

        updateWidgetView()
    }

    override fun onBindDialogView(view: View) {
        super.onBindDialogView(view)

        originalColors.clear()

        colorValue = this.getPersistedInt(0)
        for (buttonId in colorButtonIds) {

            val button = view.find<Button>(buttonId)
            button.setOnClickListener { v -> onClick(v) }

            val parent: ViewParent = button.parent

            if (parent is LinearLayout) {
                val background = parent.background

                if (background is ColorDrawable) {
                    originalColors.add(Pair(parent, background))

                    if (background.color == colorValue)
                        parent.background = getPrimaryColor(view)
                }
            }
        }

        updateWidgetView()
    }

    @Suppress("DEPRECATION")
    private fun getPrimaryColor(v: View): ColorDrawable {
        if (primaryColor == null)
            primaryColor = ColorDrawable(v.resources.getColor(R.color.primary))
        return primaryColor!!
    }

    fun onClick(v: View) {
        colorValue = Consts.DEFAULT_LED_COLOR

        var background = v.background
        if (background is ColorDrawable)
            colorValue = background.color

        for (hl in originalColors)
            hl.first.background = hl.second

        var parent: ViewParent = v.parent
        if (parent is LinearLayout)
            parent.background = getPrimaryColor(v)
    }

    override fun onDialogClosed(positiveResult: Boolean) {

        if (positiveResult) {
            persistInt(colorValue)
            updateWidgetView()
        }
    }

    fun updateWidgetView() {

        val wv = widgetView
        if (wv != null)
            wv.background = ColorDrawable(colorValue)
    }

    override fun onSetInitialValue(restorePersistedValue: Boolean, defaultValue: Any?) {

        if (restorePersistedValue) {
            // Restore existing state
            colorValue = this.getPersistedInt(0)

        }
        else if (defaultValue != null && defaultValue is Int) {
            // Set default state from the XML attribute
            colorValue = defaultValue
        }
    }

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any {
        return a.getInteger(index, 0)
    }

    companion object {
        var logger = Logger("LEDColorPickerPreference");
    }
}
