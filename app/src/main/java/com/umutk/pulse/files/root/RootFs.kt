package com.umutk.pulse.files.root

import com.umutk.pulse.Shell
import com.umutk.pulse.tr
import java.io.File

/**
 * Root access for the Files app: folders the app cannot read (/data, other apps' Android/data and obb)
 * are listed through one `su -c ls` call. Read-only. Our own code, written from the idea of Material Files'
 * root service (GPL, not copied); no libsu dependency.
 * shortcut: names with a newline inside are cut at the newline, and a whole folder is one su call with no cache, upgrade to a root service when large folders feel slow.
 */
object RootFs {
    sealed class Result {
        class Ok(val entries: List<Entry>) : Result()
        /** no su and no Shizuku grant: the caller shows the "root needed" message */
        object NoRoot : Result()
        class Failed(val msg: String) : Result()
    }

    /** [size] in bytes and [mtime] in milliseconds when the long listing gave them (0 when it did not). */
    class Entry(val name: String, val dir: Boolean, val size: Long = 0L, val mtime: Long = 0L)

    /** Folders the app cannot list by itself: they only show up through root. */
    fun needsRoot(path: String) = path.startsWith("/data") || path.contains("/Android/data") || path.contains("/Android/obb")

    /** Files can start without Pulse's main screen, so the root check may never have run: run it once here. */
    fun ensureChecked() { if (Shell.root == null) Shell.detectRoot() }

    fun available() = Shell.root == true || Shell.shizuku == 2

    fun list(dir: File): Result {
        if (!available()) return Result.NoRoot
        val q = dir.path.replace("'", "'\\''")
        // the exit code is echoed last, so an su refusal or a missing folder is not mistaken for an empty one
        // the names first (safe with any characters), then the long listing for the size and the date of each
        val cmd = "ls -1Ap -- '$q' 2>&1; echo __rc=\$?; ls -lAp -- '$q' 2>/dev/null"
        var mode = -1
        var out: String
        if (Shell.root == true && needsRoot(dir.path)) {
            // shell-uid Shizuku cannot read other apps' data: pick the root mount view that sees the folder (/data/data may only exist as /data/user/0 or /data_mirror there)
            val alt = Regex("^/data/(?:data|user/0)/([^/]+)(/.*)?$").find(dir.path)?.let { m -> Shell.dataPaths(m.groupValues[1]).map { it + m.groupValues[2] } }
            val seen = Shell.rootSee(alt ?: listOf(dir.path)) ?: return Result.Failed(tr("Root cannot see this folder", "Root bu klasörü göremiyor"))
            mode = seen.first
            out = Shell.runRoot(cmd.replace(q, seen.second.replace("'", "'\\''")), mode)
        } else out = Shell.run(cmd)
        val all = out.lines().filter { it.isNotBlank() }
        val at = all.indexOfFirst { it.startsWith("__rc=") }
        if (at < 0) return Result.Failed("no answer from su")
        val rc = all[at].removePrefix("__rc=").toIntOrNull() ?: return Result.Failed("no answer from su")
        val body = all.subList(0, at)
        if (rc != 0) return Result.Failed(body.lastOrNull() ?: "ls exit $rc")
        // the long lines: "drwxrwx--x 4 u0_a1 u0_a1 4096 2026-11-07 21:30 name/" -> name to (size, date)
        val info = HashMap<String, Pair<Long, Long>>()
        val re = Regex("^[\\-dlcbps][rwxsStT\\-]{9}\\S*\\s+\\d+\\s+\\S+\\s+\\S+\\s+(\\d+)(?:,\\s*\\d+)?\\s+(\\d{4}-\\d{2}-\\d{2})\\s+(\\d{2}:\\d{2})\\s+(.*)$")
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
        for (l in all.subList(at + 1, all.size)) {
            val m = re.find(l) ?: continue
            val name = m.groupValues[4].substringBefore(" -> ").removeSuffix("/")
            val ms = runCatching { fmt.parse(m.groupValues[2] + " " + m.groupValues[3])?.time }.getOrNull() ?: 0L
            info[name] = (m.groupValues[1].toLongOrNull() ?: 0L) to ms
        }
        return Result.Ok(body.map { n ->
            val dir = n.endsWith("/"); val name = if (dir) n.dropLast(1) else n
            val i = info[name]
            Entry(name, dir, if (dir) 0L else i?.first ?: 0L, i?.second ?: 0L)
        })
    }
}
