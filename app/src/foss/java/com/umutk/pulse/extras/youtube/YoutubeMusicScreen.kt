package com.umutk.pulse.extras.youtube

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PField
import com.umutk.pulse.PIcon
import com.umutk.pulse.T
import com.umutk.pulse.extras.music.MusicPlayerEngine
import com.umutk.pulse.extras.music.Track
import com.umutk.pulse.tr
import kotlinx.coroutines.launch

/**
 * YouTube Music inside Pulse's music player: search songs and play one straight into the player's own queue, then the screen closes and the player shows it.
 * Songs come from NewPipeExtractor; the player (equalizer, lyrics, notification) is Pulse's.
 */
@Composable
fun YoutubeMusicScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<YtClient.Item>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    BackHandler { onClose() }

    fun load() {
        scope.launch {
            busy = true; note = ""
            val r = runCatching { YtClient.search(query.ifBlank { "music" }, true) }
            busy = false
            r.onSuccess { items = it; if (it.isEmpty()) note = tr("Nothing found", "Bulunamadı") }
                .onFailure { items = emptyList(); note = tr("Could not load: ${it.message ?: it.javaClass.simpleName}", "Yüklenemedi: ${it.message ?: it.javaClass.simpleName}") }
        }
    }
    LaunchedEffect(Unit) { load() }

    fun play(item: YtClient.Item) {
        scope.launch {
            busy = true
            val s = runCatching { YtClient.streams(item.url) }.getOrNull()
            busy = false
            val audio = s?.audio
            if (audio == null) { Toast.makeText(ctx, tr("This one cannot be played", "Bu çalınamıyor"), Toast.LENGTH_SHORT).show(); return@launch }
            MusicPlayerEngine.get(ctx).playQueue(listOf(Track(path = audio, title = item.title, artist = item.by, durationSec = item.sec.toInt())), 0)
            onClose()
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clickable { onClose() }, contentAlignment = Alignment.Center) { PIcon("back", T.text, 22.dp) }
            PField(query, { query = it }, tr("Search YouTube Music", "YouTube Music'te ara"), Modifier.weight(1f), onDone = { load() })
            Box(Modifier.size(44.dp).clickable { load() }, contentAlignment = Alignment.Center) { PIcon("search", T.text, 22.dp) }
        }
        if (busy) Text("…", color = T.sub, fontSize = 14.sp, modifier = Modifier.padding(vertical = 6.dp))
        if (note.isNotEmpty()) Text(note, color = T.sub, fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp))
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items, key = { it.url }) { item ->
                val thumb by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, item.thumb) { value = item.thumb?.let { YtClient.image(it)?.asImageBitmap() } }
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { play(item) }, verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(T.panel)) {
                        thumb?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.title, color = T.text, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(item.by, color = T.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
