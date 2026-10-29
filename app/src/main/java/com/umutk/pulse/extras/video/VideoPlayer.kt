package com.umutk.pulse.extras.video

import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.MediaMetadataRetriever
import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.net.Uri
import android.util.Rational
import android.util.TypedValue
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flip
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.PictureInPictureAlt
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.umutk.pulse.LocalWindowLook
import com.umutk.pulse.Look
import com.umutk.pulse.PButton
import com.umutk.pulse.PIcon
import com.umutk.pulse.PRadio
import com.umutk.pulse.PSwitch
import com.umutk.pulse.WinAlert
import com.umutk.pulse.bevel
import com.umutk.pulse.extras.watchdog.MediaService
import com.umutk.pulse.extras.watchdog.VideoPlaybackService
import com.umutk.pulse.tr
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

@Composable
internal fun VBtn(onClick: () -> Unit, raised: Boolean = false, content: @Composable () -> Unit) {
    Box(Modifier.size(44.dp).clickable(onClick = onClick).then(if (raised) Modifier.padding(3.dp).bevel(true) else Modifier), contentAlignment = Alignment.Center) { content() }
}

/** Text glyph for the few buttons the icon set has no picture for. */
@Composable
private fun Tag(text: String, on: Boolean, fg: Color = Color.White, accent: Color = Look.accent.copy(alpha = 1f)) {
    Text(text, color = if (on) accent else fg, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
}

@Composable
private fun LockGlyph(tint: Color, size: Dp = 24.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        drawRoundRect(tint, Offset(w * .2f, w * .45f), Size(w * .6f, w * .4f), CornerRadius(w * .06f))
        drawArc(tint, 180f, 180f, false, Offset(w * .3f, w * .15f), Size(w * .4f, w * .6f), style = Stroke(w * .09f))
    }
}

private class TrackRow(val label: String, val group: Tracks.Group, val idx: Int, val selected: Boolean, val lang: String?)

private fun trackRows(tracks: Tracks, type: Int): List<TrackRow> {
    val out = ArrayList<TrackRow>()
    tracks.groups.filter { it.type == type }.forEach { g ->
        for (i in 0 until g.length) {
            if (!g.isTrackSupported(i)) continue
            val f = g.getTrackFormat(i)
            val lang = f.language?.takeIf { it != "und" }?.let { Locale(it).displayLanguage }
            val ch = if (type == C.TRACK_TYPE_AUDIO && f.channelCount > 0) "${f.channelCount}ch" else null
            val label = listOfNotNull(lang, f.label?.takeIf { it != lang }, ch).joinToString(" ").ifBlank { "#" + (out.size + 1) }
            out += TrackRow(label, g, i, g.isTrackSelected(i), f.language?.takeIf { it != "und" })
        }
    }
    return out
}

/** Codec facts of the playing tracks for the info window. */
private fun liveRows(tracks: Tracks): List<Pair<String, String>> {
    val out = ArrayList<Pair<String, String>>()
    for (g in tracks.groups) for (i in 0 until g.length) {
        if (!g.isTrackSelected(i)) continue
        val f = g.getTrackFormat(i)
        when (g.type) {
            C.TRACK_TYPE_VIDEO -> out += tr("Video", "Video") to listOfNotNull(f.sampleMimeType, if (f.width > 0) "${f.width}x${f.height}" else null, f.frameRate.takeIf { it > 0 }?.let { "%.2f fps".format(it) }, f.bitrate.takeIf { it > 0 }?.let { "${it / 1000} kbps" }).joinToString(" ")
            C.TRACK_TYPE_AUDIO -> out += tr("Audio", "Ses") to listOfNotNull(f.sampleMimeType, f.language, if (f.channelCount > 0) "${f.channelCount}ch" else null, f.sampleRate.takeIf { it > 0 }?.let { "$it Hz" }, f.bitrate.takeIf { it > 0 }?.let { "${it / 1000} kbps" }).joinToString(" ")
            C.TRACK_TYPE_TEXT -> out += tr("Subtitles", "Altyazı") to listOfNotNull(f.sampleMimeType, f.language, f.label).joinToString(" ")
        }
    }
    return out
}

private val softFirst = MediaCodecSelector { mime, secure, tunneling ->
    MediaCodecSelector.DEFAULT.getDecoderInfos(mime, secure, tunneling).sortedBy { it.hardwareAccelerated }
}

internal val speeds = listOf(.25f, .5f, .75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f, 4f)

