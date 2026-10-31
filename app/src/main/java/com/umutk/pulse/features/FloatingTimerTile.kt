package com.umutk.pulse.features

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.umutk.pulse.tr

/**
 * Quick Settings tile: shows or hides the floating timer (the same window as the Toolkit's Floating timer). Without the permission
 * to draw over other apps the tap opens that setting instead.
 */
@androidx.annotation.RequiresApi(24)
class FloatingTimerTile : TileService() {
    override fun onStartListening() = paint()

    override fun onClick() {
        if (FloatingTimer.showing) FloatingTimer.hide(this)
        else if (!FloatingTimer.show(this)) {
            val i = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (android.os.Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            else @Suppress("DEPRECATION") startActivityAndCollapse(i)
            return
        }
        paint()
    }

    private fun paint() {
        qsTile?.apply {
            label = tr("Floating timer", "Yüzen sayaç")
            state = if (FloatingTimer.showing) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            updateTile()
        }
    }
}
