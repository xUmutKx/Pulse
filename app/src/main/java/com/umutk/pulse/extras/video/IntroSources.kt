package com.umutk.pulse.extras.video

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** The skippable parts of an episode: the opening and the "previously on" recap, in milliseconds. */
internal data class Segs(val intro: LongRange?, val recap: LongRange?) {
    val empty: Boolean get() = intro == null && recap == null
}

/** Where the intro of an episode comes from, in order: IntroDB, then TheIntroDB (both community timestamps, no key), then the picture match of [IntroFinder]. */
internal object IntroSources {
    private const val INTRODB = "https://api.introdb.app/segments"
    private const val OS_API = "https://api.opensubtitles.com/api/v1"
    private const val THEINTRODB = "https://api.theintrodb.org/v2/media"

    private val seRe = Regex("""(?i)(?<![a-z0-9])s(\d{1,2})[ ._-]?e(\d{1,3})(?![0-9])""")
    private val xRe = Regex("""(?i)(?<![a-z0-9])(\d{1,2})x(\d{2,3})(?![0-9])""")

    /**
     * The show, the season and the episode of a file name like "Dexter.S03E10.1080p.5.1Ch.BluRay-Group.mkv": the show is only what stands BEFORE the
     * season and episode mark, with the dots, the underscores, a trailing year and a trailing "(US)" tag cleaned off ("Dexter", not the whole rest of the name).
     */
    fun parseEpisode(file: String): Triple<String, Int?, Int?> {
        val m = seRe.find(file) ?: xRe.find(file) ?: return Triple(file.substringBeforeLast('.').replace('.', ' ').replace('_', ' ').trim(), null, null)
        val raw = file.substring(0, m.range.first)
        val title = raw.replace('.', ' ').replace('_', ' ').replace(Regex("[\\[\\](){}]"), " ")
            .replace(Regex("\\s+(19|20)\\d{2}\\s*$"), "").replace(Regex("[\\s-]+$"), "").replace(Regex("\\s+"), " ").trim()
        return Triple(title, m.groupValues[1].toInt(), m.groupValues[2].toInt())
    }

    /** TheIntroDB's intro and recap for one episode (the first range of each); empty when it has neither or the request fails. */
    suspend fun theIntroDb(imdb: String, season: Int, episode: Int): Segs = withContext(Dispatchers.IO) {
        runCatching {
            val c = URL("$THEINTRODB?imdb_id=$imdb&season=$season&episode=$episode").openConnection() as HttpURLConnection
            c.connectTimeout = 10_000; c.readTimeout = 10_000
            c.setRequestProperty("Accept", "application/json")
            val text = try { if (c.responseCode !in 200..299) return@runCatching Segs(null, null); c.inputStream.use { String(it.readBytes(), Charsets.UTF_8) } } finally { c.disconnect() }
            val root = JSONObject(text)
            Segs(firstRange(root.optJSONArray("intro")), firstRange(root.optJSONArray("recap")))
        }.getOrElse { Segs(null, null) }
    }

    private fun firstRange(a: org.json.JSONArray?): LongRange? {
        if (a == null || a.length() == 0) return null
        val o = a.optJSONObject(0) ?: return null
        val s = o.optLong("start_ms", -1L); val e = o.optLong("end_ms", -1L)
        return if (s < 0 || e <= s) null else s until e
    }

    /** IntroDB's intro and recap for one episode; empty when it has neither or the request fails. */
    suspend fun introDb(imdb: String, season: Int, episode: Int): Segs = withContext(Dispatchers.IO) {
        runCatching {
            val c = URL("$INTRODB?imdb_id=$imdb&season=$season&episode=$episode").openConnection() as HttpURLConnection
            c.connectTimeout = 10_000; c.readTimeout = 10_000
            c.setRequestProperty("Accept", "application/json")
            val text = try { if (c.responseCode !in 200..299) return@runCatching Segs(null, null); c.inputStream.use { String(it.readBytes(), Charsets.UTF_8) } } finally { c.disconnect() }
            val root = JSONObject(text)
            Segs(range(root.optJSONObject("intro")), range(root.optJSONObject("recap")))
        }.getOrElse { Segs(null, null) }
    }

