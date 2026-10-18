package com.umutk.pulse.extras.ambient

import android.content.Context
import org.json.JSONObject

/** The last mix that played, for the Quick Settings tile and for a restart after Android killed the process. */
object AmbientLast {
    private const val PREFS = "ambient_last"

    /** Called whenever the mix changes while playing. An empty mix is ignored so the last real one stays. */
    fun save(ctx: Context, mix: Map<String, Int>) {
        if (mix.isEmpty()) return
        val o = JSONObject(); mix.forEach { (k, v) -> o.put(k, v) }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("mix", o.toString()).apply()
    }

    fun load(ctx: Context): Map<String, Int> {
        val s = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("mix", null) ?: return emptyMap()
        val o = runCatching { JSONObject(s) }.getOrNull() ?: return emptyMap()
        return o.keys().asSequence().associateWith { o.optInt(it, 50) }
    }

    fun hasLast(ctx: Context): Boolean = load(ctx).isNotEmpty()

    fun isPlaying(): Boolean = AmbientMixer.isPlaying()

    /** Stops the mix when it plays, else starts the last one in the foreground service (the mixer starts it). */
    fun toggle(ctx: Context) {
        if (isPlaying()) { AmbientMixer.stopAll(); return }
        resume(ctx)
    }

    /** Starts the last mix; for the tile and for AmbientPlaybackService.onResume after a process restart. */
    fun resume(ctx: Context) {
        val m = load(ctx)
        if (m.isEmpty() || isPlaying()) return
        AmbientMixer.load(ctx) { AmbientMixer.applyMix(m) }
    }
}
