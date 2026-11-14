package com.umutk.pulse.features

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PButton
import com.umutk.pulse.PCard
import com.umutk.pulse.PField
import com.umutk.pulse.PIcon
import com.umutk.pulse.T
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * A season in one go: the episodes of a show come from Cinemeta (the open catalogue the Stremio apps use), the streams of each episode from an add-on
 * the person names (any add-on that speaks the Stremio add-on protocol), and for each episode the largest stream that is still under the size limit is
 * taken. Pulse brings no add-on and no catalogue of its own; what is downloaded, and whether the person may have it, is their own matter.
 * A torrent stream is fetched through the Stremio app's own server on the phone (it has to be running); a plain link is fetched directly.
 */
object SeasonDl {
    class Show(val id: String, val name: String, val year: String, val poster: String = "")
    class Ep(val id: String, val season: Int, val episode: Int, val title: String)
    class Pick(val url: String, val size: Long, val label: String)

    private const val META = "https://v3-cinemeta.strem.io"
    private fun p(c: Context) = c.getSharedPreferences("season_dl", Context.MODE_PRIVATE)
    fun addon(c: Context) = p(c).getString("addon", "") ?: ""
    fun maxMb(c: Context) = p(c).getFloat("maxMb", 800f)
    fun save(c: Context, addon: String, mb: Float) { p(c).edit().putString("addon", addon.trim()).putFloat("maxMb", mb).apply() }

