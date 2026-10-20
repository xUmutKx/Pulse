package com.umutk.pulse.extras.music.poweramp

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt

/** Loudness envelope of a track: one peak per [BUCKET_MS] of audio, 0..1. Decoded once per track and kept for the last few. */
object TrackPeaks {
    const val BUCKET_MS = 100
    // shortcut: tracks over 20 minutes are not decoded and their bar stays flat, a long decode would hold the screen up
    private const val MAX_SECONDS = 20 * 60
    private val cache = object : LinkedHashMap<String, FloatArray>(8, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, FloatArray>?) = size > 12
    }

    fun load(ctx: Context, path: String): FloatArray? {
        synchronized(cache) { cache[path]?.let { return it } }
        val peaks = runCatching { decode(ctx, path) }.getOrNull() ?: return null
        synchronized(cache) { cache[path] = peaks }
        return peaks
    }

    private fun decode(ctx: Context, path: String): FloatArray? {
        val ex = MediaExtractor()
        try {
            if (path.startsWith("content:")) ex.setDataSource(ctx, Uri.parse(path), null) else ex.setDataSource(path)
            val idx = (0 until ex.trackCount).firstOrNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true } ?: return null
            ex.selectTrack(idx)
            val fmt = ex.getTrackFormat(idx)
            val codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME) ?: return null)
            try {
                codec.configure(fmt, null, null, 0)
                codec.start()
                var perBucket = samplesPerBucket(fmt)
                var float = false
                val maxBuckets = MAX_SECONDS * 1000 / BUCKET_MS
                val out = ArrayList<Float>()
                var cur = 0f; var filled = 0
                val info = MediaCodec.BufferInfo()
                var inputDone = false; var outputDone = false
                while (!outputDone && out.size < maxBuckets) {
                    if (!inputDone) {
                        val i = codec.dequeueInputBuffer(10_000)
                        if (i >= 0) {
                            val buf = codec.getInputBuffer(i) ?: return null
                            val n = ex.readSampleData(buf, 0)
                            if (n < 0) { codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true }
                            else { codec.queueInputBuffer(i, 0, n, ex.sampleTime, 0); ex.advance() }
                        }
                    }
                    val o = codec.dequeueOutputBuffer(info, 10_000)
                    when {
                        o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val f = codec.outputFormat
                            perBucket = samplesPerBucket(f)
                            float = runCatching { f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT }.getOrDefault(false)
                        }
                        o >= 0 -> {
                            val buf = codec.getOutputBuffer(o)
                            if (buf != null && info.size > 0) {
                                buf.position(info.offset); buf.limit(info.offset + info.size)
                                buf.order(ByteOrder.LITTLE_ENDIAN)
                                // one loop for both sample formats: each sample becomes a 0..1 amplitude
                                val fb = if (float) buf.asFloatBuffer() else null
                                val sb = if (float) null else buf.asShortBuffer()
                                val left = if (float) buf.remaining() / 4 else buf.remaining() / 2
                                for (k in 0 until left) {
                                    val a = if (fb != null) abs(fb.get()) else abs(sb!!.get().toInt()) / 32768f
                                    if (a > cur) cur = a
                                    if (++filled == perBucket) { out.add(cur); cur = 0f; filled = 0 }
                                }
                            }
                            codec.releaseOutputBuffer(o, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                        }
                    }
                }
                if (filled > 0) out.add(cur)
                val max = out.maxOrNull()?.takeIf { it > 0f } ?: return FloatArray(out.size)
                // square root keeps quiet passages visible next to loud ones
                return FloatArray(out.size) { sqrt(out[it] / max) }
            } finally {
                runCatching { codec.stop() }
                codec.release()
            }
        } finally {
            ex.release()
        }
    }

    /** Interleaved samples in one bucket: rate x channels x 0.1 s. */
    private fun samplesPerBucket(f: MediaFormat): Int =
        (f.getInteger(MediaFormat.KEY_SAMPLE_RATE) * f.getInteger(MediaFormat.KEY_CHANNEL_COUNT) * BUCKET_MS / 1000).coerceAtLeast(1)
}
