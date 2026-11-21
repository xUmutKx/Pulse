package com.umutk.pulse.features

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.quicksettings.TileService

/**
 * Closes the notification panel after one of Pulse's tiles was tapped, so the thing the tile did (a floating window, a keyboard, a cover) is not hidden behind the open panel. With the
 * accessibility layer on, it closes the shade itself; without it, the tile starts [CollapseActivity] (which does nothing and leaves), and starting an activity from a tile closes the panel.
 */
internal fun TileService.collapsePanel() {
    val a = CoverAccessibilityService.instance
    if (a != null) { a.collapseShade(); return }
    runCatching {
        val i = Intent(this, CollapseActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(PendingIntent.getActivity(this, 7998, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        else @Suppress("DEPRECATION") startActivityAndCollapse(i)
    }
}

/** An activity that is never shown: it starts and finishes at once. */
class CollapseActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); finish() }
}
