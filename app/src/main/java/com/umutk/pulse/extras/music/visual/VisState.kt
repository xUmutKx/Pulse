package com.umutk.pulse.extras.music.visual

import kotlin.math.exp
import kotlin.math.max

// Vis area of the classic Winamp window in skin pixels. Winamp 2.x fixes it at 76x16 on main.bmp; no skin file defines it.
const val VIS_X = 24
const val VIS_Y = 43
const val VIS_W = 76
const val VIS_H = 16
const val VIS_BAR_W = 3
const val VIS_GAP = 1

/** Bars that fit the vis area: a 3 px bar every 4 px (19 for the classic 76 px area). */
const val VIS_BARS = (VIS_W + VIS_GAP) / (VIS_BAR_W + VIS_GAP)

/**
 * Winamp-style analyser state, advanced once per frame by [step] in frame time. Bars rise in about 20 ms and fall with a
 * 0.2 s time constant. Each peak cap holds about 0.5 s, then drops slowly. [scope] is the waveform: 0..1, 0.5 is the centre.
 * The caller redraws only when [step] returns true, so the window never recomposes for the vis.
 */
class VisState {
    val level = FloatArray(VIS_BARS)
    val peak = FloatArray(VIS_BARS)
    private val hold = FloatArray(VIS_BARS)
    val scope = FloatArray(VIS_W) { 0.5f }
    private var last = 0L

    /** Moves to frame time [now] (nanos). Returns true when anything on screen changed. */
    fun step(bars: List<Float>, wave: List<Float>, now: Long): Boolean {
        val dt = if (last == 0L) 1f / 60 else ((now - last) / 1e9f).coerceIn(0f, 0.1f)
        last = now
        val rise = 1f - exp(-dt / 0.02f)
        val fall = exp(-dt / 0.2f)
        var changed = false
        for (i in 0 until VIS_BARS) {
            val target = bars.getOrElse(i) { 0f }.coerceIn(0f, 1f)
            val old = level[i]
            // shortcut: exp decay never reaches 0, so tiny values snap to 0 to let the view go idle
            val v = (if (target > old) old + (target - old) * rise else max(target, old * fall)).let { if (it < 0.002f) 0f else it }
            val p = peak[i]
            val np = when {
                v >= p -> { hold[i] = 0.5f; v }
                hold[i] > 0f -> { hold[i] = max(0f, hold[i] - dt); p }
                else -> max(v, p - dt * 0.4f)
            }
            if (v != old || np != p) changed = true
            level[i] = v
            peak[i] = np
        }
        for (i in 0 until VIS_W) {
            val s = wave.getOrElse(i) { 0.5f }.coerceIn(0f, 1f)
            if (s != scope[i]) { scope[i] = s; changed = true }
        }
        return changed
    }
}
