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
    class Result(val playlists: Int, val ratings: Int, val plays: Int, val covers: Int, val embedded: Int, val missing: Int, val missingNames: List<String> = emptyList(), val artists: Int = 0)

    /** Why the last direct import returned null. */
    @Volatile var lastError = ""

    private fun nfc(s: String) = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFC)

    private fun fold(s: String) = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFKD).replace(Regex("\\p{M}+"), "").lowercase().filter { it.isLetterOrDigit() }

    /** [library] is the scanned songs; [embed] also writes the pictures into the MP3 files themselves. Blocking: call off the main thread. */
    fun run(ctx: Context, uri: Uri, library: List<Track>, embed: Boolean): Result {
        val work = File(ctx.cacheDir, "pa_import").apply { deleteRecursively(); mkdirs() }
        var db: File? = null
        val pics = ArrayList<File>()
        var artists = 0
        ctx.contentResolver.openInputStream(uri)?.use { raw ->
            ZipInputStream(raw.buffered()).use { z ->
                var e = z.nextEntry
                while (e != null) {
                    val n = e.name
                    if (!e.isDirectory) {
                        val lower = n.lowercase()
                        if (lower.endsWith("lists-export")) File(work, "lists.db").also { f -> f.outputStream().use { z.copyTo(it) }; db = f }
                        else if ((lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")) && lower.replace(' ', '_').contains("artist_art")) {
                            // an artist picture: "Artist.jpg" inside an artist_art folder
                            runCatching { ArtistArt.save(ctx, n.substringAfterLast('/').substringBeforeLast('.'), z.readBytes()); artists++ }
                        }
                        else if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")) {
                            File(work, "art_${pics.size}_" + n.substringAfterLast('/')).also { f -> f.outputStream().use { z.copyTo(it) }; pics += f }
                        }
                    }
                    e = z.nextEntry
                }
            }
        }
        val r = process(ctx, work, db, pics, library, embed, live = false)
        return Result(r.playlists, r.ratings, r.plays, r.covers, r.embedded, r.missing, r.missingNames, artists)
    }

    /**
     * Reads Poweramp's own data with root: its library database (every rating and play count, not only the playlists a backup keeps) and the
     * album_art pictures it saved. Blocking: call off the main thread. Null when there is no root or Poweramp is not installed.
     */
    fun direct(ctx: Context, library: List<Track>, embed: Boolean): Result? {
        lastError = ""
        val pkg = "com.maxmpz.audioplayer"
        val work = File(ctx.cacheDir, "pa_import").apply { deleteRecursively(); mkdirs() }
        // plain su may run in an isolated mount view that hides other apps' data: find the root view that really sees Poweramp's databases
        val why = StringBuilder()
        val seen = com.umutk.pulse.Shell.rootSee(listOf("/data/data/$pkg/databases", "/data/user/0/$pkg/databases"), why)
        if (seen == null) { lastError = why.toString().ifBlank { "root refused or Poweramp not installed" }; return null }
        val (mode, src) = seen
        val names = com.umutk.pulse.Shell.runRoot("ls -1 $src 2>/dev/null", mode).lines().map { it.trim() }.filter { it.isNotEmpty() && !it.endsWith("-journal") }
        if (names.isEmpty()) { lastError = "no databases in $src"; return null }
        // copy the databases with their write-ahead logs, so the newest ratings are in
        com.umutk.pulse.Shell.runRoot("for f in $src/*; do cp \"\$f\" '${work.path}/' 2>/dev/null; done; chmod 777 '${work.path}'; chmod 666 '${work.path}'/* 2>/dev/null", mode)
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
        if (db == null) { lastError = "no library database among ${names.size} files"; return null }
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
            // a bare file name from an old playlist may be the title, or "artist - title", of a song stored under another file name
            t.title?.takeIf { it.isNotBlank() }?.let { ti ->
                byStem.getOrPut(fold(ti)) { ArrayList() } += t
                if (!t.artist.isNullOrBlank()) byStem.getOrPut(fold(t.artist + ti)) { ArrayList() } += t
            }
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
            val stem = fold(p.substringAfterLast('/').substringBeforeLast('.'))
            pick(byStem[stem], p)?.let { return it }
            // last resort: the phone's name holds the backup's name ("Artist - Song.mp3" for "Song.mp3") or the other way round
            if (stem.length >= 6) {
                val hit = byStem.entries.filter { (k, _) -> k.length >= 6 && (k.endsWith(stem) || stem.endsWith(k) || k.contains(stem)) }.flatMap { it.value }.distinct()
                if (hit.size == 1) return hit[0]
            }
            return null
        }
        var playlists = 0; var ratings = 0; var plays = 0; var missing = 0
        val missingNames = ArrayList<String>()
        db?.let { f ->
            val d = SQLiteDatabase.openDatabase(f.path, null, if (live) SQLiteDatabase.OPEN_READWRITE else SQLiteDatabase.OPEN_READONLY)
            try {
                if (live) {
                    // Poweramp's library: every song with its rating and play count. The columns are read from the database itself, so a different
                    // Poweramp version cannot break the query; when none matches, the column names go to the message.
                    fun columnsOf(t: String) = d.rawQuery("pragma table_info($t)", null).use { c -> generateSequence { if (c.moveToNext()) c.getString(1) else null }.toList() }
                    val cols = columnsOf("folder_files"); val fcols = columnsOf("folders")
                    // Poweramp keeps the path in file_path for some songs and as folder path + name for the rest: every form is tried, as Poweramp itself does
                    val parts = listOfNotNull(
                        "folder_files.file_path".takeIf { "file_path" in cols },
                        "(folders.path || folder_files.name)".takeIf { "path" in fcols && "name" in cols },
                        "folder_files.name".takeIf { "name" in cols },
                    )
                    val pathExpr = if (parts.isEmpty()) null else "COALESCE(" + parts.joinToString(", ") + ")"
                    val ratingCol = listOf("rating", "user_rating", "stars").firstOrNull { it in cols }
                    val playCols = listOf("played_times", "total_played_times", "play_count", "played_count", "plays").filter { it in cols }
                    if (pathExpr == null || (ratingCol == null && playCols.isEmpty())) { lastError = "folder_files: $cols; folders: $fcols"; return@let }
                    val ratingSql = ratingCol?.let { "COALESCE(folder_files.$it, 0)" } ?: "0"
                    val playSql = when { playCols.isEmpty() -> "0"; playCols.size == 1 -> "COALESCE(folder_files.${playCols[0]}, 0)"; else -> "max(" + playCols.joinToString(", ") { "COALESCE(folder_files.$it, 0)" } + ")" }
                    // the rating scale: stars 0..5, tenths of stars 0..10, or percent 0..100
                    val topRating = ratingCol?.let { d.rawQuery("select max($it) from folder_files", null).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 } } ?: 0
                    fun stars(r: Int) = when { topRating > 10 -> (r + 10) / 20; topRating > 5 -> (r + 1) / 2; else -> r }.coerceIn(0, 5)
                    var scanned = 0
                    d.rawQuery("select $pathExpr, $ratingSql, $playSql from folder_files left join folders on folders._id = folder_files.folder_id where $ratingSql > 0 or $playSql > 0", null).use { c ->
                        while (c.moveToNext()) {
                            val p = c.getString(0) ?: continue
                            scanned++
                            val t = find(p.removePrefix("/storage/").removePrefix("emulated/0/")) ?: find(p) ?: run { missing++; null } ?: continue
                            val r = stars(c.getInt(1)); val n = c.getInt(2)
                            if (r in 1..5) { MusicStats.setRating(ctx, t.path, r); ratings++ }
                            repeat(n.coerceIn(0, 500)) { MusicStats.add(ctx, t.path, t.title, t.artist, t.album, 0L, true) }
                            if (n > 0) plays++
                        }
                    }
                    // the playlists of the library (the name is in playlists.playlist, the songs in playlist_entries)
                    val tables = d.rawQuery("select name from sqlite_master where type='table'", null).use { c -> generateSequence { if (c.moveToNext()) c.getString(0) else null }.toSet() }
                    if ("playlists" in tables && "playlist_entries" in tables) {
                        val pcols = columnsOf("playlists"); val ecols = columnsOf("playlist_entries")
                        val nameCol = listOf("playlist", "name").firstOrNull { it in pcols }
                        if (nameCol != null) {
                            val epath = listOfNotNull(
                                "folder_files.file_path".takeIf { "file_path" in cols },
                                "(folders.path || folder_files.name)".takeIf { "path" in fcols && "name" in cols },
                                "(playlist_entries.folder_path || playlist_entries.file_name)".takeIf { "folder_path" in ecols && "file_name" in ecols },
                                "playlist_entries.file_name".takeIf { "file_name" in ecols },
                            )
                            if (epath.isNotEmpty()) d.rawQuery("select _id, $nameCol from playlists", null).use { pl ->
                                while (pl.moveToNext()) {
                                    val id = pl.getLong(0); val name = (pl.getString(1) ?: "").substringBefore(" (Imported").trim().ifBlank { "Poweramp $id" }
                                    val list = ArrayList<Track>()
                                    d.rawQuery("select COALESCE(${epath.joinToString(", ")}) from playlist_entries left join folder_files on folder_files._id = playlist_entries.folder_file_id left join folders on folders._id = folder_files.folder_id where playlist_entries.playlist_id = ? order by playlist_entries._id", arrayOf(id.toString())).use { c ->
                                        while (c.moveToNext()) { val p = c.getString(0) ?: continue; if (p.startsWith("http")) continue; (find(p.removePrefix("/storage/").removePrefix("emulated/0/")) ?: find(p))?.let { list += it } ?: run { missing++ } }
                                    }
                                    if (list.isNotEmpty()) { Playlists.save(ctx, name, list); playlists++ }
                                }
                            }
                        }
                    }
                    if (ratings == 0 && plays == 0) lastError = "no rated or played song matched ($scanned read, $missing not on this phone); columns: rating=$ratingCol plays=$playCols top=$topRating"
                    return@let
                }
                d.rawQuery("select _id, name from playlists", null).use { pl ->
                    while (pl.moveToNext()) {
                        val id = pl.getLong(0); val name = pl.getString(1).substringBefore(" (Imported").trim().ifBlank { "Poweramp $id" }
                        val list = ArrayList<Track>()
                        d.rawQuery("select path, readable_name from tracks where playlist_id = ? order by _id", arrayOf(id.toString())).use { c ->
                            while (c.moveToNext()) {
                                val p = c.getString(0); if (p.startsWith("http")) continue
                                // by the path, else by the name Poweramp showed for the song
                                (find(p) ?: c.getString(1)?.takeIf { it.isNotBlank() && !it.startsWith("http") }?.let { n -> pick(byStem[fold(n)], n) })?.let { list += it }
                                    ?: run { missing++; if (missingNames.size < 5) missingNames += p.substringAfterLast('/') }
                            }
                        }
                        if (list.isNotEmpty()) { Playlists.save(ctx, name, list); playlists++ }
                    }
                }
                // the backup's rating scale: stars 0..5, tenths of stars 0..10 or percent; everything is kept as 1..5 stars
                val topBackup = d.rawQuery("select max(rating) from tracks", null).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
                fun toStars(r: Int) = when { topBackup > 10 -> (r + 10) / 20; topBackup > 5 -> (r + 1) / 2; else -> r }.coerceIn(0, 5)
                ctx.getSharedPreferences("pulse_poweramp", Context.MODE_PRIVATE).edit().putInt("ratingMul", when { topBackup > 10 -> 20; topBackup > 5 -> 2; else -> 1 }).apply()
                d.rawQuery("select path, rating, played_times, total_played_times from tracks where (rating > 0 or total_played_times > 0 or played_times > 0) and path not like 'http%' group by path", null).use { c ->
                    while (c.moveToNext()) {
                        val t = find(c.getString(0)) ?: continue
                        val r = toStars(c.getInt(1)); val n = maxOf(c.getInt(2), c.getInt(3))
                        if (r in 1..5) { MusicStats.setRating(ctx, t.path, r); ratings++ }
                        repeat(n.coerceAtMost(500)) { MusicStats.add(ctx, t.path, t.title, t.artist, t.album, 0L, true) }
                        if (n > 0) plays++
                    }
                }
            } finally { d.close() }
        }
        val (covers, embedded) = matchCovers(ctx, pics, library, embed)
        work.deleteRecursively()
        return Result(playlists, ratings, plays, covers, embedded, missing, missingNames)
    }


    /** The album pictures: "Artist - Title.jpg" against "artist - title", then against the title alone when only one song has it. Returns (covers saved, written into files). */
    private fun matchCovers(ctx: Context, pics: List<File>, library: List<Track>, embed: Boolean): Pair<Int, Int> {
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
        return covers to embedded
    }

    /**
     * Pictures out of a folder the person picked (the folder that holds album_art and artist_art, or one of them): album pictures are named
     * "Artist - Title.jpg" and become covers, artist pictures are named "Artist.jpg" and show in the artists list. Blocking: call off the main thread.
     * Returns (covers, artist pictures, written into files).
     */
    fun pictures(ctx: Context, tree: Uri, library: List<Track>, embed: Boolean): Triple<Int, Int, Int> {
        val work = File(ctx.cacheDir, "pa_pics").apply { deleteRecursively(); mkdirs() }
        val albums = ArrayList<File>(); var artists = 0
        val cr = ctx.contentResolver
        fun walk(docId: String, inArtist: Boolean) {
            val kids = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
            val rows = ArrayList<Triple<String, String, Boolean>>()
            cr.query(kids, arrayOf(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID, android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME, android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)?.use { c ->
                while (c.moveToNext()) rows += Triple(c.getString(0), c.getString(1) ?: "", c.getString(2) == android.provider.DocumentsContract.Document.MIME_TYPE_DIR)
            }
            rows.forEach { (id, name, dir) ->
                if (dir) walk(id, inArtist || name.lowercase().replace(" ", "_").contains("artist"))
                else if (name.lowercase().let { it.endsWith(".jpg") || it.endsWith(".jpeg") || it.endsWith(".png") }) runCatching {
                    val bytes = cr.openInputStream(android.provider.DocumentsContract.buildDocumentUriUsingTree(tree, id))?.use { it.readBytes() } ?: return@runCatching
                    if (inArtist) { ArtistArt.save(ctx, name.substringBeforeLast('.'), bytes); artists++ }
                    else albums += File(work, "art_${albums.size}_$name").also { it.writeBytes(bytes) }
                }
            }
        }
        val root = android.provider.DocumentsContract.getTreeDocumentId(tree)
        walk(root, root.lowercase().replace(" ", "_").contains("artist"))
        val (covers, embedded) = matchCovers(ctx, albums, library, embed)
        work.deleteRecursively()
        return Triple(covers, artists, embedded)
    }

    private fun paPath(p: String): String = when {
        p.startsWith("/storage/emulated/0/") -> "primary/" + p.removePrefix("/storage/emulated/0/")
        p.startsWith("/storage/") -> p.removePrefix("/storage/")
        else -> p
    }

    private fun paType(p: String) = when (p.substringAfterLast('.', "").lowercase()) { "m4a", "mp4", "aac" -> 2; "opus" -> 18; else -> 0 }

    /**
     * Writes a Poweramp backup (.poweramp-backup, a zip) to [uri]: the lists-export database in Poweramp's own layout (playlists, and every rated or played
     * song with its rating and play count), so Poweramp's restore reads it. Poweramp's settings-export is encrypted and is not written.
     * Returns (playlists, songs with a rating or plays). Blocking: call off the main thread.
     */
    fun export(ctx: Context, uri: Uri): Pair<Int, Int> {
        val work = File(ctx.cacheDir, "pa_export").apply { deleteRecursively(); mkdirs() }
        val dbFile = File(work, "lists-export")
        val d = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        var lists = 0; var rows = 0
        try {
            d.execSQL("CREATE TABLE android_metadata (locale TEXT)")
            d.execSQL("INSERT INTO android_metadata VALUES ('en_US')")
            d.execSQL("CREATE TABLE playlists (_id INTEGER PRIMARY KEY, name TEXT NOT NULL,keep_list_pos INTEGER NOT NULL,keep_track_pos INTEGER NOT NULL)")
            d.execSQL("CREATE TABLE tracks (_id INTEGER PRIMARY KEY, playlist_id INTEGER,path TEXT NOT NULL, readable_name TEXT, file_type INTEGER, cue_offset_ms INTEGER, rating INTEGER, played_at INTEGER, played_fully_at INTEGER, played_times INTEGER, last_pos INTEGER, export_type INTEGER NOT NULL, preset_id INTEGER, total_played_times INTEGER)")
            val now = System.currentTimeMillis()
            d.beginTransaction()
            try {
                Playlists.names(ctx).forEachIndexed { i, name ->
                    val id = i + 1L
                    d.execSQL("INSERT INTO playlists (_id, name, keep_list_pos, keep_track_pos) VALUES (?, ?, 0, 0)", arrayOf<Any>(id, name))
                    Playlists.load(ctx, name).forEach { t ->
                        d.execSQL("INSERT INTO tracks (playlist_id, path, readable_name, file_type, cue_offset_ms, rating, played_at, played_fully_at, played_times, last_pos, export_type, total_played_times) VALUES (?, ?, ?, ?, 0, 0, ?, 0, 0, 0, 1, 0)",
                            arrayOf<Any>(id, paPath(t.path), t.title ?: File(t.path).nameWithoutExtension, paType(t.path), now))
                    }
                    lists++
                }
                val mul = ctx.getSharedPreferences("pulse_poweramp", Context.MODE_PRIVATE).getInt("ratingMul", 1)
                val ratings = MusicStats.ratings(ctx)
                val stats = MusicStats.all(ctx).associateBy { it.path }
                (ratings.keys + stats.keys).toSet().forEach { p ->
                    val r = ratings[p] ?: 0; val st = stats[p]; val n = st?.plays ?: 0
                    if (r == 0 && n == 0) return@forEach
                    val at = st?.lastPlayed ?: now
                    d.execSQL("INSERT INTO tracks (playlist_id, path, readable_name, file_type, cue_offset_ms, rating, played_at, played_fully_at, played_times, last_pos, export_type, total_played_times) VALUES (NULL, ?, ?, ?, 0, ?, ?, ?, ?, 0, 3, ?)",
                        arrayOf<Any>(paPath(p), st?.title?.takeIf { it.isNotBlank() } ?: File(p).nameWithoutExtension, paType(p), r * mul, at, at, n, n))
                    rows++
                }
                d.setTransactionSuccessful()
            } finally { d.endTransaction() }
            d.version = 1031
        } finally { d.close() }
        ctx.contentResolver.openOutputStream(uri)?.use { out ->
            java.util.zip.ZipOutputStream(out.buffered()).use { z ->
                z.putNextEntry(java.util.zip.ZipEntry("lists-export")); dbFile.inputStream().use { it.copyTo(z) }; z.closeEntry()
            }
        }
        work.deleteRecursively()
        return lists to rows
    }
}
