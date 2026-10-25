package com.umutk.pulse.features

import com.umutk.pulse.fadingEdges

import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.umutk.pulse.Guard
import com.umutk.pulse.InfoRows
import com.umutk.pulse.Look
import com.umutk.pulse.PChip
import com.umutk.pulse.PField
import com.umutk.pulse.PIcon
import com.umutk.pulse.T
import com.umutk.pulse.WinAlert
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.text.DateFormat
import java.util.Date
import java.util.zip.GZIPInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

// Archives in the file manager: zip / apk / jar (and apkm, xapk...), tar, tar.gz / tgz and a lone .gz, all read by hand with java.util.zip.
// 7z and rar are NOT supported (no library offline); they get no archive menu.

/** One entry of an archive; [path] has no leading "./" or "/", folders have no trailing slash. [packed] is the stored size. */
internal class AEntry(val path: String, val dir: Boolean, val size: Long, val packed: Long, val time: Long)

/** What the file manager asks the archive screens to do: kind is open, apk, here, named, pick, test, info or add. */
internal class ArcReq(val kind: String, val file: File, val files: List<File> = listOf(file))

private val ZIP_EXT = setOf("zip", "apk", "jar", "xapk", "apks", "apkm", "aar", "poweramp-backup")

/** Files the person chose "Open as > Archive" for: read as a zip whatever their extension, when they really start with the zip signature. */
internal object ArcForce { val paths = HashSet<String>() }
private fun zipMagic(f: File) = runCatching { f.inputStream().use { i -> val b = ByteArray(4); i.read(b) == 4 && b[0] == 'P'.code.toByte() && b[1] == 'K'.code.toByte() && b[2] == 3.toByte() && b[3] == 4.toByte() } }.getOrDefault(false)

private fun kindOf(f: File): String {
    val n = f.name.lowercase()
    // the volumes of a split archive: the first one stands for the whole (the others are not archives of their own)
    if (Regex("""\.\d{3}$""").containsMatchIn(n)) return if (n.endsWith(".001")) kindOf(File(f.parentFile, f.name.dropLast(4))) else ""
    return when {
        n.endsWith(".tar.gz") || n.endsWith(".tgz") -> "tgz"
        n.endsWith(".tar") -> "tar"
        n.endsWith(".tar.bz2") || n.endsWith(".tbz2") || n.endsWith(".tbz") -> "tbz"
        n.endsWith(".tar.xz") || n.endsWith(".txz") -> "txz"
        n.endsWith(".bz2") -> "bz2"
        n.endsWith(".xz") -> "xz"
        n.endsWith(".7z") -> "7z"
        n.endsWith(".rar") -> "rar"
        n.endsWith(".gz") -> "gz"
        n.substringAfterLast('.', "") in ZIP_EXT -> "zip"
        f.path in ArcForce.paths && zipMagic(f) -> "zip"
        else -> ""
    }
}

internal fun isArchiveFile(f: File) = f.isFile && kindOf(f).isNotEmpty()

private fun baseName(f: File): String {
    val n = f.name
    val l = n.lowercase()
    return when { l.endsWith(".tar.gz") -> n.dropLast(7); l.endsWith(".tgz") -> n.dropLast(4); else -> f.nameWithoutExtension }
}

private fun uniqueFile(dir: File, name: String): File {
    var t = File(dir, name)
    val stem = name.substringBeforeLast('.', name)
    val ext = if (name.contains('.')) "." + name.substringAfterLast('.') else ""
    var n = 1
    while (t.exists()) t = File(dir, "$stem ($n)$ext").also { n++ }
    return t
}

private object Stop : RuntimeException()

private class Lim(val src: InputStream, var left: Long) : InputStream() {
    override fun read(): Int { if (left <= 0) return -1; val b = src.read(); if (b >= 0) left--; return b }
    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (left <= 0) return -1
        val n = src.read(b, off, minOf(len.toLong(), left).toInt()); if (n > 0) left -= n; return n
    }
    override fun close() {} // the tar stream goes on to the next entry
}

private fun skipFully(i: InputStream, n: Long) {
    var r = n
    while (r > 0) { val s = i.skip(r); if (s > 0) r -= s else if (i.read() >= 0) r-- else break }
}

private fun readFull(i: InputStream, b: ByteArray): Boolean {
    var o = 0
    while (o < b.size) { val n = i.read(b, o, b.size - o); if (n < 0) return false; o += n }
    return true
}

private fun tarStr(h: ByteArray, off: Int, len: Int): String {
    var e = off
    while (e < off + len && h[e].toInt() != 0) e++
    return String(h, off, e - off, Charsets.UTF_8)
}

private fun tarNum(h: ByteArray, off: Int, len: Int): Long {
    if (h[off].toInt() and 0x80 != 0) { var v = 0L; for (k in 1 until len) v = (v shl 8) or (h[off + k].toLong() and 0xff); return v }
    return tarStr(h, off, len).trim().toLongOrNull(8) ?: 0L
}

private fun norm(p: String) = p.removePrefix("./").trimStart('/').trimEnd('/')

/** Walks a tar stream; GNU long names and pax paths are honoured. Links and special files are skipped. */
private fun tarWalk(input: InputStream, body: (AEntry, () -> InputStream) -> Unit) {
    val h = ByteArray(512)
    var longName: String? = null
    var pax: String? = null
    while (readFull(input, h)) {
        if (h.all { it.toInt() == 0 }) break
        var name = tarStr(h, 0, 100)
        if (tarStr(h, 257, 5) == "ustar") tarStr(h, 345, 155).let { if (it.isNotEmpty()) name = "$it/$name" }
        val size = tarNum(h, 124, 12)
        val type = h[156].toInt().toChar()
        val data = Lim(input, size)
        val pad = (512 - size % 512) % 512
        when (type) {
            'L' -> { longName = String(data.readBytes(), Charsets.UTF_8).trimEnd('\u0000', '\n') }
            'x' -> pax = String(data.readBytes(), Charsets.UTF_8).lines().firstNotNullOfOrNull { l ->
                l.substringAfter(' ', "").takeIf { it.startsWith("path=") }?.removePrefix("path=")
            }
            '0', '\u0000', '5' -> {
                val dir = type == '5' || name.endsWith("/")
                val p = norm(longName ?: pax ?: name)
                longName = null; pax = null
                if (p.isNotEmpty()) body(AEntry(p, dir, if (dir) 0 else size, size, tarNum(h, 136, 12) * 1000)) { data }
            }
            else -> {}
        }
        skipFully(input, data.left + pad)
    }
}

/** Calls [body] for every entry; the stream lambda may be used once (tar entries come in order). Throws on unreadable or unsupported files. */
/** The volumes name.ext.001, .002, ... joined into one file of the temporary folder (named name.ext so its type is known). */
private fun joinVolumes(first: File): File {
    val base = first.name.dropLast(4)
    val out = File(System.getProperty("java.io.tmpdir") ?: first.parent, "vol_" + System.nanoTime() + "_" + base)
    out.outputStream().buffered(256 * 1024).use { o ->
        var i = 1
        while (true) {
            val part = File(first.parentFile, base + "." + i.toString().padStart(3, '0'))
            if (!part.exists()) break
            part.inputStream().use { it.copyTo(o) }
            i++
        }
    }
    return out
}

