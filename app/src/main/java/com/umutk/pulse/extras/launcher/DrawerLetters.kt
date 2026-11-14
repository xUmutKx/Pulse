package com.umutk.pulse.extras.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The first letter an app is listed under: its capital, or "#" for a name that does not begin with a letter. */
internal fun listLetter(label: String): String {
    val c = label.trim().firstOrNull()?.uppercaseChar() ?: return "#"
    return if (c.isLetter()) c.toString() else "#"
}

/** The apps by letter, "#" last. */
internal fun lettered(apps: List<LaunchApp>): List<Pair<String, List<LaunchApp>>> =
    apps.groupBy { listLetter(it.label) }.toList().sortedWith(compareBy({ it.first == "#" }, { it.first }))

/**
 * The letter strip at the right edge of an A-Z list: a touch or a slide along it jumps to that letter, and the letter under the finger shows large beside the strip.
 */
@Composable
internal fun LetterBar(letters: List<String>, ink: Color, bubble: Color, onLetter: (String) -> Unit, modifier: Modifier = Modifier) {
    var h by remember { mutableStateOf(1f) }
    var cur by remember { mutableStateOf<String?>(null) }
    fun at(y: Float) { if (letters.isEmpty()) return; val i = (y / h * letters.size).toInt().coerceIn(0, letters.lastIndex); letters[i].let { if (it != cur) { cur = it; onLetter(it) } } }
    Box(modifier.fillMaxHeight().width(24.dp)) {
        Column(
            Modifier.fillMaxHeight().width(24.dp)
                .pointerInput(letters) { detectTapGestures(onPress = { o -> h = size.height.toFloat(); at(o.y); tryAwaitRelease(); cur = null }) }
                .pointerInput(letters) { detectDragGestures(onDragStart = { o -> h = size.height.toFloat(); at(o.y) }, onDragEnd = { cur = null }, onDragCancel = { cur = null }) { c, _ -> c.consume(); at(c.position.y) } },
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly,
        ) {
            letters.forEach { l -> Text(l, color = ink, fontSize = 10.sp, fontWeight = if (l == cur) FontWeight.Bold else FontWeight.Normal) }
        }
        cur?.let { l ->
            Box(Modifier.align(Alignment.CenterStart).padding(end = 0.dp).size(56.dp).background(bubble, CircleShape).let { it }, contentAlignment = Alignment.Center) {
                Text(l, color = ink, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
