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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.compositeOver
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

/** The colours of one cassette deck: the body ([main], yellow by default), the dark ink, the cream label, the keys and the panel behind them. */
class CasTheme(val id: String, val en: String, val tr: String, val main: Color, val ink: Color, val paper: Color, val key: Color, val deck: Color, val key2: Color = key)
val CASSETTE_THEMES = listOf(
    CasTheme("yellow", "Cassette yellow", "Kaset sarı", Color(0xFFF9C513), Color(0xFF16130A), Color(0xFFF4EFDC), Color(0xFF2A2A2A), Color(0xFF3C4147)),
    CasTheme("red", "Cassette red", "Kaset kırmızı", Color(0xFFD9392B), Color(0xFF1A0D0B), Color(0xFFF6E9E2), Color(0xFF2A2020), Color(0xFF4A3836), Color(0xFF3A2A2A)),
    CasTheme("blue", "Cassette blue", "Kaset mavi", Color(0xFF2F6FDB), Color(0xFF0B1426), Color(0xFFE8EEF8), Color(0xFF1F2733), Color(0xFF34404F)),
    CasTheme("mint", "Cassette mint", "Kaset nane", Color(0xFF39B08F), Color(0xFF0B1F19), Color(0xFFE6F3EE), Color(0xFF1E2B27), Color(0xFF33443F)),
    CasTheme("pink", "Cassette pink", "Kaset pembe", Color(0xFFEF6FAE), Color(0xFF2A0F1E), Color(0xFFFBE9F2), Color(0xFF2E1F27), Color(0xFF4A3641)),
    CasTheme("black", "Cassette black", "Kaset siyah", Color(0xFF0E0E0E), Color(0xFFEFEFEF), Color(0xFFCFCBBF), Color(0xFF262626), Color(0xFF1B1B1B), Color(0xFF333333)),
)
internal val LocalCas = androidx.compose.runtime.compositionLocalOf { CASSETTE_THEMES[0] }

/**
 * The cassette look: a yellow tape deck. The cassette in the middle turns its two reels while the music plays (the tape moves from the left reel to the right one
 * as the track goes on), the label carries the song and the artist, and the keys are the keys of a deck: play / pause (red while it plays), fast forward and rewind
 * (a tap goes to the next and the previous song, a hold winds inside the song), and eject, which opens the tape shelf (the library) to put another tape in.
 * The idea of the look comes from the cassette players of the 80s and from "Bionic Tape"; everything here is drawn by Pulse.
 */
@Composable
fun CassettePlayer(engine: MusicPlayerEngine, st: PlaybackState, library: List<Track>, onOpen: () -> Unit, modifier: Modifier = Modifier, variant: String = "yellow") {
    val cas = CASSETTE_THEMES.firstOrNull { it.id == variant } ?: CASSETTE_THEMES[0]
    androidx.compose.runtime.CompositionLocalProvider(LocalCas provides cas) { CassetteBody(engine, st, library, onOpen, modifier) }
}

