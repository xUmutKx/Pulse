package com.umutk.pulse.extras.youtube

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.umutk.pulse.LocalOpenTool
import com.umutk.pulse.PChip
import com.umutk.pulse.PField
import com.umutk.pulse.PIcon
import com.umutk.pulse.T
import com.umutk.pulse.extras.music.MusicPlayerEngine
import com.umutk.pulse.extras.music.Track
import com.umutk.pulse.extras.video.VideoPlayerScreen
import com.umutk.pulse.tr
import kotlinx.coroutines.launch

/**
 * The YouTube client of Pulse: the trending list, a search for videos and a search for songs. A video plays in Pulse's own video player, a song in Pulse's own music
 * player (queue, equalizer, lyrics and the notification are the music player's). No Shorts, no account. The streams come from NewPipeExtractor (GPL v3).
 */
@Composable
fun YoutubeHub(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val openTool = LocalOpenTool.current
    var tab by remember { mutableIntStateOf(0) }   // 0 trending, 1 video search, 2 song search
    var query by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<YtClient.Item>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    var play by remember { mutableStateOf<Uri?>(null) }

    val watching = play
    if (watching != null) {
        BackHandler { play = null }
        VideoPlayerScreen(watching, { play = null })
        return
    }
    BackHandler { onBack() }

    fun load() {
        scope.launch {
            busy = true; note = ""
            val r = runCatching { if (tab == 0 && query.isBlank()) YtClient.trending() else YtClient.search(query.ifBlank { "music" }, tab == 2) }
            busy = false
            r.onSuccess { items = it; if (it.isEmpty()) note = tr("Nothing found", "Bulunamadı") }
                .onFailure { items = emptyList(); note = tr("Could not load: ${it.message ?: it.javaClass.simpleName}", "Yüklenemedi: ${it.message ?: it.javaClass.simpleName}") }
        }
    }
    LaunchedEffect(tab) { if (tab == 0 || query.isNotBlank()) load() }

    fun open(item: YtClient.Item) {
        scope.launch {
            busy = true
            val s = runCatching { YtClient.streams(item.url) }.getOrNull()
            busy = false
            if (s == null) { Toast.makeText(ctx, tr("This one cannot be opened", "Bu açılamıyor"), Toast.LENGTH_SHORT).show(); return@launch }
            if (tab == 2 && s.audio != null) {
                // a song goes to Pulse's music player: the player's own queue and screen
                MusicPlayerEngine.get(ctx).playQueue(listOf(Track(path = s.audio, title = item.title, artist = item.by, durationSec = item.sec.toInt())), 0)
                openTool("music")
            } else if (s.video != null) play = Uri.parse(s.video)
            else if (s.audio != null) {
                MusicPlayerEngine.get(ctx).playQueue(listOf(Track(path = s.audio, title = item.title, artist = item.by, durationSec = item.sec.toInt())), 0)
                openTool("music")
            } else Toast.makeText(ctx, tr("No stream found", "Akış bulunamadı"), Toast.LENGTH_SHORT).show()
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clickable { onBack() }, contentAlignment = Alignment.Center) { PIcon("back", T.text, 22.dp) }
            Text("YouTube", color = T.text, fontSize = 20.sp, modifier = Modifier.weight(1f).padding(start = 4.dp))
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PChip(tab == 0, tr("Trending", "Öne çıkanlar")) { tab = 0 }
            PChip(tab == 1, tr("Videos", "Videolar")) { tab = 1 }
            PChip(tab == 2, tr("Music", "Müzik")) { tab = 2 }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PField(query, { query = it }, tr("Search", "Ara"), Modifier.weight(1f), onDone = { if (tab == 0) tab = 1 else load() })
            Box(Modifier.size(44.dp).clickable { if (tab == 0) tab = 1 else load() }, contentAlignment = Alignment.Center) { PIcon("search", T.text, 22.dp) }
        }
        if (busy) Text("…", color = T.sub, fontSize = 14.sp, modifier = Modifier.padding(vertical = 6.dp))
        if (note.isNotEmpty()) Text(note, color = T.sub, fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp))
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(items, key = { it.url }) { item ->
                val thumb by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, item.thumb) { value = item.thumb?.let { YtClient.image(it)?.asImageBitmap() } }
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { open(item) }, verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(128.dp).height(72.dp).clip(RoundedCornerShape(8.dp)).background(T.panel)) {
                        thumb?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                        if (item.sec > 0) Text(clock(item.sec), color = androidx.compose.ui.graphics.Color.White, fontSize = 11.sp,
                            modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).background(androidx.compose.ui.graphics.Color(0xAA000000), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp))
                        else if (item.live) Text("LIVE", color = androidx.compose.ui.graphics.Color.White, fontSize = 11.sp,
                            modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).background(androidx.compose.ui.graphics.Color(0xCCD32F2F), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp))
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

private fun clock(sec: Long): String = if (sec >= 3600) "%d:%02d:%02d".format(sec / 3600, sec % 3600 / 60, sec % 60) else "%d:%02d".format(sec / 60, sec % 60)
