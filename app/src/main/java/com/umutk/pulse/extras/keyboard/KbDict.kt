package com.umutk.pulse.extras.keyboard

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Dictionary and Wikipedia for a word (as in WM Keyboard): the meanings from Wiktionary, then the summary of the Wikipedia page. Both are open projects of the Wikimedia
 * Foundation and need no key; only the word looked up is sent. Blocking: call off the main thread.
 */
object KbDict {
    private fun get(u: String): String? = runCatching {
        val c = URL(u).openConnection() as HttpURLConnection
        c.connectTimeout = 6000; c.readTimeout = 8000
        c.setRequestProperty("User-Agent", "PulseKeyboard/1.0")
        c.setRequestProperty("Accept", "application/json")
        if (c.responseCode !in 200..299) null else c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }.also { c.disconnect() }
    }.getOrNull()

    private fun plain(html: String) = html.replace(Regex("<[^>]*>"), "").replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">").replace(Regex("\\s+"), " ").trim()

    /** The meanings of [term] (a few lines each kind of word), or "". */
    private fun wiktionary(term: String, langName: String): String {
        val raw = get("https://en.wiktionary.org/api/rest_v1/page/definition/" + URLEncoder.encode(term, "UTF-8").replace("+", "%20")) ?: return ""
        val o = runCatching { JSONObject(raw) }.getOrNull() ?: return ""
        val out = StringBuilder()
        // the keyboard's language first, then English
        for (code in listOf(if (langName == "tr") "tr" else "en", "en", "tr").distinct()) {
            val arr = o.optJSONArray(code) ?: continue
            for (i in 0 until arr.length()) {
                val e = arr.getJSONObject(i)
                val defs = e.optJSONArray("definitions") ?: continue
                out.append(e.optString("partOfSpeech")).append(" (").append(e.optString("language")).append(")\n")
                var n = 0
                for (j in 0 until defs.length()) {
                    val d = plain(defs.getJSONObject(j).optString("definition"))
                    if (d.isNotBlank() && n < 4) { out.append("  ${++n}. ").append(d).append('\n') }
                }
            }
            if (out.isNotEmpty()) break
        }
        return out.toString().trim()
    }

    private fun wikipedia(term: String, lang: String): String {
        val t = URLEncoder.encode(term.replace(' ', '_'), "UTF-8")
        for (l in listOf(lang, "en", "tr").distinct()) {
            val raw = get("https://$l.wikipedia.org/api/rest_v1/page/summary/$t") ?: continue
            val o = runCatching { JSONObject(raw) }.getOrNull() ?: continue
            val ex = o.optString("extract")
            if (ex.isNotBlank() && o.optString("type") != "disambiguation") return "Wikipedia (${l.uppercase()})\n$ex"
        }
        return ""
    }

    /** What to show for [term]: the meanings, then the encyclopedia summary; "" when neither knows it. */
    fun lookup(term: String, lang: String): String {
        val t = term.trim()
        if (t.length < 2) return ""
        val parts = listOf(wiktionary(t.lowercase(), lang).ifEmpty { wiktionary(t, lang) }, wikipedia(t.replaceFirstChar { it.uppercase() }, lang)).filter { it.isNotBlank() }
        return parts.joinToString("\n\n")
    }
}
