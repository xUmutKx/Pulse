package com.umutk.pulse.extras.music

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** Live band levels (0..1) of whatever ExoPlayer is playing, tapped in its audio pipeline: no RECORD_AUDIO needed. */
object MusicLevels {
    const val BANDS = 24
    /** Waveform points (one per skin pixel column of the Winamp vis area), 0..1 with 0.5 as the centre line. */
    const val WAVE = 76
    private const val N = 1024
    private val bands = FloatArray(BANDS)
    private val wave = FloatArray(WAVE) { 0.5f }
    @Volatile private var stamp = 0L
    val processor = LevelProcessor()

    internal fun publish(v: FloatArray, w: FloatArray) {
        synchronized(bands) { v.copyInto(bands); w.copyInto(wave) }
        stamp = System.nanoTime()
    }

    internal fun clear() { synchronized(bands) { bands.fill(0f); wave.fill(0.5f) }; stamp = 0L }

    private fun fresh() = System.nanoTime() - stamp <= 250_000_000L

    /** Zeros when no audio arrived for 250 ms (paused, stopped or between tracks). */
    fun snapshot(): List<Float> {
        if (!fresh()) return List(BANDS) { 0f }
        return synchronized(bands) { bands.toList() }
    }

    /** Waveform points, flat at 0.5 when no audio arrived for 250 ms. */
    fun waveSnapshot(): List<Float> {
        if (!fresh()) return List(WAVE) { 0.5f }
        return synchronized(bands) { wave.toList() }
    }

    class LevelProcessor : BaseAudioProcessor() {
        private val win = FloatArray(N) { (0.5 - 0.5 * cos(2 * PI * it / (N - 1))).toFloat() }
        private val re = FloatArray(N)
        private val im = FloatArray(N)
        private val cosT = FloatArray(N / 2) { cos(2 * PI * it / N).toFloat() }
        private val sinT = FloatArray(N / 2) { -sin(2 * PI * it / N).toFloat() }
        private val ring = FloatArray(N)
        private val out = FloatArray(BANDS)
        private val scopeOut = FloatArray(WAVE)
        private val smooth = FloatArray(BANDS)
        private val lo = IntArray(BANDS)
        private val hi = IntArray(BANDS)
        private var fill = 0
        private var float = false

        @Throws(UnhandledAudioFormatException::class)
        override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
            if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
                throw UnhandledAudioFormatException(inputAudioFormat)
            }
            float = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
            val binHz = inputAudioFormat.sampleRate.toFloat() / N
            for (b in 0 until BANDS) {
                val f0 = 40f * Math.pow(14000.0 / 40.0, b / BANDS.toDouble()).toFloat()
                val f1 = 40f * Math.pow(14000.0 / 40.0, (b + 1) / BANDS.toDouble()).toFloat()
                lo[b] = (f0 / binHz).toInt().coerceIn(1, N / 2 - 1)
                hi[b] = max(lo[b] + 1, (f1 / binHz).toInt()).coerceAtMost(N / 2)
            }
            return inputAudioFormat
        }

        override fun queueInput(inputBuffer: ByteBuffer) {
            val size = inputBuffer.remaining()
            if (size == 0) return
            val order = inputBuffer.order()
            inputBuffer.order(ByteOrder.nativeOrder())
            val ch = inputAudioFormat.channelCount
            val bps = if (float) 4 else 2
            val frames = size / (bps * ch)
            var p = inputBuffer.position()
            for (f in 0 until frames) {
                var s = 0f
                for (c in 0 until ch) {
                    s += if (float) inputBuffer.getFloat(p) else inputBuffer.getShort(p) / 32768f
                    p += bps
                }
                ring[fill++] = s / ch
                if (fill == N) { analyse(); fill = 0 }
            }
            inputBuffer.order(order)
            replaceOutputBuffer(size).put(inputBuffer).flip()
        }

        private fun analyse() {
            for (i in 0 until N) { re[i] = ring[i] * win[i]; im[i] = 0f }
            // iterative radix-2 FFT
            var j = 0
            for (i in 1 until N) {
                var bit = N shr 1
                while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
                j = j xor bit
                if (i < j) { val t = re[i]; re[i] = re[j]; re[j] = t }
            }
            var len = 2
            while (len <= N) {
                val step = N / len
                var i = 0
                while (i < N) {
                    for (k in 0 until len / 2) {
                        val wr = cosT[k * step]; val wi = sinT[k * step]
                        val a = i + k; val b = a + len / 2
                        val tr = re[b] * wr - im[b] * wi
                        val ti = re[b] * wi + im[b] * wr
                        re[b] = re[a] - tr; im[b] = im[a] - ti
                        re[a] += tr; im[a] += ti
                    }
                    i += len
                }
                len = len shl 1
            }
            for (b in 0 until BANDS) {
                var m = 0f
                for (k in lo[b] until hi[b]) m = max(m, sqrt(re[k] * re[k] + im[k] * im[k]))
                // full-scale sine with Hann window peaks near N/4; map -60..0 dB to 0..1
                val db = 20f * (ln(m / (N / 4f) + 1e-6f) / 2.302585f)
                // tilt: music falls ~3 dB per octave, lift highs so the right side moves too
                val v = ((db + 60f + b * 0.6f) / 60f).coerceIn(0f, 1f)
                smooth[b] = if (v > smooth[b]) smooth[b] + (v - smooth[b]) * .7f else smooth[b] * .88f
                out[b] = smooth[b]
            }
            // waveform: one point per column, taken from the same 1024 mono samples the FFT used
            for (i in 0 until WAVE) scopeOut[i] = (0.5f + 0.5f * ring[i * (N / WAVE)]).coerceIn(0f, 1f)
            publish(out, scopeOut)
        }

        override fun onFlush() { fill = 0; smooth.fill(0f); clear() }
        override fun onReset() { onFlush() }
        override fun onQueueEndOfStream() { smooth.fill(0f); clear() }
    }
}

/** Waveform of [MusicLevels.WAVE] points at about 30 fps while composed; flat at 0.5 when nothing plays. */
@Composable
fun rememberWave(): State<List<Float>> {
    val state = remember { mutableStateOf(List(MusicLevels.WAVE) { 0.5f }) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { t ->
                if (t - last >= 33_000_000L) {
                    last = t
                    state.value = MusicLevels.waveSnapshot()
                }
            }
        }
    }
    return state
}

/** [n] bar levels 0..1 at about 30 fps while composed; all zeros when nothing plays. */
@Composable
fun rememberLevels(n: Int = 24): State<List<Float>> {
    val state = remember { mutableStateOf(List(n) { 0f }) }
    LaunchedEffect(n) {
        var last = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { t ->
                if (t - last >= 33_000_000L) {
                    last = t
                    val src = MusicLevels.snapshot()
                    state.value = List(n) { i ->
                        val a = i * src.size.toFloat() / n
                        val b = max(a + 1f, (i + 1) * src.size.toFloat() / n)
                        var m = 0f
                        for (k in a.toInt() until minOf(b.toInt(), src.size)) m = max(m, src[k])
                        m
                    }
                }
            }
        }
    }
    return state
}
