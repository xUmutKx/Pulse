package com.umutk.pulse.extras.store

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.StatFs
import android.widget.Toast
import androidx.core.content.FileProvider
import com.umutk.pulse.features.Downloads
import com.umutk.pulse.tr
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipInputStream

enum class ModuleState { NOT_INSTALLED, DOWNLOADING, INSTALLED, UPDATE, FAILED }

/** Downloads, checks (sha256, and the signature for apk) and installs or removes store modules. */
object StoreInstaller {
    /** Download progress 0..1 per module id; an id is here while its download runs. */
    val progress = MutableStateFlow<Map<String, Float>>(emptyMap())

    /** Last error per module id, shown on its row until the next try. */
    val errors = MutableStateFlow<Map<String, String>>(emptyMap())

    /** Bumped on every change so the screen re-reads states. */
    val rev = MutableStateFlow(0)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<String, Job>()
    private const val EXTRA_LIMIT = 1_048_576L   // bytes a download may exceed its listed size by

    private fun prefs(c: Context) = c.getSharedPreferences("extra_store", Context.MODE_PRIVATE)

    /** What was unzipped for a "veri" module: version, hash and bytes on disk. */
    private data class Rec(val surum: String, val sha: String, val bytes: Long)

    private fun record(c: Context, id: String): Rec? =
        prefs(c).getString("rec_$id", null)?.split('|')?.takeIf { it.size == 3 }?.let { Rec(it[0], it[1], it[2].toLongOrNull() ?: 0L) }

    private fun saveRecord(c: Context, id: String, r: Rec?) {
        val e = prefs(c).edit()
        if (r == null) e.remove("rec_$id") else e.putString("rec_$id", "${r.surum}|${r.sha}|${r.bytes}")
        e.apply()
    }

    /** Settings: 0 GitHub (raw), 1 jsDelivr mirror of the same repo. */
    fun source(c: Context) = prefs(c).getInt("src", 0)
    fun setSource(c: Context, v: Int) = prefs(c).edit().putInt("src", v).putLong("checked", 0L).apply()

    /** Settings: where unzipped modules live, 0 app storage on the shared volume, 1 private internal storage. */
    fun location(c: Context) = prefs(c).getInt("loc", 0)

    fun autoAll(c: Context) = prefs(c).getBoolean("auto", false)
    fun setAutoAll(c: Context, v: Boolean) = prefs(c).edit().putBoolean("auto", v).apply()

    /** Per extra: follows the global auto-update switch unless turned off here. */
    fun autoOne(c: Context, id: String) = prefs(c).getBoolean("auto_$id", true)
    fun setAutoOne(c: Context, id: String, v: Boolean) = prefs(c).edit().putBoolean("auto_$id", v).apply()

    /** Per extra: keep the downloaded file in cache after install (default off). */
    fun keepFile(c: Context, id: String) = prefs(c).getBoolean("keep_$id", false)
    fun setKeepFile(c: Context, id: String, v: Boolean) = prefs(c).edit().putBoolean("keep_$id", v).apply()

    /** raw.githubusercontent.com/<user>/<repo>/<ref>/<path> becomes the jsDelivr form when that source is chosen. */
    fun resolveUrl(c: Context, url: String): String {
        if (source(c) != 1) return url
        val m = Regex("^https://raw\\.githubusercontent\\.com/([^/]+)/([^/]+)/([^/]+)/(.+)$").find(url) ?: return url
        return "https://cdn.jsdelivr.net/gh/${m.groupValues[1]}/${m.groupValues[2]}@${m.groupValues[3]}/${m.groupValues[4]}"
    }

    private fun baseDir(c: Context, loc: Int) = if (loc == 1) c.filesDir else (c.getExternalFilesDir(null)?.parentFile ?: c.filesDir)
    fun dataDir(c: Context, id: String): File = File(baseDir(c, location(c)), "extras/$id")

