package com.umutk.pulse.extras.spotify

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.view.KeyEvent
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.umutk.pulse.PButton
import com.umutk.pulse.PCard
import com.umutk.pulse.PField
import com.umutk.pulse.PIcon
import com.umutk.pulse.PLoader
import com.umutk.pulse.T
import com.umutk.pulse.extras.music.MusicLibrary
import com.umutk.pulse.extras.music.Playlists
import com.umutk.pulse.extras.music.Track
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL
import java.net.URLEncoder

private enum class Tab(val icon: String) { Now("volume"), Search("search"), Lists("details"), Liked("heart") }

/**
 * Spotify client built on the official Web API. Everything played comes from the Spotify app itself (remote control),
 * the 30 s [SpTrack.previewUrl] clip where Spotify still offers one, or a link. This module never touches Spotify audio streams.
 */
object SpotifyHub {
    @Composable
    fun Screen(onClose: () -> Unit) {
        val ctx = LocalContext.current
        LaunchedEffect(Unit) { SpotifyAuth.restore(ctx) }
        BackHandler(onBack = onClose)
        if (!SpotifyAuth.loggedIn) LoginPage(onClose) else Main(onClose)
    }
}

@Composable
private fun LoginPage(onClose: () -> Unit) {
    val ctx = LocalContext.current
    var id by remember { mutableStateOf(if (SpotifyAuth.hasClientId(ctx)) SpotifyAuth.clientId(ctx) else "") }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        IconButton(onClick = onClose) { PIcon("back", T.text, 22.dp) }
        PField(id, { id = it }, tr("Client ID", "İstemci kimliği"), Modifier.fillMaxWidth())
        PButton(tr("Log in", "Giriş yap"), Modifier.fillMaxWidth(), icon = null) {
            SpotifyAuth.error = null
            SpotifyAuth.setClientId(ctx, id); SpotifyAuth.login(ctx)
        }
        SpotifyAuth.error?.let { Text(it, color = T.sub, fontSize = 13.sp) }
        Text(SpotifyAuth.REDIRECT, color = T.sub, fontSize = 12.sp)
    }
}

@Composable
private fun Main(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val api = remember { SpotifyApi(ctx) }
    var tab by remember { mutableStateOf(Tab.Now) }
    var video by remember { mutableStateOf<SpTrack?>(null) }
    val preview = remember { Preview() }
    DisposableEffect(Unit) { onDispose { preview.stop() } }
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { PIcon("back", T.text, 22.dp) }
            Tab.values().forEach { t ->
                IconButton(onClick = { tab = t }) { PIcon(t.icon, if (t == tab) com.umutk.pulse.Look.accent else T.sub, 24.dp) }
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { SpotifyAuth.logout(ctx) }) { PIcon("power", T.sub, 22.dp) }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                Tab.Now -> NowPlaying(api) { video = it }
                Tab.Search -> SearchTab(api, preview) { video = it }
                Tab.Lists -> ListsTab(api, preview) { video = it }
                Tab.Liked -> TrackList({ api.liked() }, api, preview, { video = it }, null)
            }
        }
    }
    video?.let { VideoDialog(it) { video = null } }
}

/** Small 30 s preview player; one clip at a time. */
private class Preview {
    private var mp: MediaPlayer? = null
    var url by mutableStateOf<String?>(null)
        private set

    fun toggle(u: String) {
        val was = url
        stop()
        if (was == u) return
        url = u
        mp = MediaPlayer().apply {
            setAudioAttributes(AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).setUsage(AudioAttributes.USAGE_MEDIA).build())
            runCatching { setDataSource(u); setOnPreparedListener { it.start() }; setOnCompletionListener { stop() }; prepareAsync() }.onFailure { stop() }
        }
    }

    fun stop() { runCatching { mp?.release() }; mp = null; url = null }
}

/** Remote control: Web API first, media keys to whatever player is active as the fallback. */
private fun mediaKey(ctx: Context, code: Int) {
    val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code)); am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
}

