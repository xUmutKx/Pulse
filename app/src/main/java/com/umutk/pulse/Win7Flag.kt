package com.umutk.pulse

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.PI
import kotlin.math.sin

/** The colours of the four panes of the flag: red, green, blue, yellow. */
val WIN7_FLAG = listOf(Color(0xFFEA4A27), Color(0xFF7FBA00), Color(0xFF1FA3F0), Color(0xFFFFB900))

/**
 * The Windows flag of the Windows 7 era drawn at [c]: four panes, waving as a flag does (the whole picture is bent by a slow wave and leans a little), each pane a light top that sinks
 * into a deeper colour with a thin bright edge. [s] is the side of the whole flag; [alpha] fades it; [paneAlpha] lets each pane come in by itself (0..1 each, in the order red, green, blue, yellow).
 */
fun DrawScope.win7Flag(c: Offset, s: Float, alpha: Float = 1f, paneAlpha: List<Float> = listOf(1f, 1f, 1f, 1f)) {
    // a point of the flag in its own square (0..1), bent by the wave and leaned
    fun at(x: Float, y: Float): Offset {
        val wave = .075f * sin((x * 1.1f + .05f) * 2f * PI.toFloat() * .55f)
        val lean = (x - .5f) * -.06f
        return Offset(c.x + (x - .5f) * s * (1f - .03f * (y - .5f)), c.y + (y - .5f + wave + lean) * s)
    }
    val g = .045f
    for (k in 0 until 4) {
        val i = k % 2; val j = k / 2
        val x0 = if (i == 0) 0f else .5f + g / 2; val x1 = if (i == 0) .5f - g / 2 else 1f
        val y0 = if (j == 0) 0f else .5f + g / 2; val y1 = if (j == 0) .5f - g / 2 else 1f
        val path = Path().apply {
            val n = 8
            val top = (0..n).map { at(x0 + (x1 - x0) * it / n, y0) }
            val bottom = (0..n).map { at(x0 + (x1 - x0) * (n - it) / n, y1) }
            moveTo(top[0].x, top[0].y); top.drop(1).forEach { lineTo(it.x, it.y) }
            lineTo(at(x1, y1).x, at(x1, y1).y); bottom.forEach { lineTo(it.x, it.y) }
            close()
        }
        val col = WIN7_FLAG[k]; val a = alpha * paneAlpha[k]
        drawPath(path, Brush.verticalGradient(listOf(lighter(col, .45f).copy(alpha = a), col.copy(alpha = a), darker(col, .25f).copy(alpha = a)), startY = c.y - s * .5f, endY = c.y + s * .5f))
        drawPath(path, Color.White.copy(alpha = .35f * a), style = androidx.compose.ui.graphics.drawscope.Stroke(width = s * .012f))
    }
}

private fun lighter(c: Color, k: Float) = Color(c.red + (1f - c.red) * k, c.green + (1f - c.green) * k, c.blue + (1f - c.blue) * k)
private fun darker(c: Color, k: Float) = Color(c.red * (1f - k), c.green * (1f - k), c.blue * (1f - k))
