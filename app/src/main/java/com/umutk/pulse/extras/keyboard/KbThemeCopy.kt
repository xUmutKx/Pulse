package com.umutk.pulse.extras.keyboard

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri

/**
 * A keyboard theme read off a screenshot of another keyboard (as WM Keyboard's theme copier does, MIT; here by colours only, in Kotlin): the lower part of the
 * picture is taken as the keyboard; its most common colour is the background, the next one far enough from it the keys, a darker or lighter one the special keys,
 * the text the colour that stands out most on the keys, and the accent the most vivid colour found. They go into the Custom theme.
 */
object KbThemeCopy {
    class Colors(val bg: Int, val key: Int, val special: Int, val text: Int, val accent: Int)

    fun read(c: Context, uri: Uri): Colors? = runCatching {
        val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 }
        val bmp = c.contentResolver.openInputStream(uri)!!.use { android.graphics.BitmapFactory.decodeStream(it, null, opts) } ?: return@runCatching null
        read(bmp)
    }.getOrNull()

    fun read(b: Bitmap): Colors? {
        // the keyboard: the lowest 42 % of a screenshot (or the whole picture when it is wide, a crop of the keyboard alone)
        val top = if (b.height > b.width) (b.height * .58f).toInt() else 0
        val counts = HashMap<Int, Int>()
        val step = maxOf(1, (b.width * (b.height - top)) / 60000)
        var i = 0
        for (y in top until b.height) for (x in 0 until b.width) {
            if (i++ % step != 0) continue
            val p = b.getPixel(x, y)
            // colours are counted in steps of 16, so the shades of one fill fall together
            val q = (p and 0xF0F0F0) or 0xFF000000.toInt()
            counts[q] = (counts[q] ?: 0) + 1
        }
        if (counts.isEmpty()) return null
        val sorted = counts.entries.sortedByDescending { it.value }.map { it.key }
        fun dist(a: Int, c: Int): Int { val r = (a shr 16 and 255) - (c shr 16 and 255); val g = (a shr 8 and 255) - (c shr 8 and 255); val bl = (a and 255) - (c and 255); return r * r + g * g + bl * bl }
        fun lum(a: Int) = .299 * (a shr 16 and 255) + .587 * (a shr 8 and 255) + .114 * (a and 255)
        fun sat(a: Int): Float { val hsv = FloatArray(3); android.graphics.Color.colorToHSV(a, hsv); return hsv[1] * hsv[2] }
        val bg = sorted[0]
        val key = sorted.firstOrNull { dist(it, bg) > 600 } ?: if (lum(bg) < 128) bg + 0x202020 else bg - 0x181818
        val special = sorted.firstOrNull { dist(it, bg) > 600 && dist(it, key) > 500 && sat(it) < .35f } ?: key
        val text = sorted.take(40).maxByOrNull { kotlin.math.abs(lum(it) - lum(key)) }?.takeIf { kotlin.math.abs(lum(it) - lum(key)) > 90 }
            ?: if (lum(key) < 128) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
        val accent = sorted.take(60).filter { sat(it) > .45f }.maxByOrNull { sat(it) * (counts[it] ?: 1) } ?: 0xFF4FC3F7.toInt()
        return Colors(bg, key, special, text, accent)
    }
}