    /** Moves every unzipped module to the chosen place; a module that fails to copy stays where it was. */
    fun setLocation(c: Context, loc: Int) {
        val from = File(baseDir(c, location(c)), "extras")
        prefs(c).edit().putInt("loc", loc).apply()
        val to = File(baseDir(c, loc), "extras")
        if (from.canonicalPath == to.canonicalPath || !from.isDirectory) return
        from.listFiles()?.filter { it.isDirectory }?.forEach { d ->
            val dst = File(to, d.name)
            if (runCatching { dst.deleteRecursively(); d.copyRecursively(dst, true) }.getOrDefault(false)) d.deleteRecursively()
        }
        rev.update { it + 1 }
    }

    fun cacheBytes(c: Context) = File(c.cacheDir, "installer").walkTopDown().filter { it.isFile }.sumOf { it.length() }

    /** Deletes downloaded installer files (not running downloads); returns the bytes freed. */
    fun clearCache(c: Context): Long {
        val busy = progress.value.keys
        var n = 0L
        File(c.cacheDir, "installer").listFiles()?.forEach { f ->
            if (busy.none { f.name.startsWith("store-$it.") }) { n += f.length(); f.delete() }
        }
        return n
    }

    /** Starts updates for extras whose switch is on. Only unzipped data modules go by themselves: an apk still needs the system installer tap. */
    fun autoUpdate(c: Context, modules: List<ExtraModule>) {
        if (!autoAll(c)) return
        updates(c, modules).filter { !it.module.isApk && autoOne(c, it.id) }.forEach { download(c, it.module) }
    }

    /** Wipes one data module's files (kept installed record removed); apks are removed by the system. */
    fun clearData(c: Context, m: ExtraModule) { if (!m.isApk) remove(c, m) }

    fun packageVersion(c: Context, pkg: String): String? =
        try { c.packageManager.getPackageInfo(pkg, 0).versionName.orEmpty() } catch (e: PackageManager.NameNotFoundException) { null }

    /** Installed version of a module, or null when it is not installed. */
    fun installedVersion(c: Context, m: ExtraModule): String? =
        if (m.isApk) m.paket?.let { packageVersion(c, it) } else record(c, m.id)?.surum

    fun installedBytes(c: Context, m: ExtraModule): Long {
        if (!m.isApk) return record(c, m.id)?.bytes ?: 0L
        val pkg = m.paket ?: return 0L
        return try { File(c.packageManager.getApplicationInfo(pkg, 0).sourceDir).length() } catch (e: PackageManager.NameNotFoundException) { 0L }
    }

    /** Packages the store put on the device (SharedPreferences set); the app list hides them. */
    fun isStoreInstalled(c: Context, pkg: String): Boolean =
        pkg.startsWith(EXTRA_MODULE_PREFIX) || pkg in prefs(c).getStringSet("store_pkgs", emptySet()).orEmpty()

    private fun markStorePkg(c: Context, pkg: String) {
        val p = prefs(c)
        p.edit().putStringSet("store_pkgs", p.getStringSet("store_pkgs", emptySet()).orEmpty() + pkg).apply()
    }

    /** One pending update: module id, installed version, version in the manifest. */
    data class UpdateInfo(val id: String, val installed: String, val available: String, val module: ExtraModule)

    /** Installed modules (store ones and apps the owner already had, matched by package) with a newer manifest version. */
    fun updates(c: Context, modules: List<ExtraModule>): List<UpdateInfo> = modules.mapNotNull { m ->
        val inst = installedVersion(c, m) ?: return@mapNotNull null
        if (compareVersions(m.surum, inst) > 0) UpdateInfo(m.id, inst, m.surum, m) else null
    }

    /** One tap for all: every update goes through the normal download flow. */
    fun updateAll(c: Context, modules: List<ExtraModule>) = updates(c, modules).forEach { download(c, it.module) }

    /** Download size of everything listed (for the overall size line). */
    fun totalBytes(modules: List<ExtraModule>): Long = modules.sumOf { it.boyut }

    /** Bytes on disk of every installed module in the list. */
    fun installedBytes(c: Context, modules: List<ExtraModule>): Long =
        modules.filter { installedVersion(c, it) != null }.sumOf { installedBytes(c, it) }

