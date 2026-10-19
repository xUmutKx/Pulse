package com.umutk.pulse.extras.video

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.LocalWindowLook
import com.umutk.pulse.Look
import com.umutk.pulse.PButton
import com.umutk.pulse.PField
import com.umutk.pulse.WinAlert
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.zip.ZipInputStream

/** One subtitle of a movie: [slug] is the page path after /subtitles/, [score] the site's rating. */
internal data class SubHit(val slug: String, val release: String, val lang: String, val score: Int)

/**
 * yifysubtitles.ch, no key needed. Mostly movies. Search: /ajax/search/?mov=... gives the movie's IMDb id,
 * the movie page lists its subtitles per language, and /subtitle/<slug>.zip holds the .srt.
 */
internal object YifySubs {
    private const val SITE = "https://www.yifysubtitles.ch"
    private const val AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
    private val rowRe = Regex("""<tr data-id="\d+">(.*?)</tr>""", RegexOption.DOT_MATCHES_ALL)
    private val langRe = Regex("""<span class="sub-lang">([^<]*)</span>""")
    private val linkRe = Regex("""href="/subtitles/([^"]+)"""")
    private val releaseRe = Regex("""text-muted">subtitle</span>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
    private val scoreRe = Regex("""<span class="label[^"]*">(\d+)</span>""")
    private val tagRe = Regex("<[^>]+>")

    /** One request; throws on a network error or a non-2xx status. A referer is sent for the archive, which the site checks. */
    private fun http(url: String, referer: String? = null): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10_000; c.readTimeout = 10_000
            c.setRequestProperty("User-Agent", AGENT)
            if (referer != null) c.setRequestProperty("Referer", referer)
            if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
            return c.inputStream.use { it.readBytes() }
        } finally { c.disconnect() }
    }

    /** Subtitle rows of the movie page in [lang] (the site's English language name, e.g. "Turkish"). */
    internal fun parseRows(html: String, lang: String): List<SubHit> = rowRe.findAll(html).mapNotNull { m ->
        val row = m.groupValues[1]
        val name = langRe.find(row)?.groupValues?.get(1) ?: return@mapNotNull null
        if (!name.equals(lang, ignoreCase = true)) return@mapNotNull null
        val slug = linkRe.find(row)?.groupValues?.get(1) ?: return@mapNotNull null
        val release = releaseRe.find(row)?.groupValues?.get(1)?.let { clean(it) }.orEmpty()
        SubHit(slug, release, name, scoreRe.find(row)?.groupValues?.get(1)?.toIntOrNull() ?: 0)
    }.toList()

    private fun clean(html: String): String = tagRe.replace(html, " ")
        .replace("&amp;", "&").replace("&#39;", "'").replace("&quot;", "\"").replace("&lt;", "<").replace("&gt;", ">")
        .replace(Regex("\\s+"), " ").trim()

    suspend fun search(query: String, lang: String): Result<List<SubHit>> = withContext(Dispatchers.IO) {
        runCatching {
            val movies = JSONArray(String(http("$SITE/ajax/search/?mov=${URLEncoder.encode(query, "UTF-8")}"), Charsets.UTF_8))
            if (movies.length() == 0) return@runCatching emptyList<SubHit>()
            val imdb = movies.getJSONObject(0).getString("imdb")
            parseRows(String(http("$SITE/movie-imdb/$imdb"), Charsets.UTF_8), lang)
        }
    }

    /** Fetches the archive of [hit], takes its first .srt and keeps it in the cache. */
    suspend fun download(ctx: Context, hit: SubHit): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val zip = http("$SITE/subtitle/${hit.slug}.zip", referer = "$SITE/subtitles/${hit.slug}")
            var srt: ByteArray? = null
            ZipInputStream(zip.inputStream()).use { z ->
                while (srt == null) {
                    val e = z.nextEntry ?: break
                    if (e.name.endsWith(".srt", ignoreCase = true)) srt = z.readBytes()
                }
            }
            val bytes = srt ?: error("no .srt in archive")
            val out = File(ctx.cacheDir, "ysub_" + hit.slug.filter { it.isLetterOrDigit() || it == '-' } + ".srt")
            out.writeBytes(bytes)
            Uri.fromFile(out)
        }
    }
}

