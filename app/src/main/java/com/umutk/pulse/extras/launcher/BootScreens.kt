package com.umutk.pulse.extras.launcher

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

/** The start of the Terminal look: the lines of a Linux machine starting (kernel, services, "OK" marks) and the login, as a console shows them. A tap skips it. */
@Composable
fun TerminalBoot(onDone: () -> Unit) {
    val lines = remember { mutableStateListOf<String>() }
    LaunchedEffect(Unit) {
        val cores = Runtime.getRuntime().availableProcessors()
        val script = listOf(
            "[    0.000000] Linux version 6.1.0-pulse (pulse@phone) #1 SMP PREEMPT" to 60L,
            "[    0.000000] Machine model: ${Build.MANUFACTURER} ${Build.MODEL}" to 40L,
            "[    0.114210] CPU: ${cores} cores online" to 40L,
            "[    0.231877] Memory: ready" to 40L,
            "[    0.388102] pulse-init: mounting /data" to 60L,
            "[  OK  ] Mounted /data." to 50L,
            "[  OK  ] Started Network Manager." to 50L,
            "[  OK  ] Started Pulse launcher service." to 60L,
            "[  OK  ] Reached target Graphical Interface." to 80L,
            "" to 40L,
            "Pulse 1.0 phone tty1" to 60L,
            "" to 30L,
            "phone login: pulse (automatic login)" to 140L,
            "Last login: today on tty1" to 120L,
        )
        for ((l, d) in script) { lines.add(l); delay(d) }
        delay(260); onDone()
    }
    Column(Modifier.fillMaxSize().background(Color.Black).clickable { onDone() }.statusBarsPadding().padding(horizontal = 10.dp, vertical = 8.dp)) {
        lines.forEach { l ->
            if (l.isEmpty()) Spacer(Modifier.height(8.dp))
            else Text(l, color = if (l.startsWith("[  OK  ]")) Color(0xFF33FF33) else Color(0xFFC8C8C8), fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp)
        }
    }
}

/** The start of the Windows Phone look: black, the flat four-pane logo, and the dots of the phone's progress running across under it. A tap skips it. */
@Composable
fun WinPhoneBoot(onDone: () -> Unit) {
    var t by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = System.nanoTime()
        while (true) { t = (System.nanoTime() - start) / 1e9f; if (t > 2.2f) break; delay(16) }
        onDone()
    }
    Box(Modifier.fillMaxSize().background(Color.Black).clickable { onDone() }) {
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            val appear = (t / .5f).coerceIn(0f, 1f)
            Canvas(Modifier.size(96.dp)) {
                val s = 40.dp.toPx(); val g = 4.dp.toPx(); val c = center
                val col = Color.White.copy(alpha = appear)
                val x0 = c.x - s - g / 2; val y0 = c.y - s - g / 2
                drawRect(col, Offset(x0, y0), Size(s, s)); drawRect(col, Offset(x0 + s + g, y0), Size(s, s))
                drawRect(col, Offset(x0, y0 + s + g), Size(s, s)); drawRect(col, Offset(x0 + s + g, y0 + s + g), Size(s, s))
            }
            Spacer(Modifier.height(36.dp))
            // five dots run across, one after the other, and start again
            Canvas(Modifier.width(220.dp).height(14.dp)) {
                for (k in 0 until 5) {
                    val ph = ((t * 1.1f - k * .07f) % 1f + 1f) % 1f
                    val x = size.width * (-.15f + 1.3f * ph)
                    val a = sin(ph * PI.toFloat()).coerceIn(0f, 1f)
                    drawCircle(Color.White.copy(alpha = a * appear), 3.dp.toPx(), Offset(x, size.height / 2f))
                }
            }
        }
    }
}