    private fun range(o: JSONObject?): LongRange? {
        if (o == null) return null
        val a = o.optLong("start_ms", -1L); val b = o.optLong("end_ms", -1L)
        return if (a < 0 || b <= a) null else a until b
    }

    /** The IMDb id of a show by its title, from TVmaze (free, no key); null when it is not found. */
    private fun tvMazeImdb(title: String): String? = runCatching {
        if (title.isBlank()) return@runCatching null
        val c = URL("https://api.tvmaze.com/singlesearch/shows?q=" + URLEncoder.encode(title, "UTF-8")).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000; c.readTimeout = 10_000
        c.setRequestProperty("Accept", "application/json")
        val text = try { if (c.responseCode !in 200..299) return@runCatching null; c.inputStream.use { String(it.readBytes(), Charsets.UTF_8) } } finally { c.disconnect() }
        JSONObject(text).optJSONObject("externals")?.optString("imdb", "")?.takeIf { it.startsWith("tt") }
    }.getOrNull()

    /** The IMDb id of the show for a file name like "Dexter.S03E07...mkv", found through the OpenSubtitles key the user typed in. */
    suspend fun showImdb(ctx: Context, fileTitle: String): String? = withContext(Dispatchers.IO) {
        val key = OpenSubs.key(ctx)
        // no OpenSubtitles key: the show's IMDb id from TVmaze, which needs no key, by the show's title
        if (key.isBlank()) {
            val t = parseEpisode(fileTitle).first
            // a country tag ("The Office US") is tried again without it
            return@withContext tvMazeImdb(t) ?: tvMazeImdb(t.replace(Regex("\\s+[A-Za-z]{2}$"), "")).takeIf { t.contains(' ') }
        }
        runCatching {
            val (title, season, episode) = parseEpisode(fileTitle)
            val q = StringBuilder("$OS_API/subtitles?query=${URLEncoder.encode(title, "UTF-8")}")
            if (season != null) q.append("&season_number=$season")
            if (episode != null) q.append("&episode_number=$episode")
            val c = URL(q.toString()).openConnection() as HttpURLConnection
            c.connectTimeout = 15_000; c.readTimeout = 20_000
            c.setRequestProperty("User-Agent", "Pulse v1.0")
            c.setRequestProperty("Accept", "application/json")
            c.setRequestProperty("Api-Key", key)
            val text = try { if (c.responseCode !in 200..299) return@runCatching null; c.inputStream.use { String(it.readBytes(), Charsets.UTF_8) } } finally { c.disconnect() }
            val data = JSONObject(text).optJSONArray("data") ?: return@runCatching null
            // the episode's own feature holds the series id as parent_imdb_id; the series id is what IntroDB keys on
            for (i in 0 until data.length()) {
                val fd = data.getJSONObject(i).optJSONObject("attributes")?.optJSONObject("feature_details") ?: continue
                val parent = fd.optString("parent_imdb_id", "")
                val digits = parent.filter { it.isDigit() }
                if (digits.isNotEmpty()) return@runCatching "tt" + digits.padStart(7, '0')
            }
            null
        }.getOrNull()
    }

    /** Intro of [file] (a file name): from IntroDB when the show and episode are known, else null. */
    suspend fun forFile(ctx: Context, file: String): Segs? {
        val (_, season, episode) = parseEpisode(file)
        if (season == null || episode == null) return null
        val imdb = showImdb(ctx, file) ?: return null
        // IntroDB first; TheIntroDB for the episodes IntroDB does not have
        return introDb(imdb, season, episode).takeIf { !it.empty } ?: theIntroDb(imdb, season, episode).takeIf { !it.empty }
    }
}
