package com.umutk.pulse.features

import com.umutk.pulse.LocalOpenTool
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.net.Uri
import android.app.Service
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.os.UserManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import com.umutk.pulse.WinAlert
import com.umutk.pulse.LocalWindowLook
import com.umutk.pulse.winButtonLooks
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.umutk.pulse.AppIcon
import com.umutk.pulse.Look
import com.umutk.pulse.PButton
import com.umutk.pulse.PCheck
import com.umutk.pulse.PField
import com.umutk.pulse.PCard
import com.umutk.pulse.PChip
import com.umutk.pulse.PDialog
import com.umutk.pulse.PHead
import com.umutk.pulse.PIcon
import com.umutk.pulse.Shell
import com.umutk.pulse.T
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.text.DateFormat
import java.util.Date
import java.util.zip.ZipFile

/** A package read from a picked file: one APK, or the split APKs of an APKS / APKM / XAPK / zip bundle. */
class Picked(
    val type: String, val parts: List<File>, val label: String, val pkg: String, val version: String, val code: Long,
    val minSdk: Int, val targetSdk: Int, val sigs: List<String>, val installedCode: Long?, val installedSigs: List<String>,
    val icon: Bitmap?, val src: String?,
) {
    /** What gets installed: the base first, then the splits that are ticked. */
    var files by mutableStateOf(if (InstallShow.libs) Installer.keepDevice(parts) else parts)
    /** Set for the install that runs: [Installer.dropSource] removes [src] after success. */
    var drop = false
    /** Set when the APK carries Xposed meta-data: the install result offers the LSPosed button. */
    var xposed = false
    fun toggle(f: File) { val on = f in files; files = parts.filter { it == parts[0] || (if (it == f) !on else it in files) } }
    val mismatch get() = installedCode != null && sigs != installedSigs
    val sigShort get() = sigs.firstOrNull()?.take(23)?.plus("...") ?: "no signature"
    /** New, Signature differs, Identical, Upgrade or Downgrade against the installed app. */
    fun kind(): String {
        val ic = installedCode ?: return "New"
        return when {
            mismatch -> "Signature differs"
            code == ic -> "Identical"
            code > ic -> "Upgrade"
            else -> "Downgrade"
        }
    }
}

/** One install setup. mode: dialog, background, auto, silent. auth: root, system, none. */
data class Profile(
    val name: String, val mode: String, val auth: String, val down: Boolean, val uninst: Boolean, val user: Int, val ask: Boolean,
    val dex: Boolean = false, val dropSrc: Boolean = false, val auto: Boolean = false,
) {
    fun enc() = listOf(mode, auth, down, uninst, user, ask, dex, dropSrc, auto).joinToString("\u001f")
}

class Rec(val time: Long, val pkg: String, val label: String, val ver: String) {
    fun enc() = listOf(time, pkg, label, ver).joinToString("\u001f")
}

private const val CH = "installer"
private const val NOTE_ID = 2001
private const val CH_INST = "installs"
/** Silent channel for the install progress and result notifications. */
private const val CH_QUIET = "installer_quiet"
private fun quietChannel(c: Context) = c.getSystemService(NotificationManager::class.java).createNotificationChannel(com.umutk.pulse.QuietChannel(CH_QUIET, "Installer", NotificationManager.IMPORTANCE_LOW).apply { setSound(null, null); enableVibration(false) })
private const val ID_INST = 2002
private const val ID_BUSY = 2003
private const val APK_MIME = "application/vnd.android.package-archive"
private val MODES = listOf("dialog", "background", "auto", "silent")
private val AUTHS = listOf("root", "shizuku", "system", "none")
private val DEFAULTS = listOf(
    Profile("Ask", "dialog", "system", false, false, 0, true),
    Profile("Quick", "auto", "system", false, false, 0, false),
    Profile("Silent", "silent", "root", false, false, 0, false),
)

/**
 * Installs APK, APKS, APKM, XAPK and zip bundles. Root runs `pm`, the system authorizer uses a PackageInstaller
 * session (the system screen asks), "shizuku" runs pm through Shizuku when it is granted, "none" hands one APK to the system installer.
 * Profiles are saved in the "p" file.
 */
/** A finished install: the package, whether the system reported success, and its message. */
data class Outcome(val pkg: String, val ok: Boolean, val msg: String)

object Installer {
    var profiles by mutableStateOf(DEFAULTS)
    var active by mutableStateOf("Ask")
    var status by mutableStateOf("")
    /** The last session result, read by the install window to stay open until the app is really installed. */
    var outcome by mutableStateOf<Outcome?>(null)
    val recent = mutableStateListOf<Rec>()
    /** The install that is waiting for its result; the result receiver reads it. */
    var pending: Picked? = null
    /** Completed by [finish] for a session install. */
    private var waiter: CompletableDeferred<Boolean>? = null
    /** Completed when the uninstall dialog returns. */
    var delWait: CompletableDeferred<Unit>? = null
    private var bg = false
    /** Package names Pulse refuses to install; kept in the "p" file. */
    val black = mutableStateListOf<String>()
    /** Installs waiting for [InstallService]; it takes them when it starts. */
    val bgQueue = ArrayList<Picked>()

    val cur: Profile get() = profiles.firstOrNull { it.name == active } ?: profiles[0]

    /** root only when Shell really has root; otherwise the system installer. */
    fun engine(p: Profile) = when {
        p.auth == "root" && Shell.root == true -> "root"
        p.auth == "shizuku" && Shell.shizuku == 2 -> "shizuku"
        p.auth == "none" -> "none"
        else -> "system"
    }

    /** Silent needs root; without it the flow is the dialog flow. */
    fun modeOf(p: Profile, eng: String) = if (p.mode == "silent" && eng != "root" && eng != "shizuku") "dialog" else p.mode

    private fun sp(c: Context) = c.getSharedPreferences("p", Context.MODE_PRIVATE)

