package com.umutk.pulse.extras.keyboard

import android.content.Context
import java.io.File
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Word lists (assets/kbd/<lang>.txt: the most used words with their counts), the words and word pairs the user types, and the correction and
 * completion maths. A typo costs less when the wrong key sits next to the right one, or when only an accent is missing (i for ı, s for ş).
 */
object KeyboardDictionary {
    private class Lex(val words: Array<String>, val score: FloatArray) {
        val sorted: IntArray = words.indices.sortedBy { words[it] }.toIntArray()
        val index: HashMap<String, Int> = HashMap<String, Int>(words.size * 2).also { m -> words.forEachIndexed { i, w -> m[w] = i } }
    }

    private val lexes = ConcurrentHashMap<String, Lex>()
    private val user = ConcurrentHashMap<String, Int>()
    private val pairs = ConcurrentHashMap<String, ConcurrentHashMap<String, Int>>()
    private var userLoaded = false
    private fun d(l: String) = KeyboardLayouts.dict(l)
    fun dictOf(code: String) = d(code)
    private var dirty = 0

    /** A candidate word with its score (bigger is better) and how far it is from what was typed (0 = the typed word itself). */
    class Cand(val word: String, val score: Float, val cost: Float)

    private val emojiFor = mapOf(
        "love" to "❤️", "aşk" to "❤️", "sevgi" to "❤️", "happy" to "😊", "mutlu" to "😊", "sad" to "😢", "üzgün" to "😢", "lol" to "😂", "haha" to "😂", "fire" to "🔥", "ateş" to "🔥",
        "yes" to "👍", "evet" to "👍", "ok" to "👌", "tamam" to "👌", "thanks" to "🙏", "teşekkürler" to "🙏", "please" to "🙏", "birthday" to "🎂", "doğum" to "🎂", "pizza" to "🍕",
        "coffee" to "☕", "kahve" to "☕", "cat" to "🐱", "kedi" to "🐱", "dog" to "🐶", "köpek" to "🐶", "sun" to "☀️", "güneş" to "☀️", "sleep" to "😴", "uyku" to "😴", "party" to "🎉", "parti" to "🎉",
    )

    fun emoji(word: String): String? = emojiFor[word.lowercase(Locale.ROOT)]

    /** Reads the word list of [lang] (and the learned words once). Call from a background thread. */
    fun load(c: Context, code: String) {
        val lang = d(code)
        if (!userLoaded) loadUser(c)
        if (lexes.containsKey(lang)) return
        val ws = ArrayList<String>(); val sc = ArrayList<Float>()
        // a list downloaded in the settings first, else the one inside the app
        val file = File(c.filesDir, "kbd/$lang.txt")
        runCatching {
            (if (file.exists()) file.inputStream() else c.assets.open("kbd/$lang.txt")).bufferedReader().useLines { lines ->
                lines.forEach { line -> val i = line.indexOf(' '); if (i > 0) { ws += line.substring(0, i); sc += ln((line.substring(i + 1).toDoubleOrNull() ?: 1.0) + 1.0).toFloat() } }
            }
        }
        if (ws.isNotEmpty()) lexes[lang] = Lex(ws.toTypedArray(), sc.toFloatArray())
    }

    /** Whether a word list exists for [code]: inside the app or downloaded. */
    fun hasList(c: Context, code: String): Boolean = File(c.filesDir, "kbd/${d(code)}.txt").exists() || runCatching { c.assets.open("kbd/${d(code)}.txt").close(); true }.getOrDefault(false)

    /** Downloads the most used words of [code] (FrequencyWords, CC BY-SA 4.0) into the app's files and loads them. Call from a background thread; true when it worked. */
    fun download(c: Context, code: String): Boolean = runCatching {
        val lang = d(code)
        val url = java.net.URL("https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/$lang/${lang}_50k.txt")
        val out = ArrayList<String>()
        val conn = (url.openConnection() as java.net.HttpURLConnection).apply { connectTimeout = 15000; readTimeout = 30000 }
        conn.inputStream.bufferedReader().useLines { lines ->
            for (ln in lines) {
                val i = ln.indexOf(' ')
                if (i <= 0) continue
                val w = ln.substring(0, i)
                if (w.length in 1..24 && w.all { it.isLetter() || it == '\'' } && !(w.length == 1 && lang != "tr" && lang != "en" && w.none { it in "aeiouyàáâéèêíóúyà" })) out += ln
                if (out.size >= 40000) break
            }
        }
        if (out.size < 500) return@runCatching false
        File(c.filesDir, "kbd").mkdirs()
        File(c.filesDir, "kbd/$lang.txt").writeText(out.joinToString("\n") + "\n")
        lexes.remove(lang)
        load(c, lang)
        true
    }.getOrDefault(false)

