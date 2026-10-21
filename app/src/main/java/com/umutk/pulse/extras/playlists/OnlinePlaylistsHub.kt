package com.umutk.pulse.extras.playlists

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import com.umutk.pulse.PSlider
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PIcon
import com.umutk.pulse.PLoader
import com.umutk.pulse.T
import com.umutk.pulse.extras.music.M3uParser
import com.umutk.pulse.extras.music.MusicPlayerEngine
import com.umutk.pulse.extras.music.Track
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

private const val YTDLNIS = "com.deniscerri.ytdl"
private val thumbs = HashMap<String, Bitmap?>()
private val thumbOf = HashMap<String, String?>()

private fun ytdlnisInstalled(c: Context) = runCatching { c.packageManager.getPackageInfo(YTDLNIS, 0); true }.getOrDefault(false)

/** Hands a link to YTDLnis (no result comes back). */
private fun sendToYtdlnis(c: Context, link: String) {
    val i = Intent(Intent.ACTION_SEND).setPackage(YTDLNIS).setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, link).putExtra("TYPE", "audio").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { c.startActivity(i) }.onFailure { Toast.makeText(c, tr("Could not open it", "Açılamadı"), Toast.LENGTH_SHORT).show() }
}

private fun mmss(s: Int) = if (s < 0) "" else "%d:%02d".format(s / 60, s % 60)

@Composable
private fun Thumb(url: String?) {
    var bmp by remember(url) { mutableStateOf(url?.let { synchronized(thumbs) { thumbs[it] } }) }
    LaunchedEffect(url) {
        if (url != null && bmp == null) bmp = withContext(Dispatchers.IO) {
            runCatching { BitmapFactory.decodeStream(URL(url).openStream()) }.getOrNull()
        }.also { b -> synchronized(thumbs) { thumbs[url] = b } }
    }
    val m = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
    bmp?.let { Image(it.asImageBitmap(), null, m, contentScale = ContentScale.Crop) }
        ?: Box(m.border(BorderStroke(1.dp, T.sub.copy(alpha = .4f)), RoundedCornerShape(8.dp)), Alignment.Center) { PIcon("play", T.sub, 20.dp) }
}

