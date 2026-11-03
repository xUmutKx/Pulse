package com.umutk.pulse.extras.music

import kotlin.math.roundToInt

/**
 * Live tempo from the bass of the spectrum: a beat is the bass jumping above its own recent average, and the tempo is the median
 * gap between the last beats, folded into 80..180 BPM (a half or double reading is moved into that range). Needs a few beats first.
 */
class BpmEstimator {
    private var avg = 0f
    private var lastBeat = 0L
    private val gaps = ArrayList<Long>()
    /** Beats per minute, 0 until enough beats were heard. */
    var bpm: Int = 0
        private set

    /** [bass] is the bass level (0..1) and [now] the time in milliseconds; call it on every frame of the level meter. */
    fun feed(bass: Float, now: Long) {
        avg = if (avg == 0f) bass else avg * .95f + bass * .05f
        val gap = now - lastBeat
        val beat = bass > 0.12f && bass > avg * 1.35f + .04f && (lastBeat == 0L || gap > 280L)
        if (!beat) return
        if (lastBeat != 0L && gap < 2000L) {
            gaps.add(gap)
            if (gaps.size > 12) gaps.removeAt(0)
        }
        lastBeat = now
        if (gaps.size >= 4) {
            val median = gaps.sorted()[gaps.size / 2].toFloat()
            var b = 60000f / median
            while (b < 80f) b *= 2f
            while (b > 180f) b /= 2f
            bpm = b.roundToInt()
        }
    }
}