/** Plays [queue] (or just [uri]) from [index]. Gestures, tracks, subtitles, PiP, resume, background audio: see the controls. */
@Composable
fun VideoPlayerScreen(uri: Uri, onBack: () -> Unit, queue: List<VideoItem> = emptyList(), index: Int = 0) {
    val ctx = LocalContext.current
    val act = remember(ctx) { ctx.findActivity() }
    val view = LocalView.current
    val prefs = remember { VideoPrefs(ctx) }
    val items = remember(uri, queue) { queue.ifEmpty { listOf(VideoItem(uri, uri.lastPathSegment.orEmpty(), 0, "")) } }
    val player = remember(items) {
        val at = index.coerceIn(items.indices)
        val rf = DefaultRenderersFactory(ctx).setExtensionRendererMode(
            when (prefs.decoder) { 0 -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON; 1 -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER; else -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF },
        ).also { if (prefs.decoder == 1) it.setMediaCodecSelector(softFirst) }
        rf.setEnableDecoderFallback(true); rf.applyDecoder(prefs)
        ExoPlayer.Builder(ctx, rf).net(ctx, prefs, items.any { it.uri.scheme?.startsWith("http") == true }).build().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), prefs.audioFocus)
            setHandleAudioBecomingNoisy(prefs.pauseNoisy)
            setMediaItems(
                items.map { MediaItem.Builder().setUri(it.uri).setMediaId(it.key).setSubtitleConfigurations(siblingSubs(ctx, it, prefs)).build() },
                at, prefs.resume(items[at].key),
            )
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setPreferredAudioLanguage(prefs.prefAudio.ifBlank { null }).setPreferredTextLanguage(prefs.prefSub.ifBlank { null }).build()
            repeatMode = when (prefs.repeat) { 1 -> Player.REPEAT_MODE_ONE; 2 -> Player.REPEAT_MODE_ALL; else -> Player.REPEAT_MODE_OFF }
            skipSilenceEnabled = prefs.skipSilence
            pauseAtEndOfMediaItems = !prefs.autoPlay
            setSeekParameters(if (prefs.fastSeek) SeekParameters.CLOSEST_SYNC else SeekParameters.EXACT)
            setPlaybackSpeed(if (prefs.rememberSpeed) prefs.speed else prefs.defSpeed)
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
    var speed by remember { mutableFloatStateOf(if (prefs.rememberSpeed) prefs.speed else prefs.defSpeed) }
    var rep by remember { mutableIntStateOf(prefs.repeat) }
    var shuffle by remember { mutableStateOf(false) }
    var bg by remember { mutableStateOf(prefs.bg) }
    var aspect by remember { mutableIntStateOf(prefs.aspect) }
    var orient by remember { mutableIntStateOf(prefs.orient) }
    var skinId by remember { mutableStateOf(prefs.skin) }
    var settings by remember { mutableStateOf(false) }
    var rev by remember { mutableIntStateOf(0) } // bumped when an option changed, so gestures and subtitles re-read the saved values
    var remaining by remember { mutableStateOf(prefs.showRemaining) }
    var subDelay by remember { mutableLongStateOf(0L) }
    var ext by remember { mutableStateOf<Pair<Uri, String>?>(null) }
    val sk = skinOf(skinId)
    var zoom by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) } // two-finger pan, only while zoomed
    var panY by remember { mutableFloatStateOf(0f) }
    var hud by remember { mutableStateOf<Pair<String, String>?>(null) } // icon key, text
    var hudStamp by remember { mutableIntStateOf(0) }
    fun say(icon: String, text: String) { hud = icon to text; hudStamp++ }
    val booster = remember { arrayOfNulls<LoudnessEnhancer>(1) }
    val equalizer = remember { arrayOfNulls<Equalizer>(1) }
    var abA by remember { mutableLongStateOf(-1L) } // A-B repeat: start, end (-1 = not set)
    var abB by remember { mutableLongStateOf(-1L) }
    var sleepAt by remember { mutableLongStateOf(0L) } // SystemClock.elapsedRealtime() deadline, 0 = off
    var sleepEnd by remember { mutableStateOf(false) } // stop after the current video
    var preview by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }

    fun saveResume() {
        player.currentMediaItem?.let { prefs.setResume(it.mediaId, player.currentPosition, player.duration.coerceAtLeast(0L)) }
    }
    fun applyBoost(session: Int) {
        runCatching { booster[0]?.release() }
        booster[0] = if (prefs.boost > 0 && session != 0) runCatching { LoudnessEnhancer(session).also { it.setTargetGain(prefs.boost); it.setEnabled(true) } }.getOrNull() else null
    }
    fun applyEq(session: Int) {
        runCatching { equalizer[0]?.release() }
        equalizer[0] = if (prefs.eqOn && session != 0) runCatching {
            Equalizer(0, session).also { e ->
                val lv = prefs.eqLevels.split(',').mapNotNull { it.toIntOrNull() }
                for (b in 0 until e.numberOfBands) lv.getOrNull(b)?.let { e.setBandLevel(b.toShort(), it.toShort()) }
                e.setEnabled(true)
            }
        }.getOrNull() else null
    }
    fun applyPrefs() {
        skinId = prefs.skin; bg = prefs.bg; aspect = prefs.aspect; orient = prefs.orient; rep = prefs.repeat
        player.repeatMode = when (rep) { 1 -> Player.REPEAT_MODE_ONE; 2 -> Player.REPEAT_MODE_ALL; else -> Player.REPEAT_MODE_OFF }
        player.skipSilenceEnabled = prefs.skipSilence
        player.pauseAtEndOfMediaItems = !prefs.autoPlay
        player.setHandleAudioBecomingNoisy(prefs.pauseNoisy)
        player.setSeekParameters(if (prefs.fastSeek) SeekParameters.CLOSEST_SYNC else SeekParameters.EXACT)
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setPreferredAudioLanguage(prefs.prefAudio.ifBlank { null }).setPreferredTextLanguage(prefs.prefSub.ifBlank { null }).build()
        applyBoost(player.audioSessionId); applyEq(player.audioSessionId)
        rev++
    }
    /** Loads the picked subtitle file again with the saved encoding and the delay. */
    fun applySub() {
        val e = ext ?: return
        val i = player.currentMediaItemIndex
        val at = player.currentPosition
        val item = player.currentMediaItem ?: return
        val u = prepareSub(ctx, e.first, e.second, prefs.subEnc, subDelay) ?: e.first
        val cfg = MediaItem.SubtitleConfiguration.Builder(u).setMimeType(subMimeOf(e.second)).setLabel(e.second).setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()
        player.replaceMediaItem(i, item.buildUpon().setSubtitleConfigurations(siblingSubs(ctx, items[i.coerceIn(items.indices)], prefs) + cfg).build())
        player.seekTo(i, at)
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build()
    }

    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onTracksChanged(t: Tracks) { tracks = t }
            override fun onVideoSizeChanged(v: VideoSize) { vsize = v }
            override fun onAudioSessionIdChanged(audioSessionId: Int) { applyBoost(audioSessionId); applyEq(audioSessionId) }
            override fun onMediaItemTransition(m: MediaItem?, reason: Int) {
                cur = player.currentMediaItemIndex
                items.getOrNull(cur)?.let { prefs.addRecent(it) }
                ext = null; subDelay = 0L; abA = -1L; abB = -1L
                if (sleepEnd && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) { player.pause(); sleepEnd = false }
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK) {
                    m?.let { val r = prefs.resume(it.mediaId); if (r > 0) player.seekTo(r) }
                }
            }
        }
        player.addListener(l)
        items.getOrNull(cur)?.let { prefs.addRecent(it) }
        applyBoost(player.audioSessionId); applyEq(player.audioSessionId)
        onDispose { saveResume(); player.removeListener(l); runCatching { booster[0]?.release() }; runCatching { equalizer[0]?.release() }; player.release() }
    }

    // the screen stays on, the system bars hide, the options of the window return to normal on leave
    DisposableEffect(act) {
        val win = act?.window
        val ctl = win?.let { WindowCompat.getInsetsController(it, view) }
        val oldBright = win?.attributes?.screenBrightness ?: -1f
        if (prefs.keepAwake) win?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (prefs.rememberBright && prefs.bright > 0f) win?.attributes = win.attributes.also { it.screenBrightness = prefs.bright }
        ctl?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        ctl?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            win?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            ctl?.show(WindowInsetsCompat.Type.systemBars())
            win?.attributes = win.attributes.also { it.screenBrightness = oldBright }
            act?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            if (android.os.Build.VERSION.SDK_INT >= 31) runCatching { act?.setPictureInPictureParams(PictureInPictureParams.Builder().setAutoEnterEnabled(false).build()) }
            MediaService.stop(ctx, VideoPlaybackService::class.java)
        }
    }

    // pause when the app goes away, unless PiP or the background audio option keeps it going
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, player) {
        val observer = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_STOP) {
                if ((android.os.Build.VERSION.SDK_INT >= 24 && act?.isInPictureInPictureMode == true)) Unit
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
            if (abA >= 0 && abB > abA && player.currentPosition >= abB) player.seekTo(abA)
            if (sleepAt > 0 && SystemClock.elapsedRealtime() >= sleepAt) { player.pause(); sleepAt = 0L }
            dur = player.duration.coerceAtLeast(0L)
            inPip = (android.os.Build.VERSION.SDK_INT >= 24 && act?.isInPictureInPictureMode == true)
            if (++n % 8 == 0 && player.isPlaying) saveResume()
            delay(250)
        }
    }
    val retriever = remember(cur) { arrayOfNulls<MediaMetadataRetriever>(1) }
    DisposableEffect(cur) { onDispose { runCatching { retriever[0]?.release() } } }
    LaunchedEffect(seeking, pos / 1000) {
        if (!seeking || !prefs.seekPreview) { preview = null; return@LaunchedEffect }
        delay(150)
        preview = withContext(Dispatchers.IO) {
            runCatching {
                val u = items[cur.coerceIn(items.indices)].uri
                val r = retriever[0] ?: MediaMetadataRetriever().also {
                    if (u.scheme == "http" || u.scheme == "https") it.setDataSource(u.toString(), HashMap()) else it.setDataSource(ctx, u)
                    retriever[0] = it
                }
                r.getScaledFrameAtTime(pos * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 320, 180)?.asImageBitmap()
            }.getOrNull()
        } ?: preview
    }
    LaunchedEffect(bg) { if (bg) MediaService.start(ctx, VideoPlaybackService::class.java) else MediaService.stop(ctx, VideoPlaybackService::class.java) }
    LaunchedEffect(shown, playing, poke, dlg, rev) { if (shown && playing && dlg == null && prefs.ctrlTimeout > 0) { delay(prefs.ctrlTimeout.toLong()); shown = false } }
    LaunchedEffect(hudStamp) { delay(900); hud = null }
    LaunchedEffect(orient, vsize) {
        act?.requestedOrientation = when (orient) {
            1 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            2 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            3 -> if (vsize.width == 0) ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED else if (vsize.width >= vsize.height) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            4 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
            5 -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    // PiP: the window shape follows the video, and leaving the app while playing enters it by itself
    fun pipParams(): PictureInPictureParams? {
        if (android.os.Build.VERSION.SDK_INT < 26) return null
        val b = PictureInPictureParams.Builder()
        if (android.os.Build.VERSION.SDK_INT >= 31) b.setAutoEnterEnabled(playing && !bg && prefs.autoPip)
        if (vsize.width > 0 && vsize.height > 0) {
            val r = (vsize.width.toFloat() / vsize.height).coerceIn(0.42f, 2.39f)
            b.setAspectRatio(Rational((r * 1000).roundToInt(), 1000))
        }
        return b.build()
    }
    LaunchedEffect(playing, vsize, bg, rev) { pipParams()?.let { pp -> runCatching { act?.setPictureInPictureParams(pp) } } }

    val audio = remember { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVol = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var volF by remember { mutableFloatStateOf(restoreVolume(audio, prefs)) }
    var bright by remember { mutableFloatStateOf((act?.window?.attributes?.screenBrightness ?: -1f).let { if (it < 0) .5f else it }) }

    val pickSub = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) { ext = u to displayName(ctx, u); applySub(); dlg = null }
    }

    BackHandler(enabled = !settings) { if (!locked) onBack() }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val step = prefs.dtapSec * 1000L

    Box(
        Modifier.fillMaxSize().background(Color.Black).focusRequester(focus).focusable().onPreviewKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown) false
            else when (e.key) {
                Key.Spacebar, Key.MediaPlayPause, Key.DirectionCenter, Key.Enter -> { if (player.isPlaying) player.pause() else player.play(); shown = true; poke++; true }
                Key.DirectionRight -> { player.seekTo(player.currentPosition + step); say("play", "+${prefs.dtapSec}s"); true }
                Key.DirectionLeft -> { player.seekTo((player.currentPosition - step).coerceAtLeast(0)); say("back", "-${prefs.dtapSec}s"); true }
                Key.MediaNext -> { player.seekToNextMediaItem(); true }
                Key.MediaPrevious -> { player.seekToPreviousMediaItem(); true }
                else -> false
            }
        },
    ) {
        val shape = when (aspect) { 3 -> Modifier.align(Alignment.Center).aspectRatio(16f / 9f); 4 -> Modifier.align(Alignment.Center).aspectRatio(4f / 3f); else -> Modifier.fillMaxSize() }
        Box(shape.graphicsLayer { scaleX = zoom; scaleY = zoom; translationX = panX; translationY = panY }) {
            AndroidView(
                factory = { PlayerView(it).apply { useController = false; setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER) } },
                update = {
                    it.player = player
                    it.resizeMode = when (aspect) { 1, 3, 4 -> AspectRatioFrameLayout.RESIZE_MODE_FILL; 2 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM; else -> AspectRatioFrameLayout.RESIZE_MODE_FIT }
                    rev.let { _ ->
                        it.subtitleView?.apply {
                            applySubPrefs(prefs)
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // touch layer: taps outside, drags, pinch and hold inside (the inner pointerInput consumes first)
        Box(
            Modifier.fillMaxSize()
                .pointerInput(locked, rev) {
                    val onDt: (Offset) -> Unit = { o ->
                        if (!locked) {
                            val f = o.x / size.width
                            val seekOk = prefs.dtap == 1 || prefs.dtap == 3
                            when {
                                f < .33f && seekOk -> { player.seekTo((player.currentPosition - step).coerceAtLeast(0)); say("back", "-${prefs.dtapSec}s") }
                                f > .66f && seekOk -> { player.seekTo(player.currentPosition + step); say("play", "+${prefs.dtapSec}s") }
                                prefs.dtap >= 2 -> if (player.isPlaying) player.pause() else player.play()
                            }
                        }
                    }
                    detectTapGestures(onTap = { shown = !shown }, onDoubleTap = if (prefs.dtap == 0) null else onDt)
                }
                .pointerInput(locked, rev) {
                    if (locked) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val slop = viewConfiguration.touchSlop * 2
                        var mode = 0 // 1 seek, 2 brightness, 3 volume, 4 pinch, -1 off
                        var tx = 0f
                        var ty = 0f
                        var held = false
                        val start = player.currentPosition
                        var target = start
                        while (true) {
                            val ev = if (mode == 0 && !held && prefs.gestLong) withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { awaitPointerEvent() } else awaitPointerEvent()
                            if (ev == null) {
                                held = true; player.setPlaybackSpeed(prefs.lpSpeed); say("speed", speedName(prefs.lpSpeed))
                                continue
                            }
                            if (ev.changes.count { it.pressed } >= 2) {
                                if (prefs.gestZoom && !held) {
                                    mode = 4
                                    val z = ev.calculateZoom()
                                    zoom = (zoom * z).coerceIn(1f, 4f)
                                    if (zoom == 1f) { panX = 0f; panY = 0f } else { val p = ev.calculatePan(); panX += p.x; panY += p.y }
                                    if (z != 1f) say("scale", "${(zoom * 100).roundToInt()}%")
                                    ev.changes.forEach { it.consume() }
                                }
                            } else if ((mode in 0..3) && !held) {
                                val c = ev.changes.first()
                                val d = c.position - c.previousPosition
                                tx += d.x; ty += d.y
                                if (mode == 0 && hypot(tx, ty) > slop) {
                                    mode = if (abs(tx) > abs(ty)) (if (prefs.gestSeek) 1 else -1)
                                    else if (down.position.x < w / 2) (if (prefs.gestBright) 2 else -1)
                                    else (if (prefs.gestVol) 3 else -1)
                                }
                                when (mode) {
                                    1 -> {
                                        target = (start + tx / w * prefs.seekSens * 1000).toLong().coerceIn(0, dur.coerceAtLeast(1))
                                        say("play", clock(target) + " (" + (if (target >= start) "+" else "-") + abs(target - start) / 1000 + "s)")
                                    }
                                    2 -> {
                                        bright = (bright - d.y / h * 1.2f * prefs.gestSens / 100f).coerceIn(.01f, 1f)
                                        act?.window?.let { win -> win.attributes = win.attributes.also { it.screenBrightness = bright } }
                                        say("light", "${(bright * 100).roundToInt()}%")
                                    }
                                    3 -> {
                                        volF = (volF - d.y / h * maxVol * 1.2f * prefs.gestSens / 100f).coerceIn(0f, maxVol.toFloat())
                                        audio.setStreamVolume(AudioManager.STREAM_MUSIC, volF.roundToInt(), 0)
                                        say("volume", "${(volF / maxVol * 100).roundToInt()}%")
                                    }
                                }
                                if (mode > 0) c.consume()
                            }
                            if (ev.changes.none { it.pressed }) break
                        }
                        if (held) player.setPlaybackSpeed(speed)
                        if (mode == 1) player.seekTo(target)
                        if (mode == 2 && prefs.rememberBright) prefs.bright = bright
                    }
                },
        )

        VideoExtras(player, prefs, act, rev)
        if (!inPip) VideoStatus(prefs, rev, Modifier.align(Alignment.TopEnd).padding(top = 56.dp, end = 12.dp))
        hud?.let { (icon, text) ->
            Row(Modifier.align(Alignment.Center).background(Color(0x99000000), RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                PIcon(icon, Color.White, 22.dp)
                Spacer(Modifier.width(8.dp))
                Text(text, color = Color.White, fontSize = 16.sp)
            }
        }

        if (seeking) preview?.let { Image(it, null, Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp).size(160.dp, 90.dp).background(Color.Black, RoundedCornerShape(6.dp))) }

        if (locked && shown && !inPip) {
            Box(Modifier.align(Alignment.CenterStart).padding(16.dp).size(48.dp).background(Color(0x99000000), RoundedCornerShape(24.dp)).clickable { locked = false; poke++ }, contentAlignment = Alignment.Center) {
                LockGlyph(Look.accent.copy(alpha = 1f))
            }
        }

        if (!locked && shown && !inPip) {
            val fg = sk.fg; val tf = sk.topFg
            Row(
                // empty bar areas take the tap, so it does not reach the video and hide the bar
                Modifier.align(Alignment.TopCenter).fillMaxWidth().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xAA000000), Color.Transparent))).background(sk.top).pointerInput(Unit) { detectTapGestures {} }.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SkinBtn(sk, onBack) { PIcon("back", tf, 24.dp) }
                Text(items.getOrNull(cur)?.name.orEmpty(), Modifier.weight(1f), color = tf, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                SkinBtn(sk, { dlg = "audio" }) { PIcon("volume", tf, 24.dp) }
                SkinBtn(sk, { dlg = "text" }) { Tag("CC", tracks.isTypeSelected(C.TRACK_TYPE_TEXT), tf, sk.accent) }
                SkinBtn(sk, { settings = true }) { PIcon("settings", tf, 24.dp) }
                SkinBtn(sk, { dlg = "more" }) { com.umutk.pulse.extras.ui.TIcon("more", tf, 22.dp, sk.icons) }
            }
                Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    val many = items.size > 1
                    VBtn({ if (many) player.seekToPreviousMediaItem() else player.seekTo((player.currentPosition - step).coerceAtLeast(0)); poke++ }) { if (many) com.umutk.pulse.extras.ui.TIcon("prev", Color.White, 26.dp, sk.icons) else Tag("-${prefs.dtapSec}", false, Color.White) }
                    Box(Modifier.size(68.dp).background(Color(0x66000000), RoundedCornerShape(34.dp)).clickable { if (player.isPlaying) player.pause() else player.play(); poke++ }, contentAlignment = Alignment.Center) {
                        com.umutk.pulse.extras.ui.TIcon(if (playing) "pause" else "play", Color.White, 40.dp, sk.icons)
                    }
                    VBtn({ if (many) player.seekToNextMediaItem() else player.seekTo(player.currentPosition + step); poke++ }) { if (many) com.umutk.pulse.extras.ui.TIcon("next", Color.White, 26.dp, sk.icons) else Tag("+${prefs.dtapSec}", false, Color.White) }
                }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color(0xB3000000)))).background(sk.bottom).pointerInput(Unit) { detectTapGestures {} }.then(if (sk.bevel) Modifier.bevel(true) else Modifier).padding(horizontal = 12.dp, vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(clock(pos), color = fg, fontSize = 12.sp)
                    SkinSeek(pos.toFloat(), dur.toFloat(), sk, { seeking = true; pos = it.toLong(); poke++ }, { seeking = false; player.seekTo(pos) }, Modifier.weight(1f).padding(horizontal = 8.dp))
                    Text(if (remaining) "-" + clock(dur - pos) else clock(dur), Modifier.clickable { remaining = !remaining; prefs.showRemaining = remaining }, color = fg, fontSize = 12.sp)
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    SkinBtn(sk, { locked = true; shown = true; poke++ }) { LockGlyph(fg) }
                    SkinBtn(sk, { dlg = "speed" }) { Row(verticalAlignment = Alignment.CenterVertically) { PIcon("speed", if (speed != 1f) sk.accent else fg, 22.dp); Tag(speedName(speed), speed != 1f, fg, sk.accent) } }
                    SkinBtn(sk, {
                        aspect = (aspect + 1) % 5; prefs.aspect = aspect; zoom = 1f; panX = 0f; panY = 0f; poke++
                        say("scale", aspectNames()[aspect])
                    }) { PIcon("scale", fg, 24.dp) }
                    SkinBtn(sk, {
                        orient = (orient + 1) % 6; prefs.orient = orient; poke++
                        say("rotate", orientNames()[orient])
                    }) { PIcon("rotate", fg, 24.dp) }
                    SkinBtn(sk, {
                        rep = (rep + 1) % 3; prefs.repeat = rep; poke++
                        player.repeatMode = when (rep) { 1 -> Player.REPEAT_MODE_ONE; 2 -> Player.REPEAT_MODE_ALL; else -> Player.REPEAT_MODE_OFF }
                        say("rotate", repeatNames()[rep])
                    }) { com.umutk.pulse.extras.ui.TIcon("repeat", if (rep != 0) sk.accent else fg, 22.dp, sk.icons) }
                    SkinBtn(sk, {
                        when {
                            abA < 0 -> { abA = player.currentPosition; say("rotate", "A") }
                            abB < 0 -> { abB = player.currentPosition.coerceAtLeast(abA + 500); say("rotate", "A-B") }
                            else -> { abA = -1L; abB = -1L; say("rotate", tr("Off", "Kapalı")) }
                        }
                        poke++
                    }) { androidx.compose.material3.Icon(Icons.Outlined.Flip, null, Modifier.size(22.dp), tint = if (abA >= 0) sk.accent else fg) }
                    SkinBtn(sk, { dlg = "sleep" }) { PIcon("timer", if (sleepAt > 0 || sleepEnd) sk.accent else fg, 24.dp) }
                    SkinBtn(sk, { pipParams()?.let { pp -> runCatching { act?.enterPictureInPictureMode(pp) } } }) { androidx.compose.material3.Icon(Icons.Outlined.PictureInPictureAlt, null, Modifier.size(22.dp), tint = fg) }
                    SkinBtn(sk, { dlg = "cast" }) { androidx.compose.material3.Icon(Icons.Outlined.Cast, null, Modifier.size(22.dp), tint = fg) }
                }
            }
        }
        if (settings) VideoSettings(prefs, { settings = false }) { applyPrefs() }
    }

    when (dlg) {
        "speed" -> PickDialog("x", speeds.map { speedName(it) to (it == speed) }, { dlg = null }) { i ->
            speed = speeds[i]; player.setPlaybackSpeed(speed); if (prefs.rememberSpeed) prefs.speed = speed; dlg = null
        }
        "audio" -> {
            val rows = trackRows(tracks, C.TRACK_TYPE_AUDIO)
            var skip by remember { mutableStateOf(prefs.skipSilence) }
            val w = LocalWindowLook.current
            PickDialog(tr("Audio", "Ses"), rows.map { it.label to it.selected }, { dlg = null },
                extra = {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        PIcon("volume", w.text, 22.dp); Spacer(Modifier.width(10.dp)); Text(tr("Skip silence", "Sessizliği atla"), Modifier.weight(1f), color = w.text, fontSize = 14.sp)
                        PSwitch(skip, { skip = it; prefs.skipSilence = it; player.skipSilenceEnabled = it })
                    }
                    Row(Modifier.fillMaxWidth().clickable { dlg = "boost" }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        PIcon("volume", w.text, 22.dp); Spacer(Modifier.width(10.dp)); Text(tr("Volume boost", "Ses artırma"), Modifier.weight(1f), color = w.text, fontSize = 14.sp)
                        Text(if (prefs.boost == 0) tr("Off", "Kapalı") else "+${prefs.boost / 100} dB", color = w.sub, fontSize = 13.sp)
                    }
                },
            ) { i ->
                val r = rows[i]
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                    .addOverride(TrackSelectionOverride(r.group.mediaTrackGroup, r.idx)).build()
                if (prefs.rememberSel) r.lang?.let { prefs.prefAudio = it }
                dlg = null
            }
        }
        "sleep" -> {
            val mins = listOf(0, 10, 15, 30, 45, 60, 90, 120, -1)
            val names = mins.map { when (it) { 0 -> tr("Off", "Kapalı"); -1 -> tr("End of video", "Video sonu"); else -> "$it min" } }
            PickDialog(tr("Sleep timer", "Uyku zamanlayıcı"), names.mapIndexed { i, n -> n to ((mins[i] == 0 && sleepAt == 0L && !sleepEnd) || (mins[i] == -1 && sleepEnd)) }, { dlg = null }) { i ->
                val m = mins[i]
                sleepEnd = m == -1
                sleepAt = if (m > 0) SystemClock.elapsedRealtime() + m * 60_000L else 0L
                dlg = null
            }
        }
        "eq" -> EqDialog(prefs, equalizer[0], sk, { applyEq(player.audioSessionId) }) { dlg = null }
        "boost" -> {
            val boosts = listOf(0, 300, 600, 900, 1200, 1500)
            PickDialog(tr("Volume boost", "Ses artırma"), boosts.map { (if (it == 0) tr("Off", "Kapalı") else "+${it / 100} dB") to (it == prefs.boost) }, { dlg = null }) { i ->
                prefs.boost = boosts[i]; applyBoost(player.audioSessionId); dlg = null
            }
        }
        "text" -> {
            val rows = trackRows(tracks, C.TRACK_TYPE_TEXT)
            val off = rows.none { it.selected }
            val w = LocalWindowLook.current
            PickDialog(tr("Subtitles", "Altyazı"), listOf(tr("Off", "Kapalı") to off) + rows.map { it.label to it.selected }, { dlg = null },
                extra = {
                    Row(Modifier.fillMaxWidth().clickable { pickSub.launch(arrayOf("*/*")) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        PIcon("folder", w.text, 22.dp); Spacer(Modifier.width(10.dp)); Text(".srt .ass .vtt", color = w.text, fontSize = 14.sp)
                    }
                    Row(Modifier.fillMaxWidth().clickable { dlg = "osub" }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        PIcon("search", w.text, 22.dp); Spacer(Modifier.width(10.dp)); Text(tr("Search online", "Çevrimiçi ara"), color = w.text, fontSize = 14.sp)
                    }
                    Row(Modifier.fillMaxWidth().clickable { dlg = "subopt" }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        PIcon("settings", w.text, 22.dp); Spacer(Modifier.width(10.dp)); Text(tr("Style and delay", "Stil ve gecikme"), color = w.text, fontSize = 14.sp)
                    }
                },
            ) { i ->
                player.trackSelectionParameters = if (i == 0) player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
                else rows[i - 1].let { r ->
                    if (prefs.rememberSel) r.lang?.let { prefs.prefSub = it }
                    player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).clearOverridesOfType(C.TRACK_TYPE_TEXT)
                        .addOverride(TrackSelectionOverride(r.group.mediaTrackGroup, r.idx)).build()
                }
                dlg = null
            }
        }
        "osub" -> OnlineSubsDialog(ctx, items.getOrNull(cur)?.name.orEmpty(), { u, name -> ext = u to name; applySub(); dlg = null }) { dlg = null }
        "subopt" -> SubOptions(prefs, subDelay, ext != null, { subDelay = it; applySub() }, { rev++ }, { dlg = "enc" }) { dlg = null }
        "enc" -> PickDialog(tr("Encoding", "Kodlama"), encodings.map { it to (it == prefs.subEnc) }, { dlg = "subopt" }) { i -> prefs.subEnc = encodings[i]; applySub(); dlg = "subopt" }
        "more" -> {
            // only what the bottom bar does not already do (speed, repeat and the sleep timer have their own buttons there)
            val acts = listOf(
                "shuffle" to tr("Shuffle", "Karıştır") + ": " + (if (shuffle) tr("On", "Açık") else tr("Off", "Kapalı")),
                "bg" to tr("Background audio", "Arka plan sesi") + ": " + (if (bg) tr("On", "Açık") else tr("Off", "Kapalı")),
                "eq" to tr("Equalizer", "Ekolayzır"),
                "cast" to tr("Cast the screen", "Ekranı yansıt"),
                "info" to tr("Info", "Bilgi"),
                "share" to tr("Share", "Paylaş"),
                "zoom" to tr("Reset zoom", "Yakınlaştırmayı sıfırla"),
            )
            PickDialog(tr("More", "Daha fazla"), acts.map { it.second to false }, { dlg = null }) { i ->
                dlg = null
                when (acts[i].first) {
                    "shuffle" -> { shuffle = !shuffle; player.shuffleModeEnabled = shuffle }
                    "bg" -> { bg = !bg; prefs.bg = bg }
                    "eq" -> dlg = "eq"
                    // the phone's own cast picker (Smart View on Samsung, Cast on other phones): it mirrors the screen to a TV or a receiver; the video itself is not handed over
                    "cast" -> dlg = "cast"
                    "info" -> dlg = "info"
                    "share" -> items.getOrNull(cur)?.let { shareVideo(ctx, it) }
                    "zoom" -> { zoom = 1f; panX = 0f; panY = 0f }
                }
            }
        }
        "cast" -> LaunchedEffect(Unit) {
            // the phone's own cast picker (Smart View on Samsung, Cast on other phones): it mirrors the screen to a TV or a receiver; the video itself is not handed over
            val tryIntents = listOf(android.content.Intent("android.settings.CAST_SETTINGS"), android.content.Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS))
            val ok = tryIntents.any { it0 -> runCatching { ctx.startActivity(it0.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess }
            if (!ok) android.widget.Toast.makeText(ctx, tr("This phone has no screen cast setting", "Bu telefonda ekran yansıtma ayarı yok"), android.widget.Toast.LENGTH_LONG).show()
            dlg = null
        }
        "info" -> items.getOrNull(cur)?.let { FileInfoDialog(it.copy(durMs = if (dur > 0) dur else it.durMs, w = if (vsize.width > 0) vsize.width else it.w, h = if (vsize.height > 0) vsize.height else it.h), liveRows(tracks)) { dlg = null } }
    }
}

