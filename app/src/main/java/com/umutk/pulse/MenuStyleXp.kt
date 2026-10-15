package com.umutk.pulse

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Windows XP (Luna) menu: white panel, 1 dp #ACA899 edge, black 12 sp text, square corners, 16 px icon gutter. */
fun menuStyleXp(w: PTheme) = MenuStyle(bg = Color.White, ink = Color.Black, sub = Color(0xFF3C5A96), shape = androidx.compose.ui.graphics.RectangleShape,
    border = BorderStroke(1.dp, Color(0xFFACA899)), rowV = 3.dp, rowH = 6.dp, font = 12.sp, iconSize = 16.dp, rowMin = 22.dp, hiBg = Color(0xFF316AC5), hiInk = Color.White)

/** XP message box icon: glossy blue "i" bubble, or yellow warning triangle with "!". */
@androidx.compose.runtime.Composable
fun MsgIconXp(warn: Boolean) {
    Canvas(Modifier.size(32.dp)) {
        val s = size.width; val bar = s * .12f
        if (warn) {
            val tri = Path().apply { moveTo(s / 2, s * .04f); lineTo(s * .97f, s * .92f); lineTo(s * .03f, s * .92f); close() }
            drawPath(tri, Brush.verticalGradient(listOf(Color(0xFFFFE97A), Color(0xFFF5B800))))
            drawPath(tri, Color(0xFFB88700), style = Stroke(s * .06f, join = StrokeJoin.Round))
            drawRoundRect(Color(0xFF3A2A00), Offset(s / 2 - bar / 2, s * .34f), Size(bar, s * .3f), CornerRadius(bar / 2))
            drawCircle(Color(0xFF3A2A00), bar * .6f, Offset(s / 2, s * .77f))
        } else {
            // speech bubble: white-rimmed glossy blue ball with a tail at lower left, soft grey drop shadow
            val c = Offset(s * .54f, s * .44f); val rr = s * .42f
            val tail = Path().apply { moveTo(s * .22f, s * .66f); lineTo(s * .04f, s * .97f); lineTo(s * .44f, s * .82f); close() }
            drawCircle(Color(0x33000000), rr + s * .04f, c + Offset(s * .05f, s * .05f))
            drawPath(tail, Color.White, style = Stroke(s * .1f, join = StrokeJoin.Round)); drawCircle(Color.White, rr + s * .045f, c)
            drawPath(tail, Color(0xFF2A62D0))
            drawCircle(Brush.radialGradient(listOf(Color(0xFF9CCBFF), Color(0xFF3E85EE), Color(0xFF1748B8)), c - Offset(rr * .35f, rr * .45f), rr * 1.5f), rr, c)
            drawOval(Brush.verticalGradient(listOf(Color.White.copy(alpha = .7f), Color.Transparent), c.y - rr, c.y), Offset(c.x - rr * .62f, c.y - rr * .92f), Size(rr * 1.24f, rr * .8f))
            drawCircle(Color.White, bar * .62f, Offset(c.x, c.y - rr * .5f))
            drawRoundRect(Color.White, Offset(c.x - bar / 2, c.y - rr * .22f), Size(bar, rr * .8f), CornerRadius(bar / 3))
        }
    }
}
