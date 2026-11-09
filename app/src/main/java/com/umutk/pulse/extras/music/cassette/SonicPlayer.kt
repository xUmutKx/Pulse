package com.umutk.pulse.extras.music.cassette

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.extras.music.MusicPlayerEngine
import com.umutk.pulse.extras.music.PlaybackState
import com.umutk.pulse.extras.music.Track
import com.umutk.pulse.extras.music.rememberTrackArt
import com.umutk.pulse.tr
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private val SonicOrange = Color(0xFFF05A24)
private val LedRed = Color(0xFFFF3B30)
private val Glow = Color(0xFFF2F2F2)

/**
 * The Sonic look (after Abhiroop1122/Sonic, MIT): a deck drawn in glowing white lines on black, the record on the platter wearing the
 * album's cover, the tonearm travelling across it with the song, a panel of keys (previous, next, play/stop, eject) and red LED windows
 * for the title, the track and the time; under it the albums as a row of sleeves, the album's title and its tracks. All drawn in 2D here
 * (Sonic renders its deck in 3D).
 */
@Composable
fun SonicPlayer(engine: MusicPlayerEngine, st: PlaybackState, library: List<Track>, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    var pos by remember { mutableIntStateOf(0) }
    var dur by remember { mutableIntStateOf(0) }
    LaunchedEffect(st.current, st.isPlaying) { while (true) { pos = engine.positionMs; dur = engine.durationMs; delay(if (st.isPlaying) 250L else 900L) } }
    val turn by rememberInfiniteTransition(label = "platter").animateFloat(0f, 360f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart), label = "spin")
    val progress = if (dur > 0) (pos / dur.toFloat()).coerceIn(0f, 1f) else 0f
    // the arm swings in when a record plays, and back to its rest when it stops
    val arm by animateFloatAsState(if (st.isPlaying || pos > 0) 1f else 0f, tween(700), label = "arm")
    val cur = st.current
    val art = rememberTrackArt(cur)
    val albums = remember(library) { library.groupBy { it.album ?: "?" }.entries.map { it.key to it.value }.sortedBy { it.first.lowercase() } }
    val albumOf = cur?.album ?: "?"
    val tracks = remember(st.queue, albumOf) { st.queue.filter { (it.album ?: "?") == albumOf }.ifEmpty { st.queue } }
    Column(modifier.background(Color.Black).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        // the top line: side and track, the record count
        Row(Modifier.fillMaxWidth().padding(top = 40.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (st.isPlaying) "▶ " else "■ ", color = Color(0xFF34C759), fontSize = 14.sp)
            Text("side A  A${(st.index + 1).coerceAtLeast(1)} · ${(st.index + 1).coerceAtLeast(0)} / ${st.queue.size}", color = Glow.copy(alpha = .8f), fontSize = 14.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
            Text("${albums.size} rec", color = Glow.copy(alpha = .8f), fontSize = 14.sp, fontFamily = FontFamily.Monospace)
        }
        // the deck
        BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(1.62f)) {
            val w = constraints.maxWidth.toFloat(); val h = constraints.maxHeight.toFloat()
            Canvas(Modifier.fillMaxSize().pointerInput(st.queue.isEmpty()) {
                detectTapGestures { o ->
                    // keys of the right panel; a tap on the record plays or stops
                    val px = o.x / size.width; val py = o.y / size.height
                    when {
                        px > .62f && py < .30f -> if (px < .79f) engine.previous() else engine.next()
                        px > .62f && py < .58f -> if (st.queue.isEmpty()) onOpen() else engine.togglePlayPause()
                        px > .62f && py < .74f -> onOpen()
                        px < .60f && py < .80f -> if (st.queue.isEmpty()) onOpen() else engine.togglePlayPause()
                    }
                }
            }) { drawDeck(art, if (st.isPlaying) turn else 0f, progress, arm, st.isPlaying) }
            // the LED windows
            val d = LocalDensity.current
            with(d) {
                Led((cur?.title ?: tr("no record", "plak yok")).uppercase(), Modifier.offset((w * .07f).toDp(), (h * .82f).toDp()).size((w * .50f).toDp(), (h * .10f).toDp()))
                Led("A${(st.index + 1).coerceAtLeast(1)}   " + mmss(pos), Modifier.offset((w * .64f).toDp(), (h * .82f).toDp()).size((w * .30f).toDp(), (h * .10f).toDp()), right = true)
            }
        }
        Text("33⅓ rpm", color = Glow.copy(alpha = .5f), fontSize = 13.sp, fontFamily = FontFamily.Monospace, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
        // the crate: the albums as sleeves, the one in the middle facing you
        if (albums.isNotEmpty()) {
            val ls = rememberLazyListState(albums.indexOfFirst { it.first == albumOf }.coerceAtLeast(0))
            BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 18.dp)) {
                val item = 150.dp
                val side = (maxWidth - item) / 2
                LazyRow(state = ls, flingBehavior = rememberSnapFlingBehavior(ls), contentPadding = PaddingValues(horizontal = side), horizontalArrangement = Arrangement.spacedBy((-28).dp)) {
                    itemsIndexed(albums, key = { _, a -> a.first }) { i, (name, list) ->
                        val info = ls.layoutInfo
                        val it0 = info.visibleItemsInfo.firstOrNull { it.index == i }
                        val center = info.viewportStartOffset + info.viewportSize.width / 2f
                        val off = it0?.let { (it.offset + it.size / 2f - center) / it.size } ?: 0f
                        val cover = rememberTrackArt(list.first())
                        Box(Modifier.size(item).graphicsLayer {
                            rotationY = (-off * 38f).coerceIn(-60f, 60f); val s = 1f - (abs(off) * .14f).coerceAtMost(.3f); scaleX = s; scaleY = s
                            cameraDistance = 12f * density; alpha = 1f - (abs(off) * .25f).coerceAtMost(.6f)
                        }.clip(RoundedCornerShape(3.dp)).background(Color(0xFF1A1A1A)).border(1.dp, Glow.copy(alpha = .4f), RoundedCornerShape(3.dp))
                            .clickable { engine.playQueue(list, 0) }) {
                            if (cover != null) androidx.compose.foundation.Image(cover, name, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                            else Text(name, color = Glow, fontSize = 12.sp, modifier = Modifier.align(Alignment.Center).padding(8.dp), textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
        Text((cur?.album ?: cur?.title ?: tr("put a record on", "bir plak tak")).lowercase(), color = Glow, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 14.dp))
        Text((cur?.artist ?: "").lowercase(), color = Glow.copy(alpha = .6f), fontSize = 14.sp, maxLines = 1)
        cur?.let { t ->
            val ext = t.path.substringAfterLast('.', "").lowercase().ifEmpty { "audio" }
            Text(listOf(ext, mmss(dur)).joinToString(" · "), color = Glow, fontSize = 13.sp, fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 8.dp).border(1.dp, Glow, RoundedCornerShape(2.dp)).padding(horizontal = 10.dp, vertical = 4.dp))
        }
        // the record's tracks: the one playing on a white strip
        Column(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 24.dp)) {
            tracks.forEachIndexed { i, t ->
                val playing = t.path == cur?.path
                Row(Modifier.fillMaxWidth().background(if (playing) Color.White else Color.Transparent).clickable { engine.playAt(st.queue.indexOf(t).coerceAtLeast(0)) }.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("a${i + 1}", color = SonicOrange, fontSize = 14.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(44.dp))
                    Text((t.title ?: t.path.substringAfterLast('/')).lowercase(), color = if (playing) Color.Black else Glow.copy(alpha = .75f), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (playing) Text(if (st.isPlaying) "playing" else "paused", color = Color.Black.copy(alpha = .6f), fontSize = 13.sp)
                }
            }
        }
    }
}

private fun mmss(ms: Int): String { val s = (ms / 1000).coerceAtLeast(0); return "%02d:%02d".format(s / 60, s % 60) }

/** A red LED window: dark glass, letters in a seven-segment red with a soft glow. */
@Composable
private fun Led(text: String, modifier: Modifier, right: Boolean = false) {
    Box(modifier.border(1.5.dp, Glow.copy(alpha = .85f), RoundedCornerShape(3.dp)).background(Color(0xFF0B0505)).padding(horizontal = 8.dp), contentAlignment = if (right) Alignment.CenterEnd else Alignment.CenterStart) {
        Text(text, color = LedRed, fontSize = 15.sp, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp, maxLines = 1, overflow = TextOverflow.Clip,
            style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(LedRed.copy(alpha = .8f), Offset.Zero, 10f)))
    }
}

/** The deck in glowing lines: plinth, platter with the record (the cover on it), tonearm, the key panel. */
private fun DrawScope.drawDeck(art: ImageBitmap?, spin: Float, progress: Float, arm: Float, playing: Boolean) {
    val w = size.width; val h = size.height
    fun glowRect(l: Float, t: Float, r: Float, b: Float, rad: Float = 6f, a: Float = 1f) {
        drawRoundRect(Glow.copy(alpha = .18f * a), Offset(l, t), Size(r - l, b - t), CornerRadius(rad), style = Stroke(9f))
        drawRoundRect(Glow.copy(alpha = a), Offset(l, t), Size(r - l, b - t), CornerRadius(rad), style = Stroke(2.4f))
    }
    // the plinth and its two halves
    glowRect(w * .02f, h * .04f, w * .98f, h * .97f, 10f)
    drawLine(Glow.copy(alpha = .8f), Offset(w * .60f, h * .04f), Offset(w * .60f, h * .97f), 2f)
    // the platter and the record
    val c = Offset(w * .30f, h * .42f); val r = h * .36f
    drawCircle(Glow.copy(alpha = .15f), r * 1.04f, c, style = Stroke(10f))
    drawCircle(Color(0xFF8E8E93), r * 1.02f, c, style = Stroke(2.5f))
    rotate(spin, c) {
        drawCircle(Color(0xFF1C1C1E), r, c)
        for (k in 0..18) drawCircle(Glow.copy(alpha = if (k % 6 == 0) .16f else .06f), r * (.45f + k * .03f), c, style = Stroke(1.2f))
        if (art != null) {
            // the cover as the record's face, round (the record's own shape), its centre square cropped from any picture
            val s = r * 1.12f
            val m = minOf(art.width, art.height)
            val path = androidx.compose.ui.graphics.Path().apply { addOval(androidx.compose.ui.geometry.Rect(c, r * .98f)) }
            clipPath(path) {
                drawImage(art, srcOffset = IntOffset((art.width - m) / 2, (art.height - m) / 2), srcSize = IntSize(m, m),
                    dstOffset = IntOffset((c.x - s / 2).toInt(), (c.y - s / 2).toInt()), dstSize = IntSize(s.toInt(), s.toInt()), alpha = .92f)
            }
        } else drawCircle(SonicOrange, r * .34f, c)
        drawLine(Glow.copy(alpha = .6f), Offset(c.x, c.y - r * .9f), Offset(c.x, c.y - r * .75f), 3f, StrokeCap.Round)
    }
    drawCircle(Glow, r * .045f, c)
    // the tonearm: its base at the top right of the platter, swinging in with the song
    val pivot = Offset(w * .55f, h * .12f)
    drawCircle(Glow.copy(alpha = .2f), h * .075f, pivot, style = Stroke(8f)); drawCircle(Glow, h * .075f, pivot, style = Stroke(2.2f)); drawCircle(Glow, h * .035f, pivot, style = Stroke(2.2f))
    val rest = Math.toRadians(100.0); val start = Math.toRadians(122.0); val end = Math.toRadians(142.0)
    val ang = rest + (start - rest) * arm + (end - start) * progress * arm
    val len = h * .70f
    val head = Offset(pivot.x + (cos(ang) * len).toFloat(), pivot.y + (sin(ang) * len).toFloat())
    drawLine(Glow.copy(alpha = .2f), pivot, head, 9f, StrokeCap.Round)
    drawLine(Glow, pivot, head, 3f, StrokeCap.Round)
    rotate(Math.toDegrees(ang).toFloat() - 90f, head) { glowRect(head.x - h * .03f, head.y - h * .01f, head.x + h * .03f, head.y + h * .06f, 4f) }
    drawCircle(Glow, h * .02f, Offset(w * .55f, h * .62f), style = Stroke(2f))
    // the key panel: previous, next, play/stop, eject
    val l = w * .64f; val rr = w * .95f
    glowRect(l, h * .10f, rr, h * .74f, 6f)
    drawLine(Glow, Offset(l, h * .30f), Offset(rr, h * .30f), 2f); drawLine(Glow, Offset((l + rr) / 2, h * .10f), Offset((l + rr) / 2, h * .30f), 2f)
    drawLine(Glow, Offset(l, h * .58f), Offset(rr, h * .58f), 2f)
    val k1 = Offset((l + (l + rr) / 2) / 2, h * .20f); val k2 = Offset(((l + rr) / 2 + rr) / 2, h * .20f); val ks = h * .04f
    // |◀  ▶|
    drawLine(Glow, Offset(k1.x - ks, k1.y - ks), Offset(k1.x - ks, k1.y + ks), 3f)
    drawPath(androidx.compose.ui.graphics.Path().apply { moveTo(k1.x + ks, k1.y - ks); lineTo(k1.x - ks * .6f, k1.y); lineTo(k1.x + ks, k1.y + ks); close() }, Glow)
    drawLine(Glow, Offset(k2.x + ks, k2.y - ks), Offset(k2.x + ks, k2.y + ks), 3f)
    drawPath(androidx.compose.ui.graphics.Path().apply { moveTo(k2.x - ks, k2.y - ks); lineTo(k2.x + ks * .6f, k2.y); lineTo(k2.x - ks, k2.y + ks); close() }, Glow)
    // ▶ ■ (the one in use is bright)
    val pc = Offset((l + rr) / 2, h * .44f); val ps = h * .055f
    drawPath(androidx.compose.ui.graphics.Path().apply { moveTo(pc.x - ps * 2.1f, pc.y - ps); lineTo(pc.x - ps * .3f, pc.y); lineTo(pc.x - ps * 2.1f, pc.y + ps); close() }, if (playing) Glow else Glow.copy(alpha = .45f))
    drawRect(if (playing) Glow.copy(alpha = .45f) else Glow, Offset(pc.x + ps * .4f, pc.y - ps), Size(ps * 1.7f, ps * 2f))
    // ⏏
    val ec = Offset((l + rr) / 2, h * .66f); val es = h * .035f
    drawPath(androidx.compose.ui.graphics.Path().apply { moveTo(ec.x - es * 1.3f, ec.y + es * .2f); lineTo(ec.x, ec.y - es); lineTo(ec.x + es * 1.3f, ec.y + es * .2f); close() }, Glow)
    drawLine(Glow, Offset(ec.x - es * 1.3f, ec.y + es * .8f), Offset(ec.x + es * 1.3f, ec.y + es * .8f), 3f)
    // the little status lamp and the name plate
    drawCircle(if (playing) SonicOrange else Color(0xFF5A3A2A), h * .012f, Offset(l + w * .02f, h * .065f))
}
