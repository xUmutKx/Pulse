package com.umutk.pulse.extras.lyrics

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PCard
import com.umutk.pulse.PIcon
import com.umutk.pulse.PLoader
import com.umutk.pulse.T
import com.umutk.pulse.extras.music.Track
import com.umutk.pulse.tr
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.io.File

/** Lyric lines (synced ones follow [positionMs], tap seeks when [onSeek] is set) or the plain text. */
@Composable
fun LyricsBody(rec: LyricsRecord, positionMs: Int, onSeek: ((Int) -> Unit)?, modifier: Modifier = Modifier) {
    val lines = remember(rec) { rec.syncedLyrics?.let { parseLrc(it) }.orEmpty() }
    val state = rememberLazyListState()
    val density = LocalDensity.current
    val cur = if (lines.isEmpty()) -1 else lineIndexAt(lines, positionMs.toLong())
    // the current line glides to the middle of the view; the others stay grey until their turn
    LaunchedEffect(cur) {
        if (cur < 0) return@LaunchedEffect
        val half = state.layoutInfo.viewportSize.height / 2 - with(density) { 24.dp.roundToPx() }
        state.animateScrollToItem(cur, -half.coerceAtLeast(0))
    }
    when {
        rec.instrumental -> Box(modifier, contentAlignment = Alignment.Center) { Text(tr("Instrumental", "Enstrümantal"), color = T.sub, fontSize = 17.sp) }
        lines.isNotEmpty() -> LazyColumn(modifier, state = state, contentPadding = PaddingValues(vertical = 120.dp)) {
            itemsIndexed(lines) { i, l ->
                val on = i == cur
                val color by animateColorAsState(if (on) T.text else T.text.copy(alpha = .32f), tween(420), label = "lrc-color")
                val size by animateFloatAsState(if (on) 24f else 17f, tween(420), label = "lrc-size")
                if (l.text.isEmpty()) Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) { com.umutk.pulse.extras.ui.TIcon("note", T.sub, 20.dp) } else Text(
                    l.text,
                    color = color,
                    fontSize = size.sp,
                    lineHeight = (size * 1.35f).sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().then(if (onSeek != null) Modifier.clickable { onSeek(l.timeMs.toInt()) } else Modifier).padding(vertical = 10.dp),
                )
            }
        }
        else -> Text(rec.plainLyrics ?: tr("No lyrics", "Söz yok"), color = T.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = modifier.verticalScroll(rememberScrollState()))
    }
}

/** Lyrics of [track]: saved ones first, then a .lrc beside the file, then an automatic online search. */
@Composable
fun LyricsPanel(track: Track?, positionMs: Int, modifier: Modifier = Modifier, onSeek: ((Int) -> Unit)? = null) {
    val ctx = LocalContext.current
    val store = remember { LyricsStore(ctx) }
    val client = remember { LrclibClient(LyricsCache(File(ctx.cacheDir, "lyrics"))) }
    val scope = rememberCoroutineScope()
    var rec by remember { mutableStateOf<LyricsRecord?>(null) }
    var saved by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<LyricsRecord>>(emptyList()) }
    val notFound = tr("No lyrics found", "Söz bulunamadı")
    val netError = tr("No connection", "Bağlantı yok")
    val path = track?.path
    val (artist, title) = remember(track) { track?.let { guessArtistTitle(it) } ?: ("" to "") }

    fun online() = scope.launch {
        busy = true; status = null
        try {
            val r = withContext(Dispatchers.IO) { autoFind(client, artist, title, track?.durationSec?.takeIf { it > 0 }) }
            rec = r; saved = false
            if (r == null) status = notFound
        } catch (e: Exception) { status = netError } finally { busy = false }
    }

    LaunchedEffect(path) {
        rec = null; saved = false; status = null; searching = false; results = emptyList(); query = listOf(artist, title).filter { it.isNotBlank() }.joinToString(" ")
        if (track == null) return@LaunchedEffect
        val local = withContext(Dispatchers.IO) { store.load(path, artist, title) }
        if (local != null) { rec = local; saved = true; return@LaunchedEffect }
        // embedded tag, then .lrc / .txt beside the file, before anything is downloaded
        val side = withContext(Dispatchers.IO) { Lyrics.localRecord(track.path, title, artist) }
        if (side != null) { rec = side; return@LaunchedEffect }
        online()
    }

    Column(modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (busy) PLoader(Modifier.padding(start = 8.dp))
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { searching = !searching }) { PIcon(if (searching) "close" else "search", T.text, 22.dp) }
            IconButton(onClick = { online() }, enabled = !busy && track != null) { PIcon("rotate", T.text, 22.dp) }
            rec?.takeIf { it.hasLyrics }?.let { r ->
                if (saved) IconButton(onClick = { scope.launch { withContext(Dispatchers.IO) { store.delete(r, path) }; saved = false } }) { PIcon("delete", T.text, 22.dp) }
                else IconButton(onClick = {
                    scope.launch {
                        val f = withContext(Dispatchers.IO) { runCatching { store.save(r, path) }.getOrNull() }
                        saved = true
                        status = tr("Saved", "Kaydedildi") + f?.let { ": " + it.name }.orEmpty()
                    }
                }) { PIcon("download", T.text, 22.dp) }
            }
        }
        if (searching) {
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).border(BorderStroke(1.dp, T.sub), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 10.dp)) {
                    BasicTextField(query, { query = it }, singleLine = true, textStyle = TextStyle(color = T.text, fontSize = 15.sp), modifier = Modifier.fillMaxWidth())
                }
                IconButton(enabled = !busy && query.isNotBlank(), onClick = {
                    scope.launch {
                        busy = true; status = null
                        try {
                            results = withContext(Dispatchers.IO) { client.search(query.trim()) }.filter { it.hasLyrics }
                            if (results.isEmpty()) status = notFound
                        } catch (e: Exception) { status = netError } finally { busy = false }
                    }
                }) { PIcon("search", T.text, 22.dp) }
            }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(results) { r ->
                    PCard(Modifier.fillMaxWidth().clickable { rec = r; saved = false; searching = false }) {
                        Text("${r.artistName} - ${r.trackName}", color = T.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(if (r.syncedLyrics != null) tr("Synced", "Senkron") else tr("Plain text", "Düz metin"), color = T.sub, fontSize = 12.sp)
                    }
                }
            }
        } else {
            status?.let { Text(it, color = T.sub, fontSize = 13.sp, modifier = Modifier.padding(vertical = 4.dp)) }
            val r = rec
            if (r != null) LyricsBody(r, positionMs, onSeek, Modifier.weight(1f).fillMaxWidth())
            else Box(Modifier.weight(1f).fillMaxSize())
        }
    }
}
