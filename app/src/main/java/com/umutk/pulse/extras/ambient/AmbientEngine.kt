package com.umutk.pulse.extras.ambient

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.PowerManager
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Plays several looping layers at once, each with its own on/off and volume.
 * Use from the main thread. A layer's MediaPlayer exists only while it is on.
 */
class AmbientEngine(private val context: Context, tracks: List<AmbientTrack>) {

    private val players = HashMap<String, MediaPlayer>()
    private val _state = MutableStateFlow(tracks.map { it.copy(enabled = false) })

    val state: StateFlow<List<AmbientTrack>> = _state

    init {
        // Start the layers that were already on in the input list.
        tracks.filter { it.enabled }.forEach { setEnabled(it.id, true) }
    }

    fun setEnabled(id: String, on: Boolean) {
        val t = find(id) ?: return
        if (!on) {
            players.remove(id)?.release()
            put(t.copy(enabled = false))
            return
        }
        if (players[id] == null) openPlayer(t)?.let { players[id] = it }
        put(t.copy(enabled = players.containsKey(id)))
    }

    fun setLevel(id: String, level: Int) {
        val t = find(id) ?: return
        val lvl = level.coerceIn(0, 100)
        put(t.copy(level = lvl))
        players[id]?.setVolume(lvl / 100f, lvl / 100f)
    }

    fun setAllEnabled(on: Boolean) {
        _state.value.forEach { setEnabled(it.id, on) }
    }

    /** Applies a saved preset: matches layers by [AmbientTrack.id] and copies level and enabled. */
    fun apply(preset: List<AmbientTrack>) {
        preset.forEach { p ->
            setLevel(p.id, p.level)
            setEnabled(p.id, p.enabled)
        }
    }

    fun release() {
        players.values.forEach { it.release() }
        players.clear()
    }

    private fun find(id: String): AmbientTrack? = _state.value.firstOrNull { it.id == id }

    private fun put(track: AmbientTrack) {
        _state.update { list -> list.map { if (it.id == track.id) track else it } }
    }

    private fun openPlayer(t: AmbientTrack): MediaPlayer? {
        val file = File(t.path)
        if (!file.isFile) return null
        val mp = MediaPlayer()
        return try {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            // Keeps the layer playing with the screen off; the service holding this engine must run in foreground.
            mp.setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
            mp.setDataSource(file.path)
            mp.isLooping = true
            mp.setVolume(t.level / 100f, t.level / 100f)
            mp.setOnErrorListener { _, _, _ -> setEnabled(t.id, false); true }
            // shortcut: prepare() blocks the main thread, fine for small local loops, upgrade to prepareAsync if UI stutters
            mp.prepare()
            mp.start()
            mp
        } catch (e: Exception) {
            mp.release()
            null
        }
    }
}
