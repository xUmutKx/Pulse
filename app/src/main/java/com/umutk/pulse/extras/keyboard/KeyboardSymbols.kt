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
        "Patterns" to ("Presets" to "Presetler"), "Special" to ("Special signs" to "Özel işaretler"),
        "Flowers" to ("Flowers" to "Çiçekler"), "Hearts" to ("Hearts" to "Kalpler"), "Stars" to ("Stars" to "Yıldızlar"), "Moon and sky" to ("Moon and sky" to "Ay ve gökyüzü"),
        "Lines" to ("Lines" to "Çizgiler"), "Brackets" to ("Brackets" to "Parantezler"), "Cute" to ("Cute" to "Süs"), "Arrows" to ("Arrows" to "Oklar"), "Math" to ("Math" to "Matematik"), "Shapes" to ("Shapes" to "Şekiller"),
        "Symbols" to ("Symbols" to "Semboller"), "Punctuation" to ("Punctuation" to "Noktalama"), "Currency" to ("Currency" to "Para"), "Numbers" to ("Numbers" to "Sayılar"),
        "Music" to ("Music" to "Müzik"), "Braille" to ("Braille" to "Braille"), "Cuneiform" to ("Cuneiform" to "Çivi yazısı"), "Hieroglyphs" to ("Hieroglyphs" to "Hiyeroglif"),
    )
    private val subNames = mapOf("Sparkles" to "Parıltılar", "Kaomoji" to "Kaomoji", "Boxes" to "Kutular", "Ancient" to "Antik", "Mixed" to "Karışık", "System and keys" to "Sistem ve tuşlar", "Security and warnings" to "Güvenlik ve uyarı", "Direction and flow" to "Yön ve akış", "Grids and frames" to "Izgara ve çerçeve", "Documents and badges" to "Belge ve rozetler", "Alchemy" to "Simya", "Egyptian hieroglyphs" to "Mısır hiyeroglifleri", "Aegean objects" to "Ege nesneleri", "Phaistos disc" to "Phaistos diski", "Anatolian hieroglyphs" to "Anadolu hiyeroglifleri", "Bamum" to "Bamum", "Vai" to "Vai", "Yi" to "Yi", "Linear B" to "Lineer B", "Cuneiform signs" to "Çivi yazısı işaretleri", "Old Italic, Phoenician, Coptic" to "Eski İtalik, Fenike, Kıpti", "Canadian syllabics" to "Kanada yerli yazısı", "Tifinagh" to "Tifinagh", "South Arabian" to "Güney Arabistan", "Brahmi" to "Brahmi", "Ethiopic" to "Etiyopya", "Cherokee" to "Çeroki", "Targets and lines" to "Hedef ve çizgiler", "Punctuation ornaments" to "Noktalama süsleri", "Map and route" to "Harita ve rota", "Dividers" to "Ayraçlar", "Frames" to "Çerçeveler", "Chains" to "Zincirler", "Wings and crowns" to "Kanat ve taçlar", "Sparkle borders" to "Parıltılı kenarlar", "Cute faces" to "Tatlı yüzler", "Hearts and ribbons" to "Kalp ve kurdele", "Classics" to "Klasikler", "Lines" to "Çizgiler", "Wings" to "Kanatlar", "Brackets" to "Parantezler", "Long" to "Uzunlar", "Text and art" to "Yazı ve sanat")

    /** The name of a sub-category in the app's language (the Unicode block names stay English). */
    fun subName(en: String): String = com.umutk.pulse.tr(en, subNames[en] ?: en)

    @Volatile private var cache: List<Family>? = null

    /** Reads the files once (the Unicode families, then the extra file: the special signs and more presets, which join the families of the same name); call off the main thread. */
    fun load(c: Context): List<Family> {
        cache?.let { return it }
        val out = LinkedHashMap<String, Family>()
        runCatching {
            for (file in listOf("kbd/symbols.txt", "kbd/symbols_more.txt")) {
                var id = ""; var subs = ArrayList<Pair<String, List<Sign>>>(); var subName = ""; var list = ArrayList<Sign>()
                fun closeSub() { if (list.isNotEmpty()) subs += subName to list; list = ArrayList() }
                fun close() {
                    closeSub()
                    if (id.isNotEmpty()) {
                        val n = names[id] ?: (id to id)
                        val old = out[id]
                        out[id] = if (old == null) Family(id, n.first, n.second, subs) else Family(id, n.first, n.second, old.subs + subs)
                    }
                    subs = ArrayList()
                }
                c.assets.open(file).bufferedReader().useLines { lines ->
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
        }
        // the signs this phone's fonts cannot draw are left out (they showed as empty cells); the presets are whole texts and stay as they are
        val paint = android.graphics.Paint()
        val res = out.values.map { f ->
            if (f.id == "Patterns" || android.os.Build.VERSION.SDK_INT < 23) f
            else Family(f.id, f.en, f.tr, f.subs.map { (n, l) -> n to l.filter { paint.hasGlyph(it.ch) } }.filter { it.second.isNotEmpty() })
        }.filter { it.subs.isNotEmpty() }
        cache = res
        return res
    }

    /** The signs whose Unicode name has every word of [q] (English), first 120. */
    fun search(all: List<Family>, q: String): List<Sign> {
        val w = q.trim().lowercase().split(' ').filter { it.isNotEmpty() }
        if (w.isEmpty()) return emptyList()
        return all.asSequence().flatMap { it.signs.asSequence() }.filter { s -> w.all { it in s.name } }.take(120).toList()
    }
}
