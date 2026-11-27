package com.umutk.pulse.watch.apps

import android.app.RemoteInput
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.view.SurfaceView
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionToken
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.input.RemoteInputIntentHelper
import com.umutk.pulse.watch.link.PhoneLink
import com.umutk.pulse.watch.link.copyFromPhone
import com.umutk.pulse.watch.link.watchMediaDir
import com.umutk.pulse.watch.ui.Chip
import com.umutk.pulse.watch.ui.Header
import com.umutk.pulse.watch.ui.Page
import com.umutk.pulse.watch.ui.RoundButton
import com.umutk.pulse.watch.ui.T
import com.umutk.pulse.watch.ui.W
import com.umutk.pulse.watch.ui.clock
import com.umutk.pulse.watch.ui.crown
import com.umutk.pulse.watch.ui.tick
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

private val AUDIO = setOf("mp3", "m4a", "aac", "ogg", "opus", "flac", "wav")
private val VIDEO = setOf("mp4", "mkv", "webm", "3gp", "mov")

/** The songs and videos on the watch: what was copied from the phone, and the watch's own Music / Movies / Download folders. */
private fun watchFiles(c: Context, ext: Set<String>): List<File> {
    val roots = listOfNotNull(watchMediaDir(c), c.getExternalFilesDir(null), android.os.Environment.getExternalStorageDirectory())
    val out = LinkedHashMap<String, File>()
    fun walk(d: File, depth: Int) {
        if (depth > 3 || d.name.startsWith(".") || d.name == "Android" && depth > 0) return
        (d.listFiles() ?: return).forEach { f -> if (f.isDirectory) walk(f, depth + 1) else if (f.extension.lowercase() in ext && f.length() > 0) out.putIfAbsent(f.canonicalPath, f) }
    }
    roots.forEach { walk(it, 0) }
    return out.values.sortedBy { it.name.lowercase() }
}

/** The watch's music player: one ExoPlayer for the songs, held by [WatchPlayService] so it plays on with the screen off and the earbuds' buttons work. */
object WatchPlayer {
    private var exo: ExoPlayer? = null
    private var controller: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null
    fun get(c: Context): ExoPlayer = exo ?: ExoPlayer.Builder(c.applicationContext)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
        .setHandleAudioBecomingNoisy(true).build().also { exo = it }
    fun current(): ExoPlayer? = exo

    fun play(c: Context, files: List<File>, start: Int) {
        val p = get(c)
        p.setMediaItems(files.map { f -> MediaItem.Builder().setUri(Uri.fromFile(f)).setMediaMetadata(MediaMetadata.Builder().setTitle(f.nameWithoutExtension).build()).build() }, start, 0L)
        p.prepare(); p.play()
        // a controller starts the session service, which keeps the player alive and shows the media notification
        if (controller == null) controller = MediaController.Builder(c.applicationContext, SessionToken(c.applicationContext, ComponentName(c, WatchPlayService::class.java))).buildAsync()
    }

    fun release() { exo?.release(); exo = null; controller?.let { runCatching { MediaController.releaseFuture(it) } }; controller = null }
}

class WatchPlayService : MediaSessionService() {
    private var session: MediaSession? = null
    override fun onCreate() { super.onCreate(); session = MediaSession.Builder(this, WatchPlayer.get(this)).build() }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session
    override fun onTaskRemoved(rootIntent: Intent?) { if (WatchPlayer.current()?.isPlaying != true) stopSelf() }
    override fun onDestroy() { session?.release(); session = null; WatchPlayer.release(); super.onDestroy() }
}

