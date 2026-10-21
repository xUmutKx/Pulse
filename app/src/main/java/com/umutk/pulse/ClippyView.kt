package com.umutk.pulse

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import com.umutk.pulse.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.random.Random

/** Office Assistant (Clippy and friends) for the 95 / 2003 Office looks. Sprite sheets + frame tables live in assets/clippy (repacked from the clippy.js agents). */
object ClippyPrefs {
    var on by mutableStateOf(true)
    var who by mutableStateOf("clippy")   // clippy / links
    private fun sp(c: Context) = c.getSharedPreferences("office", Context.MODE_PRIVATE)
    fun load(c: Context) { val p = sp(c); on = p.getBoolean("clippy_on", true); who = p.getString("clippy_who", "clippy") ?: "clippy" }
    fun setOn(c: Context, v: Boolean) { on = v; sp(c).edit().putBoolean("clippy_on", v).apply() }
    fun setWho(c: Context, v: String) { who = v; sp(c).edit().putString("clippy_who", v).apply() }
}

/** [cell] is the index in the repacked sheet, [dur] ms, [br] pairs (frameIndex, percent chance) to jump to instead of the next frame. */
private class CFrame(val cell: Int, val dur: Int, val br: IntArray)
private class CAgent(val w: Int, val h: Int, val cols: Int, val sheet: ImageBitmap, val anims: Map<String, List<CFrame>>)

private fun loadAgent(c: Context, who: String): CAgent? = runCatching {
    val lines = c.assets.open("clippy/$who.dat").bufferedReader().readLines().filter { it.isNotBlank() }
    val (w, h, cols) = lines[0].trim().split(' ').map { it.toInt() }
    val anims = lines.drop(1).associate { l ->
        val name = l.substringBefore('|')
        name to l.substringAfter('|').split(',').map { f ->
            val p = f.split(':')
            CFrame(p[0].toInt(), p[1].toInt(), if (p.size > 2 && p[2].isNotEmpty()) p[2].split('/').map { it.toInt() }.toIntArray() else IntArray(0))
        }
    }
    val bmp = c.assets.open("clippy/${who}_map.webp").use { BitmapFactory.decodeStream(it) }
    CAgent(w, h, cols, bmp.asImageBitmap(), anims)
}.getOrNull()

private fun clippyTips() = listOf(
    tr("It looks like you are writing a document. Need help?", "Bir belge yazıyorsun gibi görünüyor. Yardım ister misin?"),
    tr("Tip: tap File to save a copy.", "İpucu: kopya kaydetmek için Dosya'ya dokun."),
    tr("Tip: the ribbon has bold, italic and more.", "İpucu: şeritte kalın, italik ve daha fazlası var."),
    tr("Don't forget to save!", "Kaydetmeyi unutma!"),
    tr("I am here if you need me.", "İhtiyacın olursa buradayım."),
)

@Composable
fun ClippyView(modifier: Modifier = Modifier, saved: Boolean = false) {
    val ctx = LocalContext.current; val who = ClippyPrefs.who
    val agent by produceState<CAgent?>(null, who) { value = withContext(Dispatchers.IO) { loadAgent(ctx, who) } }
    val a = agent ?: return
    var cell by remember(a) { mutableStateOf(a.anims["RestPose"]?.firstOrNull()?.cell ?: 0) }
    var balloon by remember { mutableStateOf<String?>(null) }
    var req by remember { mutableStateOf("Greeting" to 0) }   // animation name + a counter so the same one can be asked twice
    var drag by remember { mutableStateOf(Offset.Zero) }
    val rest = a.anims["RestPose"]?.firstOrNull()?.cell ?: 0

    LaunchedEffect(a) {
        balloon = tr("Hi! I am your Office Assistant.", "Merhaba! Ben Office Yardımcınızım.")
        delay(7000); balloon = null
    }
    LaunchedEffect(saved) { if (saved) { balloon = tr("Saved!", "Kaydedildi!"); req = "Save" to (req.second + 1); delay(3500); balloon = null } }
    // play the requested animation, then rest; when nothing is asked for a while, an idle one
    LaunchedEffect(a, req) {
        suspend fun play(name: String) {
            val fr = a.anims[name] ?: return
            var i = 0; var steps = 0
            while (i < fr.size && steps++ < 300) {
                val f = fr[i]; cell = f.cell; delay(f.dur.toLong().coerceAtLeast(30))
                var next = i + 1; val roll = Random.nextInt(100); var acc = 0
                var k = 0
                while (k + 1 < f.br.size) { acc += f.br[k + 1]; if (roll < acc) { next = f.br[k]; break }; k += 2 }
                i = next
            }
            cell = rest
        }
        play(req.first)
        while (true) {
            delay(Random.nextLong(7000, 14000))
            val idles = a.anims.keys.filter { it.startsWith("Idle") }
            if (idles.isNotEmpty()) play(idles.random())
        }
    }

    val w = a.w; val h = a.h
    Column(modifier.offset { IntOffset(drag.x.roundToInt(), drag.y.roundToInt()) }, horizontalAlignment = Alignment.End) {
        balloon?.let { msg ->
            Row(Modifier.widthIn(max = 190.dp).background(Color(0xFFFFFFE1)).border(1.dp, Color.Black).padding(start = 8.dp, top = 6.dp, bottom = 6.dp, end = 4.dp), verticalAlignment = Alignment.Top) {
                Text(msg, color = Color.Black, fontSize = 12.sp, modifier = Modifier.weight(1f, fill = false))
                Icon(Icons.Outlined.Close, null, Modifier.padding(start = 4.dp).size(14.dp).clickable { balloon = null }, Color.Black)
            }
            Spacer(Modifier.height(4.dp))
        }
        Canvas(Modifier.size((w).dp, (h).dp)
            .pointerInput(a) { detectDragGestures { ch, d -> ch.consume(); drag += d } }
            .pointerInput(a) { detectTapGestures {
                val pool = listOf("Wave", "GetAttention", "Explain", "Congratulate", "Thinking", "Searching").filter { n -> a.anims.containsKey(n) }
                balloon = clippyTips().random(); if (pool.isNotEmpty()) req = pool.random() to (req.second + 1)
            } }) {
            drawImage(a.sheet, IntOffset((cell % a.cols) * w, (cell / a.cols) * h), IntSize(w, h), dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()), filterQuality = FilterQuality.None)
        }
    }
}