private fun walk(f: File, body: (AEntry, () -> InputStream) -> Unit) {
    if (f.name.lowercase().endsWith(".001")) {
        val joined = joinVolumes(f)
        try { walk(joined, body) } finally { joined.delete() }
        return
    }
    when (kindOf(f)) {
        "zip" -> ZipFile(f).use { z ->
            for (e in z.entries().toList()) {
                val p = norm(e.name)
                if (p.isNotEmpty()) body(AEntry(p, e.isDirectory, maxOf(e.size, 0), if (e.compressedSize >= 0) e.compressedSize else maxOf(e.size, 0), maxOf(e.time, 0))) { z.getInputStream(e) }
            }
        }
        "tar" -> f.inputStream().buffered().use { tarWalk(it, body) }
        "tgz" -> GZIPInputStream(f.inputStream().buffered()).use { tarWalk(it, body) }
        "tbz" -> org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream(f.inputStream().buffered()).use { tarWalk(it, body) }
        "txz" -> org.tukaani.xz.XZInputStream(f.inputStream().buffered()).use { tarWalk(it, body) }
        "bz2" -> org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream(f.inputStream().buffered()).use { g -> body(AEntry(f.name.dropLast(4), false, 0, f.length(), f.lastModified())) { g } }
        "xz" -> org.tukaani.xz.XZInputStream(f.inputStream().buffered()).use { g -> body(AEntry(f.name.dropLast(3), false, 0, f.length(), f.lastModified())) { g } }
        "7z" -> org.apache.commons.compress.archivers.sevenz.SevenZFile(f).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                val p = norm(e.name)
                if (p.isNotEmpty()) body(AEntry(p, e.isDirectory, maxOf(e.size, 0), maxOf(e.size, 0), if (e.hasLastModifiedDate) e.lastModifiedDate.time else 0)) { z.getInputStream(e) }
            }
        }
        "rar" -> com.github.junrar.Archive(f).use { a ->
            for (h in a.fileHeaders.toList()) {
                val p = norm(h.fileName.replace('\\', '/'))
                if (p.isNotEmpty()) body(AEntry(p, h.isDirectory, h.fullUnpackSize, h.fullPackSize, h.mTime?.time ?: 0)) { a.getInputStream(h) }
            }
        }
        "gz" -> {
            // the last four bytes of a gzip file hold the original size (modulo 4 GB)
            val size = java.io.RandomAccessFile(f, "r").use { r -> r.seek(maxOf(r.length() - 4, 0)); val b = ByteArray(4); r.readFully(b); (0..3).sumOf { (b[it].toLong() and 0xff) shl (8 * it) } }
            GZIPInputStream(f.inputStream().buffered()).use { g -> body(AEntry(f.name.dropLast(3), false, size, f.length(), f.lastModified())) { g } }
        }
        else -> throw IOException("Unsupported archive")
    }
}

internal fun archiveEntries(f: File): List<AEntry> { val l = ArrayList<AEntry>(); walk(f) { e, _ -> l += e }; return l }

/** Unpacks everything into [dest]; entries that would leave it are skipped. Returns a note for the user. */
internal fun archiveExtract(f: File, dest: File, job: FileJob? = null): String = try {
    dest.mkdirs()
    val root = dest.canonicalPath + File.separator
    var n = 0
    walk(f) { e, open ->
        if (job?.cancelled == true) return@walk
        val out = File(dest, e.path)
        if (!out.canonicalPath.startsWith(root)) return@walk
        if (e.dir) out.mkdirs() else {
            out.parentFile?.mkdirs()
            // copied in blocks, so the bar of the job moves and a cross stops it between blocks
            open().use { i -> out.outputStream().use { o -> val buf = ByteArray(128 * 1024); while (job?.cancelled != true) { val k = i.read(buf); if (k < 0) break; o.write(buf, 0, k); job?.done?.addAndGet(k.toLong()) } } }
            if (e.time > 0) out.setLastModified(e.time)
            n++
        }
    }
    tr("Extracted $n files to ${dest.name}", "$n dosya ${dest.name} içine çıkarıldı")
} catch (x: Exception) { tr("Could not extract ${f.name}", "${f.name} çıkarılamadı") }

/** Writes one entry to [out]. */
/** The entries picked in an archive ([sel] are entry paths; a picked folder brings everything under it) written into [dest].
 *  Each file keeps its path from the picked item's parent, so a folder comes out whole with its name. Returns the files written.
 *  shortcut: each file is read out with its own pass over the archive, a single pass per archive is the upgrade. */
internal fun archiveExtractSel(f: File, sel: Set<String>, all: List<AEntry>, dest: File): List<File> {
    val out = ArrayList<File>()
    val parents = sel.associateWith { it.substringBeforeLast('/', "") }
    all.filter { !it.dir }.forEach { e ->
        val pick = sel.firstOrNull { e.path == it || e.path.startsWith("$it/") } ?: return@forEach
        val want = File(dest, e.path.removePrefix(parents.getValue(pick)).trimStart('/'))
        val parentDir = want.parentFile ?: dest
        parentDir.mkdirs()
        val target = uniqueFile(parentDir, want.name)
        if (archiveExtractOne(f, e.path, target)) out += target
    }
    return out
}

/** The file entries covered by a pick ([sel] entry paths; a picked folder covers everything under it). */
internal fun archivePicked(sel: Set<String>, all: List<AEntry>): List<AEntry> =
    all.filter { e -> !e.dir && sel.any { e.path == it || e.path.startsWith("$it/") } }

/** Takes [paths] (and everything under the folders among them) out of a zip-family archive by writing a new copy without them,
 *  then replacing the old file, so the space comes back. An APK loses its signature this way; that is accepted.
 *  shortcut: the whole archive is rewritten once per cut, not per file. Returns "" on success, else the reason. */
internal fun archiveRemove(f: File, paths: Set<String>): String {
    // an APK loses its signature when entries leave it; that is accepted
    if (kindOf(f) != "zip") return "unsupported"
    val tmp = File(f.parentFile, f.name + ".tmp")
    return try {
        ZipFile(f).use { z ->
            ZipOutputStream(tmp.outputStream().buffered()).use { zo ->
                z.entries().asSequence().forEach { e ->
                    val name = e.name.trimEnd('/')
                    if (paths.any { name == it || name.startsWith("$it/") }) return@forEach
                    zo.putNextEntry(ZipEntry(e.name))
                    if (!e.isDirectory) z.getInputStream(e).use { it.copyTo(zo) }
                    zo.closeEntry()
                }
            }
        }
        if (!f.delete() || !tmp.renameTo(f)) { tmp.delete(); "write" } else ""
    } catch (e: Exception) {
        tmp.delete()
        e.message ?: "write"
    }
}

/** Puts [files] (archive path to local file, a folder as its own path with a trailing slash in the archive) into a zip-family archive by writing a new copy, as [archiveRemove] does.
 *  A path already in the archive is replaced, with everything under it. Returns "" on success, else the reason.
 *  shortcut: zip only; 7z and rar are not written here yet. */
internal fun archiveAddZip(f: File, files: List<Pair<String, File>>): String {
    if (f.extension.lowercase() in setOf("apk", "apks", "xapk", "apkm")) return "apk"
    if (kindOf(f) != "zip") return "unsupported"
    val names = files.map { it.first.trimEnd('/') }.toSet()
    val tmp = File(f.parentFile, f.name + ".tmp")
    return try {
        ZipFile(f).use { z ->
            ZipOutputStream(tmp.outputStream().buffered()).use { zo ->
                z.entries().asSequence().forEach { e ->
                    val name = e.name.trimEnd('/')
                    if (names.any { name == it || name.startsWith("$it/") }) return@forEach
                    zo.putNextEntry(ZipEntry(e.name))
                    if (!e.isDirectory) z.getInputStream(e).use { it.copyTo(zo) }
                    zo.closeEntry()
                }
                files.forEach { (path, src) ->
                    if (src.isDirectory) zo.putNextEntry(ZipEntry("${path.trimEnd('/')}/"))
                    else { zo.putNextEntry(ZipEntry(path)); src.inputStream().use { it.copyTo(zo) } }
                    zo.closeEntry()
                }
            }
        }
        if (!f.delete() || !tmp.renameTo(f)) { tmp.delete(); "write" } else ""
    } catch (e: Exception) {
        tmp.delete()
        e.message ?: "write"
    }
}

