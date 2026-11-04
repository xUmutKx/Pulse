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
import androidx.compose.ui.graphics.compositeOver
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
                // the plinth edge: a soft shadow and a lighter rim under the platter
                drawCircle(Color.Black.copy(alpha = .35f), r * 1.14f, Offset(c.x + s * .012f, c.y + s * .016f))
                drawCircle(Brush.radialGradient(listOf(th.metal, th.metal.copy(alpha = .75f).compositeOver(Color.Black)), c, r * 1.1f), r * 1.1f, c)
                // the strobe dots of the platter rim (they turn with it)
                rotate(spin, c) { for (i in 0 until 60) { val an = Math.toRadians(i * 6.0); drawCircle(Color.Black.copy(alpha = .45f), s * .0045f, Offset(c.x + (cos(an) * r * 1.075f).toFloat(), c.y + (sin(an) * r * 1.075f).toFloat())) } }
                // the vinyl: black with a sheen, the lead-in ring, many fine grooves and the gaps between the tracks
                drawCircle(Brush.radialGradient(listOf(Color(0xFF1B1B1D), Color(0xFF070708)), c, r), r, c)
                for (k in 0..34) drawCircle(Color.White.copy(alpha = if (k % 7 == 0) .09f else .035f), r * (0.40f + k * .0165f), c, style = Stroke(1f))
                for (g in listOf(.52f, .66f, .79f, .90f)) drawCircle(Color.Black.copy(alpha = .8f), r * g, c, style = Stroke(s * .004f))
                drawCircle(Color.White.copy(alpha = .08f), r * .985f, c, style = Stroke(s * .006f))
                rotate(spin, c) {
                    // two soft light wedges turn with the record, so the turning can be seen
                    drawArc(Brush.sweepGradient(listOf(Color.White.copy(alpha = .14f), Color.Transparent), c), -22f, 44f, true, Offset(c.x - r, c.y - r), androidx.compose.ui.geometry.Size(r * 2, r * 2))
                    drawArc(Brush.sweepGradient(listOf(Color.White.copy(alpha = .14f), Color.Transparent), c), 158f, 44f, true, Offset(c.x - r, c.y - r), androidx.compose.ui.geometry.Size(r * 2, r * 2))
                    // the paper label: the colour, a ring of print, a lighter inner disc
                    drawCircle(th.label, r * .36f, c)
                    drawCircle(Color.White.copy(alpha = .18f), r * .30f, c, style = Stroke(s * .004f))
                    drawCircle(Color.Black.copy(alpha = .18f), r * .36f, c, style = Stroke(s * .004f))
                    for (i in 0 until 24) { val an = Math.toRadians(i * 15.0); drawLine(Color.Black.copy(alpha = .35f), Offset(c.x + (cos(an) * r * .25f).toFloat(), c.y + (sin(an) * r * .25f).toFloat()), Offset(c.x + (cos(an) * r * .275f).toFloat(), c.y + (sin(an) * r * .275f).toFloat()), s * .003f) }
                    drawLine(Color.White.copy(alpha = .55f), Offset(c.x, c.y - r * .20f), Offset(c.x, c.y - r * .08f), 4f, StrokeCap.Round)
                }
                // the spindle
                drawCircle(Brush.radialGradient(listOf(Color.White, th.metal), c, r * .05f), r * .05f, c)
                // the tonearm: a round base with a ring, the counterweight behind the pivot, the tube and the headshell with its cartridge
                val pivot = Offset(size.width * .86f, size.height * .12f)
                drawCircle(Color.Black.copy(alpha = .3f), s * .07f, Offset(pivot.x + s * .008f, pivot.y + s * .01f))
                drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = .7f), th.metal), pivot, s * .065f), s * .065f, pivot)
                drawCircle(th.baseDark, s * .036f, pivot); drawCircle(th.metal, s * .018f, pivot)
                val len = s * .62f
                val a = Math.toRadians((100.0 + armAngle * 1.9))
                val ux = cos(a).toFloat(); val uy = sin(a).toFloat()
                val head = Offset(pivot.x + ux * len, pivot.y + uy * len)
                val tail = Offset(pivot.x - ux * s * .13f, pivot.y - uy * s * .13f)
                drawLine(Color.Black.copy(alpha = .3f), Offset(pivot.x + 3f, pivot.y + 4f), Offset(head.x + 3f, head.y + 4f), s * .017f, StrokeCap.Round)
                drawLine(th.metal, pivot, head, s * .017f, StrokeCap.Round)
                drawLine(Color.White.copy(alpha = .5f), Offset(pivot.x - 1f, pivot.y - 1f), Offset(head.x - 1f, head.y - 1f), s * .004f, StrokeCap.Round)
                drawLine(Color(0xFF3A3A3D), pivot, tail, s * .05f, StrokeCap.Round)
                drawCircle(Color(0xFF55555A), s * .026f, tail)
                // the headshell and its small cartridge
                rotate(Math.toDegrees(a).toFloat() - 90f + 14f, head) {
                    drawRoundRect(Color(0xFF2A2A2C), Offset(head.x - s * .018f, head.y - s * .008f), androidx.compose.ui.geometry.Size(s * .036f, s * .06f), androidx.compose.ui.geometry.CornerRadius(s * .006f))
                    drawRect(th.label, Offset(head.x - s * .012f, head.y + s * .036f), androidx.compose.ui.geometry.Size(s * .024f, s * .012f))
                }
                // the rest post and a speed indicator lamp at the lower left
                drawRoundRect(th.metal, Offset(size.width * .92f, size.height * .78f), androidx.compose.ui.geometry.Size(s * .02f, s * .09f), androidx.compose.ui.geometry.CornerRadius(s * .01f))
                drawCircle(if (st.isPlaying) Color(0xFF5CFF7A) else Color(0xFF3A5A40), s * .014f, Offset(size.width * .06f, size.height * .93f))
                drawCircle(Color.White.copy(alpha = .35f), s * .014f, Offset(size.width * .06f, size.height * .93f), style = Stroke(1.5f))
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
