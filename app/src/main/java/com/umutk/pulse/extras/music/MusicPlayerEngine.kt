package com.umutk.pulse.extras.music

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Timeline
import androidx.media3.common.Tracks
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.umutk.pulse.extras.watchdog.QuickTileContract
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlaybackState(
    val current: Track? = null,
    val index: Int = -1,
    val isPlaying: Boolean = false,
    val queue: List<Track> = emptyList(),
)

/**
 * Media3 player for the queue. The ExoPlayer's own playlist is the queue. ExoPlayer handles audio focus,
 * headphone unplug and the wake lock. [MusicPlaybackService] shares this player as a MediaSession, so
 * playback continues in the background and shows the media notification.
 * Use [get] from the main thread only: ExoPlayer must be touched from its looper's thread.
 */
class MusicPlayerEngine private constructor(context: Context) {

    private val app = context.applicationContext

    val player: ExoPlayer = ExoPlayer.Builder(app)
        .setRenderersFactory(object : DefaultRenderersFactory(app) {
            override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink =
                DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessors(arrayOf<AudioProcessor>(AudioGain.processor, MusicLevels.processor))
                    .build()
        })
        .setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
            /* handleAudioFocus = */ true,
        )
        .setHandleAudioBecomingNoisy(true)
        .setWakeMode(C.WAKE_MODE_LOCAL)
        .build()

    val equalizer = Equalizer().also { it.restore(app) }

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    // Binding to the service starts it, so the notification and background playback run without the UI open.
    private val binding = MediaController.Builder(app, SessionToken(app, ComponentName(app, MusicPlaybackService::class.java)))
        .buildAsync()

    // ---- sound settings: pre-amp, ReplayGain, fade between tracks, sleep timer ----
    private var rgTrackDb: Float? = null
    private var rgAlbumDb: Float? = null
    private var xfadeSec = 0
    private var rgMode = 0
    private var rgFallback = -6
    private var preampDb = 0
    private var sleepEndAt = 0L
    private val _sleep = MutableStateFlow(0L)
    /** 0 none, -1 stops after this track, else milliseconds left. */
    val sleepLeftMs: StateFlow<Long> = _sleep.asStateFlow()

    /** Re-reads [MusicSettings]; call after the settings page changed something. */
    fun reloadSettings() {
        xfadeSec = MusicSettings.crossfade(app)
        rgMode = MusicSettings.replayGain(app)
        rgFallback = MusicSettings.rgFallback(app)
        preampDb = MusicSettings.preamp(app)
        player.setHandleAudioBecomingNoisy(MusicSettings.pauseOnUnplug(app))
        player.skipSilenceEnabled =MusicSettings.skipSilence(app)
        val sp = MusicSettings.speed(app)
        player.playbackParameters = PlaybackParameters(sp, if (MusicSettings.pitchFollows(app)) sp else 1f)
        applyGain()
    }

    private fun applyGain() {
        val rg = when (rgMode) {
            1 -> rgTrackDb ?: rgAlbumDb ?: rgFallback.toFloat()
            2 -> rgAlbumDb ?: rgTrackDb ?: rgFallback.toFloat()
            else -> 0f
        }
        AudioGain.gain = Math.pow(10.0, ((preampDb + rg) / 20.0)).toFloat().coerceAtMost(4f)
    }

    private fun readReplayGain(t: Tracks) {
        var tr: Float? = null; var al: Float? = null
        val re = Regex("(?i)replaygain_(track|album)_gain\\D*?(-?\\d+(?:[.,]\\d+)?)")
        for (g in t.groups) {
            if (g.type != C.TRACK_TYPE_AUDIO) continue
            for (i in 0 until g.length) {
                val m = g.getTrackFormat(i).metadata ?: continue
                for (k in 0 until m.length()) {
                    val hit = re.find(m.get(k).toString()) ?: continue
                    val v = hit.groupValues[2].replace(',', '.').toFloatOrNull() ?: continue
                    if (hit.groupValues[1].equals("track", true)) tr = v else al = v
                }
            }
        }
        rgTrackDb = tr; rgAlbumDb = al
        applyGain()
    }

    /** Stops playback after [minutes] (fading the last 10 s) or after the current track when [endOfTrack]. 0 cancels. */
    fun setSleep(minutes: Int, endOfTrack: Boolean = false) {
        sleepEndAt = if (minutes > 0) System.currentTimeMillis() + minutes * 60_000L else 0L
        player.pauseAtEndOfMediaItems = endOfTrack
        AudioGain.sleepFade = 1f
        _sleep.value = if (endOfTrack) -1L else if (minutes > 0) minutes * 60_000L else 0L
    }

    private val ticker = object : Runnable {
        override fun run() {
            if (player.isPlaying) {
                // fade at the start and end of every track
                val d = player.duration
                val x = minOf(xfadeSec * 1000L, if (d > 0) d / 3 else 0L)
                AudioGain.fade = if (x <= 0 || d <= 0) 1f else {
                    val p = player.currentPosition
                    minOf(1f, minOf((p + 300L).toFloat() / x, (d - p).toFloat() / x)).coerceIn(0f, 1f)
                }
                if (sleepEndAt > 0) {
                    val left = sleepEndAt - System.currentTimeMillis()
                    _sleep.value = left.coerceAtLeast(0L)
                    AudioGain.sleepFade = (left / 10_000f).coerceIn(0f, 1f)
                    if (left <= 0) { sleepEndAt = 0; _sleep.value = 0; pause(); AudioGain.sleepFade = 1f }
                }
            } else AudioGain.fade = 1f
            main2.postDelayed(this, 200L)
        }
    }
    private val main2 = android.os.Handler(android.os.Looper.getMainLooper())

    init {
        reloadSettings()
        main2.post(ticker)
        player.addListener(object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) = readReplayGain(tracks)
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) { player.pauseAtEndOfMediaItems = false; _sleep.value = 0L }
            }
        })
        if (MusicSettings.countStats(app)) player.addListener(com.umutk.pulse.extras.music.stats.StatsListener(app, player))
        if (player.audioSessionId != C.AUDIO_SESSION_ID_UNSET) equalizer.attach(player.audioSessionId)
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { publish(); if (!isPlaying) saveResume(); watch(isPlaying) }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                mediaItem?.toTrack()?.let { t ->
                    // the Quick panel "last played" tile and ACTION_RESUME read this
                    QuickTileContract.save(app, QuickTileContract.LastPlayed(QuickTileContract.Kind.TRACK, t.title ?: t.path.substringAfterLast('/'), t.path))
                }
                publish()
            }
            override fun onPlaybackStateChanged(playbackState: Int) = publish()
            override fun onTimelineChanged(timeline: Timeline, reason: Int) = publish()
            override fun onAudioSessionIdChanged(audioSessionId: Int) = equalizer.attach(audioSessionId)

            // One unreadable file must not stop the queue: skip to the next entry.
            override fun onPositionDiscontinuity(o: Player.PositionInfo, n: Player.PositionInfo, reason: Int) = saveResume()
            override fun onPlayerError(error: PlaybackException) {
                if (player.hasNextMediaItem()) {
                    player.seekToNextMediaItem()
                    player.prepare()
                }
                publish()
            }
        })
    }

    /** Replaces the queue and starts playing [startIndex]. */
    fun playQueue(list: List<Track>, startIndex: Int = 0) {
        player.setMediaItems(list.map { it.toMediaItem() }, startIndex.coerceIn(0, maxOf(list.lastIndex, 0)), C.TIME_UNSET)
        player.prepare()
        player.play()
    }

    /** Loads a queue without playing (resume after a restart). */
    fun loadQueue(list: List<Track>, index: Int, positionMs: Long) {
        if (list.isEmpty() || player.mediaItemCount > 0) return
        player.setMediaItems(list.map { it.toMediaItem() }, index.coerceIn(0, list.lastIndex), positionMs)
        player.prepare()
    }

    fun play() {
        if (player.mediaItemCount == 0) return
        if (player.playbackState == Player.STATE_IDLE) player.prepare()
        if (player.playbackState == Player.STATE_ENDED) player.seekToDefaultPosition()
        player.play()
    }

    fun pause() = player.pause()

    fun togglePlayPause() = if (player.playWhenReady) pause() else play()

    fun next() {
        if (player.hasNextMediaItem()) player.seekToNextMediaItem()
    }

    /** Restarts the track when more than 3 seconds in, otherwise goes to the previous one. */
    fun previous() = player.seekToPrevious()

    fun seekTo(ms: Int) = player.seekTo(ms.toLong())

    fun playAt(index: Int) {
        if (index !in 0 until player.mediaItemCount) return
        player.seekToDefaultPosition(index)
        play()
    }

    fun addToQueue(track: Track) = player.addMediaItem(track.toMediaItem())

    fun moveInQueue(from: Int, to: Int) = player.moveMediaItem(from, to)

    fun removeFromQueue(index: Int) = player.removeMediaItem(index)

    fun queue(): List<Track> = List(player.mediaItemCount) { player.getMediaItemAt(it).toTrack() }

    val positionMs: Int get() = player.currentPosition.toInt()
    val durationMs: Int get() = player.duration.coerceAtLeast(0L).toInt()

    /** Saves queue, index and position; MusicHub restores them on the next start. */
    fun saveResume() = Playlists.saveResume(app, queue(), player.currentMediaItemIndex, player.currentPosition)

    fun release() {
        equalizer.save(app)
        MediaController.releaseFuture(binding)
        player.release()
        equalizer.release()
    }

    // Watchdog: while music plays this process beats for the keep-alive service; if Android kills the process the beat
    // stops, the 15-minute check restarts it and its onResume loads the last track again (see extras/watchdog).
    private val anchor = com.umutk.pulse.extras.watchdog.MusicPlaybackService::class.java
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private val beat = object : Runnable {
        override fun run() {
            com.umutk.pulse.extras.watchdog.ServiceHealth.beat(app, anchor)
            main.postDelayed(this, 60_000L)
        }
    }

    private fun watch(playing: Boolean) {
        main.removeCallbacks(beat)
        com.umutk.pulse.extras.watchdog.ServiceWatchdog.setWanted(app, anchor, playing)
        if (playing) beat.run()
    }

    private fun publish() {
        val empty = player.mediaItemCount == 0
        _state.value = PlaybackState(
            current = player.currentMediaItem?.toTrack(),
            index = if (empty) -1 else player.currentMediaItemIndex,
            isPlaying = player.isPlaying,
            queue = queue(),
        )
    }

    companion object {
        @Volatile private var instance: MusicPlayerEngine? = null

        /** The one engine of the process, created on first use. */
        fun get(context: Context): MusicPlayerEngine =
            instance ?: synchronized(this) { instance ?: MusicPlayerEngine(context).also { instance = it } }

        private fun uriOf(path: String): Uri =
            if (path.contains("://")) Uri.parse(path) else Uri.fromFile(File(path))
    }

    private fun Track.toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(path)
        .setUri(uriOf(path))
        .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist(artist).setAlbumTitle(album)
            .also { b -> if (albumId > 0 && MusicSettings.notificationArt(app)) b.setArtworkUri(Uri.parse("content://media/external/audio/albumart/$albumId")) }.build())
        .build()

    private fun MediaItem.toTrack(): Track = Track(mediaId, mediaMetadata.title?.toString(), artist = mediaMetadata.artist?.toString(), album = mediaMetadata.albumTitle?.toString())
}