    fun load(c: Context) {
        val p = sp(c)
        profiles = DEFAULTS.map { d -> p.getString("prof_${d.name}", null)?.let { decode(d.name, it) } ?: d }
        active = p.getString("inst_active", "Ask") ?: "Ask"
        black.clear(); black.addAll(p.getStringSet("inst_black", null).orEmpty().sorted())
        recent.clear()
        recent.addAll((p.getStringSet("inst_recent", null) ?: emptySet()).mapNotNull { s ->
            s.split('\u001f').takeIf { it.size == 4 }?.let { Rec(it[0].toLongOrNull() ?: 0L, it[1], it[2], it[3]) }
        }.sortedByDescending { it.time }.take(8))
    }

    private fun decode(name: String, s: String): Profile? {
        val f = s.split('\u001f')
        if (f.size < 6 || f[0] !in MODES || f[1] !in AUTHS) return null
        return Profile(name, f[0], f[1], f[2].toBoolean(), f[3].toBoolean(), f[4].toIntOrNull() ?: 0, f[5].toBoolean(),
            f.getOrNull(6)?.toBoolean() ?: false, f.getOrNull(7)?.toBoolean() ?: false, f.getOrNull(8)?.toBoolean() ?: false)
    }

    fun blocked(pkg: String) = pkg in black

    /** Hands [items] to [InstallService], which runs them off the UI; each result is posted by [finish]. */
    fun startBackground(c: Context, items: List<Picked>) {
        bgQueue.clear(); bgQueue.addAll(items)
        ContextCompat.startForegroundService(c, Intent(c, InstallService::class.java))
    }
    fun addBlack(c: Context, pkg: String) { if (pkg.isNotBlank() && pkg !in black) black.add(pkg); saveBlack(c) }
    fun removeBlack(c: Context, pkg: String) { black.remove(pkg); saveBlack(c) }
    private fun saveBlack(c: Context) { sp(c).edit().putStringSet("inst_black", black.toSet()).apply() }

    fun setActive(c: Context, name: String) { active = name; sp(c).edit().putString("inst_active", name).apply() }

    fun edit(c: Context, f: (Profile) -> Profile) {
        val old = cur
        val n = f(old)
        profiles = profiles.map { if (it.name == old.name) n else it }
        sp(c).edit().putString("prof_${old.name}", n.enc()).apply()
    }

    /** Reads every picked file; returns the packages and a message for the ones that failed. */
    fun inspectAll(c: Context, uris: List<Uri>): Pair<List<Picked>, String> {
        File(c.cacheDir, "installer").deleteRecursively()
        val ok = ArrayList<Picked>()
        val bad = ArrayList<String>()
        for (u in uris) {
            try { ok += splitApps(c, inspect(c, u), u) } catch (e: Exception) { bad += "${u.lastPathSegment.orEmpty().substringAfterLast('/')}: ${e.message.orEmpty()}" }
        }
        return ok to bad.joinToString("; ")
    }

    /** Copies the picked file to the cache and reads its package info. The type comes from the entries inside. */
    fun inspect(c: Context, uri: Uri): Picked {
        val dir = File(c.cacheDir, "installer/${System.nanoTime()}").apply { mkdirs() }.canonicalFile
        val raw = File(dir, "pick.bin")
        c.contentResolver.openInputStream(uri)?.use { i -> raw.outputStream().use { i.copyCapped(it) } } ?: throw IOException("Cannot open the file")
        val names = try { entryNames(raw) } catch (e: IOException) { throw IOException("Not an APK or zip file") }
        val type = when {
            "AndroidManifest.xml" in names -> "APK"
            "manifest.json" in names -> "XAPK"
            "info.json" in names -> "APKM"
            "toc.pb" in names -> "APKS"
            // Play bundles (.apks from a device or bundletool export) have base.apk plus split_*.apk and no toc.pb
            "base.apk" in names && names.size > 1 -> "APKS"
            else -> "ZIP"
        }
        val files = if (type == "APK") {
            val apk = File(dir, "base.apk")
            if (!raw.renameTo(apk)) throw IOException("Cannot prepare the APK")
            listOf(if (InstallShow.shrink) shrinkApk(c, apk, dir) else apk)
        } else unpack(raw, dir)
        val hint = if (type == "XAPK") xapkPackage(raw) else null
        return fromFiles(c, type, files, hint, uri)
    }

    /** A plain zip that holds several different apps (not the splits of one): each app becomes its own install. */
    /**
     * The parts of a bundle without the CPU splits the phone does not use: the one for its first ABI stays (arm64-v8a on most phones), the others (v7, x86) go.
     * A bundle with no split for that ABI is left whole. A single APK cannot be cut down: changing it would break its signature.
     */
    fun keepDevice(parts: List<File>): List<File> {
        val rx = Regex("(?:^|[._-])(arm64_v8a|armeabi_v7a|x86_64|x86)(?:\\.|$)")
        fun abi(f: File) = rx.find(f.name.lowercase().replace('-', '_'))?.groupValues?.get(1)
        val main = android.os.Build.SUPPORTED_ABIS.firstOrNull()?.lowercase()?.replace('-', '_') ?: return parts
        if (parts.none { abi(it) == main }) return parts
        return parts.filter { abi(it) == null || abi(it) == main }
    }

