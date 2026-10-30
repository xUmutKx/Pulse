package com.umutk.pulse.extras.music.cassette

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot

class IpodTheme(val id: String, val en: String, val tr: String, val body: Color, val wheel: Color, val wheelInk: Color, val bg: Color)
val IPOD_THEMES = listOf(
    IpodTheme("white", "iPod white", "iPod beyaz", Color(0xFFF2F2F0), Color(0xFFFFFFFF), Color(0xFF8A8A8A), Color(0xFF1B1B1B)),
    IpodTheme("black", "iPod black", "iPod siyah", Color(0xFF1E1E1F), Color(0xFF2E2E30), Color(0xFFCFCFCF), Color(0xFF000000)),
    IpodTheme("silver", "iPod silver", "iPod gümüş", Color(0xFFC9CCD0), Color(0xFFE8EAEC), Color(0xFF6E7378), Color(0xFF14181C)),
    IpodTheme("pink", "iPod pink", "iPod pembe", Color(0xFFF2A7C6), Color(0xFFFFF1F6), Color(0xFFB5688A), Color(0xFF1B1B1B)),
    IpodTheme("blue", "iPod blue", "iPod mavi", Color(0xFF8DB7E8), Color(0xFFF1F6FD), Color(0xFF4A78B0), Color(0xFF1B1B1B)),
)

/**
 * The iPod look: a classic click-wheel player. The screen shows the song, the artist, the time and a progress bar; the wheel works like the original: the four
 * labels at its edge are Menu (the song list), Previous, Next and Play / Pause, the centre selects, and turning a finger round the ring moves in the list, or
 * changes the volume on the playing screen. Drawn by Pulse after the look of the early iPods.
 */
