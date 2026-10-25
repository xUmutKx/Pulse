package com.umutk.pulse.extras.keyboard

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface

/**
 * Emoji mix: two emojis drawn into one picture with the phone's own emoji font (so with an iOS-style emoji font installed the picture is iOS-style, too).
 * The styles are drawn here, nothing is fetched and no artwork of another app is used.
 */
object EmojiMix {
    const val styles = 4

    fun name(i: Int) = when (i) { 0 -> "Corner"; 1 -> "Halves"; 2 -> "Hat"; else -> "Side by side" }

    fun render(a: String, b: String, style: Int, size: Int = 512): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        fun draw(e: String, cx: Float, cy: Float, px: Float, clip: Path? = null) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = px; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT }
            // the glyph is centred on its real bounds so a tall or wide emoji does not drift
            val r = android.graphics.Rect(); p.getTextBounds(e, 0, e.length, r)
            val x = cx; val y = cy - r.exactCenterY()
            if (clip != null) { c.save(); c.clipPath(clip); c.drawText(e, x, y, p); c.restore() } else c.drawText(e, x, y, p)
        }
        val s = size.toFloat()
        when (style) {
            // the first big, the second small at the lower right
            0 -> { draw(a, s * .46f, s * .46f, s * .78f); draw(b, s * .74f, s * .74f, s * .44f) }
            // the left half of the first beside the right half of the second: one face made of two
            1 -> {
                val left = Path().apply { addRect(0f, 0f, s / 2f, s, Path.Direction.CW) }
                val right = Path().apply { addRect(s / 2f, 0f, s, s, Path.Direction.CW) }
                draw(a, s / 2f, s / 2f, s * .86f, left); draw(b, s / 2f, s / 2f, s * .86f, right)
            }
            // the second sits on top of the first, like a hat
            2 -> { draw(a, s / 2f, s * .58f, s * .72f); draw(b, s / 2f, s * .2f, s * .4f) }
            // side by side, slightly overlapping
            else -> { draw(a, s * .32f, s / 2f, s * .62f); draw(b, s * .68f, s / 2f, s * .62f) }
        }
        return bmp
    }
}
