package com.umutk.pulse.features

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.umutk.pulse.Hw
import com.umutk.pulse.Opt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

private const val SAMPLE_MS = 3_000L
private const val WINDOW_MIN = 10L
private const val CAPACITY = (WINDOW_MIN * 60_000L / SAMPLE_MS).toInt()
private const val WARN_C = 42f
private val WarnRed = Color(0xFFE53935)

data class TempSample(val at: Long, val battery: Float?, val cpu: Float?)

/** Fixed-size ring: once the window is full the oldest sample is dropped. */
class TempRing(private val capacity: Int = CAPACITY) {
    private val buf = ArrayDeque<TempSample>(capacity)
    fun add(s: TempSample) {
        if (buf.size == capacity) buf.removeFirst()
        buf.addLast(s)
    }
    fun snapshot(): List<TempSample> = buf.toList()
}

/** Battery temperature from the sticky ACTION_BATTERY_CHANGED intent, in °C. Null when the device reports none. */
private fun readBatteryC(ctx: Context): Float? {
    val bi: Intent = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
    if (!bi.hasExtra(BatteryManager.EXTRA_TEMPERATURE)) return null
    return bi.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f
}

/** Hottest readable /sys/class/thermal zone, reusing the same reader as the Thermal page. Null when none is readable. */
private fun readCpuC(): Float? = Hw.maxTemp().takeIf { it > 0f }

private class Series(val label: String, val color: Color, val points: List<Pair<Long, Float>>) {
    val min get() = points.minOf { it.second }
    val max get() = points.maxOf { it.second }
    val current get() = points.last().second
}

@Composable
fun TempGraphScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val ring = remember { TempRing() }
    var samples by remember { mutableStateOf<List<TempSample>>(emptyList()) }
    var probed by remember { mutableStateOf(false) }

    // Runs only while this composable is on screen; cancelled when it leaves.
    LaunchedEffect(Unit) {
        while (isActive) {
            val s = withContext(Dispatchers.IO) {
                TempSample(System.currentTimeMillis(), readBatteryC(ctx), readCpuC())
            }
            ring.add(s)
            samples = ring.snapshot()
            probed = true
            delay(SAMPLE_MS)
        }
    }

    val batteryColor = MaterialTheme.colorScheme.primary
    val cpuColor = MaterialTheme.colorScheme.tertiary
    val series = listOfNotNull(
        samples.mapNotNull { s -> s.battery?.let { s.at to it } }
            .takeIf { it.isNotEmpty() }?.let { Series("Battery", batteryColor, it) },
        samples.mapNotNull { s -> s.cpu?.let { s.at to it } }
            .takeIf { it.isNotEmpty() }?.let { Series("CPU zone", cpuColor, it) },
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
            Text("Temperature", style = MaterialTheme.typography.titleLarge)
        }

        when {
            !probed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Reading sensors...", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            series.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No temperature sensor found", style = MaterialTheme.typography.titleMedium)
            }
            else -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    series.forEach { s -> SeriesStat(s) }
                }
                if (series.none { it.label == "CPU zone" }) {
                    Text(
                        "CPU zone is not readable on this device",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TempChart(
                    series = series,
                    end = samples.last().at,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(WarnRed.copy(alpha = 0.5f)))
                    Text(
                        "  Above ${WARN_C.toInt()} °C",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SeriesStat(s: Series) {
    Column {
        Text(s.label, style = MaterialTheme.typography.labelMedium, color = s.color)
        Text(Opt.temp(s.current), style = MaterialTheme.typography.headlineSmall)
        Text(
            "min ${Opt.temp(s.min)}  max ${Opt.temp(s.max)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TempChart(series: List<Series>, end: Long, modifier: Modifier) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier) {
        val all = series.flatMap { s -> s.points.map { it.second } }
        val yMin = minOf(25f, all.min() - 3f)
        val yMax = maxOf(45f, all.max() + 3f)
        val windowMs = WINDOW_MIN * 60_000L
        val start = end - windowMs
        fun yOf(v: Float) = size.height * (1f - (v - yMin) / (yMax - yMin))
        fun xOf(t: Long) = size.width * ((t - start).toFloat() / windowMs).coerceIn(0f, 1f)

        // Grid, four horizontal lines
        for (i in 0..4) {
            val y = size.height * i / 4f
            drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        }

        // Red band above the warning threshold
        val warnY = yOf(WARN_C)
        drawRect(WarnRed.copy(alpha = 0.14f), topLeft = Offset.Zero, size = Size(size.width, warnY))
        drawLine(WarnRed.copy(alpha = 0.7f), Offset(0f, warnY), Offset(size.width, warnY), strokeWidth = 1.dp.toPx())

        for (s in series) {
            val pts = s.points.map { Offset(xOf(it.first), yOf(it.second)) }
            if (pts.size == 1) {
                drawCircle(s.color, radius = 3.dp.toPx(), center = pts[0])
                continue
            }
            val line = smoothPath(pts, bottom = null)
            val fill = smoothPath(pts, bottom = size.height)
            drawPath(
                fill,
                Brush.verticalGradient(
                    listOf(s.color.copy(alpha = 0.35f), Color.Transparent),
                    startY = 0f,
                    endY = size.height,
                ),
            )
            drawPath(line, s.color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

/** Horizontal-tangent cubic segments through the points: smooth without overshoot. Closed to [bottom] when given. */
private fun smoothPath(pts: List<Offset>, bottom: Float?): Path = Path().apply {
    moveTo(pts[0].x, pts[0].y)
    for (i in 1 until pts.size) {
        val p0 = pts[i - 1]
        val p1 = pts[i]
        val mx = (p0.x + p1.x) / 2f
        cubicTo(mx, p0.y, mx, p1.y, p1.x, p1.y)
    }
    if (bottom != null) {
        lineTo(pts.last().x, bottom)
        lineTo(pts[0].x, bottom)
        close()
    }
}
