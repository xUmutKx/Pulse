package com.umutk.pulse.extras.launcher

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.tr
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * The jokes of the start: now and then (a rare one in a while) the launcher "boots" through an old PC's BIOS screen, and very rarely it "crashes" into a blue screen
 * and restarts. Nothing is wrong and nothing is touched; the blue screen says so itself at the end.
 */
object BootEggs {
    /** 0 nothing (most starts), 1 the BIOS screen, 2 the blue screen. */
    fun roll(): Int { val r = Math.random(); return if (r < 0.02) 2 else if (r < 0.14) 1 else 0 }
}

/** Short beeps and chords made on the spot (no recordings): the POST beep of a PC, and the two falling notes with a low thump of an error. */
object BootSounds {
    private const val RATE = 22050

    private fun synth(notes: List<Triple<Double, Int, Double>>, thump: Boolean): ShortArray {
        // notes: frequency, length in ms, loudness
        val total = notes.sumOf { it.second } * RATE / 1000 + RATE / 10
        val out = ShortArray(total)
        var at = 0
        for ((f, ms, vol) in notes) {
            val n = ms * RATE / 1000
            for (i in 0 until n) {
                val t = i.toDouble() / RATE
                val env = (if (i < 200) i / 200.0 else 1.0) * exp(-3.0 * i / n)
                val v = (sin(2 * PI * f * t) + .35 * sin(2 * PI * f * 2 * t) + .15 * sin(2 * PI * f * 3 * t)) * env * vol
                out[at + i] = (v * 9000).toInt().coerceIn(-32000, 32000).toShort()
            }
            at += n
        }
        if (thump) for (i in 0 until RATE / 6) {
            val v = sin(2 * PI * 70 * i / RATE) * exp(-18.0 * i / RATE) * 14000
            out[i] = (out[i] + v.toInt()).coerceIn(-32000, 32000).toShort()
        }
        return out
    }

    private fun play(c: Context, pcm: ShortArray) {
        if (!LauncherPrefs.sounds) return
        runCatching {
            val track = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(pcm.size * 2).setTransferMode(AudioTrack.MODE_STATIC).build()
            track.write(pcm, 0, pcm.size); track.play()
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ runCatching { track.release() } }, pcm.size * 1000L / RATE + 400)
        }
    }

    /** The one short beep of a PC that passed its self-test. */
    fun postBeep(c: Context) = play(c, synth(listOf(Triple(1000.0, 170, 1.0)), false))

    /** The error: two falling notes with a thump, like the stop sound of old Windows. */
    fun error(c: Context) = play(c, synth(listOf(Triple(466.0, 260, 1.0), Triple(311.0, 420, 1.0)), true))
}

private val MONO = FontFamily.Monospace

/** The AMI logo's idea drawn by hand: an orange pyramid of stripes (they widen towards the foot), with the dark "V" cut out of its middle. */
@Composable
private fun AmiLogo(modifier: Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        val orange = Color(0xFFF26A1F)
        val w = size.width; val h = size.height
        val n = 11
        for (i in 0 until n) {
            val top = h * i / n; val bot = h * (i + .62f) / n
            val half = { y: Float -> w / 2f * (y / h) }
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(w / 2f - half(top), top); lineTo(w / 2f + half(top), top); lineTo(w / 2f + half(bot), bot); lineTo(w / 2f - half(bot), bot); close()
            }
            drawPath(path, orange)
        }
        // the dark notch of the middle
        val v = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * .50f, h * .30f); lineTo(w * .68f, h * .66f); lineTo(w * .32f, h * .66f); close()
        }
        drawPath(v, Color(0xFF0A0A12))
    }
}

/** The BIOS screen of an old PC, laid out as the AMI one: the logo and name, the maker line, the machine's own CPU and memory, the drives, "Press F1 to Run SETUP". A tap skips it. */
@Composable
fun BiosScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val lines = remember { mutableStateListOf<String>() }
    var mem by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        BootSounds.postBeep(ctx)
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val totalMb = ((android.app.ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }.totalMem) / (1024 * 1024)).toInt().coerceAtLeast(1024)
        val cores = Runtime.getRuntime().availableProcessors()
        val model = android.os.Build.MODEL.uppercase().take(28)
        val script = listOf(
            "AMIBIOS(C)2021 American Megatrends, Inc." to 140L,
            "" to 30L,
            "$model ACPI BIOS Revision 6042" to 90L,
            "CPU: ${android.os.Build.HARDWARE.replaceFirstChar { it.uppercase() }} $cores-Core Processor" to 80L,
            " Speed: 3200MHz" to 110L,
            "" to 40L,
        )
        for ((l, d) in script) { lines.add(l); delay(d) }
        lines.add("MEM"); val mi = lines.lastIndex
        var m = 0
        while (m < totalMb) { m = (m + totalMb / 14).coerceAtMost(totalMb); mem = m; delay(34) }
        lines[mi] = "Total Memory: ${totalMb}MB (LPDDR5)"
        val rest = listOf(
            "" to 40L,
            "USB Devices total: 0 Drive, 1 Keyboard, 1 Mouse, 0 Hub" to 140L,
            "" to 40L,
            "Detected ATA/ATAPI Devices..." to 160L,
            "SATA6G_1:  PULSE PHONE FLASH" to 120L,
            "" to 90L,
            "Press F1 to Run SETUP" to 520L,
        )
        for ((l, d) in rest) { lines.add(l); delay(d) }
        delay(350)
        onDone()
    }
    Column(Modifier.fillMaxSize().background(Color(0xFF0A0A12)).clickable { onDone() }.statusBarsPadding().padding(horizontal = 10.dp, vertical = 10.dp)) {
        androidx.compose.foundation.layout.Row(Modifier.padding(bottom = 18.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            AmiLogo(Modifier.size(width = 74.dp, height = 40.dp))
            Column(Modifier.padding(start = 10.dp)) {
                Text("American", color = Color(0xFFF2F2F2), fontFamily = FontFamily.Serif, fontSize = 27.sp, lineHeight = 26.sp)
                Text("Megatrends", color = Color(0xFFF2F2F2), fontFamily = FontFamily.Serif, fontSize = 27.sp, lineHeight = 26.sp)
            }
        }
        lines.forEach { l ->
            val shown = if (l == "MEM") "Total Memory: ${mem}MB" else l
            if (shown.isEmpty()) Spacer(Modifier.height(10.dp))
            else Text(shown, color = Color(0xFFE8E8E8), fontFamily = BIOS_FONT, fontSize = 15.sp, lineHeight = 17.sp)
        }
    }
}

