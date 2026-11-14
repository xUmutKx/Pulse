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

/** The BIOS screen of an old PC: grey text on black, the maker line, the memory count, the drives, then "booting". A tap skips it. */
@Composable
fun BiosScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val lines = remember { mutableStateListOf<String>() }
    var mem by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        BootSounds.postBeep(ctx)
        val script = listOf(
            "AMIBIOS (C)2009 American Megatrends, Inc." to 120L,
            "PULSE-PH 1.00  BIOS Date: 11/14/26 08:00:00 Ver: 08.00.15" to 80L,
            "CPU : Pulse Octa-Core Processor" to 60L,
            "       Speed : 3.20 GHz" to 100L,
            "" to 40L,
            "Press DEL to run Setup" to 120L,
            "Press F8 for BBS POPUP" to 120L,
        )
        for ((l, d) in script) { lines.add(l); delay(d) }
        // the memory count
        lines.add("MEM"); val mi = lines.lastIndex
        val total = 8_388_608
        var m = 0
        while (m < total) { m = (m + 524_288).coerceAtMost(total); mem = m; delay(28) }
        lines[mi] = "Memory Test : ${total}K OK"
        val rest = listOf(
            "" to 40L,
            "Initializing USB Controllers .. Done." to 150L,
            "8192MB OK" to 60L,
            "Auto-Detecting Pri Master..IDE Hard Disk" to 130L,
            "Pri Master : Pulse Phone Flash 256GB" to 90L,
            "Auto-Detecting Pri Slave..Not Detected" to 90L,
            "" to 60L,
            "Booting from Hard Disk..." to 300L,
        )
        for ((l, d) in rest) { lines.add(l); delay(d) }
        delay(250)
        onDone()
    }
    Column(Modifier.fillMaxSize().background(Color.Black).clickable { onDone() }.statusBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp)) {
        lines.forEach { l ->
            val shown = if (l == "MEM") "Memory Test : ${mem}K" else l
            if (shown.isEmpty()) Spacer(Modifier.height(10.dp))
            else Text(shown, color = if (l.startsWith("Press")) Color(0xFFFFFFFF) else Color(0xFFC0C0C0), fontFamily = MONO, fontSize = 11.sp, lineHeight = 15.sp)
        }
    }
}

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
