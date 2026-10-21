package com.umutk.pulse.extras.screenrec

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.umutk.pulse.tr

/**
 * Quick Settings tile for both: a tap takes a screenshot (or stops a running recording), a long press opens [ScreenRecActivity],
 * which asks for consent and starts a video recording.
 */
class ScreenRecTile : TileService() {
    override fun onStartListening() = paint()

    override fun onClick() {
        if (ScreenRecService.running) {
            ScreenRecService.stop(this)
            qsTile?.apply { state = Tile.STATE_INACTIVE; subtitle = tr("Off", "Kapalı"); updateTile() }
            return
        }
        // the shade must be gone before the picture is taken, so a transparent activity waits a moment and does it
        val i = Intent(this, ScreenRecActivity::class.java).putExtra("shot", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        else @Suppress("DEPRECATION") startActivityAndCollapse(i)
    }

    private fun paint() {
        val on = ScreenRecService.running
        qsTile?.apply {
            label = tr("Screenshot / record", "Ekran görüntüsü / kayıt")
            state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            subtitle = if (on) tr("Recording", "Kaydediyor") else tr("Tap: picture", "Dokun: görüntü")
            updateTile()
        }
    }
}
