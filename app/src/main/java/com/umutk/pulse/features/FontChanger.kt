package com.umutk.pulse.features

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import com.umutk.pulse.Busy
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import com.umutk.pulse.PChip
import com.umutk.pulse.PField
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.android.apksig.ApkSigner
import com.umutk.pulse.PButton
import com.umutk.pulse.PCard
import com.umutk.pulse.PHead
import com.umutk.pulse.T
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * System font without root, ZFont style: the font becomes a small signed APK (package com.monotype.android.font.pulsefont,
 * the FlipFont naming Samsung's font list scans for) that goes through [Installer]; the user then picks it in Android's own font settings.
 * Nothing in /system is touched. One pack at a time: a new font replaces the previous pack.
 * UNVERIFIED: whether a given vendor's font list lists this package (newer Samsung One UI and others may ignore third-party packs).
 */
object FontPack {
    const val PKG = "com.monotype.android.font.pulsefont"

    /** Template APK (manifest only, assets/fontpack_template.apk) + [ttf] as assets/fonts/font.ttf, signed with the bundled key (assets/fontpack.p12). */
    fun build(c: Context, ttf: File): File {
        val dir = File(c.cacheDir, "installer").apply { mkdirs() }
        val raw = File(dir, "fontpack_raw.apk")
        ZipOutputStream(raw.outputStream()).use { z ->
            ZipFile(copyAsset(c, "fontpack_template.apk", File(c.cacheDir, "fp_tpl.apk"))).use { zf ->
                zf.entries().asSequence().forEach { e ->
                    z.putNextEntry(ZipEntry(e.name))
                    val bytes = zf.getInputStream(e).use { it.readBytes() }
                    z.write(if (e.name == "AndroidManifest.xml") withVersion(bytes) else bytes); z.closeEntry()
                }
            }
            z.putNextEntry(ZipEntry("assets/fonts/font.ttf")); ttf.inputStream().use { it.copyTo(z) }; z.closeEntry()
        }
        val ks = KeyStore.getInstance("PKCS12").apply { c.assets.open("fontpack.p12").use { load(it, "pulsefont".toCharArray()) } }
        val key = ks.getKey("k", "pulsefont".toCharArray()) as PrivateKey
        val cert = ks.getCertificate("k") as X509Certificate
        val out = File(dir, "pulse_font.apk")
        ApkSigner.Builder(listOf(ApkSigner.SignerConfig.Builder("pulse", key, listOf(cert)).build()))
            .setInputApk(raw).setOutputApk(out).setMinSdkVersion(31)
            .setV1SigningEnabled(true).setV2SigningEnabled(true).build().sign()
        raw.delete()
        return out
    }

    /** The template carries versionCode 0x11223344 as a marker; every pack gets the current time so a new font always counts as an upgrade. */
    private fun withVersion(b: ByteArray): ByteArray {
        val m = byteArrayOf(0x44, 0x33, 0x22, 0x11)
        val i = (0..b.size - 4).firstOrNull { k -> (0..3).all { b[k + it] == m[it] } } ?: return b
        val v = (System.currentTimeMillis() / 1000).toInt()
        return b.copyOf().also { for (j in 0..3) it[i + j] = (v shr (8 * j)).toByte() }
    }

    private fun copyAsset(c: Context, name: String, to: File): File { c.assets.open(name).use { i -> to.outputStream().use { i.copyTo(it) } }; return to }

    /** Hands the pack to Pulse's own install window. */
    fun install(c: Context, apk: File) {
        val uri = FileProvider.getUriForFile(c, c.packageName + ".files", apk)
        c.startActivity(Intent(Intent.ACTION_VIEW).setClass(c, InstallActivity::class.java)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun installed(c: Context): Boolean = Installer.installedCode(c, PKG) != null

    /** Opens the phone's font style screen: vendor screens first (UNVERIFIED names), then Display settings, then Settings. */
    fun openSettings(c: Context) {
        val tries = listOf(
            Intent().setComponent(ComponentName("com.android.settings", "com.samsung.android.settings.display.SecFontStyleActivity")),
            Intent().setComponent(ComponentName("com.android.settings", "com.android.settings.Settings\$FontSettingsActivity")),
            Intent("miui.intent.action.FONT_SETTINGS"),
            Intent(Settings.ACTION_DISPLAY_SETTINGS), Intent(Settings.ACTION_SETTINGS),
        )
        for (i in tries) {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (i.resolveActivity(c.packageManager) != null && runCatching { c.startActivity(i) }.isSuccess) return
        }
    }
}

/** A font offered by a [FontSource]. [url] is a TTF/OTF file. */
data class FontItem(val name: String, val author: String, val url: String)

/**
 * Where the browse list comes from. ZFont 3 has no public API and none could be verified, so the only source is a
 * user-set index URL: a JSON array of {"name","author","url"} objects. Add another source by implementing this.
 */
fun interface FontSource { fun list(): List<FontItem> }

/**
 * zFont 3's own community fonts. VERIFIED by curl (2026-10-15): the app's public index repo github.com/zFont/main holds one JSON per category
 * ([{"n" name,"a" author,"u" "/Fonts/<cat>/<file>.ttf","s" size,"t" thumbnail}]) and the files sit in github.com/zFont/Host under /Font/. No API key.
 */
class ZFontSource(private val category: String = "Stylish") : FontSource {
    override fun list(): List<FontItem> {
        val a = JSONArray(httpGet("https://raw.githubusercontent.com/zFont/main/main/${Uri.encode(category)}.json").toString(Charsets.UTF_8))
        return (0 until a.length()).map { a.getJSONObject(it) }.filter { it.optString("u").endsWith(".ttf", true) }.map {
            val path = it.getString("u").replaceFirst("/Fonts/", "/Font/").split("/").joinToString("/") { seg -> Uri.encode(seg) }
            FontItem(it.getString("n"), it.optString("a"), "https://raw.githubusercontent.com/zFont/Host/main$path")
        }
    }
}

class JsonIndexSource(private val indexUrl: String) : FontSource {
    override fun list(): List<FontItem> {
        val a = JSONArray(httpGet(indexUrl).toString(Charsets.UTF_8))
        return (0 until a.length()).map { a.getJSONObject(it) }.map { FontItem(it.getString("name"), it.optString("author"), it.getString("url")) }
    }
}

private fun httpGet(url: String): ByteArray {
    val u = URL(url)
    require(u.protocol == "https") { "https only" }
    val cn = u.openConnection() as HttpURLConnection
    cn.connectTimeout = 10000; cn.readTimeout = 20000
    return cn.inputStream.use { it.readBytes() }
}

@Composable
fun FontChangerScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { ctx.getSharedPreferences("p", Context.MODE_PRIVATE) }
    var note by remember { mutableStateOf("") }
    var have by remember { mutableStateOf(false) }
    var items by remember { mutableStateOf<List<FontItem>>(emptyList()) }
    var category by remember { mutableStateOf("Stylish") }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    // fonts fetched for preview: name -> (family, cached file); every font keeps its own file so many can be shown at once
    val loaded = remember { androidx.compose.runtime.mutableStateMapOf<String, Pair<FontFamily, File>>() }
    val busy = remember { androidx.compose.runtime.mutableStateListOf<String>() }
    LaunchedEffect(Unit) { have = withContext(Dispatchers.IO) { FontPack.installed(ctx) } }
    LaunchedEffect(category) {
        val u = prefs.getString("font_index", "").orEmpty() // optional custom index (JSON [{name,author,url}]) instead of zFont's
        loading = true; items = emptyList()
        items = withContext(Dispatchers.IO) { runCatching { (if (u.isNotBlank()) JsonIndexSource(u) else ZFontSource(category)).list() }.getOrDefault(emptyList()) }
        loading = false
    }
    /** Downloads [f] once and keeps its typeface for the preview. */
    fun fetch(f: FontItem) {
        if (loaded.containsKey(f.url) || f.url in busy) return
        busy.add(f.url)
        scope.launch {
            val file = withContext(Dispatchers.IO) {
                runCatching { File(File(ctx.cacheDir, "browse").apply { mkdirs() }, "${f.url.hashCode()}.ttf").also { if (it.length() == 0L) it.writeBytes(httpGet(f.url)) } }.getOrNull()
            }
            val tf = file?.let { runCatching { Typeface.createFromFile(it) }.getOrNull() }
            if (tf != null) loaded[f.url] = FontFamily(tf) to file else note = tr("Download failed", "İndirilemedi")
            busy.remove(f.url)
        }
    }
    /** Packs [ttf] and opens the install window. */
    fun pack(ttf: File) = scope.launch {
        note = withContext(Dispatchers.IO) {
            runCatching { FontPack.install(ctx, FontPack.build(ctx, ttf)); "" }.getOrElse { tr("Failed: ", "Olmadı: ") + (it.message ?: it.javaClass.simpleName) }
        }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            val f = withContext(Dispatchers.IO) { File(ctx.cacheDir, "picked_font.ttf").also { f -> ctx.contentResolver.openInputStream(uri)?.use { i -> f.outputStream().use { i.copyTo(it) } } } }
            if (runCatching { Typeface.createFromFile(f) }.isSuccess) pack(f) else note = tr("Not a font file", "Yazı tipi dosyası değil")
        }
    }
    // a search looks through every category (an "iOS" font lives under Emoji, not Stylish): all lists are fetched once, on the first search
    var everything by remember { mutableStateOf<List<FontItem>>(emptyList()) }
    LaunchedEffect(query.isNotBlank()) {
        if (query.isNotBlank() && everything.isEmpty()) {
            loading = true
            everything = withContext(Dispatchers.IO) { FONT_CATEGORIES.flatMap { (id, _) -> runCatching { ZFontSource(id).list() }.getOrDefault(emptyList()) }.distinctBy { it.url } }
            loading = false
        }
    }
    val shown = (if (query.isBlank()) items else everything).filter { query.isBlank() || it.name.contains(query, true) || it.author.contains(query, true) }
    // lazy: a font's preview is fetched only when its card scrolls into view
    androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { PHead(tr("System font", "Sistem yazı tipi"), guide = "fonts") }
        item {
            PCard {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PButton("TTF", icon = Icons.Outlined.FolderOpen) { pick.launch(arrayOf("font/ttf", "font/otf", "font/*", "application/x-font-ttf", "application/octet-stream")) }
                    PButton(tr("Font style", "Yazı tipi"), icon = Icons.Outlined.Settings) { FontPack.openSettings(ctx) }
                    if (have) PButton(tr("Remove", "Kaldır"), danger = true) { ctx.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${FontPack.PKG}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
                if (note.isNotEmpty()) Text(note, color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
        item { PField(query, { query = it }, tr("Search fonts", "Yazı tipi ara"), Modifier.fillMaxWidth()) }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FONT_CATEGORIES.forEach { (id, label) -> PChip(category == id, label) { category = id } }
            }
        }
        if (loading) item { Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = androidx.compose.ui.Alignment.Center) { Busy() } }
        else if (shown.isEmpty()) item { Text(tr("No fonts (no connection?)", "Yazı tipi yok (bağlantı?)"), color = T.sub, fontSize = 12.sp) }
        items(shown, key = { it.url }) { f ->
            LaunchedEffect(f.url) { fetch(f) }
            val got = loaded[f.url]
            PCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(f.name, color = T.text, fontSize = 15.sp, fontFamily = got?.first)
                        if (f.author.isNotBlank()) Text(f.author, color = T.sub, fontSize = 12.sp)
                        // the preview: text in the font, then emoji (the font's own when it has them, the phone's otherwise)
                        if (got != null) {
                            Text("The quick brown fox jumps over the lazy dog\nÇçĞğİıÖöŞşÜü 0123456789", color = T.text, fontSize = 18.sp, fontFamily = got.first, modifier = Modifier.padding(top = 6.dp))
                            Text("😀 😍 🎉 👍 ❤️ 🔥 🌍 ⭐ 🍕 🐱", color = T.text, fontSize = 20.sp, fontFamily = got.first, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                    if (got != null) PButton("", icon = Icons.Outlined.Download) { pack(got.second) }
                    else if (f.url in busy) Busy(modifier = Modifier.width(40.dp))
                }
            }
        }
    }
}

/** zFont's category files (github.com/zFont/main). */
private val FONT_CATEGORIES = listOf(
    "Stylish" to "Stylish", "Emoji" to "Emoji", "Color" to "Color", "Russia" to "Russian", "Ukrainian" to "Ukrainian", "Vietnamese" to "Vietnamese",
    "Thai" to "Thai", "Arabic" to "Arabic", "Kurdish" to "Kurdish", "Urdu" to "Urdu", "Chinese" to "Chinese", "Korean" to "Korean", "Khmer" to "Khmer",
    "Lao" to "Lao", "Myanmar" to "Myanmar", "Bengali" to "Bengali", "Shan" to "Shan",
)
