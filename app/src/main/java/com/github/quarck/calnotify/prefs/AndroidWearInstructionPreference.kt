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
import android.preference.DialogPreference
import android.util.AttributeSet
import android.view.View
import com.github.quarck.calnotify.R

class AndroidWearInstructionPreference(internal var context: Context, attrs: AttributeSet) : DialogPreference(context, attrs) {

    init {
        dialogLayoutResource = R.layout.dialog_android_wear_instruction;

        setPositiveButtonText(android.R.string.ok)

        setNegativeButtonText(null)

//        setNegativeButtonText(android.R.string.cancel)

        dialogIcon = null
    }

    override fun onBindDialogView(view: View) {
        super.onBindDialogView(view)
    }

    override fun onDialogClosed(positiveResult: Boolean) {
    }

    override fun onSetInitialValue(restorePersistedValue: Boolean, defaultValue: Any?) {
    }

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any {
        return a.getString(index)
    }
}
