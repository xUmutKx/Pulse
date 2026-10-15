package com.umutk.pulse.features

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.Look
import com.umutk.pulse.PCard
import com.umutk.pulse.PHead
import com.umutk.pulse.Sampler
import com.umutk.pulse.T
import com.umutk.pulse.m3Guides
import com.umutk.pulse.m3Line
import com.umutk.pulse.resColor
import kotlinx.coroutines.delay

/** One hour of samples, one per second while the screen is visible. Only touched on the main thread. */
private object Hist {
    const val CAP = 3600
    val data = Array(3) { FloatArray(CAP) }   // indexed by Metric.ordinal
    var next = 0
    var size = 0

    fun push(ramPct: Float, cpu: Float, bat: Float) {
        data[0][next] = ramPct; data[1][next] = cpu; data[2][next] = bat
        next = (next + 1) % CAP
        if (size < CAP) size++
    }

    /** Oldest first. */
    fun series(m: Int): FloatArray = FloatArray(size) { i -> data[m][(next - size + i + CAP) % CAP] }
}

private enum class Metric(val label: String) {
    RAM("RAM"), CPU("CPU"), BATTERY("Battery");

    fun color(): Color = when (this) {
        RAM -> resColor("memory"); CPU -> resColor("cpu"); BATTERY -> resColor("battery")
    }
}

@Composable
fun HistoryGraphScreen(onBack: () -> Unit) {
    var sel by rememberSaveable { mutableStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            val s = Sampler.snap.value
            val ramPct = if (s.ramTotalMb == 0) 0f else 100f * s.ramUsedMb / s.ramTotalMb
            Hist.push(ramPct, s.cpu.toFloat(), s.batPct.toFloat())
            tick++
            // shortcut: sampling is 1 Hz at most; the app-wide loop in App() owns the real reads
            delay(maxOf(1000L, Sampler.interval))
        }
    }

    val metric = Metric.values()[sel]
    val values = remember(tick, sel) { Hist.series(sel) }
    val cur = values.lastOrNull()
    val avg = if (values.isEmpty()) null else values.average().toFloat()
    val peak = values.maxOrNull()

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("← Back", color = Look.accent, fontSize = 15.sp, modifier = Modifier.clickable(onClick = onBack).padding(vertical = 4.dp))
        PHead("History", "last hour")

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Metric.values().forEachIndexed { i, m ->
                val on = i == sel
                Text(
                    m.label,
                    color = if (on) Color.White else T.text,
                    fontSize = 14.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier
                        .clip(RoundedCornerShape(T.radius))
                        .background(if (on) m.color() else T.panel)
                        .clickable { sel = i }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }

        PCard {
            LineGraph(values, metric.color(), Modifier.fillMaxWidth().height(180.dp))
            Row(Modifier.fillMaxWidth()) {
                Stat("Current", cur, Modifier.weight(1f))
                Stat("Average", avg, Modifier.weight(1f))
                Stat("Peak", peak, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Stat(title: String, value: Float?, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.Start) {
        Text(title, color = T.sub, fontSize = 12.sp)
        Text(value?.let { "%.0f%%".format(it) } ?: "—", color = T.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Percent values (0..100) drawn left to right, newest at the right edge. */
@Composable
private fun LineGraph(values: FloatArray, color: Color, modifier: Modifier = Modifier) {
    if (T.material) {
        // M3 Expressive: rounded tonal area, dotted guides, smooth gradient line
        Canvas(modifier.clip(RoundedCornerShape(20.dp)).background(if (T.id == "amoled") Color.Black else color.copy(alpha = .08f))) {
            m3Guides(T.line)
            if (values.size < 2) return@Canvas
            val pad = 3.dp.toPx()
            val pts = values.mapIndexed { i, v -> Offset(size.width * (Hist.CAP - values.size + i) / (Hist.CAP - 1f), pad + (size.height - 2 * pad) * (1 - v.coerceIn(0f, 100f) / 100f)) }
            m3Line(pts, color, size.height)
        }
        return
    }
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        for (p in listOf(0.25f, 0.5f, 0.75f)) {
            val y = h * (1 - p)
            drawLine(T.sub.copy(alpha = 0.25f), Offset(0f, y), Offset(w, y), strokeWidth = 1.dp.toPx())
        }
        if (values.size < 2) return@Canvas
        val path = Path()
        val n = values.size
        values.forEachIndexed { i, v ->
            val x = w * (Hist.CAP - n + i) / (Hist.CAP - 1f)
            val y = h * (1 - v.coerceIn(0f, 100f) / 100f)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = 2.dp.toPx()))
    }
}
