package com.umutk.pulse.extras.weather

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * A plain chart of one series: a line, or bars when [bars] is set (for chances, [top] = 100).
 * Drawn only from the colour it is given, so it follows the look's text colour.
 */
@Composable
fun Sparkline(values: List<Double>, color: Color, bars: Boolean = false, top: Double? = null) {
    if (values.size < 2) return
    val lo = if (bars) 0.0 else values.minOrNull() ?: 0.0
    val hi = top ?: values.maxOrNull() ?: 1.0
    val span = (hi - lo).takeIf { it > 0.0 } ?: 1.0
    Canvas(Modifier.fillMaxWidth().height(72.dp)) {
        val step = size.width / values.size
        if (bars) {
            values.forEachIndexed { i, v ->
                val h = (((v - lo) / span).toFloat() * size.height).coerceIn(0f, size.height)
                drawRect(color, topLeft = Offset(i * step + step * 0.2f, size.height - h), size = Size(step * 0.6f, h))
            }
        } else {
            val path = Path()
            values.forEachIndexed { i, v ->
                val x = i * step + step / 2
                val y = size.height - (((v - lo) / span).toFloat() * size.height)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
