package com.umutk.pulse.ai.stt

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.Closeable

/** One line of a transcript: start and end in milliseconds. */
class Segment(val startMs: Long, val endMs: Long, val text: String)

/** The "lite" build has no speech engine (whisper.cpp is in the full build only): opening a model fails, the rest is the same. */
class Whisper private constructor() : Closeable {
    companion object {
        fun open(modelPath: String): Whisper = throw UnsupportedOperationException("Pulse AI is not in this build")
        fun abort() {}
        fun progress(): Int = 0
    }

    fun transcribe(audio: FloatArray, lang: String = "auto", prompt: String? = null, threads: Int = 2): List<Segment> = emptyList()

    override fun close() {}
}

/** Any audio file the phone can play, decoded to 16 kHz mono floats (what whisper reads). */
object AudioDecode {
    /**
     * The song as 16 kHz mono chunks of [chunkSec] seconds. Each chunk goes to [onChunk] as soon as it is decoded, so a long song is never held in
     * memory whole (a few minutes of floats is all that is kept at a time). [onProgress] gets 0..1 of the song.
     */
    fun pcm16kChunks(c: Context, uri: Uri, chunkSec: Int = 240, onProgress: (Float) -> Unit = {}, onDuration: (Long) -> Unit = {}, onChunk: (FloatArray) -> Unit) {
        val ex = MediaExtractor()
        try {
            ex.setDataSource(c, uri, null)
            val track = (0 until ex.trackCount).first { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
            ex.selectTrack(track)
            val fmt = ex.getTrackFormat(track)
            val durUs = if (fmt.containsKey(MediaFormat.KEY_DURATION)) fmt.getLong(MediaFormat.KEY_DURATION) else 0L
            onDuration(durUs / 1000L)
            val codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
            try {
                codec.configure(fmt, null, null, 0)
                codec.start()
                var rate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE); var ch = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                var floatOut = false
                val sink = Chunker(16000 * chunkSec, onChunk)
                var ds = Downsampler(rate, 16000, sink)
                val info = MediaCodec.BufferInfo()
                var inDone = false; var outDone = false
                while (!outDone) {
                    if (!inDone) {
                        val i = codec.dequeueInputBuffer(10_000)
                        if (i >= 0) {
                            val buf = codec.getInputBuffer(i)!!
                            val n = ex.readSampleData(buf, 0)
                            if (n < 0) { codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inDone = true }
                            else { codec.queueInputBuffer(i, 0, n, ex.sampleTime, 0); if (durUs > 0) onProgress((ex.sampleTime.toFloat() / durUs).coerceIn(0f, 1f)); ex.advance() }
                        }
                    }
                    val o = codec.dequeueOutputBuffer(info, 10_000)
                    when {
                        o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val f = codec.outputFormat
                            ds.finish()
                            rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE); ch = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            floatOut = f.containsKey(MediaFormat.KEY_PCM_ENCODING) && f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                            ds = Downsampler(rate, 16000, sink)
                        }
                        o >= 0 -> {
                            val b = codec.getOutputBuffer(o)!!
                            b.position(info.offset); b.limit(info.offset + info.size)
                            val bb = b.slice().order(java.nio.ByteOrder.nativeOrder())
                            // the channels are mixed to mono as they come
                            if (floatOut) { val fb = bb.asFloatBuffer(); val n = fb.remaining() / ch; for (k in 0 until n) { var s = 0f; for (cc in 0 until ch) s += fb.get(k * ch + cc); ds.put(s / ch) } }
                            else { val sb = bb.asShortBuffer(); val n = sb.remaining() / ch; for (k in 0 until n) { var s = 0f; for (cc in 0 until ch) s += sb.get(k * ch + cc) / 32768f; ds.put(s / ch) } }
                            codec.releaseOutputBuffer(o, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outDone = true
                        }
                    }
                }
                ds.finish()
                sink.finish()
            } finally { runCatching { codec.stop() }; codec.release() }
        } finally { ex.release() }
    }

    /** Box average of the source samples that fall in each 16 kHz sample (exact for any source rate of 16 kHz or more). */
    private class Downsampler(private val from: Int, private val to: Int, private val sink: Chunker) {
        private var pos = 0L; private var bin = -1L; private var sum = 0.0; private var cnt = 0
        fun put(v: Float) {
            val b = pos * to / from
            if (b != bin) { flush(); bin = b }
            sum += v; cnt++; pos++
        }
        private fun flush() { if (cnt > 0) sink.add((sum / cnt).toFloat()); sum = 0.0; cnt = 0 }
        fun finish() = flush()
    }

    /** Collects samples into fixed-size chunks; the last one may be shorter. */
    private class Chunker(private val size: Int, private val onChunk: (FloatArray) -> Unit) {
        private var a = FloatArray(size); private var n = 0
        fun add(v: Float) { a[n++] = v; if (n == size) { onChunk(a); a = FloatArray(size); n = 0 } }
        fun finish() { if (n > 0) onChunk(a.copyOf(n)) }
    }
}

/** Segments as a timed lyrics file ([mm:ss.xx] text). */
fun toLrc(segs: List<Segment>, title: String? = null, artist: String? = null): String = buildString {
    title?.let { append("[ti:").append(it).append("]\n") }
    artist?.let { append("[ar:").append(it).append("]\n") }
    append("[by:Pulse AI (whisper.cpp)]\n")
    for (s in segs) {
        val t = s.startMs; val m = t / 60000; val sec = (t % 60000) / 1000.0
        append("[%02d:%05.2f]".format(java.util.Locale.US, m, sec)).append(s.text).append('\n')
    }
}
