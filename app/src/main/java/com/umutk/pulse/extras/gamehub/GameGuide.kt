package com.umutk.pulse.extras.gamehub

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/*
 * A game's guide for its Details page: what the game is, from Steam's store (the same game's PC page: description, genres, developer, release) or, when
 * Steam does not have it, from the game's Google Play page. Fetched once and kept on the phone; nothing is fetched for a game that was already read.
 */

private val PKG_LIKE = Regex("^[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+$")

/** A name that is really a package id ("com.Hakita.ULTRAKIL"): no label was given. */
internal fun looksLikePackage(name: String) = PKG_LIKE.matches(name)

private fun unhtml(s: String) = s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'").replace("&lt;", "<").replace("&gt;", ">")
    .replace(Regex("&#(\\d+);")) { m -> m.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: "" }

private fun httpText(url: String, max: Int = 2_000_000): String? = runCatching {
    (URL(url).openConnection() as HttpURLConnection).run {
        connectTimeout = 8000; readTimeout = 10000
        setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36")
        try { if (responseCode != 200) return@runCatching null; val b = inputStream.use { it.readBytes() }; if (b.size > max) null else b.toString(Charsets.UTF_8) } finally { disconnect() }
    }
}.getOrNull()

/** The title of the game's Google Play page ("ULTRAKILL"), kept; null when it has no page. */
internal fun fetchPlayTitle(c: Context, pkg: String): String? {
    val p = prefs(c)
    p.getString("pn_$pkg", null)?.takeIf { it.isNotBlank() }?.let { return it }
    if (System.currentTimeMillis() - p.getLong("pnTry_$pkg", 0L) < 7L * 86_400_000L) return null
    p.edit().putLong("pnTry_$pkg", System.currentTimeMillis()).apply()
    val html = httpText("https://play.google.com/store/apps/details?id=$pkg&hl=en&gl=US") ?: return null
    val t = Regex("<title[^>]*>([^<]+?)\\s*-\\s*Apps on Google Play</title>").find(html)?.groupValues?.get(1)?.let { unhtml(it).trim() }?.takeIf { it.isNotBlank() } ?: return null
    p.edit().putString("pn_$pkg", t).apply()
    return t
}

/** The name to show: a real label stays; a package id becomes the store title when it was read, else its last part in words ("ULTRAKIL", "Need For Speed"). */
internal fun prettyName(c: Context, pkg: String, label: String): String {
    if (!looksLikePackage(label)) return label
    prefs(c).getString("pn_$pkg", null)?.takeIf { it.isNotBlank() }?.let { return it }
    val last = label.substringAfterLast('.').replace('_', ' ').replace(Regex("(?<=[a-z0-9])(?=[A-Z])"), " ").trim()
    return last.ifEmpty { label }.replaceFirstChar { it.uppercase() }
}

internal object GameGuide {
    private val busy = ConcurrentHashMap.newKeySet<String>()
    private fun file(c: Context, pkg: String) = File(c.filesDir, "gamehub/guide_" + pkg.replace('.', '_') + ".txt")

    /** The guide kept for the game (empty when none). */
    fun cached(c: Context, pkg: String): String = runCatching { file(c, pkg).takeIf { it.isFile }?.readText().orEmpty() }.getOrDefault("")

    /** Reads the guide from the net in the background (once at a time per game; a game with no page is asked again after a week). */
    fun fetchAsync(c: Context, pkg: String, name: String) {
        val app = c.applicationContext
        if (file(app, pkg).isFile || !busy.add(pkg)) return
        Thread {
            try {
                val p = prefs(app)
                if (System.currentTimeMillis() - p.getLong("guideTry_$pkg", 0L) < 7L * 86_400_000L) return@Thread
                p.edit().putLong("guideTry_$pkg", System.currentTimeMillis()).apply()
                val title = if (looksLikePackage(name)) (fetchPlayTitle(app, pkg) ?: name) else name
                val text = steamGuide(title) ?: playGuide(pkg) ?: return@Thread
                file(app, pkg).apply { parentFile?.mkdirs(); writeText(text) }
            } finally { busy.remove(pkg) }
        }.start()
    }

    private fun steamGuide(name: String): String? = runCatching {
        val id = steamAppId(name) ?: return@runCatching null
        val body = httpText("https://store.steampowered.com/api/appdetails?appids=$id&l=english", 1_500_000) ?: return@runCatching null
        val d = JSONObject(body).optJSONObject(id)?.optJSONObject("data") ?: return@runCatching null
        val desc = unhtml(d.optString("short_description")).trim()
        if (desc.isBlank()) return@runCatching null
        val genres = d.optJSONArray("genres")?.let { a -> (0 until a.length()).map { a.getJSONObject(it).optString("description") } }.orEmpty().filter { it.isNotBlank() }
        val dev = d.optJSONArray("developers")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty().filter { it.isNotBlank() }
        val date = d.optJSONObject("release_date")?.optString("date").orEmpty()
        buildString {
            append(desc)
            if (genres.isNotEmpty()) append("\n\nGenre: ").append(genres.joinToString(", "))
            if (dev.isNotEmpty()) append("\nDeveloper: ").append(dev.joinToString(", "))
            if (date.isNotBlank()) append("\nReleased: ").append(date)
        }
    }.getOrNull()

    private fun playGuide(pkg: String): String? = runCatching {
        val html = httpText("https://play.google.com/store/apps/details?id=$pkg&hl=en&gl=US") ?: return@runCatching null
        val m = Regex("<meta name=\"description\" content=\"([^\"]+)\"").find(html)?.groupValues?.get(1)?.let { unhtml(it).trim() }
        m?.takeIf { it.length > 20 }
    }.getOrNull()
}
