package com.umutk.pulse.extras.screenrec

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.umutk.pulse.tr

/** Quick Settings tile: a tap stops a running recording, otherwise [ScreenRecActivity] asks for consent and starts one. */
class ScreenRecTile : TileService() {
    override fun onStartListening() = paint()

    override fun onClick() {
        if (ScreenRecService.running) {
            ScreenRecService.stop(this)
            qsTile?.apply { state = Tile.STATE_INACTIVE; subtitle = tr("Off", "Kapalı"); updateTile() }
            return
        }
        // a tile cannot show the consent dialog itself, so the transparent activity does
        val i = Intent(this, ScreenRecActivity::class.java).putExtra("auto", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        else @Suppress("DEPRECATION") startActivityAndCollapse(i)
    }

    private fun paint() {
        val on = ScreenRecService.running
        qsTile?.apply {
            label = tr("Screen recorder", "Ekran kaydı")
            state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            subtitle = if (on) tr("Recording", "Kaydediyor") else tr("Off", "Kapalı")
            updateTile()
        }
    }
}
