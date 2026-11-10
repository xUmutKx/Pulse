package com.umutk.pulse.extras.gamehub

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/*
 * Real art for the games that exist on Steam (Need for Speed, Asphalt and the like have PC versions): Steam's own library cover (600x900), its hero
 * background and its logo, found by the game's name and kept on the phone. A game Steam does not have (a name that is not the same) keeps the store
 * picture from Google Play; a cover the person chose always comes first.
 */

internal fun steamCoverFile(c: Context, pkg: String) = File(c.filesDir, "gamehub/steam_" + pkg.replace('.', '_') + ".jpg")
internal fun steamHeroFile(c: Context, pkg: String) = File(c.filesDir, "gamehub/hero_" + pkg.replace('.', '_') + ".jpg")
internal fun steamLogoFile(c: Context, pkg: String) = File(c.filesDir, "gamehub/logo_" + pkg.replace('.', '_') + ".png")

private fun normName(s: String) = s.lowercase().replace(Regex("[\\u2122\\u00AE\\u00A9]"), " ").replace(Regex("\\b(mobile|android|the|for)\\b"), " ")
    .replace(Regex("[^a-z0-9]+"), " ").trim()

private fun httpBytes(url: String, max: Int = 3 * 1024 * 1024): ByteArray? = runCatching {
    (URL(url).openConnection() as HttpURLConnection).run {
        connectTimeout = 8000; readTimeout = 12000
        setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36")
        try {
            if (responseCode != 200) return@runCatching null
            val b = inputStream.use { it.readBytes() }
            if (b.size < 2000 || b.size > max) null else b
        } finally { disconnect() }
    }
}.getOrNull()

private val STOP = setOf("and", "of", "in", "on", "a", "an", "edition", "game", "free", "online", "hd", "remastered", "definitive", "deluxe", "pro")
private fun tokens(s: String) = normName(s).split(' ').filter { it.isNotEmpty() && it !in STOP }.toSet()

/** The part of a name before a subtitle ("Asphalt 9: Legends" -> "Asphalt 9"). */
private fun mainTitle(name: String) = name.substringBefore(':').substringBefore(" - ").trim()

private fun storeSearch(term: String): org.json.JSONArray? {
    val q = URLEncoder.encode(term.replace(Regex("[\u2122\u00AE\u00A9]"), "").trim(), "UTF-8")
    val body = httpBytes("https://store.steampowered.com/api/storesearch/?term=$q&l=english&cc=us", 600_000)?.toString(Charsets.UTF_8) ?: return null
    return JSONObject(body).optJSONArray("items")
}

/** The Steam app that is this game: the same words (case and marks aside), or one whose words nearly all agree (a subtitle more or less); null when none. */
internal fun steamAppId(name: String): String? = runCatching {
    val want = normName(name); val wt = tokens(name)
    for (term in listOf(name, mainTitle(name)).distinct()) {
        val items = storeSearch(term) ?: continue
        var best: String? = null; var bestScore = 0.0
        for (i in 0 until items.length()) {
            val o = items.getJSONObject(i)
            val n = o.optString("name")
            if (normName(n) == want) return@runCatching o.optInt("id").toString()
            val ot = tokens(n)
            if (wt.isEmpty() || ot.isEmpty()) continue
            // the words the two names share, over all the words of either (Jaccard); a single-word name must match exactly (the loop above)
            val j = wt.intersect(ot).size.toDouble() / wt.union(ot).size
            if (wt.size >= 2 && j > bestScore) { bestScore = j; best = o.optInt("id").toString() }
        }
        if (best != null && bestScore >= 0.6) return@runCatching best
    }
    null
}.getOrNull()

