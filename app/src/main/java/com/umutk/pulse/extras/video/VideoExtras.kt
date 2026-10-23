package com.umutk.pulse.extras.video

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.os.BatteryManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlinx.coroutines.delay
import java.io.File
import java.util.Calendar

/** Async codec queueing as chosen in Decoder. */
internal fun DefaultRenderersFactory.applyDecoder(p: VideoPrefs): DefaultRenderersFactory {
    when (p.asyncCodec) { 1 -> forceEnableMediaCodecAsynchronousQueueing(); 2 -> forceDisableMediaCodecAsynchronousQueueing() }
    return this
}

private var netCache: SimpleCache? = null
private var netCacheMb = 0

private fun cacheFor(ctx: Context, mb: Int): SimpleCache = synchronized(VideoPrefs::class.java) {
    if (netCache == null || netCacheMb != mb) {
        runCatching { netCache?.release() }
        netCache = SimpleCache(File(ctx.cacheDir, "video_net"), LeastRecentlyUsedCacheEvictor(mb * 1024L * 1024L), StandaloneDatabaseProvider(ctx))
        netCacheMb = mb
    }
    netCache!!
}

/** Drops the downloaded stream cache. */
internal fun clearNetCache(ctx: Context) {
    synchronized(VideoPrefs::class.java) { runCatching { netCache?.release() }; netCache = null; netCacheMb = 0 }
    runCatching { File(ctx.cacheDir, "video_net").deleteRecursively() }
}

/** Network options: user agent, timeouts, buffer lengths and the stream cache (cache only when a queued item is a web address). */
internal fun ExoPlayer.Builder.net(ctx: Context, p: VideoPrefs, remote: Boolean): ExoPlayer.Builder {
    val http = DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(p.connTimeout.coerceIn(1, 120) * 1000).setReadTimeoutMs(p.readTimeout.coerceIn(1, 120) * 1000)
    if (p.userAgent.isNotBlank()) http.setUserAgent(p.userAgent.trim())
    var up: DataSource.Factory = DefaultDataSource.Factory(ctx, http)
    if (remote && p.cacheMb > 0) up = CacheDataSource.Factory().setCache(cacheFor(ctx, p.cacheMb)).setUpstreamDataSourceFactory(up).setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    val play = (p.bufPlay * 100).coerceIn(500, 20000)
    val min = (p.bufMin * 1000).coerceAtLeast(play)
    val max = (p.bufMax * 1000).coerceAtLeast(min)
    setMediaSourceFactory(DefaultMediaSourceFactory(up))
    setLoadControl(DefaultLoadControl.Builder().setBufferDurationsMs(min, max, play, play).build())
    return this
}

/** Volume saved by the last session (when asked), applied to the system music stream; returns the stream volume now. */
internal fun restoreVolume(audio: AudioManager, p: VideoPrefs): Float {
    if (p.rememberVolume && p.volume >= 0) runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, p.volume, 0) }
    return audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
}

/** Option-driven behaviour of the player that lives outside the main screen: audio, decoder, display, calls, volume memory. Re-applies when [rev] changes. */
@Composable
internal fun VideoExtras(player: ExoPlayer, prefs: VideoPrefs, act: Activity?, rev: Int) {
    val ctx = LocalContext.current
    val audio = remember { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    // text tracks stay off until picked when auto-select is off
    LaunchedEffect(player) {
        if (!prefs.subAutoSel) player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
    }
    LaunchedEffect(rev) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setMaxAudioChannelCount(if (prefs.downmix) 2 else Int.MAX_VALUE)
            .build()
        (player.trackSelector as? androidx.media3.exoplayer.trackselection.DefaultTrackSelector)?.let { ts ->
            ts.setParameters(ts.buildUponParameters().setTunnelingEnabled(prefs.tunnel))
        }
        player.volume = prefs.volCap.coerceIn(0, 100) / 100f
        player.videoChangeFrameRateStrategy = if (prefs.matchRate) C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS else C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF
        act?.window?.let { w ->
            w.colorMode = if (prefs.hdrOut) ActivityInfo.COLOR_MODE_HDR else ActivityInfo.COLOR_MODE_DEFAULT
            if (prefs.brightFixed) w.attributes = w.attributes.also { it.screenBrightness = prefs.brightLevel.coerceIn(1, 100) / 100f }
        }
    }
    // pitch follows the speed when correction is off
    DisposableEffect(player, rev) {
        val l = object : Player.Listener {
            override fun onPlaybackParametersChanged(pp: PlaybackParameters) {
                val want = if (prefs.pitchFix) 1f else pp.speed
                if (pp.pitch != want) player.playbackParameters = PlaybackParameters(pp.speed, want)
            }
        }
        player.addListener(l); l.onPlaybackParametersChanged(player.playbackParameters)
        onDispose { player.removeListener(l) }
    }
    // a call or ring pauses the video
    DisposableEffect(player, rev) {
        if (android.os.Build.VERSION.SDK_INT < 31) return@DisposableEffect onDispose { }
        val l = AudioManager.OnModeChangedListener { m ->
            if (prefs.pauseCall && (m == AudioManager.MODE_IN_CALL || m == AudioManager.MODE_RINGTONE || m == AudioManager.MODE_IN_COMMUNICATION)) player.pause()
        }
        audio.addOnModeChangedListener(ctx.mainExecutor, l)
        onDispose { audio.removeOnModeChangedListener(l) }
    }
    DisposableEffect(Unit) {
        onDispose { if (prefs.rememberVolume) prefs.volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC) }
    }
}

/** Clock and battery level in the corner. Draws nothing when both are off. */
@Composable
internal fun VideoStatus(prefs: VideoPrefs, rev: Int, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var text by remember { mutableStateOf("") }
    LaunchedEffect(rev) {
        while (true) {
            val parts = ArrayList<String>()
            if (prefs.showClock) Calendar.getInstance().let { parts += "%02d:%02d".format(it.get(Calendar.HOUR_OF_DAY), it.get(Calendar.MINUTE)) }
            if (prefs.showBattery) {
                val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                val lv = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val sc = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
                if (lv >= 0) parts += "${lv * 100 / sc.coerceAtLeast(1)}%"
            }
            text = parts.joinToString("  ")
            delay(15000)
        }
    }
    if (text.isNotEmpty()) Text(text, modifier.background(Color(0x66000000), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 3.dp), color = Color.White, fontSize = 12.sp)
}