@Composable
private fun CassetteBody(engine: MusicPlayerEngine, st: PlaybackState, library: List<Track>, onOpen: () -> Unit, modifier: Modifier) {
    val cas = LocalCas.current
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
    val leather = rememberTexture(Tex.LEATHER)
    Box(modifier.background(cas.main).drawBehind { drawRect(leather, alpha = .10f) }) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text("PULSE TAPE", color = cas.ink, fontSize = 30.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, letterSpacing = (-1).sp)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Text("SPORT", color = cas.main, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, modifier = Modifier.background(cas.ink).padding(horizontal = 8.dp, vertical = 5.dp))
                Text("STEREO CASSETTE PLAYER", color = cas.ink, fontSize = 11.sp, letterSpacing = 1.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(start = 8.dp))
            }
            // the cassette
            Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                Cassette(st.current, spin, progress, Modifier.fillMaxWidth().aspectRatio(1.6f))
            }
            // time and a seek line
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(clock(pos), color = cas.ink, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                Box(Modifier.weight(1f).padding(horizontal = 10.dp).height(28.dp).pointerInput(dur) {
                    detectTapGestures { o -> if (dur > 0) engine.seekTo((o.x / size.width * dur).toInt()) }
                }.pointerInput(dur) {
                    detectHorizontalDragGestures { c, _ -> if (dur > 0) engine.seekTo((c.position.x / size.width * dur).toInt().coerceIn(0, dur)) }
                }, contentAlignment = Alignment.CenterStart) {
                    Box(Modifier.fillMaxWidth().height(4.dp).background(cas.ink.copy(alpha = .25f), RoundedCornerShape(2.dp)))
                    Box(Modifier.fillMaxWidth(progress).height(4.dp).background(cas.ink, RoundedCornerShape(2.dp)))
                }
                Text(clock(dur), color = cas.ink, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
            Spacer(Modifier.height(10.dp))
            // the keys of the deck
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(cas.deck).drawBehind { drawRect(leather, alpha = .30f) }.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                DeckKey("prev", Modifier.weight(1f), onClick = { engine.previous() })
                DeckKey(if (st.isPlaying) "pause" else "play", Modifier.weight(1.3f), red = st.isPlaying, onClick = { if (st.queue.isEmpty()) shelf = true else engine.togglePlayPause() })
                DeckKey("next", Modifier.weight(1f), onClick = { engine.next() })
                DeckKey("eject", Modifier.weight(1f), onClick = { shelf = true })
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Toggle("shuffle", shuffle) { shuffle = !shuffle; engine.player.shuffleModeEnabled = shuffle }
                Toggle("repeat", repeat) { repeat = !repeat; engine.player.repeatMode = if (repeat) androidx.media3.common.Player.REPEAT_MODE_ALL else androidx.media3.common.Player.REPEAT_MODE_OFF }
                Spacer(Modifier.weight(1f))
                androidx.compose.material3.Icon(deckVec("volume"), null, Modifier.size(20.dp), tint = cas.ink)
                Box(Modifier.width(140.dp).height(34.dp).pointerInput(Unit) { detectTapGestures { o -> volume = (o.x / size.width).coerceIn(0f, 1f); engine.player.volume = volume } }
                    .pointerInput(Unit) { detectHorizontalDragGestures { c, _ -> volume = (c.position.x / size.width).coerceIn(0f, 1f); engine.player.volume = volume } }, contentAlignment = Alignment.CenterStart) {
                    Box(Modifier.fillMaxWidth().height(6.dp).background(cas.ink.copy(alpha = .25f), RoundedCornerShape(3.dp)))
                    Box(Modifier.fillMaxWidth(volume).height(6.dp).background(cas.ink, RoundedCornerShape(3.dp)))
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
    val cas = LocalCas.current
    var down by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier.height(64.dp).padding(top = if (down) 3.dp else 0.dp).shadow(if (down) 1.dp else 6.dp, shape).clip(shape)
            .background(Brush.verticalGradient(if (red) listOf(Color(0xFFEF4B3A), Color(0xFFC9302C)) else listOf(cas.key2, cas.key)))
            .pointerInput(Unit) { detectTapGestures(onPress = { down = true; tryAwaitRelease(); down = false }, onTap = { onClick() }) },
        contentAlignment = Alignment.Center,
    ) { androidx.compose.material3.Icon(deckVec(icon), null, Modifier.size(30.dp), tint = if (red) Color.White else cas.main) }
}

@Composable
private fun Toggle(icon: String, on: Boolean, onClick: () -> Unit) {
    val cas = LocalCas.current
    Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(if (on) cas.ink else cas.ink.copy(alpha = .12f)).clickable(onClick = onClick), contentAlignment = Alignment.Center) { androidx.compose.material3.Icon(deckVec(icon), null, Modifier.size(22.dp), tint = if (on) cas.main else cas.ink) }
}

