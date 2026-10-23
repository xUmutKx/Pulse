package com.umutk.pulse.extras.localsend

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.umutk.pulse.ExtraToolActivity
import com.umutk.pulse.tr

/** Quick Settings tile: opens LocalSend (the nearby devices, ready to send). */
@androidx.annotation.RequiresApi(24)
class LocalSendTile : TileService() {
    override fun onStartListening() {
        qsTile?.apply {
            label = "LocalSend"
            state = Tile.STATE_INACTIVE
            subtitle = tr("Send to nearby devices", "Yakındaki cihazlara gönder")
            updateTile()
        }
    }

    override fun onClick() { try { click0() } finally { com.umutk.pulse.features.TilePop.play(this) } }
    private fun click0() {
        val i = Intent(this, ExtraToolActivity::class.java).putExtra("route", "localsend").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        else @Suppress("DEPRECATION") startActivityAndCollapse(i)
    }
}
