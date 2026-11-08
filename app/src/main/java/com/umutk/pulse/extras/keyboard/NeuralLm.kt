package com.umutk.pulse.extras.keyboard

import android.content.Context
import java.util.concurrent.ConcurrentHashMap

/**
 * The keyboard's neural language models (one per language, [CharLm]), loaded once from the assets, and the state of the text before the word,
 * kept for the last context (the bar and the space key ask about the same sentence many times).
 */
internal object NeuralLm {
    private val models = ConcurrentHashMap<String, CharLm>()
    private val missing = ConcurrentHashMap.newKeySet<String>()
    @Volatile private var lastKey = ""
    @Volatile private var lastState: FloatArray? = null

    /** The model of a dictionary language ("tr", "en"), or null when there is none. */
    fun get(c: Context, lang: String): CharLm? {
        models[lang]?.let { return it }
        if (lang in missing) return null
        return synchronized(this) {
            models[lang] ?: runCatching { c.assets.open("kb/lm_$lang.bin").use { CharLm.load(it) } }.getOrNull()?.also { models[lang] = it } ?: run { missing += lang; null }
        }
    }

    /** The model's state after [ctx] (the text before the word, lower-cased), from the cache when the text is the same. */
    fun state(lm: CharLm, lang: String, ctx: String): FloatArray {
        val key = "$lang\u0000$ctx"
        if (key == lastKey) lastState?.let { return it }
        val s = lm.state(ctx)
        lastState = s; lastKey = key
        return s
    }
}