    /**
     * The APK without the CPU libraries the phone does not use, signed again with the bundled key. Changing an APK breaks its signature, so an app that
     * is already installed (it would be refused as an update) and an APK with nothing to cut are returned as they are.
     */
    private fun shrinkApk(c: Context, apk: File, dir: File): File = try {
        val pm = c.packageManager
        val name = pm.getPackageArchiveInfo(apk.path, 0)?.packageName
        val installed = name != null && try { pm.getPackageInfo(name, 0); true } catch (e: PackageManager.NameNotFoundException) { false }
        val abis = android.os.Build.SUPPORTED_ABIS.toList()
        val have = ZipFile(apk).use { z -> z.entries().asSequence().mapNotNull { Regex("^lib/([^/]+)/").find(it.name)?.groupValues?.get(1) }.toSet() }
        // keep the first ABI of the phone that the APK has; an APK without libraries or without that ABI stays whole
        val keep = abis.firstOrNull { it in have }
        if (installed || keep == null || have.size < 2) apk else {
            val raw = File(dir, "shrunk_raw.apk")
            ZipFile(apk).use { zf ->
                java.util.zip.ZipOutputStream(raw.outputStream()).use { z ->
                    zf.entries().asSequence().filter { e -> val m = Regex("^lib/([^/]+)/").find(e.name); !e.name.startsWith("META-INF/") && (m == null || m.groupValues[1] == keep) }.forEach { e ->
                        val ne = java.util.zip.ZipEntry(e.name)
                        // stored entries (libraries the app maps straight from the APK, resources.arsc) must stay stored and aligned
                        if (e.method == java.util.zip.ZipEntry.STORED) { ne.method = java.util.zip.ZipEntry.STORED; ne.size = e.size; ne.compressedSize = e.size; ne.crc = e.crc }
                        z.putNextEntry(ne); zf.getInputStream(e).use { it.copyTo(z) }; z.closeEntry()
                    }
                }
            }
            val ks = java.security.KeyStore.getInstance("PKCS12").apply { c.assets.open("fontpack.p12").use { load(it, "pulsefont".toCharArray()) } }
            val out = File(dir, "base.shrunk.apk")
            com.android.apksig.ApkSigner.Builder(listOf(com.android.apksig.ApkSigner.SignerConfig.Builder("pulse", ks.getKey("k", "pulsefont".toCharArray()) as java.security.PrivateKey, listOf(ks.getCertificate("k") as java.security.cert.X509Certificate)).build()))
                .setInputApk(raw).setOutputApk(out).setMinSdkVersion(minOf(24, android.os.Build.VERSION.SDK_INT))
                .setV1SigningEnabled(true).setV2SigningEnabled(true).build().sign()
            raw.delete(); apk.delete(); out.renameTo(apk); apk
        }
    } catch (e: Exception) { apk }

    private fun splitApps(c: Context, p: Picked, uri: Uri): List<Picked> {
        if (p.type != "ZIP" || p.parts.size < 2) return listOf(p)
        val pm = c.packageManager
        val byPkg = p.parts.groupBy { f -> pm.getPackageArchiveInfo(f.path, 0)?.packageName ?: "" }
        if (byPkg.size < 2) return listOf(p)
        return byPkg.filterKeys { it.isNotEmpty() }.values.map { fs -> fromFiles(c, if (fs.size > 1) "ZIP" else "APK", fs, null, uri) }
    }

    /** Reads the package info of [files] (the base first, found by name or size) into one install. */
    private fun fromFiles(c: Context, type: String, files: List<File>, hint: String?, uri: Uri): Picked {
        // splits are named split_*.apk or config.*.apk; the base is base.apk, "<package>.apk", or the one APK that is not a split
        val base = files.firstOrNull { it.name.startsWith("base") || (hint != null && it.name == "$hint.apk") }
            ?: files.filterNot { it.name.startsWith("split_") || it.name.startsWith("config.") }.maxByOrNull { it.length() }
            ?: files.maxByOrNull { it.length() } ?: throw IOException("No APK inside")
        val pm = c.packageManager
        val info = pm.getPackageArchiveInfo(base.path, PackageManager.GET_SIGNING_CERTIFICATES) ?: throw IOException("Not a readable package")
        val app = info.applicationInfo ?: throw IOException("Not a readable package")
        app.sourceDir = base.path; app.publicSourceDir = base.path
        val installed = try { pm.getPackageInfo(info.packageName, PackageManager.GET_SIGNING_CERTIFICATES) } catch (e: PackageManager.NameNotFoundException) { null }
        val icon = try { app.loadIcon(pm).toBitmap(96, 96) } catch (e: Exception) { null }
        return Picked(
            type = type, parts = listOf(base) + files.filter { it != base }, label = app.loadLabel(pm).toString(), pkg = info.packageName,
            version = info.versionName.orEmpty(), code = info.longVersionCode, minSdk = app.minSdkVersion, targetSdk = app.targetSdkVersion,
            sigs = digests(info.signingInfo?.apkContentsSigners), installedCode = installed?.longVersionCode,
            installedSigs = digests(installed?.signingInfo?.apkContentsSigners), icon = icon, src = if (uri.scheme == "file") uri.path else null,
        )
    }

    private fun entryNames(zip: File): List<String> = ZipFile(zip).use { z ->
        val out = ArrayList<String>()
        val en = z.entries()
        while (en.hasMoreElements()) out += en.nextElement().name
        out
    }

    /** XAPK manifest.json names the package; its main APK is "<package>.apk". */
    private fun xapkPackage(zip: File): String? = ZipFile(zip).use { z ->
        z.getEntry("manifest.json")?.let { e ->
            Regex("\"package_name\"\\s*:\\s*\"([^\"]+)\"").find(z.getInputStream(e).bufferedReader().readText())?.groupValues?.get(1)
        }
    }

    /** Extracts only the *.apk entries, flat, into [dir]. OBB files of an XAPK are not copied. */
    private fun unpack(zip: File, dir: File): List<File> {
        val out = ArrayList<File>()
        ZipFile(zip).use { z ->
            val en = z.entries()
            while (en.hasMoreElements()) {
                val e = en.nextElement()
                val name = e.name.substringAfterLast('/')
                if (e.isDirectory || !name.endsWith(".apk", true)) continue
                val f = File(dir, name).canonicalFile
                if (f.parentFile != dir) continue   // zip-slip guard
                z.getInputStream(e).use { i -> f.outputStream().use { i.copyCapped(it) } }
                out += f
            }
        }
        if (out.isEmpty()) throw IOException("No APK inside")
        return out
    }