internal fun archiveExtractOne(f: File, path: String, out: File): Boolean {
    var ok = false
    try {
        walk(f) { e, open ->
            if (e.path == path && !e.dir) {
                out.parentFile?.mkdirs()
                open().use { i -> out.outputStream().use { i.copyTo(it) } }
                ok = true
                throw Stop
            }
        }
    } catch (s: Stop) { }
    return ok
}

/** Reads every byte of every entry: the zip and gzip checksums are verified on the way. */
internal fun archiveTest(f: File): String = try {
    var n = 0
    var bytes = 0L
    val buf = ByteArray(65536)
    walk(f) { e, open ->
        if (!e.dir) { open().use { i -> while (true) { val r = i.read(buf); if (r < 0) break; bytes += r } }; n++ }
    }
    tr("OK: $n files, ${Guard.gb(bytes)}", "Sağlam: $n dosya, ${Guard.gb(bytes)}")
} catch (x: Exception) { tr("Damaged: ${x.message.orEmpty()}", "Bozuk: ${x.message.orEmpty()}") }

private fun archiveInfo(f: File): List<Pair<String, String>> {
    val es = archiveEntries(f)
    val total = es.sumOf { it.size }
    val packed = if (kindOf(f) == "zip") es.sumOf { it.packed } else f.length()
    return listOf(
        tr("Type", "Tür") to kindOf(f).uppercase(),
        tr("File size", "Dosya boyutu") to Guard.gb(f.length()),
        tr("Unpacked", "Açılmış") to Guard.gb(total),
        tr("Packed", "Sıkıştırılmış") to Guard.gb(packed),
        tr("Files", "Dosya") to es.count { !it.dir }.toString(),
        tr("Folders", "Klasör") to es.count { it.dir }.toString(),
        tr("Ratio", "Oran") to if (total > 0) "%.0f%%".format(packed * 100.0 / total) else "-",
    )
}

/** Zips [files] into [dir] as [name] at [level] (0 store, 1 fast, 6 normal, 9 max). Returns a note. */
private fun archiveAdd(files: List<File>, dir: File, name: String, level: Int): String {
    val target = uniqueFile(dir, name.trim().removeSuffix(".zip").ifBlank { "Archive" } + ".zip")
    return runCatching {
        ZipOutputStream(target.outputStream().buffered()).use { zos ->
            zos.setLevel(level)
            for (f in files) {
                val base = f.parentFile ?: dir
                for (e in f.walkTopDown()) {
                    if (e == target) continue
                    val rel = e.relativeTo(base).path
                    if (e.isDirectory) zos.putNextEntry(ZipEntry("$rel/")) else { zos.putNextEntry(ZipEntry(rel)); e.inputStream().use { it.copyTo(zos) } }
                    zos.closeEntry()
                }
            }
        }
        tr("Created ${target.name}", "${target.name} oluşturuldu")
    }.getOrElse { target.delete(); tr("Could not create the archive", "Arşiv oluşturulamadı") }
}

private class ApkMeta(val label: String, val pkg: String, val ver: String, val min: Int, val target: Int, val perms: List<String>, val icon: ImageBitmap?, val sig: String = "", val installed: String? = null)

private fun apkMeta(ctx: android.content.Context, f: File): ApkMeta? = try {
    val pm = ctx.packageManager
    if (!f.extension.equals("apk", true)) bundleMeta(ctx, f) else {
        val info = pm.getPackageArchiveInfo(f.path, PackageManager.GET_PERMISSIONS or PackageManager.GET_SIGNING_CERTIFICATES)
        val app = info?.applicationInfo
        if (info == null || app == null) null else {
            app.sourceDir = f.path; app.publicSourceDir = f.path
            val sig = runCatching { java.security.MessageDigest.getInstance("SHA-256").digest(com.umutk.pulse.Compat.signers(info)!![0].toByteArray()).take(6).joinToString("") { "%02x".format(it) } }.getOrDefault("")
            val ver = "${info.versionName.orEmpty()} (${com.umutk.pulse.Compat.longVersion(info)})"
            ApkMeta(app.loadLabel(pm).toString(), info.packageName, ver, if (android.os.Build.VERSION.SDK_INT >= 24) app.minSdkVersion else 0, app.targetSdkVersion,
                info.requestedPermissions?.toList().orEmpty(), runCatching { app.loadIcon(pm).toBitmap(96, 96).asImageBitmap() }.getOrNull(), sig, installedOf(pm, info.packageName))
        }
    }
} catch (e: Exception) { null }

private fun installedOf(pm: PackageManager, pkg: String): String? = runCatching { pm.getPackageInfo(pkg, 0).let { "${it.versionName.orEmpty()} (${com.umutk.pulse.Compat.longVersion(it)})" } }.getOrNull()

/** .xapk: the name, package and version from its manifest.json (icon.png as icon); .apks / .apkm: null, the dialog shows the file name and size. */
private fun bundleMeta(ctx: android.content.Context, f: File): ApkMeta? = java.util.zip.ZipFile(f).use { z ->
    val en = z.getEntry("manifest.json") ?: return null
    val j = org.json.JSONObject(z.getInputStream(en).use { it.readBytes().decodeToString() })
    val pkg = j.optString("package_name")
    val icon = z.getEntry("icon.png")?.let { e -> runCatching { z.getInputStream(e).use { android.graphics.BitmapFactory.decodeStream(it) }.asImageBitmap() }.getOrNull() }
    ApkMeta(j.optString("name", f.name), pkg, "${j.optString("version_name")} (${j.optString("version_code")})", j.optString("min_sdk_version").toIntOrNull() ?: 0,
        j.optString("target_sdk_version").toIntOrNull() ?: 0, (j.optJSONArray("permissions")?.let { a -> List(a.length()) { a.optString(it) } }).orEmpty(), icon, "", installedOf(ctx.packageManager, pkg))
}

/** The archive entries of the ⋮ menu as (label, icon key, request kind). */
private fun shortName(f: File): String = baseName(f).let { if (it.length > 14) it.take(13) + "…" else it }

internal fun archiveMenu(f: File): List<Triple<String, String, String>> = listOf(
    Triple(tr("Open archive", "Arşivi aç"), "zip", "open"),
    Triple(tr("Extract here", "Buraya çıkar"), "extract", "here"),
    Triple(tr("Extract to ${shortName(f)}/", "${shortName(f)}/ içine çıkar"), "folder", "named"),
    Triple(tr("Extract to…", "Şuraya çıkar…"), "openWith", "pick"),
    Triple(tr("Add to archive…", "Arşive ekle…"), "zip", "add"),
    Triple(tr("Archive info", "Arşiv bilgisi"), "info", "info"),
)

