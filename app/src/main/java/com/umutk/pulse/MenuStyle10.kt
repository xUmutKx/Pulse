package com.umutk.pulse

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Windows 10: flat white (dark grey) panel, 1dp grey edge, square corners, 12sp text. shortcut: DropdownMenuItem keeps a 48dp minimum row, so rows stay taller than the real 32dp. */
fun menuStyle10(w: PTheme): MenuStyle {
    val dark = w.dark
    return MenuStyle(
        bg = if (dark) Color(0xFF2B2B2B) else Color.White, ink = if (dark) Color.White else Color.Black, sub = if (dark) Color(0xFFD0D0D0) else Color(0xFF444444),
        shape = RectangleShape, border = BorderStroke(1.dp, if (dark) Color(0xFF454545) else Color(0xFFCCCCCC)),
        rowV = 4.dp, rowH = 12.dp, font = 12.sp, iconSize = 16.dp, rowMin = 32.dp, hiBg = if (dark) Color(0xFF3F3F3F) else Color(0xFFE5E5E5), hiInk = if (dark) Color.White else Color.Black,
    )
}

/** Windows 10 message box icon: flat blue round "i" or flat yellow warning triangle. */
@Composable
fun MsgIcon10(warn: Boolean) {
    Canvas(Modifier.size(32.dp)) {
        val s = size.width; val bar = s * .1f
        if (warn) {
            val tri = Path().apply { moveTo(s / 2, s * .06f); lineTo(s * .97f, s * .92f); lineTo(s * .03f, s * .92f); close() }
            drawPath(tri, Color(0xFFFFB900)); drawPath(tri, Color(0xFFFFB900), style = Stroke(s * .04f, join = StrokeJoin.Round))
            drawRoundRect(Color.Black, Offset(s / 2 - bar / 2, s * .36f), Size(bar, s * .32f), CornerRadius(bar / 3))
            drawCircle(Color.Black, bar * .6f, Offset(s / 2, s * .78f))
        } else {
            drawCircle(Color(0xFF0078D7), s / 2)
            drawCircle(Color.White, bar * .65f, Offset(s / 2, s * .28f))
            drawRoundRect(Color.White, Offset(s / 2 - bar / 2, s * .42f), Size(bar, s * .36f), CornerRadius(bar / 3))
        }
    }
}
