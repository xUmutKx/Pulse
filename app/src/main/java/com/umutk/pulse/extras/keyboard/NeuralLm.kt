package com.umutk.pulse.extras.keyboard

import android.content.Context
import java.util.concurrent.ConcurrentHashMap

/**
 * The keyboard's neural language models ([CharLm]), loaded once from the assets, and the state of the text before the word.
 *
 * Two sizes can ship for a language: the large one (lm_xx_L.bin) and the small one (lm_xx.bin). Each is timed on the phone when it is loaded; the large
 * one is used where one step is quick, the small one where only that is, and on a phone too slow for both the keyboard uses its word rules alone (no
 * lag is ever traded for the model). It also watches itself: if the model's work for a word keeps taking too long, it is left out until the next start.
 */
internal object NeuralLm {
    private val models = ConcurrentHashMap<String, CharLm>()
    private val missing = ConcurrentHashMap.newKeySet<String>()
    /** A step of the large model may take this long (ns): about 40 steps are made for a word, so the whole word stays well under a frame or two. */
    private const val LARGE_NS = 1_500_000L
    private const val SMALL_NS = 600_000L
    /** The model's average time for a word (ms) above which it is left out for this run. */
    private const val SLOW_MS = 90f
    @Volatile private var avgMs = 0f
    @Volatile private var off = false

    private fun load(c: Context, file: String, limitNs: Long): CharLm? {
        val lm = runCatching { c.assets.open("kb/$file").use { CharLm.load(it) } }.getOrNull() ?: return null
        val ns = runCatching { lm.stepNanos() }.getOrDefault(Long.MAX_VALUE)
        return if (ns <= limitNs) lm else null
    }

    /** The model of a dictionary language ("tr", "en") that this phone can run, or null when there is none (or it is too slow). */
    fun get(c: Context, lang: String): CharLm? {
        if (off) return null
        models[lang]?.let { return it }
        if (lang in missing) return null
        return synchronized(this) {
            models[lang] ?: (load(c, "lm_${lang}_L.bin", LARGE_NS) ?: load(c, "lm_$lang.bin", SMALL_NS))?.also { models[lang] = it } ?: run { missing += lang; null }
        }
    }

    /** What one word's work with the model took (ms): a run of slow ones turns the model off. */
    fun took(ms: Long) {
        avgMs = if (avgMs == 0f) ms.toFloat() else avgMs * .85f + ms * .15f
        if (avgMs > SLOW_MS) off = true
    }

    private class Snap(val lm: CharLm, val ctx: String, val st: FloatArray, val since: Int)
    @Volatile private var snap: Snap? = null

    /**
     * The model's state after [ctx] (the text before the word, lower-cased). When the text is the one before with a few more characters (and the window
     * of the last characters has slid on), only the new characters are read instead of the whole text again.
     */
    fun state(lm: CharLm, lang: String, ctx: String): FloatArray {
        val s = snap
        if (s != null && s.lm === lm) {
            if (s.ctx == ctx) return s.st
            val tail = s.ctx.takeLast(60)
            if (tail.length >= 20) {
                val p = ctx.indexOf(tail)
                val more = if (p >= 0) ctx.length - (p + tail.length) else -1
                if (more in 0..200 && s.since + more <= 300) {
                    val st = if (more == 0) s.st else lm.advance(s.st, ctx.substring(p + tail.length))
                    snap = Snap(lm, ctx, st, s.since + more)
                    return st
                }
            }
        }
        val st = lm.state(ctx)
        snap = Snap(lm, ctx, st, minOf(ctx.length, 120))
        return st
    }
}
