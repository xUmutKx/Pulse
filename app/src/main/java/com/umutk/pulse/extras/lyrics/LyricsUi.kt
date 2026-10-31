package com.umutk.pulse.extras.lyrics

import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.mutableIntStateOf
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import com.umutk.pulse.PCard
import com.umutk.pulse.PField
import com.umutk.pulse.PIcon
import com.umutk.pulse.PLoader
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.umutk.pulse.T
import com.umutk.pulse.tr
import kotlinx.coroutines.delay

/** Lyric lines: synced ones follow [positionMs] (pass -1 for no highlight) and tap seeks when [onSeek] is set; unsynced ones just scroll. */
@Composable
fun LyricsView(result: LyricsResult, positionMs: Long, onSeek: ((Long) -> Unit)?, modifier: Modifier = Modifier, mark: String = "", onDark: Boolean = false) {
    // on the dark sheet the lines are white (the look's own greys vanish on black); elsewhere the look's colours
    val hi = if (onDark) Color.White else MaterialTheme.colorScheme.primary
    val lo = if (onDark) Color.White.copy(alpha = .72f) else T.sub
    val state = rememberLazyListState()
    // a size and a weight to read from the other side of the table: the setting's size plus a few, always bold
    val base = com.umutk.pulse.extras.music.MusicSettings.lyricsFont(androidx.compose.ui.platform.LocalContext.current).coerceIn(12, 30) + 4
    val cur = if (result.synced && positionMs >= 0) result.lines.indexOfLast { it.timeMs <= positionMs } else -1
    LaunchedEffect(cur) { if (cur >= 0) state.animateScrollToItem(maxOf(0, cur - 2), 0) }
    LazyColumn(modifier, state = state) {
        itemsIndexed(result.lines) { i, l ->
            val on = i == cur
            if (l.text.isEmpty()) Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) { com.umutk.pulse.extras.ui.TIcon("note", lo, 20.dp) } else {
                // the line being sung grows and takes the accent, the others fade with their distance from it (all eased, none jumps)
                val scale by androidx.compose.animation.core.animateFloatAsState(if (on) 1.18f else 1f, androidx.compose.animation.core.tween(300), label = "lyScale")
                val tone by androidx.compose.animation.animateColorAsState(if (on) hi else lo, androidx.compose.animation.core.tween(300), label = "lyTone")
                val dist = if (cur < 0) 0 else kotlin.math.abs(i - cur)
                val fade by androidx.compose.animation.core.animateFloatAsState(if (cur < 0 || on) 1f else (1f - .13f * dist).coerceAtLeast(.35f), androidx.compose.animation.core.tween(300), label = "lyFade")
                Text(
                    lyricMarked(l.text, mark, hi),
                    color = tone,
                    fontSize = base.sp,
                    lineHeight = (base * 1.3f).sp,
                    fontWeight = if (on) FontWeight.ExtraBold else FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                        .then(if (onSeek != null && result.synced) Modifier.clickable { onSeek(l.timeMs) } else Modifier)
                        .padding(vertical = 8.dp)
                        .graphicsLayer { scaleX = scale; scaleY = scale; alpha = fade },
                )
            }
        }
    }
}

