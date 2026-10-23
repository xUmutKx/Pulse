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
object TilePop {
    private val jobs = WeakHashMap<TileService, Job>()
    private const val S = 96

    fun play(svc: TileService) {
        val t = svc.qsTile ?: return
        val own = t.icon ?: return
        val d = runCatching { own.loadDrawable(svc) }.getOrNull()?.constantState?.newDrawable()?.mutate() ?: return
        jobs[svc]?.cancel()
        jobs[svc] = CoroutineScope(Dispatchers.Main).launch {
            val scale = floatArrayOf(.45f, .7f, 1.18f, 1.08f, .98f, 1f)
            var last: Icon? = null
            scale.forEachIndexed { i, k ->
                val frame = Icon.createWithBitmap(frame(d, k, (i + 1f) / scale.size))
                t.icon = frame; t.updateTile(); last = frame
                delay(46)
            }
            // someone else (the tile's own paint) changed the picture meanwhile: leave theirs
            if (t.icon === last) { t.icon = own; t.updateTile() }
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