/** Music on the watch: the songs list, and the player (a ring with the position, the crown is the volume). */
@Composable
fun MusicScreen() {
    val ctx = LocalContext.current
    var songs by remember { mutableStateOf<List<File>?>(null) }
    var showPlayer by remember { mutableStateOf(WatchPlayer.current()?.mediaItemCount ?: 0 > 0) }
    LaunchedEffect(Unit) { songs = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { watchFiles(ctx, AUDIO) } }
    if (showPlayer) { WatchNowPlaying { showPlayer = false }; return }
    Page(showTime = false) {
        val l = songs
        ScalingLazyColumn(Modifier.fillMaxSize()) {
            item { Header("Music") }
            if (WatchPlayer.current()?.mediaItemCount ?: 0 > 0) item { Chip("Now playing", "▶", WatchPlayer.current()?.mediaMetadata?.title?.toString()) { showPlayer = true } }
            if (l == null) item { T("…", color = W.sub) }
            else if (l.isEmpty()) item { T("No songs on the watch. Copy them from the phone (Phone files, YouTube).", color = W.sub, size = 12.sp, align = TextAlign.Center) }
            else {
                item { Chip("Shuffle all", "🔀") { WatchPlayer.play(ctx, l.shuffled(), 0); showPlayer = true } }
                items(l.size) { k -> Chip(l[k].nameWithoutExtension, "♪") { WatchPlayer.play(ctx, l, k); showPlayer = true } }
            }
        }
    }
}