/** The search text from a file name: no folder, extension, dots or release tags (1080p, BluRay, x264, year...). */
internal fun searchTitle(fileName: String): String {
    val base = fileName.substringBeforeLast('.').replace('.', ' ').replace('_', ' ')
    val tags = Regex("\\s[\\[(]?((19|20)\\d{2}|480p|720p|1080p|2160p|4k|bluray|brrip|bdrip|web-?dl|webrip|hdtv|dvdrip|hdrip|x26[45]|h\\.?26[45]|hevc|aac|ac3|dts|remux|repack|proper|yts|yify|rarbg).*$", RegexOption.IGNORE_CASE)
    return base.replace(tags, "").replace(Regex("\\s+"), " ").trim()
}

/** Subtitle language offered first: the app language, as the site names it. */
internal fun appLangName(): String = if (tr("en", "tr") == "tr") "Turkish" else "English"

/** Subtitle search in the look's window: query and language, a result list; a tap downloads it and hands the file to [onPick] with a name. */
@Composable
internal fun OnlineSubsDialog(ctx: Context, fileName: String, onPick: (Uri, String) -> Unit, onDismiss: () -> Unit) {
    val w = LocalWindowLook.current
    val scope = rememberCoroutineScope()
    val none = tr("No subtitles found", "Altyazı bulunamadı")
    val failed = tr("Failed. Check the connection and try again.", "Olmadı. Bağlantıyı kontrol edip yeniden deneyin.")
    var query by remember { mutableStateOf(searchTitle(fileName)) }
    var lang by remember { mutableStateOf(appLangName()) }
    var hits by remember { mutableStateOf<List<SubHit>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var line by remember { mutableStateOf("") }
    var err by remember { mutableStateOf(false) }

    fun search() {
        if (busy || query.isBlank()) return
        busy = true; err = false; hits = emptyList(); line = tr("Searching...", "Aranıyor...")
        scope.launch {
            YifySubs.search(query.trim(), lang)
                .onSuccess { hits = it; line = if (it.isEmpty()) none else "" }
                .onFailure { err = true; line = failed }
            busy = false
        }
    }
    fun pick(h: SubHit) {
        if (busy) return
        busy = true; err = false; line = tr("Downloading...", "İndiriliyor...")
        scope.launch {
            YifySubs.download(ctx, h)
                .onSuccess { onPick(it, h.release.ifBlank { "subtitle" }.replace('/', '_') + ".srt") }
                .onFailure { err = true; line = failed; busy = false }
        }
    }

    WinAlert(
        onDismissRequest = onDismiss,
        title = { Text(tr("Search online", "Çevrimiçi ara"), color = w.text) },
        text = {
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                PField(query, { query = it }, tr("Title", "Ad"), Modifier.fillMaxWidth(), onDone = { search() })
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    listOf("English" to tr("English", "İngilizce"), "Turkish" to tr("Turkish", "Türkçe")).forEach { (name, label) ->
                        Text(
                            label, Modifier.padding(end = 18.dp).clickable { lang = name },
                            color = if (lang == name) Look.accent else w.sub, fontSize = 14.sp,
                            fontWeight = if (lang == name) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
                if (line.isNotEmpty()) Text(line, color = if (err) Color(0xFFC42B1C) else w.sub, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
                hits.forEach { h ->
                    Column(Modifier.fillMaxWidth().clickable { pick(h) }.padding(vertical = 6.dp)) {
                        Text(h.release.ifBlank { "-" }, color = w.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${h.lang} · ${tr("rating", "puan")} ${h.score}", color = w.sub, fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = { PButton(tr("Search", "Ara")) { search() } },
        dismissButton = { PButton(tr("Close", "Kapat"), onClick = onDismiss) },
    )
}
