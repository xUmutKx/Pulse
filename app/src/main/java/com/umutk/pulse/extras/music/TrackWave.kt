package com.umutk.pulse.extras.music

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * The real waveform of a song for the wave seek bar: the song is decoded once, in the background, and the loudest sample of every slice of its length is kept
 * (400 slices). The result is kept in memory and in the cache folder, so a song is analysed once. Songs longer than 20 minutes are not analysed.
 */
object TrackWave {
    private const val BARS = 400
    private val mem = android.util.LruCache<String, FloatArray>(24)

    private fun key(path: String): String { val f = File(path); return "$path|${f.length()}|${f.lastModified()}" }

    /** The loudness along the song, [BARS] values from 0.12 to 1, or null when the song cannot be read. */
    suspend fun peaks(ctx: Context, path: String): FloatArray? = withContext(Dispatchers.IO) {
        val k = key(path)
        mem.get(k)?.let { return@withContext it }
        val name = java.security.MessageDigest.getInstance("MD5").digest(k.toByteArray()).joinToString("") { "%02x".format(it) }
        val disk = File(File(ctx.cacheDir, "wave").apply { mkdirs() }, "$name.bin")
        runCatching { disk.takeIf { it.isFile && it.length() == BARS.toLong() }?.readBytes()?.let { b -> FloatArray(BARS) { (b[it].toInt() and 0xFF) / 255f } } }.getOrNull()
            ?.let { mem.put(k, it); return@withContext it }
        val p = runCatching { decode(path) }.getOrNull() ?: return@withContext null
        mem.put(k, p)
        runCatching { disk.writeBytes(ByteArray(BARS) { (p[it] * 255f).toInt().coerceIn(0, 255).toByte() }) }
        p
    }

    private fun decode(path: String): FloatArray? {
        val ex = MediaExtractor()
        try {
            ex.setDataSource(path)
            val track = (0 until ex.trackCount).firstOrNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true } ?: return null
            ex.selectTrack(track)
            val fmt = ex.getTrackFormat(track)
            val durUs = if (fmt.containsKey(MediaFormat.KEY_DURATION)) fmt.getLong(MediaFormat.KEY_DURATION) else 0L
            if (durUs <= 0L || durUs > 20L * 60L * 1_000_000L) return null
            val codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
            val peaks = FloatArray(BARS)
            try {
                codec.configure(fmt, null, null, 0)
                codec.start()
                val info = MediaCodec.BufferInfo()
                var inDone = false; var outDone = false; var floatOut = false
                var guard = 0
                while (!outDone && guard++ < 3_000_000) {
                    if (!inDone) {
                        val i = codec.dequeueInputBuffer(10_000)
                        if (i >= 0) {
                            val buf = codec.getInputBuffer(i)!!
                            val n = ex.readSampleData(buf, 0)
                            if (n < 0) { codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inDone = true }
                            else { codec.queueInputBuffer(i, 0, n, ex.sampleTime, 0); ex.advance() }
                        }
                    }
                    val o = codec.dequeueOutputBuffer(info, 10_000)
                    when {
                        o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val f = codec.outputFormat
                            floatOut = android.os.Build.VERSION.SDK_INT >= 24 && f.containsKey(MediaFormat.KEY_PCM_ENCODING) && f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                        }
                        o >= 0 -> {
                            val b = codec.getOutputBuffer(o)!!
                            b.position(info.offset); b.limit(info.offset + info.size)
                            val bb = b.slice().order(ByteOrder.nativeOrder())
                            var m = 0f
                            if (floatOut) { val fb = bb.asFloatBuffer(); while (fb.hasRemaining()) { val v = abs(fb.get()); if (v > m) m = v } }
                            else { val sb = bb.asShortBuffer(); while (sb.hasRemaining()) { val v = abs(sb.get().toInt()) / 32768f; if (v > m) m = v } }
                            val idx = ((info.presentationTimeUs.toDouble() / durUs) * BARS).toInt().coerceIn(0, BARS - 1)
                            if (m > peaks[idx]) peaks[idx] = m
                            codec.releaseOutputBuffer(o, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outDone = true
                        }
                    }
                }
            } finally { runCatching { codec.stop() }; codec.release() }
            val mx = peaks.maxOrNull() ?: 0f
            if (mx <= 0f) return null
            // the loudest slice is full height; the root keeps the quiet parts visible
            return FloatArray(BARS) { sqrt(peaks[it] / mx).coerceIn(.12f, 1f) }
        } finally { ex.release() }
    }
}
