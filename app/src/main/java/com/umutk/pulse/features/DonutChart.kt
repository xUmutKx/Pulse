package com.umutk.pulse.features

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.Look
import com.umutk.pulse.T

private val FallbackPalette = listOf(
    0xFF1B8DBE, 0xFF8B12AE, 0xFF4DA64D, 0xFFD97B29, 0xFFC0392B, 0xFF2E9E5B,
).map { Color(it) }

/**
 * Storage breakdown donut: one arc per category, round caps, total in the centre, legend below.
 * Sizes are any unit (bytes are fine); entries that are zero or negative are ignored.
 * The sweep animates from zero whenever [slices] changes.
 */
@Composable
fun DonutChart(slices: List<Pair<String, Long>>, modifier: Modifier = Modifier, centerText: String = "") {
    val parts = slices.filter { it.second > 0L }
    val total = parts.sumOf { it.second }
    val palette = listOf(Look.accent) + FallbackPalette
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(slices) {
        sweep.snapTo(0f)
        sweep.animateTo(1f, tween(durationMillis = 600))
    }

    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(180.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(180.dp)) {
                val strokePx = 22.dp.toPx()
                val inset = strokePx / 2
                val arcSize = Size(size.width - strokePx, size.height - strokePx)
                val topLeft = Offset(inset, inset)
                if (total <= 0L) {
                    drawArc(T.line, 0f, 360f, false, topLeft, arcSize, style = Stroke(strokePx))
                } else if (T.material) {
                    // M3 Expressive: rounded segments with gaps on a tonal track
                    drawArc(T.line.copy(alpha = .5f), 0f, 360f, false, topLeft, arcSize, style = Stroke(strokePx))
                    val capDeg = Math.toDegrees((strokePx / 2 / (arcSize.width / 2)).toDouble()).toFloat() // round cap overhang
                    val gapDeg = if (parts.size > 1) 2 * capDeg + 4f else 0f
                    var start = -90f
                    parts.forEachIndexed { i, (_, v) ->
                        val sweepDeg = 360f * v / total * sweep.value
                        val draw = sweepDeg - gapDeg
                        if (parts.size == 1) drawArc(palette[0], start, sweepDeg, false, topLeft, arcSize, style = Stroke(strokePx, cap = StrokeCap.Round))
                        else if (draw > 0f) drawArc(palette[i % palette.size], start + gapDeg / 2, draw, false, topLeft, arcSize, style = Stroke(strokePx, cap = StrokeCap.Round))
                        else if (sweepDeg > 0f) drawCircle(palette[i % palette.size], strokePx / 3, androidx.compose.ui.geometry.Offset(center.x + arcSize.width / 2 * kotlin.math.cos(Math.toRadians((start + sweepDeg / 2).toDouble())).toFloat(), center.y + arcSize.width / 2 * kotlin.math.sin(Math.toRadians((start + sweepDeg / 2).toDouble())).toFloat())) // tiny slice: a dot
                        start += sweepDeg
                    }
                } else {
                    // slices touch each other: flat ends, no gap between them, the ring is one whole circle
                    var start = -90f
                    parts.forEachIndexed { i, (_, v) ->
                        val sweepDeg = 360f * v / total * sweep.value
                        drawArc(palette[i % palette.size], start, sweepDeg, false, topLeft, arcSize, style = Stroke(strokePx))
                        start += sweepDeg
                    }
                }
            }
            if (centerText.isNotEmpty()) {
                Text(centerText, color = T.text, fontSize = if (T.material) 22.sp else 16.sp, fontWeight = if (T.material) FontWeight.Medium else FontWeight.SemiBold) // M3 title-large in Material
            }
        }
        Spacer(Modifier.height(12.dp))
        if (total <= 0L) Text("No data", color = T.sub, fontSize = 12.sp)
        parts.forEachIndexed { i, (name, v) ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start,
            ) {
                Box(Modifier.size(10.dp).background(palette[i % palette.size], CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(name, color = T.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text("%.1f%%".format(v * 100.0 / total), color = T.sub, fontSize = 13.sp)
            }
        }
    }
}
