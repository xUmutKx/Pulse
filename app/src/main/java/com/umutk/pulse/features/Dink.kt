package com.umutk.pulse.features

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PButton
import com.umutk.pulse.PCard
import com.umutk.pulse.T
import kotlinx.coroutines.delay

private const val DINK_SECONDS = 20

/** Dink: tap the dot as often as you can in 20 seconds. The dot jumps somewhere new after each hit. */
@Composable
fun Dink() {
    var score by remember { mutableIntStateOf(0) }
    var left by remember { mutableIntStateOf(DINK_SECONDS) }
    var running by remember { mutableStateOf(false) }
    // dot position as fractions of the board, so it keeps its place when the size changes
    var dot by remember { mutableStateOf(Offset(0.5f, 0.5f)) }
    LaunchedEffect(running) {
        while (running && left > 0) { delay(1000); left-- }
        running = false
    }
    PCard {
        Text("Score $score   ·   ${left}s", Modifier.fillMaxWidth(), color = T.text, fontSize = 20.sp, textAlign = TextAlign.Center)
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
            Canvas(Modifier.fillMaxSize()) {
                if (running) drawCircle(T.accent, radius = size.width * 0.08f, center = Offset(dot.x * size.width, dot.y * size.height))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PButton(
                when { running -> "Stop"; left == 0 -> "Play again"; else -> "Start" },
                Modifier.weight(1f)
            ) {
                if (running) running = false
                else { score = 0; left = DINK_SECONDS; dot = Offset(0.5f, 0.5f); running = true }
            }
        }
    }
}
