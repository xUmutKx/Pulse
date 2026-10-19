package com.umutk.pulse.extras.ambient

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import java.io.File
import kotlin.random.Random
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Plays several looping layers at once, each with its own on/off and volume.
 * Use from the main thread. A layer's MediaPlayer exists only while it is on.
 */
class AmbientEngine(private val context: Context, tracks: List<AmbientTrack>) {

    private val players = HashMap<String, MediaPlayer>()
    private val handler = Handler(Looper.getMainLooper())
    private val cycles = HashMap<String, Runnable>()
    private val drift = HashMap<String, Float>()
    private val _state = MutableStateFlow(tracks.map { it.copy(enabled = false) })

    val state: StateFlow<List<AmbientTrack>> = _state

    init {
        // Start the layers that were already on in the input list.
        tracks.filter { it.enabled }.forEach { setEnabled(it.id, true) }
    }

    fun setEnabled(id: String, on: Boolean) {
        val t = find(id) ?: return
        if (!on) {
            stopCycle(id)
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
        val v = lvl / 100f * (drift[id] ?: 1f)
        players[id]?.setVolume(v, v)
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
        cycles.keys.toList().forEach { stopCycle(it) }
        players.values.forEach { it.release() }
        players.clear()
    }

    /** A steady layer breathes: every 4-9 s its volume drifts to a new spot between 70 and 100 % so a wave or rain never sounds fixed. */
    private fun startDrift(id: String, mp: MediaPlayer) {
        val run = object : Runnable {
            override fun run() {
                if (players[id] !== mp) return
                drift[id] = 0.7f + Random.nextFloat() * 0.3f
                val lvl = (find(id)?.level ?: 100) / 100f * drift[id]!!
                runCatching { mp.setVolume(lvl, lvl) }
                handler.postDelayed(this, Random.nextLong(4000, 9000))
            }
        }
        cycles[id] = run
        handler.postDelayed(run, Random.nextLong(2000, 5000))
    }

    private fun stopCycle(id: String) {
        drift.remove(id)
        cycles.remove(id)?.let { handler.removeCallbacks(it) }
    }

    /** Bird layers are not a steady loop: a random 8-15 s stretch from a random spot, then 10-35 s of quiet, again and again. */
    private fun startSparse(id: String, mp: MediaPlayer) {
        val run = object : Runnable {
            override fun run() {
                if (players[id] !== mp) return
                runCatching {
                    val dur = mp.duration
                    mp.seekTo(if (dur > 16000) Random.nextInt(0, dur - 15000) else 0)
                    mp.start()
                }
                handler.postDelayed({ if (players[id] === mp) runCatching { mp.pause() } }, Random.nextLong(8000, 15000))
                handler.postDelayed(this, Random.nextLong(20000, 50000))
            }
        }
        cycles[id] = run
        handler.postDelayed(run, Random.nextLong(1500, 8000))
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
            val sparse = file.path.contains("/birds/")
            mp.isLooping = !sparse
            mp.setVolume(t.level / 100f, t.level / 100f)
            mp.setOnErrorListener { _, _, _ -> setEnabled(t.id, false); true }
            // shortcut: prepare() blocks the main thread, fine for small local loops, upgrade to prepareAsync if UI stutters
            mp.prepare()
            if (sparse) { players[t.id] = mp; startSparse(t.id, mp) } else { runCatching { val d = mp.duration; if (d > 20000) mp.seekTo(Random.nextInt(0, d - 10000)) }; mp.start(); players[t.id] = mp; startDrift(t.id, mp) }
            mp
        } catch (e: Exception) {
            mp.release()
            null
        }
    }
}
