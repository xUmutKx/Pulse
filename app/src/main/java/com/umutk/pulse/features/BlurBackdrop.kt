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

    /** Blur [src] in place of its size by halving and doubling (cheap, no RenderEffect), then lay [tint] over it. */
    fun blur(src: Bitmap, radiusDp: Float, tint: Int): Bitmap {
        var b = src
        val full = b.width to b.height
        val steps = (radiusDp / 6f).toInt().coerceIn(0, 5)
        repeat(steps) { b = Bitmap.createScaledBitmap(b, (b.width / 2).coerceAtLeast(4), (b.height / 2).coerceAtLeast(4), true) }
        repeat(steps) { b = Bitmap.createScaledBitmap(b, b.width * 2, b.height * 2, true) }
        if (steps > 0 && b.width != full.first) b = Bitmap.createScaledBitmap(b, full.first, full.second, true)
        val out = b.copy(Bitmap.Config.ARGB_8888, true)
        Canvas(out).drawColor(tint)
        return out
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