    fun state(c: Context, m: ExtraModule): ModuleState {
        if (progress.value.containsKey(m.id)) return ModuleState.DOWNLOADING
        val inst = installedVersion(c, m)
            ?: return if (errors.value.containsKey(m.id)) ModuleState.FAILED else ModuleState.NOT_INSTALLED
        return if (compareVersions(m.surum, inst) > 0) ModuleState.UPDATE else ModuleState.INSTALLED
    }

    /** A FOSS app counts as ready when installed and, if it has a minimum, at least that version. */
    fun externalReady(c: Context, a: ExternalApp): Boolean {
        val v = packageVersion(c, a.pkg) ?: return false
        return a.minVersion == null || compareVersions(v, a.minVersion) >= 0
    }

    private var bundledIds: Set<String>? = null

    /** True when the APK ships inside Pulse (assets/extras/<id>.apk, or the backup assets/extras/backup/<id>.zip): no download. */
    fun isBundled(c: Context, m: ExtraModule): Boolean {
        if (!m.isApk) return false
        val ids = bundledIds ?: (
            runCatching { c.assets.list("extras").orEmpty().filter { it.endsWith(".apk") }.map { it.removeSuffix(".apk") } }.getOrDefault(emptyList()) +
                runCatching { c.assets.list("extras/backup").orEmpty().filter { it.endsWith(".zip") }.map { it.removeSuffix(".zip") } }.getOrDefault(emptyList())
            ).toSet().also { bundledIds = it }
        return m.id in ids
    }

    /** Opens the bundled APK: the plain asset, or (plain = false) the one entry named <id>.apk in the backup zip. */
    private fun openBundled(c: Context, id: String, plain: Boolean): InputStream? {
        if (plain) return runCatching { c.assets.open("extras/$id.apk") }.getOrNull()
        val z = runCatching { ZipInputStream(c.assets.open("extras/backup/$id.zip")) }.getOrNull() ?: return null
        // matched by exact name and only that entry is read; no path from the zip is ever used
        val hit = runCatching { generateSequence { z.nextEntry }.firstOrNull { !it.isDirectory && it.name == "$id.apk" } }.getOrNull()
        if (hit == null) { z.close(); return null }
        return z
    }

    /** Copies one bundled source to [out]; true when it reads and its version is the manifest one or newer. */
    private fun copyBundled(c: Context, m: ExtraModule, out: File, plain: Boolean): Boolean {
        val ok = runCatching {
            val src = openBundled(c, m.id, plain) ?: return@runCatching false
            src.use { i -> out.outputStream().use { i.copyTo(it) } }
            out.length() <= m.boyut + EXTRA_LIMIT &&
                compareVersions(c.packageManager.getPackageArchiveInfo(out.path, 0)?.versionName.orEmpty(), m.surum) >= 0
        }
        return ok.getOrDefault(false)
    }

    /** Copies the bundled APK to cache: the plain asset first, then the backup zip. Null when neither is usable (the download path runs). */
    private fun bundledCopy(c: Context, m: ExtraModule): File? {
        val out = File(c.cacheDir, "installer/store-${m.id}.apk")
        out.parentFile?.mkdirs()
        for (plain in listOf(true, false)) if (copyBundled(c, m, out, plain)) return out
        out.delete()
        return null
    }

    /** Starts a download in the background; the row shows progress and any error. */
    fun download(c: Context, m: ExtraModule) {
        val app = c.applicationContext
        if (jobs.containsKey(m.id)) return
        val free = runCatching { StatFs(app.filesDir.path).availableBytes }.getOrDefault(Long.MAX_VALUE)
        if (free < m.boyut * 2) { fail(m.id, tr("Not enough free space", "Yetersiz boş alan")); return }
        errors.update { it - m.id }
        progress.update { it + (m.id to 0f) }
        jobs[m.id] = scope.launch {
            val tmp = File(app.cacheDir, "installer/store-${m.id}.${if (m.isApk) "apk" else "zip"}")
            try {
                tmp.parentFile?.mkdirs()
                val b = if (isBundled(app, m)) runCatching { bundledCopy(app, m) }.getOrNull() else null
                if (b != null) { installApk(app, m, b); rev.update { it + 1 }; return@launch }
                fetchTo(app, m, tmp)
                if (sha256Hex(tmp) != m.sha256) throw IOException(tr("Checksum differs, file removed", "Doğrulama tutmadı, dosya silindi"))
                if (m.isApk) installApk(app, m, tmp) else { installData(app, m, tmp); if (!keepFile(app, m.id)) tmp.delete() }
                rev.update { it + 1 }
            } catch (e: CancellationException) {
                tmp.delete()
                throw e
            } catch (e: Exception) {
                tmp.delete()
                fail(m.id, e.message ?: tr("Failed", "Başarısız"))
            } finally {
                progress.update { it - m.id }
                jobs.remove(m.id)
            }
        }
    }

