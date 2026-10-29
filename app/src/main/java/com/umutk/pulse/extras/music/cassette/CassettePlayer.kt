package com.umutk.pulse.extras.music.cassette

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.Eject
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.VolumeUp
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PIcon
import com.umutk.pulse.extras.music.MusicPlayerEngine
import com.umutk.pulse.extras.music.PlaybackState
import com.umutk.pulse.extras.music.Track
import com.umutk.pulse.tr
import kotlinx.coroutines.delay

private val YELLOW = Color(0xFFF9C513)
private val INK = Color(0xFF16130A)
private val PAPER = Color(0xFFF4EFDC)
private val KEY = Color(0xFF2A2A2A)
private val DECK = Color(0xFF3C4147)

/**
 * The cassette look: a yellow tape deck. The cassette in the middle turns its two reels while the music plays (the tape moves from the left reel to the right one
 * as the track goes on), the label carries the song and the artist, and the keys are the keys of a deck: play / pause (red while it plays), fast forward and rewind
 * (a tap goes to the next and the previous song, a hold winds inside the song), and eject, which opens the tape shelf (the library) to put another tape in.
 * The idea of the look comes from the cassette players of the 80s and from "Bionic Tape"; everything here is drawn by Pulse.
 */
@Composable
fun CassettePlayer(engine: MusicPlayerEngine, st: PlaybackState, library: List<Track>, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    var shelf by remember { mutableStateOf(false) }
    var pos by remember { mutableIntStateOf(0) }
    var dur by remember { mutableIntStateOf(0) }
    var volume by remember { mutableFloatStateOf(engine.player.volume) }
    var shuffle by remember { mutableStateOf(engine.player.shuffleModeEnabled) }
    var repeat by remember { mutableStateOf(engine.player.repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF) }
    LaunchedEffect(st.current, st.isPlaying) {
        while (true) { pos = engine.positionMs; dur = engine.durationMs; delay(if (st.isPlaying) 200L else 800L) }
    }
    val turn by rememberInfiniteTransition(label = "reel").animateFloat(0f, 360f, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart), label = "turn")
    val spin = if (st.isPlaying) turn else 0f
    val progress = if (dur > 0) (pos / dur.toFloat()).coerceIn(0f, 1f) else 0f
    Box(modifier.background(YELLOW)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text("PULSE TAPE", color = INK, fontSize = 30.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, letterSpacing = (-1).sp)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Text("SPORT", color = YELLOW, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, modifier = Modifier.background(INK).padding(horizontal = 8.dp, vertical = 5.dp))
                Text("STEREO CASSETTE PLAYER", color = INK, fontSize = 11.sp, letterSpacing = 1.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(start = 8.dp))
            }
            // the cassette
            Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                Cassette(st.current, spin, progress, Modifier.fillMaxWidth().aspectRatio(1.6f))
            }
            // time and a seek line
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(clock(pos), color = INK, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                Box(Modifier.weight(1f).padding(horizontal = 10.dp).height(28.dp).pointerInput(dur) {
                    detectTapGestures { o -> if (dur > 0) engine.seekTo((o.x / size.width * dur).toInt()) }
                }.pointerInput(dur) {
                    detectHorizontalDragGestures { c, _ -> if (dur > 0) engine.seekTo((c.position.x / size.width * dur).toInt().coerceIn(0, dur)) }
                }, contentAlignment = Alignment.CenterStart) {
                    Box(Modifier.fillMaxWidth().height(4.dp).background(INK.copy(alpha = .25f), RoundedCornerShape(2.dp)))
                    Box(Modifier.fillMaxWidth(progress).height(4.dp).background(INK, RoundedCornerShape(2.dp)))
                }
                Text(clock(dur), color = INK, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
            Spacer(Modifier.height(10.dp))
            // the keys of the deck
            Row(Modifier.fillMaxWidth().background(DECK, RoundedCornerShape(14.dp)).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                DeckKey("prev", Modifier.weight(1f), onClick = { engine.previous() })
                DeckKey(if (st.isPlaying) "pause" else "play", Modifier.weight(1.3f), red = st.isPlaying, onClick = { if (st.queue.isEmpty()) shelf = true else engine.togglePlayPause() })
                DeckKey("next", Modifier.weight(1f), onClick = { engine.next() })
                DeckKey("eject", Modifier.weight(1f), onClick = { shelf = true })
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Toggle("shuffle", shuffle) { shuffle = !shuffle; engine.player.shuffleModeEnabled = shuffle }
                Toggle("repeat", repeat) { repeat = !repeat; engine.player.repeatMode = if (repeat) androidx.media3.common.Player.REPEAT_MODE_ALL else androidx.media3.common.Player.REPEAT_MODE_OFF }
                Spacer(Modifier.weight(1f))
                androidx.compose.material3.Icon(deckVec("volume"), null, Modifier.size(20.dp), tint = INK)
                Box(Modifier.width(140.dp).height(34.dp).pointerInput(Unit) { detectTapGestures { o -> volume = (o.x / size.width).coerceIn(0f, 1f); engine.player.volume = volume } }
                    .pointerInput(Unit) { detectHorizontalDragGestures { c, _ -> volume = (c.position.x / size.width).coerceIn(0f, 1f); engine.player.volume = volume } }, contentAlignment = Alignment.CenterStart) {
                    Box(Modifier.fillMaxWidth().height(6.dp).background(INK.copy(alpha = .25f), RoundedCornerShape(3.dp)))
                    Box(Modifier.fillMaxWidth(volume).height(6.dp).background(INK, RoundedCornerShape(3.dp)))
                }
            }
        }
        if (shelf) TapeShelf(library, st, onPick = { i -> engine.playQueue(library, i); shelf = false }, onFiles = { shelf = false; onOpen() }, onClose = { shelf = false })
    }
}

