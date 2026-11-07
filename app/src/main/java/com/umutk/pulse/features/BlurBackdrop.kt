package com.umutk.pulse.features

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import com.umutk.pulse.R

/**
 * Blurred wallpaper as a ready bitmap (reusable: lock screen, Win11 widgets/app "Blur" material).
 * Rendered once per (radius, tint, picture) and cached, so showing it costs nothing: no RenderEffect on every frame.
 */
object BlurBackdrop {
    private var key = ""
    private var bmp: Bitmap? = null

    /** [radiusDp] 0..40 blur strength, [tint] ARGB colour laid over the picture (alpha = tint strength), [res] source picture. */
    @Synchronized
    fun blurredWallpaper(c: Context, radiusDp: Float, tint: Int, res: Int = R.drawable.splash_win11_wallpaper): Bitmap {
        val k = "$res/${radiusDp.toInt()}/$tint"
        bmp?.let { if (k == key) return it }
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(c.resources, res, o)
        o.inSampleSize = Integer.highestOneBit((o.outWidth / 540).coerceAtLeast(1)); o.inJustDecodeBounds = false
        val out = blur(BitmapFactory.decodeResource(c.resources, res, o), radiusDp, tint)
        bmp = out; key = k
        return out
    }

    /**
     * A real blur: the picture is made small, blurred with three passes of a box blur (horizontal and vertical), and stretched back to full size with a
     * smooth filter, so no block edges show. The old halving and doubling made large square pixels.
     */
    fun blur(src: Bitmap, radiusDp: Float, tint: Int): Bitmap {
        val targetW = 270
        val sw = targetW.coerceAtMost(src.width).coerceAtLeast(1)
        val sh = (src.height.toFloat() * sw / src.width).toInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(src, sw, sh, true)
        val px = IntArray(sw * sh)
        small.getPixels(px, 0, sw, 0, 0, sw, sh)
        if (radiusDp > 0f) {
            val r = (radiusDp / 40f * 8f + 1f).toInt().coerceIn(1, 12)
            val tmp = IntArray(px.size)
            repeat(3) {
                boxPass(px, tmp, sw, sh, r, horizontal = true)
                boxPass(tmp, px, sw, sh, r, horizontal = false)
            }
        }
        val blurredSmall = Bitmap.createBitmap(px, sw, sh, Bitmap.Config.ARGB_8888)
        val out = Bitmap.createScaledBitmap(blurredSmall, src.width, src.height, true).copy(Bitmap.Config.ARGB_8888, true)
        Canvas(out).drawColor(tint)
        return out
    }

    /** One box blur pass along a row (horizontal) or a column, each channel averaged over 2r+1 pixels, the edges repeated. */
    private fun boxPass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val len = if (horizontal) w else h
        val d = 2 * r + 1
        for (line in 0 until lines) {
            fun at(i: Int): Int {
                val k = i.coerceIn(0, len - 1)
                return if (horizontal) src[line * w + k] else src[k * w + line]
            }
            var sa = 0; var sr = 0; var sg = 0; var sb = 0
            for (i in -r..r) { val c = at(i); sa += c ushr 24; sr += (c shr 16) and 255; sg += (c shr 8) and 255; sb += c and 255 }
            for (i in 0 until len) {
                val v = ((sa / d) shl 24) or ((sr / d) shl 16) or ((sg / d) shl 8) or (sb / d)
                if (horizontal) dst[line * w + i] = v else dst[i * w + line] = v
                val add = at(i + r + 1); val rem = at(i - r)
                sa += (add ushr 24) - (rem ushr 24)
                sr += ((add shr 16) and 255) - ((rem shr 16) and 255)
                sg += ((add shr 8) and 255) - ((rem shr 8) and 255)
                sb += (add and 255) - (rem and 255)
            }
        }
    }
}

/** A look's lock-screen wallpaper (the user's pick, else the stock one it shows in Appearance) decoded once at <= 1280 px and cached. */
object LockWall {
    private val cache = HashMap<String, Bitmap>()

    /** Changes when the picked wallpaper of [id] changes: part of the service's rebuild signature. */
    fun sig(c: Context, id: String): String {
        val p = c.getSharedPreferences("p", Context.MODE_PRIVATE)
        return "${p.getString("wall_$id", null)}/${p.getInt("wallsel_$id", 0)}"
    }

    private fun stock(c: Context, id: String): Int? {
        if (id == "win11") return R.drawable.splash_win11_wallpaper
        val l = wallsFor(id)
        return l.getOrNull(c.getSharedPreferences("p", Context.MODE_PRIVATE).getInt("wallsel_$id", 0)) ?: l.firstOrNull()
    }

    @Synchronized
    fun get(c: Context, id: String): Bitmap? {
        val uri = c.getSharedPreferences("p", Context.MODE_PRIVATE).getString("wall_$id", null)
        val res = stock(c, id)
        val k = uri ?: res?.toString() ?: return null
        cache[k]?.let { return it }
        fun open() = if (uri != null) c.contentResolver.openInputStream(android.net.Uri.parse(uri)) else c.resources.openRawResource(res!!)
        val b = runCatching {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            open()?.use { BitmapFactory.decodeStream(it, null, o) }
            var s = 1
            while (maxOf(o.outWidth, o.outHeight) / s > 1280) s *= 2
            val d = BitmapFactory.Options().apply { inSampleSize = s; inPreferredConfig = Bitmap.Config.RGB_565 }
            open()?.use { BitmapFactory.decodeStream(it, null, d) }
        }.getOrNull() ?: return null
        if (cache.size > 6) cache.clear()
        cache[k] = b
        return b
    }

    private val blurred = HashMap<String, Bitmap>()

    /** The wallpaper of [id] blurred and tinted, rendered once per setting. */
    @Synchronized
    fun blurred(c: Context, id: String, radiusDp: Float, tint: Int): Bitmap? {
        val src = get(c, id) ?: return null
        val k = "$id/${sig(c, id)}/${radiusDp.toInt()}/$tint"
        blurred[k]?.let { return it }
        if (blurred.size > 3) blurred.clear()
        return BlurBackdrop.blur(src, radiusDp, tint).also { blurred[k] = it }
    }
}
