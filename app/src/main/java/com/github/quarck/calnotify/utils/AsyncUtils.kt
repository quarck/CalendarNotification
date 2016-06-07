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

// Anko is not building properly for me for some reason, so
// it was easier to just create a few lines of code below rather then
// investigating why it is not working

import android.os.AsyncTask
import android.os.PowerManager

class AsyncOperation(val fn: () -> Unit)
: AsyncTask<Void?, Void?, Void?>() {
    override fun doInBackground(vararg p0: Void?): Void? {
        fn()
        return null
    }
}

@Suppress("NOTHING_TO_INLINE")
inline fun background(noinline fn: () -> Unit) {
    AsyncOperation(fn).execute();
}

