package com.umutk.pulse

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Windows 7: a pale grey Aero menu with a soft edge and slightly rounded corners. */
fun menuStyle7(w: PTheme) = MenuStyle(bg = Color(0xFFF0F0F0), ink = Color.Black, sub = Color(0xFF3B5A82), shape = RoundedCornerShape(3.dp), border = BorderStroke(1.dp, Color(0xFF979797)),
    edge = Modifier.padding(1.dp).border(1.dp, Color.White, RoundedCornerShape(2.dp)), rowV = 3.dp, font = 12.sp, iconSize = 16.dp, rowMin = 24.dp, hiBg = Color(0xFFCFE3F8), hiInk = Color.Black)

/** Keys the Win7 icon pack lacks, mapped to the nearest one it has (PIcon then tries the XP pack before the Fluent fallback). */
val ICON7_ALIAS = mapOf("power" to "close", "speed" to "performance", "bookmark" to "heart", "share" to "download", "zip" to "folder")

/** Message box icon of this look: the Aero glossy blue "i" ball, or the yellow warning triangle with a bevelled edge. */
@androidx.compose.runtime.Composable
fun MsgIcon7(warn: Boolean) {
    androidx.compose.foundation.Canvas(Modifier.size(32.dp)) {
        val s = size.width; val bar = s * .12f
        if (warn) {
            val tri = Path().apply { moveTo(s / 2, s * .04f); lineTo(s * .98f, s * .92f); lineTo(s * .02f, s * .92f); close() }
            drawPath(tri, Brush.verticalGradient(listOf(Color(0xFFFFE680), Color(0xFFF2B200), Color(0xFFD99400))))
            drawPath(tri, Color(0xFF8A6100), style = Stroke(s * .05f, join = StrokeJoin.Round))
            drawRoundRect(Color(0xFF3B2A00), Offset(s / 2 - bar / 2, s * .34f), Size(bar, s * .3f), CornerRadius(bar / 2))
            drawCircle(Color(0xFF3B2A00), bar * .6f, Offset(s / 2, s * .77f))
        } else {
            drawCircle(Brush.verticalGradient(listOf(Color(0xFF8FC8FA), Color(0xFF2B7FD8), Color(0xFF0F4FA8))), s / 2)
            drawCircle(Color(0xFF0B3C86), s / 2 - s * .01f, style = Stroke(s * .03f))
            drawArc(Color.White.copy(alpha = .45f), 190f, 160f, true, Offset(s * .12f, s * .05f), Size(s * .76f, s * .45f))
            drawCircle(Color.White, bar * .62f, Offset(s / 2, s * .28f))
            drawRoundRect(Color.White, Offset(s / 2 - bar / 2, s * .42f), Size(bar, s * .36f), CornerRadius(bar / 3))
        }
    }
}

/** Aero caption button hanging from the frame top: glossy glass (− ) or red (×), sharp gloss line at mid height, white glyph with a dark outline. */
@androidx.compose.runtime.Composable
fun Cap7(close: Boolean, onClick: () -> Unit) {
    androidx.compose.foundation.Canvas(Modifier.size(if (close) 44.dp else 27.dp, 19.dp)
        .clickable(onClick = onClick).semantics { contentDescription = if (close) "Close" else "Minimise" }) {
        val p = 1.dp.toPx(); val r = CornerRadius(4.dp.toPx()); val wd = size.width; val ht = size.height
        // top corners square (they meet the frame edge), only the outer bottom corner is round
        fun shape(i: Float) = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(i, 0f, wd - i, ht - i,
            CornerRadius.Zero, CornerRadius.Zero, if (close) r else CornerRadius.Zero, if (close) CornerRadius.Zero else r)) }
        val fill = if (close) Brush.verticalGradient(0f to Color(0xFFEDB6A8), .5f to Color(0xFFD7816E), .5f to Color(0xFFC23A23), 1f to Color(0xFFE2806A))
            else Brush.verticalGradient(0f to Color(0xFFF4F8FD), .5f to Color(0xFFD9E5F3), .5f to Color(0xFFBDD0E9), 1f to Color(0xFFDCE8F7))
        drawPath(shape(0f), fill)
        drawPath(shape(1.5f * p), Color.White.copy(alpha = .55f), style = Stroke(p))
        drawPath(shape(p / 2), if (close) Color(0xFF4F1A10) else Color(0xFF3E4F66), style = Stroke(p))
        val c = Offset(wd / 2, ht / 2 - p / 2); val g = 4.dp.toPx(); val dark = if (close) Color(0xFF4F1A10) else Color(0xFF2C3E57)
        for ((col, sw) in listOf(dark to 3.6f * p, Color.White to 2f * p)) {
            if (close) {
                drawLine(col, c + Offset(-g, -g * .8f), c + Offset(g, g * .8f), sw, StrokeCap.Square)
                drawLine(col, c + Offset(g, -g * .8f), c + Offset(-g, g * .8f), sw, StrokeCap.Square)
            } else drawLine(col, c + Offset(-g * .9f, g * .7f), c + Offset(g * .9f, g * .7f), sw, StrokeCap.Square)
        }
    }
}
