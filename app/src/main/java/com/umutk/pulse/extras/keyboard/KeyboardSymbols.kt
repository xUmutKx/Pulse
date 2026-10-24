package com.umutk.pulse.extras.keyboard

import android.content.Context

/** One sign of the symbol tab: the character and its Unicode name (lower case, English), which the search reads. */
class Sign(val ch: String, val name: String)

/** The symbol tab: the signs of Unicode's symbol, punctuation, number, arrow, shape, music and Braille blocks in families, read once from assets/kbd/symbols.txt (tools/gen_symbols.py). */
object KeyboardSymbols {
    class Family(val id: String, val en: String, val tr: String, val signs: List<Sign>)

    private val names = mapOf(
        "Patterns" to ("Patterns" to "Hazır süsler"), "Flowers" to ("Flowers" to "Çiçekler"), "Hearts" to ("Hearts" to "Kalpler"), "Stars" to ("Stars" to "Yıldızlar"), "Moon and sky" to ("Moon and sky" to "Ay ve gökyüzü"),
        "Lines" to ("Lines" to "Çizgiler"), "Brackets" to ("Brackets" to "Parantezler"), "Cute" to ("Cute" to "Süs"), "Arrows" to ("Arrows" to "Oklar"), "Math" to ("Math" to "Matematik"), "Shapes" to ("Shapes" to "Şekiller"),
        "Symbols" to ("Symbols" to "Semboller"), "Punctuation" to ("Punctuation" to "Noktalama"), "Currency" to ("Currency" to "Para"), "Numbers" to ("Numbers" to "Sayılar"),
        "Music" to ("Music" to "Müzik"), "Braille" to ("Braille" to "Braille"),
    )

    @Volatile private var cache: List<Family>? = null

    /** Reads the file once; call off the main thread. */
    fun load(c: Context): List<Family> {
        cache?.let { return it }
        val out = ArrayList<Family>()
        var id = ""; var list = ArrayList<Sign>()
        fun close() { if (id.isNotEmpty()) { val n = names[id] ?: (id to id); out += Family(id, n.first, n.second, list) } }
        runCatching {
            c.assets.open("kbd/symbols.txt").bufferedReader().useLines { lines ->
                lines.forEach { l ->
                    if (l.startsWith("#")) { close(); id = l.substring(1); list = ArrayList() }
                    else { val t = l.indexOf('\t'); if (t > 0) list += Sign(l.substring(0, t), l.substring(t + 1)) }
                }
            }
            close()
        }
        cache = out
        return out
    }

    /** The signs whose Unicode name has every word of [q] (English), first 120. */
    fun search(all: List<Family>, q: String): List<Sign> {
        val w = q.trim().lowercase().split(' ').filter { it.isNotEmpty() }
        if (w.isEmpty()) return emptyList()
        return all.asSequence().flatMap { it.signs.asSequence() }.filter { s -> w.all { it in s.name } }.take(120).toList()
    }
}