/** Subtitle style and delay in the look's window. */
@Composable
private fun SubOptions(p: VideoPrefs, delayMs: Long, hasExt: Boolean, onDelay: (Long) -> Unit, onStyle: () -> Unit, onEnc: () -> Unit, onDismiss: () -> Unit) {
    val w = LocalWindowLook.current
    var rev by remember { mutableIntStateOf(0) }
    rev.let { }
    @Composable fun sw(label: String, on: Boolean, set: (Boolean) -> Unit) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), color = w.text, fontSize = 14.sp); PSwitch(on, { set(it); rev++; onStyle() })
        }
    }
    @Composable fun stepper(label: String, value: String, minus: () -> Unit, plus: () -> Unit) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), color = w.text, fontSize = 14.sp)
            PButton("-", compact = true, onClick = minus); Text(value, Modifier.width(72.dp), color = w.text, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center); PButton("+", compact = true, onClick = plus)
        }
    }
    WinAlert(
        onDismissRequest = onDismiss,
        title = { Text(tr("Subtitles", "Altyazı"), color = w.text) },
        text = {
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                if (hasExt) stepper(tr("Delay", "Gecikme"), "%+d ms".format(delayMs), { onDelay(delayMs - 100); rev++ }, { onDelay(delayMs + 100); rev++ })
                stepper(tr("Size", "Boyut"), "${p.subSize}", { p.subSize = (p.subSize - 2).coerceAtLeast(10); rev++; onStyle() }, { p.subSize = (p.subSize + 2).coerceAtMost(60); rev++; onStyle() })
                sw(tr("Bold", "Kalın"), p.subBold) { p.subBold = it }
                sw(tr("Background", "Arka plan"), p.subBg) { p.subBg = it }
                sw(tr("Outline", "Kenar"), p.subEdge) { p.subEdge = it }
                Row(Modifier.fillMaxWidth().clickable(onClick = onEnc).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Encoding", "Kodlama"), Modifier.weight(1f), color = w.text, fontSize = 14.sp); Text(p.subEnc, color = w.sub, fontSize = 13.sp)
                }
            }
        },
        confirmButton = { PButton(tr("OK", "Tamam"), onClick = onDismiss) },
    )
}