    private fun loadUser(c: Context) {
        userLoaded = true
        runCatching { File(c.filesDir, "kbd_user.txt").forEachLine { l -> val p = l.split(' '); if (p.size == 2) user[p[0]] = p[1].toIntOrNull() ?: 1 } }
        runCatching { File(c.filesDir, "kbd_typos.txt").forEachLine { l -> val p = l.split(' '); if (p.size == 2) typos[p[0]] = p[1] } }
        runCatching { File(c.filesDir, "kbd_pairs.txt").forEachLine { l -> val p = l.split(' '); if (p.size == 3) pairs.getOrPut(p[0]) { ConcurrentHashMap() }[p[1]] = p[2].toIntOrNull() ?: 1 } }
    }

    fun saveUser(c: Context) {
        if (dirty == 0) return
        dirty = 0
        runCatching {
            File(c.filesDir, "kbd_user.txt").writeText(user.entries.sortedByDescending { it.value }.take(8000).joinToString("\n") { "${it.key} ${it.value}" })
            File(c.filesDir, "kbd_pairs.txt").writeText(pairs.entries.flatMap { (a, m) -> m.entries.map { Triple(a, it.key, it.value) } }.sortedByDescending { it.third }.take(8000).joinToString("\n") { "${it.first} ${it.second} ${it.third}" })
        }
    }

    /** Forgets everything learned from the user (settings > factory settings, and the "Clear learned words" row). */
    fun forgetUser(c: Context) {
        user.clear(); pairs.clear(); typos.clear(); dirty = 0
        File(c.filesDir, "kbd_user.txt").delete(); File(c.filesDir, "kbd_pairs.txt").delete(); File(c.filesDir, "kbd_typos.txt").delete()
    }

    fun learnedCount() = user.size

    /** Words taken from another keyboard: each becomes a known word (counted three times, so it is not "corrected" away). */
    fun importUser(c: Context, words: List<String>) {
        if (!userLoaded) loadUser(c)
        words.forEach { w -> val l = w.lowercase(Locale.ROOT); if ((user[l] ?: 0) < 3) user[l] = 3 }
        dirty = 1; saveUser(c)
    }

    /** A word the user finished: it counts towards the completions, and the pair with the word before it towards the next-word suggestions. */
    fun learn(c: Context, prev: String?, word: String) {
        val w = word.lowercase(Locale.ROOT)
        if (w.length < 2 || w.any { it.isDigit() }) return
        user[w] = (user[w] ?: 0) + 1
        if (!prev.isNullOrEmpty()) { val m = pairs.getOrPut(prev.lowercase(Locale.ROOT)) { ConcurrentHashMap() }; m[w] = (m[w] ?: 0) + 1 }
        if (++dirty >= 25) saveUser(c)
    }

    fun isWord(code: String, w: String): Boolean {
        val lang = d(code)
        val l = w.lowercase(Locale.ROOT)
        return lexes[lang]?.index?.containsKey(l) == true || (user[l] ?: 0) >= 2 || (lang == "tr" && trKnown(l))
    }

    private fun score(code: String, w: String): Float {
        val lang = d(code)
        val lx = lexes[lang]
        val base = lx?.index?.get(w)?.let { lx.score[it] } ?: 0f
        val u = user[w] ?: 0
        return maxOf(base, if (u > 0) 8f + ln(1f + u) * 1.5f else 0f) + if (u > 0) 1f else 0f
    }

    /** Words that start with [prefix] (lowercase), the likeliest first. */
    fun complete(code: String, prefix: String, n: Int): List<Cand> {
        val lang = d(code)
        val out = ArrayList<Cand>()
        val lx = lexes[lang]
        if (lx != null) {
            var lo = 0; var hi = lx.sorted.size
            while (lo < hi) { val mid = (lo + hi) ushr 1; if (lx.words[lx.sorted[mid]] < prefix) lo = mid + 1 else hi = mid }
            var i = lo; var seen = 0
            while (i < lx.sorted.size && seen < 4000) {
                val w = lx.words[lx.sorted[i]]
                if (!w.startsWith(prefix)) break
                out += Cand(w, lx.score[lx.sorted[i]] - 0.35f * (w.length - prefix.length), 0f); i++; seen++
            }
        }
        user.forEach { (w, _) -> if (w.startsWith(prefix) && out.none { it.word == w }) out += Cand(w, score(lang, w) - 0.35f * (w.length - prefix.length), 0f) }
        return out.sortedByDescending { it.score }.take(n)
    }

