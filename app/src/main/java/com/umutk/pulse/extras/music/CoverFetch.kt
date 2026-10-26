package com.umutk.pulse.extras.music

import android.content.Context
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Looper
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.umutk.pulse.extras.music.poweramp.PowerampCovers
import com.umutk.pulse.extras.music.poweramp.displayName
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Cover for a track that has none: looked up once by artist and album (iTunes, no key) and saved with the user's own covers,
 * so it survives a restart. A lookup that finds nothing or fails is remembered for a week, then tried again.
 */
internal object CoverFetch {
    private const val WEEK_MS = 7L * 24 * 3600 * 1000
    // shortcut: a fixed pause between lookups, about 20 a minute, instead of reading the service's real rate limit
    private const val GAP_MS = 3_000L
    private val lock = Any() // one lookup at a time, so a library scan does not flood the service
    private val running = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Looks up covers for every track that has none, one at a time in the background, and keeps them on disk.
     *  Runs only while the phone has internet; a pass that is already running is not started again. */
    fun backfill(c: Context, tracks: List<Track>) {
        if (!MusicSettings.onlineCovers(c)) return
        if (!running.compareAndSet(false, true)) return
        scope.launch {
            try {
                for (t in tracks) {
                    if (!online(c)) break
                    if (t.path.startsWith("http")) continue
                    if (PowerampCovers.saved(c, t.path) != null || embeddedArt(c, t.path) != null) continue
                    val miss = PowerampCovers.missTime(c, t.path)
                    if (miss > 0L && System.currentTimeMillis() - miss < WEEK_MS) continue
                    find(c, t)
                    delay(GAP_MS)
                }
            } finally {
                running.set(false)
            }
        }
    }

    private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    /** Words that are not part of a name: a compilation, a remaster, a video tag, a track number. They only misdirect the search. */
    private val NOISE = Regex("(?i)\\b(no\\.?\\s*1|number\\s*one|greatest\\s+hits|best\\s+of|the\\s+best|classics?|hits|collection|essentials?|anthology|remaster(ed)?(\\s+\\d{4})?|deluxe(\\s+edition)?|official\\s+(music\\s+)?(video|audio)|lyrics?|hd|hq|mp3|feat\\.?|ft\\.?)\\b")

    /** A name as the tags give it, without the noise: "01 - Song (Remastered 2011) [HD]" becomes "Song". */
    private fun clean(s: String): String = s.replace(Regex("^\\s*\\d{1,3}\\s*[-._)]\\s*"), "").replace(Regex("[\\[(].*?[\\])]"), " ").replace(NOISE, " ")
        .replace(Regex("[_]+"), " ").replace(Regex("\\s+"), " ").trim(' ', '-', '.', ',')

    /** An album name that is a compilation / hits package, not one record: a search by it finds some other album, so the song is searched instead. */
    private fun generic(album: String) = NOISE.containsMatchIn(album) || album.length < 3 || album.lowercase() in setOf("unknown", "various", "various artists", "music", "download", "downloads")

    /** Two names that are the same apart from case, punctuation and an added "(Deluxe)" or "- Single" part. */
    private fun sameWork(found: String, wanted: String): Boolean {
        val a = norm(clean(found).substringBefore(" - "))
        val b = norm(clean(wanted).substringBefore(" - "))
        if (a.isEmpty() || b.isEmpty()) return false
        // a short name must match whole: "love" is inside a hundred other names
        if (a == b) return true
        if (minOf(a.length, b.length) < 6) return false
        return a.contains(b) || b.contains(a)
    }

    private fun online(c: Context): Boolean {
        val cm = c.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun find(c: Context, t: Track, force: Boolean = false, loose: Boolean = false): ImageBitmap? {
        // the network is never used on the main thread; the cover is read again on the next pass
        if (Looper.myLooper() == Looper.getMainLooper() || t.path.startsWith("http")) return null
        PowerampCovers.saved(c, t.path)?.let { return it }
        if (!force && !MusicSettings.onlineCovers(c)) return null
        val miss = PowerampCovers.missTime(c, t.path)
        if (!force && miss > 0L && System.currentTimeMillis() - miss < WEEK_MS) return null
        val artist = t.artist?.takeIf { it.isNotBlank() && it != "<unknown>" }?.let { clean(it).ifBlank { null } }
        // the album only when it is one record; a compilation ("No.1 Classics") or the folder name is no album, the song is searched then
        val album = t.album?.takeIf { it.isNotBlank() && it != "<unknown>" && !generic(it) }?.let { clean(it).ifBlank { null } }
        val song = clean(t.displayName()).ifBlank { null }
        if (album == null && song == null) return null
        synchronized(lock) {
            PowerampCovers.saved(c, t.path)?.let { return it }
            // several tries, narrow to wide: the album with the artist, the song with the artist, the song alone; a hit counts when the same work is meant (and the same artist, when one is known).
            // No cover is better than a wrong one, so every try checks the names.
            val tries = ArrayList<Triple<String, String, String>>()   // search term, entity, name to match
            if (album != null) tries += Triple(listOfNotNull(artist, album).joinToString(" "), "album", album)
            if (song != null) { tries += Triple(listOfNotNull(artist, song).joinToString(" "), "song", song); if (artist != null) tries += Triple(song, "song", song) }
            var jpg: ByteArray? = null
            for ((term, entity, want) in tries) {
                val all = PowerampCovers.search(term, entity)
                // loose (the second pass of "Search and apply missing covers"): when nothing matches by name, the first results of the search are taken
                val hits = all.filter { sameWork(it.name, want) && (artist == null || sameWork(it.artist, artist) || sameWork(artist, it.artist)) }.ifEmpty { if (loose) all.take(3) else emptyList() }
                jpg = hits.firstNotNullOfOrNull { h -> runCatching { PowerampCovers.fetch(h.big) }.getOrNull()?.takeIf { it.size > 2000 } }
                if (jpg != null) break
            }
            if (jpg == null) { PowerampCovers.markMiss(c, t.path); return null }
            PowerampCovers.save(c, t.path, jpg)
            return BitmapFactory.decodeByteArray(jpg, 0, jpg.size)?.asImageBitmap()
        }
    }
}
