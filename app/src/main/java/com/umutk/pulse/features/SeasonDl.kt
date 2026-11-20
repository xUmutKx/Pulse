package com.umutk.pulse.features

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
    class Show(val id: String, val name: String, val year: String, val poster: String = "", val kind: String = "series")
    class Ep(val id: String, val season: Int, val episode: Int, val title: String, val thumb: String = "", val overview: String = "")
    /** [seeds] is the number of people sharing it when the add-on says it ("👤 12"), -1 when it does not. */
    class Pick(val url: String, val size: Long, val label: String, val over: Boolean = false, val seeds: Int = -1)

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
    /** The add-ons in the saved text (several can be kept, one after the other; each is a link the person gave), as the addresses Pulse asks. */
    fun addons(text: String): List<String> = text.split(Regex("[\\s,]+")).mapNotNull { normalizeAddon(it) }.distinct()

    /** What an add-on says about itself (its manifest): its name and whether it has streams at all. */
    class AddonInfo(val name: String, val streams: Boolean)

    /** Reads the add-on's manifest, so a link that is wrong, down, or not an add-on is told right when it is added (and not as an empty list later). */
    suspend fun check(manifestUrl: String): AddonInfo = withContext(Dispatchers.IO) {
        val j = get(manifestUrl.replace("|", "%7C"))
        val r = j.optJSONArray("resources")
        var streams = false
        if (r != null) for (i in 0 until r.length()) {
            val x = r.opt(i)
            if (x == "stream" || (x is JSONObject && x.optString("name") == "stream")) streams = true
        }
        if (r == null && !j.has("name")) throw java.io.IOException(tr("this is not an add-on's manifest", "bu bir eklenti bildirimi değil"))
        AddonInfo(j.optString("name"), streams)
    }

    fun maxMb(c: Context) = p(c).getFloat("maxMb", 800f)
    fun save(c: Context, addon: String, mb: Float) { p(c).edit().putString("addon", addon.trim()).putFloat("maxMb", mb).apply() }

    /** The add-on's server has a certificate this phone does not trust (a self-made one, or an incomplete chain). Only the person can say to trust it. */
    class CertProblem(val host: String, val sha: String) : java.io.IOException(
        tr("This server's certificate is not trusted by the phone: ", "Bu sunucunun sertifikasına telefon güvenmiyor: ") + host)

    private fun pins(c: Context) = p(c).getStringSet("certPins", emptySet()) ?: emptySet()

    /** The person chose to trust this server's certificate: it is remembered by its SHA-256 and only that exact certificate is accepted for that host. */
    fun trust(c: Context, cp: CertProblem) { p(c).edit().putStringSet("certPins", pins(c) + "${cp.host}|${cp.sha}").apply() }

    private fun sha256(b: ByteArray) = java.security.MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    /** The normal check, and in addition a certificate the person approved for this host. */
    private fun pinnedFactory(host: String, c: Context): javax.net.ssl.SSLSocketFactory? {
        val ok = pins(c).filter { it.startsWith("$host|") }.map { it.substringAfter('|') }.toSet()
        if (ok.isEmpty()) return null
        val def = javax.net.ssl.TrustManagerFactory.getInstance(javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm()).apply { init(null as java.security.KeyStore?) }
            .trustManagers.filterIsInstance<javax.net.ssl.X509TrustManager>().first()
        val tm = object : javax.net.ssl.X509TrustManager {
            override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, type: String) = def.checkClientTrusted(chain, type)
            override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, type: String) {
                try { def.checkServerTrusted(chain, type) } catch (e: java.security.cert.CertificateException) {
                    if (chain.isEmpty() || sha256(chain[0].encoded) !in ok) throw e
                }
            }
            override fun getAcceptedIssuers() = def.acceptedIssuers
        }
        return javax.net.ssl.SSLContext.getInstance("TLS").apply { init(null, arrayOf(tm), null) }.socketFactory
    }

    /** The certificate the server shows (only its fingerprint is taken: the handshake is made and closed, nothing is sent), so the person can be asked about it. */
    private fun probe(host: String, port: Int): String? = runCatching {
        var found: String? = null
        val tm = object : javax.net.ssl.X509TrustManager {
            override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, type: String) {}
            override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, type: String) { if (chain.isNotEmpty()) found = sha256(chain[0].encoded) }
            override fun getAcceptedIssuers() = emptyArray<java.security.cert.X509Certificate>()
        }
        val f = javax.net.ssl.SSLContext.getInstance("TLS").apply { init(null, arrayOf(tm), null) }.socketFactory
        (f.createSocket(host, port) as javax.net.ssl.SSLSocket).use { it.soTimeout = 8000; it.startHandshake() }
        found
    }.getOrNull()

    private fun get(u: String): JSONObject {
        val body = fetch(u)
        if (!body.trimStart().startsWith("{")) throw java.io.IOException(tr("not a list of streams (a web page)", "yayın listesi değil (bir web sayfası)"))
        return JSONObject(body)
    }

    /** A list of add-ons from a link the person gives (the Stremio add-on list format: entries with a "transportUrl" and a "manifest", or just links). Nothing is built in. */
    class ListedAddon(val name: String, val about: String, val url: String)

    fun catUrl(c: Context) = p(c).getString("catUrl", "") ?: ""
    fun saveCatUrl(c: Context, u: String) { p(c).edit().putString("catUrl", u.trim()).apply() }

    suspend fun addonList(listUrl: String): List<ListedAddon> = withContext(Dispatchers.IO) {
        val body = fetch(listUrl.trim()).trim()
        val arr = when {
            body.startsWith("[") -> org.json.JSONArray(body)
            body.startsWith("{") -> JSONObject(body).let { it.optJSONArray("addons") ?: it.optJSONArray("items") ?: org.json.JSONArray() }
            else -> throw java.io.IOException(tr("not a list of add-ons (a web page)", "eklenti listesi değil (bir web sayfası)"))
        }
        (0 until arr.length()).mapNotNull { i ->
            val x = arr.opt(i)
            if (x is String) normalizeAddon(x)?.let { ListedAddon(runCatching { Uri.parse(it).host }.getOrNull() ?: it, "", it) }
            else (x as? JSONObject)?.let { o ->
                val m = o.optJSONObject("manifest")
                val u = normalizeAddon(o.optString("transportUrl").ifBlank { o.optString("manifestUrl") }) ?: return@let null
                ListedAddon((m?.optString("name") ?: o.optString("name")).ifBlank { runCatching { Uri.parse(u).host }.getOrNull() ?: u }, (m?.optString("description") ?: o.optString("description")).take(140), u)
            }
        }.distinctBy { it.url }.take(300)
    }

    private fun fetch(u: String): String {
        val c = URL(u).openConnection() as HttpURLConnection
        if (c is javax.net.ssl.HttpsURLConnection) Downloads.appContext()?.let { ctx -> pinnedFactory(c.url.host, ctx)?.let { c.sslSocketFactory = it } }
        c.connectTimeout = 10_000; c.readTimeout = 20_000
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36")
        c.setRequestProperty("Accept", "application/json")
        try {
            val code = try { c.responseCode } catch (e: javax.net.ssl.SSLHandshakeException) {
                // an untrusted certificate: the person is asked whether to trust this server's one (never done silently)
                val untrusted = generateSequence<Throwable>(e) { it.cause }.any { it is java.security.cert.CertificateException }
                val host = c.url.host
                if (untrusted) probe(host, c.url.port.takeIf { it > 0 } ?: 443)?.let { throw CertProblem(host, it) }
                throw e
            }
            // a refusal (403 of a protection page, 404, a server error) is said as such: it was taken for "no stream" before, and nothing told why
            if (code !in 200..299) throw java.io.IOException("HTTP $code")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally { c.disconnect() }
    }

    /** The person's own TMDB key (their free account, Download manager settings): the season pictures come from there. */
    fun tmdbKey(c: Context) = p(c).getString("tmdbKey", "") ?: ""
    fun saveTmdbKey(c: Context, k: String) { p(c).edit().putString("tmdbKey", k.trim()).apply() }

    private val seasonPics = HashMap<String, Map<Int, String>>()

    /** Season number to picture of a show (its IMDb id), from TMDB with the person's key; empty when there is no key or no answer. */
    suspend fun seasonPosters(imdbId: String, key: String): Map<Int, String> = withContext(Dispatchers.IO) {
        if (key.isBlank() || !imdbId.startsWith("tt")) return@withContext emptyMap()
        val cacheKey = "$imdbId|$key"
        seasonPics[cacheKey]?.let { return@withContext it }
        val api = "https://api.themoviedb.org/3"
        val id = JSONObject(fetch("$api/find/${Uri.encode(imdbId)}?external_source=imdb_id&api_key=${Uri.encode(key)}")).optJSONArray("tv_results")?.optJSONObject(0)?.optInt("id", 0) ?: 0
        val out = HashMap<Int, String>()
        if (id != 0) {
            val seasons = JSONObject(fetch("$api/tv/$id?api_key=${Uri.encode(key)}")).optJSONArray("seasons") ?: org.json.JSONArray()
            for (i in 0 until seasons.length()) {
                val x = seasons.optJSONObject(i) ?: continue
                val path = x.optString("poster_path")
                if (path.startsWith("/")) out[x.optInt("season_number", -1)] = "https://image.tmdb.org/t/p/w342$path"
            }
        }
        out.also { seasonPics[cacheKey] = it }
    }

    /** The Download manager's own settings: the key for the season pictures. */
    @Composable
    fun Settings() {
        val ctx = LocalContext.current
        var key by remember { mutableStateOf(tmdbKey(ctx)) }
        var note by remember { mutableStateOf("") }
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // a torrent nobody shares gives way to another stream of the episode after a while (45 to 75 seconds without its file list, 20 to 25 with it)
            Row(Modifier.fillMaxWidth().clickable { Downloads.setAutoSwitch(ctx, !Downloads.autoSwitch) }.padding(vertical = 6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("Try another stream when nobody shares", "Kimse paylaşmıyorsa başka yayını dene"), color = T.text, fontSize = 15.sp)
                    Text(tr("When a torrent shows no one to download from for a while (about a minute, longer right after the engine starts), the next stream of the episode starts: from a quarter to nearly twice the size you typed, the bigger ones first, never under 200 MB", "Bir torrent bir süre (yaklaşık bir dakika, motor yeni açıldıysa daha uzun) kimseden indirilemezse bölümün sıradaki yayını başlar: yazdığın boyutun dörtte biri ile neredeyse iki katı arası, büyükler önce, 200 MB altına asla inmeden"), color = T.sub, fontSize = 12.sp)
                }
                com.umutk.pulse.PSwitch(Downloads.autoSwitch, { Downloads.setAutoSwitch(ctx, it) })
            }
            // the network of the torrents is chosen by itself: a Wi-Fi that gives no peers (or asks for a sign-in page) sends them over mobile data
            Row(Modifier.fillMaxWidth().clickable { Downloads.setTorrentAuto(ctx, !Downloads.torrentAuto) }.padding(vertical = 6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("Choose the network for torrents by itself", "Torrentlerin ağını kendisi seçsin"), color = T.text, fontSize = 15.sp)
                    Text(tr("Wi-Fi first; when it gives no peers for a minute, mobile data", "Önce Wi-Fi; bir dakika kimse bulamazsa mobil veri"), color = T.sub, fontSize = 12.sp)
                }
                com.umutk.pulse.PSwitch(Downloads.torrentAuto, { Downloads.setTorrentAuto(ctx, it) })
            }
            // a Wi-Fi that blocks torrents (school, hotel, sign-in page): they run over mobile data while the rest of the phone stays on the Wi-Fi
            Row(Modifier.fillMaxWidth().clickable { Downloads.setTorrentCell(ctx, !Downloads.torrentCell) }.padding(vertical = 6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("Torrents over mobile data on Wi-Fi", "Wi-Fi'deyken torrentler mobil veriden"), color = T.text, fontSize = 15.sp)
                    Text(tr("For a Wi-Fi that blocks torrents or asks for a sign-in page", "Torrentleri engelleyen ya da giriş sayfası isteyen Wi-Fi için"), color = T.sub, fontSize = 12.sp)
                }
                com.umutk.pulse.PSwitch(Downloads.torrentCell, { Downloads.setTorrentCell(ctx, it) })
            }
            // without Stremio the torrents of Series and movies are downloaded by the engine in Pulse: it is in the full build only, on a 64-bit phone
            if (!TorrentEngine.available && Downloads.localServer.isBlank()) Text(tr("Torrents need the full Pulse build (not Lite or the Play one) on a 64-bit phone, or a local torrent server below.", "Torrent için tam Pulse sürümü (Lite ya da Play değil) ve 64 bit telefon gerekir, ya da aşağıdaki yerel torrent sunucusu."), color = androidx.compose.ui.graphics.Color(0xFFE08A3A), fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp))
            // a magnet link (and a Stremio link, which is one) starts as a torrent only when this is on: off by default
            Row(Modifier.fillMaxWidth().clickable { Downloads.setMagnetLinks(ctx, !Downloads.magnetLinks) }.padding(vertical = 6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("Magnet links as torrents", "Magnet bağlantıları torrent olarak"), color = T.text, fontSize = 15.sp)
                    Text(tr("A magnet link or a Stremio link is downloaded as a torrent when this is on. Off: they are not started.", "Açıkken magnet bağlantısı ya da Stremio bağlantısı torrent olarak indirilir. Kapalıyken başlatılmaz."), color = T.sub, fontSize = 12.sp)
                }
                com.umutk.pulse.PSwitch(Downloads.magnetLinks, { Downloads.setMagnetLinks(ctx, it) })
            }
            // an optional local torrent server (Stremio's, or another app's such as Nuvio): empty = Pulse's own torrent engine
            run {
                var addr by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(Downloads.localServer) }
                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text(tr("Local torrent server (optional)", "Yerel torrent sunucusu (isteğe bağlı)"), color = T.text, fontSize = 15.sp)
                    Text(tr("Stremio's is http://127.0.0.1:11470. Empty: Pulse's own torrent engine downloads the stream.", "Stremio'nunki http://127.0.0.1:11470. Boşken Pulse'un kendi torrent motoru indirir."), color = T.sub, fontSize = 12.sp)
                    PField(addr, { addr = it; Downloads.setLocalServer(ctx, it) }, "http://127.0.0.1:11470", Modifier.fillMaxWidth().padding(top = 6.dp), onDone = null)
                }
            }
            // removing a finished download from the list leaves its file on the phone (off: the file goes too)
            Row(Modifier.fillMaxWidth().clickable { Downloads.setKeepFiles(ctx, !Downloads.keepFiles) }.padding(vertical = 6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("Keep the file when removed", "Silince dosya kalsın"), color = T.text, fontSize = 15.sp)
                    Text(tr("A finished download leaves only the list; the file stays on the phone", "Biten indirme yalnızca listeden çıkar, dosya telefonda kalır"), color = T.sub, fontSize = 12.sp)
                }
                com.umutk.pulse.PSwitch(Downloads.keepFiles, { Downloads.setKeepFiles(ctx, it) })
            }
            Spacer(Modifier.height(8.dp))
            Text(tr("Season pictures", "Sezon resimleri"), color = T.text, fontSize = 15.sp)
            Text(tr("Each season's picture comes from themoviedb.org. Make a free account there, copy your API key (v3) and paste it here. Without a key each season shows the series picture.",
                "Her sezonun resmi themoviedb.org'dan gelir. Orada ücretsiz bir hesap aç, API anahtarını (v3) kopyala ve buraya yapıştır. Anahtar olmazsa her sezon dizinin resmini gösterir."), color = T.sub, fontSize = 12.sp)
            PField(key, { key = it }, tr("API key", "API anahtarı"), Modifier.fillMaxWidth())
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                PButton(tr("Save", "Kaydet"), compact = true) { saveTmdbKey(ctx, key); note = tr("Saved", "Kaydedildi") }
                if (note.isNotEmpty()) Text("  " + note, color = T.sub, fontSize = 12.sp)
            }
        }
    }

    /** [kind] is "series" or "movie". The posters are the catalogue's own pictures, loaded as they are shown. */
    suspend fun search(q: String, kind: String): List<Show> = withContext(Dispatchers.IO) {
        val a = get("$META/catalog/$kind/top/search=${Uri.encode(q)}.json").optJSONArray("metas") ?: return@withContext emptyList()
        (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { Show(it.optString("id"), it.optString("name"), it.optString("releaseInfo"), it.optString("poster"), kind) } }.filter { it.id.isNotBlank() }.take(10)
    }

    private val popularCache = HashMap<String, List<Show>>()

    /** What is popular now in the catalogue (Cinemeta's "top" list of series or of movies), for the shelves of the page; kept for the run. */
    suspend fun popular(kind: String): List<Show> = withContext(Dispatchers.IO) {
        popularCache[kind]?.let { return@withContext it }
        val a = get("$META/catalog/$kind/top.json").optJSONArray("metas") ?: return@withContext emptyList()
        (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { Show(it.optString("id"), it.optString("name"), it.optString("releaseInfo"), it.optString("poster"), kind) } }
            .filter { it.id.isNotBlank() }.take(24).also { popularCache[kind] = it }
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

    /** Plays a stream straight away in Pulse's video player (no download, nothing is kept): plain links and a local torrent server's links. A magnet link has to be downloaded first. */
    fun watch(ctx: Context, url: String): Boolean {
        if (!url.startsWith("http")) return false
        return runCatching {
            val i = android.content.Intent(ctx, com.umutk.pulse.ExtraToolActivity::class.java).setAction(android.content.Intent.ACTION_VIEW).setDataAndType(Uri.parse(url), "video/*").putExtra("route", "video")
            i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
        }.isSuccess
    }

    /**
     * Plays the best of [all] at once: a plain link (or the local server's) straight in the player; a torrent through the engine's own small server on the phone, which hands the
     * player the file as its pieces arrive (the first pieces are fetched before it opens). [onStatus] tells the progress. Returns what to show ("" when the player opened).
     */
    suspend fun playBest(ctx: Context, all: List<Pick>, limit: Long, onStatus: (String) -> Unit): String {
        if (all.isEmpty()) return tr("No stream found", "Yayın bulunamadı")
        val direct = all.filter { it.url.startsWith("http") }.sortedByDescending { maxOf(it.seeds, 0) }.firstOrNull()
        if (direct != null && watch(ctx, direct.url)) return ""
        val torrents = all.filter { it.url.startsWith("magnet:") }
        if (torrents.isEmpty()) return tr("Could not open the stream", "Yayın açılamadı")
        if (!TorrentEngine.available) return tr("This phone cannot run the torrent engine (64-bit only): set a local torrent server, or use an add-on with direct links", "Bu telefon torrent motorunu çalıştıramıyor (yalnız 64 bit): yerel torrent sunucusu ya da doğrudan bağlantı veren bir eklenti kullan")
        // the best shared ones first, then the ones near the size wanted; if one does not start, the next is tried
        val order = (torrents.sortedByDescending { it.seeds } .take(3) + ranked(torrents, limit).take(2)).distinctBy { it.url }
        var last = ""
        for (t in order) {
            try {
                val u = TorrentEngine.stream(ctx, t.url, onStatus)
                if (u != null && watch(ctx, u)) return ""
                if (u == null) return tr("Watching torrents is not in this build: download it instead, or set a local torrent server", "Torrent'i indirmeden izleme bu sürümde yok: indir ya da yerel torrent sunucusu kullan")
            } catch (e: java.io.IOException) { last = e.message.orEmpty() }
        }
        return tr("The torrent did not start: ", "Torrent başlamadı: ") + last
    }

    /** The address of a stream: a plain link as it is; a torrent as a magnet link (the file number goes along when the stream names one). */
    private fun streamUrl(s: JSONObject): String? {
        s.optString("url").takeIf { it.startsWith("http") }?.let { return it }
        val hash = s.optString("infoHash").takeIf { it.length == 40 } ?: return null
        val idx = if (s.has("fileIdx")) s.optInt("fileIdx", -1) else -1
        val src = s.optJSONArray("sources")
        // the add-ons list "tracker:udp://..." and "dht:<hash>": only the trackers go in the link, without their label
        val tr = (0 until (src?.length() ?: 0)).map { src!!.optString(it) }.filter { it.startsWith("tracker:") }
            .joinToString("&") { "tr=" + Uri.encode(it.removePrefix("tracker:")) }
        // a local torrent server (Stremio, or another app that serves torrents the same way) when its address is set; else the torrent engine in Pulse (full build, 64-bit)
        val server = Downloads.localServer.trim().trimEnd('/')
        if (server.isNotEmpty()) return "$server/$hash/${maxOf(idx, 0)}" + if (tr.isNotEmpty()) "?$tr" else ""
        if (!TorrentEngine.available) return null
        return "magnet:?xt=urn:btih:$hash" + (if (tr.isNotEmpty()) "&$tr" else "") + (if (idx >= 0) "&pulse_idx=$idx" else "")
    }

    /**
     * The largest stream of the episode that is under [maxBytes]. When there is none under it, the smallest one there is (marked [Pick.over]), so an episode is not left out
     * just because everything is bigger. Null only when the add-on has no stream that says its size.
     */
    /** Every stream the add-on has for the episode (or the movie), each with its size when the add-on says it (0 when not) and its first line of text. */
    suspend fun streams(addonBase: String, ep: Ep, kind: String = "series"): List<Pick> = withContext(Dispatchers.IO) {
        // every add-on the person gave is asked, one after the other: when one is down or refuses, the others still answer; only when none answers is the first error said
        val all = addons(addonBase).ifEmpty { listOf(addonBase.trim()) }
        val out = ArrayList<Pick>()
        var firstError: Throwable? = null
        var answered = false
        for (one in all) {
            try { out += streamsOf(one, ep, kind); answered = true } catch (e: Throwable) { if (firstError == null || e is CertProblem) firstError = e }
        }
        if (!answered && firstError != null) throw firstError
        out.distinctBy { it.url }
    }

    private fun streamsOf(addonBase: String, ep: Ep, kind: String): List<Pick> {
        // a "|" in the add-on's settings part of the address is sent encoded
        val base = (normalizeAddon(addonBase) ?: addonBase.trim()).removeSuffix("/manifest.json").removeSuffix("/").replace("|", "%7C")
        val a = get("$base/stream/$kind/${pathId(ep.id)}.json").optJSONArray("streams") ?: return emptyList()
        val out = ArrayList<Pick>()
        for (i in 0 until a.length()) {
            val s = a.optJSONObject(i) ?: continue
            val text = s.optString("title") + " " + s.optString("name") + " " + s.optJSONObject("behaviorHints")?.optString("filename").orEmpty()
            val size = s.optJSONObject("behaviorHints")?.optLong("videoSize", 0L)?.takeIf { it > 0 } ?: sizeIn(text)
            val url = streamUrl(s) ?: continue
            // the label: the quality and the name, on one line (the add-ons put them on several)
            val label = (s.optString("name").replace('\n', ' ') + "  " + s.optString("title").lineSequence().firstOrNull().orEmpty()).trim()
            val seeds = Regex("(?:👤|seeders?:?)\\s*(\\d+)", RegexOption.IGNORE_CASE).find(s.optString("title") + " " + s.optString("description"))?.groupValues?.get(1)?.toIntOrNull() ?: -1
            out.add(Pick(url, size, label.ifBlank { text.trim().take(80) }, seeds = seeds))
        }
        return out
    }

    /**
     * The largest stream of the episode that is under [maxBytes]. When there is none under it, the smallest one there is (marked [Pick.over]); when no stream
     * says its size, the first one the add-on lists. Null only when the add-on has no stream at all.
     */
    suspend fun best(addonBase: String, ep: Ep, maxBytes: Long, kind: String = "series"): Pick? = bestOf(streams(addonBase, ep, kind), maxBytes)

    /**
     * The streams of an episode in the order they are tried. [maxBytes] is the size the person typed (in GB): a stream from a quarter of it up to nearly twice it is wanted
     * (never under 200 MB). The ones at or above the size come first (the most shared first), then the ones under it, the biggest first; in each group a stream somebody shares comes
     * before one nobody shares. Streams that do not say their size follow, then the rest, and the ones nobody shares go to the very end.
     */
    fun ranked(all: List<Pick>, maxBytes: Long): List<Pick> {
        val lo = maxOf(MIN_BYTES, maxBytes / 4); val hi = maxBytes * 2
        fun inside(p: Pick) = p.size in lo..hi
        fun order(l: List<Pick>): List<Pick> {
            // streams with many people sharing them come first (70 and more): they start quickly and stay fast; inside them the size nearest the one wanted from under it, then from above
            val good = l.filter { inside(it) && it.seeds >= GOOD_SEEDS }
            val goodDown = good.filter { it.size <= maxBytes }.sortedWith(compareByDescending<Pick> { it.size }.thenByDescending { it.seeds })
            val goodUp = good.filter { it.size > maxBytes }.sortedWith(compareBy<Pick> { it.size }.thenByDescending { it.seeds })
            val rest = l.filter { inside(it) && it.seeds < GOOD_SEEDS }
            val up = rest.filter { it.size >= maxBytes }.sortedWith(compareByDescending<Pick> { it.seeds }.thenBy { it.size })
            val down = rest.filter { it.size < maxBytes }.sortedWith(compareByDescending<Pick> { it.size }.thenByDescending { it.seeds })
            val unknown = l.filter { it.size <= 0L }.sortedByDescending { it.seeds }
            val outside = l.filter { it.size >= MIN_BYTES && !inside(it) }.sortedWith(compareByDescending<Pick> { it.seeds >= GOOD_SEEDS }.thenBy { kotlin.math.abs(it.size - maxBytes) })
            val tiny = l.filter { it.size in 1 until MIN_BYTES }
            return goodDown + goodUp + up + down + unknown + outside + tiny
        }
        return (order(all.filter { it.seeds != 0 }) + order(all.filter { it.seeds == 0 })).distinctBy { it.url }
    }

    /** The stream to start with: the first of [ranked] (marked [Pick.over] when it is bigger than the range wanted). Null only when the add-on has no stream at all. */
    fun bestOf(all: List<Pick>, maxBytes: Long): Pick? {
        val r = ranked(all, maxBytes).firstOrNull() ?: return null
        return if (r.size > maxBytes * 2) Pick(r.url, r.size, r.label, over = true, seeds = r.seeds) else r
    }

    /** The streams to fall back on when [pick] finds no one to download from: the rest of [ranked] in its order (eight at most). */
    fun alternatives(all: List<Pick>, pick: Pick, maxBytes: Long): List<String> = ranked(all, maxBytes).filter { it.url != pick.url }.take(8).map { it.url }

    private fun gbText(mb: Int) = "%.1f".format(mb / 1024.0).removeSuffix(".0").removeSuffix(",0")

    /** A stream shared by this many people or more is the first choice. */
    private const val GOOD_SEEDS = 70

    /** Under 200 MB a stream is too poor to be taken, unless nothing else is there. */
    private const val MIN_BYTES = 200L * 1_048_576L

    // 24 MB of pictures at most (a few big ones would fill a count-based cache)
    private val posters = object : android.util.LruCache<String, android.graphics.Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: android.graphics.Bitmap) = value.byteCount
    }

    /** The catalogue's small posters ("/poster/small/") are 100 pixels wide: the same picture in its 500 pixel size is asked for (what the Stremio apps show). */
    private fun sharp(url: String) = url.replace("/poster/small/", "/poster/medium/")

    /** A picture of the catalogue at the size it is shown (2x the box, so it stays sharp), cached; null when it cannot be had. */
    private fun loadPicture(url: String, wPx: Int, hPx: Int = 0): android.graphics.Bitmap? {
        val key = "$url@$wPx@$hPx"
        posters.get(key)?.let { return it }
        // timeouts and a browser agent (a bare openStream hung or got refused by the picture hosts); the 500 px link falls back to the original size, and one retry
        fun fetch(u: String) = runCatching {
            val c = URL(u).openConnection() as HttpURLConnection
            c.connectTimeout = 8000; c.readTimeout = 12000; c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36")
            c.inputStream.use { android.graphics.BitmapFactory.decodeStream(it) }
        }.getOrNull()
        val raw = fetch(url) ?: (if (url.contains("/poster/medium/")) fetch(url.replace("/poster/medium/", "/poster/small/")) else null) ?: fetch(url) ?: return null
        // a wide picture shown upright (cropped to a portrait box) needs enough height, not only enough width: twice the box's height, at most what the picture has
        val want = minOf(raw.width, if (hPx > 0) maxOf(wPx * 2, (hPx * 2f * raw.width / raw.height).toInt()) else wPx * 2)
        val out = if (raw.width > want) android.graphics.Bitmap.createScaledBitmap(raw, want, (raw.height * want.toFloat() / raw.width).toInt().coerceAtLeast(1), true).also { raw.recycle() } else raw
        posters.put(key, out)
        return out
    }

    /** A picture of the catalogue, loaded as it is shown; while it is not there (or there is none) the box shows [label], so a missing picture never leaves a hole. */
    @Composable
    private fun Poster(url: String, w: androidx.compose.ui.unit.Dp = 46.dp, h: androidx.compose.ui.unit.Dp = 68.dp, label: String = "") {
        val src = sharp(url)
        val wPx = with(androidx.compose.ui.platform.LocalDensity.current) { w.roundToPx() }
        val bmp by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(posters.get("$src@$wPx"), src, wPx) {
            // a new link starts from its own cached picture (or none): the picture of the link before must not stay (a list that changes kept the old posters)
            value = posters.get("$src@$wPx")
            if (value == null && src.startsWith("http")) value = withContext(Dispatchers.IO) { loadPicture(src, wPx) }
        }
        val b = bmp
        androidx.compose.foundation.layout.Box(Modifier.width(w).height(h).clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).background(T.line.copy(alpha = .35f)), contentAlignment = androidx.compose.ui.Alignment.Center) {
            if (b != null) androidx.compose.foundation.Image(b.asImageBitmap(), null, Modifier.width(w).height(h), contentScale = androidx.compose.ui.layout.ContentScale.Crop, filterQuality = androidx.compose.ui.graphics.FilterQuality.High)
            else if (label.isNotEmpty()) Text(label, color = T.sub, fontSize = 13.sp)
        }
    }

    /** A season's cover: its first episode's picture, wide and sharp, with the season and its episode count over a soft shade at the bottom; a ring when it is the open one. */
    @Composable
    private fun SeasonCover(url: String, title: String, sub: String, selected: Boolean) {
        val src = sharp(url)
        val w = 96.dp
        val wPx = with(androidx.compose.ui.platform.LocalDensity.current) { w.roundToPx() }
        val hPx = with(androidx.compose.ui.platform.LocalDensity.current) { 144.dp.roundToPx() }
        val bmp by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(posters.get("$src@$wPx@$hPx"), src, wPx) {
            value = posters.get("$src@$wPx@$hPx")
            if (value == null && src.startsWith("http")) value = withContext(Dispatchers.IO) { loadPicture(src, wPx, hPx) }
        }
        val b = bmp
        val shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
        androidx.compose.foundation.layout.Box(Modifier.width(w).height(144.dp).clip(shape).background(T.line.copy(alpha = .35f))
            .then(if (selected) Modifier.border(2.dp, T.accent, shape) else Modifier)) {
            if (b != null) androidx.compose.foundation.Image(b.asImageBitmap(), null, Modifier.matchParentSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop, filterQuality = androidx.compose.ui.graphics.FilterQuality.High)
            Box(Modifier.matchParentSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color.Transparent, androidx.compose.ui.graphics.Color(0xCC000000)), startY = 120f)))
            Column(Modifier.align(androidx.compose.ui.Alignment.BottomStart).padding(10.dp, 6.dp)) {
                Text(title, color = androidx.compose.ui.graphics.Color.White, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, maxLines = 1)
                Text(sub, color = androidx.compose.ui.graphics.Color(0xCCFFFFFF), fontSize = 11.sp, maxLines = 1)
            }
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

    private fun sizeText(b: Long) = if (b <= 0L) "?" else if (b >= 1_073_741_824L) "%.1f GB".format(b / 1_073_741_824.0) else "${b / 1_048_576} MB"

    private val SIZES = listOf(500, 800, 1500, 3000, 6000)

    @Composable
    private fun Caption(s: String) = Text(s, color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))

    /** Series and movies as a screen of its own (opened from YTXTube): a back button and the title over the page, on the look's own background. */
    @Composable
    fun PageScreen(back: () -> Unit) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                // the way back in the look's own style: a Windows button on the Windows looks, the arrow elsewhere
                if (T.id in com.umutk.pulse.winButtonLooks && T.id != "win11") PButton(tr("Back", "Geri"), compact = true, onClick = back)
                else Box(Modifier.size(40.dp).clickable(onClick = back), contentAlignment = androidx.compose.ui.Alignment.Center) { PIcon("back", T.text, 22.dp) }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { Card(page = true, shelf = true) }
        }
    }

    private fun Modifier.graphicsLayerRotate(on: Boolean) = this.then(Modifier.graphicsLayer { rotationZ = if (on) 180f else 0f })

    /** One shelf: its title over a row of posters (the poster, the name under it); a tap picks the title. */
    @Composable
    private fun ShelfRow(title: String, list: List<Show>, onPick: (Show) -> Unit) {
        Text(title, color = T.text, fontSize = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
        if (list.isEmpty()) { Text(tr("Loading…", "Yükleniyor…"), color = T.sub, fontSize = 12.sp); return }
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(list) { s ->
                Column(Modifier.width(110.dp).clickable { onPick(s) }) {
                    Poster(s.poster, 110.dp, 163.dp)
                    Text(s.name, color = T.text, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                    if (s.year.isNotBlank()) Text(s.year, color = T.sub, fontSize = 11.sp, maxLines = 1)
                }
            }
        }
    }

    /** The series and movies page is open (its own page; going back shows the downloads). */
    var pageOpen by androidx.compose.runtime.mutableStateOf(false)

    @Composable
    fun Card(page: Boolean = false, shelf: Boolean = false) {
        // the free-software builds only: the Google Play build has neither this card nor the torrent engine
        if (!com.umutk.pulse.BuildConfig.APK_INSTALL) return
        val ctx = LocalContext.current
        val scope = rememberCoroutineScope()
        val open = page
        var movies by remember { mutableStateOf(false) }
        var addon by remember { mutableStateOf(addon(ctx)) }
        var editAddon by remember { mutableStateOf(addon(ctx).isBlank()) }
        var draft by remember { mutableStateOf("") }
        var listOpen by remember { mutableStateOf(false) }
        var listUrl by remember { mutableStateOf(catUrl(ctx)) }
        var listNote by remember { mutableStateOf("") }
        var listItems by remember { mutableStateOf(listOf<ListedAddon>()) }
        // what each add-on's manifest said (true = it answers and has streams, the text under its name), filled in as they are added or opened
        var states by remember { mutableStateOf(mapOf<String, Pair<Boolean, String>>()) }
        // an add-on whose server certificate the phone does not trust: its problem, and the button that trusts that one certificate
        var certs by remember { mutableStateOf(mapOf<String, SeasonDl.CertProblem>()) }
        var checkTick by remember { mutableStateOf(0) }
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
        // why the add-on gave nothing (its answer or the network's), shown under the list; and the episode whose streams are being chosen from
        var why by remember { mutableStateOf("") }
        fun said(e: Throwable): String { return tr("The add-on did not answer: ", "Eklenti cevap vermedi: ") + (e.message ?: e.javaClass.simpleName) }
        var choose by remember { mutableStateOf<Ep?>(null) }
        var options by remember { mutableStateOf<List<Pick>?>(null) }
        fun limit() = mb * 1_048_576L
        fun clean(s: String) = s.replace(Regex("[^\\p{L}\\p{N} ._()-]"), "").trim()
        fun reset() { shows = emptyList(); show = null; eps = emptyList(); note = ""; season = null; picked = emptySet(); found = emptyMap() }
        // the shelves (shelf mode): what is popular now; and the add-on / size part, shut once an add-on is there
        var settingsOpen by remember { mutableStateOf(addon(ctx).isBlank()) }
        var popSeries by remember { mutableStateOf<List<Show>>(emptyList()) }
        var popMovies by remember { mutableStateOf<List<Show>>(emptyList()) }
        androidx.compose.runtime.LaunchedEffect(shelf) {
            if (shelf) {
                popSeries = runCatching { popular("series") }.getOrDefault(emptyList())
                popMovies = runCatching { popular("movie") }.getOrDefault(emptyList())
            }
        }
        /** A title is chosen (from a shelf, or from the search results): a series opens its seasons, a movie its download button. */
        fun pick(s: Show) {
            val mv = if (shelf) s.kind == "movie" else movies
            movies = mv
            show = s; eps = emptyList(); season = null; picked = emptySet(); found = emptyMap()
            if (!mv) {
                note = tr("Reading the episodes…", "Bölümler okunuyor…")
                scope.launch { eps = runCatching { episodes(s) }.getOrDefault(emptyList()); note = if (eps.isEmpty()) tr("No episodes found", "Bölüm bulunamadı") else "" }
            } else note = ""
        }
        /** The server first (the Stremio app is opened if it is not running, when the engine in Pulse is not there), then every pick is added. */
        suspend fun start(picks: List<Triple<String, String, List<String>>>) {
            if (picks.any { it.first.contains(":11470/") } && !ensureServer(ctx)) note = tr("The Stremio app is not running and could not be opened", "Stremio uygulaması çalışmıyor ve açılamadı")
            // the torrent's own file name stays (not a name made of the title): subtitle search and the player find the release by it
            // the episode's own name ("Dexter S05E02") is the file's name: the torrent's file name is kept only when no name was given
            picks.forEach { (u, name, alts) -> Downloads.add(ctx, u, name.takeIf { it.isNotBlank() }?.let { Downloads.cleanName(it) }, alts) }
        }
        PCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!page) Row(Modifier.fillMaxWidth().clickable { pageOpen = true }, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
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
                    // the shelf page: a big title, a line under it, and the add-on / size part behind one row
                    if (shelf && show == null) {
                        Text(tr("Series and movies", "Diziler ve filmler"), color = T.text, fontSize = 26.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        Text(tr("What is popular now. Pick one to see its seasons or to download the film.", "Şu an popüler olanlar. Sezonlarını görmek ya da filmi indirmek için birini seç."), color = T.sub, fontSize = 13.sp)
                        Row(Modifier.fillMaxWidth().clickable { settingsOpen = !settingsOpen }.padding(vertical = 6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            PIcon("settings", T.sub, 18.dp)
                            Text(tr("Add-on and size", "Eklenti ve boyut"), color = T.sub, fontSize = 13.sp, modifier = Modifier.weight(1f).padding(start = 8.dp))
                            PIcon("chevron", T.sub, 18.dp, Modifier.graphicsLayerRotate(settingsOpen))
                        }
                    }
                    if (!shelf) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PChip(!movies, tr("Series", "Diziler")) { movies = false; reset() }
                        PChip(movies, tr("Movies", "Filmler")) { movies = true; reset() }
                    }
                    if ((!shelf || settingsOpen) && show == null || !shelf) {
                    // 1. where the streams come from
                    Caption(tr("1  Add-ons (as many as you like)", "1  Eklentiler (istediğin kadar)"))
                    // the add-ons given so far, each with a remove button; a new one is added with the field below (any number: when one does not answer, the next one does)
                    androidx.compose.runtime.LaunchedEffect(addon, checkTick) {
                        for (one in addons(addon)) if (states[one] == null) {
                            val r = runCatching { check(one) }
                            states = states + (one to r.fold(
                                { i -> (i.streams) to (i.name.ifBlank { runCatching { Uri.parse(one).host }.getOrNull() ?: one } + if (i.streams) "" else tr("  (no streams)", "  (yayın yok)")) },
                                { e -> (e as? SeasonDl.CertProblem)?.let { certs = certs + (one to it) }; false to (e.message ?: e.javaClass.simpleName) }))
                        }
                    }
                    addons(addon).forEach { one ->
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            val st = states[one]
                            Column(Modifier.weight(1f)) {
                                Text((if (st == null) "…  " else if (st.first) "✓  " else "✗  ") + (st?.second ?: (runCatching { Uri.parse(one).host }.getOrNull() ?: one)), color = if (st != null && !st.first) androidx.compose.ui.graphics.Color(0xFFE5A04D) else T.text, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (st != null) Text(runCatching { Uri.parse(one).host }.getOrNull().orEmpty(), color = T.sub, fontSize = 11.sp, maxLines = 1)
                                certs[one]?.let { cp -> Text(tr("Fingerprint: ", "Parmak izi: ") + cp.sha.take(16) + "…", color = T.sub, fontSize = 11.sp, maxLines = 1) }
                            }
                            certs[one]?.let { cp ->
                                PChip(false, tr("Trust this server", "Bu sunucuya güven")) { SeasonDl.trust(ctx, cp); certs = certs - one; states = states - one; checkTick++ }
                                Spacer(Modifier.width(6.dp))
                            }
                            PChip(false, tr("Remove", "Kaldır")) { addon = addons(addon).filter { it != one }.joinToString(" "); save(ctx, addon, mb.toFloat()); found = emptyMap(); editAddon = addon.isBlank() }
                        }
                    }
                    if (addon.isNotBlank() && !editAddon) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PChip(false, tr("Add another add-on", "Başka eklenti ekle")) { editAddon = true; draft = "" }
                        PChip(listOpen, tr("From a list", "Listeden")) { listOpen = !listOpen }
                    }
                    if (addon.isBlank() || editAddon) PChip(listOpen, tr("From a list", "Listeden")) { listOpen = !listOpen }
                    // an add-on list from a link the person gives (Pulse ships no list and no add-on): each entry has an Add button
                    if (listOpen) {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            PField(listUrl, { listUrl = it }, tr("Link of an add-on list", "Eklenti listesinin bağlantısı"), Modifier.weight(1f))
                            Spacer(Modifier.width(8.dp))
                            PButton(tr("Load", "Yükle"), compact = true) {
                                if (listUrl.isBlank()) { listNote = tr("Put the link of a list first", "Önce bir liste bağlantısı yaz"); return@PButton }
                                saveCatUrl(ctx, listUrl); listNote = tr("Loading…", "Yükleniyor…"); listItems = emptyList()
                                scope.launch { runCatching { addonList(listUrl) }.onSuccess { listItems = it; listNote = if (it.isEmpty()) tr("The list is empty", "Liste boş") else "" }.onFailure { e -> said(e); listNote = e.message ?: e.javaClass.simpleName } }
                            }
                        }
                        if (listNote.isNotEmpty()) Text(listNote, color = androidx.compose.ui.graphics.Color(0xFFE5A04D), fontSize = 12.sp)
                        listItems.forEach { li ->
                            val have = li.url in addons(addon)
                            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(li.name, color = T.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (li.about.isNotBlank()) Text(li.about, color = T.sub, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                                if (have) PIcon("check", T.accent, 20.dp) else PChip(false, tr("Add", "Ekle")) { addon = (addons(addon) + li.url).distinct().joinToString(" "); save(ctx, addon, mb.toFloat()); found = emptyMap(); editAddon = false }
                            }
                        }
                    }
                    else Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        PField(draft, { draft = it }, "https://…/manifest.json", Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        // the link copied from the add-on's page goes in with one press
                        PButton(tr("Paste", "Yapıştır"), compact = true) {
                            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val s = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()?.trim().orEmpty()
                            val n = normalizeAddon(s)
                            if (n != null) { addon = (addons(addon) + n).distinct().joinToString(" "); save(ctx, addon, mb.toFloat()); found = emptyMap(); editAddon = false; draft = ""; note = "" }
                            else note = tr("Copy the add-on link first", "Önce eklenti bağlantısını kopyala")
                        }
                        if (draft.isNotBlank()) { Spacer(Modifier.width(6.dp)); PButton(tr("OK", "Tamam"), compact = true) { val n = normalizeAddon(draft); if (n != null) { addon = (addons(addon) + n).distinct().joinToString(" "); save(ctx, addon, mb.toFloat()); found = emptyMap(); editAddon = false; draft = "" } else note = tr("That is not a link", "Bu bir bağlantı değil") } }
                        if (addon.isNotBlank()) { Spacer(Modifier.width(6.dp)); PChip(false, tr("Cancel", "Vazgeç")) { editAddon = false; draft = "" } }
                    }
                    if (addon.isBlank() || editAddon) Text(tr("Open the add-on's page in your browser, copy the link of its page or of its Install button, and come back: Pulse takes it from what you copied (or press Paste).", "Eklentinin sayfasını tarayıcıda aç, sayfanın ya da Yükle düğmesinin bağlantısını kopyala ve buraya dön: Pulse kopyaladığından kendisi alır (ya da Yapıştır'a bas)."), color = T.sub, fontSize = 12.sp)
                    // 2. the size limit: the largest stream still under it is taken
                    Caption(tr("2  Size per ${if (movies) "movie" else "episode"} (GB)", "2  ${if (movies) "Film" else "Bölüm"} başına boyut (GB)"))
                    var gbField by remember { mutableStateOf(gbText(mb)) }
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        PField(gbField, { v ->
                            gbField = v
                            val g = v.replace(',', '.').trim().toDoubleOrNull()
                            if (g != null && g in 0.2..60.0) { mb = (g * 1024).toInt(); save(ctx, addon, mb.toFloat()); found = emptyMap() }
                        }, "GB", Modifier.width(110.dp))
                        Text("  " + tr("looks from ${sizeText(maxOf(MIN_BYTES, limit() / 4))} to ${sizeText((limit() * 2))}, streams shared by 70 or more first", "${sizeText(maxOf(MIN_BYTES, limit() / 4))} ile ${sizeText((limit() * 2))} arası aranır, 70 ve üzeri kişinin paylaştığı yayınlar önce"), color = T.sub, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    }
                    }
                    // 3. what to download
                    if (!shelf) Caption(tr("3  Find the ${if (movies) "movie" else "series"}", "3  ${if (movies) "Filmi" else "Diziyi"} bul"))
                    // the results come by themselves as the name is typed (a moment after the last letter); no button to press
                    androidx.compose.runtime.LaunchedEffect(q, if (shelf) false else movies) {
                        val name = q.trim()
                        if (name.length < 2) { if (name.isEmpty() && (shows.isNotEmpty() || (show != null && !shelf))) reset(); return@LaunchedEffect }
                        kotlinx.coroutines.delay(450)
                        reset(); note = tr("Looking…", "Aranıyor…")
                        shows = if (shelf) runCatching { search(name, "series") }.getOrDefault(emptyList()) + runCatching { search(name, "movie") }.getOrDefault(emptyList())
                            else runCatching { search(name, if (movies) "movie" else "series") }.getOrDefault(emptyList())
                        note = if (shows.isEmpty()) tr("Nothing found", "Bulunamadı") else ""
                    }
                    if (!shelf || show == null) PField(q, { q = it }, if (shelf) tr("Search series and movies", "Dizi ve film ara") else if (movies) tr("Movie name", "Film adı") else tr("Series name", "Dizi adı"), Modifier.fillMaxWidth(), onDone = null)
                    // the shelves, while nothing is typed
                    if (shelf && show == null && q.isBlank()) {
                        ShelfRow(tr("Popular series", "Popüler diziler"), popSeries) { pick(it) }
                        ShelfRow(tr("Popular movies", "Popüler filmler"), popMovies) { pick(it) }
                    }
                    val sel = show
                    if (sel == null) {
                        // the results: a tap chooses one, and the list gives way to it (its seasons or its download button come right under it)
                        shows.forEach { s -> key(s.id) {
                            Row(Modifier.fillMaxWidth().clickable { pick(s) }.padding(vertical = 4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Poster(s.poster, 54.dp, 80.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(s.name, color = T.text, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    if (s.year.isNotBlank()) Text(s.year, color = T.sub, fontSize = 12.sp)
                                }
                            }
                        } }
                    } else {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Poster(sel.poster, 64.dp, 96.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(sel.name, color = T.accent, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (sel.year.isNotBlank()) Text(sel.year, color = T.sub, fontSize = 12.sp)
                            }
                            PChip(false, tr("Back", "Geri")) { show = null; eps = emptyList(); note = "" }
                        }
                        if (movies) PButton(tr("Watch now", "Hemen izle"), Modifier.fillMaxWidth()) {
                            if (busy) return@PButton
                            if (addon.isBlank()) { note = tr("Put the add-on link first", "Önce eklenti bağlantısını yaz"); return@PButton }
                            busy = true; note = tr("Finding the stream…", "Yayın aranıyor…")
                            scope.launch {
                                val all = runCatching { streams(addon, Ep(sel.id, 0, 0, ""), "movie") }.getOrDefault(emptyList())
                                try { note = playBest(ctx, all, limit()) { note = it } } catch (e: Throwable) { if (e is kotlinx.coroutines.CancellationException) throw e; note = e.message.orEmpty() } finally { busy = false }
                            }
                        }
                        if (movies) PButton(tr("Download the movie (up to ${mb} MB)", "Filmi indir (en çok ${mb} MB)"), Modifier.fillMaxWidth()) {
                            if (busy) return@PButton
                            if (addon.isBlank()) { note = tr("Put the add-on link first", "Önce eklenti bağlantısını yaz"); return@PButton }
                            busy = true; note = tr("Finding the stream…", "Yayın aranıyor…")
                            scope.launch {
                                val all = runCatching { streams(addon, Ep(sel.id, 0, 0, ""), "movie") }.getOrDefault(emptyList())
                                val pick = bestOf(all, limit())
                                if (pick == null) note = tr("Nothing under $mb MB", "$mb MB altında yayın yok")
                                else { start(listOf(Triple(pick.url, clean(sel.name + if (sel.year.isNotBlank()) " (" + sel.year.take(4) + ")" else ""), alternatives(all, pick, limit())))); if (note.startsWith(tr("Finding", "Yayın"))) note = tr("Started (${sizeText(pick.size)})", "Başladı (${sizeText(pick.size)})") }
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
                                    // whatever goes wrong, the button must come back (a stuck "busy" made the whole season button do nothing from then on) and the person must read why
                                    try {
                                        var skipped = 0; var over = 0
                                        val picks = ArrayList<Triple<String, String, List<String>>>()
                                        val name = clean(sel.name)
                                        // the streams of the episodes are asked three at a time (one after the other, a season took minutes before anything started)
                                        val looked = ArrayList<Pair<Ep, List<Pick>>>()
                                        for (chunk in list.chunked(3)) {
                                            note = tr("Finding streams ${looked.size + 1}/${list.size}…", "Yayınlar aranıyor ${looked.size + 1}/${list.size}…")
                                            looked += coroutineScope {
                                                chunk.map { ep -> async(Dispatchers.IO) { ep to (runCatching { streams(addon, ep) }.onFailure { e -> why = said(e) }.getOrNull().orEmpty()) } }.awaitAll()
                                            }
                                        }
                                        for ((ep, all) in looked) {
                                            // the stream already found for the list is used; else the best of the whole list (the rest go along as streams to fall back on)
                                            val pick = found[ep.id] ?: bestOf(all, limit())
                                            if (pick == null) skipped++ else { if (pick.over) over++; picks.add(Triple(pick.url, "%s S%02dE%02d".format(name, ep.season, ep.episode), alternatives(all, pick, limit()))) }
                                        }
                                        note = tr("Starting…", "Başlıyor…")
                                        start(picks)
                                        note = when {
                                            picks.isEmpty() -> why.ifEmpty { tr("No stream was found for these episodes (check the add-on link)", "Bu bölümler için yayın bulunamadı (eklenti bağlantısını kontrol et)") }
                                            else -> tr("${picks.size} started (up to $mb MB each): see them in Downloads", "${picks.size} başladı (her biri en çok $mb MB): İndirilenler'de görebilirsin") +
                                                (if (over > 0) tr(", $over over the limit (the smallest there was)", ", $over sınırın üstünde (bulunan en küçük)") else "") +
                                                (if (skipped > 0) tr(", $skipped without a stream", ", $skipped için yayın yok") else "")
                                        }
                                    } catch (e: Throwable) {
                                        if (e is kotlinx.coroutines.CancellationException) throw e
                                        note = tr("Could not start: ", "Başlatılamadı: ") + (e.message ?: e.javaClass.simpleName)
                                    } finally { busy = false }
                                }
                            }
                            /** The size of the stream that would be taken is looked up for every episode of the season, three at a time, and shown on its row. */
                            fun lookUp(sn: Int) {
                                val todo = eps.filter { it.season == sn && !found.containsKey(it.id) }
                                if (todo.isEmpty() || addon.isBlank()) return
                                val sem = kotlinx.coroutines.sync.Semaphore(3)
                                scope.launch {
                                    todo.map { ep -> scope.launch { sem.acquire(); try { val r = runCatching { best(addon, ep, limit()) }; r.exceptionOrNull()?.let { e -> why = said(e) }; found = found + (ep.id to r.getOrNull()) } finally { sem.release() } } }
                                }
                            }
                            // the sizes are looked up as soon as a season is open, and again when the size limit or the add-on changes
                            androidx.compose.runtime.LaunchedEffect(season, mb, addon) { season?.let { lookUp(it) } }
                            // each season's own picture from TMDB, when the person has given a key in Download manager settings
                            var pics by remember(sel.id) { mutableStateOf(emptyMap<Int, String>()) }
                            androidx.compose.runtime.LaunchedEffect(sel.id) { pics = runCatching { seasonPosters(sel.id, tmdbKey(ctx)) }.getOrDefault(emptyMap()) }
                            if (seasons.isNotEmpty()) {
                                Caption(tr("Seasons", "Sezonlar"))
                                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    items(seasons) { sn ->
                                        val n = eps.count { it.season == sn }
                                        Box(Modifier.clickable { season = sn; picked = eps.filter { it.season == sn }.map { it.id }.toSet() }) {
                                            // Cinemeta has no picture of its own for a season: the series poster (upright) stands for each one, with the season over it
                                            SeasonCover(pics[sn] ?: eps.firstOrNull { it.season == sn && it.thumb.isNotBlank() }?.thumb ?: sel.poster, if (sn == 0) tr("Specials", "Özel bölümler") else tr("Season $sn", "Sezon $sn"), tr("$n ep", "$n bölüm"), season == sn)
                                        }
                                    }
                                }
                                val sn = season
                                if (sn != null) {
                                    val list = eps.filter { it.season == sn }
                                    val chosen = list
                                    // the whole season (or the ticked episodes) in one press
                                    PButton(tr("Download season $sn  ·  ${chosen.size} of ${list.size} episodes", "Sezon $sn indir  ·  ${list.size} bölümden ${chosen.size}"), Modifier.fillMaxWidth()) { grab(chosen) }
                                    // the episodes: a tap opens the streams of that episode (who shares it, its size) to choose from, the arrow downloads the best one at once
                                    list.forEach { ep -> key(ep.id) {
                                        val on = ep.id in picked
                                        Row(Modifier.fillMaxWidth().clickable {
                                            choose = ep; options = null
                                            scope.launch { options = runCatching { streams(addon, ep) }.onFailure { e -> why = said(e) }.getOrDefault(emptyList()).sortedWith(compareByDescending<Pick> { it.seeds }.thenByDescending { it.size }) }
                                        }.padding(vertical = 6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                            // the episode's own picture; without one the series poster, and without that the number
                                            Poster(ep.thumb.ifBlank { sel.poster }, 112.dp, 63.dp, "E%02d".format(ep.episode))
                                            Spacer(Modifier.width(10.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text("E%02d".format(ep.episode) + if (ep.title.isNotBlank()) "  ·  " + ep.title else "", color = if (on) T.text else T.sub, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                if (ep.overview.isNotBlank()) Text(ep.overview, color = T.sub, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                            }
                                            // the size of the stream that would be taken ("…" while it is being looked for, "—" when there is none)
                                            val f = found[ep.id]
                                            Text(if (!found.containsKey(ep.id)) "…" else if (f == null) "—" else (if (f.over) "↑ " else "") + sizeText(f.size), color = if (f?.over == true) androidx.compose.ui.graphics.Color(0xFFE5A04D) else T.sub, fontSize = 12.sp)
                                            androidx.compose.foundation.layout.Box(Modifier.clickable {
                                                if (addon.isBlank()) { note = tr("Put the add-on link first", "Önce eklenti bağlantısını yaz"); return@clickable }
                                                note = tr("Finding the stream…", "Yayın aranıyor…")
                                                scope.launch {
                                                    val all = runCatching { streams(addon, ep) }.onFailure { e -> why = said(e) }.getOrNull().orEmpty()
                                                    note = try { playBest(ctx, all, limit()) { note = it } } catch (e: Throwable) { if (e is kotlinx.coroutines.CancellationException) throw e; e.message.orEmpty() }
                                                }
                                            }.padding(6.dp)) { PIcon("play", T.accent, 20.dp) }
                                            androidx.compose.foundation.layout.Box(Modifier.clickable { grab(listOf(ep)) }.padding(6.dp)) { PIcon("download", T.text, 20.dp) }
                                        }
                                    }
                                    }
                                }
                            }
                        }
                    }
                    if (note.isNotEmpty()) Text(note, color = T.sub, fontSize = 12.sp)
                    if (why.isNotEmpty() && why != note) Text(why, color = androidx.compose.ui.graphics.Color(0xFFE5A04D), fontSize = 12.sp)
                    // the server's certificate is not one the phone knows: shown with its fingerprint, trusted only when the person says so (just that certificate, just this server)
                    // the streams of one episode to choose from: quality and size, a tap downloads that one
                    choose?.let { ep ->
                        com.umutk.pulse.WinAlert(onDismissRequest = { choose = null },
                            title = { Text("S%02dE%02d".format(ep.season, ep.episode) + (if (ep.title.isNotBlank()) "  ·  " + ep.title else ""), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            text = {
                                val opts = options
                                Column(Modifier.height(420.dp).verticalScroll(rememberScrollState())) {
                                    when {
                                        opts == null -> Text(tr("Looking for streams…", "Yayınlar aranıyor…"), color = T.sub, fontSize = 13.sp)
                                        opts.isEmpty() -> Text(why.ifEmpty { tr("No stream for this episode", "Bu bölüm için yayın yok") }, color = T.sub, fontSize = 13.sp)
                                        else -> opts.forEach { o ->
                                            Row(Modifier.fillMaxWidth().clickable {
                                                val name = clean(show?.name.orEmpty()) + " S%02dE%02d".format(ep.season, ep.episode)
                                                choose = null
                                                scope.launch { start(listOf(Triple(o.url, name, alternatives(opts, o, if (o.size > 0) o.size else limit())))); note = tr("Started (${sizeText(o.size)})", "Başladı (${sizeText(o.size)})") }
                                            }.padding(vertical = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                                Text(o.label, color = T.text, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                                Spacer(Modifier.width(8.dp))
                                                // how many share it (the add-on's own number) and its size; green when it is in the range wanted
                                                Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                                                    Text(if (o.seeds >= 0) "👤 ${o.seeds}" else "👤 ?", color = if (o.seeds == 0) androidx.compose.ui.graphics.Color(0xFFE5A04D) else T.text, fontSize = 13.sp)
                                                    Text(sizeText(o.size), color = if (o.size in maxOf(MIN_BYTES, limit() / 4)..(limit() * 2)) T.accent else T.sub, fontSize = 12.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                            confirmButton = { androidx.compose.material3.TextButton({ choose = null }) { Text(tr("Close", "Kapat")) } })
                    }
                    Spacer(Modifier.height(2.dp))
                }
            }
        }
    }
}