    /** Words close to [typed] (lowercase): the typed word itself when it is one, and the ones a typo or a missing accent away. */
    fun correct(code: String, typed: String, maxCost: Float, n: Int): List<Cand> {
        val lang = d(code)
        val lx = lexes[lang] ?: return emptyList()
        val t = typed
        val best = ArrayList<Cand>()
        val d0 = FloatArray(32); val d1 = FloatArray(32); val d2 = FloatArray(32)
        val f0 = foldc(t[0]); val f1 = if (t.length > 1) foldc(t[1]) else f0
        fun consider(w: String, base: Float) {
            if (abs(w.length - t.length) > 2 || w.length > 28) return
            // a typo is rarely on the first letter: the word starts like the typed one, with a neighbour key, or is one letter longer or shorter at the start
            if (t.length >= 3) { val w0 = foldc(w[0]); if (w0 != f0 && w0 != f1 && !near(w[0], t[0]) && !(w.length > 1 && foldc(w[1]) == f0)) return }
            val cost = distance(t, w, maxCost, d0, d1, d2)
            if (cost > maxCost) return
            best += Cand(w, base - 4f * cost, cost)
        }
        for (i in lx.words.indices) consider(lx.words[i], lx.score[i])
        user.forEach { (w, _) -> if (best.none { it.word == w }) consider(w, score(lang, w)) }
        return best.sortedByDescending { it.score }.take(n)
    }

    // ---- Turkish endings ---------------------------------------------------------------------------------------------------------------------------
    // The lists hold the base words; an inflected word ("yanlışlıkları") is a base word with a chain of endings. A typed word counts as known when it splits
    // into a known base and a chain of endings, and a mistyped one is mended on both sides: the base word by the typo cost, the ending to the nearest chain.

    private val endings = listOf("lar", "ler", "ım", "im", "um", "üm", "ın", "in", "un", "ün", "ı", "i", "u", "ü", "a", "e", "da", "de", "ta", "te", "dan", "den", "tan", "ten",
        "nın", "nin", "nun", "nün", "ya", "ye", "yı", "yi", "yu", "yü", "lı", "li", "lu", "lü", "sız", "siz", "suz", "süz", "ki", "ken", "mış", "miş", "muş", "müş",
        "dı", "di", "du", "dü", "tı", "ti", "tu", "tü", "yor", "ıyor", "iyor", "uyor", "üyor", "acak", "ecek", "mak", "mek", "ma", "me", "m", "n", "k", "nız", "niz", "nuz", "nüz",
        "ız", "iz", "uz", "üz", "lık", "lik", "luk", "lük", "cı", "ci", "cu", "cü", "çı", "çi", "çu", "çü", "la", "le", "ca", "ce", "ça", "çe", "dır", "dir", "dur", "dür",
        "tır", "tir", "tur", "tür", "sın", "sin", "sun", "sün", "sı", "si", "su", "sü", "nı", "ni", "nu", "nü", "na", "ne", "nda", "nde", "ndan", "nden", "mız", "miz", "muz", "müz",
        "ları", "leri", "larım", "lerim", "ımız", "imiz", "dım", "dim", "tım", "tim", "dık", "dik", "tık", "tik", "sa", "se", "mı", "mi", "mu", "mü", "bil", "ebil", "abil", "madı", "medi")
    private val common = listOf("lar", "ler", "ı", "i", "u", "ü", "ın", "in", "da", "de", "dan", "den", "a", "e", "ya", "ye", "lı", "li", "sız", "siz", "ım", "im", "mız", "miz", "ları", "leri", "ki", "la", "le", "lık", "lik", "yor", "mak", "mek")

    /** Endings by length: single ones, two in a row, and three in a row from the common ones. */
    private val chains: Array<List<String>> by lazy {
        val set = LinkedHashSet<String>()
        set += ""
        endings.forEach { set += it }
        for (a in endings) for (b in endings) if (a.length + b.length <= 9) set += a + b
        for (a in common) for (b in common) for (c in common) if (a.length + b.length + c.length <= 9) set += a + b + c
        val by = Array(10) { ArrayList<String>() }
        set.forEach { if (it.length < 10) by[it.length] += it }
        Array(10) { by[it] }
    }
    private val chainSet: Set<String> by lazy { chains.flatMap { it }.toHashSet() }

    private fun backVowel(c: Char) = c in "aıou"
    private fun frontVowel(c: Char) = c in "eiöü"

