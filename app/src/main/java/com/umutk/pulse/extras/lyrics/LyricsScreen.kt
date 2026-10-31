package com.umutk.pulse.extras.lyrics

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.runtime.mutableIntStateOf
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
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import java.io.File
import java.io.IOException

/**
 * Lyrics for one track: search, pick a result, synced lines highlighted by [positionMs], save as .lrc / .txt.
 * [onClose] is the back arrow; navigation belongs to the caller.
 */
@Composable
fun LyricsScreen(
    title: String,
    artist: String,
    album: String?,
    durationSec: Int?,
    positionMs: () -> Long,
    saveDir: File,
    onClose: () -> Unit,
    store: LyricsStore? = null,
) {
    val ctx = LocalContext.current
    val client = remember { LrclibClient(LyricsCache(null)) }
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf(listOf(artist, title).filter { it.isNotBlank() }.joinToString(" ")) }
    var results by remember { mutableStateOf<List<LyricsRecord>>(emptyList()) }
    var record by remember { mutableStateOf<LyricsRecord?>(null) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var current by remember { mutableIntStateOf(-1) }
    val lines = remember(record) { record?.syncedLyrics?.let { parseLrc(it) }.orEmpty() }
    val listState = rememberLazyListState()
    val notFound = tr("No lyrics found", "Söz bulunamadı")
    val netError = tr("Network error", "Ağ hatası")

    fun task(block: suspend () -> Unit) = scope.launch {
        busy = true
        status = null
        try { block() } catch (e: IOException) { status = netError } catch (e: JSONException) { status = netError } finally { busy = false }
    }

    LaunchedEffect(Unit) {
        if (title.isNotBlank()) task {
            record = withContext(Dispatchers.IO) { client.get(title, artist, album, durationSec) }
            if (record == null) status = notFound
        }
    }

    LaunchedEffect(lines) {
        if (lines.isEmpty()) return@LaunchedEffect
        while (true) {
            current = lineIndexAt(lines, positionMs())
            delay(200)
        }
    }
    LaunchedEffect(current) {
        if (current >= 0) listState.animateScrollToItem(maxOf(0, current - 2))
    }

    val rec = record
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { PIcon("back", T.text, 22.dp) }
            Box(Modifier.weight(1f).border(BorderStroke(1.dp, T.sub), RoundedShape).padding(horizontal = 12.dp, vertical = 10.dp)) {
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = T.text, fontSize = 15.sp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            IconButton(
                onClick = {
                    task {
                        results = withContext(Dispatchers.IO) { client.search(query.trim()) }
                        record = null
                        if (results.isEmpty()) status = notFound
                    }
                },
                enabled = !busy && query.isNotBlank(),
            ) { PIcon("search", T.text, 22.dp) }
        }
        if (busy) PLoader(Modifier.fillMaxWidth().padding(top = 8.dp))
        status?.let { Text(it, color = T.sub, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp)) }

        when {
            rec == null -> LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(results) { r ->
                    PCard(Modifier.fillMaxWidth().clickable { record = r }) {
                        Text("${r.artistName} - ${r.trackName}", color = T.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        r.albumName?.let { Text(it, color = T.sub, fontSize = 13.sp) }
                        Text(kindLabel(r), color = T.sub, fontSize = 12.sp)
                    }
                }
            }
            rec.instrumental -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(tr("Instrumental", "Enstrümantal"), color = T.sub, fontSize = 17.sp)
            }
            lines.isNotEmpty() -> LazyColumn(Modifier.weight(1f), state = listState) {
                itemsIndexed(lines) { i, line ->
                    val on = i == current
                    Text(
                        line.text,
                        color = if (on) MaterialTheme.colorScheme.primary else T.sub,
                        fontSize = if (on) 22.sp else 17.sp,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    )
                }
            }
            else -> Text(
                rec.plainLyrics ?: tr("No lyrics", "Söz yok"),
                color = T.text,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            )
        }

        if (rec != null) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (results.isNotEmpty()) IconButton(onClick = { record = null }) { PIcon("back", T.text, 22.dp) }
            Spacer(Modifier.weight(1f))
            if (rec.hasLyrics) IconButton(onClick = {
                task {
                    val file = withContext(Dispatchers.IO) { store?.save(rec, null); saveLyrics(saveDir, rec) }
                    status = file?.let { tr("Saved", "Kaydedildi") + ": " + it.name } ?: tr("Nothing to save", "Kaydedilecek söz yok")
                }
            }) { PIcon("download", T.text, 22.dp) }
        }
    }
}

private val RoundedShape = RoundedCornerShape(12.dp)

private fun kindLabel(r: LyricsRecord) = when {
    r.instrumental -> tr("Instrumental", "Enstrümantal")
    r.syncedLyrics != null -> tr("Synced", "Senkron")
    r.plainLyrics != null -> tr("Plain text", "Düz metin")
    else -> tr("No lyrics", "Söz yok")
}
