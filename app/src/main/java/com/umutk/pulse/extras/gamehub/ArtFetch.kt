package com.umutk.pulse.extras.gamehub

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URLEncoder

/*
 * The pictures the shelf shows behind a game and on the back of its box, fetched on their own when the shelf opens: the wide Steam picture (the background),
 * the logo, and four screenshots. Steam's pictures come first; a RAWG key (Game Hub settings, the person's own free key) fills the screenshots that Steam
 * does not have and gives the game's rating. A game is looked at again after three days when something is still missing.
 */
internal object ArtFetch {
    @Volatile var running = false
        private set

    fun rawgKey(c: Context): String = prefs(c).getString("rawgKey", "").orEmpty().trim()

    /** How many of the four screenshots the game has. */
    fun shots(c: Context, pkg: String): Int = (0 until 4).count { steamShotFile(c, pkg, it).isFile }

    /** Whether something the shelf shows is still missing for the game. */
    private fun missing(c: Context, pkg: String): Boolean =
        !(coverFile(c, pkg).isFile || steamCoverFile(c, pkg).isFile || playCoverFile(c, pkg).isFile) || !steamHeroFile(c, pkg).isFile || !steamLogoFile(c, pkg).isFile || shots(c, pkg) < 4 || rating(c, pkg).isEmpty()

    /** The rating shown on the back of the box ("RAWG 4.3 / 5", or Steam's score), or empty. */
    fun rating(c: Context, pkg: String): String = prefs(c).getString("rating_$pkg", "").orEmpty()

    /** Starts the search for every game that still lacks a picture, one after the other, in the background. Does nothing while it runs. */
    fun start(c: Context, games: List<GameRow>) {
        if (running) return
        val app = c.applicationContext
        running = true
        Thread {
            try {
                // the work is for the background: it starts a while after the shelf is open and has the lowest priority, so moving the shelf is never made to wait for it
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
                Thread.sleep(8000)
                var any = false
                for (g in games) {
                    if (MegaGames.isMega(g.pkg) || HtmlGames.isHtml(g.pkg) || !missing(app, g.pkg)) continue
                    if (fetch(app, g)) any = true
                    Thread.sleep(500)
                }
                // the shelf is made again once, at the end (it used to be made again after every game, so the page reloaded every few seconds)
                if (any) ShelfSync.bump()
            } catch (_: Throwable) {
            } finally { running = false }
        }.apply { isDaemon = true; name = "art-fetch" }.start()
    }

    private fun bytes(url: String, max: Int = 3 * 1024 * 1024): ByteArray? = runCatching {
        (java.net.URL(url).openConnection() as java.net.HttpURLConnection).run {
            connectTimeout = 8000; readTimeout = 12000
            setRequestProperty("User-Agent", "Pulse/1.0")
            try { if (responseCode != 200) return@runCatching null; inputStream.use { i -> i.readNBytes(max + 1).takeIf { it.size <= max } } } finally { disconnect() }
        }
    }.getOrNull()

    /** The screenshots and the rating from the game's Google Play page (an Android package only). */
    private fun playExtras(c: Context, g: GameRow): Boolean {
        if (!g.pkg.contains('.')) return false
        val html = bytes("https://play.google.com/store/apps/details?id=${g.pkg}&hl=en&gl=US", 3_000_000)?.toString(Charsets.UTF_8) ?: return false
        var got = false
        if (rating(c, g.pkg).isEmpty()) Regex("Rated ([0-9.]+) stars").find(html)?.groupValues?.get(1)?.toDoubleOrNull()?.takeIf { it > 0 }?.let { prefs(c).edit().putString("rating_${g.pkg}", "Google Play ${"%.1f".format(it)} / 5").apply(); got = true }
        if (shots(c, g.pkg) < 4) {
            // the first picture is the icon, the next the feature graphic; the screenshots follow
            val ids = Regex("https://play-lh\\.googleusercontent\\.com/([A-Za-z0-9_\\-]+)=w\\d+-h\\d+").findAll(html).map { it.groupValues[1] }.distinct().toList().drop(2)
            var k = shots(c, g.pkg)
            for (id in ids) {
                if (k >= 4) break
                val raw = bytes("https://play-lh.googleusercontent.com/$id=w480", 3_000_000) ?: continue
                if (raw.size < 6000) continue
                steamShotFile(c, g.pkg, k).apply { parentFile?.mkdirs() }.writeBytes(raw); k++; got = true
            }
        }
        return got
    }

