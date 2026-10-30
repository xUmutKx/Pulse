package com.umutk.pulse.extras.music.cassette

import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.extras.music.MusicPlayerEngine
import com.umutk.pulse.extras.music.PlaybackState
import com.umutk.pulse.extras.music.Track
import com.umutk.pulse.tr
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

class TurntableTheme(val id: String, val en: String, val tr: String, val base: Color, val baseDark: Color, val label: Color, val metal: Color)
val TURNTABLE_THEMES = listOf(
    TurntableTheme("walnut", "Turntable walnut", "Pikap ceviz", Color(0xFF8A5A33), Color(0xFF5A3820), Color(0xFFE0A33A), Color(0xFFC9C9CC)),
    TurntableTheme("black", "Turntable black", "Pikap siyah", Color(0xFF2B2B2D), Color(0xFF151516), Color(0xFFD9392B), Color(0xFFB9B9BD)),
    TurntableTheme("white", "Turntable white", "Pikap beyaz", Color(0xFFEDEBE6), Color(0xFFCFCBC2), Color(0xFF2F6FDB), Color(0xFF9A9A9E)),
)

/**
 * The turntable look: a record on a platter that turns while the music plays (33 turns a minute, slowed to a stop when paused), a label with the song, and a
 * tonearm that moves in from the rim towards the centre as the song goes on. A tap on the record plays or pauses, and the keys below go to the previous and
 * next song or open the library to pick a record. Drawn by Pulse.
 */
@Composable
fun TurntablePlayer(engine: MusicPlayerEngine, st: PlaybackState, library: List<Track>, onOpen: () -> Unit, modifier: Modifier = Modifier, variant: String = "walnut") {
    val th = TURNTABLE_THEMES.firstOrNull { it.id == variant } ?: TURNTABLE_THEMES[0]
    var pos by remember { mutableIntStateOf(0) }
    var dur by remember { mutableIntStateOf(0) }
    var list by remember { mutableStateOf(false) }
    LaunchedEffect(st.current, st.isPlaying) { while (true) { pos = engine.positionMs; dur = engine.durationMs; delay(if (st.isPlaying) 250L else 900L) } }
    val turn by rememberInfiniteTransition(label = "disc").animateFloat(0f, 360f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart), label = "t")
    // the arm lifts to its rest when nothing plays, and sits on the record otherwise
    val progress = if (dur > 0) (pos / dur.toFloat()).coerceIn(0f, 1f) else 0f
    val armAngle = if (st.isPlaying || pos > 0) 8f + 22f * progress else -6f
    val spin = if (st.isPlaying) turn else 0f
    val ink = if (th.base.luminance() > .5f) Color(0xFF1B1B1B) else Color.White
    Column(modifier.background(Brush.verticalGradient(listOf(th.base, th.baseDark))).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(st.current?.title?.ifBlank { null } ?: st.current?.path?.substringAfterLast('/')?.substringBeforeLast('.') ?: tr("Put a record on", "Bir plak tak"), color = ink, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text((st.current?.artist ?: "").uppercase(), color = ink.copy(alpha = .7f), fontSize = 11.sp, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp, maxLines = 1, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxWidth().aspectRatio(1f).pointerInput(Unit) { detectTapGestures { if (st.queue.isEmpty()) list = true else engine.togglePlayPause() } }) {
                val s = size.minDimension; val c = Offset(size.width * .46f, size.height * .52f); val r = s * .40f
                // the platter and the record
                drawCircle(th.metal, r * 1.07f, c)
                drawCircle(Color(0xFF0C0C0D), r, c)
                for (k in 1..9) drawCircle(Color.White.copy(alpha = .05f), r * (0.42f + k * .055f), c, style = Stroke(1f))
                rotate(spin, c) {
                    // a shine that turns with the record, so the turning can be seen
                    drawArc(Color.White.copy(alpha = .10f), -20f, 40f, true, Offset(c.x - r, c.y - r), androidx.compose.ui.geometry.Size(r * 2, r * 2))
                    drawArc(Color.White.copy(alpha = .10f), 160f, 40f, true, Offset(c.x - r, c.y - r), androidx.compose.ui.geometry.Size(r * 2, r * 2))
                    drawCircle(th.label, r * .36f, c)
                    drawCircle(Color.Black.copy(alpha = .25f), r * .36f, c, style = Stroke(2f))
                    drawLine(Color.White.copy(alpha = .55f), Offset(c.x, c.y - r * .30f), Offset(c.x, c.y - r * .12f), 4f, StrokeCap.Round)
                }
                drawCircle(th.metal, r * .045f, c)
                // the tonearm: pivot at the top right, the head swings over the record
                val pivot = Offset(size.width * .86f, size.height * .12f)
                drawCircle(th.metal, s * .045f, pivot); drawCircle(th.baseDark, s * .02f, pivot)
                val len = s * .62f
                val a = Math.toRadians((100.0 + armAngle * 1.9))
                val head = Offset(pivot.x + (cos(a) * len).toFloat(), pivot.y + (sin(a) * len).toFloat())
                drawLine(th.metal, pivot, head, s * .017f, StrokeCap.Round)
                drawCircle(th.metal, s * .02f, head)
            }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            @Composable fun key(icon: androidx.compose.ui.graphics.vector.ImageVector, big: Boolean = false, onClick: () -> Unit) =
                Box(Modifier.size(if (big) 64.dp else 48.dp).clip(CircleShape).background(ink.copy(alpha = if (big) .22f else .12f)).clickable(onClick = onClick), contentAlignment = Alignment.Center) { Icon(icon, null, tint = ink, modifier = Modifier.size(if (big) 34.dp else 26.dp)) }
            key(Icons.Rounded.LibraryMusic) { list = true }
            key(Icons.Rounded.SkipPrevious) { engine.previous() }
            key(if (st.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, big = true) { if (st.queue.isEmpty()) list = true else engine.togglePlayPause() }
            key(Icons.Rounded.SkipNext) { engine.next() }
        }
        if (list) androidx.compose.ui.window.Dialog(onDismissRequest = { list = false }) {
            Column(Modifier.clip(RoundedCornerShape(16.dp)).background(th.baseDark).padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Records", "Plaklar") + " · ${library.size}", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(tr("Files", "Dosyalar"), color = Color.White.copy(alpha = .8f), fontSize = 14.sp, modifier = Modifier.clickable { list = false; onOpen() }.padding(8.dp))
                }
                androidx.compose.foundation.lazy.LazyColumn(Modifier.padding(top = 6.dp).size(width = 320.dp, height = 420.dp)) {
                    itemsIndexed(library) { i, t ->
                        Text(t.title?.ifBlank { null } ?: t.path.substringAfterLast('/').substringBeforeLast('.'), color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().clickable { engine.playQueue(library, i); list = false }.padding(vertical = 10.dp, horizontal = 6.dp))
                    }
                }
            }
        }
    }
}

private fun Color.luminance() = 0.2126f * red + 0.7152f * green + 0.0722f * blue