@Composable
private fun WatchNowPlaying(onList: () -> Unit) {
    val ctx = LocalContext.current
    val p = remember { WatchPlayer.get(ctx) }
    val am = remember { ctx.getSystemService(AudioManager::class.java) }
    var title by remember { mutableStateOf("") }
    var playing by remember { mutableStateOf(false) }
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    var vol by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            title = p.mediaMetadata.title?.toString().orEmpty(); playing = p.isPlaying; pos = p.currentPosition; dur = p.duration.coerceAtLeast(0L)
            vol = am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            delay(500L)
        }
    }
    Page(showTime = false) {
        Box(Modifier.fillMaxSize().crown { d -> am.adjustStreamVolume(AudioManager.STREAM_MUSIC, if (d > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, 0); tick(ctx) }) {
            Canvas(Modifier.fillMaxSize().padding(3.dp)) {
                val sw = 5.dp.toPx()
                drawArc(W.card, -90f, 360f, false, style = Stroke(sw))
                if (dur > 0) drawArc(W.accent, -90f, 360f * (pos.toFloat() / dur).coerceIn(0f, 1f), false, style = Stroke(sw, cap = StrokeCap.Round))
                drawArc(W.sub.copy(alpha = .6f), 135f, 90f * vol, false, topLeft = androidx.compose.ui.geometry.Offset(sw * 2, sw * 2),
                    size = androidx.compose.ui.geometry.Size(size.width - sw * 4, size.height - sw * 4), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            }
            Column(Modifier.fillMaxSize().padding(horizontal = 26.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                T(clock(pos) + " / " + clock(dur), color = W.sub, size = 11.sp)
                T(title, size = 15.sp, weight = FontWeight.SemiBold, maxLines = 2, align = TextAlign.Center)
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    RoundButton("⏮", size = 40.dp, textSize = 15.sp) { if (p.currentPosition > 3000L) p.seekTo(0L) else p.seekToPreviousMediaItem() }
                    RoundButton(if (playing) "⏸" else "▶", size = 54.dp, color = W.accent, textColor = W.bg) { if (p.isPlaying) p.pause() else p.play() }
                    RoundButton("⏭", size = 40.dp, textSize = 15.sp) { p.seekToNextMediaItem() }
                }
                T("☰", color = W.sub, size = 16.sp, modifier = Modifier.padding(top = 6.dp).clickable { onList() }.padding(6.dp))
            }
        }
    }
}

/** Video on the watch: the list, and a full-screen player (a tap pauses, the crown seeks 10 s). */
@Composable
fun VideoScreen() {
    val ctx = LocalContext.current
    var vids by remember { mutableStateOf<List<File>?>(null) }
    var playing by remember { mutableStateOf<File?>(null) }
    LaunchedEffect(Unit) { vids = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { watchFiles(ctx, VIDEO) } }
    playing?.let { f -> VideoPlayerPage(f); return }
    Page(showTime = false) {
        val l = vids
        ScalingLazyColumn(Modifier.fillMaxSize()) {
            item { Header("Video") }
            if (l == null) item { T("…", color = W.sub) }
            else if (l.isEmpty()) item { T("No videos on the watch. Copy them from the phone (Phone files).", color = W.sub, size = 12.sp, align = TextAlign.Center) }
            else items(l.size) { k -> Chip(l[k].nameWithoutExtension, "🎬", "${l[k].length() / 1048576} MB") { WatchPlayer.current()?.pause(); playing = l[k] } }
        }
    }
}

@Composable
private fun VideoPlayerPage(f: File) {
    val ctx = LocalContext.current
    val p = remember { ExoPlayer.Builder(ctx).build().apply { setMediaItem(MediaItem.fromUri(Uri.fromFile(f))); repeatMode = Player.REPEAT_MODE_OFF; prepare(); play() } }
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    var paused by remember { mutableStateOf(false) }
    var fit by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        val w = (ctx as? android.app.Activity)?.window
        w?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { p.release(); w?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    LaunchedEffect(Unit) { while (true) { pos = p.currentPosition; dur = p.duration.coerceAtLeast(0L); paused = !p.isPlaying; delay(500L) } }
    Box(Modifier.fillMaxSize().background(W.bg).crown(stepPx = 60f) { d -> p.seekTo((p.currentPosition + d * 10_000L).coerceIn(0L, dur)); tick(ctx) }.clickable { if (p.isPlaying) p.pause() else p.play() }) {
        AndroidView({ c -> SurfaceView(c).also { p.setVideoSurfaceView(it) } }, Modifier.fillMaxSize().padding(if (fit == 0) 18.dp else 0.dp))
        Canvas(Modifier.fillMaxSize().padding(2.dp)) {
            if (dur > 0) drawArc(W.accent.copy(alpha = .8f), -90f, 360f * (pos.toFloat() / dur).coerceIn(0f, 1f), false, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        }
        if (paused) Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            T("▶", size = 30.sp)
            T(clock(pos) + " / " + clock(dur), color = W.text, size = 12.sp)
            T(if (fit == 0) "Fill" else "Fit", color = W.accent, size = 12.sp, modifier = Modifier.padding(top = 6.dp).clickable { fit = 1 - fit }.padding(6.dp))
        }
    }
}

/** YouTube and YouTube Music through Pulse on the phone: search by voice or keys, play on the phone, or save the song to the watch. */
@Composable
fun YouTubeScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var music by remember { mutableStateOf(Prefs.int("ytMusic", 1) == 1) }
    var query by remember { mutableStateOf(Prefs.string("ytLast")) }
    var results by remember { mutableStateOf<List<JSONObject>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf<JSONObject?>(null) }
    var note by remember { mutableStateOf("") }
    fun search(q: String) {
        if (q.isBlank()) return
        query = q; Prefs.putString("ytLast", q)
        scope.launch {
            busy = true; note = ""
            val r = PhoneLink.tryAsk(ctx, "yt_search", JSONObject().put("q", q).put("music", music))
            val a = r?.optJSONArray("items")
            results = if (a == null) emptyList() else (0 until a.length()).map { a.getJSONObject(it) }
            if (r == null) note = PhoneLink.error.ifEmpty { "No phone" }
            busy = false
        }
    }
    val input = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.let { RemoteInput.getResultsFromIntent(it)?.getCharSequence("q")?.toString() }?.trim()?.let { search(it) }
    }
    fun ask() {
        val i: Intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
        RemoteInputIntentHelper.putRemoteInputsExtra(i, listOf(RemoteInput.Builder("q").setLabel("Search").build()))
        runCatching { input.launch(i) }
    }
    LaunchedEffect(Unit) { if (query.isNotBlank() && results == null) search(query) }
    val pk = picked
    if (pk != null) {
        Page {
            ScalingLazyColumn(Modifier.fillMaxSize()) {
                item { T(pk.optString("title"), size = 14.sp, weight = FontWeight.SemiBold, maxLines = 3, align = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
                item { T(pk.optString("ch"), color = W.sub, size = 12.sp, align = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
                if (note.isNotEmpty()) item { T(note, color = W.accent, size = 12.sp, align = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
                item { Chip("Play on the phone", "📱") { scope.launch { tick(ctx); PhoneLink.tryAsk(ctx, "yt_play", JSONObject().put("id", pk.optString("id"))); note = "Playing on the phone" } } }
                item {
                    Chip("Save to the watch", "⌚", if (busy) "…" else "Audio, for offline") {
                        if (busy) return@Chip
                        scope.launch {
                            busy = true; note = "Phone is downloading…"
                            val r = PhoneLink.tryAsk(ctx, "yt_save", JSONObject().put("id", pk.optString("id")).put("title", pk.optString("title")))
                            val path = r?.optString("p").orEmpty()
                            if (path.isEmpty()) { note = r?.optString("err")?.ifEmpty { null } ?: "Failed"; busy = false; return@launch }
                            val f = copyFromPhone(ctx, path, r!!.optLong("size")) { note = "Copying ${(it * 100).toInt()}%" }
                            note = if (f != null) "Saved on the watch" else "Copy stopped"
                            if (f != null) WatchPlayer.play(ctx, listOf(f), 0)
                            busy = false; tick(ctx, strong = true)
                        }
                    }
                }
                item { Chip("Back", "↰") { picked = null; note = "" } }
            }
        }
        return
    }
    Page(showTime = false) {
        ScalingLazyColumn(Modifier.fillMaxSize()) {
            item { Header(if (music) "YouTube Music" else "YouTube") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RoundButton("🔍", size = 44.dp, color = W.accent, textColor = W.bg) { ask() }
                    RoundButton(if (music) "♪" else "▶", size = 44.dp) { music = !music; Prefs.putInt("ytMusic", if (music) 1 else 0); if (query.isNotBlank()) search(query) }
                }
            }
            if (query.isNotBlank()) item { T(query, color = W.sub, size = 12.sp, maxLines = 1) }
            if (busy) item { T("…", color = W.sub) }
            if (note.isNotEmpty()) item { T(note, color = W.sub, size = 12.sp, align = TextAlign.Center) }
            results?.let { l -> items(l) { v -> Chip(v.optString("title"), if (music) "♪" else "▶", v.optString("ch") + "  " + clock(v.optLong("dur") * 1000L)) { picked = v } } }
        }
    }
}

/** The toolkit: the watch's small tools in one list (as Pulse's toolkit on the phone); a tapped icon moves (spins, wobbles, bounces …) before its tool opens. */
@Composable
fun ToolkitScreen() {
    val tools = remember {
        listOf("calc" to "bounce", "convert" to "spin", "tip" to "pop", "counter" to "pulse", "dice" to "spin", "stopwatch" to "wobble", "timer" to "spin", "world" to "spin",
            "compass" to "wobble", "metronome" to "wobble", "light" to "pulse", "breathe" to "pulse", "reaction" to "shake", "battery" to "bounce", "files" to "bounce", "apps" to "pop")
    }
    Page(showTime = false) {
        ScalingLazyColumn(Modifier.fillMaxSize()) {
            item { Header("Toolkit") }
            items(tools.mapNotNull { (r, m) -> Apps.all.firstOrNull { it.route == r }?.let { it to m } }) { (a, m) -> ToolRow(a, m) }
        }
    }
}

@Composable
private fun ToolRow(a: WatchApp, motion: String) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val t = remember { androidx.compose.animation.core.Animatable(0f) }
    Row(
        Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(26.dp)).background(W.card)
            .clickable { scope.launch { tick(ctx); t.snapTo(0f); t.animateTo(1f, androidx.compose.animation.core.tween(420)); Apps.open(a.route); t.snapTo(0f) } }
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val v = t.value; val damp = 1f - v
        val pi = Math.PI.toFloat()
        Box(
            Modifier.size(32.dp).clip(androidx.compose.foundation.shape.CircleShape).background(W.accent.copy(alpha = .22f)).graphicsLayer {
                when (motion) {
                    "spin" -> rotationZ = 360f * (1f - damp * damp * damp)
                    "wobble" -> rotationZ = 20f * damp * kotlin.math.sin(v * 6f * pi)
                    "shake" -> translationX = 6.dp.toPx() * damp * kotlin.math.sin(v * 8f * pi)
                    "bounce" -> translationY = -8.dp.toPx() * kotlin.math.abs(kotlin.math.sin(v * 2f * pi)) * damp
                    "pop" -> { val k = 1f - 0.3f * kotlin.math.sin(v * pi); scaleX = k; scaleY = k }
                    else -> { val k = 1f + 0.25f * kotlin.math.sin(v * 2f * pi) * damp; scaleX = k; scaleY = k }
                }
            },
            contentAlignment = Alignment.Center,
        ) { T(a.icon, size = 16.sp) }
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            T(a.name, size = 15.sp, weight = FontWeight.Medium, maxLines = 1)
            T(a.about, color = W.sub, size = 12.sp, maxLines = 1)
        }
    }
}
