package com.umutk.pulse.extras.colorpick

import com.umutk.pulse.features.collapsePanel
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.service.quicksettings.TileService
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.tr

/** The colours picked before, newest first (16 at most), kept between uses. */
object ColorHistory {
    private fun sp(c: Context) = c.getSharedPreferences("color_pick", Context.MODE_PRIVATE)
    fun all(c: Context): List<Int> = sp(c).getString("h", "").orEmpty().split(',').mapNotNull { it.toLongOrNull(16)?.toInt() }
    fun add(c: Context, argb: Int) { sp(c).edit().putString("h", (listOf(argb) + all(c).filter { it != argb }).take(16).joinToString(",") { Integer.toHexString(it) }).apply() }
}

fun hexOf(argb: Int) = "#%06X".format(argb and 0xFFFFFF)

/** The quick settings tile: the eyedropper over whatever is on the screen. */
class ColorPickTile : TileService() {
    override fun onClick() {
        val i = Intent(this, ColorPickActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (android.os.Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(android.app.PendingIntent.getActivity(this, 7_900, i, android.app.PendingIntent.FLAG_IMMUTABLE))
        else @Suppress("DEPRECATION") startActivityAndCollapse(i)
        collapsePanel()
    }
}

/**
 * The colour picker: the screen as it was a moment ago (the system's screenshot through root, Shizuku or the accessibility service; a window that forbids pictures
 * comes out black, as everywhere), where a touch takes the colour under the finger with a magnifier; and the picker with hue, saturation and brightness.
 * The colour's code is copied and kept in the history.
 */
class ColorPickActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val shot = mutableStateOf<ImageBitmap?>(null)
        val busy = mutableStateOf(com.umutk.pulse.extras.screenrec.ScreenShot.possible())
        if (busy.value) {
            // the shade is gone and this window is still see-through when the picture is taken
            window.decorView.postDelayed({
                com.umutk.pulse.extras.screenrec.ScreenShot.take(this) { path ->
                    val bmp = path?.let { p -> runCatching { BitmapFactory.decodeFile(p) }.getOrNull().also { runCatching { java.io.File(p).delete() } } }
                    shot.value = bmp?.asImageBitmap(); busy.value = false
                }
            }, 450)
        }
        setContent { Picker(shot.value, busy.value) { finish() } }
    }
}

@Composable
private fun Picker(shot: ImageBitmap?, busy: Boolean, close: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var color by remember { mutableStateOf(ColorHistory.all(ctx).firstOrNull() ?: 0xFF2B7DE9.toInt()) }
    var history by remember { mutableStateOf(ColorHistory.all(ctx)) }
    var at by remember { mutableStateOf<Offset?>(null) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    val hsv = remember(color) { FloatArray(3).also { android.graphics.Color.colorToHSV(color, it) } }
    fun keep(c: Int) {
        color = c; ColorHistory.add(ctx, c); history = ColorHistory.all(ctx)
        runCatching { (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("colour", hexOf(c))) }
    }
    fun sample(o: Offset) {
        val s = shot ?: return
        if (box.width == 0) return
        val x = (o.x / box.width * s.width).toInt().coerceIn(0, s.width - 1); val y = (o.y / box.height * s.height).toInt().coerceIn(0, s.height - 1)
        val px = IntArray(1); s.readPixels(px, x, y, 1, 1); color = px[0] or 0xFF000000.toInt()
    }
    // nothing is drawn while the screen is being taken (the window is see-through, so the picture shows what was under it)
    if (busy && shot == null) return
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (shot != null) {
            Image(shot, null, Modifier.fillMaxSize().onSizeChanged { box = it }
                .pointerInput(shot) { detectTapGestures { o -> at = o; sample(o); keep(color) } }
                .pointerInput(shot) { detectDragGestures(onDragEnd = { keep(color) }) { ch, _ -> at = ch.position; sample(ch.position) } }, contentScale = ContentScale.FillBounds)
            at?.let { p ->
                // the magnifier ring in the picked colour, above the finger
                Canvas(Modifier.fillMaxSize()) {
                    val c = p.copy(y = (p.y - 140f).coerceAtLeast(70f))
                    drawCircle(Color(color), 56f, c)
                    drawCircle(Color.White, 58f, c, style = Stroke(5f))
                    drawCircle(Color.Black, 63f, c, style = Stroke(2f))
                    drawLine(Color.White, p - Offset(14f, 0f), p + Offset(14f, 0f), 2f); drawLine(Color.White, p - Offset(0f, 14f), p + Offset(0f, 14f), 2f)
                }
            }
        } else Text(if (busy) tr("Taking the screen…", "Ekran alınıyor…") else tr("The screen could not be taken (it needs root, Shizuku or Pulse's accessibility service); the picker below still works.", "Ekran alınamadı (root, Shizuku ya da Pulse'un erişilebilirlik hizmeti gerekir); alttaki seçici yine çalışır."),
            color = Color.White, fontSize = 14.sp, modifier = Modifier.align(Alignment.Center).padding(24.dp))
        // the panel: the colour and its codes, the picker's sliders, the history
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0xE6101010)).navigationBarsPadding().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(Color(color)).border(1.dp, Color.White.copy(alpha = .4f), RoundedCornerShape(10.dp)))
                Column(Modifier.weight(1f).padding(start = 12.dp).clickable { keep(color) }) {
                    Text(hexOf(color), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    val c = Color(color)
                    Text("rgb(${(c.red * 255).toInt()}, ${(c.green * 255).toInt()}, ${(c.blue * 255).toInt()})", color = Color(0xFFB0B0B0), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
                Text(tr("Close", "Kapat"), color = Color(0xFF8AB4F8), fontSize = 15.sp, modifier = Modifier.clickable { close() }.padding(8.dp))
            }
            listOf(0 to 360f, 1 to 1f, 2 to 1f).forEach { (k, max) ->
                Slider(hsv[k], { v -> val h = hsv.copyOf(); h[k] = v; color = android.graphics.Color.HSVToColor(h) }, valueRange = 0f..max, onValueChangeFinished = { keep(color) })
            }
            if (history.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                history.forEach { h -> Box(Modifier.size(30.dp).clip(CircleShape).background(Color(h)).border(1.dp, Color.White.copy(alpha = .4f), CircleShape).clickable { keep(h) }) }
            }
        }
    }
}