private fun deckVec(n: String) = when (n) {
    "prev" -> androidx.compose.material.icons.Icons.Rounded.SkipPrevious
    "next" -> androidx.compose.material.icons.Icons.Rounded.SkipNext
    "play" -> androidx.compose.material.icons.Icons.Rounded.PlayArrow
    "pause" -> androidx.compose.material.icons.Icons.Rounded.Pause
    "eject" -> androidx.compose.material.icons.Icons.Rounded.Eject
    "shuffle" -> androidx.compose.material.icons.Icons.Rounded.Shuffle
    "repeat" -> androidx.compose.material.icons.Icons.Rounded.Repeat
    else -> androidx.compose.material.icons.Icons.Rounded.VolumeUp
}

private fun clock(ms: Int): String { val s = (ms / 1000).coerceAtLeast(0); return "%d:%02d".format(s / 60, s % 60) }

/** One key of the deck: a raised dark key with a light glyph; the play key turns red while the tape runs. */
@Composable
private fun DeckKey(icon: String, modifier: Modifier, red: Boolean = false, onClick: () -> Unit) {
    var down by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier.height(64.dp).padding(top = if (down) 3.dp else 0.dp).shadow(if (down) 1.dp else 6.dp, shape).clip(shape)
            .background(Brush.verticalGradient(if (red) listOf(Color(0xFFEF4B3A), Color(0xFFC9302C)) else listOf(Color(0xFF3A3A3A), KEY)))
            .pointerInput(Unit) { detectTapGestures(onPress = { down = true; tryAwaitRelease(); down = false }, onTap = { onClick() }) },
        contentAlignment = Alignment.Center,
    ) { androidx.compose.material3.Icon(deckVec(icon), null, Modifier.size(30.dp), tint = if (red) Color.White else YELLOW) }
}

@Composable
private fun Toggle(icon: String, on: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(if (on) INK else INK.copy(alpha = .12f)).clickable(onClick = onClick), contentAlignment = Alignment.Center) { androidx.compose.material3.Icon(deckVec(icon), null, Modifier.size(22.dp), tint = if (on) YELLOW else INK) }
}

