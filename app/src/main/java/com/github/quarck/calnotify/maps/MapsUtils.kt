package com.github.quarck.calnotify.maps

import android.content.Context
import android.content.Intent
import android.net.Uri

object MapsUtils {
    fun getLocationIntent(location: String): Intent {
        var gmmIntentUri = Uri.parse("geo:0,0?q=$location");
        var mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri);
        //mapIntent.setPackage("com.google.android.apps.maps");
        return mapIntent
    }

    fun openLocation(context: Context, location: String) {
        context.startActivity(getLocationIntent(location))
    }
}