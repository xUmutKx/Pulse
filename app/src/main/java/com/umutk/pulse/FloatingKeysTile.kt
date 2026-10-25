package com.umutk.pulse

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Quick Settings tile: shows or hides the floating keyboard button (the overlay of the Toolkit's Floating keys). */
@androidx.annotation.RequiresApi(24)
class FloatingKeysTile : TileService() {
    override fun onStartListening() {
        qsTile?.apply {
            label = tr("Floating keys", "Yüzen tuşlar")
            state = if (FloatingKeysService.running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        // without the permission to draw over other apps the tile opens that setting instead
        if (!FloatingKeysService.toggle(this)) {
            val i = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (android.os.Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(android.app.PendingIntent.getActivity(this, 0, i, android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT))
            else @Suppress("DEPRECATION") startActivityAndCollapse(i)
            return
        }
        qsTile?.apply { state = if (state == Tile.STATE_ACTIVE) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE; updateTile() }
    }
}
