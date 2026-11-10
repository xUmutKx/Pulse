package com.umutk.pulse.extras.gamehub

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import java.io.File
import java.io.FileOutputStream

/**
 * A game's box in a console's case: the game's picture (its Steam cover, else its wide Steam picture cropped to the box) is drawn in the case's
 * window and the case's own frame (with its platform logo) is laid over it. Steam is the default: the Steam cover as it is.
 * The frames are from the steam-case-covers project (GPL-3.0); the windows are its measured positions (600 x 900 px).
 */
internal object CaseCover {
    val STYLES = listOf("steam", "ps5", "ps4", "ps3", "switch", "switch2", "xbox360", "xboxone", "xboxseries", "mix")
    /** The mix: the games are dealt evenly to these (a shuffled order, so it is not sorted by name); each keeps its box. */
    val MIX = listOf("steam", "ps5", "switch", "xboxseries")
    private val WINDOWS = mapOf(
        "ps3" to intArrayOf(13, 116, 586, 886), "ps4" to intArrayOf(16, 116, 584, 884), "ps5" to intArrayOf(16, 117, 584, 884),
        "switch" to intArrayOf(16, 16, 584, 884), "switch2" to intArrayOf(16, 165, 584, 884),
        "xbox360" to intArrayOf(16, 116, 584, 884), "xboxone" to intArrayOf(16, 116, 584, 884), "xboxseries" to intArrayOf(16, 16, 584, 884),
    )
    private fun sp(c: Context) = c.getSharedPreferences("gamehub", Context.MODE_PRIVATE)

    /** The chosen box style: "steam" (the default) or one of the console cases. */
    fun style(c: Context): String = sp(c).getString("boxStyle", "steam")?.takeIf { it in STYLES } ?: "steam"
    fun setStyle(c: Context, s: String) { sp(c).edit().putString("boxStyle", s).apply() }

    fun file(c: Context, pkg: String, style: String): File = File(c.filesDir, "gamehub/case_" + pkg.replace('.', '_') + "_$style.jpg")

    /** The box of [pkg] in the chosen style, or null for the Steam cover. In the mix each game has its own style (see [applyAll]). */
    fun fileFor(c: Context, pkg: String): File? {
        val st = style(c)
        val one = if (st == "mix") mixMap(c)[pkg] ?: return null else st
        if (one == "steam") return null
        return file(c, pkg, one)
    }

    /** The shelf group of [pkg] in the mix: its box's colour (the order of [MIX]); 0 for every game outside the mix. */
    fun groupOf(c: Context, pkg: String): Int = if (style(c) == "mix") MIX.indexOf(mixMap(c)[pkg] ?: "steam").coerceAtLeast(0) else 0

    private fun mixMap(c: Context): Map<String, String> =
        (sp(c).getString("boxMix", "") ?: "").split(';').mapNotNull { e -> e.split('=').takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()

    /** The box picture of [art] in [style], 600 x 900. [art] is cropped to fill the case's window. */
    fun compose(c: Context, art: Bitmap, style: String): Bitmap? {
        val win = WINDOWS[style] ?: return null
        val frame = c.assets.open("cases/$style.png").use { BitmapFactory.decodeStream(it) } ?: return null
        val out = Bitmap.createBitmap(600, 900, Bitmap.Config.ARGB_8888)
        val cv = Canvas(out)
        cv.drawColor(0xFF14151A.toInt())
        val x0 = win[0]; val y0 = win[1]; val ww = win[2] - win[0]; val wh = win[3] - win[1]
        // fill the window: the middle of the art, cropped to the window's proportions, drawn into the window
        val cropW = art.width.toFloat().coerceAtMost(art.height * ww / wh.toFloat())
        val cropH = cropW * wh / ww
        val left = (art.width - cropW) / 2f; val top = (art.height - cropH) / 2f
        val src = android.graphics.Rect(left.toInt(), top.toInt(), (left + cropW).toInt(), (top + cropH).toInt())
        cv.drawBitmap(art, src, RectF(x0.toFloat(), y0.toFloat(), (x0 + ww).toFloat(), (y0 + wh).toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
        cv.drawBitmap(frame, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        frame.recycle()
        return out
    }

    /** Makes the box of game [pkg] in [style] from its Steam cover (else its wide Steam picture). False when it has neither. */
    fun make(c: Context, pkg: String, style: String): Boolean {
        if (style == "steam") return false
        val cover = steamCoverFile(c, pkg).takeIf { it.isFile } ?: steamHeroFile(c, pkg).takeIf { it.isFile } ?: return false
        val art = BitmapFactory.decodeFile(cover.absolutePath) ?: return false
        val box = compose(c, art, style) ?: return false
        val f = file(c, pkg, style); f.parentFile?.mkdirs()
        FileOutputStream(f).use { box.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        box.recycle(); art.recycle()
        return true
    }

    /** Makes the box of every game that has a Steam picture, in the chosen style. */
    fun applyAll(c: Context, pkgs: List<String>, style: String): Int {
        if (style == "mix") {
            // shuffled by a hash of the package (the same order every time), then dealt round the MIX styles: an even share of each
            val order = pkgs.distinct().sortedBy { it.hashCode() }
            val map = order.mapIndexed { i, pkg -> pkg to MIX[i % MIX.size] }.toMap()
            sp(c).edit().putString("boxMix", map.entries.joinToString(";") { "${it.key}=${it.value}" }).apply()
            return map.count { (pkg, st) -> make(c, pkg, st) }
        }
        var n = 0
        for (pkg in pkgs) if (make(c, pkg, style)) n++
        return n
    }
}
