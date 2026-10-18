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

    fun dataDir(c: Context, id: String): File = File(c.getExternalFilesDir(null)?.parentFile ?: c.filesDir, "extras/$id")

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
                fetchTo(app, m, tmp)
                if (sha256Hex(tmp) != m.sha256) throw IOException(tr("Checksum differs, file removed", "Doğrulama tutmadı, dosya silindi"))
                if (m.isApk) installApk(app, m, tmp) else { installData(app, m, tmp); tmp.delete() }
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
        require(m.url.startsWith("https://")) { "https only" }
        Downloads.fetchFile(c, m.url, out, m.boyut) { p -> progress.update { it + (m.id to p) } }
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
