package com.umutk.pulse.extras.store

import android.content.Context
import android.content.SharedPreferences
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Installed extra modules are packages named `com.umutk.pulse.extra.<id>`; the app list hides them. */
const val EXTRA_MODULE_PREFIX = "com.umutk.pulse.extra."

/** The live manifest (doc 13). Read with ETag, at most once a day; the bundled copy is the fallback. */
const val EXTRAS_MANIFEST_URL = "https://raw.githubusercontent.com/xUmutKx/Pulse/main/extras/manifest.json"

/** Packs whose text manifest is bundled as assets/extras/<pack>/manifest.json. */
val BUNDLED_PACKS = listOf("ambient", "skins")

/** Built into Pulse now, so never offered as a download, even when a saved or live manifest still lists them. */
val BUILT_IN_MODULES = setOf("fossify-messages")

/** One downloadable module from the manifest (doc 13). "apk" is installed, "veri" is unzipped into the app data folder. */
data class ExtraModule(
    val id: String, val adEn: String, val adTr: String, val tur: String, val kategori: String,
    val boyut: Long, val surum: String, val url: String, val sha256: String,
    val paket: String?, val imzaSha256: String?, val upstream: String?, val kaynakUrl: String?,
    val lisans: String, val lisansMetni: String?, val atif: String?,
    val aciklamaEn: String, val aciklamaTr: String, val minPulse: Long, val guncellendi: String,
) {
    val isApk get() = tur == "apk"
    fun name() = tr(adEn, adTr)
    fun description() = tr(aciklamaEn, aciklamaTr)
}

/** A FOSS app the store does not host (doc 10, 11): a link to its store page, then open it. */
class ExternalApp(
    val pkg: String, val name: String, val fdroid: String?, val upstream: String, val license: String,
    val sizeText: String, val kategori: String, val minVersion: String? = null,
    /** False: the row appears only once the app is installed. */
    val showWhenMissing: Boolean = true,
)

/** Empty: every extra is built into Pulse (video, music, lyrics, white noise), no outside app is linked. */
val EXTERNAL_APPS = listOf<ExternalApp>()

private val ID_RE = Regex("[a-z0-9-]{1,64}")
private val SHA_RE = Regex("[0-9a-f]{64}")
private val PKG_RE = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")

/** Reads a manifest; a broken entry is skipped, so a bad line never reaches the screen. */
fun parseManifest(text: String): List<ExtraModule> {
    val arr = runCatching { JSONObject(text).getJSONArray("moduller") }.getOrNull() ?: return emptyList()
    return (0 until arr.length()).mapNotNull { runCatching { arr.getJSONObject(it).toModule() }.getOrNull() }
}

private fun JSONObject.toModule(): ExtraModule {
    val id = getString("id").also { require(ID_RE.matches(it)) }
    val tur = getString("tur").also { require(it == "apk" || it == "veri") }
    val url = getString("url").also { require(it.startsWith("https://")) }
    val sha = getString("sha256").lowercase().also { require(SHA_RE.matches(it)) }
    val boyut = getLong("boyut").also { require(it > 0) }
    val paket = optString("paket").ifEmpty { null }?.also { require(PKG_RE.matches(it)) }
    val imza = optString("imza_sha256").ifEmpty { null }?.lowercase()?.also { require(SHA_RE.matches(it)) }
    require(tur == "veri" || (paket != null && imza != null))
    val en = getString("ad_en")
    val aciklamaEn = optString("aciklama_en")
    return ExtraModule(
        id, en, optString("ad_tr", en), tur, getString("kategori"), boyut, getString("surum"), url, sha,
        paket, imza, optString("upstream").ifEmpty { null }, optString("kaynak_url").ifEmpty { null },
        getString("lisans"), optString("lisans_metni").ifEmpty { null }, optString("atif").ifEmpty { null },
        aciklamaEn, optString("aciklama_tr", aciklamaEn), optLong("min_pulse", 0L), optString("guncellendi"),
    )
}