    /** One game: what is missing is looked for; true when something was saved. */
    fun fetch(c: Context, g: GameRow): Boolean {
        val p = prefs(c)
        val tried = "artTry_${g.pkg}"
        if (System.currentTimeMillis() - p.getLong(tried, 0L) < 3L * 86_400_000L) return false
        p.edit().putLong(tried, System.currentTimeMillis()).apply()
        var got = false
        // a game without any cover gets one now (Steam, SteamGridDB, Google Play), as "Look for covers" would
        if (!(coverFile(c, g.pkg).isFile || steamCoverFile(c, g.pkg).isFile || playCoverFile(c, g.pkg).isFile)) runCatching { if (fetchBestCover(c, g)) got = true }
        // Steam: the id is found once and kept
        val idKey = "steamId_${g.pkg}"
        val id = p.getString(idKey, null)?.takeIf { it.isNotEmpty() } ?: steamAppId(g.name)?.also { p.edit().putString(idKey, it).apply() }
        if (id != null) {
            val base = "https://cdn.cloudflare.steamstatic.com/steam/apps/$id/"
            if (!steamHeroFile(c, g.pkg).isFile) bytes(base + "library_hero.jpg")?.let { steamHeroFile(c, g.pkg).apply { parentFile?.mkdirs() }.writeBytes(it); got = true }
            if (!steamLogoFile(c, g.pkg).isFile) bytes(base + "logo.png", 1_000_000)?.let { steamLogoFile(c, g.pkg).apply { parentFile?.mkdirs() }.writeBytes(it); got = true }
            if (shots(c, g.pkg) < 4) runCatching { fetchSteamShots(c, g.pkg, id) }.onSuccess { if (shots(c, g.pkg) > 0) got = true }
            if (rating(c, g.pkg).isEmpty()) runCatching {
                val d = bytes("https://store.steampowered.com/api/appdetails?appids=$id&filters=metacritic", 300_000)?.toString(Charsets.UTF_8)
                val mc = d?.let { JSONObject(it).optJSONObject(id)?.optJSONObject("data")?.optJSONObject("metacritic")?.optInt("score", 0) } ?: 0
                if (mc > 0) p.edit().putString("rating_${g.pkg}", "Metacritic $mc / 100").apply()
            }
        }
        // Google Play: the store page of an Android game has its screenshots and its rating
        if (shots(c, g.pkg) < 4 || rating(c, g.pkg).isEmpty()) runCatching { got = playExtras(c, g) || got }
        // RAWG: the rating and the screenshots Steam does not have (and a background when Steam has none)
        val key = rawgKey(c)
        if (key.isNotEmpty() && (shots(c, g.pkg) < 4 || rating(c, g.pkg).isEmpty() || !steamHeroFile(c, g.pkg).isFile)) runCatching {
            val q = URLEncoder.encode(g.name, "UTF-8")
            val list = JSONObject(bytes("https://api.rawg.io/api/games?search=$q&page_size=1&key=$key", 300_000)?.toString(Charsets.UTF_8) ?: return@runCatching)
                .optJSONArray("results") ?: return@runCatching
            val hit = list.optJSONObject(0) ?: return@runCatching
            val rid = hit.optInt("id", 0).takeIf { it > 0 } ?: return@runCatching
            val rating = hit.optDouble("rating", 0.0)
            if (rating > 0 && rating(c, g.pkg).isEmpty()) p.edit().putString("rating_${g.pkg}", "RAWG ${"%.1f".format(rating)} / 5").apply()
            if (!steamHeroFile(c, g.pkg).isFile) hit.optString("background_image").takeIf { it.startsWith("https://") }?.let { u ->
                bytes(u)?.let { steamHeroFile(c, g.pkg).apply { parentFile?.mkdirs() }.writeBytes(it); got = true }
            }
            if (shots(c, g.pkg) < 4) {
                val shots = JSONObject(bytes("https://api.rawg.io/api/games/$rid/screenshots?page_size=8&key=$key", 300_000)?.toString(Charsets.UTF_8) ?: return@runCatching)
                    .optJSONArray("results") ?: return@runCatching
                var k = shots(c, g.pkg)
                for (i in 0 until shots.length()) {
                    if (k >= 4) break
                    val u = shots.getJSONObject(i).optString("image").takeIf { it.startsWith("https://") } ?: continue
                    val raw = bytes(u, 6 * 1024 * 1024) ?: continue
                    // smaller copies: the back of the box shows them at about 200 px
                    val bmp = BitmapFactory.decodeByteArray(raw, 0, raw.size) ?: continue
                    val scale = minOf(1f, 480f / bmp.width)
                    val small = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
                    val out = ByteArrayOutputStream(); small.compress(Bitmap.CompressFormat.JPEG, 86, out)
                    steamShotFile(c, g.pkg, k).apply { parentFile?.mkdirs() }.writeBytes(out.toByteArray())
                    if (small !== bmp) small.recycle(); bmp.recycle()
                    k++; got = true
                }
            }
        }
        return got
    }
}