/** Runs one archive request: a browser, a dialog, or a quick job whose result goes to [onNote]. [onChanged] fires after files were written. */
@Composable
internal fun ArchiveHost(req: ArcReq, onClose: () -> Unit, onNote: (String) -> Unit, onChanged: () -> Unit, onView: (File) -> Unit) {
    val ctx = LocalContext.current
    val f = req.file
    val parent = f.parentFile ?: f
    var kind by remember(req) { mutableStateOf(req.kind) }
    LaunchedEffect(req) {
        File(ctx.cacheDir, "arc").deleteRecursively()
        if (kind == "here" || kind == "named" || kind == "test") {
            // extracting shows a bar in the Files screen (FileJobLines) instead of a "Working" text
            val job = if (kind == "test") null else FileJobs.begin(ctx, tr("Extracting ${f.name}", "${f.name} çıkarılıyor"), withContext(Dispatchers.IO) { runCatching { archiveEntries(f).filter { !it.dir }.sumOf { it.size } }.getOrDefault(0L) }.coerceAtLeast(1L))
            val note = withContext(Dispatchers.IO) {
                when (kind) { "here" -> archiveExtract(f, parent, job); "named" -> archiveExtract(f, uniqueFile(parent, baseName(f)), job); else -> archiveTest(f) }
            }
            job?.let { FileJobs.end(ctx, it, if (it.cancelled) tr("Cancelled", "İptal edildi") else note) }
            onNote(if (job?.cancelled == true) "" else note); onChanged(); onClose()
        }
    }
    when (kind) {
        "open" -> ArcBrowser(f, onClose, onNote, onChanged, onView)
        "apk" -> ApkInfo(f, onClose) { kind = "open" }
        "pick" -> FolderPick(parent, onClose) { dest ->
            onClose()
            kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                val job = FileJobs.begin(ctx, tr("Extracting ${f.name}", "${f.name} çıkarılıyor"), runCatching { archiveEntries(f).filter { !it.dir }.sumOf { it.size } }.getOrDefault(0L).coerceAtLeast(1L))
                val n = archiveExtract(f, dest, job)
                FileJobs.end(ctx, job, if (job.cancelled) tr("Cancelled", "İptal edildi") else n)
                withContext(Dispatchers.Main) { onNote(if (job.cancelled) "" else n); onChanged() }
            }
        }
        "info" -> {
            val rows by produceRows(f)
            WinAlert(onDismissRequest = onClose, title = { Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                text = { rows?.let { InfoRows(it) } ?: Text(tr("Reading…", "Okunuyor…"), color = T.sub, fontSize = 13.sp) },
                confirmButton = { TextButton(onClose) { Text(tr("Close", "Kapat")) } })
        }
        "add" -> AddDialog(req.files, parent, onClose, onNote, onChanged)
    }
}

@Composable
private fun produceRows(f: File) = androidx.compose.runtime.produceState<List<Pair<String, String>>?>(null, f) {
    value = withContext(Dispatchers.IO) { runCatching { archiveInfo(f) }.getOrElse { listOf(tr("Error", "Hata") to it.message.orEmpty()) } }
}

@Composable
private fun AddDialog(files: List<File>, dir: File, onClose: () -> Unit, onNote: (String) -> Unit, onChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(if (files.size == 1) files[0].nameWithoutExtension else "Archive") }
    var level by remember { mutableStateOf(6) }
    WinAlert(onDismissRequest = onClose, title = { Text(tr("Add to archive", "Arşive ekle")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PField(name, { name = it }, tr("Name", "Ad"), Modifier.fillMaxWidth())
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0 to tr("Store", "Sıkıştırma yok"), 1 to tr("Fast", "Hızlı"), 6 to tr("Normal", "Normal"), 9 to tr("Max", "En iyi")).forEach { (l, t) -> PChip(level == l, t) { level = l } }
                }
                Text("ZIP", color = T.sub, fontSize = 11.sp)
            }
        },
        confirmButton = { TextButton({
            scope.launch { onNote(tr("Working…", "Çalışıyor…")); onClose(); onNote(withContext(Dispatchers.IO) { archiveAdd(files, dir, name, level) }); onChanged() }
        }) { Text(tr("Add", "Ekle")) } },
        dismissButton = { TextButton(onClose) { Text(tr("Cancel", "Vazgeç")) } })
}

/** Folder chooser for "Extract to…": walks down from [start]; the button picks the folder that is open. */
@Composable
private fun FolderPick(start: File, onClose: () -> Unit, onPick: (File) -> Unit) {
    var cur by remember { mutableStateOf(start) }
    val kids = remember(cur) { cur.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") }?.sortedBy { it.name.lowercase() }.orEmpty() }
    WinAlert(onDismissRequest = onClose, title = { Text(cur.path, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 14.sp) },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                cur.parentFile?.let { p -> ArcRow("folder", "..", "") { cur = p } }
                kids.forEach { k -> ArcRow("folder", k.name, "") { cur = k } }
            }
        },
        confirmButton = { TextButton({ onPick(cur) }) { Text(tr("Extract here", "Buraya çıkar")) } },
        dismissButton = { TextButton(onClose) { Text(tr("Cancel", "Vazgeç")) } })
}

@Composable
private fun ArcRow(icon: String, name: String, sub: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        PIcon(icon, Look.accent, 20.dp)
        Spacer(Modifier.width(10.dp))
        Text(name, Modifier.weight(1f), color = T.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (sub.isNotEmpty()) Text(sub, color = T.sub, fontSize = 11.sp)
    }
}

/** Tap on an APK / bundle: Material info dialog (follows the Files look), Open archive, Install, Cancel; the installer gets the Files look in the standalone app. */
@Composable
internal fun ApkInfo(f: File, onClose: () -> Unit, onView: () -> Unit) {
    val ctx = LocalContext.current
    // one line: Cancel on the left, Browse and Install side by side on the right
    WinAlert(onDismissRequest = onClose,
        title = { Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClose) { Text("Cancel") }
            Spacer(Modifier.weight(1f))
            TextButton(onView) { Text("Browse") }
            TextButton({ onClose(); apkInstall(ctx, f) }) { Text("Install") }
        } },
        confirmButton = {})
}

/** Starts the existing installer window; from the standalone Files app it is told to use the Files look. */
private fun apkInstall(ctx: android.content.Context, f: File) {
    if (ctx is com.umutk.pulse.FilesActivity) {
        Installer.load(ctx); InstallShow.load(ctx)
        Thread { com.umutk.pulse.Shell.detectRoot() }.start()
        FilesInstall.file = f
        return
    }
    val i = android.content.Intent(ctx, com.umutk.pulse.features.InstallActivity::class.java).setDataAndType(androidx.core.content.FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f), "application/vnd.android.package-archive")
        .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    if (ctx is com.umutk.pulse.FilesActivity) i.putExtra("look", FilesLook.theme)
    runCatching { ctx.startActivity(i) }
}

internal class ARow(val name: String, val dir: Boolean, var size: Long, var time: Long)

/** The entries one level below [cwd] ("" or "a/b/"): folders are made up from the paths, with the size of what they hold. */
internal fun childrenOf(all: List<AEntry>, cwd: String): List<ARow> {
    val m = LinkedHashMap<String, ARow>()
    for (e in all) {
        if (!e.path.startsWith(cwd)) continue
        val rest = e.path.substring(cwd.length)
        if (rest.isEmpty()) continue
        val seg = rest.substringBefore('/')
        val isDir = e.dir && !rest.contains('/') || rest.contains('/')
        val r = m.getOrPut(seg) { ARow(seg, isDir, 0, 0) }
        if (!e.dir) r.size += e.size
        if (!isDir || e.time > r.time) r.time = maxOf(r.time, e.time)
    }
    return m.values.sortedWith(compareBy<ARow> { !it.dir }.thenBy { it.name.lowercase() })
}