    private fun digests(s: Array<Signature>?): List<String> =
        s.orEmpty().map { sig -> MessageDigest.getInstance("SHA-256").digest(sig.toByteArray()).joinToString(":") { "%02X".format(it) } }.sorted()

    fun installedCode(c: Context, pkg: String): Long? =
        try { c.packageManager.getPackageInfo(pkg, 0).longVersionCode } catch (e: PackageManager.NameNotFoundException) { null }

    /** pm with every split in one call: the first path is the base. */
    fun silentCmd(p: Picked, down: Boolean, user: Int, dex: Boolean = false) =
        "pm install -r" + (if (down) " -d" else "") + InstallShow.srcPkg?.let { " -d -i $it --install-reason 0" }.orEmpty() + (if (dex) " --force-dex-opt" else "") + " --user $user" + p.files.joinToString("") { " '${it.path}'" }

    /**
     * The same install as a pm session fed through stdin: `pm install <path>` makes the system open the path itself, and the system
     * may not read Pulse's private cache (SELinux), so the install failed without a clear reason. `cat` runs as root (or shell)
     * and hands every APK over a pipe. Shizuku runs as shell, which cannot read the private cache either: the files are first
     * copied to Pulse's folder on shared storage.
     */
    fun silentScript(c: Context, p: Picked, down: Boolean, user: Int): String {
        val files = if (Shell.root == true) p.files else {
            val d = File(c.externalCacheDir ?: c.cacheDir, "install").apply { deleteRecursively(); mkdirs() }
            p.files.map { f -> File(d, f.name).also { f.copyTo(it, true) } }
        }
        fun q(s: String) = "'" + s.replace("'", "'\\''") + "'"
        val create = "pm install-create -r" + (if (down) " -d" else "") + InstallShow.srcPkg?.let { " -i $it --install-reason 0" }.orEmpty() + " --user $user"
        val sb = StringBuilder()
        sb.append("S=$(").append(create).append(" | sed -n 's/.*\\[\\([0-9]*\\)\\].*/\\1/p'); ")
        sb.append("[ -z \"\$S\" ] && { echo 'Failure: no install session'; exit 1; }; ")
        files.forEachIndexed { n, f -> sb.append("cat ${q(f.path)} | pm install-write -S ${f.length()} \$S ${n}_${q(f.name)} - >/dev/null || { pm install-abandon \$S; echo 'Failure: could not write ${f.name}'; exit 1; }; ") }
        sb.append("pm install-commit \$S")
        return sb.toString()
    }

    /** Root/Shizuku: makes the installed [pkg] report [src] as its installer. Returns "" on success, else the error. */
    fun setSource(pkg: String, src: String): String {
        if (!PKG_RE.matches(pkg) || !PKG_RE.matches(src)) return "Bad package name"
        val a = Shell.run("cmd package set-installer $pkg $src")
        if (a.isBlank() || a.contains("Success", true)) return ""
        // no set-installer on this build: reinstall the app's own APKs with -i
        val paths = Shell.run("pm path $pkg").lines().filter { it.startsWith("package:") }.map { it.removePrefix("package:").trim() }
        if (paths.isEmpty()) return a.trim()
        val b = Shell.run("pm install -r -d -i $src --install-reason 0" + paths.joinToString("") { " '$it'" })
        return if (b.contains("Success")) "" else b.trim()
    }

    private val PKG_RE = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")

