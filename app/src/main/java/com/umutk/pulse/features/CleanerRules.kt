package com.umutk.pulse.features

import android.content.Context
import android.os.Environment
import com.umutk.pulse.Cleaner
import com.umutk.pulse.Shell
import java.io.File

/** One deletable thing. [rule] decides both how it was found and how it is re-checked before deletion. */
class Finding(val path: String, val size: Long, val rule: String, val label: String = "")

/** A line in the list: one app, one leftover folder or one system rule, holding the findings behind it. */
class CleanRow(val id: String, val pkg: String?, val title: String, val sub: String, val items: List<Finding>) {
    val size get() = items.sumOf { it.size }
}

/**
 * SD Maid style rules: AppCleaner (per-app cache and known junk folders), CorpseFinder (data of uninstalled apps)
 * and SystemCleaner (thumbnails, trash, old temp files, empty folders). Scanning is read-only; [delete] re-checks every rule.
 */
object CleanerRules {
    const val APP = "app"; const val CACHE = "cache"; const val CORPSE = "corpse"
    private const val TEMP_DAYS = 7L
    private const val APK_DAYS = 30L

    private val ext: String by lazy { runCatching { Environment.getExternalStorageDirectory().canonicalPath }.getOrDefault("/storage/emulated/0") }
    private val viaShell get() = Shell.root == true || Shell.shizukuOk()

    private val mediaRels = listOf("cache")
    private val appRe = Regex("^(/data/user/\\d+|" + Regex.escape(ext) + "/Android/data|" + Regex.escape(ext) + "/Android/media)/([A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+)/(.+)$")
    private val pkgRe = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")
    private val corpseRe = Regex("^(" + Regex.escape(ext) + "/Android/(?:data|obb|media)|/data/user/\\d+|/data/user_de/\\d+)/([A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+)$")

    // Allow/deny: system and vendor packages are never reported as leftovers even when PackageManager does not list them.
    private val denyPrefix = listOf(
        "android", "com.android.", "com.google.", "com.samsung.", "com.sec.", "com.qualcomm.", "com.qti.", "com.miui.", "com.xiaomi.",
        "com.huawei.", "com.oppo.", "com.coloros.", "com.oplus.", "com.vivo.", "com.mediatek.", "com.sony.", "com.motorola.",
        "com.lge.", "com.oneplus.", "org.chromium.", "org.lineageos.", "org.codeaurora.", "vendor.", "com.topjohnwu.", "me.weishu.", "moe.shizuku.",
    )

    private fun denied(pkg: String) = pkg == "com.umutk.pulse" || denyPrefix.any { pkg.startsWith(it) }

    private fun safeArg(p: String) = '\'' !in p && '\n' !in p

    /** True when [p] has no symlink tricks. /data/user/0 is itself a symlink, so there only the last component is compared. */
    private fun plain(p: String): Boolean {
        if (".." in p) return false
        val c = runCatching { File(p).canonicalPath }.getOrNull() ?: return false
        if (c == p) return true
        val f = File(p)
        return p.startsWith("/data/") && c == runCatching { File(f.parent ?: return false).canonicalPath }.getOrNull() + "/" + f.name
    }

    /** Names of all packages PackageManager knows, including uninstalled-with-data and disabled ones, plus root's `pm list packages -u`. */
    private fun installed(ctx: Context): Set<String> {
        val s = HashSet<String>()
        runCatching { ctx.packageManager.getInstalledPackages(0x00002000 or 0x00000200 or 0x00400000).forEach { s += it.packageName } }
        if (viaShell) Shell.run("pm list packages -u").lineSequence().forEach { if (it.startsWith("package:")) s += it.removePrefix("package:").trim() }
        return s
    }

    private fun listDirs(dir: String): List<File> =
        if (viaShell) Shell.run("ls -1d '$dir'/* 2>/dev/null").lineSequence().map { it.trim() }.filter { it.startsWith("/") }.map { File(it) }.toList()
        else File(dir).listFiles().orEmpty().filter { it.isDirectory }

    private fun sizes(paths: List<String>): Map<String, Long> {
        if (paths.isEmpty()) return emptyMap()
        if (!viaShell) return paths.associateWith { File(it).walkTopDown().filter { f -> f.isFile }.sumOf { f -> f.length() } }
        val m = HashMap<String, Long>()
        paths.filter { safeArg(it) }.chunked(40).forEach { c ->
            Shell.run("du -sk " + c.joinToString(" ") { "'$it'" } + " 2>/dev/null").lineSequence().forEach { l ->
                val kb = l.substringBefore('\t').trim().toLongOrNull() ?: return@forEach
                m[l.substringAfter('\t').trim()] = kb * 1024
            }
        }
        return m
    }

    // ------------------------------------------------------------------ AppCleaner

    /** Folder names never descended into or reported, even though their name might contain "cache": real app data, not junk. */
    private val neverCache = setOf("databases", "shared_prefs")