private val BIOS_FONT = FontFamily(androidx.compose.ui.text.font.Font(com.umutk.pulse.R.font.vt323_regular))

/**
 * A blue screen in the style of the look (95 and XP and 7 the white-on-blue text, 10 the sad face on blue, 11 the same on black) with its stop code, a percent counter, and a line that
 * says it is a joke. It lasts a few seconds, then the start goes on. A tap skips it.
 */
@Composable
fun BsodScreen(lookId: String, onDone: () -> Unit) {
    val ctx = LocalContext.current
    var pct by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        BootSounds.error(ctx)
        delay(1500)
        while (pct < 100) { pct += (3..9).random().coerceAtMost(100 - pct); delay(160) }
        delay(1700)
        onDone()
    }
    val modern = lookId == "win10" || lookId == "win11"
    val bg = when (lookId) { "win11" -> Color(0xFF000000); "win10" -> Color(0xFF0078D7); else -> Color(0xFF0000AA) }
    Column(Modifier.fillMaxSize().background(bg).clickable { onDone() }.statusBarsPadding().padding(horizontal = 22.dp, vertical = 28.dp)) {
        if (modern) {
            Text(":(", color = Color.White, fontSize = 72.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.height(18.dp))
            Text(tr("Your PC ran into a problem and needs to restart. We're just collecting some error info, and then we'll restart for you.", "Bilgisayarınızda bir sorun oluştu ve yeniden başlatılması gerekiyor. Yalnızca bazı hata bilgilerini topluyoruz, sonra sizin için yeniden başlatacağız."), color = Color.White, fontSize = 17.sp, lineHeight = 24.sp)
            Spacer(Modifier.height(18.dp))
            Text("$pct% " + tr("complete", "tamamlandı"), color = Color.White, fontSize = 17.sp)
            Spacer(Modifier.height(28.dp))
            Text(tr("Stop code: PULSE_JOKE_NOT_A_REAL_ERROR", "Durdurma kodu: PULSE_ŞAKA_GERÇEK_HATA_DEĞİL"), color = Color.White, fontSize = 12.sp)
        } else {
            Text("A problem has been detected and Pulse has been shut down to prevent damage to your phone.", color = Color.White, fontFamily = MONO, fontSize = 12.sp, lineHeight = 17.sp)
            Spacer(Modifier.height(14.dp))
            Text("PULSE_JOKE_NOT_A_REAL_ERROR", color = Color.White, fontFamily = MONO, fontSize = 12.sp)
            Spacer(Modifier.height(14.dp))
            Text("If this is the first time you've seen this Stop error screen, restart your phone. If this screen appears again, follow these steps:\n\nCheck to make sure any new hardware or software is properly installed. If this is a new installation, ask your hardware or software manufacturer for any Windows updates you might need.", color = Color.White, fontFamily = MONO, fontSize = 12.sp, lineHeight = 17.sp)
            Spacer(Modifier.height(14.dp))
            Text("*** STOP: 0x0000007E (0xC0000005, 0xF8A1B2C3, 0xF8A1B0DC, 0xF8A1AD8C)", color = Color.White, fontFamily = MONO, fontSize = 12.sp, lineHeight = 17.sp)
            Spacer(Modifier.height(14.dp))
            Text("Beginning dump of physical memory\nPhysical memory dump complete: $pct%", color = Color.White, fontFamily = MONO, fontSize = 12.sp, lineHeight = 17.sp)
        }
        Spacer(Modifier.weight(1f))
        Text(if (pct >= 100) tr("Just kidding. Nothing is wrong, this is a joke of Pulse. Starting again…", "Şaka yaptık. Bir sorun yok, bu Pulse'un şakası. Yeniden başlıyor…") else "", color = Color.White.copy(alpha = .85f), fontFamily = if (modern) FontFamily.Default else MONO, fontSize = 12.sp)
    }
}