    /** One PackageInstaller session with every file written into it; the system screen or [InstallReceiver] takes it from there. */
    fun commit(c: Context, p: Picked) {
        val inst = c.packageManager.packageInstaller
        val sp = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        // best effort without root: only the "package source" changes, the installer name stays Pulse
        if (InstallShow.srcPkg == "com.android.vending" && Build.VERSION.SDK_INT >= 33) runCatching { sp.setPackageSource(PackageInstaller.PACKAGE_SOURCE_STORE) }
        val id = inst.createSession(sp)
        inst.openSession(id).use { s ->
            for (f in p.files) f.inputStream().use { i -> s.openWrite(f.name, 0, f.length()).use { o -> i.copyCapped(o); s.fsync(o) } }
            val cb = PendingIntent.getBroadcast(c, id, Intent(c, InstallReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
            s.commit(cb.intentSender)
        }
    }

    /** Installs one package with [eng]. Root and "none" return at once; a session returns when its result arrives. */
    suspend fun run(c: Context, pk: Picked, eng: String, down: Boolean, user: Int, background: Boolean, dex: Boolean = false, drop: Boolean = false): Boolean {
        if (pk.pkg in black) { status = "Blacklisted, not installed: ${pk.pkg}"; return false }
        bg = background
        pending = pk
        pk.drop = drop
        if (!background) notifyInstalling(c, pk.label)
        // a bundle with splits can't go through the single-APK hand-off: it always uses the session (root/Shizuku keep their own path)
        val engine = if (eng == "none" && pk.files.size > 1) "system" else eng
        return when (engine) {
            "root", "shizuku" -> {
                val out = withContext(Dispatchers.IO) { Shell.run(silentScript(c, pk, down, user)) }
                val ok = out.contains("Success")
                finish(c, ok, out.trim())
                ok
            }
            "none" -> {
                pending = null
                val uri = FileProvider.getUriForFile(c, "${c.packageName}.files", pk.files[0])
                c.startActivity(Intent(Intent.ACTION_INSTALL_PACKAGE).setDataAndType(uri, APK_MIME)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
                status = "Opened in the system installer; its result is not reported back"
                cancelInstalling(c)
                true
            }
            else -> {
                val d = CompletableDeferred<Boolean>()
                waiter = d
                status = "Confirm in the system install screen"
                withContext(Dispatchers.IO) { commit(c, pk) }
                d.await()
            }
        }
    }

    /** Called on the main thread with the final result of an install. */
    fun finish(c: Context, ok: Boolean, msg: String) {
        cancelInstalling(c)
        val p = pending; pending = null
        val by = if (ok && p != null) runCatching { c.packageManager.getInstallerPackageName(p.pkg) }.getOrNull() else null
        status = if (ok) "Installed ${p?.label.orEmpty()}" + (by?.let { " (installer: $it)" }.orEmpty()) else "Failed: ${msg.ifEmpty { "no message from the installer" }}"
        if (ok && p != null) {
            if (p.drop) dropSource(c, p)
            recent.removeAll { it.pkg == p.pkg }
            recent.add(0, Rec(System.currentTimeMillis(), p.pkg, p.label, p.version))
            while (recent.size > 8) recent.removeAt(recent.lastIndex)
            sp(c).edit().putStringSet("inst_recent", recent.map { it.enc() }.toSet()).apply()
        }
        notify(c, if (ok) "Installed ${p?.label.orEmpty()}" else "Install failed", if (ok) p?.pkg.orEmpty() else msg, ok)
        if (ok && p?.xposed == true) notifyModule(c, p.label)
        outcome = Outcome(p?.pkg.orEmpty(), ok, msg)
        waiter?.complete(ok)
        waiter = null
    }

    /** Removes the install source only when Pulse made the copy: a file in the cache or in Downloads. Content picks have no path and are kept. */
    fun dropSource(c: Context, pk: Picked) {
        val src = pk.src?.let { File(it).canonicalFile } ?: return
        val dl = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).path).canonicalFile
        val cache = c.cacheDir.canonicalFile
        if (src.path.startsWith(cache.path + File.separator) || src.path.startsWith(dl.path + File.separator)) src.delete()
    }

    @SuppressLint("MissingPermission")
    /** A module installed: the notification opens LSPosed so it can be turned on. */
    private fun notifyModule(c: Context, label: String) {
        val i = lsposedIntent(c) ?: return
        c.getSystemService(NotificationManager::class.java).createNotificationChannel(com.umutk.pulse.QuietChannel(CH, "Installer", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(c, 1, i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(c, CH).setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("$label installed").setContentText("Turn it on in LSPosed").setAutoCancel(true)
            .setContentIntent(open).addAction(0, "Open LSPosed", open).build()
        NotificationManagerCompat.from(c).notify(NOTE_ID + 1, n)
    }

    /** Ongoing "Installing" notification while a window install runs; [finish] swaps it for the result. */
    private fun notifyInstalling(c: Context, label: String) {
        quietChannel(c)
        val n = NotificationCompat.Builder(c, CH_QUIET).setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Installing").setContentText(label).setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
            .setProgress(0, 0, true).build()
        NotificationManagerCompat.from(c).notify(ID_BUSY, n)
    }

    private fun cancelInstalling(c: Context) = NotificationManagerCompat.from(c).cancel(ID_BUSY)

    /** The result, silent; a success clears itself 3 seconds later. */
    private fun notify(c: Context, title: String, text: String, ok: Boolean = false) {
        quietChannel(c)
        val b = NotificationCompat.Builder(c, CH_QUIET).setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title).setContentText(text).setAutoCancel(true).setSilent(true)
        if (ok) b.setTimeoutAfter(3000)
        NotificationManagerCompat.from(c).notify(NOTE_ID, b.build())
    }
}

/** Foreground service for the "background" install mode: runs the queued installs and posts each result as a notification. */
class InstallService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        getSystemService(NotificationManager::class.java).createNotificationChannel(com.umutk.pulse.QuietChannel(CH_INST, "Installs", NotificationManager.IMPORTANCE_LOW))
        val n = NotificationCompat.Builder(this, CH_INST).setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Installing").setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(ID_INST, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(ID_INST, n)
        val items = Installer.bgQueue.toList(); Installer.bgQueue.clear()
        val p = Installer.cur
        val eng = Installer.engine(p)
        scope.launch {
            try {
                for (pk in items) Installer.run(this@InstallService, pk, eng, p.down, p.user, true, dex = p.dex, drop = p.dropSrc)
            } catch (e: Exception) {
                Installer.finish(this@InstallService, false, e.message.orEmpty())
            } finally { stopSelf(startId) }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}

/** Gets the result of a PackageInstaller session: starts the system confirm screen when it asks, else reports the result. */
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val st = i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (st == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @Suppress("DEPRECATION")
            val confirm = i.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
            confirm?.let { c.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            return
        }
        Installer.finish(c, st == PackageInstaller.STATUS_SUCCESS, i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty())
    }
}

/** Users and profiles from UserManager, through reflection because the method is not public on every SDK. The id is the UserHandle hash (shortcut: no public id getter, check with a second user). 0 alone when unreadable. */
fun userIds(c: Context): List<Int> {
    val ids = runCatching {
        val um = c.getSystemService(UserManager::class.java)
        (UserManager::class.java.getMethod("getUserProfiles").invoke(um) as List<*>).map { it.hashCode() }
    }.getOrDefault(emptyList())
    return (listOf(0) + ids).distinct().sorted()
}

/** Ticks for the splits of an APKS, APKM or XAPK; the base is always on. */
@Composable
fun SplitList(pk: Picked) {
    if (pk.type !in listOf("APKS", "APKM", "XAPK") || pk.parts.size < 2) return
    Text("Splits", color = Look.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    pk.parts.forEachIndexed { i, f ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PCheck(i == 0 || f in pk.files, { pk.toggle(f) }, enabled = i > 0)
            Text(f.name, color = T.text, fontSize = 13.sp, maxLines = 1)
        }
    }
}

private val MIMES = arrayOf(APK_MIME, "application/zip", "application/octet-stream")
private class Dlg(val title: String, val text: String, val label: String, val go: () -> Unit)
private val MODES_LABEL = listOf("dialog", "background", "auto", "silent")
private val AUTH_LABEL = listOf("root" to "Root", "shizuku" to "Shizuku", "system" to "System", "none" to "None")

@Composable
fun InstallerPage() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf(listOf<Picked>()) }
    var sel by remember { mutableStateOf(setOf<Picked>()) }
    var busy by remember { mutableStateOf(false) }
    var dlg by remember { mutableStateOf<Dlg?>(null) }
    var win by remember { mutableStateOf<Picked?>(null) }
    var refused by remember { mutableStateOf(false) }
    val users = remember { userIds(ctx) }
    val prof = Installer.cur
    val eng = Installer.engine(prof)
    val mode = Installer.modeOf(prof, eng)
    val root = Shell.root == true

