package com.umutk.pulse.extras.music

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * A five-band equalizer inside the player's own audio chain, for phones whose system equalizer cannot be created ("Cannot initialize effect engine").
 * It does not depend on any system effect: peaking filters on the decoded sound, so it works on every phone. [active] is switched by [Equalizer].
 */
object SoftEq {
    val FREQS = listOf(60, 230, 910, 3600, 14000)
    val PRESETS = listOf(
        "Flat" to intArrayOf(0, 0, 0, 0, 0), "Bass boost" to intArrayOf(600, 400, 0, 0, 0), "Treble boost" to intArrayOf(0, 0, 0, 400, 600),
        "Vocal" to intArrayOf(-200, 0, 400, 300, -100), "Rock" to intArrayOf(400, 200, -100, 300, 400), "Pop" to intArrayOf(-100, 300, 400, 200, -200),
        "Jazz" to intArrayOf(300, 200, -200, 200, 400), "Classical" to intArrayOf(400, 300, -200, 300, 400),
    )
    @Volatile var active = false
    @Volatile var enabled = true
    @Volatile private var gainsDb = FloatArray(5)
    @Volatile private var version = 0

    fun setLevelsMb(mb: IntArray) { gainsDb = FloatArray(5) { (mb.getOrElse(it) { 0 } / 100f) }; version++ }

    val processor = Proc()

    class Proc : BaseAudioProcessor() {
        private var seen = -1
        private var ch = 2
        private var rate = 44100
        // per band: b0 b1 b2 a1 a2 (normalised), and the state per channel
        private val coef = Array(5) { DoubleArray(5) }
        private var st = Array(0) { DoubleArray(4) }

        @Throws(UnhandledAudioFormatException::class)
        override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
            if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) throw UnhandledAudioFormatException(inputAudioFormat)
            ch = inputAudioFormat.channelCount; rate = inputAudioFormat.sampleRate; seen = -1
            st = Array(ch * 5) { DoubleArray(4) }
            return inputAudioFormat
        }

        private fun refresh() {
            val g = gainsDb
            for (b in 0 until 5) {
                val a = 10.0.pow(g[b] / 40.0); val w = 2 * PI * FREQS[b] / rate; val al = sin(w) / 2.0   // Q = 1
                val a0 = 1 + al / a
                coef[b][0] = (1 + al * a) / a0; coef[b][1] = -2 * cos(w) / a0; coef[b][2] = (1 - al * a) / a0
                coef[b][3] = -2 * cos(w) / a0; coef[b][4] = (1 - al / a) / a0
            }
            seen = version
        }

        override fun queueInput(inputBuffer: ByteBuffer) {
            val size = inputBuffer.remaining()
            if (size == 0) return
            val out = replaceOutputBuffer(size)
            val flat = !active || !enabled || gainsDb.all { it == 0f }
            val saved = inputBuffer.order()
            inputBuffer.order(ByteOrder.nativeOrder())
            if (flat) out.put(inputBuffer)
            else {
                if (seen != version) refresh()
                val float = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
                var c = 0
                val step = if (float) 4 else 2
                while (inputBuffer.remaining() >= step) {
                    var x = if (float) inputBuffer.getFloat().toDouble() else inputBuffer.getShort() / 32768.0
                    for (b in 0 until 5) {
                        val s = st[c * 5 + b]; val k = coef[b]
                        val y = k[0] * x + k[1] * s[0] + k[2] * s[1] - k[3] * s[2] - k[4] * s[3]
                        s[1] = s[0]; s[0] = x; s[3] = s[2]; s[2] = y
                        x = y
                    }
                    if (float) out.putFloat(x.toFloat().coerceIn(-1f, 1f)) else out.putShort((x * 32768.0).toInt().coerceIn(-32768, 32767).toShort())
                    c = (c + 1) % ch
                }
            }
            inputBuffer.order(saved)
            out.flip()
        }
    }
}