    /** Every folder under [root] whose name contains "cache" (any depth, bounded), without going into [neverCache] folders.
     *  Replaces a fixed list of exact relative paths: an app can name its cache folder almost anything, "files/picasso-cache"
     *  and "cache/exoplayer" alike, and a fixed list missed most of them (that was why, say, YouTube's cache never showed up). */
    private fun findCacheDirs(root: File, maxDepth: Int = 6): List<File> {
        val out = ArrayList<File>()
        fun walk(dir: File, depth: Int) {
            if (depth > maxDepth) return
            dir.listFiles()?.forEach { k ->
                if (!k.isDirectory) return@forEach
                when {
                    k.name in neverCache -> {}
                    k.name.contains("cache", ignoreCase = true) -> out += k
                    else -> walk(k, depth + 1)
                }
            }
        }
        walk(root, 0)
        return out
    }

    fun appJunk(ctx: Context): List<CleanRow> {
        val paths = ArrayList<String>()
        if (viaShell) {
            val prune = neverCache.joinToString(" -o ") { "-name '$it'" }
            val out = Shell.run("for b in /data/user/*/* '$ext'/Android/data/*; do find \"\$b\" -maxdepth 6 \\( $prune \\) -prune -o -type d -iname '*cache*' -print 2>/dev/null; done; " +
                "for b in '$ext'/Android/media/*; do [ -d \"\$b/cache\" ] && echo \"\$b/cache\"; done")
            out.lineSequence().map { it.trim() }.filter { it.startsWith("/") }.forEach { paths += it }
        } else {
            listDirs("$ext/Android/data").forEach { b -> findCacheDirs(b).forEach { paths += it.path } }
            listDirs("$ext/Android/media").forEach { b -> mediaRels.forEach { r -> File(b, r).takeIf { it.isDirectory }?.let { paths += it.path } } }
        }
        val sz = sizes(paths)
        val pm = ctx.packageManager
        val byPkg = paths.mapNotNull { p ->
            val pkg = appRe.matchEntire(p)?.groupValues?.get(2) ?: return@mapNotNull null
            val s = sz[p] ?: 0L
            // every path found this way is, by construction, a folder named "cache" something, or the one fixed "media/cache": always CACHE
            if (s <= 0L) null else pkg to Finding(p, s, CACHE)
        }.groupBy({ it.first }, { it.second })
        return byPkg.map { (pkg, f) ->
            val label = runCatching { pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString() }.getOrDefault(pkg)
            val cache = f.filter { it.rule == CACHE }.sumOf { it.size }
            CleanRow(pkg, pkg, label, "Cache ${mb(cache)} · Other junk ${mb(f.sumOf { it.size } - cache)}", f)
        }.sortedByDescending { it.size }
    }

    private fun mb(b: Long) = if (b >= 1073741824L) "%.1f GB".format(b / 1073741824.0) else "%.1f MB".format(b / 1048576.0)

    /** Path is one of the known junk folders of an app, in an allowed root: under Android/media exactly "cache", under
     *  Android/data or /data/user any folder named "cache" something, at any depth, none of its path components a real-data folder. */
    private fun appJunkOk(p: String): Boolean {
        if (!plain(p)) return false
        val m = appRe.matchEntire(p) ?: return false
        val rel = m.groupValues[3]
        val parts = rel.split('/')
        if (parts.any { it in neverCache }) return false
        return if (m.groupValues[1].endsWith("/Android/media")) rel in mediaRels else parts.last().contains("cache", ignoreCase = true)
    }

    // ------------------------------------------------------------------ CorpseFinder

    fun corpses(ctx: Context): List<CleanRow> {
        val have = installed(ctx)
        // An empty package list means PackageManager is not answering: report nothing rather than every folder.
        if (have.size < 20) return emptyList()
        val roots = ArrayList<String>().apply {
            add("$ext/Android/data"); add("$ext/Android/obb"); add("$ext/Android/media")
            if (viaShell) { add("/data/user/*"); add("/data/user_de/*") }
        }
        val dirs = roots.flatMap { r ->
            if ('*' in r) Shell.run("ls -1d $r/* 2>/dev/null").lineSequence().map { it.trim() }.filter { it.startsWith("/") }.map { File(it) }.toList() else listDirs(r)
        }.filter { it.name !in have && pkgRe.matches(it.name) && !denied(it.name) && corpseRe.matches(it.path) }
        val sz = sizes(dirs.map { it.path })
        return dirs.groupBy { it.name }.mapNotNull { (pkg, ds) ->
            val f = ds.map { Finding(it.path, sz[it.path] ?: 0L, CORPSE) }
            if (f.isEmpty()) null else CleanRow(pkg, pkg, pkg, ds.joinToString(", ") { it.path.removePrefix(ext).removePrefix("/data/") }.take(120), f)
        }.sortedByDescending { it.size }
    }

    private fun corpseOk(ctx: Context, p: String): Boolean {
        if (!plain(p)) return false
        val m = corpseRe.matchEntire(p) ?: return false
        val pkg = m.groupValues[2]
        return !denied(pkg) && installed(ctx).let { it.size >= 20 && pkg !in it }
    }

    // ------------------------------------------------------------------ SystemCleaner

