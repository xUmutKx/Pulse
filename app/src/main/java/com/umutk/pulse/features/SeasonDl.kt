package com.umutk.pulse.features

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.clickable
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
    class Show(val id: String, val name: String, val year: String)
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

    suspend fun search(q: String): List<Show> = withContext(Dispatchers.IO) {
        val a = get("$META/catalog/series/top/search=${Uri.encode(q)}.json").optJSONArray("metas") ?: return@withContext emptyList()
        (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { Show(it.optString("id"), it.optString("name"), it.optString("releaseInfo")) } }.filter { it.id.isNotBlank() }.take(8)
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
    suspend fun best(addonBase: String, ep: Ep, maxBytes: Long): Pick? = withContext(Dispatchers.IO) {
        val base = addonBase.trim().replaceFirst(Regex("^stremio://", RegexOption.IGNORE_CASE), "https://").removeSuffix("/manifest.json").removeSuffix("/")
        val a = get("$base/stream/series/${Uri.encode(ep.id)}.json").optJSONArray("streams") ?: return@withContext null
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

    private fun mb(b: Long) = if (b >= 1_073_741_824L) "%.1f GB".format(b / 1_073_741_824.0) else "${b / 1_048_576} MB"

    @Composable
    fun Card() {
        val ctx = LocalContext.current
        val scope = rememberCoroutineScope()
        var open by remember { mutableStateOf(false) }
        var addon by remember { mutableStateOf(addon(ctx)) }
        var gb by remember { mutableStateOf(maxMb(ctx).toInt().toString()) }
        var q by remember { mutableStateOf("") }
        var shows by remember { mutableStateOf<List<Show>>(emptyList()) }
        var show by remember { mutableStateOf<Show?>(null) }
        var eps by remember { mutableStateOf<List<Ep>>(emptyList()) }
        var note by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        PCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth().clickable { open = !open }, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    PIcon("download", T.text, 22.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(tr("A whole season", "Bir sezonun tamamı"), color = T.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    PIcon("chevron", T.sub, 18.dp)
                }
                if (open) {
                    PField(addon, { addon = it }, tr("Add-on link, once (…/manifest.json or stremio://…)", "Eklenti bağlantısı, bir kez (…/manifest.json veya stremio://…)"), Modifier.fillMaxWidth())
                    PField(gb, { gb = it.filter { c -> c.isDigit() || c == '.' }.take(5) }, tr("Largest size per episode (MB)", "Bölüm başına en büyük boyut (MB)"), Modifier.fillMaxWidth())
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        PField(q, { q = it }, tr("Show name", "Dizi adı"), Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        PButton(tr("Find", "Bul"), compact = true) {
                            if (q.isBlank()) return@PButton
                            note = tr("Looking…", "Aranıyor…"); show = null; eps = emptyList()
                            scope.launch { shows = runCatching { search(q.trim()) }.getOrDefault(emptyList()); note = if (shows.isEmpty()) tr("Nothing found", "Bulunamadı") else "" }
                        }
                    }
                    shows.forEach { s ->
                        Text(s.name + if (s.year.isNotBlank()) "  ·  ${s.year}" else "", color = if (show?.id == s.id) T.accent else T.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().clickable {
                                show = s; note = tr("Reading the episodes…", "Bölümler okunuyor…")
                                scope.launch { eps = runCatching { episodes(s) }.getOrDefault(emptyList()); note = if (eps.isEmpty()) tr("No episodes found", "Bölüm bulunamadı") else "" }
                            }.padding(vertical = 6.dp))
                    }
                    val seasons = eps.map { it.season }.filter { it > 0 }.distinct()
                    if (seasons.isNotEmpty() && show != null) {
                        Text(tr("Season", "Sezon"), color = T.sub, fontSize = 12.sp)
                        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            androidx.compose.foundation.lazy.items(seasons) { sn ->
                                PButton("$sn", compact = true) {
                                    val list = eps.filter { it.season == sn }
                                    if (busy) return@PButton
                                    val limit = ((gb.toFloatOrNull() ?: 800f) * 1_048_576.0).toLong()
                                    save(ctx, addon, gb.toFloatOrNull() ?: 800f)
                                    if (addon.isBlank()) { note = tr("Put the add-on link first", "Önce eklenti bağlantısını yaz"); return@PButton }
                                    busy = true
                                    scope.launch {
                                        var added = 0; var skipped = 0
                                        val name = show!!.name.replace(Regex("[^\\p{L}\\p{N} ._-]"), "")
                                        list.forEachIndexed { i, ep ->
                                            note = tr("Finding streams ${i + 1}/${list.size}…", "Yayınlar aranıyor ${i + 1}/${list.size}…")
                                            val pick = runCatching { best(addon, ep, limit) }.getOrNull()
                                            if (pick == null) skipped++ else { Downloads.add(ctx, pick.url, "%s S%02dE%02d".format(name, ep.season, ep.episode)); added++ }
                                        }
                                        busy = false
                                        note = tr("$added episodes started (${mb(limit)} at most each)", "$added bölüm başladı (her biri en çok ${mb(limit)})") +
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
