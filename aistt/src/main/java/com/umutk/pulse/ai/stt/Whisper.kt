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

/** whisper.cpp over a small JNI (libpulsewhisper). One model file per instance; one transcription at a time. */
class Whisper private constructor(private var ctx: Long) : Closeable {
    companion object {
        init { System.loadLibrary("pulsewhisper") }
        @JvmStatic private external fun nInit(path: String): Long
        @JvmStatic private external fun nFree(ctx: Long)
        @JvmStatic private external fun nFull(ctx: Long, threads: Int, audio: FloatArray, lang: String?, prompt: String?): Int
        @JvmStatic private external fun nAbort()
        @JvmStatic private external fun nProgress(): Int
        @JvmStatic private external fun nSegments(ctx: Long): Int
        @JvmStatic private external fun nText(ctx: Long, i: Int): ByteArray
        @JvmStatic private external fun nT0(ctx: Long, i: Int): Long
        @JvmStatic private external fun nT1(ctx: Long, i: Int): Long

        fun open(modelPath: String): Whisper {
            val c = nInit(modelPath)
            require(c != 0L) { "The speech model could not be read: $modelPath" }
            return Whisper(c)
        }

        /** Stops the transcription that is running. */
        fun abort() = nAbort()
        /** 0..100 of the transcription that is running. */
        fun progress(): Int = nProgress()
    }

    /** The segments of 16 kHz mono [audio]; [lang] "auto", "tr", "en" ... */
    fun transcribe(audio: FloatArray, lang: String = "auto", prompt: String? = null, threads: Int = defaultThreads()): List<Segment> {
        check(ctx != 0L)
        if (nFull(ctx, threads, audio, lang, prompt) != 0) return emptyList()
        return (0 until nSegments(ctx)).map { i -> Segment(nT0(ctx, i) * 10, nT1(ctx, i) * 10, String(nText(ctx, i), Charsets.UTF_8).trim()) }
            .filter { it.text.isNotEmpty() }
    }

    override fun close() { if (ctx != 0L) { nFree(ctx); ctx = 0L } }

    private fun defaultThreads() = Runtime.getRuntime().availableProcessors().let { (it - 2).coerceIn(2, 6) }
}

/** Any audio file the phone can play, decoded to 16 kHz mono floats (what whisper reads). [onProgress] gets 0..1. */
object AudioDecode {
    fun pcm16k(c: Context, uri: Uri, onProgress: (Float) -> Unit = {}): FloatArray {
        val ex = MediaExtractor()
        ex.setDataSource(c, uri, null)
        val track = (0 until ex.trackCount).first { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
        ex.selectTrack(track)
        val fmt = ex.getTrackFormat(track)
        val durUs = if (fmt.containsKey(MediaFormat.KEY_DURATION)) fmt.getLong(MediaFormat.KEY_DURATION) else 0L
        val codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(fmt, null, null, 0)
        codec.start()
        var rate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE); var ch = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var floatOut = false
        val out = FloatBuilder()
        // mono at the source rate first, resampled at the end
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
                    rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE); ch = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    floatOut = f.containsKey(MediaFormat.KEY_PCM_ENCODING) && f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                }
                o >= 0 -> {
                    val b = codec.getOutputBuffer(o)!!
                    b.position(info.offset); b.limit(info.offset + info.size)
                    val bb = b.slice().order(java.nio.ByteOrder.nativeOrder())
                    if (floatOut) { val fb = bb.asFloatBuffer(); val n = fb.remaining() / ch; for (k in 0 until n) { var s = 0f; for (cc in 0 until ch) s += fb.get(k * ch + cc); out.add(s / ch) } }
                    else { val sb = bb.asShortBuffer(); val n = sb.remaining() / ch; for (k in 0 until n) { var s = 0f; for (cc in 0 until ch) s += sb.get(k * ch + cc) / 32768f; out.add(s / ch) } }
                    codec.releaseOutputBuffer(o, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outDone = true
                }
            }
        }
        codec.stop(); codec.release(); ex.release()
        return resample(out.toArray(), rate, 16000)
    }

    /** Linear resampling with a short box filter first when going down (enough for speech). */
    private fun resample(x: FloatArray, from: Int, to: Int): FloatArray {
        if (from == to || x.isEmpty()) return x
        val ratio = from.toDouble() / to
        val k = ratio.toInt().coerceAtLeast(1)
        val y = if (k > 1) FloatArray(x.size) { i -> var s = 0f; var n = 0; for (j in i until minOf(x.size, i + k)) { s += x[j]; n++ }; s / n } else x
        val n = (y.size / ratio).toInt()
        return FloatArray(n) { i -> val p = i * ratio; val a = p.toInt(); val f = (p - a).toFloat(); val b = minOf(a + 1, y.size - 1); y[a] * (1 - f) + y[b] * f }
    }

    private class FloatBuilder {
        private var a = FloatArray(1 shl 20); private var n = 0
        fun add(v: Float) { if (n == a.size) a = a.copyOf(a.size * 2); a[n++] = v }
        fun toArray(): FloatArray = a.copyOf(n)
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