    // the uninstall dialog of the system returns here
    val deleter = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        Installer.delWait?.complete(Unit); Installer.delWait = null
    }

    suspend fun uninstall(pkg: String, e: String, user: Int): Boolean {
        if (e == "root" || e == "shizuku") return withContext(Dispatchers.IO) { Shell.run("pm uninstall --user $user $pkg") }.contains("Success")
        val d = CompletableDeferred<Unit>()
        Installer.delWait = d
        deleter.launch(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")))
        d.await()
        return withContext(Dispatchers.IO) { Installer.installedCode(ctx, pkg) } == null
    }

    fun installAll(items: List<Picked>, uninst: Boolean, forceDown: Boolean = false) {
        scope.launch {
            busy = true
            val p = Installer.cur
            val e = Installer.engine(p)
            val m = Installer.modeOf(p, e)
            if (m != p.mode) Installer.status = "Silent needs root; using the system installer"
            if (m == "background" && !uninst) {
                Installer.startBackground(ctx, items); win = null; busy = false
                return@launch
            }
            for (pk in items) {
                try {
                    if (uninst && pk.mismatch && !uninstall(pk.pkg, e, p.user)) { Installer.status = "Not uninstalled: ${pk.pkg}"; continue }
                    Installer.run(ctx, pk, e, p.down || forceDown, p.user, m == "background", dex = p.dex, drop = p.dropSrc)
                    refused = Installer.status.contains("DOWNGRADE", ignoreCase = true)
                } catch (ex: Exception) { Installer.finish(ctx, false, ex.message.orEmpty()) }
            }
            // a refused downgrade keeps the window open so the restore option can be used
            if (!refused) win = null
            busy = false
        }
    }

    // root only: back up the data folder, remove the app keeping its data, install the APK, copy the data back
    fun removeRestore(pk: Picked) {
        scope.launch {
            busy = true
            val u = Installer.cur.user
            val data = "/data/user/$u/${pk.pkg}"
            val bak = "${ctx.filesDir.path}/restore/${pk.pkg}"
            Installer.status = "Backing up the app data"
            val b = withContext(Dispatchers.IO) { Shell.run("rm -rf '$bak' && mkdir -p '$bak' && cp -a '$data/.' '$bak/' && echo OK") }
            if (!b.contains("OK")) { Installer.status = "Backup failed, nothing was removed: ${b.trim()}"; busy = false; return@launch }
            Installer.status = "Removing the app, keeping its data"
            val r = withContext(Dispatchers.IO) { Shell.run("pm uninstall -k --user $u ${pk.pkg}") }
            if (!r.contains("Success")) { Installer.status = "Remove failed: ${r.trim()}"; busy = false; return@launch }
            Installer.status = "Installing the APK"
            if (!Installer.run(ctx, pk, "root", true, u, false, drop = Installer.cur.dropSrc)) {
                Installer.status = "Install failed; the data backup is kept in $bak"; busy = false; return@launch
            }
            Installer.status = "Restoring the app data"
            val rs = withContext(Dispatchers.IO) {
                Shell.run("U=\$(stat -c %u:%g '$data') && cp -a '$bak/.' '$data/' && chown -R \$U '$data' && { restorecon -R '$data' 2>/dev/null; rm -rf '$bak'; echo OK; }")
            }
            Installer.status = if (rs.contains("OK")) "Installed ${pk.label} and restored its data" else "Restore failed; the backup is kept in $bak"
            win = null
            busy = false
        }
    }

    // the blacklist, the signature gate and the ask-first step; a single package opens the install window
    fun start(all: List<Picked>) {
        if (busy || all.isEmpty()) return
        val black = all.filter { it.pkg in Installer.black }
        if (black.isNotEmpty()) Installer.status = "Blacklisted, not installed: ${black.joinToString { it.pkg }}"
        val items = all - black.toSet()
        if (items.isEmpty()) return
        val p = Installer.cur
        if (items.size == 1 && (p.ask || items[0].mismatch || items[0].kind() == "Downgrade")) {
            refused = false; win = items[0]; return
        }
        val bad = items.filter { it.mismatch }
        if (bad.isNotEmpty() && !p.uninst) Installer.status = "Signature differs: ${bad.joinToString { it.pkg }}. Turn on uninstall on mismatch in the profile to replace it."
        val go = if (bad.isEmpty() || !p.uninst) items - bad.toSet() else items
        if (go.isEmpty()) return
        when {
            bad.isNotEmpty() && p.uninst -> dlg = Dlg("Uninstall first?",
                "The signature of ${bad.joinToString { it.pkg }} differs from the installed app, so it cannot be installed over it. Uninstalling deletes the app's data.",
                "Uninstall and install") { installAll(go, true) }
            p.ask -> dlg = Dlg("Install", "Install ${go.joinToString { it.label }}?", "Install") { installAll(go, false) }
            else -> installAll(go, false)
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty() && !busy) scope.launch {
            busy = true
            val (items, bad) = withContext(Dispatchers.IO) { Installer.inspectAll(ctx, uris) }
            list = items
            sel = items.toSet()
            Installer.status = if (bad.isEmpty()) "" else "Could not read: $bad"
            busy = false
            if (Installer.cur.mode == "auto") start(items)
        }
    }

    val openTool = LocalOpenTool.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // the settings gear sits in the page corner: installer settings are reached from here too, not only from the menu
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PHead("Installer")
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(40.dp).clickable { openTool("installer_settings") }, contentAlignment = Alignment.Center) { PIcon("settings", T.text, 22.dp) }
        }
        PCard {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PButton("Pick files", icon = Icons.Outlined.FolderOpen) { picker.launch(MIMES) }
                if (list.isNotEmpty()) {
                    PButton("All") { sel = list.toSet() }
                    PButton("None") { sel = emptySet() }
                    PButton("Install selected (${list.count { it in sel }})") { start(list.filter { it in sel }) }
                }
            }
            if (Installer.status.isNotEmpty()) Text(Installer.status, color = Look.accent, fontSize = 13.sp)
        }
        list.forEach { pk ->
            PCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PCheck(pk in sel, { sel = if (pk in sel) sel - pk else sel + pk })
                    PIcon("extract", Look.accent, 28.dp)
                    Column(Modifier.weight(1f)) {
                        Text(pk.label, color = T.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text(pk.pkg, color = T.sub, fontSize = 12.sp)
                    }
                }
                Text("${pk.type} / ${pk.version} (${pk.code}) / ${pk.files.size} part(s) / SDK ${pk.minSdk}-${pk.targetSdk}", color = T.sub, fontSize = 12.sp)
                Text("Installed: ${pk.installedCode?.let { "code $it" } ?: "no"}", color = T.sub, fontSize = 12.sp)
                Text("SHA-256 ${pk.sigShort}", color = T.sub, fontSize = 12.sp)
                if (pk.mismatch) Text("Signature differs from the installed app", color = Color(0xFFC42B1C), fontSize = 13.sp)
                val ic = pk.installedCode
                if (ic != null && pk.code < ic) Text("Older than the installed version", color = T.sub, fontSize = 13.sp)
                SplitList(pk)
                PButton("Install") { start(listOf(pk)) }
            }
        }
        if (Installer.recent.isNotEmpty()) PCard {
            Text("Recent", color = Look.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Installer.recent.forEach { r ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppIcon(r.pkg, 28.dp)
                    Column(Modifier.weight(1f)) {
                        Text(r.label, color = T.text, fontSize = 14.sp, maxLines = 1)
                        Text("${r.pkg} ${r.ver}", color = T.sub, fontSize = 12.sp, maxLines = 1)
                    }
                    Text(DateFormat.getDateInstance().format(Date(r.time)), color = T.sub, fontSize = 12.sp)
                }
            }
        }
        PCard {
            Text("Profile", color = Look.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Installer.profiles.forEach { x -> PChip(x.name == prof.name, x.name) { Installer.setActive(ctx, x.name) } }
            }
            Text("Install mode", color = T.sub, fontSize = 12.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MODES_LABEL.forEach { m -> PChip(prof.mode == m, m.replaceFirstChar { it.uppercase() }) { Installer.edit(ctx) { it.copy(mode = m) } } }
            }
            if (mode != prof.mode) Text("Silent needs root; this profile installs with the system dialog", color = T.sub, fontSize = 12.sp)
            Text("Authorizer", color = T.sub, fontSize = 12.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AUTH_LABEL.forEach { (a, label) -> PChip(prof.auth == a, label) { Installer.edit(ctx) { it.copy(auth = a) } } }
            }
            Text(if (root) "Root: available" else "Root: not available", color = T.sub, fontSize = 12.sp)
            Row(Modifier.clickable { Shell.askShizuku(ctx) }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PIcon("power", T.sub, 16.dp)
                Text(when (Shell.shizuku) { 2 -> "Shizuku: running, granted"; 1 -> "Shizuku: running, not granted"; else -> "Shizuku: not running" }, color = T.sub, fontSize = 12.sp)
            }
            if (prof.auth == "root" && eng != "root") Text("Root is not available, the system installer is used", color = T.sub, fontSize = 12.sp)
            if (prof.auth == "shizuku" && eng != "shizuku") Text("Shizuku is not granted, the system installer is used", color = T.sub, fontSize = 12.sp)
            Text("Allow downgrade", color = T.sub, fontSize = 12.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PChip(prof.down, "On") { Installer.edit(ctx) { it.copy(down = true) } }
                PChip(!prof.down, "Off") { Installer.edit(ctx) { it.copy(down = false) } }
            }
            Text("Uninstall on signature mismatch (deletes app data, asked first)", color = T.sub, fontSize = 12.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PChip(prof.uninst, "On") { Installer.edit(ctx) { it.copy(uninst = true) } }
                PChip(!prof.uninst, "Off") { Installer.edit(ctx) { it.copy(uninst = false) } }
            }
            Text("Ask before installing", color = T.sub, fontSize = 12.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PChip(prof.ask, "On") { Installer.edit(ctx) { it.copy(ask = true) } }
                PChip(!prof.ask, "Off") { Installer.edit(ctx) { it.copy(ask = false) } }
            }
            Text("Target user (root installs; system sessions go to the current user)", color = T.sub, fontSize = 12.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                users.forEach { u -> PChip(prof.user == u, "User $u") { Installer.edit(ctx) { it.copy(user = u) } } }
            }
            Text("Dexopt (root only)", color = T.sub, fontSize = 12.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PChip(prof.dex, "On") { Installer.edit(ctx) { it.copy(dex = true) } }
                PChip(!prof.dex, "Off") { Installer.edit(ctx) { it.copy(dex = false) } }
            }
            if (prof.dex && eng != "root") Text("Dexopt needs root; the system installer does not take it, so it is skipped", color = T.sub, fontSize = 12.sp)
            Text("Delete the install source after success (only copies Pulse made in the cache or Downloads)", color = T.sub, fontSize = 12.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PChip(prof.dropSrc, "On") { Installer.edit(ctx) { it.copy(dropSrc = true) } }
                PChip(!prof.dropSrc, "Off") { Installer.edit(ctx) { it.copy(dropSrc = false) } }
            }
            Text("Install on open (New or Upgrade only, in the install window)", color = T.sub, fontSize = 12.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PChip(prof.auto, "On") { Installer.edit(ctx) { it.copy(auto = true) } }
                PChip(!prof.auto, "Off") { Installer.edit(ctx) { it.copy(auto = false) } }
            }
        }
        PCard {
            Text("Blacklist", color = Look.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            var name by remember { mutableStateOf("") }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PField(name, { name = it.trim() }, "package name", Modifier.weight(1f))
                PButton("Add") { Installer.addBlack(ctx, name); name = "" }
            }
            Installer.black.forEach { pkg ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(pkg, color = T.text, fontSize = 13.sp, maxLines = 1, modifier = Modifier.weight(1f))
                    PIcon("delete", T.sub, 20.dp, Modifier.clickable { Installer.removeBlack(ctx, pkg) })
                }
            }
        }
    }

    // the installer's own look (Settings > Installer) for its window and confirm boxes
    CompositionLocalProvider(LocalWindowLook provides installLook()) {
        win?.let { w ->
            InstallWindow(w, root = root, refused = refused, uninstOk = prof.uninst, busy = busy,
                onInstall = { win = null; installAll(listOf(w), false) },
                onDowngrade = { installAll(listOf(w), false, forceDown = true) },
                onRestore = { removeRestore(w) },
                onUninstall = { win = null; installAll(listOf(w), true) },
                onCancel = { win = null })
        }
        dlg?.let { d -> PDialog(d.title, d.text, d.label, onConfirm = { dlg = null; d.go() }, onDismiss = { dlg = null }) }
    }
}

