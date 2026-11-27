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

    @Volatile private var wordIndex: Map<String, String>? = null
    private val vague = setOf("yüz", "face", "adam", "man", "woman", "kadın", "el", "hand", "kişi", "person", "işaret", "sign", "sembol", "symbol", "renk", "color", "kare", "square", "daire", "circle", "ok", "arrow", "the", "and", "ile", "bir", "one", "two", "iki")

    /** One emoji for a single word (a keyword of the emoji, in Turkish or English): the most specific emoji, the one with the fewest keywords. Call from a background thread. */
    fun index(c: Context): Map<String, String> {
        wordIndex?.let { return it }
        val best = HashMap<String, Pair<String, Int>>()
        runCatching {
            c.assets.open("kbd/emoji.txt").bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val p = line.split('\t')
                    if (p.size < 3) return@forEach
                    val g = p[0].toIntOrNull() ?: return@forEach
                    if (g >= 10) return@forEach
                    val kws = p[2].split(';').map { it.trim().lowercase(Locale.ROOT) }.filter { it.isNotEmpty() }
                    kws.forEach { k ->
                        if (k.length < 3 || ' ' in k || k in vague) return@forEach
                        val cur = best[k]
                        if (cur == null || kws.size < cur.second) best[k] = p[1] to kws.size
                    }
                }
            }
        }
        return best.mapValues { it.value.first }.also { wordIndex = it }
    }

    /** The emoji for [word] once the index is built (null before that, or when none fits). */
    fun forWord(word: String): String? = wordIndex?.get(word.lowercase(Locale.ROOT)) ?: chem[word.lowercase(Locale.ROOT)]

    /** Chemistry words (Turkish and English) -> the sign or formula written for them: the benzene ring, formulas, the elements' symbols. Offered on the bar like an emoji. */
    private val chem: Map<String, String> = buildMap {
        // (a local function named put called itself: the map's own put is reached through the receiver)
        val m = this
        fun add(sym: String, vararg names: String) { names.forEach { m.put(it, sym) } }
        add("\u232C", "benzen", "benzene", "benzol")
        add("\u2B21", "heksagon", "hexagon", "altıgen")
        add("H\u2082O", "su", "water", "h2o")
        add("CO\u2082", "karbondioksit", "carbondioxide", "karbon dioksit", "carbon dioxide", "co2")
        add("O\u2082", "oksijen", "oxygen")
        add("H\u2082", "hidrojen", "hydrogen")
        add("N\u2082", "azot", "nitrogen")
        add("NaCl", "tuz", "salt", "sodyum klorür")
        add("CH\u2084", "metan", "methane")
        add("C\u2082H\u2085OH", "etanol", "ethanol", "alkol")
        add("C\u2086H\u2081\u2082O\u2086", "glikoz", "glucose", "glukoz")
        add("NH\u2083", "amonyak", "ammonia")
        add("H\u2082SO\u2084", "sülfürik asit", "sulfuric acid", "sülfürik")
        add("HCl", "hidroklorik asit", "hydrochloric acid", "tuz ruhu")
        add("NaOH", "sodyum hidroksit", "sodium hydroxide", "kostik")
        add("O\u2083", "ozon", "ozone")
        add("\u2697", "kimya", "chemistry", "simya", "alchemy")
        add("Fe", "demir", "iron"); add("Au", "altın", "gold"); add("Ag", "gümüş", "silver"); add("Cu", "bakır", "copper")
        add("Na", "sodyum", "sodium"); add("K", "potasyum", "potassium"); add("Ca", "kalsiyum", "calcium"); add("Mg", "magnezyum", "magnesium")
        add("Hg", "cıva", "mercury"); add("Pb", "kurşun"); add("Zn", "çinko", "zinc"); add("Al", "alüminyum", "aluminium", "aluminum")
        add("Cl", "klor", "chlorine"); add("He", "helyum", "helium"); add("Si", "silisyum", "silicon"); add("U", "uranyum", "uranium")
    }

    /** The items whose words contain every word of [query]. */
    /** Semantic emoji search (as in WM Keyboard): the words are matched whole, by their start, by their Turkish stem, by a near spelling and by meaning
     *  (groups of words that mean the same thing, Turkish and English), and the best matches come first. */
    fun search(all: List<EmojiItem>, query: String, max: Int = 150): List<EmojiItem> {
        val q = norm(query).split(' ').filter { it.isNotEmpty() }
        if (q.isEmpty()) return emptyList()
        // the fast path: every word is in the keywords as it is
        val plain = all.asSequence().filter { it.kw.isNotEmpty() && q.all { w -> it.kw.contains(w) } }.take(max).toList()
        if (plain.size >= 12) return plain
        val wants = q.map { w -> listOf(w, stem(w)) + (meaning[w] ?: meaning[stem(w)] ?: emptyList()) }
        val scored = all.mapNotNull { e ->
            if (e.kw.isEmpty()) return@mapNotNull null
            val toks = e.kw.split(';', ' ').filter { it.isNotEmpty() }
            var total = 0f
            for ((i, alts) in wants.withIndex()) {
                var best = 0f
                for (t in toks) {
                    for ((j, a) in alts.withIndex()) {
                        val sc = when {
                            t == a -> if (j == 0) 3f else if (j == 1) 2.5f else 2f
                            a.length >= 3 && t.startsWith(a) -> if (j == 0) 2f else 1.5f
                            a.length >= 5 && t.length >= 5 && near(t, a) -> 1f
                            else -> 0f
                        }
                        if (sc > best) best = sc
                    }
                }
                if (best == 0f) return@mapNotNull null
                total += best
                if (i > 6) break
            }
            e to (total - toks.size * .01f)
        }
        val seen = plain.toMutableSet()
        return (plain + scored.sortedByDescending { it.second }.map { it.first }.filter { seen.add(it) }).take(max)
    }

    private val sufs = listOf("lerimiz", "larımız", "leri", "ları", "ler", "lar", "lik", "lık", "luk", "lük", "siz", "sız", "suz", "süz", "li", "lı", "lu", "lü", "cik", "cık", "im", "ım", "in", "ın", "de", "da", "te", "ta", "yi", "yı", "i", "ı", "u", "ü", "s", "ing", "ed", "es").map { norm(it) }.distinct().sortedByDescending { it.length }
    private fun stem(w: String): String { for (s in sufs) if (w.length - s.length >= 3 && w.endsWith(s)) return w.dropLast(s.length); return w }
    /** One letter changed, added or left out. */
    private fun near(a: String, b: String): Boolean {
        if (kotlin.math.abs(a.length - b.length) > 1) return false
        var i = 0; var j = 0; var d = 0
        while (i < a.length && j < b.length) {
            if (a[i] == b[j]) { i++; j++; continue }
            if (++d > 1) return false
            when { a.length > b.length -> i++; a.length < b.length -> j++; else -> { i++; j++ } }
        }
        return d + (a.length - i) + (b.length - j) <= 1
    }

    /** Words that mean the same thing (each word of a group finds the others). */
    private val meaning: Map<String, List<String>> by lazy {
        val groups = listOf(
            "sad üzgün mutsuz hüzün ağla cry tear gözyaşı upset kırgın unhappy depressed",
            "happy mutlu sevinç neşe joy glad smile gülümse güldü gül",
            "laugh kahkaha lol haha gül komik funny joke şaka",
            "love aşk sevgi seviyorum heart kalp romance romantik sevgili kiss öpücük",
            "angry kızgın sinirli öfke mad rage hırs furious",
            "scared korku korkmuş fear afraid panik dehşet horror",
            "tired yorgun uykulu sleepy sleep uyku bitkin exhausted",
            "sick hasta ill grip ateş fever virus mikrop",
            "food yemek yiyecek eat acıktım hungry aç meal",
            "drink içecek içmek thirsty susadım",
            "coffee kahve çay tea cup fincan",
            "beer bira alcohol alkol şarap wine cheers şerefe",
            "party parti kutlama celebrate celebration tebrik congrats doğumgünü birthday",
            "money para cash nakit zengin rich dolar euro lira bank banka",
            "work iş çalış office ofis job meslek",
            "school okul ders lesson study çalış sınav exam book kitap",
            "car araba oto vehicle araç drive sür",
            "travel seyahat tatil holiday vacation trip yolculuk uçak plane",
            "home ev house yuva",
            "weather hava güneş sun yağmur rain kar snow bulut cloud",
            "cold soğuk üşüdüm freezing buz ice",
            "hot sıcak heat ateş fire yanıyor",
            "music müzik şarkı song melody dinle listen",
            "sport spor futbol football soccer basketbol ball top maç match",
            "win kazan galibiyet trophy kupa victory zafer champion şampiyon",
            "ok tamam okay peki onay agree yes evet",
            "no hayır yok reddet refuse cross çarpı",
            "thanks teşekkür sağol thank grateful minnettar",
            "sorry özür pardon affet",
            "cat kedi kitten pisi",
            "dog köpek puppy kuçu",
            "baby bebek çocuk child kid",
            "night gece moon ay yıldız star",
            "morning sabah günaydın sunrise",
            "think düşün hmm thinking düşünce idea fikir",
            "strong güçlü kas muscle power",
            "cool havalı süper super awesome harika mükemmel perfect",
            "phone telefon mobil call ara",
            "time zaman saat clock watch",
            "gift hediye present paket",
            "flower çiçek gül rose bahar spring",
            "christmas yılbaşı noel santa",
            "ghost hayalet halloween cadı witch",
        )
        val m = HashMap<String, List<String>>()
        groups.forEach { g -> val ws = g.split(' ').map { norm(it) }.filter { it.isNotEmpty() }; ws.forEach { w -> m[w] = (m[w].orEmpty() + ws.filter { it != w }).distinct() } }
        m
    }
}
