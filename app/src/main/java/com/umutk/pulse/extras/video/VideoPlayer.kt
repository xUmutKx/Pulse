package com.umutk.pulse.extras.video

import android.Manifest
import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Rational
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.umutk.pulse.LocalWindowLook
import com.umutk.pulse.Look
import com.umutk.pulse.PButton
import com.umutk.pulse.PIcon
import com.umutk.pulse.PRadio
import com.umutk.pulse.PSlider
import com.umutk.pulse.T
import com.umutk.pulse.WinAlert
import com.umutk.pulse.extras.watchdog.MediaService
import com.umutk.pulse.extras.watchdog.VideoPlaybackService
import com.umutk.pulse.tr
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

private class Play(val items: List<VideoItem>, val index: Int)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Video app: folders of the device (MediaStore), a file picker and a URL box; a tap plays. */
@Composable
fun VideoHub(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var play by remember { mutableStateOf<Play?>(null) }
    var folder by remember { mutableStateOf<String?>(null) }
    var videos by remember { mutableStateOf<List<VideoItem>?>(null) }
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED) }
    var urlDialog by remember { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) play = Play(listOf(VideoItem(u, displayName(ctx, u), 0, "")), 0)
    }
    LaunchedEffect(granted, play == null) {
        if (granted && play == null) videos = withContext(Dispatchers.IO) { queryVideos(ctx) }
    }
    BackHandler(enabled = play == null) { if (folder != null) folder = null else onBack() }

    play?.let { p ->
        VideoPlayerScreen(p.items[p.index].uri, onBack = { play = null }, queue = p.items, index = p.index)
        return
    }

    val all = videos.orEmpty()
    val folders = all.groupBy { it.folder }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            VBtn({ if (folder != null) folder = null else onBack() }) { PIcon("back", T.text, 24.dp) }
            Text(folder ?: tr("Video", "Video"), Modifier.weight(1f), color = T.text, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            VBtn({ pick.launch(arrayOf("video/*")) }) { PIcon("folder", T.text, 24.dp) }
            VBtn({ urlDialog = true }) { PIcon("wifi", T.text, 24.dp) }
        }
        if (!granted) {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                PButton(tr("Allow video access", "Video erişimine izin ver"), icon = Icons.Outlined.FolderOpen) { ask.launch(Manifest.permission.READ_MEDIA_VIDEO) }
            }
        } else if (folder == null) {
            LazyColumn(Modifier.fillMaxSize()) {
                items(folders.keys.toList()) { name ->
                    val list = folders.getValue(name)
                    Row(Modifier.fillMaxWidth().clickable { folder = name }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(96.dp, 54.dp).background(T.panel, RoundedCornerShape(6.dp))) {
                            Thumb(list.first(), Modifier.fillMaxSize())
                            PIcon("folder", Color.White, 20.dp, Modifier.align(Alignment.BottomStart).padding(4.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(name.ifBlank { "/" }, Modifier.weight(1f), color = T.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(list.size.toString(), color = T.sub, fontSize = 13.sp)
                    }
                }
            }
        } else {
            val list = folders[folder].orEmpty()
            LazyColumn(Modifier.fillMaxSize()) {
                items(list.size) { i ->
                    val v = list[i]
                    Row(Modifier.fillMaxWidth().clickable { play = Play(list, i) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(96.dp, 54.dp).background(T.panel, RoundedCornerShape(6.dp))) {
                            Thumb(v, Modifier.fillMaxSize())
                            Text(clock(v.durMs), Modifier.align(Alignment.BottomEnd).background(Color(0x99000000)).padding(horizontal = 4.dp), color = Color.White, fontSize = 11.sp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(v.name, Modifier.weight(1f), color = T.text, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
    if (urlDialog) UrlDialog(onDismiss = { urlDialog = false }) { u -> urlDialog = false; play = Play(listOf(VideoItem(u, u.lastPathSegment ?: u.toString(), 0, "")), 0) }
}

@Composable
private fun Thumb(v: VideoItem, modifier: Modifier) {
    val bmp = videoThumb(v)
    if (bmp != null) Image(bmp, null, modifier.then(Modifier.background(Color.Black, RoundedCornerShape(6.dp))), contentScale = ContentScale.Crop)
}

@Composable
private fun UrlDialog(onDismiss: () -> Unit, onOk: (Uri) -> Unit) {
    var text by remember { mutableStateOf("https://") }
    val w = LocalWindowLook.current
    WinAlert(
        onDismissRequest = onDismiss,
        title = { Text("URL", color = w.text) },
        text = {
            BasicTextField(
                text, { text = it }, singleLine = true,
                textStyle = TextStyle(color = w.text, fontSize = 14.sp), cursorBrush = SolidColor(Look.accent),
                modifier = Modifier.fillMaxWidth().border(1.dp, w.sub.copy(alpha = .5f)).padding(8.dp),
            )
        },
        confirmButton = { PButton(tr("Play", "Oynat")) { if (text.startsWith("http", true)) onOk(Uri.parse(text.trim())) } },
        dismissButton = { PButton(tr("Cancel", "Vazgeç"), onClick = onDismiss) },
    )
}

private fun displayName(ctx: Context, u: Uri): String =
    runCatching {
        ctx.contentResolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
    }.getOrNull() ?: u.lastPathSegment.orEmpty()

@Composable
private fun VBtn(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.size(44.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) { content() }
}

/** Text glyph for the few buttons the icon set has no picture for. */
@Composable
private fun Tag(text: String, on: Boolean) {
    Text(text, color = if (on) Look.accent.copy(alpha = 1f) else Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
}

@Composable
private fun LockGlyph(tint: Color, size: Dp = 24.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        drawRoundRect(tint, Offset(w * .2f, w * .45f), Size(w * .6f, w * .4f), CornerRadius(w * .06f))
        drawArc(tint, 180f, 180f, false, Offset(w * .3f, w * .15f), Size(w * .4f, w * .6f), style = Stroke(w * .09f))
    }
}

private class TrackRow(val label: String, val group: Tracks.Group?, val idx: Int, val selected: Boolean)

private fun trackRows(tracks: Tracks, type: Int): List<TrackRow> {
    val out = ArrayList<TrackRow>()
    tracks.groups.filter { it.type == type }.forEach { g ->
        for (i in 0 until g.length) {
            if (!g.isTrackSupported(i)) continue
            val f = g.getTrackFormat(i)
            val lang = f.language?.takeIf { it != "und" }?.let { Locale(it).displayLanguage }
            val ch = if (type == C.TRACK_TYPE_AUDIO && f.channelCount > 0) "${f.channelCount}ch" else null
            val label = listOfNotNull(lang, f.label?.takeIf { it != lang }, ch).joinToString(" ").ifBlank { "#" + (out.size + 1) }
            out += TrackRow(label, g, i, g.isTrackSelected(i))
        }
    }
    return out
}

/** Plays [queue] (or just [uri]) from [index]. Gestures, tracks, PiP, resume, background audio: see the controls. */
@Composable
fun VideoPlayerScreen(uri: Uri, onBack: () -> Unit, queue: List<VideoItem> = emptyList(), index: Int = 0) {
    val ctx = LocalContext.current
    val act = remember(ctx) { ctx.findActivity() }
    val view = LocalView.current
    val prefs = remember { VideoPrefs(ctx) }
    val items = remember(uri, queue) { queue.ifEmpty { listOf(VideoItem(uri, uri.lastPathSegment.orEmpty(), 0, "")) } }
    val player = remember(items) {
        val at = index.coerceIn(items.indices)
        ExoPlayer.Builder(ctx).build().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
            setMediaItems(items.map { MediaItem.Builder().setUri(it.uri).setMediaId(it.uri.toString()).build() }, at, prefs.resume(items[at].uri.toString()))
            repeatMode = if (prefs.loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            prepare()
            playWhenReady = true
        }
    }

    var playing by remember { mutableStateOf(false) }
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    var seeking by remember { mutableStateOf(false) }
    var cur by remember { mutableIntStateOf(index.coerceIn(items.indices)) }
    var tracks by remember { mutableStateOf(Tracks.EMPTY) }
    var vsize by remember { mutableStateOf(VideoSize.UNKNOWN) }
    var inPip by remember { mutableStateOf(false) }
    var shown by remember { mutableStateOf(true) }
    var poke by remember { mutableIntStateOf(0) }
    var locked by remember { mutableStateOf(false) }
    var dlg by remember { mutableStateOf<String?>(null) }
    var speed by remember { mutableFloatStateOf(1f) }
    var loop by remember { mutableStateOf(prefs.loop) }
    var bg by remember { mutableStateOf(prefs.bg) }
    var aspect by remember { mutableIntStateOf(prefs.aspect) }
    var orient by remember { mutableIntStateOf(prefs.orient) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var hud by remember { mutableStateOf<Pair<String, String>?>(null) } // icon key, text
    var hudStamp by remember { mutableIntStateOf(0) }
    fun say(icon: String, text: String) { hud = icon to text; hudStamp++ }

    fun saveResume() {
        player.currentMediaItem?.let { prefs.setResume(it.mediaId, player.currentPosition, player.duration.coerceAtLeast(0L)) }
    }

    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onTracksChanged(t: Tracks) { tracks = t }
            override fun onVideoSizeChanged(v: VideoSize) { vsize = v }
            override fun onMediaItemTransition(m: MediaItem?, reason: Int) {
                cur = player.currentMediaItemIndex
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK) {
                    m?.let { val r = prefs.resume(it.mediaId); if (r > 0) player.seekTo(r) }
                }
            }
        }
        player.addListener(l)
        onDispose { saveResume(); player.removeListener(l); player.release() }
    }

    // the screen stays on, the system bars hide, the options of the window return to normal on leave
    DisposableEffect(act) {
        val win = act?.window
        val ctl = win?.let { WindowCompat.getInsetsController(it, view) }
        val oldBright = win?.attributes?.screenBrightness ?: -1f
        win?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        ctl?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        ctl?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            win?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            ctl?.show(WindowInsetsCompat.Type.systemBars())
            win?.attributes = win.attributes.also { it.screenBrightness = oldBright }
            act?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            runCatching { act?.setPictureInPictureParams(PictureInPictureParams.Builder().setAutoEnterEnabled(false).build()) }
            MediaService.stop(ctx, VideoPlaybackService::class.java)
        }
    }

    // pause when the app goes away, unless PiP or the background audio option keeps it going
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, player) {
        val observer = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_STOP) {
                if (act?.isInPictureInPictureMode == true) Unit
                else if (prefs.bg) player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true).build()
                else player.pause()
                saveResume()
            } else if (e == Lifecycle.Event.ON_START) {
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false).build()
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(player) {
        var n = 0
        while (true) {
            if (!seeking) pos = player.currentPosition
            dur = player.duration.coerceAtLeast(0L)
            inPip = act?.isInPictureInPictureMode == true
            if (++n % 8 == 0 && player.isPlaying) saveResume()
            delay(250)
        }
    }
    LaunchedEffect(bg) { if (bg) MediaService.start(ctx, VideoPlaybackService::class.java) else MediaService.stop(ctx, VideoPlaybackService::class.java) }
    LaunchedEffect(shown, playing, poke, dlg) { if (shown && playing && dlg == null) { delay(3500); shown = false } }
    LaunchedEffect(hudStamp) { delay(900); hud = null }
    LaunchedEffect(orient, vsize) {
        act?.requestedOrientation = when (orient) {
            1 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            2 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            3 -> if (vsize.width == 0) ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED else if (vsize.width >= vsize.height) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    // PiP: the window shape follows the video, and leaving the app while playing enters it by itself
    fun pipParams(): PictureInPictureParams {
        val b = PictureInPictureParams.Builder().setAutoEnterEnabled(playing && !bg)
        if (vsize.width > 0 && vsize.height > 0) {
            val r = (vsize.width.toFloat() / vsize.height).coerceIn(0.42f, 2.39f)
            b.setAspectRatio(Rational((r * 1000).roundToInt(), 1000))
        }
        return b.build()
    }
    LaunchedEffect(playing, vsize, bg) { runCatching { act?.setPictureInPictureParams(pipParams()) } }

    val audio = remember { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVol = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var volF by remember { mutableFloatStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()) }
    var bright by remember { mutableFloatStateOf((act?.window?.attributes?.screenBrightness ?: -1f).let { if (it < 0) .5f else it }) }

    val pickSub = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) {
            val name = displayName(ctx, u).lowercase()
            val mime = when {
                name.endsWith(".ass") || name.endsWith(".ssa") -> MimeTypes.TEXT_SSA
                name.endsWith(".vtt") -> MimeTypes.TEXT_VTT
                name.endsWith(".ttml") || name.endsWith(".xml") -> MimeTypes.APPLICATION_TTML
                else -> MimeTypes.APPLICATION_SUBRIP
            }
            val i = player.currentMediaItemIndex
            val at = player.currentPosition
            val sub = MediaItem.SubtitleConfiguration.Builder(u).setMimeType(mime).setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()
            player.replaceMediaItem(i, player.currentMediaItem!!.buildUpon().setSubtitleConfigurations(listOf(sub)).build())
            player.seekTo(i, at)
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build()
            dlg = null
        }
    }

    BackHandler { if (!locked) onBack() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val shape = if (aspect == 3) Modifier.align(Alignment.Center).aspectRatio(16f / 9f) else Modifier.fillMaxSize()
        Box(shape.graphicsLayer { scaleX = zoom; scaleY = zoom }) {
            AndroidView(
                factory = { PlayerView(it).apply { useController = false; setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER) } },
                update = {
                    it.player = player
                    it.resizeMode = when (aspect) { 1, 3 -> AspectRatioFrameLayout.RESIZE_MODE_FILL; 2 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM; else -> AspectRatioFrameLayout.RESIZE_MODE_FIT }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // touch layer: taps outside, drags and pinch inside (the inner pointerInput consumes first)
        Box(
            Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { shown = !shown },
                        onDoubleTap = { o ->
                            if (locked) return@detectTapGestures
                            val f = o.x / size.width
                            when {
                                f < .33f -> { player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0)); say("back", "-10s") }
                                f > .66f -> { player.seekTo(player.currentPosition + 10_000); say("play", "+10s") }
                                else -> if (player.isPlaying) player.pause() else player.play()
                            }
                        },
                    )
                }
                .pointerInput(locked) {
                    if (locked) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val slop = viewConfiguration.touchSlop * 2
                        var mode = 0 // 1 seek, 2 brightness, 3 volume, 4 pinch
                        var tx = 0f
                        var ty = 0f
                        val start = player.currentPosition
                        var target = start
                        do {
                            val ev = awaitPointerEvent()
                            if (ev.changes.count { it.pressed } >= 2) {
                                mode = 4
                                val z = ev.calculateZoom()
                                if (z != 1f) { zoom = (zoom * z).coerceIn(1f, 4f); say("scale", "${(zoom * 100).roundToInt()}%") }
                                ev.changes.forEach { it.consume() }
                            } else if (mode != 4) {
                                val c = ev.changes.first()
                                val d = c.position - c.previousPosition
                                tx += d.x; ty += d.y
                                if (mode == 0 && hypot(tx, ty) > slop) mode = if (abs(tx) > abs(ty)) 1 else if (down.position.x < w / 2) 2 else 3
                                when (mode) {
                                    1 -> {
                                        target = (start + tx / w * 90_000).toLong().coerceIn(0, dur.coerceAtLeast(1))
                                        say("play", clock(target) + " (" + (if (target >= start) "+" else "-") + abs(target - start) / 1000 + "s)")
                                    }
                                    2 -> {
                                        bright = (bright - d.y / h * 1.2f).coerceIn(.01f, 1f)
                                        act?.window?.let { win -> win.attributes = win.attributes.also { it.screenBrightness = bright } }
                                        say("light", "${(bright * 100).roundToInt()}%")
                                    }
                                    3 -> {
                                        volF = (volF - d.y / h * maxVol * 1.2f).coerceIn(0f, maxVol.toFloat())
                                        audio.setStreamVolume(AudioManager.STREAM_MUSIC, volF.roundToInt(), 0)
                                        say("volume", "${(volF / maxVol * 100).roundToInt()}%")
                                    }
                                }
                                if (mode != 0) c.consume()
                            }
                        } while (ev.changes.any { it.pressed })
                        if (mode == 1) player.seekTo(target)
                    }
                },
        )

        hud?.let { (icon, text) ->
            Row(Modifier.align(Alignment.Center).background(Color(0x99000000), RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                PIcon(icon, Color.White, 22.dp)
                Spacer(Modifier.width(8.dp))
                Text(text, color = Color.White, fontSize = 16.sp)
            }
        }

        if (locked && shown && !inPip) {
            Box(Modifier.align(Alignment.CenterStart).padding(16.dp).size(48.dp).background(Color(0x99000000), RoundedCornerShape(24.dp)).clickable { locked = false; poke++ }, contentAlignment = Alignment.Center) {
                LockGlyph(Look.accent.copy(alpha = 1f))
            }
        }

        if (!locked && shown && !inPip) {
            Row(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xAA000000), Color.Transparent))).padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VBtn(onBack) { PIcon("back", Color.White, 24.dp) }
                Text(items.getOrNull(cur)?.name.orEmpty(), Modifier.weight(1f), color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                VBtn({ dlg = "audio" }) { PIcon("volume", Color.White, 24.dp) }
                VBtn({ dlg = "text" }) { Tag("CC", tracks.isTypeSelected(C.TRACK_TYPE_TEXT)) }
                VBtn({ dlg = "speed" }) { Tag(if (speed % 1f == 0f) "${speed.toInt()}x" else "${speed}x", speed != 1f) }
            }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xAA000000)))).padding(horizontal = 12.dp, vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(clock(pos), color = Color.White, fontSize = 12.sp)
                    Box(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        PSlider(
                            value = pos.toFloat(),
                            onChange = { seeking = true; pos = it.toLong(); poke++ },
                            range = 0f..dur.coerceAtLeast(1L).toFloat(),
                            onDone = { seeking = false; player.seekTo(pos) },
                        )
                    }
                    Text(clock(dur), color = Color.White, fontSize = 12.sp)
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally)) {
                    VBtn({ player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0)); poke++ }) { Tag("-10", false) }
                    if (items.size > 1) VBtn({ player.seekToPreviousMediaItem(); poke++ }) { Tag("|<", false) }
                    VBtn({ if (player.isPlaying) player.pause() else player.play(); poke++ }) { PIcon(if (playing) "pause" else "play", Color.White, 32.dp) }
                    if (items.size > 1) VBtn({ player.seekToNextMediaItem(); poke++ }) { Tag(">|", false) }
                    VBtn({ player.seekTo(player.currentPosition + 10_000); poke++ }) { Tag("+10", false) }
                    VBtn({ loop = !loop; prefs.loop = loop; player.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF; poke++ }) { Tag("∞", loop) }
                    VBtn({
                        aspect = (aspect + 1) % 4; prefs.aspect = aspect; zoom = 1f; poke++
                        say("scale", listOf(tr("Fit", "Sığdır"), tr("Stretch", "Uzat"), tr("Crop", "Kırp"), "16:9")[aspect])
                    }) { PIcon("scale", Color.White, 24.dp) }
                    VBtn({
                        orient = (orient + 1) % 4; prefs.orient = orient; poke++
                        say("rotate", listOf(tr("System", "Sistem"), tr("Landscape", "Yatay"), tr("Portrait", "Dikey"), tr("By video", "Videoya göre"))[orient])
                    }) { PIcon("rotate", Color.White, 24.dp) }
                    VBtn({ bg = !bg; prefs.bg = bg; poke++ }) { Tag("BG", bg) }
                    VBtn({ locked = true; shown = true; poke++ }) { LockGlyph(Color.White) }
                    VBtn({ runCatching { act?.enterPictureInPictureMode(pipParams()) } }) { Tag("PiP", false) }
                }
            }
        }
    }

    when (dlg) {
        "speed" -> PickDialog("x", listOf(.25f, .5f, .75f, 1f, 1.25f, 1.5f, 2f, 3f).map { s -> (if (s % 1f == 0f) "${s.toInt()}x" else "${s}x") to (s == speed) }, { dlg = null }) { i ->
            speed = listOf(.25f, .5f, .75f, 1f, 1.25f, 1.5f, 2f, 3f)[i]; player.setPlaybackSpeed(speed); dlg = null
        }
        "audio" -> {
            val rows = trackRows(tracks, C.TRACK_TYPE_AUDIO)
            PickDialog(tr("Audio", "Ses"), rows.map { it.label to it.selected }, { dlg = null }) { i ->
                val r = rows[i]
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                    .addOverride(TrackSelectionOverride(r.group!!.mediaTrackGroup, r.idx)).build()
                dlg = null
            }
        }
        "text" -> {
            val rows = trackRows(tracks, C.TRACK_TYPE_TEXT)
            val off = rows.none { it.selected }
            PickDialog(tr("Subtitles", "Altyazı"), listOf(tr("Off", "Kapalı") to off) + rows.map { it.label to it.selected }, { dlg = null },
                extra = { Row(Modifier.fillMaxWidth().clickable { pickSub.launch(arrayOf("*/*")) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    PIcon("folder", LocalWindowLook.current.text, 22.dp); Spacer(Modifier.width(10.dp)); Text(".srt .ass .vtt", color = LocalWindowLook.current.text, fontSize = 14.sp)
                } },
            ) { i ->
                player.trackSelectionParameters = if (i == 0) player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
                else rows[i - 1].let { r ->
                    player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).clearOverridesOfType(C.TRACK_TYPE_TEXT)
                        .addOverride(TrackSelectionOverride(r.group!!.mediaTrackGroup, r.idx)).build()
                }
                dlg = null
            }
        }
    }
}

/** A list of radio rows in the look's own window. */
@Composable
private fun PickDialog(title: String, rows: List<Pair<String, Boolean>>, onDismiss: () -> Unit, extra: (@Composable () -> Unit)? = null, onPick: (Int) -> Unit) {
    val w = LocalWindowLook.current
    WinAlert(
        onDismissRequest = onDismiss,
        title = { Text(title, color = w.text) },
        text = {
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                rows.forEachIndexed { i, (label, on) ->
                    Row(Modifier.fillMaxWidth().clickable { onPick(i) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        PRadio(on) { onPick(i) }
                        Spacer(Modifier.width(8.dp))
                        Text(label, color = w.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                extra?.invoke()
            }
        },
        confirmButton = { PButton(tr("OK", "Tamam"), onClick = onDismiss) },
    )
}