    private val trashNames = setOf(".thumbnails", "lost.dir", ".trash", ".trashbin", ".recycle", ".recyclebin")
    private val adDirs = listOf(".cache", ".tmp", ".umeng", ".um", ".utsystemconfig", ".datastorage", ".gs_fs0", ".gs_fs1", "applovin", "facebook/audience", "miui/debug_log")
    private val stdDirs = setOf("Download", "DCIM", "Pictures", "Music", "Movies", "Documents", "Alarms", "Notifications", "Ringtones", "Podcasts", "Audiobooks", "Recordings")
    private val tempExt = setOf("tmp", "temp", "log", "bak")

    private fun isTrashDir(f: File) = f.name.lowercase().let { it in trashNames || it.startsWith(".trash-") }
    private fun rel(p: String) = p.removePrefix("$ext/")

    fun system(ctx: Context? = null): List<CleanRow> {
        val root = File(ext)
        val now = System.currentTimeMillis()
        val rows = LinkedHashMap<String, MutableList<Finding>>()
        fun add(title: String, f: File, size: Long, rule: String) { rows.getOrPut(title) { ArrayList() }.add(Finding(f.path, size, rule)) }
        fun dirSize(d: File) = d.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        val tops = root.listFiles().orEmpty().filter { it.isDirectory }
        (tops + tops.filter { it.name.equals("facebook", true) || it.name.equals("miui", true) }.flatMap { it.listFiles().orEmpty().filter { d -> d.isDirectory } })
            .filter { rel(it.path).lowercase() in adDirs }.forEach { add("Analytics and ads leftovers", it, dirSize(it), "ads") }
        root.walkTopDown().maxDepth(6).onEnter { d -> d.path != "$ext/Android" && d.parentFile?.let { !isTrashDir(it) } != false }.forEach { f ->
            if (f.path == ext || f.parentFile?.let { isTrashDir(it) } == true) return@forEach
            if (f.isDirectory && isTrashDir(f)) add(if (f.name.equals("lost.dir", true)) "LOST.DIR" else if (f.name.lowercase().contains("thumbnails")) "Thumbnails" else "Trash folders", f, dirSize(f), "trash")
            else if (f.isFile && f.extension.lowercase() in tempExt && now - f.lastModified() > TEMP_DAYS * 86400000L) add("Old temp, log and backup files", f, f.length(), "tmp")
        }
        File(root, "Download").walkTopDown().maxDepth(3).filter { it.isFile && it.extension.equals("apk", true) && now - it.lastModified() > APK_DAYS * 86400000L }
            .forEach { add("Old APKs in Download", it, it.length(), "apk") }
        if (ctx != null) ExpendableDb.scan(ctx).forEach { (d, title) -> add(title, d, dirSize(d), "db") }
        Cleaner.emptyDirs().filter { !it.file.path.startsWith("$ext/Android") && !(it.file.parent == ext && it.file.name in stdDirs) }.forEach { add("Empty folders", it.file, 0L, "empty") }
        return rows.map { (t, f) -> CleanRow(t, null, t, f.take(2).joinToString(", ") { rel(it.path) }, f) }
    }

    private fun systemOk(f: Finding, ctx: Context? = null): Boolean {
        val file = File(f.path)
        if (!plain(f.path) || !f.path.startsWith("$ext/") || f.path.startsWith("$ext/Android/") || f.path == "$ext/Android") return false
        val now = System.currentTimeMillis()
        return when (f.rule) {
            "trash" -> file.isDirectory && isTrashDir(file)
            "ads" -> file.isDirectory && rel(f.path).lowercase() in adDirs
            "tmp" -> file.isFile && file.extension.lowercase() in tempExt && now - file.lastModified() > TEMP_DAYS * 86400000L
            "apk" -> file.isFile && file.extension.equals("apk", true) && f.path.startsWith("$ext/Download/") && now - file.lastModified() > APK_DAYS * 86400000L
            "empty" -> file.isDirectory && file.list()?.isEmpty() == true
            "db" -> ctx != null && file.isDirectory && ExpendableDb.allowed(ctx, f.path)
            else -> false
        }
    }

    // ------------------------------------------------------------------ deletion

    /** Deletes one finding after re-checking its rule. App junk folders are emptied and kept; the rest is removed. Returns bytes freed. */
    fun delete(ctx: Context, f: Finding): Long {
        val ok = when (f.rule) {
            CACHE, APP -> appJunkOk(f.path)
            CORPSE -> corpseOk(ctx, f.path)
            else -> systemOk(f, ctx)
        }
        if (!ok) return 0L
        val keep = f.rule == CACHE || f.rule == APP
        if (viaShell && f.rule in setOf(CACHE, APP, CORPSE) && safeArg(f.path)) {
            Shell.run(if (keep) "rm -rf '${f.path}'/* '${f.path}'/.[!.]* 2>/dev/null" else "rm -rf '${f.path}' 2>/dev/null")
            return f.size
        }
        val file = File(f.path)
        if (keep) file.listFiles()?.forEach { runCatching { it.deleteRecursively() } } else runCatching { file.deleteRecursively() }
        return f.size
    }
}
