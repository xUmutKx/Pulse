package com.umutk.pulse.features

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Icon
import android.service.quicksettings.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.WeakHashMap

/**
 * The little animation of a quick panel tile when it is switched: its picture opens from the middle and pops (as the system's own tiles do). Quick settings
 * cannot play a drawable animation for an app's tile, so a few frames of the picture are put on the tile one after the other; the tile's own picture comes back.
 */
@androidx.annotation.RequiresApi(24)
object TilePop {
    private val jobs = WeakHashMap<TileService, Job>()
    private const val S = 96

    // the tile's real picture, and the last frame this put on it: a second tap while the first animation still runs must not take a frame for the picture
    private val origin = WeakHashMap<TileService, Icon>()
    private val shown = WeakHashMap<TileService, Icon>()

    fun play(svc: TileService) {
        val t = svc.qsTile ?: return
        val cur = t.icon ?: return
        val own = if (cur === shown[svc] && origin[svc] != null) origin[svc]!! else cur
        origin[svc] = own
        val d = runCatching { own.loadDrawable(svc) }.getOrNull()?.constantState?.newDrawable()?.mutate() ?: return
        jobs[svc]?.cancel()
        jobs[svc] = CoroutineScope(Dispatchers.Main).launch {
            val scale = floatArrayOf(.45f, .7f, 1.18f, 1.08f, .98f, 1f)
            var last: Icon? = null
            scale.forEachIndexed { i, k ->
                val frame = Icon.createWithBitmap(frame(d, k, (i + 1f) / scale.size))
                // the tile painted its own picture meanwhile (a state change): that one stays, this animation ends
                if (i > 0 && t.icon !== last) return@launch
                t.icon = frame; t.updateTile(); last = frame; shown[svc] = frame
                delay(46)
            }
            // someone else (the tile's own paint) changed the picture meanwhile: leave theirs
            if (t.icon === last) { t.icon = own; t.updateTile() }
            shown.remove(svc)
        }
    }

    private fun frame(d: android.graphics.drawable.Drawable, k: Float, open: Float): Bitmap {
        val bmp = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val half = S / 2f * open.coerceIn(0f, 1f)
        c.save()
        c.clipRect(S / 2f - half, 0f, S / 2f + half, S.toFloat())
        c.scale(k, k, S / 2f, S / 2f)
        d.setBounds(0, 0, S, S)
        d.draw(c)
        c.restore()
        return bmp
    }
}
