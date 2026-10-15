package com.umutk.pulse.features

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.Look
import com.umutk.pulse.PButton
import com.umutk.pulse.PCard
import com.umutk.pulse.T
import com.umutk.pulse.tr
import kotlinx.coroutines.delay

private const val DINK_SECONDS = 20

/** Dink: tap the dot as often as you can in 20 seconds. The dot jumps somewhere new after each hit. */
@Composable
fun Dink() {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("p", android.content.Context.MODE_PRIVATE) }
    var best by remember { mutableIntStateOf(prefs.getInt("dink_best", 0)) }
    var score by remember { mutableIntStateOf(0) }
    var left by remember { mutableIntStateOf(DINK_SECONDS) }
    var running by remember { mutableStateOf(false) }
    // dot position as fractions of the board, so it keeps its place when the size changes
    var dot by remember { mutableStateOf(Offset(0.5f, 0.5f)) }
    LaunchedEffect(running) {
        while (running && left > 0) { delay(1000); left-- }
        running = false
        if (left == 0 && score > best) { best = score; prefs.edit().putInt("dink_best", score).apply() }
    }
    PCard {
        Text(tr("Score", "Puan") + " $score   ·   ${left}s", Modifier.fillMaxWidth(), color = T.text, fontSize = 20.sp, textAlign = TextAlign.Center)
        Text(tr("Best", "En iyi") + " $best", Modifier.fillMaxWidth(), color = T.sub, fontSize = 13.sp, textAlign = TextAlign.Center)
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).background(T.line, RoundedCornerShape(T.radius))
                .pointerInput(running) {
                    detectTapGestures { p ->
                        if (!running) return@detectTapGestures
                        val c = Offset(dot.x * size.width, dot.y * size.height)
                        if ((p - c).getDistance() <= size.width * 0.1f) {
                            score++
                            dot = Offset(0.15f + kotlin.random.Random.nextFloat() * 0.7f, 0.15f + kotlin.random.Random.nextFloat() * 0.7f)
                        }
                    }
                }
        ) {
            val accent = Look.accent
            if (!running && left == 0) Text(tr("Time's up", "Süre doldu"), Modifier.align(Alignment.Center), color = T.text, fontSize = 22.sp)
            Canvas(Modifier.fillMaxSize()) {
                if (running) drawCircle(accent, radius = size.width * 0.08f, center = Offset(dot.x * size.width, dot.y * size.height))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PButton(
                when { running -> tr("Stop", "Durdur"); left == 0 -> tr("Play again", "Tekrar oyna"); else -> tr("Start", "Başlat") },
                Modifier.weight(1f)
            ) {
                if (running) running = false
                else { score = 0; left = DINK_SECONDS; dot = Offset(0.5f, 0.5f); running = true }
            }
        }
    }
}