    /** The ending agrees with the last vowel of the base (back or front vowels), as Turkish endings do. */
    private fun harmony(stem: String, tail: String): Boolean {
        if (tail.isEmpty()) return true
        if ("yor" in tail || "ki" in tail || "ken" in tail) return true
        val v = stem.lastOrNull { backVowel(it) || frontVowel(it) } ?: return true
        val back = backVowel(v)
        return tail.none { if (back) frontVowel(it) else backVowel(it) }
    }

    private fun trBaseWord(lx: Lex, w: String): Boolean = lx.index.containsKey(w) || (user[w] ?: 0) >= 2

    /** A base word and a chain of endings: the word is a real (inflected) Turkish word. */
    fun trKnown(w: String): Boolean {
        val lx = lexes["tr"] ?: return false
        if (w.length < 5) return false
        for (k in w.length - 1 downTo 3) {
            val tail = w.substring(k)
            if (tail.length > 9) break
            if (tail in chainSet && harmony(w.substring(0, k), tail) && trBaseWord(lx, w.substring(0, k))) return true
            // a softened last consonant (kitap -> kitabı, çocuk -> çocuğu)
            val stem = w.substring(0, k)
            val hard = when (stem.last()) { 'b' -> "p"; 'c' -> "ç"; 'd' -> "t"; 'ğ' -> "k"; else -> null }
            if (hard != null && tail.isNotEmpty() && (tail[0] in "aeıioöuü") && tail in chainSet && trBaseWord(lx, stem.dropLast(1) + hard)) return true
        }
        return false
    }

    /** Mended inflected words: the base mended by [correct], the ending mended to the nearest chain that agrees with it. */
    fun trCorrect(typed: String, n: Int): List<Cand> {
        val lx = lexes["tr"] ?: return emptyList()
        if (typed.length < 5 || typed.length > 26) return emptyList()
        val out = ArrayList<Cand>()
        val a = FloatArray(32); val b = FloatArray(32); val c = FloatArray(32)
        for (k in typed.length - 1 downTo maxOf(3, typed.length - 9)) {
            val stemT = typed.substring(0, k); val tailT = typed.substring(k)
            // the nearest ending chains
            var bestTail: String? = null; var bestCost = 1.05f
            for (len in maxOf(0, tailT.length - 1)..minOf(9, tailT.length + 1)) for (tail in chains[len]) {
                val cst = if (tail == tailT) 0f else distance(tailT, tail, bestCost, a, b, c)
                if (cst < bestCost) { bestCost = cst; bestTail = tail }
                if (cst == 0f) break
            }
            val tail = bestTail ?: continue
            val stems = correct("tr", stemT, if (stemT.length >= 5) 1f else .7f, 3)
            for (st in stems) {
                if (!harmony(st.word, tail) || st.word.length < 3) continue
                val cost = st.cost + bestCost
                out += Cand(st.word + tail, st.score - 4f * cost - .6f, cost)
            }
        }
        return out.sortedByDescending { it.score }.distinctBy { it.word }.take(n)
    }

    // ---- what the user's own typos turned out to mean ------------------------------------------------------------------------------------------------

    private val typos = ConcurrentHashMap<String, String>()

    /** The word this exact typo was turned into before (by tapping the suggestion), or null. */
    fun typoFix(c: Context, typed: String): String? { if (!userLoaded) loadUser(c); return typos[typed] }

    fun learnTypo(c: Context, typed: String, word: String) {
        if (typed.length < 3 || typed == word || word.startsWith(typed)) return
        typos[typed] = word
        runCatching { File(c.filesDir, "kbd_typos.txt").writeText(typos.entries.take(4000).joinToString("\n") { "${it.key} ${it.value}" }) }
    }

    /**
     * The words a swipe over the letters [seq] could mean: they start and end on the swiped first and last letter and their letters come in order
     * among the swiped ones (a double letter needs only one pass). Likelier and longer words come first, words that skip many swiped letters last.
     */
    fun glide(code: String, seq: String, n: Int): List<String> {
        if (seq.length < 2) return emptyList()
        val lang = d(code)
        val lx = lexes[lang]
        val out = ArrayList<Cand>()
        fun consider(w: String, base: Float) {
            if (w.length < 2 || w[0] != seq[0] || w.last() != seq.last()) return
            var i = 0
            var prev = '\u0000'
            var len = 0
            for (ch in w) {
                if (ch == prev) continue
                prev = ch; len++
                while (i < seq.length && seq[i] != ch) i++
                if (i >= seq.length) return
                i++
            }
            out += Cand(w, base + .9f * len - .35f * (seq.length - len), 0f)
        }
        if (lx != null) for (k in lx.words.indices) consider(lx.words[k], lx.score[k])
        user.forEach { (w, _) -> consider(w, score(lang, w)) }
        return out.sortedByDescending { it.score }.map { it.word }.distinct().take(n)
    }

