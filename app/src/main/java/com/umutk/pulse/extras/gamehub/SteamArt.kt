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

/** The Steam app whose name is the game's name (same words, case and marks aside), or null. */
internal fun steamAppId(name: String): String? = runCatching {
    val q = URLEncoder.encode(name.replace(Regex("[\\u2122\\u00AE\\u00A9]"), "").trim(), "UTF-8")
    val body = httpBytes("https://store.steampowered.com/api/storesearch/?term=$q&l=english&cc=us", 600_000)?.toString(Charsets.UTF_8) ?: return@runCatching null
    val items = JSONObject(body).optJSONArray("items") ?: return@runCatching null
    val want = normName(name)
    for (i in 0 until items.length()) {
        val o = items.getJSONObject(i)
        if (normName(o.optString("name")) == want) return@runCatching o.optInt("id").toString()
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
internal fun fetchBestCover(c: Context, g: GameRow): Boolean = fetchSteamArt(c, g) || (!playCoverFile(c, g.pkg).isFile && fetchPlayCover(c, g.pkg))
