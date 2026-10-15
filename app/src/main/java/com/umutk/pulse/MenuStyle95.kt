package com.umutk.pulse

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Windows 95: a grey panel with the raised bevel, black text, square corners, tight rows, small system font. */
fun menuStyle95(w: PTheme) = MenuStyle(bg = Color(0xFFC0C0C0), ink = Color.Black, sub = Color.Black, shape = RectangleShape, edge = Modifier.bevel(true), rowV = 2.dp, rowH = 6.dp, font = 12.sp, iconSize = 16.dp, rowMin = 20.dp, hiBg = Color(0xFF000080), hiInk = Color.White)

/** Caption button glyph on the 16x14 grid, drawn in whole cells like the real bitmap: × (8x7, 2-px strokes) or the minimise bar. */
@androidx.compose.runtime.Composable
fun CapGlyph95(close: Boolean) {
    Canvas(Modifier.size(16.dp, 14.dp)) {
        val u = size.width / 16f
        fun px(x: Int, y: Int, w: Int = 1) = drawRect(Color.Black, Offset(x * u, y * u), Size(w * u, u))
        if (close) for (y in 0..6) { val i = if (y <= 3) y else 6 - y; if (i == 3) px(7, 3 + y, 2) else { px(4 + i, 3 + y, 2); px(10 - i, 3 + y, 2) } }
        else { px(4, 9, 6); px(4, 10, 6) }
    }
}

/** The small app icon at the left of the title bar: a tiny window with a navy caption. */
@androidx.compose.runtime.Composable
fun TitleIcon95() {
    Canvas(Modifier.size(14.dp)) {
        val u = size.width / 14f
        drawRect(Color.White, Offset(u, 2 * u), Size(12 * u, 10 * u))
        drawRect(Color(0xFF000080), Offset(u, 2 * u), Size(12 * u, 3 * u))
        drawRect(Color.Black, Offset(u, 2 * u), Size(12 * u, 10 * u), style = Stroke(u))
    }
}

/** Message box icon of this look: yellow warning triangle with "!", or a blue round "i"; hard black outline, no gradients. */
@androidx.compose.runtime.Composable
fun MsgIcon95(warn: Boolean) {
    Canvas(Modifier.size(32.dp)) {
        val s = size.width; val u = s / 32f
        if (warn) {
            val tri = Path().apply { moveTo(s / 2, 2f * u); lineTo(s - 1f * u, s - 3f * u); lineTo(1f * u, s - 3f * u); close() }
            drawPath(tri, Color(0xFFFFFF00)); drawPath(tri, Color.Black, style = Stroke(1.5f * u))
            drawRect(Color.Black, Offset(s / 2 - 1.5f * u, 11f * u), Size(3f * u, 9f * u))
            drawRect(Color.Black, Offset(s / 2 - 1.5f * u, 22f * u), Size(3f * u, 3f * u))
        } else {
            drawCircle(Color(0xFF0000FF), s / 2 - 1f * u); drawCircle(Color.Black, s / 2 - 1f * u, style = Stroke(1.5f * u))
            drawRect(Color.White, Offset(s / 2 - 1.5f * u, 13f * u), Size(3f * u, 11f * u))
            drawRect(Color.White, Offset(s / 2 - 1.5f * u, 7f * u), Size(3f * u, 3f * u))
        }
    }
}
