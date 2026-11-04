package com.umutk.pulse.extras.video

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Finds the intro of an episode by comparing it with the next episode of the same show: the opening is the part whose pictures
 * match at (nearly) the same time in both files. One sample every [STEP_MS] for the first [WINDOW_MS]; each picture is reduced to
 * a 64-bit hash. The longest run of matching samples is the intro. Needs video files only (no audio matching yet).
 */
internal object IntroFinder {
    private const val STEP_MS = 2000L
    private const val WINDOW_MS = 180_000L
    private const val MATCH_BITS = 10        // pictures whose hashes differ in at most this many of 64 bits are the same
    private const val MIN_RUN = 8            // 8 samples = 16 seconds, the shortest intro taken
    private const val MAX_SHIFT = 5          // the openings may start a few samples apart

    /** The intro of [a] in milliseconds, or null when nothing repeats in [b]. Heavy: decodes frames; run it off the main thread. */
    suspend fun find(ctx: Context, a: Uri, b: Uri): LongRange? = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < 27) return@withContext null
        val ha = hashes(ctx, a) ?: return@withContext null
        val hb = hashes(ctx, b) ?: return@withContext null
        val run = longestRun(ha, hb) ?: return@withContext null
        run.first * STEP_MS until (run.last + 1) * STEP_MS
    }

    /** One 64-bit hash per sample, from a small grey picture. */
    private fun hashes(ctx: Context, u: Uri): LongArray? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(ctx, u)
            val n = (WINDOW_MS / STEP_MS).toInt()
            val out = LongArray(n)
            for (i in 0 until n) {
                val bmp = r.getScaledFrameAtTime(i * STEP_MS * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 64, 36) ?: return null
                out[i] = dhash(bmp)
                bmp.recycle()
            }
            out
        } catch (e: Exception) {
            null
        } finally {
            runCatching { r.release() }
        }
    }

    /** Average hash on an 8x8 grey picture: each bit is one cell brighter than the mean. */
    private fun dhash(bmp: Bitmap): Long {
        val s = Bitmap.createScaledBitmap(bmp, 8, 8, true)
        val g = IntArray(64)
        for (y in 0 until 8) for (x in 0 until 8) {
            val c = s.getPixel(x, y)
            g[y * 8 + x] = (((c shr 16) and 0xff) * 3 + ((c shr 8) and 0xff) * 6 + (c and 0xff)) / 10
        }
        if (s !== bmp) s.recycle()
        val mean = g.average()
        var h = 0L
        for (i in 0 until 64) if (g[i] > mean) h = h or (1L shl i)
        return h
    }

    private fun same(x: Long, y: Long) = java.lang.Long.bitCount(x xor y) <= MATCH_BITS

    /** The longest stretch where a[i] matches b[i + shift] for consecutive i, over small shifts. */
    internal fun longestRun(a: LongArray, b: LongArray): IntRange? {
        var best: IntRange? = null
        for (shift in -MAX_SHIFT..MAX_SHIFT) {
            var start = -1
            for (i in a.indices) {
                val j = i + shift
                val ok = j in b.indices && same(a[i], b[j])
                if (ok) { if (start < 0) start = i }
                if ((!ok || i == a.lastIndex) && start >= 0) {
                    val end = if (ok) i else i - 1
                    val len = end - start + 1
                    if (len >= MIN_RUN && (best == null || len > best.last - best.first + 1)) best = start..end
                    start = -1
                }
            }
        }
        return best
    }
}

/**
 * The found parts per episode (key: the file's address). IntroDB results and picture-match results are kept apart, so a
 * match never stops the IntroDB lookup. Files are the v2 ones: older entries were per folder and had no recap.
 */
internal object IntroCache {
    const val DB_FILE = "video_intro_db2"
    const val LOCAL_FILE = "video_intro_local2"
    private fun prefs(c: Context, file: String) = c.getSharedPreferences(file, Context.MODE_PRIVATE)
    fun key(u: Uri): String = u.toString()
    /** The same episode found again under another address (the media index or a file path): its name and size are the same. */
    fun nameKey(name: String, size: Long): String = "n:" + name + "|" + size
    private fun part(p: String): LongRange? {
        val f = p.split(',')
        if (f.size != 2) return null
        val a = f[0].toLongOrNull() ?: return null
        val b = f[1].toLongOrNull() ?: return null
        return a until b
    }
    private fun text(r: LongRange?) = if (r == null) "" else "${r.first},${r.last + 1}"
    fun get(c: Context, key: String, file: String): Segs? = runCatching {
        val v = prefs(c, file).getString(key, null) ?: return null
        val parts = v.split(';')
        Segs(part(parts[0]), part(parts.getOrElse(1) { "" })).takeIf { !it.empty }
    }.getOrNull()
    fun put(c: Context, key: String, s: Segs, file: String) { prefs(c, file).edit().putString(key, text(s.intro) + ";" + text(s.recap)).apply() }
}
