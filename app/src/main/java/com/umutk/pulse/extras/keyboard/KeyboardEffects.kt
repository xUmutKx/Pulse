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
import kotlin.math.cos
import kotlin.math.hypot
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import kotlin.math.exp
import kotlin.math.sin

/** The looks of the keyboard that go beyond the theme: a colour wash over the keyboard, a colour effect on each key, how a key moves when pressed, and the touch sounds. */
object KeyboardEffects {
    val keyNames = listOf("None" to "Yok", "Tint" to "Ton", "Blob" to "Leke", "Glass" to "Cam", "Glow" to "Işıltı")
    val boardNames = listOf("Off" to "Kapalı", "Dusk" to "Alacakaranlık", "Sea" to "Deniz", "Meadow" to "Çayır", "Sun" to "Güneş", "Slate" to "Kayrak", "Rose" to "Gül")
    val motionNames = listOf("Default" to "Varsayılan", "Pop" to "Yüksel", "Sink" to "Bas", "Bounce" to "Zıpla")
    /** The key-press effects: none, five around the key, five across the keyboard, five dark styles. */
    val pressNames = listOf(
        "None" to "Yok", "Ring" to "Halka", "Sparks" to "Kıvılcım", "Ripple" to "Dalga", "Halo" to "Hale", "Stars" to "Yıldızlar",
        "Wave" to "Büyük dalga", "Flash" to "Parlama", "Edge" to "Kenar ışığı", "Sweep" to "Süpürme", "Rings" to "Halkalar",
        "Neon" to "Neon", "Ember" to "Kor", "Snow" to "Kar", "Matrix" to "Matrix", "Smoke" to "Duman",
    )

    /** How long one press effect lasts, in ms (the ones across the keyboard travel further). */
    fun pressMs(kind: Int): Float = if (kind in 6..10) 650f else 520f

    private fun DrawScope.star(x: Float, y: Float, r: Float, col: Color) {
        val p = Path().apply {
            moveTo(x, y - r); lineTo(x + r * .28f, y - r * .28f); lineTo(x + r, y); lineTo(x + r * .28f, y + r * .28f)
            lineTo(x, y + r); lineTo(x - r * .28f, y + r * .28f); lineTo(x - r, y); lineTo(x - r * .28f, y - r * .28f); close()
        }
        drawPath(p, col)
    }

    /**
     * One key-press effect at [p] (0 at the press, 1 when it is gone) for the key at [key]; [seed] keeps the particles of one press the same from frame to frame.
     * 1-5 stay around the key, 6-10 run across the whole keyboard (the size of this drawing), 11-15 are styles made for dark keyboards.
     */
    fun DrawScope.drawPress(kind: Int, key: Rect, p: Float, accent: Color, seed: Int) {
        val c = key.center; val kh = key.height; val kw = key.width
        val fade = (1f - p).coerceIn(0f, 1f)
        val rnd = java.util.Random(seed.toLong())
        val far = hypot(maxOf(c.x, size.width - c.x), maxOf(c.y, size.height - c.y))
        when (kind) {
            1 -> drawCircle(accent.copy(alpha = fade), kh * (.4f + .8f * p), c, style = Stroke(2.dp.toPx() * fade + 1f))
            2 -> repeat(10) { i ->
                val a = i / 10f * 2f * PI.toFloat() + rnd.nextFloat() * .5f
                val d = kh * (.3f + 1.1f * p) * (.7f + rnd.nextFloat() * .5f)
                drawCircle(accent.copy(alpha = fade), kh * .05f * (1f - p * .6f) + 1f, Offset(c.x + cos(a) * d, c.y + sin(a) * d))
            }
            3 -> drawCircle(accent.copy(alpha = .35f * fade), kh * (.3f + .9f * p), c)
            4 -> { val r = kh * (.9f + .3f * p); drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = .55f * fade), Color.Transparent), c, r), r, c) }
            5 -> repeat(6) {
                val x = c.x + (rnd.nextFloat() - .5f) * kw * 1.4f
                val y = c.y - kh * (.2f + 1.2f * p) * (.6f + rnd.nextFloat() * .6f)
                star(x, y, kh * .1f * (1f - p * .5f), Color.White.copy(alpha = fade))
            }
            6 -> drawCircle(accent.copy(alpha = .8f * fade), far * p, c, style = Stroke(3.dp.toPx()))
            7 -> drawRect(accent.copy(alpha = .2f * fade))
            8 -> listOf(10.dp.toPx() to .12f, 5.dp.toPx() to .3f, 2.dp.toPx() to .9f).forEach { (w, a) ->
                drawRoundRect(accent.copy(alpha = a * fade), Offset(w / 2, w / 2), Size(size.width - w, size.height - w), CornerRadius(16.dp.toPx()), Stroke(w))
            }
            9 -> {
                val x = -size.width * .3f + size.width * 1.6f * p
                drawRect(Brush.horizontalGradient(listOf(Color.Transparent, accent.copy(alpha = .35f), Color.Transparent), x - size.width * .15f, x + size.width * .15f))
            }
            10 -> repeat(3) { j ->
                val q = p * 1.4f - j * .2f
                if (q in 0f..1f) drawCircle(accent.copy(alpha = .6f * (1f - q)), far * q, c, style = Stroke(2.dp.toPx()))
            }
            11 -> { val neon = Color(0xFF00E5FF)
                listOf(9.dp.toPx() to .15f, 5.dp.toPx() to .3f, 2.dp.toPx() to .95f).forEach { (w, a) ->
                    drawRoundRect(neon.copy(alpha = a * fade), key.topLeft, key.size, CornerRadius(kh * .2f), Stroke(w))
                } }
            12 -> repeat(9) { i ->
                val sp = .6f + rnd.nextFloat() * .7f
                val x = c.x + (rnd.nextFloat() - .5f) * kw + sin(p * 6f + i) * kh * .1f
                val y = c.y - kh * 1.6f * p * sp
                drawCircle(androidx.compose.ui.graphics.lerp(Color(0xFFFFC107), Color(0xFFFF5722), p).copy(alpha = fade), kh * .07f * (1f - p * .7f) + 1f, Offset(x, y))
            }
            13 -> repeat(9) { i ->
                val sp = .6f + rnd.nextFloat() * .7f
                val x = c.x + (rnd.nextFloat() - .5f) * kw * 1.3f + sin(p * 5f + i) * kh * .08f
                val y = c.y + kh * 1.4f * p * sp
                drawCircle(Color.White.copy(alpha = .9f * fade), kh * .05f + 1f, Offset(x, y))
            }
            14 -> repeat(5) { j ->
                val x = key.left + kw * (j + .5f) / 5f
                val off = rnd.nextFloat() * kh * .4f
                repeat(4) { k ->
                    val y = key.top - off + p * kh * 1.8f - k * kh * .2f
                    drawRect(Color(0xFF00FF66).copy(alpha = fade * (1f - k * .22f)), Offset(x - kh * .03f, y), Size(kh * .06f, kh * .13f))
                }
            }
            15 -> repeat(5) {
                val x = c.x + (rnd.nextFloat() - .5f) * kw * .9f
                val y = c.y - kh * .9f * p * (.6f + rnd.nextFloat() * .6f)
                drawCircle(Color(0xFF9E9E9E).copy(alpha = .3f * fade), kh * (.25f + .6f * p) * (.7f + rnd.nextFloat() * .5f), Offset(x, y))
            }
        }
    }

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
