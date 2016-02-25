package com.github.quarck.calnotify.Utils

import android.app.Activity
import android.view.View


fun <T> View.find(id: Int) =  findViewById(id) as T
fun <T> Activity.find(id: Int) =  findViewById(id) as T