@Composable
private fun ArcBrowser(f: File, onClose: () -> Unit, onNote: (String) -> Unit, onChanged: () -> Unit, onView: (File) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var all by remember(f) { mutableStateOf<List<AEntry>?>(null) }
    var err by remember(f) { mutableStateOf("") }
    var cwd by remember(f) { mutableStateOf("") }
    var pick by remember(f) { mutableStateOf<String?>(null) }
    var apk by remember(f) { mutableStateOf<File?>(null) }
    var inner by remember(f) { mutableStateOf<File?>(null) }
    var mf by remember(f) { mutableStateOf(false) }
    val isApk = f.extension.equals("apk", true)
    val meta by androidx.compose.runtime.produceState<ApkMeta?>(null, f) { value = if (isApk) withContext(Dispatchers.IO) { apkMeta(ctx, f) } else null }
    LaunchedEffect(f) {
        try { all = withContext(Dispatchers.IO) { archiveEntries(f) } } catch (e: Exception) { all = emptyList(); err = e.message ?: "?" }
    }
    val list = all
    val rows = remember(list, cwd) { childrenOf(list.orEmpty(), cwd) }
    val fmt = remember { DateFormat.getDateInstance(DateFormat.SHORT) }
    // one entry out of the archive into the cache; the callback runs on the main thread with null when it failed
    fun toCache(path: String, then: (File?) -> Unit) {
        scope.launch {
            val out = File(ctx.cacheDir, "arc/${System.nanoTime()}/${path.substringAfterLast('/')}")
            val ok = withContext(Dispatchers.IO) { runCatching { archiveExtractOne(f, path, out) }.getOrDefault(false) }
            then(if (ok) out else null)
        }
    }
    WinAlert(onDismissRequest = { if (cwd.isNotEmpty()) cwd = cwd.dropLast(1).substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" } else onClose() },
        title = { Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.heightIn(max = 420.dp)) {
                val parts = cwd.trimEnd('/').split('/').filter { it.isNotEmpty() }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("/", Modifier.clickable { cwd = "" }.padding(end = 4.dp), color = Look.accent, fontSize = 13.sp)
                    parts.forEachIndexed { i, p ->
                        Text("$p /", Modifier.clickable { cwd = parts.take(i + 1).joinToString("/") + "/" }.padding(end = 4.dp), color = Look.accent, fontSize = 13.sp, maxLines = 1)
                    }
                }
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (isApk && cwd.isEmpty()) {
                        ArcRow("info", "AndroidManifest", meta?.pkg.orEmpty()) { mf = !mf }
                        meta?.takeIf { mf }?.let { m ->
                            InfoRows(listOf(tr("App", "Uygulama") to m.label, tr("Package", "Paket") to m.pkg, tr("Version", "Sürüm") to m.ver, "SDK" to "min ${m.min}, target ${m.target}"))
                            m.perms.forEach { Text(it.substringAfterLast('.'), color = T.sub, fontSize = 11.sp, modifier = Modifier.padding(start = 30.dp)) }
                        }
                    }
                    when {
                        list == null -> Text(tr("Reading…", "Okunuyor…"), color = T.sub, fontSize = 13.sp)
                        err.isNotEmpty() -> Text(tr("Cannot read: $err", "Okunamadı: $err"), color = T.sub, fontSize = 13.sp)
                        rows.isEmpty() -> Text(tr("Empty", "Boş"), color = T.sub, fontSize = 13.sp)
                        // shortcut: only the first 400 rows of a folder are drawn, a lazy list is the upgrade
                        else -> rows.take(400).forEach { r ->
                            val sub = (if (r.dir) "" else Guard.gb(r.size) + "  ") + (if (r.time > 0) fmt.format(Date(r.time)) else "")
                            ArcRow(if (r.dir) "folder" else "filetypes", r.name, sub.trim()) {
                                val p = cwd + r.name
                                if (r.dir) cwd = "$p/"
                                else if (r.name.endsWith(".apk", true)) toCache(p) { out -> if (out != null) apk = out else onNote(tr("Could not read the entry", "Öğe okunamadı")) }
                                else pick = p
                            }
                        }
                    }
                    if (rows.size > 400) Text("+${rows.size - 400}", color = T.sub, fontSize = 11.sp)
                }
            }
        },
        confirmButton = { TextButton(onClose) { Text(tr("Close", "Kapat")) } })

    pick?.let { p ->
        val e = list?.firstOrNull { it.path == p }
        WinAlert(onDismissRequest = { pick = null }, title = { Text(p.substringAfterLast('/'), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = { Text(Guard.gb(e?.size ?: 0), color = T.sub, fontSize = 13.sp) },
            confirmButton = { TextButton({ pick = null; toCache(p) { out -> if (out != null) onView(out) else onNote(tr("Could not read the entry", "Öğe okunamadı")) } }) { Text(tr("View", "Göster")) } },
            dismissButton = { TextButton({
                pick = null
                scope.launch {
                    val out = uniqueFile(f.parentFile ?: f, p.substringAfterLast('/'))
                    val ok = withContext(Dispatchers.IO) { runCatching { archiveExtractOne(f, p, out) }.getOrDefault(false) }
                    onNote(if (ok) tr("Extracted ${out.name}", "${out.name} çıkarıldı") else tr("Could not extract", "Çıkarılamadı")); onChanged()
                }
            }) { Text(tr("Extract", "Çıkar")) } })
    }
    apk?.let { a -> ApkInfo(a, { apk = null }) { inner = a; apk = null } }
    inner?.let { a -> ArcBrowser(a, { inner = null }, onNote, onChanged, onView) }
}

/** The file manager's own look ("app" follows the app theme): its dialogs, menus and full-screen window use it. Saved as files_theme. */
object FilesLook {
    var theme by mutableStateOf("material")
    // Files-only colours and language: stored as files_*, applied to the process-wide Look / Opt only while Files is on screen (FilesActivity restores Pulse's own on stop)
    var dynamic by mutableStateOf(true)
    var accentIdx by mutableStateOf(-1)
    var lang by mutableStateOf("auto")
    private var loaded = false
    private fun sp(c: android.content.Context) = com.umutk.pulse.ownPrefs(c, "files_look") { it.startsWith("files_") }
    fun applyGlobal() { com.umutk.pulse.Look.dynamic = dynamic; com.umutk.pulse.Look.accentIdx = accentIdx; com.umutk.pulse.Opt.lang = lang }
    fun setDynamic(c: android.content.Context, v: Boolean) { dynamic = v; sp(c).edit().putBoolean("files_dyn", v).apply(); applyGlobal() }
    // picking a colour turns Material You off, so the chosen accent is the one shown
    fun setAccent(c: android.content.Context, i: Int) { accentIdx = i; dynamic = false; sp(c).edit().putInt("files_accent", i).putBoolean("files_dyn", false).apply(); applyGlobal() }
    fun setLang(c: android.content.Context, v: String) { lang = v; sp(c).edit().putString("files_lang", v).apply(); applyGlobal() }
    fun load(c: android.content.Context) {
        if (loaded) return
        loaded = true
        val q = sp(c)
        // no Files choice saved yet: Pulse's own colours / language are the fallback (read from Pulse's settings file, not "p")
        val pulse = com.umutk.pulse.ownPrefs(c, "pulse_settings")
        dynamic = q.getBoolean("files_dyn", pulse.getBoolean("dynamic", true)); accentIdx = q.getInt("files_accent", pulse.getInt("accent2", -1)); lang = q.getString("files_lang", pulse.getString("lang", "auto")) ?: "auto"
        // no choice saved yet: the file manager follows the app's look, so it does not switch to Material when opened
        theme = (q.getString("files_theme", "app") ?: "app")
            .takeIf { it == "app" || it == "amoled" || com.umutk.pulse.Themes.any { t -> t.id == it } } ?: "app"
    }
    fun set(c: android.content.Context, v: String) { theme = v; sp(c).edit().putString("files_theme", v).apply() }
}

/** The window look the file manager provides to its content, or null to leave the app's look. */
@Composable
internal fun filesLook(): com.umutk.pulse.PTheme? {
    FilesLook.load(LocalContext.current)
    val id = FilesLook.theme
    if (id == "app") return null
    return when (id) {
        "amoled" -> com.umutk.pulse.PTheme("amoled", "AMOLED", true, androidx.compose.ui.graphics.Color(0xFF000000), androidx.compose.ui.graphics.Color(0xFF000000), androidx.compose.ui.graphics.Color(0xFFFFFFFF),
            androidx.compose.ui.graphics.Color(0xFFB0B0B0), androidx.compose.ui.graphics.Color(0xFFFFFFFF), androidx.compose.ui.graphics.Color(0xFF222222), 16.dp, material = true)
        "coloros" -> com.umutk.pulse.effective(com.umutk.pulse.Themes.first { it.id == "coloros" })
        in com.umutk.pulse.winButtonLooks, "oneui" -> com.umutk.pulse.effective(com.umutk.pulse.Themes.first { it.id == id })
        else -> com.umutk.pulse.monoWindow(com.umutk.pulse.effective(com.umutk.pulse.Themes.first { it.id == "material" }))
    }
}

/** File manager options, kept in the shared "p" preferences (ex_* keys; the sort order is the explorer's own ex_sort_all). */
object FilesPrefs {
    var start by mutableStateOf("")          // default start folder, "" = internal storage
    var hidden by mutableStateOf(false)      // show hidden files
    var grid by mutableStateOf(false)        // list or grid
    var icon by mutableStateOf(2)            // 0 tiny, 1 small, 2 medium, 3 large, 4 huge
    var confirm by mutableStateOf(true)      // ask before delete
    var ext by mutableStateOf(true)          // show file extensions
    var stdFolders by mutableStateOf(true)   // drawer: standard folders
    var bookmarks by mutableStateOf(true)    // drawer: bookmarks
    var sort by mutableStateOf(0)            // 0..5: name/size/date x asc/desc
    var previews by mutableStateOf(true)     // PDF / Office / video / audio / APK previews and type badges
    var thumbs by mutableStateOf(true)       // image thumbnails in the list
    var binary by mutableStateOf(false)      // sizes in KiB/MiB (1024) instead of KB/MB (1000)
    var openRecent by mutableStateOf(false)  // opening page: false = start folder, true = recent files
    var dateFmt by mutableStateOf(0)         // 0 short, 1 long, 2 ISO
    var foldersFirst by mutableStateOf(true)
    var openAsk by mutableStateOf(false)     // files open through the system chooser instead of Pulse's viewers
    var storageInfo by mutableStateOf(true)  // drawer: storage bar and free space
    var cards by mutableStateOf(true)        // standalone app: each row is its own card
    var rowSep by mutableStateOf(false)      // a thin line between list rows
    var folderCards by mutableStateOf(false) // a card behind each folder (Material looks start without it)
    var alt by mutableStateOf(false)         // alternate row colours
    var startBrowse by mutableStateOf(false) // the standalone app opens on the Browse page, not the Home page
    var glass by mutableStateOf(true)        // the standalone app is see-through (blurred behind) in the glass looks (Windows 11); off: opaque
    var analysis by mutableStateOf(false)    // the folder sizes / file types / cleanup icons in the header (the menu and the home page reach them anyway)
    var radius by mutableStateOf(1)          // card corner radius: small / medium / large
    // the Library folders of internal storage (drawer), by relative path; the ones in libHidden are not shown
    val libFolders = listOf("DCIM", "Download", "Movies", "Music", "Pictures", "Documents", "DCIM/Screenshots")
    var libHidden by mutableStateOf(emptySet<String>())
    private var loaded = false
    private fun sp(c: android.content.Context) = c.getSharedPreferences("p", android.content.Context.MODE_PRIVATE)
    fun load(c: android.content.Context) {
        if (loaded) return
        loaded = true
        val p = sp(c)
        start = p.getString("ex_start", "") ?: ""; hidden = p.getBoolean("ex_hidden", false); grid = p.getBoolean("ex_grid", false)
        icon = p.getInt("ex_icon", 1); confirm = p.getBoolean("ex_confirm", true); ext = p.getBoolean("ex_ext", true); sort = p.getInt("ex_sort_all", 0)
        stdFolders = p.getBoolean("ex_std", true); bookmarks = p.getBoolean("ex_bm", true)
        thumbs = p.getBoolean("ex_thumbs", true); previews = p.getBoolean("ex_prev", true); binary = p.getBoolean("ex_bin", false); dateFmt = p.getInt("ex_datefmt", 0)
        foldersFirst = p.getBoolean("ex_ff", true); openAsk = p.getBoolean("ex_ask", false); openRecent = p.getBoolean("ex_openrecent", false); storageInfo = p.getBoolean("ex_sinfo", true)
        cards = p.getBoolean("ex_cards", true); folderCards = p.getBoolean("ex_fcards", false); rowSep = p.getBoolean("ex_rowsep", false); alt = p.getBoolean("ex_alt", false); radius = p.getInt("ex_rad", 1)
        analysis = p.getBoolean("ex_analysis", false); startBrowse = p.getBoolean("ex_startbrowse", false); glass = p.getBoolean("ex_glass", true)
        libHidden = p.getStringSet("ex_lib_off", emptySet())?.toSet() ?: emptySet()
    }
    fun save(c: android.content.Context) {
        sp(c).edit().putStringSet("ex_lib_off", libHidden).putBoolean("ex_glass", glass).apply()
        sp(c).edit().putString("ex_start", start).putBoolean("ex_hidden", hidden).putBoolean("ex_grid", grid).putInt("ex_icon", icon)
            .putBoolean("ex_confirm", confirm).putBoolean("ex_ext", ext).putInt("ex_sort_all", sort).putBoolean("ex_std", stdFolders).putBoolean("ex_bm", bookmarks)
            .putBoolean("ex_thumbs", thumbs).putBoolean("ex_prev", previews).putBoolean("ex_bin", binary).putInt("ex_datefmt", dateFmt).putBoolean("ex_ff", foldersFirst).putBoolean("ex_rowsep", rowSep).putBoolean("ex_ask", openAsk).putBoolean("ex_openrecent", openRecent).putBoolean("ex_sinfo", storageInfo).putBoolean("ex_cards", cards).putBoolean("ex_fcards", folderCards).putBoolean("ex_alt", alt).putInt("ex_rad", radius).putBoolean("ex_analysis", analysis).putBoolean("ex_startbrowse", startBrowse).apply()
    }
}

/** Settings > Files: collapsible sections (look, colours, language, files), every header and row with an icon of the look's pack. */
@Composable
fun FilesSettings() {
    val ctx = LocalContext.current
    FilesLook.load(ctx); FilesPrefs.load(ctx)
    val fp = FilesPrefs
    val openTool = com.umutk.pulse.LocalOpenTool.current
    // the chips above only change the values: store them, or the choice is lost on the next start
    androidx.compose.runtime.LaunchedEffect(fp.grid, fp.sort, fp.icon, fp.start, fp.cards, fp.folderCards, fp.rowSep) { fp.save(ctx) }
    @Composable
    fun Ic(k: String) = PIcon(k, T.text, 22.dp)
    @Composable
    fun Sec(title: String, k: String, open: Boolean = false, c: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) =
        com.umutk.pulse.SetSection(title, { PIcon(k, Look.accent, 24.dp) }, open, c)
    @Composable
    fun SwRow(title: String, k: String, on: Boolean, desc: String? = null, f: (Boolean) -> Unit) = Row(verticalAlignment = Alignment.CenterVertically) {
        Ic(k)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, color = T.text, fontSize = 15.sp)
            if (desc != null) Text(desc, color = T.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        com.umutk.pulse.PSwitch(on, { f(it); fp.save(ctx) })
    }
    @Composable
    fun Chips(title: String, k: String, opts: List<String>, sel: Int, f: (Int) -> Unit) =
        FilesPickRow(k, title, opts, sel, null) { f(it); fp.save(ctx) }
    fun sp(c: android.content.Context) = c.getSharedPreferences("p", android.content.Context.MODE_PRIVATE)
    val top = android.os.Environment.getExternalStorageDirectory().path
    val folders = listOf("" to tr("Storage", "Depolama")) + listOf("DCIM", "Download", "Documents", "Pictures", "Music", "Movies").filter { java.io.File(top, it).isDirectory }.map { "$top/$it" to it }
    com.umutk.pulse.PCard {
        Sec(tr("Look", "Görünüm"), "palette") {
        val looks = listOf("app" to tr("App theme", "Uygulama teması"), "material" to "Material You", "amoled" to "AMOLED black", "coloros" to "ColorOS", "win11" to "Windows 11",
            "win10" to "Windows 10", "win7" to "Windows 7", "xp" to "Windows XP", "win95" to "Windows 95", "oneui" to "One UI")
        FilesPickRow("palette", tr("Look", "Görünüm"), looks.map { it.second }, looks.indexOfFirst { it.first == FilesLook.theme }.coerceAtLeast(0),
            { n -> val id = looks[n].first; Box(Modifier.width(64.dp)) { com.umutk.pulse.ThemePreview(if (id == "app") Look.theme else com.umutk.pulse.Themes.first { it.id == id }) } }) { FilesLook.set(ctx, looks[it].first) }
        }
        Sec(tr("Colours", "Renkler"), "light") {
            val canDyn = android.os.Build.VERSION.SDK_INT >= 31
            if (canDyn) Row(verticalAlignment = Alignment.CenterVertically) {
                Ic("palette"); Text("Material You", Modifier.weight(1f).padding(start = 12.dp), color = T.text, fontSize = 15.sp)
                com.umutk.pulse.PSwitch(FilesLook.dynamic, { FilesLook.setDynamic(ctx, it) })
            }
            run {
                var dots by remember { mutableStateOf(false) }
                Row(Modifier.fillMaxWidth().clickable { dots = true }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Ic("palette"); Text(tr("Accent", "Vurgu"), Modifier.weight(1f).padding(start = 12.dp), color = T.text, fontSize = 15.sp)
                    Box(Modifier.size(22.dp).background(Look.accent, androidx.compose.foundation.shape.CircleShape))
                }
                if (dots) WinAlert(onDismissRequest = { dots = false }, title = { Text(tr("Accent", "Vurgu")) },
                    text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { com.umutk.pulse.accentsFor(T.material).chunked(5).forEachIndexed { r, row -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { row.forEachIndexed { k, c ->
                        val i = r * 5 + k
                        Box(Modifier.size(38.dp).background(c, androidx.compose.foundation.shape.CircleShape)
                            .border(if (Look.accentIdx == i) 3.dp else 1.dp, if (Look.accentIdx == i) T.text else T.line, androidx.compose.foundation.shape.CircleShape)
                            .clickable { FilesLook.setAccent(ctx, i); dots = false })
                    } } } } },
                    confirmButton = { TextButton({ dots = false }) { Text(tr("Close", "Kapat")) } })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Ic("light"); Text("AMOLED", Modifier.weight(1f).padding(start = 12.dp), color = T.text, fontSize = 15.sp)
                com.umutk.pulse.PSwitch(FilesLook.theme == "amoled", { FilesLook.set(ctx, if (it) "amoled" else "app") })
            }
        }
        Sec(tr("Language", "Dil"), "globe") {
            FilesPickRow("globe", tr("Language", "Dil"), listOf("English", "Türkçe"), if (com.umutk.pulse.Opt.lang == "tr") 1 else 0, null) { FilesLook.setLang(ctx, if (it == 1) "tr" else "en") }
        }
        Sec(tr("Files", "Dosyalar"), "folder") {
            Chips(tr("Start folder", "Başlangıç klasörü"), "folder", folders.map { it.second }, folders.indexOfFirst { it.first == fp.start }.coerceAtLeast(0)) { fp.start = folders[it].first }
            Chips(tr("Sort by", "Sıralama"), "sort", listOf(tr("Name", "Ad"), tr("Size", "Boyut"), tr("Date", "Tarih")), fp.sort / 2) { fp.sort = it * 2 + fp.sort % 2 }
            Chips(tr("Order", "Düzen"), "sort", listOf(tr("Ascending", "Artan"), tr("Descending", "Azalan")), fp.sort % 2) { fp.sort = fp.sort / 2 * 2 + it }
            Chips(tr("View", "Görünüm"), "gridView", listOf(tr("List", "Liste"), tr("Grid", "Izgara")), if (fp.grid) 1 else 0) { fp.grid = it == 1 }
            Chips(tr("Icon size", "Simge boyutu"), "image", listOf(tr("Tiny", "En küçük"), tr("Small", "Küçük"), tr("Medium", "Orta"), tr("Large", "Büyük"), tr("Huge", "En büyük")), fp.icon / 2) { fp.icon = it * 2 + 1 }
            // files taken out of an archive for a copy or cut wait in the app's data until they are pasted; this is how long
            val ttl = listOf(15 to "15 min", 60 to "1 h", 360 to "6 h", 1440 to "1 day", 0 to tr("Never", "Hiç"))
            Chips(tr("Delete temporary copies after", "Geçici kopyalar şu süre sonra silinsin"), "delete", ttl.map { it.second }, ttl.indexOfFirst { it.first == TempCopies.minutes(ctx) }.coerceAtLeast(1)) { TempCopies.setMinutes(ctx, ttl[it].first) }
            SwRow(tr("Show hidden files", "Gizli dosyaları göster"), "search", fp.hidden) { fp.hidden = it }
            SwRow(tr("Open on the Browse page", "Gözat sayfasında aç"), "folder", fp.startBrowse) { fp.startBrowse = it }
            SwRow(tr("Size, type and cleanup icons in the header", "Başlıkta boyut, tür ve temizlik simgeleri"), "disk", fp.analysis) { fp.analysis = it }
            SwRow(tr("Show file extensions", "Dosya uzantılarını göster"), "notes", fp.ext) { fp.ext = it }
            SwRow(tr("Confirm before delete", "Silmeden önce sor"), "delete", fp.confirm) { fp.confirm = it }
            // the recycle bin switch: same Opt.recycle and "recycle" pref as before, written without Opt.save (that would write Pulse's other settings)
            SwRow(tr("Recycle bin", "Çöp kutusu"), "recycle", com.umutk.pulse.Opt.recycle, tr("Deleted files wait there; off deletes for good", "Silinen dosyalar orada bekler; kapalıyken kalıcı silinir")) {
                com.umutk.pulse.Opt.recycle = it; com.umutk.pulse.ownPrefs(ctx, "pulse_settings").edit().putBoolean("recycle", it).apply()
            }
            if (com.umutk.pulse.Opt.recycle) Row(Modifier.fillMaxWidth().clickable { openTool("recycle") }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Ic("recycle")
                Text(tr("Open Recycle bin", "Çöp kutusunu aç"), color = T.text, fontSize = 15.sp, modifier = Modifier.weight(1f).padding(start = 12.dp))
                PIcon("chevron", T.sub, 18.dp)
            }
            SwRow(tr("Thumbnails", "Küçük resimler"), "image", fp.thumbs) { fp.thumbs = it }
            SwRow(tr("Previews and type badges", "Önizleme ve tür rozetleri"), "image", fp.previews) { fp.previews = it }
            SwRow(tr("Folders first", "Önce klasörler"), "folder", fp.foldersFirst) { fp.foldersFirst = it }
            Chips(tr("Size units", "Boyut birimi"), "sizes", listOf("KB · MB", "KiB · MiB"), if (fp.binary) 1 else 0) { fp.binary = it == 1 }
            Chips(tr("Date format", "Tarih biçimi"), "history", listOf(exDate(1_700_000_000_000L, 0), exDate(1_700_000_000_000L, 1), exDate(1_700_000_000_000L, 2)), fp.dateFmt) { fp.dateFmt = it }
            Chips(tr("Start page", "Açılış sayfası"), "folder", listOf(tr("Home", "Ana ekran"), tr("Recent", "Son")), if (fp.openRecent) 1 else 0) { fp.openRecent = it == 1 }
            Chips(tr("Open files", "Dosyaları aç"), "openFolder", listOf(tr("Pulse viewers", "Pulse görüntüleyiciler"), tr("Ask", "Sor")), if (fp.openAsk) 1 else 0) { fp.openAsk = it == 1 }
        }
        Sec(tr("Appearance of rows", "Satır görünümü"), "gridView") {
            SwRow(tr("Transparency", "Şeffaflık"), "light", fp.glass, tr("See-through Files window in Windows 11 (next time it opens)", "Windows 11'de şeffaf Dosyalar penceresi (bir sonraki açılışta)")) { fp.glass = it }
            SwRow(tr("Separate rows (cards)", "Ayrı satırlar (kartlar)"), "gridView", fp.cards, tr("Each file in its own card", "Her dosya kendi kartında")) { fp.cards = it }
            SwRow(tr("Folder cards", "Klasör kartları"), "folder", fp.folderCards, tr("A card behind each folder", "Her klasörün arkasında kart")) { fp.folderCards = it }
            SwRow(tr("Row separators", "Satır çizgileri"), "folder", fp.rowSep, tr("A thin line between list rows", "Liste satırları arasında ince çizgi")) { fp.rowSep = it }
            if (fp.cards) {
                SwRow(tr("Alternate row colours", "Dönüşümlü satır renkleri"), "light", fp.alt, tr("Every second row slightly different", "Her ikinci satır biraz farklı")) { fp.alt = it }
                Chips(tr("Row corner radius", "Satır köşe yarıçapı"), "gridView", listOf(tr("Small", "Küçük"), tr("Medium", "Orta"), tr("Large", "Büyük")), fp.radius) { fp.radius = it }
            }
        }
        Sec(tr("Drawer", "Çekmece"), "bookmark") {
            SwRow(tr("Standard folders", "Standart klasörler"), "folder", fp.stdFolders, tr("DCIM, Downloads, Movies… in the menu", "DCIM, İndirilenler, Filmler… menüde")) { fp.stdFolders = it }
            SwRow(tr("Bookmarks", "Yer imleri"), "bookmark", fp.bookmarks, tr("Your saved folders in the menu", "Kayıtlı klasörlerin menüde")) { fp.bookmarks = it }
            SwRow(tr("Storage info", "Depolama bilgisi"), "disk", fp.storageInfo, tr("Used and free space in the menu", "Menüde kullanılan ve boş alan")) { fp.storageInfo = it }
            // Library: each standard folder can be shown or hidden in the menu's Library section
            Text(tr("Library", "Kütüphane"), color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            fp.libFolders.forEach { rel ->
                SwRow(File(rel).name, "folder", rel !in fp.libHidden, rel) { on ->
                    fp.libHidden = if (on) fp.libHidden - rel else fp.libHidden + rel
                    fp.save(ctx)
                }
            }
            // Manage bookmarks: the folders starred in the file manager, each with a trash icon
            var marks by remember { mutableStateOf(sp(ctx).getString("ex_marks", "").orEmpty().lines().filter { it.isNotBlank() }) }
            marks.forEach { m ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Ic("bookmark"); Text(java.io.File(m).name.ifEmpty { "/" }, Modifier.weight(1f).padding(start = 12.dp), color = T.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Box(Modifier.size(36.dp).clickable { marks = marks - m; sp(ctx).edit().putString("ex_marks", marks.joinToString("\n")).apply() }, contentAlignment = Alignment.Center) { PIcon("delete", T.sub, 20.dp) }
                }
            }
        }
    }
}

/** A full-width row (icon, title, current value) that opens a radio list; [thumb] draws a small preview before each name. */
@Composable
private fun FilesPickRow(k: String, title: String, opts: List<String>, sel: Int, thumb: (@Composable (Int) -> Unit)?, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clickable { open = true }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        PIcon(k, T.text, 22.dp)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, color = T.text, fontSize = 15.sp)
            Text(opts.getOrElse(sel) { "" }, color = T.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    if (open) WinAlert(onDismissRequest = { open = false }, title = { Text(title) },
        text = { val sc = rememberScrollState(); Column(Modifier.heightIn(max = 420.dp).then(if (thumb != null) Modifier.fadingEdges(sc, true) else Modifier).verticalScroll(sc)) {
            opts.forEachIndexed { i, n ->
                Row(Modifier.fillMaxWidth().clickable { onPick(i); open = false }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.RadioButton(sel == i, null)
                    Spacer(Modifier.width(10.dp))
                    thumb?.let { it(i); Spacer(Modifier.width(10.dp)) }
                    Text(n, color = T.text, fontSize = 14.sp)
                }
            }
        } },
        confirmButton = { TextButton({ open = false }) { Text(tr("Close", "Kapat")) } })
}


/** The files an archive cut / copy writes into the app's data (arcclip) and cache (arcsel) so they can be pasted later: swept after a time the user sets (an hour at first). */
object TempCopies {
    fun minutes(c: android.content.Context): Int = c.getSharedPreferences("p", android.content.Context.MODE_PRIVATE).getInt("tmp_copy_min", 60)
    fun setMinutes(c: android.content.Context, m: Int) { c.getSharedPreferences("p", android.content.Context.MODE_PRIVATE).edit().putInt("tmp_copy_min", m).apply(); sweep(c) }
    /** Deletes the temporary copy folders older than the chosen time. Cheap: call it whenever the app or Files opens. */
    fun sweep(c: android.content.Context) {
        val m = minutes(c); if (m <= 0) return
        val limit = System.currentTimeMillis() - m * 60_000L
        listOf(java.io.File(c.filesDir, "arcclip"), java.io.File(c.cacheDir, "arcsel"), java.io.File(c.cacheDir, "pa_import"), java.io.File(c.cacheDir, "restore")).forEach { root ->
            root.listFiles()?.filter { it.lastModified() < limit }?.forEach { runCatching { it.deleteRecursively() } }
        }
    }
}