/** "1.2.0" style versions, part by part; a missing or non-number part counts as 0. */
fun compareVersions(a: String, b: String): Int {
    val x = a.split('.', '-').map { it.toIntOrNull() ?: 0 }
    val y = b.split('.', '-').map { it.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(x.size, y.size)) {
        val c = x.getOrElse(i) { 0 }.compareTo(y.getOrElse(i) { 0 })
        if (c != 0) return c
    }
    return 0
}

/** Megabytes with one decimal, or a dash while the size is unknown. */
fun formatMb(bytes: Long?): String = if (bytes == null) "—" else "%.1f MB".format(bytes / 1_000_000.0)

/** Sum of the listed download sizes. */
fun totalBytes(modules: List<ExtraModule>): Long = modules.sumOf { it.boyut }

fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

fun sha256Hex(file: File): String {
    val md = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { i ->
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = i.read(buf)
            if (n < 0) break
            md.update(buf, 0, n)
        }
    }
    return md.digest().joinToString("") { "%02x".format(it) }
}

/** HTTPS only. Used for the manifest and every download. */
internal fun openHttps(url: String): HttpURLConnection {
    val u = URL(url)
    require(u.protocol == "https") { "https only" }
    return (u.openConnection() as HttpURLConnection).apply { connectTimeout = 10_000; readTimeout = 30_000; instanceFollowRedirects = true }
}

/** The module list: the saved manifest when there is one, else the bundled packs. */
object StoreCatalog {
    private const val DAY_MS = 86_400_000L

    /** True when the last refresh failed, so the list on screen is the saved one. */
    @Volatile var offline = false

    private fun prefs(c: Context): SharedPreferences = c.getSharedPreferences("extra_store", Context.MODE_PRIVATE)
    private fun cacheFile(c: Context) = File(c.filesDir, "store/manifest.json")

    /** Modules this Pulse build is too old for are dropped (min_pulse). */
    fun load(c: Context): List<ExtraModule> {
        val saved = runCatching { cacheFile(c).readText() }.getOrNull()?.let(::parseManifest).orEmpty()
        val all = saved.ifEmpty {
            runCatching { c.assets.open("extras/manifest.json").bufferedReader().use { it.readText() } }
                .getOrNull()?.let(::parseManifest).orEmpty()
        }.ifEmpty {
            BUNDLED_PACKS.flatMap { pack ->
                runCatching { c.assets.open("extras/$pack/manifest.json").bufferedReader().use { it.readText() } }
                    .getOrNull()?.let(::parseManifest).orEmpty()
            }
        }
        val code = runCatching { com.umutk.pulse.Compat.longVersion(c.packageManager.getPackageInfo(c.packageName, 0)) }.getOrDefault(Long.MAX_VALUE)
        // the ambient sounds and the Winamp skins are in the app already: no download row for them
        return all.filter { it.minPulse <= code && it.id !in BUILT_IN_MODULES && (it.paket ?: "") !in BUNDLED_PACKS && it.id !in BUNDLED_PACKS }
    }

    /** Asks GitHub with ETag, at most once a day unless [force]. Any failure keeps the saved list. */
    suspend fun refresh(c: Context, force: Boolean = false) {
        val p = prefs(c)
        if (!force && System.currentTimeMillis() - p.getLong("checked", 0L) < DAY_MS) return
        withContext(Dispatchers.IO) { offline = runCatching { fetch(c, p) }.isFailure }
    }

    private fun fetch(c: Context, p: SharedPreferences) {
        val cn = openHttps(StoreInstaller.resolveUrl(c, EXTRAS_MANIFEST_URL))
        try {
            val etag = p.getString("etag", "").orEmpty()
            if (etag.isNotEmpty()) cn.setRequestProperty("If-None-Match", etag)
            val code = cn.responseCode
            val now = System.currentTimeMillis()
            when (code) {
                200 -> {
                    val text = cn.inputStream.bufferedReader().use { it.readText() }
                    require(parseManifest(text).isNotEmpty()) { "empty manifest" }
                    cacheFile(c).apply { parentFile?.mkdirs(); writeText(text) }
                    p.edit().putString("etag", cn.getHeaderField("ETag").orEmpty()).putLong("checked", now).apply()
                }
                304 -> p.edit().putLong("checked", now).apply()
                else -> error("HTTP $code")
            }
        } finally {
            cn.disconnect()
        }
    }
}