/** The install window: icon, name, package, version, status chip and the actions that fit the status. */
@Composable
private fun InstallWindow(
    pk: Picked, root: Boolean, refused: Boolean, uninstOk: Boolean, busy: Boolean,
    onInstall: () -> Unit, onDowngrade: () -> Unit, onRestore: () -> Unit, onUninstall: () -> Unit, onCancel: () -> Unit,
) {
    val t = LocalWindowLook.current
    val kind = pk.kind()
    val chip = when (kind) {
        "Signature differs" -> Color(0xFFC42B1C)
        "Downgrade" -> Color(0xFFCA5010)
        "Identical" -> t.sub
        else -> Look.accent
    }
    val ic = pk.icon
    // Windows looks: the title bar holds one line, so the header moves into the body
    val winLook = t.id in winButtonLooks
    val winTitle: @Composable () -> Unit = { Text(pk.label) }
    val header: @Composable () -> Unit = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (ic != null) Image(ic.asImageBitmap(), null, Modifier.size(72.dp)) else PIcon("extract", Look.accent, 56.dp)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(pk.label, color = t.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.background(Color(0xFF3DDC84), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp)) {
                        Text("Android", color = Color(0xFF073D1E), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Text(pk.pkg, color = t.sub, fontSize = 12.sp)
                Text("Version: ${pk.version} (${pk.code})", color = t.sub, fontSize = 12.sp)
                Box(Modifier.padding(top = 6.dp).background(chip, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 3.dp)) {
                    Text(kind, color = Color.White, fontSize = 12.sp)
                }
            }
    }
    WinAlert(onDismissRequest = onCancel,
        title = if (winLook) winTitle else header,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (winLook) header()
                if (pk.mismatch) Text(
                    "The signature differs from the installed app, so it cannot be installed over it. " +
                        (if (root) "Remove and restore data deletes the app and copies its data back. The data may not work with the new signature."
                        else if (uninstOk) "Uninstalling deletes the app's data."
                        else "Turn on uninstall on mismatch in the profile to replace it."),
                    color = t.sub, fontSize = 13.sp)
                if (refused) Text("The downgrade was refused by the system. Remove and restore data is risky: the app is removed, the older APK is installed and the data is copied back. If a step fails, the backup stays on the device.",
                    color = Color(0xFFC42B1C), fontSize = 13.sp)
                Text("SHA-256 ${pk.sigShort}", color = t.sub, fontSize = 12.sp)
            }
        },
        confirmButton = {
            if (busy) Text("Working...", color = t.sub, fontSize = 13.sp)
            else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!pk.mismatch) when (kind) {
                    "Downgrade" -> InstButton("Downgrade", onClick = onDowngrade)
                    "Identical" -> InstButton("Reinstall", onClick = onInstall)
                    else -> InstButton("Install", onClick = onInstall)
                }
                if (kind == "Downgrade" && refused && root) InstButton("Remove and restore data", danger = true, onClick = onRestore)
                if (pk.mismatch && root) InstButton("Remove and restore data", danger = true, onClick = onRestore)
                if (pk.mismatch && !root && uninstOk) InstButton("Uninstall and install", danger = true, onClick = onUninstall)
            }
        },
        dismissButton = { InstButton("Cancel", onClick = onCancel) },
    )
}

/** A button of the install window in the look of the window: the Windows push buttons in the Windows looks, a plain text button in Material, AMOLED and the others (the app's own look can be another one). */
@Composable
private fun InstButton(text: String, danger: Boolean = false, onClick: () -> Unit) {
    val t = LocalWindowLook.current
    if (t.id in winButtonLooks) PButton(text, danger = danger, onClick = onClick)
    else androidx.compose.material3.TextButton(onClick) { Text(text, color = if (danger) Color(0xFFC42B1C) else t.accent, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
}