@Composable
fun OnlinePlaylistsHub(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var list by remember { mutableStateOf<PList?>(null) }
    var found by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    val dl = remember { ytdlnisInstalled(ctx) }
    BackHandler { if (list != null || found.isNotEmpty()) { list = null; found = emptyList() } else onBack() }

    fun toast(s: String) = Toast.makeText(ctx, s, Toast.LENGTH_SHORT).show()
    fun run(block: suspend () -> Unit) {
        scope.launch {
            busy = true; status = null
            try { block() } catch (e: Exception) { status = tr("No result. Try again later.", "Sonuç yok. Sonra tekrar dene.") }
            busy = false
        }
    }
    fun open(src: PlaylistClient.Src) = run {
        found = emptyList()
        when (src) {
            is PlaylistClient.Src.YtList -> list = PlaylistClient.youtubePlaylist(src.id)
            is PlaylistClient.Src.YtVideo -> list = PList("YouTube", listOf(PItem(src.id, "", -1, "https://i.ytimg.com/vi/${src.id}/mqdefault.jpg", src.id)))
            is PlaylistClient.Src.Spotify -> list = PlaylistClient.spotifyPlaylist(src.id)
            is PlaylistClient.Src.Text -> {
                list = PlaylistClient.searchSongs(src.q)
                found = PlaylistClient.searchPlaylists(src.q).take(8)
            }
        }
        if (list?.items.isNullOrEmpty()) status = tr("Nothing found", "Bulunamadı")
    }
    val engine = remember { MusicPlayerEngine.get(ctx) }
    val st by engine.state.collectAsState()
    var showQueue by remember { mutableStateOf(false) }
    var gen by remember { mutableIntStateOf(0) }
    var pos by remember { mutableIntStateOf(0) }
    var dur by remember { mutableIntStateOf(0) }
    LaunchedEffect(st.current, st.isPlaying) {
        while (true) { pos = engine.positionMs; dur = engine.durationMs; delay(if (st.isPlaying) 500L else 1500L) }
    }
    fun item(i: PItem, u: String) { synchronized(thumbs) { thumbOf[u] = i.thumb } }
    // The first song starts as soon as its stream is known; the rest of the list is resolved in the background and appended.
    fun play(from: Int) {
        val l = list ?: return
        val mine = ++gen
        scope.launch {
            busy = true
            val first = l.items.drop(from).take(1)
            val got = PlaylistClient.audioUrls(first)
            busy = false
            if (got.isEmpty()) { toast(tr("Cannot play it", "Çalınamıyor")); return@launch }
            got.forEach { (i, u) -> item(i, u) }
            engine.playQueue(got.map { (i, u) -> Track(u, "${i.artist} - ${i.title}", i.durSec) })
            l.items.drop(from + 1).chunked(4).forEach { chunk ->
                if (mine != gen) return@launch
                val more = PlaylistClient.audioUrls(chunk)
                if (mine != gen) return@launch
                more.forEach { (i, u) -> item(i, u); engine.addToQueue(Track(u, "${i.artist} - ${i.title}", i.durSec)) }
            }
        }
    }
    fun enqueue(i: PItem) {
        scope.launch {
            val got = PlaylistClient.audioUrls(listOf(i))
            if (got.isEmpty()) { toast(tr("Cannot play it", "Çalınamıyor")); return@launch }
            val (it, u) = got[0]
            item(it, u)
            val t = Track(u, "${it.artist} - ${it.title}", it.durSec)
            if (engine.player.mediaItemCount == 0) engine.playQueue(listOf(t)) else engine.addToQueue(t)
        }
    }
    fun saveM3u() {
        val l = list ?: return
        scope.launch {
            busy = true
            val ids = withContext(Dispatchers.IO) { l.items.map { i -> PlaylistClient.videoId(i)?.let { i to it } } }.filterNotNull()
            val f = File(ctx.getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC), l.name.replace(Regex("[^\\p{L}\\p{N} _-]"), "").trim().ifEmpty { "playlist" } + ".m3u")
            // shortcut: watch links, not stream URLs (those expire); a player needs a YouTube-aware resolver to open them
            runCatching { f.parentFile?.mkdirs(); f.writeText(M3uParser.write(ids.map { (i, id) -> Track("https://www.youtube.com/watch?v=$id", "${i.artist} - ${i.title}", i.durSec) })) }
                .onSuccess { toast(f.absolutePath) }.onFailure { toast(tr("Could not save", "Kaydedilemedi")) }
            busy = false
        }
    }
    fun download(i: PItem?) {
        scope.launch {
            val link = if (i == null) list?.ytPlaylistUrl else PlaylistClient.videoId(i)?.let { "https://www.youtube.com/watch?v=$it" }
            if (link != null) sendToYtdlnis(ctx, link) else toast(tr("Nothing to send", "Gönderilecek yok"))
        }
    }

    Column(Modifier.fillMaxSize().background(com.umutk.pulse.pageBg).padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (list != null) { list = null; found = emptyList() } else onBack() }) { PIcon("back", T.text, 22.dp) }
            com.umutk.pulse.ExtraLookIcon()
            Box(Modifier.weight(1f).border(BorderStroke(1.dp, T.sub), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 10.dp)) {
                if (input.isEmpty()) Text(tr("YouTube / Spotify link or search", "YouTube / Spotify bağlantısı veya arama"), color = T.sub, fontSize = 14.sp, maxLines = 1)
                BasicTextField(input, { input = it }, singleLine = true, textStyle = TextStyle(color = T.text, fontSize = 15.sp), cursorBrush = SolidColor(T.text), modifier = Modifier.fillMaxWidth())
            }
            IconButton(onClick = { if (input.isNotBlank()) open(PlaylistClient.parse(input)) }) { PIcon("search", T.text, 22.dp) }
        }
        if (busy) PLoader()
        status?.let { Text(it, color = T.sub, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp)) }
        list?.let { l ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(l.name, color = T.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                IconButton(onClick = { play(0) }) { PIcon("play", T.text, 22.dp) }
                IconButton(onClick = { saveM3u() }) { PIcon("folder", T.text, 22.dp) }
                if (dl && l.ytPlaylistUrl != null) IconButton(onClick = { download(null) }) { PIcon("download", T.text, 22.dp) }
            }
        }
        if (showQueue) LazyColumn(Modifier.weight(1f)) {
            itemsIndexed(st.queue) { idx, t ->
                Row(Modifier.fillMaxWidth().clickable { engine.playAt(idx) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Thumb(synchronized(thumbs) { thumbOf[t.path] })
                    Text(t.title ?: t.path, color = if (idx == st.index) com.umutk.pulse.Look.accent.copy(alpha = 1f) else T.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 10.dp))
                    IconButton(onClick = { engine.removeFromQueue(idx) }) { PIcon("close", T.sub, 20.dp) }
                }
            }
        } else LazyColumn(Modifier.weight(1f)) {
            itemsIndexed(found) { _, (id, name) ->
                Text(name, color = T.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().clickable { open(PlaylistClient.Src.YtList(id)) }.padding(vertical = 10.dp))
            }
            itemsIndexed(list?.items ?: emptyList()) { idx, it ->
                Row(Modifier.fillMaxWidth().clickable { play(idx) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Thumb(it.thumb)
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text(it.title, color = T.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(listOf(it.artist, mmss(it.durSec)).filter { s -> s.isNotEmpty() }.joinToString(" · "), color = T.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { enqueue(it) }) { PIcon("newfolder", T.sub, 20.dp) }
                    if (dl) IconButton(onClick = { download(it) }) { PIcon("download", T.sub, 20.dp) }
                }
            }
        }
        // the player: the same engine as the Music page, so it keeps playing in the background and drives the widgets
        st.current?.let { cur ->
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Thumb(synchronized(thumbs) { thumbOf[cur.path] })
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text(cur.title ?: "", color = T.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${mmss(pos / 1000)} / ${mmss(dur / 1000)}", color = T.sub, fontSize = 12.sp)
                    }
                    IconButton(onClick = { showQueue = !showQueue }) { PIcon("sort", if (showQueue) com.umutk.pulse.Look.accent.copy(alpha = 1f) else T.text, 22.dp) }
                }
                var drag by remember { mutableStateOf<Float?>(null) }
                PSlider(drag ?: pos.toFloat(), { drag = it }, 0f..maxOf(dur, 1).toFloat(), onDone = { drag?.let { engine.seekTo(it.toInt()); pos = it.toInt() }; drag = null })
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { engine.player.shuffleModeEnabled = !engine.player.shuffleModeEnabled }) { PIcon("dice", if (engine.player.shuffleModeEnabled) com.umutk.pulse.Look.accent.copy(alpha = 1f) else T.sub, 22.dp) }
                    IconButton(onClick = engine::previous) { PIcon("back", T.text, 24.dp) }
                    IconButton(onClick = engine::togglePlayPause) { PIcon(if (st.isPlaying) "pause" else "play", T.text, 30.dp) }
                    IconButton(onClick = engine::next) { PIcon("back", T.text, 24.dp, Modifier.graphicsLayer { rotationZ = 180f }) }
                    IconButton(onClick = { engine.player.repeatMode = if (engine.player.repeatMode == 0) 2 else 0 }) { PIcon("rotate", if (engine.player.repeatMode != 0) com.umutk.pulse.Look.accent.copy(alpha = 1f) else T.sub, 22.dp) }
                }
            }
        }
    }
}
