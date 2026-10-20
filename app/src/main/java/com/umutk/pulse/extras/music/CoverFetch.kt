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

    private fun online(c: Context): Boolean {
        val cm = c.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun find(c: Context, t: Track): ImageBitmap? {
        // the network is never used on the main thread; the cover is read again on the next pass
        if (Looper.myLooper() == Looper.getMainLooper() || t.path.startsWith("http")) return null
        PowerampCovers.saved(c, t.path)?.let { return it }
        if (!MusicSettings.onlineCovers(c)) return null
        val miss = PowerampCovers.missTime(c, t.path)
        if (miss > 0L && System.currentTimeMillis() - miss < WEEK_MS) return null
        val term = listOfNotNull(t.artist?.takeIf { it.isNotBlank() }, t.album?.takeIf { it.isNotBlank() } ?: t.displayName())
            .joinToString(" ").trim()
        if (term.isEmpty()) return null
        synchronized(lock) {
            PowerampCovers.saved(c, t.path)?.let { return it }
            val hit = PowerampCovers.search(term, "album").firstOrNull()
            if (hit == null) {
                PowerampCovers.markMiss(c, t.path)
                return null
            }
            val jpg = runCatching { PowerampCovers.fetch(hit.big) }.getOrNull() ?: return null
            PowerampCovers.save(c, t.path, jpg)
            return BitmapFactory.decodeByteArray(jpg, 0, jpg.size)?.asImageBitmap()
        }
    }
}
