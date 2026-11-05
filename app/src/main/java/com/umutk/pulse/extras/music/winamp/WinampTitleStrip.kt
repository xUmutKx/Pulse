package com.umutk.pulse.extras.music.winamp

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.umutk.pulse.extras.music.skin.WINAMP_W
import com.umutk.pulse.extras.music.skin.WinampSkin
import com.umutk.pulse.extras.music.skin.blit

/** Draws the playlist window's top bar (pledit.bmp: the corners, the gold double line tiled across, the plain bar in the middle) with [title] written on it in the pixel font. */
internal fun DrawScope.winampTitleBar(skin: WinampSkin, title: String, measurer: androidx.compose.ui.text.TextMeasurer, barColor: Color, textColor: Color, font: androidx.compose.ui.text.font.FontFamily) {
    val u = size.width / WINAMP_W
    val pl = skin.pledit
    // the tiles of gold line between the two corners
    var x = 25
    while (x < WINAMP_W - 25) { blit(pl, 127, 0, minOf(25, WINAMP_W - 25 - x), 20, x.toFloat(), 0f, u); x += 25 }
    blit(pl, 0, 0, 25, 20, 0f, 0f, u)
    blit(pl, 153, 0, 25, 20, (WINAMP_W - 25).toFloat(), 0f, u)
    // the plain bar under the title, then the title itself, centred
    val layout = measurer.measure(title, TextStyle(color = textColor, fontFamily = font, fontSize = (7.5f * u).toSp()), maxLines = 1)
    val tw = layout.size.width.toFloat(); val th = layout.size.height.toFloat()
    val padX = 6 * u
    val left = (size.width - tw) / 2f
    drawRect(barColor, Offset(left - padX, 2 * u), Size(tw + 2 * padX, 16 * u))
    drawText(layout, topLeft = Offset(left, (20 * u - th) / 2f))
}

/** The bar as a button or a title: the playlist window's own top bar with [title] on it. [onClick] runs on a tap anywhere on it (none: not touchable). */
@Composable
internal fun WinampTitleStrip(skin: WinampSkin, title: String, scaleDp: Float, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val measurer = rememberTextMeasurer()
    val font = remember { com.umutk.pulse.BundledFonts.first { it.first == "Press Start 2P" }.second }
    // the colours are read from the skin itself: the bar between the lines, and the light title text of the playlist
    val barColor = remember(skin) { runCatching { Color(skin.pledit!!.asAndroidBitmap().getPixel(30, 10)) }.getOrDefault(Color(0xFF2B2F4A)) }
    val textColor = remember(skin) { runCatching { skin.playlist.normal }.getOrDefault(Color(0xFFD8DCE8)).let { Color(0xFFD8DCE8) } }
    Box(modifier.size((WINAMP_W * scaleDp).dp, (20 * scaleDp).dp).then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)) {
        Canvas(Modifier.size((WINAMP_W * scaleDp).dp, (20 * scaleDp).dp)) { winampTitleBar(skin, title, measurer, barColor, textColor, font) }
    }
}
