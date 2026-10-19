package com.umutk.pulse.extras.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.umutk.pulse.T

/**
 * Per-look icon families for the player chrome (transport, close, more, favourite...).
 * FILLED = Material solid, ROUNDED = puffy glossy-era, SHARP = square geometric, LINE = thin outline, PIXEL = Win95 pixel blocks.
 */
enum class IconStyle { FILLED, ROUNDED, SHARP, LINE, PIXEL }

object IconSet {
    // solid shapes (24 grid) and open strokes; PIXEL has its own block versions of the solid ones
    private val solid = mapOf(
        "play" to "M7 4v16l13-8z", "pause" to "M6 4h4v16H6zM14 4h4v16h-4z", "stop" to "M6 6h12v12H6z",
        "prev" to "M5 4h3v16H5zM20 4v16L9 12z", "next" to "M16 4h3v16h-3zM4 4l11 8L4 20z",
        "more" to "M12 7.2a2.2 2.2 0 1 0 0-4.4 2.2 2.2 0 0 0 0 4.4zM12 14.2a2.2 2.2 0 1 0 0-4.4 2.2 2.2 0 0 0 0 4.4zM12 21.2a2.2 2.2 0 1 0 0-4.4 2.2 2.2 0 0 0 0 4.4z",
        "note" to "M9 3v11.3A3.5 3.5 0 1 0 11 17.5V7h6V3z",
        "heart" to "M12 21l-1.5-1.3C5.4 15.1 2 12 2 8.5 2 5.4 4.4 3 7.5 3c1.7 0 3.4.8 4.5 2.1C13.1 3.8 14.8 3 16.5 3 19.6 3 22 5.4 22 8.5c0 3.5-3.4 6.6-8.5 11.2z",
        "star" to "M12 2l3.1 6.3 6.9 1-5 4.9 1.2 6.8L12 17.8 5.8 21l1.2-6.8-5-4.9 6.9-1z",
    )
    private val stroke = mapOf(
        "close" to "M6 6L18 18M18 6L6 18", "menu" to "M4 6h16M4 12h16M4 18h16", "check" to "M5 12.5l4.5 4.5L19 7.5",
        "shuffle" to "M4 7h3.5c5 0 5 10 10 10H20M4 17h3.5c1.6 0 2.6-.8 3.5-2M13 9c1-1.2 2.2-2 4.5-2H20M17.5 4.5L20 7l-2.5 2.5M17.5 14.5L20 17l-2.5 2.5",
        "repeat" to "M4 11V9a2 2 0 0 1 2-2h13M16 4l3 3-3 3M20 13v2a2 2 0 0 1-2 2H5M8 14l-3 3 3 3",
    )
    private val pixel = mapOf(
        "play" to "M8 4h2v16H8zM10 6h2v12h-2zM12 8h2v8h-2zM14 10h2v4h-2z",
        "pause" to "M6 4h4v16H6zM14 4h4v16h-4z", "stop" to "M6 6h12v12H6z",
        "prev" to "M5 4h2v16H5zM17 4h2v16h-2zM15 6h2v12h-2zM13 8h2v8h-2zM11 10h2v4h-2z",
        "next" to "M17 4h2v16h-2zM5 4h2v16H5zM7 6h2v12H7zM9 8h2v8H9zM11 10h2v4h-2z",
        "more" to "M10 3h4v4h-4zM10 10h4v4h-4zM10 17h4v4h-4z",
        "note" to "M14 4h6v4h-2V6h-4zM14 6h2v10h-2zM8 14h6v6H8z",
        "heart" to "M4 5h6v2H4zM14 5h6v2h-6zM2 7h20v4H2zM4 11h16v2H4zM6 13h12v2H6zM8 15h8v2H8zM10 17h4v2h-4z",
        "star" to "M11 2h2v4h-2zM9 6h6v2H9zM2 8h20v2H2zM4 10h16v2H4zM6 12h12v2H6zM5 14h6v2H5zM13 14h6v2h-6zM4 16h5v4H4zM15 16h5v4h-5z",
    )

    private val cache = HashMap<String, ImageVector>()

    fun vec(name: String, style: IconStyle): ImageVector = cache.getOrPut("$name/$style") { build(name, style) }

    private fun build(name: String, style: IconStyle): ImageVector {
        val b = ImageVector.Builder("i_$name", 24.dp, 24.dp, 24f, 24f)
        val ink = SolidColor(Color.Black)
        val st = stroke[name]
        if (st != null) {
            val w = when (style) { IconStyle.LINE -> 1.5f; IconStyle.ROUNDED -> 2.6f; IconStyle.PIXEL -> 2.5f; else -> 2.2f }
            val round = style == IconStyle.FILLED || style == IconStyle.ROUNDED || style == IconStyle.LINE
            b.addPath(addPathNodes(st), stroke = ink, strokeLineWidth = w,
                strokeLineCap = if (round) StrokeCap.Round else StrokeCap.Butt, strokeLineJoin = if (round) StrokeJoin.Round else StrokeJoin.Miter)
        } else {
            val d = (if (style == IconStyle.PIXEL) pixel[name] else null) ?: solid[name] ?: solid.getValue("note")
            when (style) {
                IconStyle.LINE -> b.addPath(addPathNodes(d), stroke = ink, strokeLineWidth = 1.5f, strokeLineJoin = StrokeJoin.Round)
                IconStyle.ROUNDED -> b.addPath(addPathNodes(d), fill = ink, stroke = ink, strokeLineWidth = 2f, strokeLineJoin = StrokeJoin.Round)
                else -> b.addPath(addPathNodes(d), fill = ink)
            }
        }
        return b.build()
    }

    /** The icon family of a look / skin id (app look ids, video skin ids, poweramp ids). */
    fun styleFor(id: String): IconStyle = when (id) {
        "win95", "classic", "winamp" -> IconStyle.PIXEL
        "xp", "win7", "wmp", "vlc", "coloros" -> IconStyle.ROUNDED
        "win10", "flat", "poweramp" -> IconStyle.SHARP
        "win11", "amoled", "minimal", "oneui" -> IconStyle.LINE
        else -> IconStyle.FILLED
    }

    /** Generated palettes cycle through four families, so neighbours differ. */
    fun styleForIndex(i: Int): IconStyle = listOf(IconStyle.FILLED, IconStyle.ROUNDED, IconStyle.SHARP, IconStyle.LINE)[i % 4]
}

/** A vector chrome icon in the given family (default: the current app look's). */
@Composable
fun TIcon(name: String, tint: Color, size: Dp = 24.dp, style: IconStyle = IconSet.styleFor(T.id), modifier: Modifier = Modifier) {
    Icon(IconSet.vec(name, style), null, modifier.size(size), tint)
}
