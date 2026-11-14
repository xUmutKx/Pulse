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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import com.umutk.pulse.PChip
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
    class Ep(val id: String, val season: Int, val episode: Int, val title: String, val thumb: String = "", val overview: String = "")
    class Pick(val url: String, val size: Long, val label: String, val over: Boolean = false)

    private const val META = "https://v3-cinemeta.strem.io"
    private fun p(c: Context) = c.getSharedPreferences("season_dl", Context.MODE_PRIVATE)
    fun addon(c: Context) = p(c).getString("addon", "") ?: ""

    /**
     * Any link that belongs to an add-on becomes the address Pulse needs: "stremio://…" is https, and a configure page ("…/configure") or a bare site address is its
     * manifest ("…/manifest.json"). Null when the text is not a link at all.
     */
    fun normalizeAddon(raw: String): String? {
        var s = raw.trim().replaceFirst(Regex("^stremio://", RegexOption.IGNORE_CASE), "https://")
        if (!s.startsWith("http", ignoreCase = true) || s.contains(' ')) return null
        s = s.substringBefore('#').substringBefore('?').trimEnd('/')
        s = when {
            s.endsWith("/manifest.json", true) -> s
            s.endsWith("/configure", true) -> s.removeSuffix("/configure") + "/manifest.json"
            else -> "$s/manifest.json"
        }
        return s
    }
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
            if (s < 0 || e < 0) null else Ep(v.optString("id"), s, e, v.optString("title").ifBlank { v.optString("name") }, v.optString("thumbnail"), v.optString("overview").ifBlank { v.optString("description") })
        }.filter { it.id.isNotBlank() }.sortedWith(compareBy({ it.season }, { it.episode }))
    }

    /** "1.4 GB" or "700 MB" somewhere in a stream's text, in bytes; 0 when there is none. */
    private fun sizeIn(s: String): Long {
        val m = Regex("(\\d+(?:[.,]\\d+)?)\\s*(GB|GiB|MB|MiB)", RegexOption.IGNORE_CASE).find(s) ?: return 0L
        val n = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return 0L
        return (n * if (m.groupValues[2].startsWith("G", true)) 1_073_741_824.0 else 1_048_576.0).toLong()
    }

    /** The id of an episode in a path: the colons stay as they are ("tt0773262:1:2"), as the apps ask for it (an encoded colon was not found by the add-ons). */
    private fun pathId(id: String) = Uri.encode(id, ":")

    /** The address of a stream: a plain link as it is; a torrent as a magnet link (the file number goes along when the stream names one). */
    private fun streamUrl(s: JSONObject): String? {
        s.optString("url").takeIf { it.startsWith("http") }?.let { return it }
        val hash = s.optString("infoHash").takeIf { it.length == 40 } ?: return null
        val idx = if (s.has("fileIdx")) s.optInt("fileIdx", -1) else -1
        val src = s.optJSONArray("sources")
        // the add-ons list "tracker:udp://..." and "dht:<hash>": only the trackers go in the link, without their label
        val tr = (0 until (src?.length() ?: 0)).map { src!!.optString(it) }.filter { it.startsWith("tracker:") }
            .joinToString("&") { "tr=" + Uri.encode(it.removePrefix("tracker:")) }
        // the engine in Pulse when it is there (no other app needed); else the Stremio server on the phone
        return if (TorrentEngine.available) "magnet:?xt=urn:btih:$hash" + (if (tr.isNotEmpty()) "&$tr" else "") + (if (idx >= 0) "&pulse_idx=$idx" else "")
        else "http://127.0.0.1:11470/$hash/${maxOf(idx, 0)}" + if (tr.isNotEmpty()) "?$tr" else ""
    }

    /**
     * The largest stream of the episode that is under [maxBytes]. When there is none under it, the smallest one there is (marked [Pick.over]), so an episode is not left out
     * just because everything is bigger. Null only when the add-on has no stream that says its size.
     */
    suspend fun best(addonBase: String, ep: Ep, maxBytes: Long, kind: String = "series"): Pick? = withContext(Dispatchers.IO) {
        // a "|" in the add-on's settings part of the address is sent encoded
        val base = (normalizeAddon(addonBase) ?: addonBase.trim()).removeSuffix("/manifest.json").removeSuffix("/").replace("|", "%7C")
        val a = get("$base/stream/$kind/${pathId(ep.id)}.json").optJSONArray("streams") ?: return@withContext null
        var best: Pick? = null; var smallest: Pick? = null
        for (i in 0 until a.length()) {
            val s = a.optJSONObject(i) ?: continue
            val text = s.optString("title") + " " + s.optString("name") + " " + s.optJSONObject("behaviorHints")?.optString("filename").orEmpty()
            val size = s.optJSONObject("behaviorHints")?.optLong("videoSize", 0L)?.takeIf { it > 0 } ?: sizeIn(text)
            if (size <= 0) continue
            val url = streamUrl(s) ?: continue
            val p = Pick(url, size, text.trim().lineSequence().firstOrNull().orEmpty())
            if (smallest == null || size < smallest.size) smallest = p
            if (size <= maxBytes && (best == null || size > best.size)) best = p
        }
        best ?: smallest?.let { Pick(it.url, it.size, it.label, over = true) }
    }

    private val posters = android.util.LruCache<String, android.graphics.Bitmap>(40)

    /** A picture of the catalogue, loaded as it is shown; while it is not there (or there is none) the box shows [label], so a missing picture never leaves a hole. */
    @Composable
    private fun Poster(url: String, w: androidx.compose.ui.unit.Dp = 46.dp, h: androidx.compose.ui.unit.Dp = 68.dp, label: String = "") {
        val bmp by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(posters.get(url), url) {
            // a new link starts from its own cached picture (or none): the picture of the link before must not stay (a list that changes kept the old posters)
            value = posters.get(url)
            if (value == null && url.startsWith("http")) value = withContext(Dispatchers.IO) {
                runCatching { URL(url).openStream().use { android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = 2 }) } }.getOrNull()?.also { posters.put(url, it) }
            }
        }
        val b = bmp
        androidx.compose.foundation.layout.Box(Modifier.width(w).height(h).clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp)).background(T.line.copy(alpha = .35f)), contentAlignment = androidx.compose.ui.Alignment.Center) {
            if (b != null) androidx.compose.foundation.Image(b.asImageBitmap(), null, Modifier.width(w).height(h), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            else if (label.isNotEmpty()) Text(label, color = T.sub, fontSize = 13.sp)
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

    private fun sizeText(b: Long) = if (b >= 1_073_741_824L) "%.1f GB".format(b / 1_073_741_824.0) else "${b / 1_048_576} MB"

    private val SIZES = listOf(500, 800, 1500, 3000, 6000)

    @Composable
    private fun Caption(s: String) = Text(s, color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))

    @Composable
    fun Card() {
        // the free-software builds only: the Google Play build has neither this card nor the torrent engine
        if (!com.umutk.pulse.BuildConfig.APK_INSTALL) return
        val ctx = LocalContext.current
        val scope = rememberCoroutineScope()
        var open by remember { mutableStateOf(false) }
        var movies by remember { mutableStateOf(false) }
        var addon by remember { mutableStateOf(addon(ctx)) }
        var editAddon by remember { mutableStateOf(addon(ctx).isBlank()) }
        var mb by remember { mutableStateOf(maxMb(ctx).toInt()) }
        var q by remember { mutableStateOf("") }
        var shows by remember { mutableStateOf<List<Show>>(emptyList()) }
        var show by remember { mutableStateOf<Show?>(null) }
        var eps by remember { mutableStateOf<List<Ep>>(emptyList()) }
        var note by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        var season by remember { mutableStateOf<Int?>(null) }
        // the stream chosen for each episode of the open season (null = looked at, none says its size); found in the background as soon as a season opens
        var found by remember { mutableStateOf<Map<String, Pick?>>(emptyMap()) }
        var picked by remember { mutableStateOf<Set<String>>(emptySet()) }
        fun limit() = mb * 1_048_576L
        fun clean(s: String) = s.replace(Regex("[^\\p{L}\\p{N} ._()-]"), "").trim()
        fun reset() { shows = emptyList(); show = null; eps = emptyList(); note = ""; season = null; picked = emptySet(); found = emptyMap() }
        /** The server first (the Stremio app is opened if it is not running, when the engine in Pulse is not there), then every pick is added. */
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
                // opened without an add-on: the link copied last is taken if it is one (nothing to paste by hand)
                androidx.compose.runtime.LaunchedEffect(open) {
                    if (open && addon.isBlank()) {
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val s = runCatching { cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString().orEmpty() }.getOrDefault("")
                        normalizeAddon(s)?.takeIf { it.contains("manifest.json") && s.trim().length < 500 }?.let { addon = it; save(ctx, it, mb.toFloat()); editAddon = false; note = tr("Add-on taken from what you copied", "Eklenti, kopyaladığından alındı") }
                    }
                }
                if (open) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PChip(!movies, tr("Series", "Diziler")) { movies = false; reset() }
                        PChip(movies, tr("Movies", "Filmler")) { movies = true; reset() }
                    }
                    // 1. where the streams come from
                    Caption(tr("1  Add-on (paste its link once)", "1  Eklenti (bağlantısını bir kez yapıştır)"))
                    if (addon.isNotBlank() && !editAddon) Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text("✓  " + (runCatching { Uri.parse(addon.replaceFirst(Regex("^stremio://", RegexOption.IGNORE_CASE), "https://")).host }.getOrNull() ?: addon), color = T.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        PChip(false, tr("Change", "Değiştir")) { editAddon = true }
                    } else Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        PField(addon, { addon = it }, "https://…/manifest.json", Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        // the link copied from the add-on's page goes in with one press
                        PButton(tr("Paste", "Yapıştır"), compact = true) {
                            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val s = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()?.trim().orEmpty()
                            val n = normalizeAddon(s)
                            if (n != null) { addon = n; save(ctx, n, mb.toFloat()); editAddon = false; note = "" }
                            else note = tr("Copy the add-on link first", "Önce eklenti bağlantısını kopyala")
                        }
                        if (addon.isNotBlank()) { Spacer(Modifier.width(6.dp)); PButton(tr("OK", "Tamam"), compact = true) { val n = normalizeAddon(addon); if (n != null) { addon = n; save(ctx, n, mb.toFloat()); editAddon = false } else note = tr("That is not a link", "Bu bir bağlantı değil") } }
                    }
                    if (addon.isBlank() || editAddon) Text(tr("Open the add-on's page in your browser, copy the link of its page or of its Install button, and come back: Pulse takes it from what you copied (or press Paste).", "Eklentinin sayfasını tarayıcıda aç, sayfanın ya da Yükle düğmesinin bağlantısını kopyala ve buraya dön: Pulse kopyaladığından kendisi alır (ya da Yapıştır'a bas)."), color = T.sub, fontSize = 12.sp)
                    // 2. the size limit: the largest stream still under it is taken
                    Caption(tr("2  Largest size per ${if (movies) "movie" else "episode"}", "2  ${if (movies) "Film" else "Bölüm"} başına en büyük boyut"))
                    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(SIZES) { n -> PChip(mb == n, if (n >= 1000) "${n / 1000f} GB".replace(".0", "") else "$n MB") { mb = n; save(ctx, addon, n.toFloat()); found = emptyMap() } }
                    }
                    // 3. what to download
                    Caption(tr("3  Find the ${if (movies) "movie" else "series"}", "3  ${if (movies) "Filmi" else "Diziyi"} bul"))
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        PField(q, { q = it }, if (movies) tr("Movie name", "Film adı") else tr("Series name", "Dizi adı"), Modifier.weight(1f), onDone = null)
                        Spacer(Modifier.width(8.dp))
                        PButton(tr("Find", "Bul"), compact = true) {
                            if (q.isBlank()) return@PButton
                            reset(); note = tr("Looking…", "Aranıyor…")
                            scope.launch { shows = runCatching { search(q.trim(), if (movies) "movie" else "series") }.getOrDefault(emptyList()); note = if (shows.isEmpty()) tr("Nothing found", "Bulunamadı") else "" }
                        }
                    }
                    val sel = show
                    if (sel == null) {
                        // the results: a tap chooses one, and the list gives way to it (its seasons or its download button come right under it)
                        shows.forEach { s -> key(s.id) {
                            Row(Modifier.fillMaxWidth().clickable {
                                show = s; eps = emptyList(); season = null; picked = emptySet(); found = emptyMap()
                                if (!movies) {
                                    note = tr("Reading the episodes…", "Bölümler okunuyor…")
                                    scope.launch { eps = runCatching { episodes(s) }.getOrDefault(emptyList()); note = if (eps.isEmpty()) tr("No episodes found", "Bölüm bulunamadı") else "" }
                                } else note = ""
                            }.padding(vertical = 4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Poster(s.poster)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(s.name, color = T.text, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    if (s.year.isNotBlank()) Text(s.year, color = T.sub, fontSize = 12.sp)
                                }
                            }
                        } }
                    } else {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Poster(sel.poster)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(sel.name, color = T.accent, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (sel.year.isNotBlank()) Text(sel.year, color = T.sub, fontSize = 12.sp)
                            }
                            PChip(false, tr("Back", "Geri")) { show = null; eps = emptyList(); note = "" }
                        }
                        if (movies) PButton(tr("Download the movie (up to ${mb} MB)", "Filmi indir (en çok ${mb} MB)"), Modifier.fillMaxWidth()) {
                            if (busy) return@PButton
                            if (addon.isBlank()) { note = tr("Put the add-on link first", "Önce eklenti bağlantısını yaz"); return@PButton }
                            busy = true; note = tr("Finding the stream…", "Yayın aranıyor…")
                            scope.launch {
                                val pick = runCatching { best(addon, Ep(sel.id, 0, 0, ""), limit(), "movie") }.getOrNull()
                                if (pick == null) note = tr("Nothing under $mb MB", "$mb MB altında yayın yok")
                                else { start(listOf(pick.url to clean(sel.name + if (sel.year.isNotBlank()) " (" + sel.year.take(4) + ")" else ""))); if (note.startsWith(tr("Finding", "Yayın"))) note = tr("Started (${sizeText(pick.size)})", "Başladı (${sizeText(pick.size)})") }
                                busy = false
                            }
                        } else {
                            val seasons = eps.map { it.season }.filter { it > 0 }.distinct()
                            /** For each episode the largest stream under the size limit is found by itself; what has none under the limit is counted. */
                            fun grab(list: List<Ep>) {
                                if (busy || list.isEmpty()) return
                                if (addon.isBlank()) { note = tr("Put the add-on link first", "Önce eklenti bağlantısını yaz"); return }
                                save(ctx, addon, mb.toFloat()); busy = true
                                scope.launch {
                                    var skipped = 0; var over = 0
                                    val picks = ArrayList<Pair<String, String>>()
                                    val name = clean(sel.name)
                                    list.forEachIndexed { i, ep ->
                                        note = tr("Finding streams ${i + 1}/${list.size}…", "Yayınlar aranıyor ${i + 1}/${list.size}…")
                                        // the stream already found for the list is used; else it is looked for now
                                        val pick = if (found.containsKey(ep.id)) found[ep.id] else runCatching { best(addon, ep, limit()) }.getOrNull()
                                        if (pick == null) skipped++ else { if (pick.over) over++; picks.add(pick.url to "%s S%02dE%02d".format(name, ep.season, ep.episode)) }
                                    }
                                    note = tr("Starting…", "Başlıyor…")
                                    start(picks)
                                    busy = false
                                    if (note == tr("Starting…", "Başlıyor…")) note = tr("${picks.size} started (up to $mb MB each)", "${picks.size} başladı (her biri en çok $mb MB)") +
                                        (if (over > 0) tr(", $over over the limit (the smallest there was)", ", $over sınırın üstünde (bulunan en küçük)") else "") +
                                        (if (skipped > 0) tr(", $skipped without a stream", ", $skipped için yayın yok") else "")
                                }
                            }
                            /** The size of the stream that would be taken is looked up for every episode of the season, three at a time, and shown on its row. */
                            fun lookUp(sn: Int) {
                                val todo = eps.filter { it.season == sn && !found.containsKey(it.id) }
                                if (todo.isEmpty() || addon.isBlank()) return
                                val sem = kotlinx.coroutines.sync.Semaphore(3)
                                scope.launch {
                                    todo.map { ep -> scope.launch { sem.acquire(); try { val p = runCatching { best(addon, ep, limit()) }.getOrNull(); found = found + (ep.id to p) } finally { sem.release() } } }
                                }
                            }
                            // the sizes are looked up as soon as a season is open, and again when the size limit or the add-on changes
                            androidx.compose.runtime.LaunchedEffect(season, mb, addon) { season?.let { lookUp(it) } }
                            if (seasons.isNotEmpty()) {
                                Caption(tr("Seasons", "Sezonlar"))
                                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    items(seasons) { sn ->
                                        val n = eps.count { it.season == sn }
                                        Column(Modifier.clickable { season = sn; picked = eps.filter { it.season == sn }.map { it.id }.toSet() }, horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                                            // Cinemeta has no picture of its own for a season: the series poster stands for it, the number on it when it is not there
                                            Poster(sel.poster, 64.dp, 92.dp, "S$sn")
                                            Text(tr("Season $sn", "Sezon $sn"), color = if (season == sn) T.accent else T.text, fontSize = 13.sp)
                                            Text(tr("$n ep", "$n bölüm"), color = T.sub, fontSize = 11.sp)
                                        }
                                    }
                                }
                                val sn = season
                                if (sn != null) {
                                    val list = eps.filter { it.season == sn }
                                    val chosen = list.filter { it.id in picked }
                                    // the whole season (or the ticked episodes) in one press
                                    PButton(tr("Download season $sn  ·  ${chosen.size} of ${list.size} episodes", "Sezon $sn indir  ·  ${list.size} bölümden ${chosen.size}"), Modifier.fillMaxWidth()) { grab(chosen) }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        PChip(chosen.size == list.size, tr("All", "Hepsi")) { picked = list.map { it.id }.toSet() }
                                        PChip(chosen.isEmpty(), tr("None", "Hiçbiri")) { picked = emptySet() }
                                    }
                                    // the episodes: a tap ticks or unticks one, the arrow downloads just that one
                                    list.forEach { ep -> key(ep.id) {
                                        val on = ep.id in picked
                                        Row(Modifier.fillMaxWidth().clickable { picked = if (on) picked - ep.id else picked + ep.id }.padding(vertical = 6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                            androidx.compose.foundation.layout.Box(Modifier.width(22.dp).height(22.dp), contentAlignment = androidx.compose.ui.Alignment.Center) { if (on) PIcon("check", T.accent, 20.dp) }
                                            Spacer(Modifier.width(8.dp))
                                            // the episode's own picture; without one the series poster, and without that the number
                                            Poster(ep.thumb.ifBlank { sel.poster }, 96.dp, 54.dp, "E%02d".format(ep.episode))
                                            Spacer(Modifier.width(10.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text("E%02d".format(ep.episode) + if (ep.title.isNotBlank()) "  ·  " + ep.title else "", color = if (on) T.text else T.sub, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                if (ep.overview.isNotBlank()) Text(ep.overview, color = T.sub, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                            }
                                            // the size of the stream that would be taken ("…" while it is being looked for, "—" when there is none)
                                            val f = found[ep.id]
                                            Text(if (!found.containsKey(ep.id)) "…" else if (f == null) "—" else (if (f.over) "↑ " else "") + sizeText(f.size), color = if (f?.over == true) androidx.compose.ui.graphics.Color(0xFFE5A04D) else T.sub, fontSize = 12.sp)
                                            androidx.compose.foundation.layout.Box(Modifier.clickable { grab(listOf(ep)) }.padding(6.dp)) { PIcon("download", T.text, 20.dp) }
                                        }
                                    }
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
