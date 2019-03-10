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

import android.app.Activity
import android.support.v4.app.Fragment
import android.view.View


fun <T : View?> View.find(id: Int): T? = findViewById(id) as T?

fun <T : View?> Activity.find(id: Int): T? = findViewById(id) as T?

fun <T : View?> Fragment.find(id: Int): T? = view?.findViewById(id) as T?


fun <T : View?> View.findOrThrow(id: Int): T = (findViewById(id) ?: throw Exception("Cant find resource id $id"))  as T

fun <T : View?> Activity.findOrThrow(id: Int): T = (findViewById(id) ?: throw Exception("Cant find resource id $id"))  as T

fun <T : View?> Fragment.findOrThrow(id: Int): T = (view?.findViewById(id) ?: throw Exception("Cant find resource id $id")) as T
