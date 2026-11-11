package com.umutk.pulse

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import com.umutk.pulse.features.Downloads
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Inflater

/** ArtCraft: the Android side of the PC launcher. The apps come from the newest release of MostafaXS1/artcraft-android (third-party builds of the Storytold apps). Icons are read straight from each APK over HTTP (no full download), a download puts the APK in Download/ArtCraft, and the installed app is found by its package name. */
object ArtCraft {
    const val API = "https://api.github.com/repos/MostafaXS1/artcraft-android/releases"
    const val PKG_PREFIX = "ai.storyteller."   // the builder's default package name (see the repo README)

    class Item(val app: String, val file: String, val url: String, val size: Long, val updated: Long = 0L)
    enum class State { NONE, INSTALL, UPDATE, INSTALLED }

    var tag by mutableStateOf("")
    var status by mutableStateOf("")
    /** The release notes of the shown release (the same text for every app of it). */
    var notes by mutableStateOf("")
    /** Bumped when the screen comes back (after an install the states must be read again). */
    var tick by mutableIntStateOf(0)
    val items = mutableStateListOf<Item>()
    val progress = mutableStateMapOf<String, Int>()   // app -> percent while it downloads
    val icons = mutableStateMapOf<String, ImageBitmap>()
    val colors = mutableStateMapOf<String, Color>()   // app -> colour taken from its icon

    private val main = Handler(Looper.getMainLooper())
    private fun ui(block: () -> Unit) = main.post(block)

