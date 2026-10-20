package com.umutk.pulse.extras.music.skin

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/** Copies a sprite rectangle to skin position (dx, dy), where one skin pixel is [u] device pixels. Pixel-crisp, no gaps. */
fun DrawScope.blit(img: ImageBitmap?, sx: Int, sy: Int, sw: Int, sh: Int, dx: Float, dy: Float, u: Float, dw: Float = sw.toFloat(), dh: Float = sh.toFloat()) {
    if (img == null || sw <= 0 || sh <= 0 || sx < 0 || sy < 0 || sx + sw > img.width || sy + sh > img.height) return
    val x0 = (dx * u).roundToInt()
    val y0 = (dy * u).roundToInt()
    drawImage(
        img, IntOffset(sx, sy), IntSize(sw, sh), IntOffset(x0, y0),
        IntSize(((dx + dw) * u).roundToInt() - x0, ((dy + dh) * u).roundToInt() - y0),
        filterQuality = FilterQuality.None,
    )
}

/** Draws the whole [img] stretched over the 275x116 window, so a skin whose main.bmp is not exactly that size leaves no blank band. */
fun DrawScope.blitFit(img: ImageBitmap?, u: Float) {
    if (img == null) return
    drawImage(img, IntOffset.Zero, IntSize(img.width, img.height), IntOffset.Zero, IntSize((WINAMP_W * u).roundToInt(), (WINAMP_H * u).roundToInt()), filterQuality = FilterQuality.None)
}

private val TEXT_ROW0 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ\"@"
private val TEXT_ROW1 = "0123456789….:()-'!_+\\/[]^&%,=$#"
private val TEXT_ROW2 = "ÅÖÄ?* "

/** Cell (column, row) of [c] in text.bmp (5x6 cells); null for characters the font does not have. */
fun skinGlyph(c: Char): Pair<Int, Int>? {
    // text.bmp has no Turkish letters: use the closest plain letter instead of a blank
    val ch = when (c) { 'ş', 'Ş' -> 'S'; 'ğ', 'Ğ' -> 'G'; 'ı', 'İ' -> 'I'; 'ü', 'Ü' -> 'U'; 'ç', 'Ç' -> 'C'; else -> c.uppercaseChar() }
    TEXT_ROW0.indexOf(ch).takeIf { it >= 0 }?.let { return it to 0 }
    TEXT_ROW1.indexOf(ch).takeIf { it >= 0 }?.let { return it to 1 }
    TEXT_ROW2.indexOf(ch).takeIf { it >= 0 }?.let { return it to 2 }
    return null
}

/** Draws [s] with text.bmp at skin position (x, y). Unknown characters become spaces. */
fun DrawScope.skinText(skin: WinampSkin, s: String, x: Float, y: Float, u: Float) {
    var cx = x
    for (c in s) {
        val g = skinGlyph(c) ?: skinGlyph(' ') ?: (5 to 2)
        blit(skin.text, g.first * 5, g.second * 6, 5, 6, cx, y, u)
        cx += 5f
    }
}

/** Width of [s] in text.bmp pixels. */
fun skinTextWidth(s: String) = s.length * 5

/** Draws a digit (0-9, 10 = blank, 11 = minus) of numbers.bmp at skin position. */
fun DrawScope.skinDigit(skin: WinampSkin, d: Int, x: Float, y: Float, u: Float) {
    val sx = when (d) { in 0..9 -> d * 9; 11 -> 99; else -> 90 }
    val img = skin.numbers ?: return
    // plain numbers.bmp is 99 wide and has no minus cell; fall back to a blank
    if (sx + 9 <= img.width) blit(img, sx, 0, 9, 13, x, y, u)
}

/** A touch area in skin pixels. [vertical] zones give the fraction from the top. [drag] zones keep receiving the finger while it is down; [inset] is dead space at each end for fractions. */
class Zone(val id: Int, val x: Float, val y: Float, val w: Float, val h: Float, val drag: Boolean = false, val inset: Float = 0f, val padY: Float = 0f, val vertical: Boolean = false, val padX: Float = 0f)

/**
 * Gives a window drawn from sprites a spoken name and one accessibility action per control (TalkBack actions menu).
 */
fun Modifier.skinActions(label: String, actions: List<Pair<String, () -> Unit>>): Modifier = semantics {
    contentDescription = label
    customActions = actions.map { (name, act) -> CustomAccessibilityAction(name) { act(); true } }
}

/** Touch phases passed to the zone handler. */
const val PH_DOWN = 0
const val PH_MOVE = 1
const val PH_UP = 2   // finger lifted over the zone (a tap for buttons)
const val PH_CANCEL = 3

/**
 * One handler for all controls of a window. [sc] is device pixels per skin pixel. Touches outside every zone are left
 * unconsumed so a parent can scroll. [pressed] gets the id of the held zone, -1 when released.
 * The handler gets the id, the 0..1 fraction along the zone (inset removed) and the phase.
 */
@Composable
fun Modifier.skinZones(
    sc: Float,
    zones: List<Zone>,
    pressed: (Int) -> Unit,
    handler: (id: Int, frac: Float, phase: Int) -> Unit,
): Modifier {
    val p by rememberUpdatedState(pressed)
    val hd by rememberUpdatedState(handler)
    return pointerInput(zones, sc) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = true)
            fun hit(zn: Zone, px: Float, py: Float) =
                px / sc >= zn.x - zn.padX && px / sc <= zn.x + zn.w + zn.padX && py / sc >= zn.y - zn.padY && py / sc <= zn.y + zn.h + zn.padY
            val z = zones.firstOrNull { hit(it, down.position.x, down.position.y) } ?: return@awaitEachGesture
            fun frac(o: Offset): Float {
                val len = (if (z.vertical) z.h else z.w) - 2 * z.inset
                val at = (if (z.vertical) o.y / sc - z.y else o.x / sc - z.x) - z.inset
                return if (len <= 0f) 0f else (at / len).coerceIn(0f, 1f)
            }
            down.consume()
            p(z.id)
            hd(z.id, frac(down.position), PH_DOWN)
            var inside = true
            while (true) {
                val ev = awaitPointerEvent()
                val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                inside = hit(z, c.position.x, c.position.y)
                if (c.changedToUp()) {
                    c.consume()
                    hd(z.id, frac(c.position), if (inside || z.drag) PH_UP else PH_CANCEL)
                    break
                }
                if (!c.pressed) { hd(z.id, 0f, PH_CANCEL); break }
                if (z.drag) { c.consume(); hd(z.id, frac(c.position), PH_MOVE) }
            }
            p(-1)
        }
    }
}
