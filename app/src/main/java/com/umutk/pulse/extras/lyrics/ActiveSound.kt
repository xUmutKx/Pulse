package com.umutk.pulse.extras.lyrics

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * The moments of a song that have sound: the start of every 100 ms window louder than about -30 dB under the loudest one.
 * Silence before the first vocal, after the last one and in the gaps between is left out, so plain lyrics spread over the
 * sounding part only. Decoded once per song (the file is read through, in the background); null when it cannot be read.
 */
object ActiveSound {
    private const val WIN_MS = 100L
    private const val MAX_NANOS = 40_000_000_000L
    private val cache = HashMap<String, LongArray?>()

    fun of(path: String): LongArray? {
        synchronized(cache) { if (cache.containsKey(path)) return cache[path] }
        val r = runCatching { scan(path) }.getOrNull()
        synchronized(cache) { cache[path] = r }
        return r
    }

    private fun scan(path: String): LongArray? {
        if (!File(path).isFile) return null
        val ex = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            ex.setDataSource(path)
            val idx = (0 until ex.trackCount).firstOrNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true } ?: return null
            ex.selectTrack(idx)
            val fmt = ex.getTrackFormat(idx)
            val c = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME) ?: return null)
            codec = c
            c.configure(fmt, null, null, 0)
            c.start()
            var rate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var ch = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val peaks = ArrayList<Int>()
            var winPeak = 0
            var winLen = 0
            var inputDone = false
            var outputDone = false
            val info = MediaCodec.BufferInfo()
            val t0 = System.nanoTime()
            while (!outputDone && System.nanoTime() - t0 < MAX_NANOS) {
                if (!inputDone) {
                    val i = c.dequeueInputBuffer(10_000)
                    if (i >= 0) {
                        val buf = c.getInputBuffer(i) ?: return null
                        val n = ex.readSampleData(buf, 0)
                        if (n < 0) {
                            c.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            c.queueInputBuffer(i, 0, n, ex.sampleTime, 0)
                            ex.advance()
                        }
                    }
                }
                val o = c.dequeueOutputBuffer(info, 10_000)
                when {
                    o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val f = c.outputFormat
                        // 16-bit samples only; other encodings are not read
                        if (f.getInteger(MediaFormat.KEY_PCM_ENCODING, 2) != 2) return null
                        rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        ch = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                    o >= 0 -> {
                        val out = c.getOutputBuffer(o)
                        if (out != null && info.size > 0 && ch > 0) {
                            val shorts = out.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                            val frames = shorts.remaining() / ch
                            val win = maxOf(1, rate / 10)
                            for (f in 0 until frames) {
                                var m = 0
                                for (k in 0 until ch) m = maxOf(m, abs(shorts.get(f * ch + k).toInt()))
                                if (m > winPeak) winPeak = m
                                if (++winLen >= win) { peaks += winPeak; winPeak = 0; winLen = 0 }
                            }
                        }
                        c.releaseOutputBuffer(o, false)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true
                    }
                }
            }
            if (winLen > 0) peaks += winPeak
            val loudest = peaks.maxOrNull() ?: return null
            if (loudest == 0) return null
            val limit = loudest / 30
            val active = peaks.indices.filter { peaks[it] > limit }.map { it * WIN_MS }.toLongArray()
            return active.takeIf { it.isNotEmpty() }
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            ex.release()
        }
    }
}