    /** What follows [prev] in what the user typed, then the commonest words of the language. */
    fun next(code: String, prev: String?, n: Int): List<String> {
        val lang = d(code)
        val out = ArrayList<String>()
        if (!prev.isNullOrEmpty()) pairs[prev.lowercase(Locale.ROOT)]?.entries?.sortedByDescending { it.value }?.forEach { if (out.size < n) out += it.key }
        val seed = if (lang == "tr") listOf("bir", "bu", "ben", "ve", "ne", "çok") else listOf("I", "the", "you", "it", "to", "a")
        seed.forEach { if (out.size < n && it !in out) out += it }
        return out
    }

    // ---- the typo cost -------------------------------------------------------------------------------------------------------------------------------

    private val foldT = CharArray(512) { computeFold(it.toChar()) }
    private fun foldc(c: Char): Char = if (c.code < 512) foldT[c.code] else computeFold(c)
    private fun computeFold(c: Char): Char = when (c) {
        'ı' -> 'i'; 'ş' -> 's'; 'ç' -> 'c'; 'ğ' -> 'g'; 'ö' -> 'o'; 'ü' -> 'u'; 'İ' -> 'i'
        else -> if (c.code < 128) c else Normalizer.normalize(c.toString(), Normalizer.Form.NFD).firstOrNull() ?: c
    }

    private val grid: Map<Char, Pair<Float, Float>> = HashMap<Char, Pair<Float, Float>>().also { m ->
        listOf("qwertyuıopğü", "asdfghjklşi", "zxcvbnmöç").forEachIndexed { r, row -> row.forEachIndexed { i, ch -> m[ch] = (i + if (r == 1) .25f else if (r == 2) .75f else 0f) to r.toFloat() } }
        m['x'] = m['x'] ?: (2.75f to 2f); m['i'] = m['i'] ?: (10.25f to 1f)
    }

    private val nearIdx = IntArray(512) { -1 }
    private val nearT: Array<BooleanArray> = run {
        val keys = grid.keys.filter { it.code < 512 }
        keys.forEachIndexed { i, ch -> nearIdx[ch.code] = i }
        Array(keys.size) { i -> BooleanArray(keys.size) { j ->
            val p = grid.getValue(keys[i]); val q = grid.getValue(keys[j]); val dx = p.first - q.first; val dy = p.second - q.second
            sqrt(dx * dx + dy * dy) <= 1.45f
        } }
    }

    private fun near(a: Char, b: Char): Boolean {
        if (a.code >= 512 || b.code >= 512) return false
        val i = nearIdx[a.code]; val j = nearIdx[b.code]
        return i >= 0 && j >= 0 && nearT[i][j]
    }

    /** The accent-free form of a letter (ı -> i, ş -> s, é -> e). */
    fun fold(c: Char): Char = when (c) {
        'ı' -> 'i'; 'ş' -> 's'; 'ç' -> 'c'; 'ğ' -> 'g'; 'ö' -> 'o'; 'ü' -> 'u'; 'İ' -> 'i'
        else -> if (c.code < 128) c else Normalizer.normalize(c.toString(), Normalizer.Form.NFD).firstOrNull() ?: c
    }

    private fun sub(a: Char, b: Char): Float {
        if (a == b) return 0f
        val fa = foldc(a); val fb = foldc(b)
        return if (fa == fb) .15f else if (near(a, b) || near(fa, fb)) .5f else 1f
    }

    /** Weighted edit distance (substitution, insertion, deletion, swap of two neighbours); stops early beyond [limit]. */
    private fun distance(a: String, b: String, limit: Float, r0: FloatArray, r1: FloatArray, r2: FloatArray): Float {
        val n = a.length; val m = b.length
        if (n >= 31 || m >= 31) return limit + 1
        var prev2 = r0; var prev = r1; var cur = r2
        for (j in 0..m) prev[j] = j.toFloat()
        for (i in 1..n) {
            cur[0] = i.toFloat()
            var rowMin = cur[0]
            for (j in 1..m) {
                var v = minOf(prev[j - 1] + sub(a[i - 1], b[j - 1]), prev[j] + 1f, cur[j - 1] + 1f)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) v = minOf(v, prev2[j - 2] + .6f)
                cur[j] = v
                if (v < rowMin) rowMin = v
            }
            if (rowMin > limit) return limit + 1
            val t = prev2; prev2 = prev; prev = cur; cur = t
        }
        return prev[m]
    }
}
