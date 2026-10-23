package com.umutk.pulse.extras.keyboard

import android.content.Context
import android.graphics.Paint
import java.util.Locale

/** One emoji (or kaomoji) of the picker: the text, its group (0..8 the Unicode groups, 10 and up kaomoji) and its words, in Turkish and English, for the search. */
class EmojiItem(val e: String, val group: Int, val kw: String)

/**
 * Every emoji of Unicode with its names and keywords (assets/kbd/emoji.txt: Unicode CLDR annotations in Turkish and English, the emoji list of the
 * unicode-emoji-json project), plus a set of kaomoji. Emoji this phone's font cannot draw are left out, so the picker shows no empty boxes.
 */
object EmojiData {
    @Volatile private var cache: List<EmojiItem>? = null

    fun norm(s: String): String = buildString(s.length) { s.lowercase(Locale.ROOT).forEach { append(KeyboardDictionary.fold(it)) } }

    /** Reads the list once. Call from a background thread. */
    fun load(c: Context): List<EmojiItem> {
        cache?.let { return it }
        val paint = Paint()
        val out = ArrayList<EmojiItem>(2000)
        runCatching {
            c.assets.open("kbd/emoji.txt").bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val p = line.split('\t')
                    if (p.size < 2) return@forEach
                    val g = p[0].toIntOrNull() ?: return@forEach
                    val e = p[1]
                    if (g < 10 && android.os.Build.VERSION.SDK_INT >= 23 && !paint.hasGlyph(e)) return@forEach
                    out += EmojiItem(e, g, norm(p.getOrElse(2) { "" }))
                }
            }
        }
        cache = out
        return out
    }

    /** The items whose words contain every word of [query]. */
    fun search(all: List<EmojiItem>, query: String, max: Int = 150): List<EmojiItem> {
        val q = norm(query).split(' ').filter { it.isNotEmpty() }
        if (q.isEmpty()) return emptyList()
        return all.asSequence().filter { it.kw.isNotEmpty() && q.all { w -> it.kw.contains(w) } }.take(max).toList()
    }
}
