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
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
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

    init {
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
        .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist(artist).setAlbumTitle(album).build())
        .build()

    private fun MediaItem.toTrack(): Track = Track(mediaId, mediaMetadata.title?.toString(), artist = mediaMetadata.artist?.toString(), album = mediaMetadata.albumTitle?.toString())
}
