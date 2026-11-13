package com.umutk.pulse.extras.ebook

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/**
 * The real name of a book whose title is a file name ("ilide.info-ahmet-umit-e ytan-ayrntda-gizlidir-pr_ab12.epub"): the name is cleaned into words, and the book is looked up
 * (Open Library, no key) by pieces of it, since a file name has often lost its accented letters ("e ytan" for "Şeytan"). The best match of the lot is taken when it fits the words.
 */
internal object BookNames {
    private val FOLD = mapOf('ç' to 'c', 'ğ' to 'g', 'ı' to 'i', 'ö' to 'o', 'ş' to 's', 'ü' to 'u', 'â' to 'a', 'î' to 'i', 'û' to 'u', 'é' to 'e', 'è' to 'e', 'á' to 'a', 'à' to 'a', 'í' to 'i', 'ó' to 'o', 'ú' to 'u', 'ñ' to 'n')
    private val NOISE = setOf("pdf", "epub", "mobi", "txt", "indir", "download", "ebook", "ebooks", "kitap", "ilide", "info", "com", "net", "org", "pr", "www", "free", "full", "copy", "kopya")

    fun fold(s: String): String = buildString { s.lowercase(Locale("tr")).forEach { append(FOLD[it] ?: it) } }

    /** True for a title that is a file name rather than a name: no spaces, a site in front, ids and many dashes. */
    fun looksLikeFile(t: String): Boolean {
        val s = t.trim()
        if (s.isEmpty()) return false
        if (Regex("\\.(epub|pdf|txt|mobi)$", RegexOption.IGNORE_CASE).containsMatchIn(s)) return true
        if (Regex("^[A-Za-z0-9]+(\\.[A-Za-z0-9]+)*\\.(info|com|net|org|ru|io|biz)[-_ ]", RegexOption.IGNORE_CASE).containsMatchIn(s)) return true
        if (s.count { it == '-' || it == '_' } >= 3) return true
        return !s.contains(' ') && s.length > 24
    }

    /** The words of a file name: no site, no extension, no ids, dashes and dots turned into spaces. */
    fun clean(t: String): String {
        var s = t.trim().replace(Regex("\\.(epub|pdf|txt|mobi)$", RegexOption.IGNORE_CASE), "")
        s = s.replace(Regex("^[A-Za-z0-9]+(\\.[A-Za-z0-9]+)*\\.(info|com|net|org|ru|io|biz)[-_ ]+", RegexOption.IGNORE_CASE), "")
        val words = s.split(Regex("[-_.\\s]+")).filter { it.isNotBlank() && it.lowercase() !in NOISE && !it.any { ch -> ch.isDigit() } }
        return words.joinToString(" ")
    }

    /** "ahmet umit" → "Ahmet Umit", in Turkish letter case. */
    fun titleCase(s: String): String = s.split(' ').filter { it.isNotEmpty() }.joinToString(" ") { w -> w.substring(0, 1).uppercase(Locale("tr")) + w.substring(1).lowercase(Locale("tr")) }

    // a damaged word of the file name (letters lost) against a real word: the same letters in order, few missing, or one slip
    private fun fits(c: String, t: String): Boolean {
        if (c == t) return true
        if (c.length < 3) return false
        var i = 0
        for (ch in t) if (i < c.length && ch == c[i]) i++
        if (i == c.length && t.length - c.length <= 3) return true
        return lev(c, t) <= (if (t.length >= 6) 2 else 1)
    }

    private fun lev(a: String, b: String): Int {
        val d = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) d[i][0] = i
        for (j in 0..b.length) d[0][j] = j
        for (i in 1..a.length) for (j in 1..b.length) d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
        return d[a.length][b.length]
    }

    private class Doc(val title: String, val author: String)

    private fun get(url: String): String? = runCatching {
        (URL(url).openConnection() as HttpURLConnection).run {
            connectTimeout = 8000; readTimeout = 10000
            setRequestProperty("User-Agent", "Pulse/1.0 (book names)")
            try { if (responseCode != 200) null else inputStream.bufferedReader().use { it.readText() } } finally { disconnect() }
        }
    }.getOrNull()

    private fun search(q: String): List<Doc> {
        val body = get("https://openlibrary.org/search.json?limit=12&fields=title,author_name&q=" + URLEncoder.encode(q, "UTF-8")) ?: return emptyList()
        return runCatching {
            val a = JSONObject(body).getJSONArray("docs")
            List(a.length()) { i -> val o = a.getJSONObject(i); Doc(o.optString("title"), o.optJSONArray("author_name")?.optString(0).orEmpty()) }
        }.getOrDefault(emptyList())
    }

    /** The title and author found for a cleaned file name, or null when nothing fits. Blocking: run it off the main thread. */
    fun lookup(cleaned: String): Pair<String, String>? {
        val tokens = fold(cleaned).split(' ').filter { it.isNotEmpty() }
        if (tokens.size < 2) return null
        // the whole name first; then pairs of neighbouring words (an author's name is usually one of them), then single long words
        val queries = LinkedHashSet<String>()
        queries += tokens.joinToString(" ")
        for (i in 0 until tokens.size - 1) if (tokens[i].length >= 3 && tokens[i + 1].length >= 3) queries += tokens[i] + " " + tokens[i + 1]
        tokens.filter { it.length >= 6 }.forEach { queries += it }
        val pool = LinkedHashMap<String, Doc>()
        var n = 0
        for (q in queries) {
            if (n++ >= 7) break
            search(q).forEach { pool.putIfAbsent(fold(it.title) + "|" + fold(it.author), it) }
            if (pool.size >= 40) break
        }
        var best: Doc? = null; var bestScore = 0f
        for (d in pool.values) {
            val tt = fold(d.title).split(Regex("[^a-z0-9]+")).filter { it.length >= 2 }
            if (tt.isEmpty()) continue
            val hit = tt.count { w -> tokens.any { c -> fits(c, w) } }
            val at = fold(d.author).split(Regex("[^a-z0-9]+")).filter { it.length >= 2 }
            val ahit = at.count { w -> tokens.any { c -> fits(c, w) } }
            // the title's words must be in the name; the author's words count a little
            var score = hit.toFloat() / tt.size + (if (at.isNotEmpty()) .2f * ahit / at.size else 0f) + (if (d.title.any { it.code > 127 }) .05f else 0f)
            // a title that is longer than the name (a series, a subtitle) is a weaker answer
            if (tt.size > tokens.size + 1) score -= .3f
            if (score > bestScore) { bestScore = score; best = d }
        }
        val b = best ?: return null
        val matched = fold(b.title).split(Regex("[^a-z0-9]+")).filter { it.length >= 2 }
        if (bestScore < .85f || matched.size < 2) return null
        return b.title.trim() to b.author.trim()
    }

    /** The name to show: the looked-up title when found, else the cleaned file name in capitals of each word. */
    fun resolve(title: String, author: String): Pair<String, String> {
        val cl = clean(title)
        if (cl.isBlank()) return title to author
        val found = runCatching { lookup(cl) }.getOrNull()
        return if (found != null) found.first.let { t -> if (t == fold(t)) titleCase(t) else t } to (author.ifBlank { found.second }) else titleCase(cl) to author
    }
}
