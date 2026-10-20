package com.umutk.pulse.extras.music.poweramp

import android.content.Context
import android.media.audiofx.EnvironmentalReverb
import kotlin.math.roundToInt

/**
 * Poweramp's reverb tab: four knobs (mix, size, damp, pre-delay) over Android's [EnvironmentalReverb] on the
 * player's audio session. Values are 0..1 and saved in the Poweramp prefs; the player calls [attach] on each session.
 * shortcut: Poweramp's fade and filter knobs are not mapped, EnvironmentalReverb has no direct match for them.
 */
internal object PowerampReverb {
    private var fx: EnvironmentalReverb? = null
    private var session = -1

    private fun sp(c: Context) = c.getSharedPreferences("pulse_poweramp", Context.MODE_PRIVATE)

    /** Order: mix, size, damp, pre-delay. */
    fun load(c: Context): FloatArray = floatArrayOf(
        sp(c).getFloat("rv_mix", 0f), sp(c).getFloat("rv_size", .5f), sp(c).getFloat("rv_damp", .5f), sp(c).getFloat("rv_pre", 0f),
    )

    fun set(c: Context, v: FloatArray) {
        sp(c).edit().putFloat("rv_mix", v[0]).putFloat("rv_size", v[1]).putFloat("rv_damp", v[2]).putFloat("rv_pre", v[3]).apply()
        push(v)
    }

    /** Re-binds the effect to a new audio session and applies the saved values. Session 0 means none yet. */
    fun attach(c: Context, sessionId: Int) {
        if (sessionId <= 0 || sessionId == session) return
        session = sessionId
        fx?.release()
        fx = runCatching { EnvironmentalReverb(0, sessionId) }.getOrNull()
        push(load(c))
    }

    private fun push(v: FloatArray) {
        val e = fx ?: return
        runCatching {
            e.decayTime = (300 + v[1] * 5700).roundToInt()          // ms, size
            e.decayHFRatio = (2000 - v[2] * 1900).roundToInt().toShort()// permille, more damp = darker tail
            e.reverbLevel = (-9000 + v[0] * 9000).roundToInt().toShort()      // mB, mix
            e.reverbDelay = (v[3] * 100).roundToInt()               // ms, pre-delay (max 100)
            e.enabled = v[0] > 0f
        }
    }
}