private fun openSpotify(ctx: Context, t: SpTrack) {
    val i = Intent(Intent.ACTION_VIEW, Uri.parse(t.uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { ctx.startActivity(i) }.onFailure { t.url?.let { u -> runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } } }
}

@Composable
private fun Cover(url: String?, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    val bmp by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, url) {
        value = url?.let { u -> withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeStream(URL(u).openStream())?.asImageBitmap() }.getOrNull() } }
    }
    val b = bmp
    if (b != null) Image(b, null, modifier.size(size), contentScale = ContentScale.Crop) else Box(modifier.size(size))
}

@Composable
private fun NowPlaying(api: SpotifyApi, onVideo: (SpTrack) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var now by remember { mutableStateOf<SpNow?>(null) }
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(tick) {
        while (true) { now = api.now(); delay(3000) }
    }
    fun cmd(f: suspend () -> Boolean, key: Int) = scope.launch { if (!f()) mediaKey(ctx, key); delay(600); tick++ }
    val n = now
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (n == null) { Spacer(Modifier.weight(1f)); PIcon("volume", T.sub, 40.dp); Spacer(Modifier.weight(1f)) }
        else {
            Cover(n.track.image, 280.dp, Modifier.aspectRatio(1f).fillMaxWidth())
            Text(n.track.title, color = T.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(n.track.artist, color = T.sub, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { cmd({ api.previous() }, KeyEvent.KEYCODE_MEDIA_PREVIOUS) }) { com.umutk.pulse.extras.ui.TIcon("prev", T.text, 26.dp) }
                IconButton(onClick = { if (n.playing) cmd({ api.pause() }, KeyEvent.KEYCODE_MEDIA_PAUSE) else cmd({ api.play() }, KeyEvent.KEYCODE_MEDIA_PLAY) }) {
                    PIcon(if (n.playing) "pause" else "play", T.text, 36.dp)
                }
                IconButton(onClick = { cmd({ api.next() }, KeyEvent.KEYCODE_MEDIA_NEXT) }) { com.umutk.pulse.extras.ui.TIcon("next", T.text, 26.dp) }
                IconButton(onClick = { onVideo(n.track) }) { PIcon("view", T.text, 24.dp) }
            }
        }
    }
}

@Composable
private fun TrackRow(t: SpTrack, api: SpotifyApi, preview: Preview, onVideo: (SpTrack) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    Row(Modifier.fillMaxWidth().clickable { scope.launch { if (!api.play(t.uri)) openSpotify(ctx, t) } }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Cover(t.image, 48.dp)
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(t.title, color = T.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(t.artist, color = T.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        t.previewUrl?.let { u -> IconButton(onClick = { preview.toggle(u) }) { PIcon(if (preview.url == u) "pause" else "play", T.sub, 22.dp) } }
        IconButton(onClick = { onVideo(t) }) { PIcon("view", T.sub, 22.dp) }
    }
}

@Composable
private fun TrackList(load: suspend () -> List<SpTrack>, api: SpotifyApi, preview: Preview, onVideo: (SpTrack) -> Unit, header: (@Composable () -> Unit)?, key: Any = Unit) {
    var items by remember(key) { mutableStateOf<List<SpTrack>?>(null) }
    LaunchedEffect(key) { items = load() }
    val l = items
    Column(Modifier.fillMaxSize()) {
        header?.invoke()
        if (l == null) Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) { PLoader() }
        else LazyColumn(Modifier.weight(1f)) { items(l) { TrackRow(it, api, preview, onVideo) } }
    }
}

@Composable
private fun SearchTab(api: SpotifyApi, preview: Preview, onVideo: (SpTrack) -> Unit) {
    var q by remember { mutableStateOf("") }
    var go by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        PField(q, { q = it }, tr("Search", "Ara"), Modifier.fillMaxWidth().padding(bottom = 8.dp), onDone = { go = q.trim() })
        if (go.isNotEmpty()) TrackList({ api.search(go) }, api, preview, onVideo, null, go)
    }
}

