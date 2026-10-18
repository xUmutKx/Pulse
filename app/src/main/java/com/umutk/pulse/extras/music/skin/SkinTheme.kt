package com.umutk.pulse.extras.music.skin

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.umutk.pulse.PTheme
import com.umutk.pulse.bevel

enum class SkinBackground { BLACK, ALBUM_BLUR, SOLID }

/** What a skin screen draws with, taken from the current look (Material You, Win 7/XP/95, AMOLED, ...). */
data class SkinTokens(
    val bg: Color,
    val panel: Color,
    val text: Color,
    val sub: Color,
    val accent: Color,
    val line: Color,
    val radius: Dp,
    val bevel: Boolean,
    val tabs: Boolean,
)

/** Pass the resolved look (Look.current) so Material You wallpaper colours arrive already applied. */
fun skinTokensFor(t: PTheme, accent: Color = t.accent): SkinTokens =
    SkinTokens(t.bg, t.panel, t.text, t.sub, accent, t.line, t.radius, t.bevel, t.tabs)

/** Look of a skin screen: its tokens, the Poweramp background mode and an optional Winamp skin. */
data class SkinLook(
    val tokens: SkinTokens,
    val background: SkinBackground = SkinBackground.BLACK,
    val solid: Color = Color.Black,
    val winamp: WinampSkin = DefaultWinampSkin,
)

/** Win 95 raised edge, Win 7/XP thin border, flat for the rest. */
fun Modifier.skinFrame(tk: SkinTokens): Modifier =
    if (tk.bevel) bevel(raised = true) else if (tk.tabs) border(1.dp, tk.line) else this

/** Transport glyph drawn with shapes, so every look gets the same controls without icon fonts. */
fun DrawScope.transportGlyph(t: Transport, c: Color) {
    val w = size.width
    val h = size.height
    fun tri(x0: Float, x1: Float) = drawPath(Path().apply {
        moveTo(x0, h * .25f); lineTo(x0, h * .75f); lineTo(x1, h * .5f); close()
    }, c)
    fun bar(x: Float) = drawRect(c, Offset(x, h * .25f), Size(w * .1f, h * .5f))
    when (t) {
        Transport.PREV -> { bar(w * .2f); tri(w * .8f, w * .3f) }
        Transport.PLAY -> tri(w * .3f, w * .75f)
        Transport.PAUSE -> { bar(w * .3f); bar(w * .6f) }
        Transport.STOP -> drawRect(c, Offset(w * .3f, h * .25f), Size(w * .4f, h * .5f))
        Transport.NEXT -> { tri(w * .2f, w * .7f); bar(w * .8f) }
        Transport.EJECT -> {
            drawPath(Path().apply { moveTo(w * .2f, h * .6f); lineTo(w * .5f, h * .3f); lineTo(w * .8f, h * .6f); close() }, c)
            drawRect(c, Offset(w * .2f, h * .7f), Size(w * .6f, h * .1f))
        }
    }
}

/** Places a child at a skin-pixel position, scaled by [s]. */
fun Modifier.skinAt(x: Int, y: Int, s: Int): Modifier = offset((x * s).dp, (y * s).dp)

/** Same, for a fractional scale (dp per skin pixel). */
fun Modifier.skinAt(x: Int, y: Int, s: Float): Modifier = offset((x * s).dp, (y * s).dp)
