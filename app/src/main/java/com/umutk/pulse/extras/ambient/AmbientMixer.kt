package com.umutk.pulse.extras.ambient

import android.content.Context
import com.umutk.pulse.extras.watchdog.AmbientPlaybackService
import com.umutk.pulse.extras.watchdog.MediaService
import com.umutk.pulse.extras.whitenoise.WhiteNoiseSounds
import com.umutk.pulse.extras.whitenoise.installWhiteNoiseSounds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/** One playable sound: built-in loop or a file from an installed pack. [cat] is nature, water, fire, city, cozy, noise, instruments or birds. */
class Snd(val id: String, val en: String, val tr: String, val cat: String, val path: String)

/**
 * Process-wide holder of the one [AmbientEngine], shared by the White noise page and [AmbientPlaybackService]:
 * the page can close and the sound keeps playing. The service runs while at least one layer is on.
 * Everything except [scan] runs on the main thread.
 */
object AmbientMixer {
    val sounds = MutableStateFlow<List<Snd>>(emptyList())
    /** User level 0..100 per sound id (kept for sounds that are off). */
    val levels = MutableStateFlow<Map<String, Int>>(emptyMap())
    val master = MutableStateFlow(100)
    /** Ids of the layers that are on. */
    val running: StateFlow<Set<String>> get() = _running
    private val _running = MutableStateFlow<Set<String>>(emptySet())

    private var engine: AmbientEngine? = null
    private var job: Job? = null
    private var started = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val BUILTIN_CAT = mapOf(
        "white" to "noise", "coffee" to "cozy", "bell" to "instruments", "rain" to "water",
        "fire" to "fire", "wind" to "nature", "waves" to "water",
    )

    fun isPlaying() = _running.value.isNotEmpty()

    /** Built-in loops plus every pack found under extras/ambient and extras/birds. Call on IO. */
    fun scan(ctx: Context): List<Snd> {
        val out = ArrayList<Snd>()
        val bundled = com.umutk.pulse.extras.whitenoise.installBundledPacks(ctx).toMap()
        listOf("ambient" to null, "birds" to "birds").forEach { (dir, forced) ->
            // bundled assets first; a downloaded pack (Android/data/<pkg>/extras/<id>, or older filesDir/extras/<id>) only adds missing ids
            val dirs = listOfNotNull(bundled[dir], com.umutk.pulse.extras.store.StoreInstaller.dataDir(ctx, dir), File(ctx.filesDir, "extras/$dir"))
                .filter { File(it, "pack.json").isFile }
            for (d in dirs) {
                val arr = runCatching { JSONObject(File(d, "pack.json").readText()).getJSONArray("sounds") }.getOrNull() ?: continue
                for (i in 0 until arr.length()) runCatching {
                    val o = arr.getJSONObject(i)
                    val f = File(d, o.getString("file"))
                    val id = o.getString("id")
                    if (f.isFile && out.none { it.id == id }) {
                        out += Snd(id, o.optString("en", id), o.optString("tr", o.optString("en", id)), forced ?: o.optString("cat", "nature"), f.path)
                    }
                }
            }
        }
        // raw WAV loops stay as the fallback for ids the packs do not have
        val missing = WhiteNoiseSounds.filter { s -> out.none { it.id == s.id } }
        if (missing.isNotEmpty()) {
            val built = installWhiteNoiseSounds(ctx)
            missing.forEach { s ->
                built.firstOrNull { it.id == s.id }?.let { out += Snd(s.id, s.en, s.tr, BUILTIN_CAT[s.id] ?: "noise", it.path) }
            }
        }
        return out
    }

    /** Makes [found] the current sound list. While something plays the engine is kept (new packs show after it stops). */
    fun ensure(ctx: Context, found: List<Snd>) {
        appCtx = ctx.applicationContext
        val e = engine
        if (e == null || (!isPlaying() && sounds.value.map { it.id } != found.map { it.id })) {
            job?.cancel(); e?.release()
            val ne = AmbientEngine(ctx.applicationContext, found.map { AmbientTrack(it.id, it.path) })
            engine = ne
            sounds.value = found
            val app = ctx.applicationContext
            job = scope.launch {
                ne.state.collect { st ->
                    val on = st.filter { it.enabled }.map { it.id }.toSet()
                    _running.value = on
                    if (on.isNotEmpty()) {
                        if (!started) { started = true; MediaService.start(app, AmbientPlaybackService::class.java) }
                        AmbientLast.save(app, mix())
                    } else if (started) {
                        started = false; MediaService.stop(app, AmbientPlaybackService::class.java)
                    }
                }
            }
        }
    }

    fun mix(): Map<String, Int> = _running.value.associateWith { levels.value[it] ?: 50 }

    fun setEnabled(id: String, on: Boolean) {
        val e = engine ?: return
        if (on) push(id)
        e.setEnabled(id, on)
    }

    fun setLevel(id: String, level: Int) {
        levels.value = levels.value + (id to level.coerceIn(0, 100))
        push(id)
        appCtx?.let { AmbientLast.save(it, mix()) }
    }

    fun setMaster(m: Int) {
        master.value = m.coerceIn(0, 100)
        _running.value.forEach { push(it) }
    }

    /** Switches [ids] on or off together. */
    fun setMany(ids: Collection<String>, on: Boolean) = ids.forEach { setEnabled(it, on) }

    fun stopAll() { scope.launch { engine?.setAllEnabled(false) } }

    private var fadeJob: Job? = null

    /** Ramps the master volume to zero over [sec] seconds, stops everything, then puts the volume back. Without a fade it is [stopAll]. */
    fun stopFaded(sec: Int) {
        fadeJob?.cancel()
        if (sec <= 0) { stopAll(); return }
        fadeJob = scope.launch {
            val base = master.value
            val steps = 24
            try {
                for (i in 1..steps) { setMaster(base * (steps - i) / steps); kotlinx.coroutines.delay(sec * 1000L / steps) }
                engine?.setAllEnabled(false)
            } finally { setMaster(base) }
        }
    }

    /** [applyMix] with the master volume rising from zero over [sec] seconds. */
    fun startFaded(m: Map<String, Int>, sec: Int) {
        fadeJob?.cancel()
        if (sec <= 0) { applyMix(m); return }
        fadeJob = scope.launch {
            val base = master.value
            val steps = 24
            try {
                setMaster(0); applyMix(m)
                for (i in 1..steps) { setMaster(base * i / steps); kotlinx.coroutines.delay(sec * 1000L / steps) }
            } finally { setMaster(base) }
        }
    }

    /** Plays exactly [m]; ids that are not installed are skipped. */
    fun applyMix(m: Map<String, Int>) {
        val have = sounds.value.map { it.id }.toSet()
        _running.value.filter { it !in m }.forEach { setEnabled(it, false) }
        m.filterKeys { it in have }.forEach { (id, l) ->
            levels.value = levels.value + (id to l.coerceIn(0, 100)); setEnabled(id, true)
        }
    }

    private var appCtx: Context? = null

    private fun push(id: String) {
        val l = (levels.value[id] ?: 50) * master.value / 100
        engine?.setLevel(id, l)
    }

    /** Background-safe entry for the tile and the service: loads sounds, then runs [then] on the main thread. */
    fun load(ctx: Context, then: () -> Unit) {
        scope.launch {
            val found = withContext(Dispatchers.IO) { scan(ctx) }
            ensure(ctx, found); then()
        }
    }
}