    private fun get(u: String): JSONObject {
        val c = URL(u).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000; c.readTimeout = 20_000
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) Pulse")
        try { return JSONObject(c.inputStream.bufferedReader().use { it.readText() }) } finally { c.disconnect() }
    }

    /** [kind] is "series" or "movie". The posters are the catalogue's own pictures, loaded as they are shown. */
    suspend fun search(q: String, kind: String): List<Show> = withContext(Dispatchers.IO) {
        val a = get("$META/catalog/$kind/top/search=${Uri.encode(q)}.json").optJSONArray("metas") ?: return@withContext emptyList()
        (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { Show(it.optString("id"), it.optString("name"), it.optString("releaseInfo"), it.optString("poster")) } }.filter { it.id.isNotBlank() }.take(10)
    }

    suspend fun episodes(show: Show): List<Ep> = withContext(Dispatchers.IO) {
        val a = get("$META/meta/series/${Uri.encode(show.id)}.json").optJSONObject("meta")?.optJSONArray("videos") ?: return@withContext emptyList()
        (0 until a.length()).mapNotNull { i ->
            val v = a.optJSONObject(i) ?: return@mapNotNull null
            val s = v.optInt("season", -1); val e = if (v.has("episode")) v.optInt("episode", -1) else v.optInt("number", -1)
            if (s < 0 || e < 0) null else Ep(v.optString("id"), s, e, v.optString("title").ifBlank { v.optString("name") })
        }.filter { it.id.isNotBlank() }.sortedWith(compareBy({ it.season }, { it.episode }))
    }

    /** "1.4 GB" or "700 MB" somewhere in a stream's text, in bytes; 0 when there is none. */
    private fun sizeIn(s: String): Long {
        val m = Regex("(\\d+(?:[.,]\\d+)?)\\s*(GB|GiB|MB|MiB)", RegexOption.IGNORE_CASE).find(s) ?: return 0L
        val n = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return 0L
        return (n * if (m.groupValues[2].startsWith("G", true)) 1_073_741_824.0 else 1_048_576.0).toLong()
    }

    /** The largest stream of the episode that is under [maxBytes], or null when there is none (or none says its size). */
    suspend fun best(addonBase: String, ep: Ep, maxBytes: Long, kind: String = "series"): Pick? = withContext(Dispatchers.IO) {
        val base = addonBase.trim().replaceFirst(Regex("^stremio://", RegexOption.IGNORE_CASE), "https://").removeSuffix("/manifest.json").removeSuffix("/")
        val a = get("$base/stream/$kind/${Uri.encode(ep.id)}.json").optJSONArray("streams") ?: return@withContext null
        var best: Pick? = null
        for (i in 0 until a.length()) {
            val s = a.optJSONObject(i) ?: continue
            val text = s.optString("title") + " " + s.optString("name") + " " + s.optJSONObject("behaviorHints")?.optString("filename").orEmpty()
            val size = s.optJSONObject("behaviorHints")?.optLong("videoSize", 0L)?.takeIf { it > 0 } ?: sizeIn(text)
            if (size <= 0 || size > maxBytes || (best != null && size <= best.size)) continue
            val url = s.optString("url").takeIf { it.startsWith("http") } ?: run {
                val hash = s.optString("infoHash").takeIf { it.length == 40 } ?: return@run null
                val idx = if (s.has("fileIdx")) s.optInt("fileIdx", -1) else -1
                if (idx < 0) return@run null
                val src = s.optJSONArray("sources")
                val tr = (0 until (src?.length() ?: 0)).joinToString("&") { "tr=" + Uri.encode(src!!.optString(it)) }
                "http://127.0.0.1:11470/$hash/$idx" + if (tr.isNotEmpty()) "?$tr" else ""
            } ?: continue
            best = Pick(url, size, text.trim().lineSequence().firstOrNull().orEmpty())
        }
        best
    }

    private val posters = android.util.LruCache<String, android.graphics.Bitmap>(40)

    @Composable
    private fun Poster(url: String) {
        val bmp by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(posters.get(url), url) {
            if (value == null && url.startsWith("http")) value = withContext(Dispatchers.IO) {
                runCatching { URL(url).openStream().use { android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = 2 }) } }.getOrNull()?.also { posters.put(url, it) }
            }
        }
        val b = bmp
        androidx.compose.foundation.layout.Box(Modifier.width(46.dp).height(68.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp)).background(T.line.copy(alpha = .35f))) {
            if (b != null) androidx.compose.foundation.Image(b.asImageBitmap(), null, Modifier.fillMaxWidth().height(68.dp), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        }
    }

    /** The Stremio server on the phone (torrent streams go through it): any answer on its port means it is up. */
    private fun serverUp(): Boolean = runCatching {
        val c = URL("http://127.0.0.1:11470/").openConnection() as HttpURLConnection
        c.connectTimeout = 700; c.readTimeout = 900
        try { c.responseCode; true } finally { c.disconnect() }
    }.getOrDefault(false)

    /** The Stremio (or Nuvio) app installed on the phone, found by its name, so no package name is wired in. */
    private fun streamApp(ctx: Context): android.content.Intent? {
        val pm = ctx.packageManager
        val list = pm.queryIntentActivities(android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_LAUNCHER), 0)
        for (n in listOf("stremio", "nuvio")) list.firstOrNull { it.loadLabel(pm).toString().contains(n, ignoreCase = true) }?.let { return pm.getLaunchIntentForPackage(it.activityInfo.packageName) }
        return null
    }

    /**
     * Makes sure the server is running: if not, the Stremio app is opened (the server lives in it), and Pulse is brought back to the front when it answers.
     * The phone may refuse to bring Pulse back by itself; Back or the recent apps then does it.
     */
    suspend fun ensureServer(ctx: Context): Boolean = withContext(Dispatchers.IO) {
        if (serverUp()) return@withContext true
        val i = streamApp(ctx) ?: return@withContext false
        runCatching { ctx.startActivity(i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
        repeat(25) {
            kotlinx.coroutines.delay(1000)
            if (serverUp()) {
                runCatching { ctx.startActivity(android.content.Intent(ctx, com.umutk.pulse.MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)) }
                return@withContext true
            }
        }
        false
    }

    private fun mb(b: Long) = if (b >= 1_073_741_824L) "%.1f GB".format(b / 1_073_741_824.0) else "${b / 1_048_576} MB"

    @Composable
    fun Card() {
        val ctx = LocalContext.current
        val scope = rememberCoroutineScope()
        var open by remember { mutableStateOf(false) }
        var movies by remember { mutableStateOf(false) }
        var addon by remember { mutableStateOf(addon(ctx)) }
        var mbText by remember { mutableStateOf(maxMb(ctx).toInt().toString()) }
        var q by remember { mutableStateOf("") }
        var shows by remember { mutableStateOf<List<Show>>(emptyList()) }
        var show by remember { mutableStateOf<Show?>(null) }
        var eps by remember { mutableStateOf<List<Ep>>(emptyList()) }
        var note by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        fun limit() = ((mbText.toFloatOrNull() ?: 800f) * 1_048_576.0).toLong()
        fun clean(s: String) = s.replace(Regex("[^\\p{L}\\p{N} ._()-]"), "").trim()
        /** The server first (the Stremio app is opened if it is not running), then every pick is added. */
        suspend fun start(picks: List<Pair<String, String>>) {
            if (picks.any { it.first.contains(":11470/") } && !ensureServer(ctx)) note = tr("The Stremio app is not running and could not be opened", "Stremio uygulaması çalışmıyor ve açılamadı")
            picks.forEach { (u, n) -> Downloads.add(ctx, u, n) }
        }
        PCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth().clickable { open = !open }, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    PIcon("download", T.text, 22.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(tr("Series and movies", "Diziler ve filmler"), color = T.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    PIcon("chevron", T.sub, 18.dp)
                }
                if (open) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PButton(tr("Series", "Diziler"), compact = true) { movies = false; shows = emptyList(); show = null; eps = emptyList(); note = "" }
                        PButton(tr("Movies", "Filmler"), compact = true) { movies = true; shows = emptyList(); show = null; eps = emptyList(); note = "" }
                        Text(if (movies) tr("Movies", "Filmler") else tr("Series", "Diziler"), color = T.accent, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp))
                    }
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        PField(addon, { addon = it }, tr("Add-on link, once (…/manifest.json or stremio://…)", "Eklenti bağlantısı, bir kez (…/manifest.json veya stremio://…)"), Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        // the link copied from the add-on's page goes in with one press
                        PButton(tr("Paste", "Yapıştır"), compact = true) {
                            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val s = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()?.trim().orEmpty()
                            if (s.startsWith("http") || s.startsWith("stremio://")) { addon = s; save(ctx, s, mbText.toFloatOrNull() ?: 800f) }
                            else note = tr("Copy the add-on link first", "Önce eklenti bağlantısını kopyala")
                        }
                    }
                    PField(mbText, { mbText = it.filter { c -> c.isDigit() || c == '.' }.take(6) }, tr("Largest size (MB)", "En büyük boyut (MB)"), Modifier.fillMaxWidth())
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        PField(q, { q = it }, if (movies) tr("Movie name", "Film adı") else tr("Series name", "Dizi adı"), Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        PButton(tr("Find", "Bul"), compact = true) {
                            if (q.isBlank()) return@PButton
                            note = tr("Looking…", "Aranıyor…"); show = null; eps = emptyList()
                            scope.launch { shows = runCatching { search(q.trim(), if (movies) "movie" else "series") }.getOrDefault(emptyList()); note = if (shows.isEmpty()) tr("Nothing found", "Bulunamadı") else "" }
                        }
                    }
                    shows.forEach { s ->
                        Row(Modifier.fillMaxWidth().clickable {
                            show = s; eps = emptyList()
                            if (!movies) {
                                note = tr("Reading the episodes…", "Bölümler okunuyor…")
                                scope.launch { eps = runCatching { episodes(s) }.getOrDefault(emptyList()); note = if (eps.isEmpty()) tr("No episodes found", "Bölüm bulunamadı") else "" }
                            } else note = ""
                        }.padding(vertical = 4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Poster(s.poster)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(s.name, color = if (show?.id == s.id) T.accent else T.text, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (s.year.isNotBlank()) Text(s.year, color = T.sub, fontSize = 12.sp)
                            }
                        }
                    }
                    val sel = show
                    if (sel != null && movies) PButton(tr("Download the movie", "Filmi indir"), Modifier.fillMaxWidth()) {
                        if (busy) return@PButton
                        if (addon.isBlank()) { note = tr("Put the add-on link first", "Önce eklenti bağlantısını yaz"); return@PButton }
                        save(ctx, addon, mbText.toFloatOrNull() ?: 800f); busy = true; note = tr("Finding the stream…", "Yayın aranıyor…")
                        scope.launch {
                            val pick = runCatching { best(addon, Ep(sel.id, 0, 0, ""), limit(), "movie") }.getOrNull()
                            if (pick == null) note = tr("Nothing under ${mb(limit())}", "${mb(limit())} altında yayın yok")
                            else { start(listOf(pick.url to clean(sel.name + if (sel.year.isNotBlank()) " (" + sel.year.take(4) + ")" else ""))); if (note.startsWith(tr("Finding", "Yayın"))) note = tr("Started (${mb(pick.size)})", "Başladı (${mb(pick.size)})") }
                            busy = false
                        }
                    }
                    val seasons = eps.map { it.season }.filter { it > 0 }.distinct()
                    if (seasons.isNotEmpty() && sel != null && !movies) {
                        Text(tr("Season", "Sezon"), color = T.sub, fontSize = 12.sp)
                        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            androidx.compose.foundation.lazy.items(seasons) { sn ->
                                PButton("$sn", compact = true) {
                                    val list = eps.filter { it.season == sn }
                                    if (busy) return@PButton
                                    if (addon.isBlank()) { note = tr("Put the add-on link first", "Önce eklenti bağlantısını yaz"); return@PButton }
                                    save(ctx, addon, mbText.toFloatOrNull() ?: 800f); busy = true
                                    scope.launch {
                                        var skipped = 0
                                        val picks = ArrayList<Pair<String, String>>()
                                        val name = clean(sel.name)
                                        list.forEachIndexed { i, ep ->
                                            note = tr("Finding streams ${i + 1}/${list.size}…", "Yayınlar aranıyor ${i + 1}/${list.size}…")
                                            val pick = runCatching { best(addon, ep, limit()) }.getOrNull()
                                            if (pick == null) skipped++ else picks.add(pick.url to "%s S%02dE%02d".format(name, ep.season, ep.episode))
                                        }
                                        note = tr("Starting…", "Başlıyor…")
                                        start(picks)
                                        busy = false
                                        if (note == tr("Starting…", "Başlıyor…")) note = tr("${picks.size} episodes started (${mb(limit())} at most each)", "${picks.size} bölüm başladı (her biri en çok ${mb(limit())})") +
                                            if (skipped > 0) tr(", $skipped had nothing under the limit", ", $skipped için sınırın altında yayın yok") else ""
                                    }
                                }
                            }
                        }
                    }
                    if (note.isNotEmpty()) Text(note, color = T.sub, fontSize = 12.sp)
                    Spacer(Modifier.height(2.dp))
                }
            }
        }
    }
}
