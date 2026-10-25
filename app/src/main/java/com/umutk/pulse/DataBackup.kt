package com.umutk.pulse

import android.content.Context
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Settings and files in one zip: every shared-preferences file (Pulse, looks, extra apps) and the user data under files/.
 * Everything under files/ goes in except what Pulse rebuilds by itself (see DROP), and secrets are left out (the Authenticator accounts only travel when the user asks, sealed with a password of theirs). The zip layout (shared_prefs/ and files/
 * entries) is the same as before, so older backups still import. Import first unpacks into a staging folder, so a
 * broken zip changes nothing.
 */
object DataBackup {
    private const val PREFS = "shared_prefs/"
    private const val FILES = "files/"

    /** shared_prefs files that never leave the phone: Spotify login tokens and the app-lock PIN hash. */
    private val SKIP_PREFS = setOf("spotify.xml", "applock.xml")

    /** What the backup leaves out of files/: things Pulse downloads or builds again by itself (covers, search indexes, word lists, the store, caches), temp and broken files, and anything over [MAX_FILE]. Everything else the user made is in. */
    private val DROP = setOf("covers", "artist_art", "lyrics-cache", "lyrics-hits", "lyrics_index.gz", "music_library.tsv", "widget_blur_bg.jpg", "store", "kbd", "restore")
    private const val MAX_FILE = 100L * 1024 * 1024
    private fun keepFile(rel: String, size: Long) =
        size <= MAX_FILE && DROP.none { rel == it || rel.startsWith("$it/") } && !rel.endsWith(".tmp") && !rel.endsWith(".bad") && !rel.startsWith("autobackup.")

    /** What a restore put back, for the message shown after import. */
    class Restored(val files: Int, val prefs: List<String>, val folders: List<String>)

    private fun prefsDir(ctx: Context) = File(ctx.applicationInfo.dataDir, "shared_prefs")

    /** Every file the export writes, as (zip name, source file). */
    private fun entries(ctx: Context): List<Pair<String, File>> {
        val pd = prefsDir(ctx)
        val prefs = pd.walkTopDown().filter { it.isFile && it.name !in SKIP_PREFS }
            .map { PREFS + it.relativeTo(pd).invariantSeparatorsPath to it }
        val fd = ctx.filesDir
        val files = fd.walkTopDown().filter { it.isFile && keepFile(it.relativeTo(fd).invariantSeparatorsPath, it.length()) }
            .map { FILES + it.relativeTo(fd).invariantSeparatorsPath to it }
        return prefs.toList() + files
    }

    /** File count and total bytes that an export would write, shown before the user saves. */
    fun summary(ctx: Context): Pair<Int, Long> = entries(ctx).let { e -> val a = com.umutk.pulse.features.AuthBackup.seal(ctx); (e.size + (if (a != null) 1 else 0)) to (e.sumOf { it.second.length() } + (a?.size ?: 0)) }

    /** Writes the zip to [out] and returns how many files it holds. */
    fun export(ctx: Context, out: OutputStream): Int {
        val list = entries(ctx)
        var extra = 0
        ZipOutputStream(out).use { zip ->
            list.forEach { (name, f) ->
                zip.putNextEntry(ZipEntry(name))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
            // the Authenticator accounts, when the user asked for them: sealed with their own password (see AuthBackup)
            com.umutk.pulse.features.AuthBackup.seal(ctx)?.let { zip.putNextEntry(ZipEntry(FILES + "auth-backup.enc")); zip.write(it); zip.closeEntry(); extra = 1 }
        }
        return list.size + extra
    }

    /** Copies a backup from [input] over the current settings and files. Files = 0 when the zip holds nothing Pulse knows. */
    fun restore(ctx: Context, input: InputStream): Restored {
        val stage = File(ctx.cacheDir, "restore").apply { deleteRecursively(); mkdirs() }
        var n = 0
        ZipInputStream(input).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                if (e.isDirectory) continue
                val prefix = listOf(PREFS, FILES).firstOrNull { e.name.startsWith(it) } ?: continue
                val rel = e.name.removePrefix(prefix)
                // no absolute paths and no ".." parts: a zip must not write outside its own folders
                if (rel.isEmpty() || rel.startsWith("/") || rel.split('/').any { it == ".." || it.isEmpty() }) continue
                val target = File(stage, prefix + rel)
                target.parentFile?.mkdirs()
                target.outputStream().use { zip.copyTo(it) }
                n++
            }
        }
        if (n == 0) return Restored(0, emptyList(), emptyList())
        val stagedPrefs = File(stage, PREFS.dropLast(1))
        val stagedFiles = File(stage, FILES.dropLast(1))
        val prefs = stagedPrefs.walkTopDown().filter { it.isFile }.map { it.name.removeSuffix(".xml") }.sorted().toList()
        val folders = stagedFiles.listFiles().orEmpty().map { it.name }.sorted()
        copyInto(stagedPrefs, prefsDir(ctx))
        copyInto(stagedFiles, ctx.filesDir)
        stage.deleteRecursively()
        return Restored(n, prefs, folders)
    }

    /** Copies every file under [from] into [to], keeping the folder layout. Existing files with the same name are replaced. */
    private fun copyInto(from: File, to: File) {
        if (!from.isDirectory) return
        from.walkTopDown().filter { it.isFile }.forEach { f ->
            val dest = File(to, f.relativeTo(from).invariantSeparatorsPath)
            dest.parentFile?.mkdirs()
            f.copyTo(dest, overwrite = true)
        }
    }
}