    fun dir(c: Context) = File(c.filesDir, "artcraft").apply { mkdirs() }
    fun shared(): File = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "ArtCraft").apply { mkdirs() }
    // "cadcraft-release.apk" and "cadcraft-0.9.0.apk" are both the app "Cadcraft"
    fun appName(file: String) = file.substringBefore("-").replaceFirstChar { it.uppercase() }
    fun pkgOf(item: Item) = PKG_PREFIX + item.app.lowercase()

    /** Download mode: "apk" = a separate APK in Download/ArtCraft (default), "pulse" = Pulse's own download manager. */
    var mode by mutableStateOf("apk")

    /** The APK already on the phone, wherever it was put. Returns the expected path when none exists yet. */
    fun fileFor(c: Context, item: Item): File {
        val downloads = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
        return listOf(File(shared(), item.file), File(dir(c), item.file), File(downloads, item.file)).firstOrNull { it.exists() }
            ?: File(if (mode == "apk") shared() else dir(c), item.file)
    }

    private fun getText(url: String): String {
        val con = URL(url).openConnection() as HttpURLConnection
        con.setRequestProperty("Accept", "application/vnd.github+json")
        con.connectTimeout = 15000; con.readTimeout = 30000
        return con.inputStream.bufferedReader().use { it.readText() }
    }

    /**
     * The newest release read from github.com without the API: /releases/latest leads to the tag, and the tag's asset list (expanded_assets) holds the APK links;
     * the size of each comes from a HEAD request. Returned in the API's shape.
     */
    private fun fromPage(): JSONObject {
        val repo = API.removePrefix("https://api.github.com/repos/").removeSuffix("/releases")
        val latest = URL("https://github.com/$repo/releases/latest").openConnection() as HttpURLConnection
        latest.instanceFollowRedirects = true; latest.connectTimeout = 15000; latest.readTimeout = 30000
        latest.inputStream.close()
        val tag = latest.url.path.substringAfter("/releases/tag/", "").takeIf { it.isNotEmpty() } ?: throw IllegalStateException("no release")
        val html = (URL("https://github.com/$repo/releases/expanded_assets/$tag").openConnection() as HttpURLConnection).apply { connectTimeout = 15000; readTimeout = 30000 }.inputStream.bufferedReader().use { it.readText() }
        val links = Regex("""href="(/$repo/releases/download/[^"]+\.apk)"""").findAll(html).map { "https://github.com" + it.groupValues[1] }.distinct().toList()
        if (links.isEmpty()) throw IllegalStateException("no APK in $tag")
        val assets = JSONArray()
        for (u in links) {
            val size = runCatching { (URL(u).openConnection() as HttpURLConnection).run { requestMethod = "HEAD"; instanceFollowRedirects = true; connectTimeout = 15000; com.umutk.pulse.Compat.contentLength(this).also { disconnect() } } }.getOrDefault(0L)
            assets.put(JSONObject().put("name", java.net.URLDecoder.decode(u.substringAfterLast('/'), "UTF-8")).put("browser_download_url", u).put("size", size))
        }
        return JSONObject().put("tag_name", java.net.URLDecoder.decode(tag, "UTF-8")).put("body", "").put("assets", assets)
    }

    /** Reads the newest release that has APKs, fills the list, and fetches the icon of every APK over HTTP. */
    fun refresh(c: Context) {
        Thread {
            try {
                // the API first; when it fails (404 or a rate limit, it happens without a token) the release page itself is read
                val rel: JSONObject = runCatching {
                    val arr = JSONArray(getText(API))
                    (0 until arr.length()).map { arr.getJSONObject(it) }.firstOrNull { (it.optJSONArray("assets")?.length() ?: 0) > 0 } ?: throw IllegalStateException("no release with APKs")
                }.getOrElse { first -> runCatching { fromPage() }.getOrElse { throw first } }
                val assets = rel.getJSONArray("assets")
                // a release can ship two .apk builds of the same app (e.g. a split-ABI build next to a universal one); keep the larger, it is the fuller build
                val list = (0 until assets.length()).map { assets.getJSONObject(it) }
                    .filter { it.optString("name").endsWith(".apk") }
                    .map { Item(appName(it.getString("name")), it.getString("name"), it.getString("browser_download_url"), it.optLong("size"), runCatching { java.time.Instant.parse(it.optString("updated_at")).toEpochMilli() }.getOrDefault(0L)) }
                    .sortedByDescending { it.size }.distinctBy { it.app }
                ui {
                    items.clear(); items.addAll(list); tag = rel.optString("tag_name"); notes = rel.optString("body"); status = ""
                    list.forEach { item -> val f = fileFor(c, item); if (f.exists()) readIcon(c, f, item.app) }
                }
                // the original icons ship inside the app (assets/artcraft, from each Storytold repo); the APK is only read when one is missing
                list.forEach { item ->
                    val bmp = bundledIcon(c, item.app)
                    if (bmp != null) { val col = dominant(bmp); ui { icons[item.app] = bmp.asImageBitmap(); colors[item.app] = col } }
                    else if (!fileFor(c, item).exists()) fetchIcon(c, item)
                }
            } catch (e: Exception) {
                ui { status = tr("Could not read the release list: ", "Sürüm listesi okunamadı: ") + (e.message ?: "") }
            }
        }.start()
    }

    private fun bundledIcon(c: Context, app: String): android.graphics.Bitmap? =
        try { c.assets.open("artcraft/${app.lowercase()}.png").use { BitmapFactory.decodeStream(it) } } catch (e: Exception) { null }

    /** Downloads one APK; runs on the calling thread. */
    fun downloadBlocking(c: Context, item: Item) {
        if (mode == "pulse") { ui { status = tr("Sent to Pulse Downloads: ", "Pulse indirmelerine gönderildi: ") + item.app }; Downloads.add(c, item.url); return }
        val f = File(if (mode == "apk") shared() else dir(c), item.file)
        ui { progress[item.app] = 0 }
        try {
            val con = URL(item.url).openConnection() as HttpURLConnection
            con.instanceFollowRedirects = true; con.connectTimeout = 15000; con.readTimeout = 30000
            val total = com.umutk.pulse.Compat.contentLength(con).takeIf { it > 0 } ?: item.size
            var done = 0L
            con.inputStream.use { inp ->
                f.outputStream().use { out ->
                    val buf = ByteArray(65536)
                    while (true) {
                        val n = inp.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n); done += n
                        val pct = if (total > 0) (done * 100 / total).toInt().coerceIn(0, 99) else 0
                        ui { progress[item.app] = pct }
                    }
                }
            }
            ui { progress.remove(item.app) }
            readIcon(c, f, item.app)
        } catch (e: Exception) {
            f.delete()
            ui { progress.remove(item.app); status = tr("Download failed: ", "İndirme başarısız: ") + item.app + " (" + (e.message ?: "") + ")" }
        }
    }

    fun download(c: Context, item: Item) = Thread { downloadBlocking(c, item) }.start()

    /** The icon the APK carries, read from a downloaded file. */
    fun readIcon(c: Context, f: File, app: String) {
        try {
            val pm = c.packageManager
            val info = pm.getPackageArchiveInfo(f.path, 0)?.applicationInfo ?: return
            info.sourceDir = f.path; info.publicSourceDir = f.path
            val raw = info.loadIcon(pm).toBitmap(192, 192)
            val col = dominant(raw)
            ui { icons[app] = raw.asImageBitmap(); colors[app] = col }
        } catch (e: Exception) { }
    }

    // ---- icon without downloading the APK: the zip directory and one PNG are read with HTTP range requests ----

    /** Downloads the whole APK (once) and reads its icon the way the system does, which covers the icons the quick reads cannot find. */
    private fun fullIcon(c: Context, item: Item) {
        try {
            val f = fileFor(c, item)
            if (!f.exists() || f.length() == 0L) {
                f.parentFile?.mkdirs()
                (java.net.URL(item.url).openConnection() as java.net.HttpURLConnection).apply { connectTimeout = 15000; readTimeout = 60000; instanceFollowRedirects = true }
                    .inputStream.use { ins -> f.outputStream().use { ins.copyTo(it) } }
            }
            readIcon(c, f, item.app)
        } catch (e: Exception) { }
    }

    private fun ranged(url: String, from: Long, to: Long): ByteArray {
        val con = URL(url).openConnection() as HttpURLConnection
        con.instanceFollowRedirects = true; con.connectTimeout = 15000; con.readTimeout = 30000
        con.setRequestProperty("Range", "bytes=$from-$to")
        if (con.responseCode != 206) throw IllegalStateException("no range support (${con.responseCode})")
        return con.inputStream.use { it.readBytes() }
    }

    /** The icon of an app that has no bundled one: a plain launcher PNG is read from the APK over HTTP; when there is none (adaptive or vector icons), the APK is downloaded and its icon read. */
    fun fetchIcon(c: Context, item: Item) {
        Thread {
            try {
                if (item.size <= 0) { fullIcon(c, item); return@Thread }
                val tailLen = minOf(65557L, item.size)
                val tail = ranged(item.url, item.size - tailLen, item.size - 1)
                val tb = ByteBuffer.wrap(tail).order(ByteOrder.LITTLE_ENDIAN)
                var e = tail.size - 22
                while (e >= 0 && tb.getInt(e) != 0x06054b50) e--
                if (e < 0) { fullIcon(c, item); return@Thread }
                val cdSize = tb.getInt(e + 12).toLong() and 0xffffffffL
                val cdOff = tb.getInt(e + 16).toLong() and 0xffffffffL
                val cd = ranged(item.url, cdOff, cdOff + cdSize - 1)
                val cb = ByteBuffer.wrap(cd).order(ByteOrder.LITTLE_ENDIAN)
                // the launcher icon, the densest bitmap that exists wins
                val want = listOf("xxxhdpi", "xxhdpi", "xhdpi", "hdpi", "mdpi")
                var best: Triple<Int, Long, IntArray>? = null   // rank, local header offset, (method, compressed, uncompressed)
                var p = 0
                while (p + 46 <= cd.size && cb.getInt(p) == 0x02014b50) {
                    val method = cb.getShort(p + 10).toInt() and 0xffff
                    val comp = cb.getInt(p + 20).toInt()
                    val uncomp = cb.getInt(p + 24).toInt()
                    val nl = cb.getShort(p + 28).toInt() and 0xffff
                    val el = cb.getShort(p + 30).toInt() and 0xffff
                    val cl = cb.getShort(p + 32).toInt() and 0xffff
                    val loc = cb.getInt(p + 42).toLong() and 0xffffffffL
                    val name = String(cd, p + 46, nl, Charsets.UTF_8)
                    val dens = want.indexOfFirst { name.startsWith("res/mipmap-$it") }
                    if (dens >= 0 && name.endsWith("/ic_launcher.png")) {
                        val rank = dens
                        if (best == null || rank < best.first) best = Triple(rank, loc, intArrayOf(method, comp, uncomp))
                    }
                    p += 46 + nl + el + cl
                }
                val (_, loc, info) = best ?: run { fullIcon(c, item); return@Thread }
                val lh = ranged(item.url, loc, loc + 29)
                val lb = ByteBuffer.wrap(lh).order(ByteOrder.LITTLE_ENDIAN)
                val start = loc + 30 + (lb.getShort(26).toInt() and 0xffff) + (lb.getShort(28).toInt() and 0xffff)
                val data = ranged(item.url, start, start + info[1] - 1)
                val png = if (info[0] == 8) {
                    val inf = Inflater(true); inf.setInput(data)
                    val out = ByteArray(info[2]); var n = 0
                    while (n < out.size && !inf.finished()) { val k = inf.inflate(out, n, out.size - n); if (k == 0 && (inf.finished() || inf.needsInput())) break; n += k }
                    inf.end(); out.copyOf(n)
                } else data
                val raw = BitmapFactory.decodeByteArray(png, 0, png.size) ?: run { fullIcon(c, item); return@Thread }
                val col = dominant(raw)
                ui { icons[item.app] = raw.asImageBitmap(); colors[item.app] = col }
            } catch (e: Exception) { }
        }.start()
    }

    /** The colour the icon is mostly made of, ignoring white, black and grey (backgrounds and outlines). */
    private fun dominant(bmp: android.graphics.Bitmap): Color {
        var r = 0L; var g = 0L; var b = 0L; var n = 0L
        for (x in 0 until bmp.width step 6) for (y in 0 until bmp.height step 6) {
            val p = bmp.getPixel(x, y)
            if (android.graphics.Color.alpha(p) < 200) continue
            val rr = android.graphics.Color.red(p); val gg = android.graphics.Color.green(p); val bb = android.graphics.Color.blue(p)
            if (maxOf(rr, gg, bb) - minOf(rr, gg, bb) < 40) continue
            r += rr; g += gg; b += bb; n++
        }
        return if (n == 0L) Color(0xFF9E9E9E) else Color(red = r / n / 255f, green = g / n / 255f, blue = b / n / 255f)
    }

    /** Layout settings: "square" (grid of square tiles, the default) or "list" (wide cards with a description); columns for the grid; details on/off. */
    var view by mutableStateOf("square")
    var columns by mutableStateOf(2)
    var details by mutableStateOf(true)
    /** Background: false = the look's solid colour (default), true = a darker see-through layer over the window. */
    var translucent by mutableStateOf(false)
    private fun prefs(c: Context) = c.getSharedPreferences("artcraft", Context.MODE_PRIVATE)
    fun load(c: Context) {
        val p = prefs(c)
        view = p.getString("view", "square") ?: "square"; columns = p.getInt("columns", 2); details = p.getBoolean("details", true)
        mode = p.getString("mode", "apk") ?: "apk"; translucent = p.getBoolean("translucent", false)
    }
    fun saveLayout(c: Context) {
        prefs(c).edit().putString("view", view).putInt("columns", columns).putBoolean("details", details)
            .putString("mode", mode).putBoolean("translucent", translucent).apply()
    }

    /** The APK on the phone is the release's own build, not an older one saved under the same name (a new release often keeps its file names). */
    private fun fileIsCurrent(f: File, item: Item) = f.exists() && (item.updated <= 0L || f.lastModified() + 120_000L >= item.updated)

    /**
     * The state of an app: its APK is on the phone and/or the app is installed (found by package name). An installed app is "Update available" when the release was
     * published after the app was last installed or updated, even if the new APK is not downloaded yet.
     */
    fun stateOf(c: Context, item: Item): State {
        val pm = c.packageManager
        val f = fileFor(c, item)
        val fileNew = fileIsCurrent(f, item)
        val pkg = if (f.exists()) (pm.getPackageArchiveInfo(f.path, 0)?.packageName ?: pkgOf(item)) else pkgOf(item)
        val pi = try { pm.getPackageInfo(pkg, 0) } catch (e: Exception) { null }
        val installed = pi?.let { com.umutk.pulse.Compat.longVersion(it) } ?: -1L
        if (installed < 0) return if (fileNew) State.INSTALL else State.NONE
        val archive = if (fileNew) pm.getPackageArchiveInfo(f.path, 0)?.let { com.umutk.pulse.Compat.longVersion(it) } ?: installed else installed
        if (archive > installed) return State.UPDATE
        return if (item.updated > 0L && pi != null && item.updated > pi.lastUpdateTime + 120_000L) State.UPDATE else State.INSTALLED
    }

    /** Hands the APK to the system installer (the same path Pulse's file manager uses). */
    fun install(c: Context, f: File) {
        val uri = FileProvider.getUriForFile(c, "${c.packageName}.files", f)
        c.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun open(c: Context, item: Item) {
        c.packageManager.getLaunchIntentForPackage(pkgOf(item))?.let { c.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /** The installed version name, or the one in the downloaded APK when nothing is installed. */
    fun versionOf(c: Context, item: Item): String? {
        val pm = c.packageManager
        return try { pm.getPackageInfo(pkgOf(item), 0).versionName } catch (e: Exception) {
            fileFor(c, item).takeIf { it.exists() }?.let { pm.getPackageArchiveInfo(it.path, 0)?.versionName }
        }
    }

    /** The trash button: removes the downloaded APK and, when the app is installed, opens its uninstall screen. */
    fun trash(c: Context, item: Item) {
        fileFor(c, item).takeIf { it.exists() }?.delete()
        val pkg = pkgOf(item)
        val installed = try { c.packageManager.getPackageInfo(pkg, 0); true } catch (e: Exception) { false }
        if (installed) c.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        ui { status = tr("Removed ", "Silindi: ") + item.app }
    }

    /** One tap: download, then install or update, then open. */
    fun tap(c: Context, item: Item) {
        val f = fileFor(c, item)
        when (stateOf(c, item)) {
            State.NONE -> download(c, item)
            State.INSTALL -> install(c, f)
            // an update whose file is the release's own is installed; otherwise the new build is downloaded first (then the same button installs it)
            State.UPDATE -> if (fileIsCurrent(f, item)) install(c, f) else download(c, item)
            State.INSTALLED -> open(c, item)
        }
    }
}
