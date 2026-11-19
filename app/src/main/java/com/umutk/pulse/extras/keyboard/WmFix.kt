package com.umutk.pulse.extras.keyboard

import android.content.Context
import java.util.concurrent.ConcurrentHashMap

/** One WM fixer per language (built from the same word list Pulse uses, the first time the hybrid correction needs it). */
object WmFix {
    class Cand(val word: String, val score: Double, val edits: Int)
    private val fixers = ConcurrentHashMap<String, Long>()
    private val building = ConcurrentHashMap.newKeySet<String>()
    private val rows = mapOf("tr" to "qwertyuıopğü,asdfghjklşi,zxcvbnmöç", "en" to "qwertyuiop,asdfghjkl,zxcvbnm")

    /** Builds the fixer of [lang] in the background; [search] answers nothing until it is ready. */
    fun warm(c: Context, lang: String) {
        if (!WmFixNative.AVAILABLE || fixers.containsKey(lang) || !building.add(lang)) return
        Thread {
            runCatching {
                val f = java.io.File(c.filesDir, "kbd/$lang.txt")
                val text = (if (f.exists()) f.inputStream() else c.assets.open("kbd/$lang.txt")).bufferedReader().use { it.readText() }
                val h = WmFixNative.create(text, rows[lang] ?: rows.getValue("en"))
                if (h != 0L) fixers[lang] = h
            }
            building.remove(lang)
        }.start()
    }

    fun search(lang: String, typed: String, limit: Int = 3): List<Cand> {
        val h = fixers[lang] ?: return emptyList()
        return runCatching {
            WmFixNative.search(h, typed, limit).split('\n').mapNotNull { l -> l.split('\t').takeIf { it.size == 3 }?.let { Cand(it[0], it[1].toDouble(), it[2].toInt()) } }
        }.getOrDefault(emptyList())
    }
}