/** Steam's cover, hero and logo for the game. True when the cover was found (a miss is remembered for a week, so it is not asked again at every start). */
internal fun fetchSteamArt(c: Context, g: GameRow): Boolean {
    val p = prefs(c)
    val missed = p.getLong("steamMiss_${g.pkg}", 0L)
    if (missed > 0 && System.currentTimeMillis() - missed < 7L * 86_400_000L) return false
    fun miss(): Boolean { p.edit().putLong("steamMiss_${g.pkg}", System.currentTimeMillis()).apply(); return false }
    val id = steamAppId(g.name) ?: return miss()
    val base = "https://cdn.cloudflare.steamstatic.com/steam/apps/$id/"
    val cover = httpBytes(base + "library_600x900_2x.jpg") ?: httpBytes(base + "library_600x900.jpg") ?: return miss()
    steamCoverFile(c, g.pkg).apply { parentFile?.mkdirs(); writeBytes(cover) }
    httpBytes(base + "library_hero.jpg")?.let { steamHeroFile(c, g.pkg).writeBytes(it) }
    httpBytes(base + "logo.png", 1_000_000)?.let { steamLogoFile(c, g.pkg).writeBytes(it) }
    return true
}

/** The best picture there is for the game: Steam's art first, then the store picture of Google Play. */
/** SteamGridDB (the cover source Boxshelf itself uses besides Steam): the person's own free key, set in Game Hub's settings; the best 600x900 picture for the game. */
internal fun sgdbKey(c: Context): String = prefs(c).getString("sgdbKey", "").orEmpty().trim()

private fun sgdbGet(url: String, key: String): ByteArray? = runCatching {
    (URL(url).openConnection() as HttpURLConnection).run {
        connectTimeout = 8000; readTimeout = 12000
        setRequestProperty("Authorization", "Bearer $key"); setRequestProperty("User-Agent", "Pulse")
        try { if (responseCode != 200) return@runCatching null; inputStream.use { it.readBytes() } } finally { disconnect() }
    }
}.getOrNull()

internal fun fetchSgdbCover(c: Context, g: GameRow): Boolean = runCatching {
    val key = sgdbKey(c); if (key.isEmpty()) return@runCatching false
    val q = URLEncoder.encode(g.name, "UTF-8").replace("+", "%20")
    val id = JSONObject(sgdbGet("https://www.steamgriddb.com/api/v2/search/autocomplete/$q", key)?.toString(Charsets.UTF_8) ?: return@runCatching false)
        .optJSONArray("data")?.optJSONObject(0)?.optInt("id", 0)?.takeIf { it > 0 } ?: return@runCatching false
    val url = JSONObject(sgdbGet("https://www.steamgriddb.com/api/v2/grids/game/$id?dimensions=600x900&types=static&nsfw=false", key)?.toString(Charsets.UTF_8) ?: return@runCatching false)
        .optJSONArray("data")?.optJSONObject(0)?.optString("url").orEmpty().takeIf { it.startsWith("https://") } ?: return@runCatching false
    val bytes = httpBytes(url) ?: return@runCatching false
    steamCoverFile(c, g.pkg).apply { parentFile?.mkdirs(); writeBytes(bytes) }
    true
}.getOrDefault(false)

internal fun fetchBestCover(c: Context, g: GameRow): Boolean {
    // a game that only has its package id for a name ("com.Hakita.ULTRAKIL") is looked up by the title of its store page
    val named = if (looksLikePackage(g.name)) GameRow(g.pkg, fetchPlayTitle(c, g.pkg) ?: g.name) else g
    if (fetchSteamArt(c, named) || fetchSgdbCover(c, named)) return true
    // the name as the store has it: "MiSide Android Beta" is "MiSide" (platform, edition and version words are dropped)
    val short = named.name.replace(Regex("(?i)\\b(android|beta|demo|early access|edition|version|port|remastered|alpha)\\b"), " ").replace(Regex("\\s+"), " ").trim()
    if (short.isNotEmpty() && short != named.name) {
        val alt = GameRow(g.pkg, short)
        if (fetchSteamArt(c, alt) || fetchSgdbCover(c, alt)) return true
    }
    return !playCoverFile(c, g.pkg).isFile && fetchPlayCover(c, g.pkg)
}
