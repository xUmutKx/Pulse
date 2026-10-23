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
    fun load(c: Context, lang: String) {
        if (!userLoaded) loadUser(c)
        if (lexes.containsKey(lang)) return
        val ws = ArrayList<String>(); val sc = ArrayList<Float>()
        runCatching {
            c.assets.open("kbd/$lang.txt").bufferedReader().useLines { lines ->
                lines.forEach { line -> val i = line.indexOf(' '); if (i > 0) { ws += line.substring(0, i); sc += ln((line.substring(i + 1).toDoubleOrNull() ?: 1.0) + 1.0).toFloat() } }
            }
        }
        if (ws.isNotEmpty()) lexes[lang] = Lex(ws.toTypedArray(), sc.toFloatArray())
    }

    private fun loadUser(c: Context) {
        userLoaded = true
        runCatching { File(c.filesDir, "kbd_user.txt").forEachLine { l -> val p = l.split(' '); if (p.size == 2) user[p[0]] = p[1].toIntOrNull() ?: 1 } }
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
        user.clear(); pairs.clear(); dirty = 0
        File(c.filesDir, "kbd_user.txt").delete(); File(c.filesDir, "kbd_pairs.txt").delete()
    }

    fun learnedCount() = user.size

    /** A word the user finished: it counts towards the completions, and the pair with the word before it towards the next-word suggestions. */
    fun learn(c: Context, prev: String?, word: String) {
        val w = word.lowercase(Locale.ROOT)
        if (w.length < 2 || w.any { it.isDigit() }) return
        user[w] = (user[w] ?: 0) + 1
        if (!prev.isNullOrEmpty()) { val m = pairs.getOrPut(prev.lowercase(Locale.ROOT)) { ConcurrentHashMap() }; m[w] = (m[w] ?: 0) + 1 }
        if (++dirty >= 25) saveUser(c)
    }

    fun isWord(lang: String, w: String): Boolean {
        val l = w.lowercase(Locale.ROOT)
        return lexes[lang]?.index?.containsKey(l) == true || (user[l] ?: 0) >= 2
    }

    private fun score(lang: String, w: String): Float {
        val lx = lexes[lang]
        val base = lx?.index?.get(w)?.let { lx.score[it] } ?: 0f
        val u = user[w] ?: 0
        return maxOf(base, if (u > 0) 8f + ln(1f + u) * 1.5f else 0f) + if (u > 0) 1f else 0f
    }

    /** Words that start with [prefix] (lowercase), the likeliest first. */
    fun complete(lang: String, prefix: String, n: Int): List<Cand> {
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
    fun correct(lang: String, typed: String, maxCost: Float, n: Int): List<Cand> {
        val lx = lexes[lang] ?: return emptyList()
        val t = typed
        val best = ArrayList<Cand>()
        val d0 = FloatArray(32); val d1 = FloatArray(32); val d2 = FloatArray(32)
        fun consider(w: String, base: Float) {
            if (abs(w.length - t.length) > 2 || w.length > 28) return
            val cost = distance(t, w, maxCost, d0, d1, d2)
            if (cost > maxCost) return
            best += Cand(w, base - 4f * cost, cost)
        }
        for (i in lx.words.indices) consider(lx.words[i], lx.score[i])
        user.forEach { (w, _) -> if (best.none { it.word == w }) consider(w, score(lang, w)) }
        return best.sortedByDescending { it.score }.take(n)
    }

    /**
     * The words a swipe over the letters [seq] could mean: they start and end on the swiped first and last letter and their letters come in order
     * among the swiped ones (a double letter needs only one pass). Likelier and longer words come first, words that skip many swiped letters last.
     */
    fun glide(lang: String, seq: String, n: Int): List<String> {
        if (seq.length < 2) return emptyList()
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
    fun next(lang: String, prev: String?, n: Int): List<String> {
        val out = ArrayList<String>()
        if (!prev.isNullOrEmpty()) pairs[prev.lowercase(Locale.ROOT)]?.entries?.sortedByDescending { it.value }?.forEach { if (out.size < n) out += it.key }
        val seed = if (lang == "tr") listOf("bir", "bu", "ben", "ve", "ne", "çok") else listOf("I", "the", "you", "it", "to", "a")
        seed.forEach { if (out.size < n && it !in out) out += it }
        return out
    }

    // ---- the typo cost -------------------------------------------------------------------------------------------------------------------------------

    private val grid: Map<Char, Pair<Float, Float>> = HashMap<Char, Pair<Float, Float>>().also { m ->
        listOf("qwertyuıopğü", "asdfghjklşi", "zxcvbnmöç").forEachIndexed { r, row -> row.forEachIndexed { i, ch -> m[ch] = (i + if (r == 1) .25f else if (r == 2) .75f else 0f) to r.toFloat() } }
        m['x'] = m['x'] ?: (2.75f to 2f); m['i'] = m['i'] ?: (10.25f to 1f)
    }

    private fun near(a: Char, b: Char): Boolean {
        val p = grid[a] ?: return false; val q = grid[b] ?: return false
        val dx = p.first - q.first; val dy = p.second - q.second
        return sqrt(dx * dx + dy * dy) <= 1.45f
    }

    /** The accent-free form of a letter (ı -> i, ş -> s, é -> e). */
    fun fold(c: Char): Char = when (c) {
        'ı' -> 'i'; 'ş' -> 's'; 'ç' -> 'c'; 'ğ' -> 'g'; 'ö' -> 'o'; 'ü' -> 'u'; 'İ' -> 'i'
        else -> if (c.code < 128) c else Normalizer.normalize(c.toString(), Normalizer.Form.NFD).firstOrNull() ?: c
    }

    private fun sub(a: Char, b: Char): Float = when {
        a == b -> 0f
        fold(a) == fold(b) -> .15f
        near(a, b) || near(fold(a), fold(b)) -> .5f
        else -> 1f
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
