package com.umutk.pulse.extras.keyboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PButton
import com.umutk.pulse.PField
import com.umutk.pulse.T
import com.umutk.pulse.tr
import kotlinx.coroutines.delay

/** Typing speed test (as in WM Keyboard): type the line, words per minute and accuracy show when it is done. */
object KbSpeed {
    private val EN = listOf(
        "the quick brown fox jumps over the lazy dog while the sun sets behind the hills",
        "a small boat drifted slowly across the quiet lake as the evening lights came on",
        "music filled the room and everyone forgot the time until the last song ended",
        "she packed her bag, locked the door and walked to the station before the rain",
        "good habits are built one day at a time with patience and a little curiosity",
    )
    private val TR = listOf(
        "sabah erkenden kalkıp denize karşı çayımı içtim ve günün planını yaptım",
        "küçük kedi pencerenin önünde güneşlenirken kuşları dikkatle izliyordu",
        "akşam yemeğinden sonra hep birlikte sahilde uzun bir yürüyüşe çıktık",
        "yağmur başlamadan önce çamaşırları topladım ve pencereleri kapattım",
        "iyi bir kitap insanı bambaşka dünyalara götürür ve zamanı unutturur",
    )
    fun line(i: Int): String { val l = if (KeyboardPrefs.langList().first() == "tr") TR else EN; return l[Math.floorMod(i, l.size)] }
    fun best(c: android.content.Context) = c.getSharedPreferences("pulse_kb_speed", 0).getInt("best", 0)
    fun saveBest(c: android.content.Context, w: Int) { if (w > best(c)) c.getSharedPreferences("pulse_kb_speed", 0).edit().putInt("best", w).apply() }
}

@Composable
fun KbSpeedTest() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var round by remember { mutableStateOf((0..4).random()) }
    val target = KbSpeed.line(round)
    var typed by remember(round) { mutableStateOf("") }
    var start by remember(round) { mutableLongStateOf(0L) }
    var now by remember(round) { mutableLongStateOf(0L) }
    val done = typed.length >= target.length
    LaunchedEffect(start, done) { while (start > 0 && !done) { now = System.currentTimeMillis(); delay(250) } }
    val secs = if (start == 0L) 0.0 else ((if (done) now else System.currentTimeMillis()) - start) / 1000.0
    val right = typed.indices.count { it < target.length && typed[it] == target[it] }
    val wpm = if (secs < 1) 0 else ((right / 5.0) / (secs / 60.0)).toInt()
    val acc = if (typed.isEmpty()) 100 else right * 100 / typed.length
    LaunchedEffect(done) { if (done) { now = System.currentTimeMillis(); KbSpeed.saveBest(ctx, wpm) } }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(buildAnnotatedString {
            target.forEachIndexed { i, ch ->
                val col = when { i >= typed.length -> T.sub; typed[i] == ch -> T.text; else -> androidx.compose.ui.graphics.Color(0xFFE5484D) }
                withStyle(SpanStyle(color = col)) { append(ch) }
            }
        }, fontSize = 16.sp, lineHeight = 22.sp)
        PField(typed, { if (start == 0L && it.isNotEmpty()) start = System.currentTimeMillis(); if (!done) typed = it.take(target.length) }, tr("Type the line", "Satırı yaz"), Modifier.fillMaxWidth().padding(top = 8.dp))
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("$wpm " + tr("wpm", "kelime/dk"), color = T.text, fontSize = 15.sp)
            Text("%$acc", color = T.text, fontSize = 15.sp)
            Text("${secs.toInt()} " + tr("s", "sn"), color = T.sub, fontSize = 15.sp)
            Text(tr("Best", "En iyi") + " ${KbSpeed.best(ctx)}", color = T.sub, fontSize = 15.sp)
        }
        PButton(tr("New line", "Yeni satır"), Modifier.padding(top = 8.dp)) { round++ }
    }
}
