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

    class Entry(val name: String, val dir: Boolean)

    /** Folders the app cannot list by itself: they only show up through root. */
    fun needsRoot(path: String) = path.startsWith("/data") || path.contains("/Android/data") || path.contains("/Android/obb")

    /** Files can start without Pulse's main screen, so the root check may never have run: run it once here. */
    fun ensureChecked() { if (Shell.root == null) Shell.detectRoot() }

    fun available() = Shell.root == true || Shell.shizuku == 2

    fun list(dir: File): Result {
        if (!available()) return Result.NoRoot
        val q = dir.path.replace("'", "'\\''")
        // the exit code is echoed last, so an su refusal or a missing folder is not mistaken for an empty one
        val cmd = "ls -1Ap -- '$q' 2>&1; echo __rc=\$?"
        var mode = -1
        var out: String
        if (Shell.root == true && needsRoot(dir.path)) {
            // shell-uid Shizuku cannot read other apps' data: pick the root mount view that sees the folder (/data/data may only exist as /data/user/0 or /data_mirror there)
            val alt = Regex("^/data/(?:data|user/0)/([^/]+)(/.*)?$").find(dir.path)?.let { m -> Shell.dataPaths(m.groupValues[1]).map { it + m.groupValues[2] } }
            val seen = Shell.rootSee(alt ?: listOf(dir.path)) ?: return Result.Failed(tr("Root cannot see this folder", "Root bu klasörü göremiyor"))
            mode = seen.first
            out = Shell.runRoot(cmd.replace(q, seen.second.replace("'", "'\\''")), mode)
        } else out = Shell.run(cmd)
        val lines = out.lines().filter { it.isNotBlank() }
        val rc = lines.lastOrNull()?.removePrefix("__rc=")?.toIntOrNull() ?: return Result.Failed("no answer from su")
        val body = lines.dropLast(1)
        if (rc != 0) return Result.Failed(body.lastOrNull() ?: "ls exit $rc")
        return Result.Ok(body.map { if (it.endsWith("/")) Entry(it.dropLast(1), true) else Entry(it, false) })
    }
}
