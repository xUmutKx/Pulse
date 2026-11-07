package com.umutk.pulse.extras.ebook

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PIcon
import com.umutk.pulse.PSlider
import com.umutk.pulse.tr
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** One word of the RSVP stream; [paraEnd] is true for the last word of a paragraph (a longer breath follows). */
internal class RWord(val text: String, val paraEnd: Boolean)

/** The words of [paras] in order. */
internal fun rsvpWords(paras: List<Para>): List<RWord> {
    val out = ArrayList<RWord>()
    for (p in paras) {
        val ws = p.text.split(Regex("\\s+")).filter { it.isNotBlank() }
        ws.forEachIndexed { i, w -> out.add(RWord(w, i == ws.lastIndex)) }
    }
    return out
}

/** The letter the eye rests on: a little left of the middle of the word, as in speed-reading apps. */
private fun orp(len: Int): Int = when { len <= 1 -> 0; len <= 5 -> 1; len <= 9 -> 2; len <= 13 -> 3; else -> 4 }

/** How long a word stays, relative to the base time of the speed: long words, commas, the ends of sentences and paragraphs get a breath. */
private fun hold(w: RWord): Float {
    var k = 1f
    val core = w.text.trimEnd('"', '\'', ')', '”', '’')
    if (core.length >= 12) k *= 1.6f else if (core.length >= 8) k *= 1.3f
    k *= when (core.lastOrNull()) { ',', ';', ':' -> 1.6f; '.', '!', '?', '…' -> 2.4f; else -> 1f }
    if (w.paraEnd) k *= 1.5f
    return k
}

private fun rsvpPrefs(c: Context) = c.getSharedPreferences("ebook_rsvp", Context.MODE_PRIVATE)

/**
 * RSVP speed reading: one word at a time on the middle of the screen, the focus letter in the accent colour, 100 to 1000 words a minute.
 * It runs from the word where [words] begins (the page being read); [onTick] is told at each word so the reading time is counted.
 */
@Composable
internal fun RsvpPane(words: List<RWord>, ink: Color, sub: Color, bg: Color, accent: Color, onTick: () -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var wpm by remember { mutableIntStateOf(rsvpPrefs(ctx).getInt("wpm", 300)) }
    var at by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(true) }
    val total = words.size
    LaunchedEffect(playing, at, wpm, total) {
        if (!playing || total == 0) return@LaunchedEffect
        if (at >= total - 1) { playing = false; return@LaunchedEffect }
        onTick()
        delay((60_000f / wpm * hold(words[at])).toLong().coerceAtLeast(20L))
        at++
    }
    Column(Modifier.fillMaxSize().background(bg).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clickable { onClose() }, contentAlignment = Alignment.Center) { PIcon("close", ink, 22.dp) }
            Text("RSVP", color = ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 4.dp))
            Text("$wpm ${tr("wpm", "kelime/dk")}", color = sub, fontSize = 13.sp)
        }
        Box(Modifier.weight(1f).fillMaxWidth().clickable { playing = !playing }, contentAlignment = Alignment.Center) {
            val w = words.getOrNull(at)?.text.orEmpty()
            val p = orp(w.length).coerceAtMost((w.length - 1).coerceAtLeast(0))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // the marks above and below the focus letter
                Box(Modifier.width(2.dp).height(14.dp).background(accent.copy(alpha = .6f)))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(w.take(p), color = ink, fontSize = 38.sp, fontFamily = FontFamily.Serif, textAlign = TextAlign.End, maxLines = 1, softWrap = false, modifier = Modifier.weight(1f))
                    Text(w.drop(p).take(1), color = accent, fontSize = 38.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    Text(w.drop(p + 1), color = ink, fontSize = 38.sp, fontFamily = FontFamily.Serif, textAlign = TextAlign.Start, maxLines = 1, softWrap = false, modifier = Modifier.weight(1f))
                }
                Box(Modifier.width(2.dp).height(14.dp).background(accent.copy(alpha = .6f)))
            }
        }
        // where in the text it is
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(ink.copy(alpha = .12f))) {
            Box(Modifier.fillMaxWidth(if (total > 0) (at.toFloat() / total).coerceIn(0f, 1f) else 0f).height(4.dp).background(accent))
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).clickable { at = (at - 20).coerceAtLeast(0) }, contentAlignment = Alignment.Center) { Text("⏪", color = ink, fontSize = 18.sp) }
            Box(Modifier.size(56.dp).clip(CircleShape).background(accent.copy(alpha = .2f)).clickable {
                if (at >= total - 1) at = 0
                playing = !playing
            }, contentAlignment = Alignment.Center) { PIcon(if (playing) "pause" else "play", ink, 26.dp) }
            Box(Modifier.size(44.dp).clip(CircleShape).clickable { at = (at + 20).coerceAtMost((total - 1).coerceAtLeast(0)) }, contentAlignment = Alignment.Center) { Text("⏩", color = ink, fontSize = 18.sp) }
        }
        Spacer(Modifier.height(8.dp))
        PSlider(wpm.toFloat(), { wpm = ((it / 25f).roundToInt() * 25).coerceIn(100, 1000) }, 100f..1000f, onDone = { rsvpPrefs(ctx).edit().putInt("wpm", wpm).apply() })
        Text(tr("A tap on the word stops and starts it.", "Kelimeye dokunmak durdurur ve başlatır."), color = sub, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
    }
}
