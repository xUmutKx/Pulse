package com.umutk.pulse.extras.watchdog

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.umutk.pulse.extras.ambient.AmbientLast
import com.umutk.pulse.extras.music.MusicPlayerEngine
import com.umutk.pulse.extras.music.Track
import com.umutk.pulse.tr

/**
 * Quick Settings tile "last played": a tap toggles the last ambient mix; without one it toggles the music queue.
 * Works with the app closed: the tile's process starts and [AmbientLast.toggle] / the music engine start their foreground service.
 */
@androidx.annotation.RequiresApi(24)
class LastPlayedToggleTile : TileService() {

    override fun onStartListening() = paint()

    override fun onClick() { try { click0() } finally { com.umutk.pulse.features.TilePop.play(this) } }
    private fun click0() {
        if (AmbientLast.hasLast(this)) {
            AmbientLast.toggle(this)
            musicOn = false
        } else {
            val e = MusicPlayerEngine.get(this)
            val last = QuickTileContract.load(this)
            if (e.player.isPlaying) e.pause()
            else if (e.player.mediaItemCount > 0) e.play()
            else if (last?.kind == QuickTileContract.Kind.TRACK && last.ref.isNotEmpty()) e.playQueue(listOf(Track(last.ref, last.name)))
            musicOn = e.player.playWhenReady
        }
        paint()
    }

    private fun paint() {
        val ambient = AmbientLast.hasLast(this)
        val last = QuickTileContract.load(this)
        val on = if (ambient) AmbientLast.isPlaying() else musicOn
        qsTile?.apply {
            label = tr("Last played", "Son çalınan")
            state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            if (Build.VERSION.SDK_INT >= 29) subtitle = last?.name
            updateTile()
        }
    }

    private companion object {
        // the music engine only exists once used, so the tile keeps its own last known state
        @Volatile var musicOn = false
    }
}
