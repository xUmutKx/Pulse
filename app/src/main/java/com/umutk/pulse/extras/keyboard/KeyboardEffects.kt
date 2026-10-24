package com.umutk.pulse.extras.keyboard

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** The looks of the keyboard that go beyond the theme: a colour wash over the keyboard, a colour effect on each key, how a key moves when pressed, and the touch sounds. */
object KeyboardEffects {
    val keyNames = listOf("None" to "Yok", "Tint" to "Ton", "Blob" to "Leke", "Glass" to "Cam", "Glow" to "Işıltı")
    val boardNames = listOf("Off" to "Kapalı", "Dusk" to "Alacakaranlık", "Sea" to "Deniz", "Meadow" to "Çayır", "Sun" to "Güneş", "Slate" to "Kayrak", "Rose" to "Gül")
    val motionNames = listOf("Default" to "Varsayılan", "Pop" to "Yüksel", "Sink" to "Bas", "Bounce" to "Zıpla")
    val soundNames = listOf("System" to "Sistem", "Retro" to "Retro", "Calm" to "Sakin", "Fun" to "Eğlenceli", "Pink" to "Pembe", "Space" to "Uzay")

    private val boards = listOf(
        listOf(0xFF9C8CFF to Offset(.12f, .25f), 0xFFFF9A6B to Offset(.9f, .85f)),
        listOf(0xFF4FC3F7 to Offset(.1f, .2f), 0xFF26A69A to Offset(.85f, .9f)),
        listOf(0xFF9CCC65 to Offset(.15f, .3f), 0xFF66BB6A to Offset(.9f, .8f)),
        listOf(0xFFFFD54F to Offset(.1f, .8f), 0xFFFFB74D to Offset(.8f, .15f)),
        listOf(0xFF78909C to Offset(.1f, .15f), 0xFF90A4AE to Offset(.9f, .9f)),
        listOf(0xFFF48FB1 to Offset(.85f, .2f), 0xFFBA68C8 to Offset(.12f, .85f)),
    )

    /** Soft colour blobs behind the keys (drawn on the keyboard's own background). */
    fun Modifier.boardWash(kind: Int): Modifier = if (kind <= 0) this else drawBehind {
        boards.getOrNull(kind - 1)?.forEach { (c, at) ->
            val centre = Offset(size.width * at.x, size.height * at.y)
            val r = size.width * .55f
            drawCircle(Brush.radialGradient(listOf(Color(c).copy(alpha = .38f), Color.Transparent), centre, r), r, centre)
        }
    }

    /** The colour effect of a key: a wash, a blob, glass or a glowing edge over its fill. */
    fun Modifier.keyWash(kind: Int, accent: Color, corner: Float): Modifier = if (kind <= 0) this else drawBehind {
        val w = size.width; val h = size.height
        when (kind) {
            1 -> drawRoundRect(Brush.verticalGradient(listOf(accent.copy(alpha = .28f), Color.Transparent)), cornerRadius = CornerRadius(corner))
            2 -> drawCircle(accent.copy(alpha = .3f), h * .55f, Offset(w * .22f, h * .3f))
            3 -> {
                drawRoundRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = .26f), Color.White.copy(alpha = .04f))), cornerRadius = CornerRadius(corner))
                drawRoundRect(Color.White.copy(alpha = .3f), Offset(.5f, .5f), androidx.compose.ui.geometry.Size(w - 1f, h - 1f), CornerRadius(corner), Stroke(1.5f))
            }
            4 -> drawRoundRect(accent.copy(alpha = .85f), Offset(3f, 3f), androidx.compose.ui.geometry.Size(w - 6f, h - 6f), CornerRadius(corner), Stroke(4f))
        }
    }

    // ---- touch sounds: short tones made here, no sound files ----
    private val tracks = HashMap<Int, AudioTrack>()
    private const val RATE = 22050

    private fun wave(kind: Int): ShortArray {
        val ms = when (kind) { 1 -> 45; 2 -> 90; 3 -> 70; 4 -> 110; else -> 130 }
        val n = RATE * ms / 1000
        return ShortArray(n) { i ->
            val t = i / RATE.toDouble(); val f = i / n.toDouble()
            val env = exp(-f * 6.0)
            val v = when (kind) {
                1 -> if (sin(2 * PI * 880 * t) >= 0) .5 else -.5
                2 -> sin(2 * PI * 520 * t) * minOf(1.0, f * 20)
                3 -> sin(2 * PI * (600 * t + 5000 * t * t))
                4 -> .6 * sin(2 * PI * 1318 * t) + .4 * sin(2 * PI * 1976 * t)
                else -> sin(2 * PI * (1400 * t - 6000 * t * t)) * (.7 + .3 * sin(2 * PI * 30 * t))
            }
            (v * env * 9000).toInt().toShort()
        }
    }

    fun play(kind: Int) {
        if (kind <= 0) return
        runCatching {
            val t = tracks.getOrPut(kind) {
                val data = wave(kind)
                AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(data.size * 2).setTransferMode(AudioTrack.MODE_STATIC).build().also { it.write(data, 0, data.size) }
            }
            t.stop(); t.setPlaybackHeadPosition(0); t.play()
        }
    }
}
