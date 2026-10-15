package com.umutk.pulse

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.Modifier

/** Windows 11: a see-through acrylic panel with rounded corners and a thin edge. */
fun menuStyle11(w: PTheme) = MenuStyle(bg = w.panel.copy(alpha = .85f), ink = w.text, sub = w.sub, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, w.text.copy(alpha = .1f)), rowV = 6.dp, rowH = 14.dp, font = 13.sp, iconSize = 16.dp, rowMin = 36.dp, hiBg = w.text.copy(alpha = .08f), hiInk = w.text)

/** Message box icon of this look: Fluent blue circle with "i", or yellow rounded triangle with "!". */
@androidx.compose.runtime.Composable
fun MsgIcon11(warn: Boolean) {
    Canvas(Modifier.size(32.dp)) {
        val s = size.width; val bar = s * .1f
        if (warn) {
            val tri = Path().apply { moveTo(s / 2, s * .08f); lineTo(s * .95f, s * .88f); lineTo(s * .05f, s * .88f); close() }
            drawPath(tri, Color(0xFFFCE100)); drawPath(tri, Color(0xFFFCE100), style = Stroke(s * .08f, join = StrokeJoin.Round))
            drawRoundRect(Color(0xFF1A1A1A), Offset(s / 2 - bar / 2, s * .36f), Size(bar, s * .28f), CornerRadius(bar / 2))
            drawCircle(Color(0xFF1A1A1A), bar * .6f, Offset(s / 2, s * .76f))
        } else {
            drawCircle(Color(0xFF0F6CBD), s / 2)
            drawCircle(Color.White, bar * .6f, Offset(s / 2, s * .29f))
            drawRoundRect(Color.White, Offset(s / 2 - bar / 2, s * .42f), Size(bar, s * .34f), CornerRadius(bar / 2))
        }
    }
}
