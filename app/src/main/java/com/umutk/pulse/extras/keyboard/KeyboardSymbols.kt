package com.umutk.pulse.extras.keyboard

import android.content.Context

/** One sign of the symbol tab: the character and its Unicode name (lower case, English), which the search reads. */
class Sign(val ch: String, val name: String)

/** The symbol tab: the signs of Unicode's symbol, punctuation, number, arrow, shape, music and Braille blocks in families, read once from assets/kbd/symbols.txt (tools/gen_symbols.py). */
object KeyboardSymbols {
    /** A family (flowers, arrows, ...) with its signs in sub-categories ("" when it has none). */
    class Family(val id: String, val en: String, val tr: String, val subs: List<Pair<String, List<Sign>>>) {
        val signs: List<Sign> get() = subs.flatMap { it.second }
    }

    private val names = mapOf(
        "Patterns" to ("Presets" to "Presetler"),
        "Flowers" to ("Flowers" to "Çiçekler"), "Hearts" to ("Hearts" to "Kalpler"), "Stars" to ("Stars" to "Yıldızlar"), "Moon and sky" to ("Moon and sky" to "Ay ve gökyüzü"),
        "Lines" to ("Lines" to "Çizgiler"), "Brackets" to ("Brackets" to "Parantezler"), "Cute" to ("Cute" to "Süs"), "Arrows" to ("Arrows" to "Oklar"), "Math" to ("Math" to "Matematik"), "Shapes" to ("Shapes" to "Şekiller"),
        "Symbols" to ("Symbols" to "Semboller"), "Punctuation" to ("Punctuation" to "Noktalama"), "Currency" to ("Currency" to "Para"), "Numbers" to ("Numbers" to "Sayılar"),
        "Music" to ("Music" to "Müzik"), "Braille" to ("Braille" to "Braille"), "Cuneiform" to ("Cuneiform" to "Çivi yazısı"), "Hieroglyphs" to ("Hieroglyphs" to "Hiyeroglif"),
    )
    private val subNames = mapOf("Cute faces" to "Tatlı yüzler", "Hearts and ribbons" to "Kalp ve kurdele", "Classics" to "Klasikler", "Lines" to "Çizgiler", "Wings" to "Kanatlar", "Brackets" to "Parantezler", "Long" to "Uzunlar", "Text and art" to "Yazı ve sanat")

    /** The name of a sub-category in the app's language (the Unicode block names stay English). */
    fun subName(en: String): String = com.umutk.pulse.tr(en, subNames[en] ?: en)

    @Volatile private var cache: List<Family>? = null

    /** Reads the file once; call off the main thread. */
    fun load(c: Context): List<Family> {
        cache?.let { return it }
        val out = ArrayList<Family>()
        var id = ""; var subs = ArrayList<Pair<String, List<Sign>>>(); var subName = ""; var list = ArrayList<Sign>()
        fun closeSub() { if (list.isNotEmpty()) subs += subName to list; list = ArrayList() }
        fun close() { closeSub(); if (id.isNotEmpty()) { val n = names[id] ?: (id to id); out += Family(id, n.first, n.second, subs) }; subs = ArrayList() }
        runCatching {
            c.assets.open("kbd/symbols.txt").bufferedReader().useLines { lines ->
                lines.forEach { l ->
                    when {
                        l.startsWith("##") -> { closeSub(); subName = l.substring(2) }
                        l.startsWith("#") -> { close(); id = l.substring(1); subName = "" }
                        else -> { val t = l.indexOf('\t'); if (t > 0) list += Sign(l.substring(0, t), l.substring(t + 1)) }
                    }
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
