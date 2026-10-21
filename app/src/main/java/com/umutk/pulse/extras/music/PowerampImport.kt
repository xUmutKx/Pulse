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
        // library indexes: the folder path below the storage root, then the bare file name
        val byRel = HashMap<String, Track>(); val byName = HashMap<String, MutableList<Track>>()
        library.forEach { t ->
            val rel = t.path.substringAfter("/storage/", t.path).substringAfter('/').lowercase()   // "emulated/0/Music/..." -> "0/Music/..."
            byRel[t.path.lowercase().substringAfter("/0/")] = t
            byRel[rel] = t
            byName.getOrPut(File(t.path).name.lowercase()) { ArrayList() } += t
        }
        fun find(p: String): Track? {
            val rel = p.substringAfter('/').lowercase()   // "primary/Music/Audio/X.m4a" -> "music/audio/x.m4a"
            byRel[rel]?.let { return it }
            return byName[p.substringAfterLast('/').lowercase()]?.singleOrNull()
        }
        var playlists = 0; var ratings = 0; var plays = 0; var missing = 0
        db?.let { f ->
            val d = SQLiteDatabase.openDatabase(f.path, null, SQLiteDatabase.OPEN_READONLY)
            try {
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