/** The cassette drawn on a canvas: the cream body with its label strip, the window with the two reels, the tape between them. */
@Composable
private fun Cassette(track: Track?, spin: Float, progress: Float, modifier: Modifier) {
    BoxWithConstraints(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            // body
            drawRoundRect(PAPER, Offset.Zero, size, CornerRadius(h * .08f))
            drawRoundRect(INK, Offset.Zero, size, CornerRadius(h * .08f), style = Stroke(h * .012f))
            // the yellow stripe and the dark stripe on the label
            drawRect(YELLOW, Offset(w * .0f, h * .06f), Size(w, h * .05f))
            drawRect(INK, Offset(0f, h * .12f), Size(w, h * .018f))
            // the window with the reels
            val wx = w * .20f; val wy = h * .40f; val ww = w * .60f; val wh = h * .34f
            drawRoundRect(Color(0xFF6C6A4C), Offset(wx, wy), Size(ww, wh), CornerRadius(wh * .25f))
            val rl = Offset(wx + ww * .25f, wy + wh / 2f); val rr = Offset(wx + ww * .75f, wy + wh / 2f)
            // the tape: much on the left reel at the start, much on the right at the end
            val full = wh * .46f; val empty = wh * .22f
            val leftR = empty + (full - empty) * (1f - progress); val rightR = empty + (full - empty) * progress
            drawCircle(Color(0xFF7A4A2A), leftR, rl); drawCircle(Color(0xFF7A4A2A), rightR, rr)
            reel(rl, wh * .20f, spin); reel(rr, wh * .20f, spin)
            // the lower edge with its two holes
            drawRoundRect(INK.copy(alpha = .85f), Offset(w * .30f, h * .82f), Size(w * .40f, h * .10f), CornerRadius(h * .03f))
            drawCircle(PAPER, h * .018f, Offset(w * .36f, h * .87f)); drawCircle(PAPER, h * .018f, Offset(w * .64f, h * .87f))
        }
        // the label: song and artist, in a hand-written looking face
        Column(Modifier.align(Alignment.TopStart).padding(start = maxWidth * .06f, top = maxHeight * .17f).width(maxWidth * .88f)) {
            Text(track?.title?.ifBlank { null } ?: track?.path?.substringAfterLast('/')?.substringBeforeLast('.') ?: tr("Insert a tape", "Bir kaset tak"),
                color = INK, fontSize = 22.sp, fontFamily = FontFamily.Cursive, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text((track?.artist ?: tr("Press eject", "Eject'e bas")).uppercase(), color = INK.copy(alpha = .75f), fontSize = 10.sp, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("D-C90", color = Color(0xFFD9392B), fontSize = 14.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, modifier = Modifier.align(Alignment.BottomStart).padding(start = maxWidth * .05f, bottom = maxHeight * .05f))
        Text("TYPE I · NORMAL", color = INK.copy(alpha = .6f), fontSize = 9.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.align(Alignment.BottomEnd).padding(end = maxWidth * .05f, bottom = maxHeight * .06f))
    }
}

/** A reel: a white ring with six teeth around a dark hub, turned by [angle]. */
private fun DrawScope.reel(c: Offset, r: Float, angle: Float) {
    drawCircle(Color.White, r * 1.35f, c); drawCircle(INK.copy(alpha = .35f), r * 1.35f, c, style = Stroke(r * .12f))
    rotate(angle, c) {
        drawCircle(INK, r * .75f, c)
        for (i in 0 until 6) {
            rotate(i * 60f, c) { drawRect(Color.White, Offset(c.x - r * .09f, c.y - r * .75f), Size(r * .18f, r * .32f)) }
        }
        drawCircle(Color.White, r * .38f, c); drawCircle(INK, r * .2f, c)
    }
}

/** The tape shelf: the songs of the library as tapes to put in; "files" opens the file picker for songs that are not in the library. */
@Composable
private fun TapeShelf(library: List<Track>, st: PlaybackState, onPick: (Int) -> Unit, onFiles: () -> Unit, onClose: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Color(0xF2121212)).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Tapes", "Kasetler") + " · ${library.size}", color = YELLOW, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Box(Modifier.size(40.dp).clickable(onClick = onFiles), contentAlignment = Alignment.Center) { PIcon("folder", YELLOW, 22.dp) }
            Box(Modifier.size(40.dp).clickable(onClick = onClose), contentAlignment = Alignment.Center) { PIcon("close", Color.White, 22.dp) }
        }
        LazyColumn(Modifier.fillMaxSize().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(library) { i, t ->
                val now = st.current?.path == t.path
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (now) YELLOW else PAPER).clickable { onPick(i) }.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(6.dp).height(34.dp).background(if (now) INK else YELLOW))
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(t.title?.ifBlank { null } ?: t.path.substringAfterLast('/').substringBeforeLast('.'), color = INK, fontSize = 17.sp, fontFamily = FontFamily.Cursive, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text((t.artist ?: "").uppercase(), color = INK.copy(alpha = .65f), fontSize = 10.sp, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (now) PIcon("play", INK, 20.dp)
                }
            }
        }
    }
}