/** The cassette drawn on a canvas: the cream body with its label strip, the window with the two reels, the tape between them. */
@Composable
private fun Cassette(track: Track?, spin: Float, progress: Float, modifier: Modifier) {
    val cas = LocalCas.current
    val steel = rememberTexture(Tex.STEEL)
    BoxWithConstraints(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            val dark = cas.ink.luminance() > .5f
            val plastic = if (dark) cas.paper else cas.paper
            val edge = cas.ink.copy(alpha = .85f)
            // the soft contact shadow under the tape
            drawRoundRect(Color.Black.copy(alpha = .22f), Offset(w * .012f, h * .03f), Size(w, h), CornerRadius(h * .07f))
            // the shell: plastic with a faint top light, an outline and an inner bevel
            drawRoundRect(Brush.verticalGradient(listOf(plastic, Color.Black.copy(alpha = .07f).compositeOver(plastic)), 0f, h), Offset.Zero, size, CornerRadius(h * .07f))
            drawRoundRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = .55f), Color.Transparent, Color.Black.copy(alpha = .12f))), Offset.Zero, size, CornerRadius(h * .07f))
            drawRoundRect(edge, Offset.Zero, size, CornerRadius(h * .07f), style = Stroke(h * .012f))
            drawRoundRect(Color.Black.copy(alpha = .18f), Offset(h * .02f, h * .02f), Size(w - h * .04f, h - h * .04f), CornerRadius(h * .06f), style = Stroke(h * .006f))
            // the label sheet: paper with ruled lines, a colour band on top (the deck colour), a thin dark rule
            val lx = w * .045f; val ly = h * .05f; val lw = w * .91f; val lh = h * .40f
            drawRoundRect(Color(0xFFFBF7E8), Offset(lx, ly), Size(lw, lh), CornerRadius(h * .025f))
            drawRoundRect(Color.Black.copy(alpha = .22f), Offset(lx, ly), Size(lw, lh), CornerRadius(h * .025f), style = Stroke(h * .006f))
            drawRect(cas.main, Offset(lx, ly), Size(lw, lh * .20f))
            drawRect(cas.ink, Offset(lx, ly + lh * .20f), Size(lw, lh * .028f))
            for (i in 1..2) { val y = ly + lh * (.50f + i * .17f); drawLine(Color(0xFF6E8BC7).copy(alpha = .55f), Offset(lx + lw * .04f, y), Offset(lx + lw * .96f, y), h * .005f) }
            // the window: a smoked plastic opening with a lip, over the dark tape shell
            val wx = w * .17f; val wy = h * .50f; val ww = w * .66f; val wh = h * .30f
            drawRoundRect(Color(0xFF16150F), Offset(wx - h * .015f, wy - h * .015f), Size(ww + h * .03f, wh + h * .03f), CornerRadius(wh * .30f))
            drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF5C5A44), Color(0xFF3A3829), Color(0xFF5C5A44)), wy, wy + wh), Offset(wx, wy), Size(ww, wh), CornerRadius(wh * .28f))
            // tick marks of the tape counter along the lower edge of the window
            for (i in 0..12) drawLine(Color.White.copy(alpha = .35f), Offset(wx + ww * (.08f + i * .07f), wy + wh * .93f), Offset(wx + ww * (.08f + i * .07f), wy + wh * (if (i % 3 == 0) .80f else .86f)), h * .004f)
            val rl = Offset(wx + ww * .26f, wy + wh / 2f); val rr = Offset(wx + ww * .74f, wy + wh / 2f)
            val full = wh * .47f; val empty = wh * .24f
            val leftR = empty + (full - empty) * (1f - progress); val rightR = empty + (full - empty) * progress
            // the tape running from the left pack, under the guide posts, to the right pack
            val tapeC = Color(0xFF2B1B10)
            drawLine(tapeC, Offset(rl.x, rl.y + leftR), Offset(wx + ww * .36f, wy + wh * .98f), h * .012f)
            drawLine(tapeC, Offset(wx + ww * .36f, wy + wh * .98f), Offset(wx + ww * .64f, wy + wh * .98f), h * .012f)
            drawLine(tapeC, Offset(wx + ww * .64f, wy + wh * .98f), Offset(rr.x, rr.y + rightR), h * .012f)
            // the packs: brown tape with a gloss ring
            for ((c, r) in listOf(rl to leftR, rr to rightR)) {
                drawCircle(Brush.radialGradient(listOf(Color(0xFF8A5A33), Color(0xFF4B2E19)), c, r), r, c)
                drawCircle(Color.White.copy(alpha = .14f), r * .86f, c, style = Stroke(r * .08f))
                drawCircle(Color.Black.copy(alpha = .35f), r, c, style = Stroke(r * .04f))
            }
            reel(rl, wh * .20f, spin, cas.ink); reel(rr, wh * .20f, spin, cas.ink)
            // two guide posts
            for (gx in listOf(wx + ww * .36f, wx + ww * .64f)) { drawCircle(Color(0xFFB8B8B0), h * .014f, Offset(gx, wy + wh * .98f)); drawCircle(Color.Black.copy(alpha = .4f), h * .014f, Offset(gx, wy + wh * .98f), style = Stroke(h * .003f)) }
            // glass shine over the window
            drawRoundRect(Brush.linearGradient(listOf(Color.White.copy(alpha = .22f), Color.Transparent), Offset(wx, wy), Offset(wx + ww * .5f, wy + wh)), Offset(wx, wy), Size(ww, wh), CornerRadius(wh * .28f))
            // the lower trapezoid with the head openings, two capstan holes and the guide notches
            val tp = androidx.compose.ui.graphics.Path().apply {
                moveTo(w * .20f, h * .84f); lineTo(w * .80f, h * .84f); lineTo(w * .86f, h * .97f); lineTo(w * .14f, h * .97f); close()
            }
            drawPath(tp, Brush.verticalGradient(listOf(Color(0xFF8C887A), Color(0xFF55534A)), h * .84f, h * .97f))
            drawPath(tp, steel, alpha = .4f)
            drawPath(tp, edge, style = Stroke(h * .006f))
            for (hx in listOf(.30f, .40f, .50f, .60f, .70f)) drawRoundRect(Color(0xFF14130E), Offset(w * hx - w * .016f, h * .875f), Size(w * .032f, h * .07f), CornerRadius(h * .01f))
            drawCircle(Color(0xFF14130E), h * .026f, Offset(w * .24f, h * .905f)); drawCircle(Color(0xFF14130E), h * .026f, Offset(w * .76f, h * .905f))
            // five screws: the four corners and the one under the label
            for (sc in listOf(Offset(w * .028f, h * .06f), Offset(w * .972f, h * .06f), Offset(w * .028f, h * .94f), Offset(w * .972f, h * .94f), Offset(w * .5f, h * .47f))) screw(sc, h * .022f)
        }
        // the label: song and artist, in a hand-written looking face
        Column(Modifier.align(Alignment.TopStart).padding(start = maxWidth * .06f, top = maxHeight * .17f).width(maxWidth * .88f)) {
            Text(track?.title?.ifBlank { null } ?: track?.path?.substringAfterLast('/')?.substringBeforeLast('.') ?: tr("Insert a tape", "Bir kaset tak"),
                color = cas.ink, fontSize = 22.sp, fontFamily = FontFamily.Cursive, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text((track?.artist ?: tr("Press eject", "Eject'e bas")).uppercase(), color = cas.ink.copy(alpha = .75f), fontSize = 10.sp, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("D-C90", color = Color(0xFFD9392B), fontSize = 11.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, maxLines = 1, modifier = Modifier.align(Alignment.BottomStart).padding(start = maxWidth * .03f, bottom = maxHeight * .015f))
        Text("TYPE I", color = cas.ink.copy(alpha = .6f), fontSize = 9.sp, fontFamily = FontFamily.Monospace, maxLines = 1, modifier = Modifier.align(Alignment.BottomEnd).padding(end = maxWidth * .03f, bottom = maxHeight * .02f))
    }
}

/** A cross-head screw: a metal disc with a slot and a rim shadow. */
private fun DrawScope.screw(c: Offset, r: Float) {
    drawCircle(Brush.radialGradient(listOf(Color(0xFFE6E4DC), Color(0xFF9B988C)), c, r), r, c)
    drawCircle(Color.Black.copy(alpha = .45f), r, c, style = Stroke(r * .16f))
    drawLine(Color.Black.copy(alpha = .6f), Offset(c.x - r * .6f, c.y), Offset(c.x + r * .6f, c.y), r * .22f)
    drawLine(Color.Black.copy(alpha = .6f), Offset(c.x, c.y - r * .6f), Offset(c.x, c.y + r * .6f), r * .22f)
}

/** A reel: a white ring with six teeth around a dark hub, turned by [angle]. */
private fun DrawScope.reel(c: Offset, r: Float, angle: Float, ink: Color) {
    drawCircle(Color.White, r * 1.35f, c); drawCircle(ink.copy(alpha = .35f), r * 1.35f, c, style = Stroke(r * .12f))
    rotate(angle, c) {
        drawCircle(ink, r * .75f, c)
        for (i in 0 until 6) {
            rotate(i * 60f, c) { drawRect(Color.White, Offset(c.x - r * .09f, c.y - r * .75f), Size(r * .18f, r * .32f)) }
        }
        drawCircle(Color.White, r * .38f, c); drawCircle(ink, r * .2f, c)
    }
}

/** The tape shelf: the songs of the library as tapes to put in; "files" opens the file picker for songs that are not in the library. */
@Composable
private fun TapeShelf(library: List<Track>, st: PlaybackState, onPick: (Int) -> Unit, onFiles: () -> Unit, onClose: () -> Unit) {
    val cas = LocalCas.current
    // the colour of the deck on the dark shelf (a black deck uses its light ink)
    val acc = if (cas.deck.luminance() < .15f) cas.ink else cas.deck
    Column(Modifier.fillMaxSize().background(Color(0xF2121212)).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Tapes", "Kasetler") + " · ${library.size}", color = acc, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Box(Modifier.size(40.dp).clickable(onClick = onFiles), contentAlignment = Alignment.Center) { PIcon("folder", acc, 22.dp) }
            Box(Modifier.size(40.dp).clickable(onClick = onClose), contentAlignment = Alignment.Center) { PIcon("close", Color.White, 22.dp) }
        }
        LazyColumn(Modifier.fillMaxSize().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(library) { i, t ->
                val now = st.current?.path == t.path
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (now) acc else cas.paper).clickable { onPick(i) }.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(6.dp).height(34.dp).background(if (now) cas.ink else acc))
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(t.title?.ifBlank { null } ?: t.path.substringAfterLast('/').substringBeforeLast('.'), color = cas.ink, fontSize = 17.sp, fontFamily = FontFamily.Cursive, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text((t.artist ?: "").uppercase(), color = cas.ink.copy(alpha = .65f), fontSize = 10.sp, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (now) PIcon("play", cas.ink, 20.dp)
                }
            }
        }
    }
}