/** Search box plus results: each hit shows the line before, the matching line and the line after. */
@Composable
fun LyricsSearchScreen(onOpen: (LyricHit) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var q by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<LyricHit>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    BackHandler(onBack = onClose)
    LaunchedEffect(q) {
        done = false
        if (q.trim().length < 3) { hits = emptyList(); busy = false; return@LaunchedEffect }
        delay(600)
        busy = true
        hits = Lyrics.searchByText(ctx, q)
        busy = false; done = true
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { PIcon("back", T.text, 22.dp) }
            PField(q, { q = it }, tr("Lyrics", "Söz"), Modifier.weight(1f))
            if (busy) PLoader(Modifier.padding(start = 8.dp))
        }
        if (done && hits.isEmpty() && !busy) Box(Modifier.fillMaxSize(), Alignment.Center) { PIcon("search", T.sub, 40.dp) }
        LazyColumn(Modifier.weight(1f).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(hits) { h ->
                PCard(Modifier.fillMaxWidth().clickable { onOpen(h) }) {
                    Text(listOf(h.artist, h.title).filter { it.isNotBlank() }.joinToString(" - "), color = T.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    // the hit reads like the lyrics view: centred lines, matched line in the active colour
                    h.before?.let { Text(it, color = T.sub, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(), maxLines = 2, overflow = TextOverflow.Ellipsis) }
                    Text(h.line, color = MaterialTheme.colorScheme.primary, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(), maxLines = 3, overflow = TextOverflow.Ellipsis)
                    h.after?.let { Text(it, color = T.sub, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(), maxLines = 2, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
    }
}

/** Swipe up that starts in the lower part of the element (the album art) calls [onTrigger]. */
@Composable
fun Modifier.lyricsSwipeUp(onTrigger: () -> Unit): Modifier {
    val fire by rememberUpdatedState(onTrigger)
    val reach = with(LocalDensity.current) { 72.dp.toPx() }
    return pointerInput(Unit) {
        var startY = 0f
        var sum = 0f
        detectVerticalDragGestures(
            onDragStart = { startY = it.y; sum = 0f },
            onDragEnd = { if (startY > size.height * 0.55f && sum < -reach) fire() },
            onDragCancel = { sum = 0f },
            onVerticalDrag = { _, d -> sum += d },
        )
    }
}

/** Lyrics over the album art. Fills its parent: put it in a Box on top of the cover. Searches online when the device has none. */
@Composable
fun LyricsSheet(
    title: String, artist: String, album: String?, durationMs: Long, localPath: String?, positionMs: Long,
    onDismiss: () -> Unit, onSeek: ((Long) -> Unit)? = null, modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    var res by remember { mutableStateOf<LyricsResult?>(null) }
    var busy by remember { mutableStateOf(true) }
    // the sheet rises from the bottom and sinks back the same way; y is how far it sits below its rest position, in px
    val scope = rememberCoroutineScope()
    val y = remember { Animatable(0f) }
    var height by remember { mutableFloatStateOf(0f) }
    var entered by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    fun close() {
        if (closing) return
        closing = true
        scope.launch { y.animateTo(height, tween(300, easing = FastOutLinearInEasing)); onDismiss() }
    }
    fun settle() { scope.launch { y.animateTo(0f, tween(280, easing = FastOutSlowInEasing)) } }
    BackHandler { close() }
    LaunchedEffect(height) {
        if (height > 0f && !entered) {
            entered = true
            y.snapTo(height)
            y.animateTo(0f, tween(420, easing = FastOutSlowInEasing))
        }
    }
    // when the last timed line is over, the lyrics step aside and the cover shows again
    val lastMs = res?.takeIf { it.synced }?.lines?.lastOrNull { it.timeMs >= 0 }?.timeMs
    // only when the song really ran through the lyrics while they were open: opening them past the last line must not close them again
    var wasBefore by remember { mutableStateOf(false) }
    LaunchedEffect(lastMs, positionMs <= (lastMs ?: 0L)) { if (lastMs != null && positionMs <= lastMs) wasBefore = true }
    LaunchedEffect(lastMs != null && positionMs > lastMs + 6000) { if (lastMs != null && wasBefore && positionMs > lastMs + 6000 && !closing) close() }
    // the names the search uses; the fields below the "No lyrics" note change them
    var qTitle by remember(title) { mutableStateOf(title) }
    var qArtist by remember(artist) { mutableStateOf(artist) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(title, artist, localPath, attempt) {
        busy = true; res = null
        val r = if (attempt == 0) {
            // the notification finds lyrics under the file's own title with no artist; the same second try here
            Lyrics.forTrack(ctx, title, artist, album, durationMs, localPath) ?: Lyrics.forTrack(ctx, title.substringAfter(" - "), "", album, durationMs, localPath)
        } else Lyrics.forTrack(ctx, qTitle, qArtist, album, durationMs, null)
        res = r
        busy = false
        // found online: written beside the song when the settings say so
        if (r != null && localPath != null && r.source in setOf("lrclib", "ovh", "cache")) withContext(Dispatchers.IO) {
            Lyrics.keep(localPath, r)
        }
    }
    Box(
        modifier.fillMaxSize()
            .onSizeChanged { height = it.height.toFloat() }
            // hidden until its height is known, so it does not flash at rest before it rises
            .alpha(if (entered) 1f else 0f)
            .offset { IntOffset(0, y.value.roundToInt()) }
            .background(Color.Black.copy(alpha = .92f))
            // a downward drag moves the sheet with the finger; past a quarter of its height it closes, else it springs back
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = { if (y.value > height * .25f) close() else settle() },
                    onDragCancel = { settle() },
                    onVerticalDrag = { c, d -> c.consume(); scope.launch { y.snapTo((y.value + d).coerceAtLeast(0f)) } },
                )
            },
    ) {
        val r = res
        when {
            r != null -> LyricsView(r, positionMs, onSeek, Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 48.dp), onDark = true)
            busy -> Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
                PLoader()
                Text(tr("Searching for lyrics…", "Sözler aranıyor…"), color = Color.White.copy(alpha = .7f), fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
            }
            else -> Column(Modifier.fillMaxSize().padding(horizontal = 28.dp), Arrangement.Center, Alignment.CenterHorizontally) {
                PIcon("book", Color.White.copy(alpha = .6f), 40.dp)
                Text(tr("No lyrics found", "Söz bulunamadı"), color = Color.White.copy(alpha = .7f), fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp, bottom = if (Lyrics.lastError == null) 12.dp else 2.dp))
                Lyrics.lastError?.let { Text(tr("Network: ", "Ağ: ") + it, color = Color.White.copy(alpha = .5f), fontSize = 11.sp, modifier = Modifier.padding(bottom = 12.dp)) }
                // another spelling of the song or the artist, then search again
                @Composable fun field(v: String, hint: String, on: (String) -> Unit) = androidx.compose.foundation.text.BasicTextField(
                    v, on, singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 15.sp),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).background(Color.White.copy(alpha = .1f), androidx.compose.foundation.shape.RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
                    decorationBox = { inner -> if (v.isEmpty()) Text(hint, color = Color.White.copy(alpha = .4f), fontSize = 15.sp); inner() })
                field(qArtist, tr("Artist", "Sanatçı")) { qArtist = it }
                field(qTitle, tr("Song", "Şarkı")) { qTitle = it }
                Text(tr("Search again", "Tekrar ara"), color = Color.Black, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp)
                    .background(Color.White, androidx.compose.foundation.shape.RoundedCornerShape(20.dp)).clickable { attempt++ }.padding(horizontal = 20.dp, vertical = 9.dp))
            }
        }
        IconButton(onClick = { close() }, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)) { PIcon("close", Color.White, 22.dp) }
    }
}

/** [text] with every word of [query] (words of 2+ letters, split on spaces and commas) in bold on a tinted background; the text itself when there is no query. */
fun lyricMarked(text: String, query: String, accent: androidx.compose.ui.graphics.Color): androidx.compose.ui.text.AnnotatedString {
    val words = query.lowercase().split(' ', ',').map { it.trim() }.filter { it.length >= 2 }.distinct()
    if (words.isEmpty()) return androidx.compose.ui.text.AnnotatedString(text)
    val low = text.lowercase()
    val hit = BooleanArray(text.length)
    words.forEach { w -> var i = low.indexOf(w); while (i >= 0) { for (k in i until minOf(i + w.length, hit.size)) hit[k] = true; i = low.indexOf(w, i + w.length) } }
    return androidx.compose.ui.text.buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            var j = i; while (j < text.length && hit[j] == hit[i]) j++
            if (hit[i]) withStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.ExtraBold, background = accent.copy(alpha = .35f), color = androidx.compose.ui.graphics.Color.White)) { append(text.substring(i, j)) } else append(text.substring(i, j))
            i = j
        }
    }
}
