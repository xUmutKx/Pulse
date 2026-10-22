package com.umutk.pulse.extras.music

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import com.umutk.pulse.extras.music.poweramp.PowerampCovers
import com.umutk.pulse.extras.music.stats.MusicStats
import java.io.File
import java.util.zip.ZipInputStream

/**
 * Reads a Poweramp backup (.poweramp-backup, a zip): the lists database (playlists, ratings, play counts) and, when the backup has them,
 * the album_art pictures named "artist - title.jpg". Songs are matched to the library by their folder path, then by file name.
 * The settings file of the backup is encrypted by Poweramp and is not read.
 */
object PowerampImport {
    class Result(val playlists: Int, val ratings: Int, val plays: Int, val covers: Int, val embedded: Int, val missing: Int)

    private fun nfc(s: String) = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFC)

    private fun fold(s: String) = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFKD).replace(Regex("\\p{M}+"), "").lowercase().filter { it.isLetterOrDigit() }

    /** [library] is the scanned songs; [embed] also writes the pictures into the MP3 files themselves. Blocking: call off the main thread. */
    fun run(ctx: Context, uri: Uri, library: List<Track>, embed: Boolean): Result {
        val work = File(ctx.cacheDir, "pa_import").apply { deleteRecursively(); mkdirs() }
        var db: File? = null
        val pics = ArrayList<File>()
        ctx.contentResolver.openInputStream(uri)?.use { raw ->
            ZipInputStream(raw.buffered()).use { z ->
                var e = z.nextEntry
                while (e != null) {
                    val n = e.name
                    if (!e.isDirectory) {
                        val lower = n.lowercase()
                        if (lower.endsWith("lists-export")) File(work, "lists.db").also { f -> f.outputStream().use { z.copyTo(it) }; db = f }
                        else if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")) {
                            File(work, "art_${pics.size}_" + n.substringAfterLast('/')).also { f -> f.outputStream().use { z.copyTo(it) }; pics += f }
                        }
                    }
                    e = z.nextEntry
                }
            }
        }
        return process(ctx, work, db, pics, library, embed, live = false)
    }

    /**
     * Reads Poweramp's own data with root: its library database (every rating and play count, not only the playlists a backup keeps) and the
     * album_art pictures it saved. Blocking: call off the main thread. Null when there is no root or Poweramp is not installed.
     */
    fun direct(ctx: Context, library: List<Track>, embed: Boolean): Result? {
        if (com.umutk.pulse.Shell.root != true) return null
        val pkg = "com.maxmpz.audioplayer"
        val work = File(ctx.cacheDir, "pa_import").apply { deleteRecursively(); mkdirs() }
        val src = "/data/data/$pkg/databases"
        val names = com.umutk.pulse.Shell.runRoot("ls -1 $src 2>/dev/null").lines().map { it.trim() }.filter { it.isNotEmpty() && !it.endsWith("-journal") }
        if (names.isEmpty()) return null
        // copy the databases with their write-ahead logs, so the newest ratings are in
        com.umutk.pulse.Shell.runRoot("for f in $src/*; do cp \"\$f\" '${work.path}/' 2>/dev/null; done; chmod -R 666 '${work.path}' 2>/dev/null")
        var db: File? = null
        for (n in names.filter { !it.endsWith("-wal") && !it.endsWith("-shm") }) {
            val f = File(work, n)
            val has = runCatching {
                SQLiteDatabase.openDatabase(f.path, null, SQLiteDatabase.OPEN_READWRITE).use { d ->
                    d.rawQuery("select 1 from sqlite_master where name='folder_files'", null).use { it.moveToFirst() }
                }
            }.getOrDefault(false)
            if (has) { db = f; break }
        }
        db ?: return null
        // the pictures: Poweramp's album_art folders on shared storage
        val pics = ArrayList<File>()
        val picDirs = listOf("/storage/emulated/0/Poweramp/album_art", "/storage/emulated/0/Android/data/$pkg/files/album_art", "/storage/emulated/0/Android/data/$pkg/files/Poweramp/album_art")
        picDirs.forEach { d ->
            File(d).listFiles()?.filter { it.isFile && it.extension.lowercase() in setOf("jpg", "jpeg", "png") }?.forEach { f ->
                File(work, "art_${pics.size}_" + f.name).also { o -> runCatching { f.copyTo(o, true); pics += o } }
            }
        }
        return process(ctx, work, db, pics, library, embed, live = true)
    }

    private fun process(ctx: Context, work: File, db: File?, pics: List<File>, library: List<Track>, embed: Boolean, live: Boolean): Result {
        // library indexes: the folder path below the storage root, then the bare file name
        val byStem = HashMap<String, MutableList<Track>>()
        val byRel = HashMap<String, Track>(); val byName = HashMap<String, MutableList<Track>>()
        library.forEach { t ->
            val rel = nfc(t.path).substringAfter("/storage/", nfc(t.path)).substringAfter('/').lowercase()   // "emulated/0/Music/..." -> "0/Music/..."
            byRel[nfc(t.path).lowercase().substringAfter("/0/")] = t
            byRel[rel] = t
            byName.getOrPut(nfc(File(t.path).name).lowercase()) { ArrayList() } += t
            byStem.getOrPut(fold(File(t.path).nameWithoutExtension)) { ArrayList() } += t
        }
        // the same file name in several folders: the one whose folder name is also the backup's folder name wins
        fun pick(c: List<Track>?, p: String): Track? {
            if (c.isNullOrEmpty()) return null
            if (c.size == 1) return c[0]
            val dir = fold(p.substringBeforeLast('/', "").substringAfterLast('/'))
            return c.firstOrNull { fold(File(it.path).parentFile?.name.orEmpty()) == dir }
        }
        fun find(p: String): Track? {
            val rel = nfc(p).substringAfter('/').lowercase()   // "primary/Music/Audio/X.m4a" -> "music/audio/x.m4a"
            byRel[rel]?.let { return it }
            pick(byName[nfc(p.substringAfterLast('/')).lowercase()], p)?.let { return it }
            // the song was converted or renamed (x.m4a -> x.mp3): the name without its extension
            return pick(byStem[fold(p.substringAfterLast('/').substringBeforeLast('.'))], p)
        }
        var playlists = 0; var ratings = 0; var plays = 0; var missing = 0
        db?.let { f ->
            val d = SQLiteDatabase.openDatabase(f.path, null, if (live) SQLiteDatabase.OPEN_READWRITE else SQLiteDatabase.OPEN_READONLY)
            try {
                if (live) {
                    // Poweramp's library: every song with its rating and play count; the path is the folder's path plus the file name
                    val cols = d.rawQuery("pragma table_info(folder_files)", null).use { c -> generateSequence { if (c.moveToNext()) c.getString(1) else null }.toList() }
                    val playCol = listOf("played_times", "total_played_times", "play_count").firstOrNull { it in cols }
                    d.rawQuery("select COALESCE(folder_files.file_path, folders.path || folder_files.name, folder_files.name), folder_files.rating" + (if (playCol != null) ", folder_files.$playCol" else ", 0") +
                        " from folder_files left join folders on folders._id = folder_files.folder_id where folder_files.rating > 0" + (if (playCol != null) " or folder_files.$playCol > 0" else ""), null).use { c ->
                        while (c.moveToNext()) {
                            val p = c.getString(0) ?: continue
                            val t = find(p.removePrefix("/storage/").removePrefix("emulated/0/")) ?: find(p) ?: run { missing++; null } ?: continue
                            val r = c.getInt(1); val n = c.getInt(2)
                            if (r in 1..5) { MusicStats.setRating(ctx, t.path, r); ratings++ }
                            repeat(n.coerceIn(0, 500)) { MusicStats.add(ctx, t.path, t.title, t.artist, t.album, 0L, true) }
                            if (n > 0) plays++
                        }
                    }
                    return@let
                }
                d.rawQuery("select _id, name from playlists", null).use { pl ->
                    while (pl.moveToNext()) {
                        val id = pl.getLong(0); val name = pl.getString(1).substringBefore(" (Imported").trim().ifBlank { "Poweramp $id" }
                        val list = ArrayList<Track>()
                        d.rawQuery("select path from tracks where playlist_id = ? order by _id", arrayOf(id.toString())).use { c ->
                            while (c.moveToNext()) { val p = c.getString(0); if (p.startsWith("http")) continue; find(p)?.let { list += it } ?: run { missing++ } }
                        }
                        if (list.isNotEmpty()) { Playlists.save(ctx, name, list); playlists++ }
                    }
                }
                d.rawQuery("select path, rating, played_times, total_played_times from tracks where (rating > 0 or total_played_times > 0 or played_times > 0) and path not like 'http%' group by path", null).use { c ->
                    while (c.moveToNext()) {
                        val t = find(c.getString(0)) ?: continue
                        val r = c.getInt(1); val n = maxOf(c.getInt(2), c.getInt(3))
                        if (r in 1..5) { MusicStats.setRating(ctx, t.path, r); ratings++ }
                        repeat(n.coerceAtMost(500)) { MusicStats.add(ctx, t.path, t.title, t.artist, t.album, 0L, true) }
                        if (n > 0) plays++
                    }
                }
            } finally { d.close() }
        }
        // covers: "Artist - Title.jpg" against "artist - title", then against the title alone when only one song has it
        var covers = 0; var embedded = 0
        val byFull = HashMap<String, Track>(); val byTitle = HashMap<String, MutableList<Track>>()
        library.forEach { t ->
            val title = t.title?.takeIf { it.isNotBlank() } ?: File(t.path).nameWithoutExtension
            byFull[fold((t.artist.orEmpty() + title))] = t
            byFull[fold(File(t.path).nameWithoutExtension)] = t
            byTitle.getOrPut(fold(title)) { ArrayList() } += t
        }
        pics.forEach { f ->
            val base = f.nameWithoutExtension.substringAfter("_", f.nameWithoutExtension).substringAfter("_")
            val key = fold(base.replace(" - ", ""))
            val t = byFull[key] ?: byFull[fold(base)] ?: byTitle[fold(base.substringAfter(" - "))]?.singleOrNull() ?: return@forEach
            val bytes = f.readBytes()
            PowerampCovers.save(ctx, t.path, bytes); covers++
            if (embed && Id3Frames.putFrame(t.path, "APIC", Id3Frames.coverBody(bytes))) embedded++
        }
        work.deleteRecursively()
        return Result(playlists, ratings, plays, covers, embedded, missing)
    }
}