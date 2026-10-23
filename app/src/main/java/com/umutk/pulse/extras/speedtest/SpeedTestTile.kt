package com.umutk.pulse.extras.speedtest

import com.umutk.pulse.subtitleCompat

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.umutk.pulse.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The speed test in the quick panel: a tap starts it, the tile lights up and its ring turns while it measures; when the panel is open wide the result sits under the name
 * (download, upload, ping). Another tap while it runs stops it.
 */
@androidx.annotation.RequiresApi(24)
class SpeedTestTile : TileService() {
    private var scope: CoroutineScope? = null
    private var spin: Job? = null

    override fun onStartListening() {
        val sc = CoroutineScope(SupervisorJob() + Dispatchers.Main).also { scope = it }
        sc.launch { SpeedTest.state.collect { paint(it) } }
    }

    override fun onStopListening() { spin?.cancel(); scope?.cancel(); scope = null }

    override fun onClick() {
        if (SpeedTest.running) SpeedTest.cancel() else SpeedTest.start(this)
    }

    private fun paint(s: SpeedLive) {
        val t = qsTile ?: return
        val busy = s.phase == "ping" || s.phase == "down" || s.phase == "up"
        t.label = "Speed test"
        if (busy) {
            t.state = Tile.STATE_ACTIVE
            t.subtitleCompat = when (s.phase) { "ping" -> "Ping…"; "down" -> "↓ %.1f".format(s.value); else -> "↑ %.1f".format(s.value) }
            if (spin?.isActive != true) spin = scope?.launch {
                var a = 0f
                while (true) { t.icon = Icon.createWithBitmap(ring(a)); t.updateTile(); a = (a + 40f) % 360f; delay(120) }
            }
        } else {
            spin?.cancel()
            t.icon = Icon.createWithResource(this, R.drawable.ic_qs_speed)
            val r = if (s.phase == "done") s else SpeedResult.last(this)?.let { SpeedLive("done", it.down, it.pingMs, it.jitterMs, it.down, it.up) }
            t.state = Tile.STATE_INACTIVE
            t.subtitleCompat = if (s.phase == "error") "—" else r?.let { "↓ %.0f  ↑ %.0f  %.0f ms".format(it.down, it.up, it.ping) }
        }
        t.updateTile()
    }

    /** A ring with a bright arc at angle [a]: one frame of the spinning tile icon. */
    private fun ring(a: Float): Bitmap {
        val bmp = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 9f; strokeCap = Paint.Cap.ROUND }
        val r = RectF(12f, 12f, 84f, 84f)
        p.color = Color.argb(70, 255, 255, 255); c.drawArc(r, 0f, 360f, false, p)
        p.color = Color.WHITE; c.drawArc(r, a, 100f, false, p)
        return bmp
    }
}
