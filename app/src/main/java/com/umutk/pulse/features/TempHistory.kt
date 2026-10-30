package com.umutk.pulse.features

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.Look
import com.umutk.pulse.Opt
import com.umutk.pulse.PCard
import com.umutk.pulse.PChip
import com.umutk.pulse.T
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * The temperature of the battery and of the processor, noted once a minute while Pulse is running (only readings the phone really gave, nothing made up) and
 * kept for three days in one small text file: "time battery cpu" per line. The Temperature tool draws a day of it and tells the hottest moment.
 */
object TempHistory {
    class Sample(val t: Long, val bat: Float, val cpu: Float)
    private fun file(c: Context) = File(c.filesDir, "temp_history.txt")
    private const val KEEP_MS = 3L * 24 * 3600_000L
    private var lastWrite = 0L

    @Synchronized fun add(c: Context, bat: Float, cpu: Float) {
        val now = System.currentTimeMillis()
        if (now - lastWrite < 55_000L) return
        lastWrite = now
        runCatching { file(c).appendText("$now $bat $cpu\n") }
        // old lines go once in a while
        if (now % 7 == 0L) runCatching {
            val keep = load(c).filter { now - it.t < KEEP_MS }
            file(c).writeText(keep.joinToString("") { "${it.t} ${it.bat} ${it.cpu}\n" })
        }
    }

    fun load(c: Context): List<Sample> = runCatching {
        file(c).readLines().mapNotNull { l -> l.split(' ').takeIf { it.size == 3 }?.let { p -> Sample(p[0].toLong(), p[1].toFloat(), p[2].toFloat()) } }
    }.getOrDefault(emptyList())
}

/** The history card: a day at a time (today, yesterday, two days ago), battery and processor lines on one scale, and the hottest moment of each. */
@Composable
fun TempHistoryCard(bat: Float, cpu: Float) {
    val ctx = LocalContext.current
    var ago by remember { mutableIntStateOf(0) }
    var rev by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { withContext(Dispatchers.IO) { TempHistory.add(ctx, bat, cpu) }; rev++; delay(60_000) } }
    val all = remember(rev) { TempHistory.load(ctx) }
    val start = remember(ago) { Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0); add(Calendar.DAY_OF_YEAR, -ago) }.timeInMillis }
    val day = all.filter { it.t >= start && it.t < start + 86_400_000L }
    val bc = Look.accent; val cc = Color(0xFFFF8A3D)
    PCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0 to tr("Today", "Bugün"), 1 to tr("Yesterday", "Dün"), 2 to SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(System.currentTimeMillis() - 2 * 86_400_000L))).forEach { (a, n) -> PChip(ago == a, n) { ago = a } }
        }
        if (day.size < 2) { Text(tr("Not enough readings yet. Pulse notes the temperature each minute while it runs.", "Henüz yeterli ölçüm yok. Pulse çalışırken sıcaklığı dakikada bir kaydeder."), color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)); return@PCard }
        val lo = (day.minOf { minOf(it.bat, if (it.cpu > 0f) it.cpu else it.bat) } - 1f); val hi = (day.maxOf { maxOf(it.bat, it.cpu) } + 1f)
        Canvas(Modifier.fillMaxWidth().height(130.dp).padding(top = 10.dp)) {
            fun x(t: Long) = size.width * ((t - start) / 86_400_000f).coerceIn(0f, 1f)
            fun y(v: Float) = size.height * (1f - (v - lo) / (hi - lo))
            for (h in 0..24 step 6) drawLine(T.sub.copy(alpha = .25f), Offset(size.width * h / 24f, 0f), Offset(size.width * h / 24f, size.height), 1f)
            fun line(sel: (TempHistory.Sample) -> Float, col: Color) {
                val p = Path(); var first = true
                day.forEach { s -> val v = sel(s); if (v <= 0f) return@forEach; if (first) { p.moveTo(x(s.t), y(v)); first = false } else p.lineTo(x(s.t), y(v)) }
                drawPath(p, col, style = Stroke(2.5f, cap = StrokeCap.Round))
            }
            line({ it.bat }, bc); line({ it.cpu }, cc)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { listOf("00", "06", "12", "18", "24").forEach { Text(it, color = T.sub, fontSize = 10.sp) } }
        val hb = day.maxByOrNull { it.bat }!!; val hc = day.filter { it.cpu > 0f }.maxByOrNull { it.cpu }
        val tf = SimpleDateFormat("HH:mm", Locale.getDefault())
        Text(tr("Battery", "Pil") + ": " + Opt.temp(day.minOf { it.bat }) + " – " + Opt.temp(hb.bat) + "  (" + tr("hottest ", "en sıcak ") + tf.format(Date(hb.t)) + ")", color = bc, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        if (hc != null) Text(tr("Processor", "İşlemci") + ": " + Opt.temp(day.filter { it.cpu > 0f }.minOf { it.cpu }) + " – " + Opt.temp(hc.cpu) + "  (" + tr("hottest ", "en sıcak ") + tf.format(Date(hc.t)) + ")", color = cc, fontSize = 12.sp)
    }
}