@Composable
fun IpodPlayer(engine: MusicPlayerEngine, st: PlaybackState, library: List<Track>, onOpen: () -> Unit, modifier: Modifier = Modifier, variant: String = "white") {
    val th = IPOD_THEMES.firstOrNull { it.id == variant } ?: IPOD_THEMES[0]
    var menu by remember { mutableStateOf(false) }
    var sel by remember { mutableIntStateOf(0) }
    var pos by remember { mutableIntStateOf(0) }
    var dur by remember { mutableIntStateOf(0) }
    var vol by remember { mutableStateOf(engine.player.volume) }
    var volShown by remember { mutableStateOf(0L) }
    LaunchedEffect(st.current, st.isPlaying) { while (true) { pos = engine.positionMs; dur = engine.durationMs; delay(if (st.isPlaying) 250L else 900L) } }
    LaunchedEffect(menu, st.index) { if (menu) sel = st.index.coerceAtLeast(0) }
    fun step(n: Int) {
        if (menu) sel = (sel + n).coerceIn(0, (library.size - 1).coerceAtLeast(0))
        else { vol = (vol + n * 0.03f).coerceIn(0f, 1f); engine.player.volume = vol; volShown = System.currentTimeMillis() }
    }
    Column(modifier.background(Color(0xFF101010)).padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        // the body
        Column(Modifier.weight(1f).fillMaxWidth().shadow(10.dp, RoundedCornerShape(26.dp)).clip(RoundedCornerShape(26.dp)).background(Brush.verticalGradient(listOf(th.body, th.body.copy(alpha = .94f)))).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            // the screen
            Column(Modifier.fillMaxWidth().height(210.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFE4EBE0)).border(3.dp, th.wheelInk.copy(alpha = .5f), RoundedCornerShape(8.dp))) {
                Row(Modifier.fillMaxWidth().background(Color(0xFFB9C7B4)).padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (menu) tr("Songs", "Şarkılar") else "iPod", color = Color(0xFF1B1B1B), fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(if (st.isPlaying) "▶" else "❚❚", color = Color(0xFF1B1B1B), fontSize = 11.sp)
                }
                if (menu) {
                    val scroll = rememberScrollState()
                    Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                        library.forEachIndexed { i, t ->
                            Text(t.title?.ifBlank { null } ?: t.path.substringAfterLast('/').substringBeforeLast('.'), color = if (i == sel) Color.White else Color(0xFF1B1B1B), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth().background(if (i == sel) Color(0xFF3B7BD8) else Color.Transparent).padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                    }
                    LaunchedEffect(sel) { scroll.animateScrollTo((sel * 27 - 60).coerceAtLeast(0)) }
                } else Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(st.current?.title?.ifBlank { null } ?: st.current?.path?.substringAfterLast('/')?.substringBeforeLast('.') ?: tr("No song", "Şarkı yok"), color = Color(0xFF1B1B1B), fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(st.current?.artist ?: "", color = Color(0xFF3A3A3A), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(st.current?.album ?: "", color = Color(0xFF5A5A5A), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Column {
                        if (System.currentTimeMillis() - volShown < 1500) {
                            Text(tr("Volume", "Ses") + " ${(vol * 100).toInt()}%", color = Color(0xFF1B1B1B), fontSize = 11.sp)
                        }
                        Box(Modifier.fillMaxWidth().height(10.dp).border(1.dp, Color(0xFF1B1B1B)).padding(1.dp)) {
                            Box(Modifier.fillMaxWidth(if (dur > 0) (pos / dur.toFloat()).coerceIn(0f, 1f) else 0f).fillMaxSize().background(Color(0xFF3B7BD8)))
                        }
                        Row(Modifier.fillMaxWidth()) {
                            Text(clk(pos), color = Color(0xFF1B1B1B), fontSize = 11.sp, modifier = Modifier.weight(1f))
                            Text("-" + clk((dur - pos).coerceAtLeast(0)), color = Color(0xFF1B1B1B), fontSize = 11.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            // the click wheel
            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val side = minOf(maxWidth, 300.dp)
                var lastAngle by remember { mutableStateOf<Float?>(null) }
                var acc by remember { mutableStateOf(0f) }
                Box(Modifier.size(side).clip(CircleShape).background(th.wheel).pointerInput(menu, library.size) {
                    detectDragGestures(onDragStart = { lastAngle = null; acc = 0f }, onDragEnd = { lastAngle = null }) { c, _ ->
                        val ctr = Offset(size.width / 2f, size.height / 2f)
                        val d = c.position - ctr
                        if (hypot(d.x, d.y) < size.width * .30f) return@detectDragGestures
                        val a = (atan2(d.y, d.x) * 180f / PI.toFloat())
                        lastAngle?.let { p ->
                            var delta = a - p; if (delta > 180f) delta -= 360f; if (delta < -180f) delta += 360f
                            acc += delta
                            while (acc >= 22f) { step(1); acc -= 22f }
                            while (acc <= -22f) { step(-1); acc += 22f }
                        }
                        lastAngle = a
                    }
                }.pointerInput(menu, sel, library.size) {
                    detectTapGestures { o ->
                        val ctr = Offset(size.width / 2f, size.height / 2f); val d = o - ctr; val r = hypot(d.x, d.y)
                        when {
                            r < size.width * .20f -> {
                                // the centre: choose the song under the bar, or open the list when the screen shows the song
                                if (menu) { if (library.isNotEmpty()) { engine.playQueue(library, sel); menu = false } } else if (st.queue.isEmpty()) onOpen() else engine.togglePlayPause()
                            }
                            r > size.width * .20f && kotlin.math.abs(d.y) > kotlin.math.abs(d.x) -> if (d.y < 0) menu = !menu else engine.togglePlayPause()
                            r > size.width * .20f -> if (d.x < 0) engine.previous() else engine.next()
                        }
                    }
                }, contentAlignment = Alignment.Center) {
                    Text("MENU", color = th.wheelInk, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.TopCenter).padding(top = side * .07f))
                    Text("⏮", color = th.wheelInk, fontSize = 20.sp, modifier = Modifier.align(Alignment.CenterStart).padding(start = side * .08f))
                    Text("⏭", color = th.wheelInk, fontSize = 20.sp, modifier = Modifier.align(Alignment.CenterEnd).padding(end = side * .08f))
                    Text("▶❚❚", color = th.wheelInk, fontSize = 15.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = side * .08f))
                    Box(Modifier.size(side * .38f).clip(CircleShape).background(th.body).border(1.dp, th.wheelInk.copy(alpha = .25f), CircleShape))
                }
            }
        }
    }
}

private fun clk(ms: Int): String { val s = (ms / 1000).coerceAtLeast(0); return "%d:%02d".format(s / 60, s % 60) }