/** A list of radio rows in the look's own window. */
@Composable
internal fun PickDialog(title: String, rows: List<Pair<String, Boolean>>, onDismiss: () -> Unit, extra: (@Composable () -> Unit)? = null, onPick: (Int) -> Unit) {
    val w = LocalWindowLook.current
    WinAlert(
        onDismissRequest = onDismiss,
        title = { Text(title, color = w.text, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
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

/** Equalizer bands as skin seek bars. Levels are saved in millibel and applied through [apply]; switching on creates the effect. */
@Composable
private fun EqDialog(prefs: VideoPrefs, eq: Equalizer?, sk: VSkin, apply: () -> Unit, onDismiss: () -> Unit) {
    val w = LocalWindowLook.current
    var on by remember { mutableStateOf(prefs.eqOn) }
    // bands of the live effect, or of a short-lived one when the equalizer is off
    val info = remember(on) {
        runCatching {
            val e = eq ?: Equalizer(0, 0)
            val n = e.numberOfBands.toInt(); val lo = e.bandLevelRange[0].toInt(); val hi = e.bandLevelRange[1].toInt()
            val hz = List(n) { e.getCenterFreq(it.toShort()) / 1000 }
            if (eq == null) e.release()
            Triple(hz, lo, hi)
        }.getOrNull()
    }
    val levels = remember(info) {
        val saved = prefs.eqLevels.split(',').mapNotNull { it.toIntOrNull() }
        androidx.compose.runtime.mutableStateListOf<Int>().also { l -> info?.first?.indices?.forEach { l += saved.getOrNull(it) ?: 0 } }
    }
    WinAlert(
        onDismissRequest = onDismiss,
        title = { Text(tr("Equalizer", "Ekolayzır"), color = w.text) },
        text = {
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    PIcon("volume", w.text, 22.dp); Spacer(Modifier.width(10.dp)); Text("EQ", Modifier.weight(1f), color = w.text, fontSize = 14.sp)
                    PSwitch(on, { on = it; prefs.eqOn = it; apply() })
                }
                if (info != null && on) info.first.forEachIndexed { b, hz ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (hz >= 1000) "${hz / 1000} kHz" else "$hz Hz", Modifier.width(64.dp), color = w.text, fontSize = 12.sp)
                        SkinSeek((levels[b] - info.second).toFloat(), (info.third - info.second).toFloat(), sk, {
                            levels[b] = it.roundToInt() + info.second
                            prefs.eqLevels = levels.joinToString(","); apply()
                        }, {}, Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = { PButton(tr("OK", "Tamam"), onClick = onDismiss) },
    )
}
