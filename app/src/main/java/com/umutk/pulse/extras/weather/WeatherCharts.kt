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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb

/**
 * A plain chart of one series: a line, or bars when [bars] is set (for chances, [top] = 100).
 * Drawn only from the colour it is given, so it follows the look's text colour.
 */
@Composable
fun Sparkline(values: List<Double>, color: Color, bars: Boolean = false, top: Double? = null, label: ((Double) -> String)? = null) {
    if (values.size < 2) return
    val lo = if (bars) 0.0 else values.minOrNull() ?: 0.0
    val hi = top ?: values.maxOrNull() ?: 1.0
    val span = (hi - lo).takeIf { it > 0.0 } ?: 1.0
    // the numbers of the highest and the lowest point (the highest bar), written on the chart
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { this.color = color.copy(alpha = .85f).toArgb(); textAlign = android.graphics.Paint.Align.CENTER }
    Canvas(Modifier.fillMaxWidth().height(88.dp)) {
        paint.textSize = 11.dp.toPx()
        val pad = 14.dp.toPx()
        val step = size.width / values.size
        if (bars) {
            values.forEachIndexed { i, v ->
                val h = (((v - lo) / span).toFloat() * (size.height - pad)).coerceIn(0f, size.height - pad)
                drawRect(color, topLeft = Offset(i * step + step * 0.2f, size.height - h), size = Size(step * 0.6f, h))
            }
            if (label != null) {
                val mi = values.indices.maxByOrNull { values[it] } ?: 0
                val h = (((values[mi] - lo) / span).toFloat() * (size.height - pad)).coerceIn(0f, size.height - pad)
                drawContext.canvas.nativeCanvas.drawText(label(values[mi]), mi * step + step / 2, size.height - h - 3.dp.toPx(), paint)
            }
        } else {
            val path = Path()
            values.forEachIndexed { i, v ->
                val x = i * step + step / 2
                val y = size.height - pad - (((v - lo) / span).toFloat() * (size.height - 2 * pad))
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            if (label != null) {
                val hiI = values.indices.maxByOrNull { values[it] } ?: 0
                val loI = values.indices.minByOrNull { values[it] } ?: 0
                fun yOf(v: Double) = size.height - pad - (((v - lo) / span).toFloat() * (size.height - 2 * pad))
                val hx = (hiI * step + step / 2).coerceIn(18.dp.toPx(), size.width - 18.dp.toPx())
                val lx = (loI * step + step / 2).coerceIn(18.dp.toPx(), size.width - 18.dp.toPx())
                drawContext.canvas.nativeCanvas.drawText(label(values[hiI]), hx, yOf(values[hiI]) - 6.dp.toPx(), paint)
                if (loI != hiI) drawContext.canvas.nativeCanvas.drawText(label(values[loI]), lx, yOf(values[loI]) + 15.dp.toPx(), paint)
            }
            drawPath(path, color, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