@Composable
private fun ListsTab(api: SpotifyApi, preview: Preview, onVideo: (SpTrack) -> Unit) {
    var lists by remember { mutableStateOf<List<SpPlaylist>?>(null) }
    var open by remember { mutableStateOf<SpPlaylist?>(null) }
    LaunchedEffect(Unit) { lists = api.playlists() }
    val o = open
    if (o != null) {
        BackHandler { open = null }
        ImportableList(o, api, preview, onVideo) { open = null }
        return
    }
    val l = lists
    if (l == null) Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) { PLoader() }
    else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(l) { p ->
            PCard(Modifier.fillMaxWidth().clickable { open = p }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Cover(p.image, 48.dp)
                    Column(Modifier.padding(start = 10.dp)) {
                        Text(p.name, color = T.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${p.count}", color = T.sub, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportableList(p: SpPlaylist, api: SpotifyApi, preview: Preview, onVideo: (SpTrack) -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    TrackList({ api.playlistTracks(p.id) }, api, preview, onVideo, {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { PIcon("back", T.text, 22.dp) }
            Text(p.name, color = T.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            result?.let { Text(it, color = T.sub, fontSize = 13.sp) }
            if (busy) PLoader() else IconButton(onClick = {
                scope.launch {
                    busy = true
                    val all = api.playlistTracks(p.id)
                    val (saved, total) = importPlaylist(ctx, p.name, all)
                    result = "$saved/$total"; busy = false
                }
            }) { PIcon("download", T.text, 22.dp) }
        }
    }, p.id)
}

private fun norm(s: String) = s.lowercase().replace(Regex("\\(.*?\\)|\\[.*?]"), " ").replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

/** Matches Spotify tracks to the device's music by title, artist and length, and saves the found files as a Pulse playlist. Returns (matched, total). */
suspend fun importPlaylist(ctx: Context, name: String, tracks: List<SpTrack>): Pair<Int, Int> = withContext(Dispatchers.IO) {
    if (MusicLibrary.tracks.value.isEmpty()) MusicLibrary.refresh(ctx)
    val local = MusicLibrary.tracks.value
    val byTitle = local.groupBy { norm(it.title ?: it.path.substringAfterLast('/').substringBeforeLast('.')) }
    val found = tracks.mapNotNull { t ->
        val cands = byTitle[norm(t.title)] ?: return@mapNotNull null
        val artists = norm(t.artist).split(' ').filter { it.length > 2 }.toSet()
        cands.firstOrNull { c ->
            val ca = norm(c.artist.orEmpty()).split(' ').toSet()
            (artists.isEmpty() || artists.any { it in ca } || c.path.lowercase().let { p -> artists.any { it in norm(p) } }) &&
                (c.durationSec <= 0 || kotlin.math.abs(c.durationSec * 1000L - t.durationMs) < 7000)
        }
    }
    if (found.isNotEmpty()) Playlists.save(ctx, name, found.distinctBy { it.path })
    found.size to tracks.size
}

/** Music-video preview: a YouTube search page in a WebView (YouTube's own player and terms), nothing downloaded. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun VideoDialog(t: SpTrack, onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().padding(8.dp).background(androidx.compose.ui.graphics.Color.Black)) {
            IconButton(onClick = onDismiss) { PIcon("close", androidx.compose.ui.graphics.Color.White, 22.dp) }
            AndroidView({ c ->
                WebView(c).apply {
                    settings.javaScriptEnabled = true; settings.mediaPlaybackRequiresUserGesture = true
                    loadUrl("https://m.youtube.com/results?search_query=" + URLEncoder.encode("${t.artist} ${t.title} official video", "UTF-8"))
                }
            }, Modifier.fillMaxSize(), onRelease = { it.destroy() })
        }
    }
}
