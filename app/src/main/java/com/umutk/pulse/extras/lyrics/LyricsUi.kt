package com.umutk.pulse.extras.lyrics

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PCard
import com.umutk.pulse.PField
import com.umutk.pulse.PIcon
import com.umutk.pulse.PLoader
import com.umutk.pulse.T
import com.umutk.pulse.tr
import kotlinx.coroutines.delay

/** Lyric lines: synced ones follow [positionMs] (pass -1 for no highlight) and tap seeks when [onSeek] is set; unsynced ones just scroll. */
@Composable
fun LyricsView(result: LyricsResult, positionMs: Long, onSeek: ((Long) -> Unit)?, modifier: Modifier = Modifier) {
    val state = rememberLazyListState()
    val cur = if (result.synced && positionMs >= 0) result.lines.indexOfLast { it.timeMs <= positionMs } else -1
    LaunchedEffect(cur) { if (cur >= 0) state.animateScrollToItem(maxOf(0, cur - 2)) }
    LazyColumn(modifier, state = state) {
        itemsIndexed(result.lines) { i, l ->
            val on = i == cur
            if (l.text.isEmpty()) Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) { com.umutk.pulse.extras.ui.TIcon("note", T.sub, 20.dp) } else Text(
                l.text,
                color = if (on) MaterialTheme.colorScheme.primary else T.sub,
                fontSize = if (on) 22.sp else 17.sp,
                fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
                    .then(if (onSeek != null && result.synced) Modifier.clickable { onSeek(l.timeMs) } else Modifier)
                    .padding(vertical = 8.dp),
            )
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
    BackHandler(onBack = onDismiss)
    LaunchedEffect(title, artist, localPath) {
        busy = true; res = null
        res = Lyrics.forTrack(ctx, title, artist, album, durationMs, localPath)
        busy = false
    }
    Box(modifier.fillMaxSize().background(Color.Black.copy(alpha = .78f))) {
        val r = res
        when {
            r != null -> LyricsView(r, positionMs, onSeek, Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 48.dp))
            busy -> Box(Modifier.fillMaxSize(), Alignment.Center) { PLoader() }
            else -> Box(Modifier.fillMaxSize(), Alignment.Center) { PIcon("book", Color.White.copy(alpha = .6f), 40.dp) }
        }
        IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)) { PIcon("close", Color.White, 22.dp) }
    }
}