    fun cancel(id: String) { jobs.remove(id)?.cancel() }

    /** Removes an unzipped module now; an apk goes through the system uninstall screen. */
    fun remove(c: Context, m: ExtraModule) {
        if (m.isApk) {
            m.paket?.let { c.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$it")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            return
        }
        dataDir(c, m.id).deleteRecursively()
        saveRecord(c, m.id, null)
        rev.update { it + 1 }
    }

    /** Opens a link in the browser or the store. */
    fun open(c: Context, url: String) {
        try { c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (e: ActivityNotFoundException) { Toast.makeText(c, tr("No app can open this", "Bunu açacak uygulama yok"), Toast.LENGTH_SHORT).show() }
    }

    fun launch(c: Context, pkg: String) {
        c.packageManager.getLaunchIntentForPackage(pkg)?.let { c.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    private fun fail(id: String, msg: String) {
        errors.update { it + (id to msg) }
        rev.update { it + 1 }
    }

    /** The module file goes through the download manager (parallel ranges, notification, service); progress feeds the row. */
    private suspend fun fetchTo(c: Context, m: ExtraModule, out: File) {
        require(resolveUrl(c, m.url).startsWith("https://")) { "https only" }
        Downloads.fetchFile(c, resolveUrl(c, m.url), out, m.boyut) { p -> progress.update { it + (m.id to p) } }
        if (out.length() > m.boyut + EXTRA_LIMIT) throw IOException(tr("File is bigger than listed", "Dosya listedekinden büyük"))
    }

    /** Unzips a "veri" module into the app's own data folder; entries that leave the folder are skipped. */
    private fun installData(c: Context, m: ExtraModule, zip: File) {
        val dir = dataDir(c, m.id)
        dir.deleteRecursively()
        dir.mkdirs()
        val root = dir.canonicalPath + File.separator
        var bytes = 0L
        ZipInputStream(zip.inputStream()).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                if (e.isDirectory) continue
                val f = File(dir, e.name).canonicalFile
                if (!f.path.startsWith(root)) continue
                f.parentFile?.mkdirs()
                f.outputStream().use { z.copyTo(it) }
                bytes += f.length()
                if (bytes > m.boyut * 4) throw IOException(tr("Unzipped size is too big", "Açılan boyut çok büyük"))
            }
        }
        if (bytes == 0L) throw IOException(tr("Empty archive", "Arşiv boş"))
        saveRecord(c, m.id, Rec(m.surum, m.sha256, bytes))
    }

    /** Checks the package name and certificate before the system installer gets the file. */
    private fun installApk(c: Context, m: ExtraModule, apk: File) {
        val info = c.packageManager.getPackageArchiveInfo(apk.path, PackageManager.GET_SIGNING_CERTIFICATES)
            ?: throw IOException(tr("Not a readable package", "Paket okunamadı"))
        if (info.packageName != m.paket) throw IOException(tr("Package name differs", "Paket adı farklı"))
        val digests = info.signingInfo?.apkContentsSigners.orEmpty().map { sha256Hex(it.toByteArray()) }
        if (m.imzaSha256 !in digests) throw IOException(tr("Signature differs", "İmza farklı"))
        if (packageVersion(c, info.packageName) == null) markStorePkg(c, info.packageName) // an app the owner already had stays listed
        val uri = FileProvider.getUriForFile(c, "${c.packageName}.files", apk)
        c.startActivity(
            Intent(Intent.ACTION_INSTALL_PACKAGE).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
