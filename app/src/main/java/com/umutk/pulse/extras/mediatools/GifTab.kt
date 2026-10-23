package com.umutk.pulse.extras.mediatools

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PButton
import com.umutk.pulse.PCard
import com.umutk.pulse.PChip
import com.umutk.pulse.PField
import com.umutk.pulse.PSlider
import com.umutk.pulse.PSwitch
import com.umutk.pulse.T
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

/** Pictures and the time each one shows: what is being edited. */
private class Anim(val frames: List<Bitmap>, val delays: List<Int>) {
    val w get() = frames.firstOrNull()?.width ?: 0
    val h get() = frames.firstOrNull()?.height ?: 0
    fun map(f: (Bitmap) -> Bitmap) = Anim(frames.map(f), delays)
}

private const val BUDGET = 110L * 1024 * 1024   // bytes of pictures kept in memory
private fun maxFrames(w: Int, h: Int) = (BUDGET / (w.toLong() * h * 4).coerceAtLeast(1)).toInt().coerceIn(2, 300)

/** The GIF editor: make a GIF from a video or pictures, or open one, then cut, turn, resize, crop, speed up, add text, colour it, shrink it and save it. */
@Composable
internal fun GifTab() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var anim by remember { mutableStateOf<Anim?>(null) }
    val undo = remember { ArrayList<Anim>() }
    var busy by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var fps by remember { mutableStateOf(10f) }
    var maxW by remember { mutableStateOf(400f) }
    var videoSecs by remember { mutableStateOf(0f) }
    var videoUri by remember { mutableStateOf<Uri?>(null) }
    var from by remember { mutableStateOf(0f) }
    var to by remember { mutableStateOf(5f) }
    // export settings
    var loops by remember { mutableStateOf(0) }
    var colors by remember { mutableStateOf(255) }
    var optimize by remember { mutableStateOf(true) }
    var dither by remember { mutableStateOf(false) }

    fun set(a: Anim) { anim?.let { undo += it; if (undo.size > 6) undo.removeAt(0) }; anim = a }
    fun run(label: String, work: suspend () -> Anim?) { scope.launch { busy = label; note = ""; val r = runCatching { work() }; busy = ""; r.getOrNull()?.let { set(it) }; r.exceptionOrNull()?.let { note = tr("Failed: ", "Olmadı: ") + (it.message ?: it.javaClass.simpleName) } } }

    val pickGif = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) run(tr("Reading the GIF", "GIF okunuyor")) { withContext(Dispatchers.Default) { readGif(ctx, u, fps.roundToInt(), maxW.roundToInt()) } }
    }
    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) {
            videoUri = u
            videoSecs = runCatching { val r = MediaMetadataRetriever(); r.setDataSource(ctx, u); val d = (r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) / 1000f; r.release(); d }.getOrDefault(0f)
            from = 0f; to = minOf(videoSecs, 5f)
        }
    }
    val pickPics = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { list ->
        if (list.isNotEmpty()) run(tr("Reading the pictures", "Resimler okunuyor")) { withContext(Dispatchers.Default) { fromPictures(ctx, list, maxW.roundToInt()) } }
    }

    PCard {
        Text(tr("Start from", "Şundan başla"), color = T.text, fontSize = 16.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PButton(tr("A GIF", "Bir GIF"), compact = true) { pickGif.launch(arrayOf("image/gif")) }
            PButton(tr("A video", "Bir video"), compact = true) { pickVideo.launch(arrayOf("video/*")) }
            PButton(tr("Pictures", "Resimler"), compact = true) { pickPics.launch(arrayOf("image/*")) }
        }
        Text("${tr("Pictures per second", "Saniyede resim")}: ${fps.roundToInt()}", color = T.sub, fontSize = 12.sp)
        PSlider(fps, { fps = it }, 2f..24f, onDone = {})
        Text("${tr("Width", "Genişlik")}: ${maxW.roundToInt()} px", color = T.sub, fontSize = 12.sp)
        PSlider(maxW, { maxW = it }, 120f..720f, onDone = {})
        if (videoUri != null && videoSecs > 0f) {
            Text("${tr("Video part", "Video bölümü")}: %.1f – %.1f s".format(from, to), color = T.text, fontSize = 13.sp)
            PSlider(from, { from = minOf(it, to - 0.2f).coerceAtLeast(0f) }, 0f..videoSecs, onDone = {})
            PSlider(to, { to = maxOf(it, from + 0.2f).coerceAtMost(videoSecs) }, 0f..videoSecs, onDone = {})
            PButton(tr("Make the GIF", "GIF yap"), compact = true) {
                val u = videoUri ?: return@PButton
                run(tr("Taking pictures from the video", "Videodan resim alınıyor")) { withContext(Dispatchers.Default) { fromVideo(ctx, u, from, to, fps.roundToInt(), maxW.roundToInt()) } }
            }
        }
    }
    if (busy.isNotEmpty()) Text(busy + "…", color = T.text, fontSize = 14.sp)
    if (note.isNotEmpty()) Text(note, color = T.text, fontSize = 13.sp)
    val a = anim ?: return
    // the preview plays the pictures with their own times
    PCard {
        var shown by remember(a) { mutableStateOf(0) }
        LaunchedEffect(a) { while (true) { delay(a.delays[shown.coerceIn(0, a.delays.lastIndex)].toLong().coerceAtLeast(20)); shown = (shown + 1) % a.frames.size } }
        Box(Modifier.fillMaxWidth().heightIn(max = 300.dp), contentAlignment = Alignment.Center) {
            Image(a.frames[shown.coerceIn(0, a.frames.lastIndex)].asImageBitmap(), null, Modifier.fillMaxWidth().heightIn(max = 300.dp), contentScale = ContentScale.Fit)
        }
        Text("${a.frames.size} " + tr("pictures", "resim") + " · ${a.w}×${a.h} · %.1f s".format(a.delays.sum() / 1000f), color = T.sub, fontSize = 12.sp)
    }
    EditTools(a, ::set, { run(it.first, it.second) }, undo, { anim = undo.removeAt(undo.lastIndex) })
    PCard {
        Text(tr("Save", "Kaydet"), color = T.text, fontSize = 16.sp)
        Text(tr("Repeat", "Tekrar"), color = T.sub, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PChip(loops == 0, tr("Forever", "Sonsuz")) { loops = 0 }
            PChip(loops == 1, "1×") { loops = 1 }
            PChip(loops == 3, "3×") { loops = 3 }
        }
        Text(tr("Colours", "Renk"), color = T.sub, fontSize = 12.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(255, 128, 64, 32, 16).forEach { n -> PChip(colors == n, "$n") { colors = n } }
        }
        Row(verticalAlignment = Alignment.CenterVertically) { Text(tr("Optimise (store only what changes)", "En iyile (yalnız değişeni sakla)"), color = T.text, fontSize = 14.sp, modifier = Modifier.weight(1f)); PSwitch(optimize, { optimize = it }) }
        Row(verticalAlignment = Alignment.CenterVertically) { Text(tr("Dither (smoother colours, bigger file)", "Dither (daha yumuşak renk, büyük dosya)"), color = T.text, fontSize = 14.sp, modifier = Modifier.weight(1f)); PSwitch(dither, { dither = it }) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PButton(tr("Save GIF", "GIF kaydet")) {
                run(tr("Writing the GIF", "GIF yazılıyor")) {
                    withContext(Dispatchers.Default) {
                        val bytes = ByteArrayOutputStream()
                        val enc = GifEncoder(bytes, a.w, a.h, if (loops == 0) 0 else loops - 1, colors, optimize, dither)
                        a.frames.forEachIndexed { i, f -> enc.add(f, a.delays[i]) }
                        enc.finish()
                        val data = bytes.toByteArray()
                        val name = "pulse_" + System.currentTimeMillis().toString(36) + ".gif"
                        com.umutk.pulse.docScanSave(ctx, name, "image/gif") { it.write(data) }
                        note = tr("Saved ", "Kaydedildi: ") + name + " (" + fmt(data.size.toLong()) + ")"
                    }
                    null
                }
            }
            PButton(tr("Save the pictures", "Resimleri kaydet"), compact = true) {
                run(tr("Saving the pictures", "Resimler kaydediliyor")) {
                    withContext(Dispatchers.Default) {
                        val tag = System.currentTimeMillis().toString(36)
                        a.frames.forEachIndexed { i, f -> val b = ByteArrayOutputStream(); f.compress(Bitmap.CompressFormat.PNG, 100, b); com.umutk.pulse.docScanSave(ctx, "gif_${tag}_%03d.png".format(i + 1), "image/png") { it.write(b.toByteArray()) } }
                        note = tr("Saved ${a.frames.size} pictures in Download/Pulse", "${a.frames.size} resim Download/Pulse içine kaydedildi")
                    }
                    null
                }
            }
        }
    }
}

/** The editing buttons: each one changes every picture and can be undone. */
@Composable
private fun EditTools(a: Anim, set: (Anim) -> Unit, run: (Pair<String, suspend () -> Anim?>) -> Unit, undo: List<Anim>, onUndo: () -> Unit) {
    var textIn by remember { mutableStateOf("") }
    var textY by remember { mutableStateOf(85f) }
    var textSize by remember { mutableStateOf(12f) }
    var textWhite by remember { mutableStateOf(true) }
    var speed by remember { mutableStateOf(1f) }
    var newW by remember(a.w) { mutableStateOf(a.w.toFloat()) }
    var cropL by remember { mutableStateOf(0f) }; var cropT by remember { mutableStateOf(0f) }; var cropR by remember { mutableStateOf(0f) }; var cropB by remember { mutableStateOf(0f) }
    var cutFrom by remember(a) { mutableStateOf(0f) }; var cutTo by remember(a) { mutableStateOf((a.frames.size - 1).toFloat()) }
    PCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Edit", "Düzenle"), color = T.text, fontSize = 16.sp, modifier = Modifier.weight(1f))
            if (undo.isNotEmpty()) PButton(tr("Undo", "Geri al"), compact = true) { onUndo() }
        }
        Text(tr("Order and direction", "Sıra ve yön"), color = T.sub, fontSize = 12.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PChip(false, tr("Reverse", "Ters çevir")) { set(Anim(a.frames.reversed(), a.delays.reversed())) }
            PChip(false, tr("Back and forth", "İleri geri")) { set(Anim(a.frames + a.frames.reversed().drop(1).dropLast(1), a.delays + a.delays.reversed().drop(1).dropLast(1))) }
            PChip(false, tr("Every other picture", "Bir resim atla")) { set(Anim(a.frames.filterIndexed { i, _ -> i % 2 == 0 }, a.delays.filterIndexed { i, _ -> i % 2 == 0 }.map { it * 2 })) }
        }
        Text(tr("Turn and mirror", "Döndür ve aynala"), color = T.sub, fontSize = 12.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PChip(false, "↻ 90°") { set(a.map { turn(it, 90f, false, false) }) }
            PChip(false, "↺ 90°") { set(a.map { turn(it, 270f, false, false) }) }
            PChip(false, "180°") { set(a.map { turn(it, 180f, false, false) }) }
            PChip(false, tr("Mirror ↔", "Ayna ↔")) { set(a.map { turn(it, 0f, true, false) }) }
            PChip(false, tr("Mirror ↕", "Ayna ↕")) { set(a.map { turn(it, 0f, false, true) }) }
        }
        Text("${tr("Speed", "Hız")}: ×%.2f".format(speed), color = T.sub, fontSize = 12.sp)
        PSlider(speed, { speed = it }, 0.25f..4f, onDone = {})
        PChip(false, tr("Change the speed", "Hızı değiştir")) { set(Anim(a.frames, a.delays.map { (it / speed).roundToInt().coerceAtLeast(20) })); speed = 1f }
        Text("${tr("Width", "Genişlik")}: ${newW.roundToInt()} px", color = T.sub, fontSize = 12.sp)
        PSlider(newW, { newW = it }, 40f..maxOf(a.w.toFloat() * 1.5f, 80f), onDone = {})
        PChip(false, tr("Resize", "Boyutlandır")) {
            val k = newW / a.w
            set(a.map { Bitmap.createScaledBitmap(it, newW.roundToInt().coerceAtLeast(2), (it.height * k).roundToInt().coerceAtLeast(2), true) })
        }
        Text(tr("Cut off the sides (%)", "Kenarlardan kes (%)"), color = T.sub, fontSize = 12.sp)
        listOf(Triple(tr("Left", "Sol"), cropL, { v: Float -> cropL = v }), Triple(tr("Top", "Üst"), cropT, { v: Float -> cropT = v }),
            Triple(tr("Right", "Sağ"), cropR, { v: Float -> cropR = v }), Triple(tr("Bottom", "Alt"), cropB, { v: Float -> cropB = v })).forEach { (n, v, f) ->
            Text("$n: ${v.roundToInt()}%", color = T.sub, fontSize = 12.sp); PSlider(v, f, 0f..45f, onDone = {})
        }
        PChip(false, tr("Crop", "Kırp")) {
            val l = (a.w * cropL / 100f).roundToInt(); val t = (a.h * cropT / 100f).roundToInt()
            val r = a.w - (a.w * cropR / 100f).roundToInt(); val b = a.h - (a.h * cropB / 100f).roundToInt()
            if (r - l >= 4 && b - t >= 4) { set(a.map { Bitmap.createBitmap(it, l, t, r - l, b - t) }); cropL = 0f; cropT = 0f; cropR = 0f; cropB = 0f }
        }
        if (a.frames.size > 2) {
            Text("${tr("Keep pictures", "Tutulacak resimler")}: ${cutFrom.roundToInt() + 1} – ${cutTo.roundToInt() + 1}", color = T.sub, fontSize = 12.sp)
            PSlider(cutFrom, { cutFrom = minOf(it, cutTo - 1f) }, 0f..(a.frames.size - 1).toFloat(), onDone = {})
            PSlider(cutTo, { cutTo = maxOf(it, cutFrom + 1f) }, 0f..(a.frames.size - 1).toFloat(), onDone = {})
            PChip(false, tr("Cut", "Kes")) { val s = cutFrom.roundToInt(); val e = cutTo.roundToInt(); set(Anim(a.frames.subList(s, e + 1), a.delays.subList(s, e + 1))); cutFrom = 0f }
        }
        Text(tr("Colour", "Renk"), color = T.sub, fontSize = 12.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PChip(false, tr("Black and white", "Siyah beyaz")) { set(a.map { tint(it, grayMatrix()) }) }
            PChip(false, "Sepia") { set(a.map { tint(it, sepiaMatrix()) }) }
            PChip(false, tr("Invert", "Ters renk")) { set(a.map { tint(it, invertMatrix()) }) }
            PChip(false, tr("Brighter", "Daha parlak")) { set(a.map { tint(it, brightMatrix(1.15f, 18f)) }) }
            PChip(false, tr("Darker", "Daha koyu")) { set(a.map { tint(it, brightMatrix(.87f, -14f)) }) }
            PChip(false, tr("More colour", "Daha canlı")) { set(a.map { tint(it, satMatrix(1.5f)) }) }
        }
        Text(tr("Text", "Yazı"), color = T.sub, fontSize = 12.sp)
        PField(textIn, { textIn = it }, tr("Words on the GIF", "GIF üzerine yazı"), Modifier.fillMaxWidth())
        Text("${tr("Size", "Boyut")}: ${textSize.roundToInt()}%   ${tr("Height", "Yükseklik")}: ${textY.roundToInt()}%", color = T.sub, fontSize = 12.sp)
        PSlider(textSize, { textSize = it }, 4f..30f, onDone = {})
        PSlider(textY, { textY = it }, 5f..100f, onDone = {})
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PChip(textWhite, tr("White", "Beyaz")) { textWhite = true }
            PChip(!textWhite, tr("Black", "Siyah")) { textWhite = false }
            PChip(false, tr("Add the text", "Yazıyı ekle")) { if (textIn.isNotBlank()) set(a.map { caption(it, textIn, textSize, textY, textWhite) }) }
        }
    }
}

private fun turn(b: Bitmap, deg: Float, flipX: Boolean, flipY: Boolean): Bitmap =
    Bitmap.createBitmap(b, 0, 0, b.width, b.height, Matrix().apply { postRotate(deg); postScale(if (flipX) -1f else 1f, if (flipY) -1f else 1f) }, true)

private fun tint(b: Bitmap, m: ColorMatrix): Bitmap {
    val o = Bitmap.createBitmap(b.width, b.height, Bitmap.Config.ARGB_8888)
    Canvas(o).drawBitmap(b, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(m) })
    return o
}
private fun grayMatrix() = ColorMatrix().apply { setSaturation(0f) }
private fun satMatrix(s: Float) = ColorMatrix().apply { setSaturation(s) }
private fun sepiaMatrix() = ColorMatrix(floatArrayOf(.393f, .769f, .189f, 0f, 0f, .349f, .686f, .168f, 0f, 0f, .272f, .534f, .131f, 0f, 0f, 0f, 0f, 0f, 1f, 0f))
private fun invertMatrix() = ColorMatrix(floatArrayOf(-1f, 0f, 0f, 0f, 255f, 0f, -1f, 0f, 0f, 255f, 0f, 0f, -1f, 0f, 255f, 0f, 0f, 0f, 1f, 0f))
private fun brightMatrix(scale: Float, shift: Float) = ColorMatrix(floatArrayOf(scale, 0f, 0f, 0f, shift, 0f, scale, 0f, 0f, shift, 0f, 0f, scale, 0f, shift, 0f, 0f, 0f, 1f, 0f))

/** The words on a picture, with an outline so they read on any background. */
private fun caption(b: Bitmap, text: String, sizePct: Float, yPct: Float, white: Boolean): Bitmap {
    val o = b.copy(Bitmap.Config.ARGB_8888, true)
    val c = Canvas(o)
    val size = o.height * sizePct / 100f
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (white) Color.WHITE else Color.BLACK; textSize = size; textAlign = Paint.Align.CENTER; typeface = android.graphics.Typeface.DEFAULT_BOLD }
    val edge = Paint(fill).apply { color = if (white) Color.BLACK else Color.WHITE; style = Paint.Style.STROKE; strokeWidth = size / 8f }
    val y = o.height * yPct / 100f
    c.drawText(text, o.width / 2f, y, edge); c.drawText(text, o.width / 2f, y, fill)
    return o
}

// ---- reading -------------------------------------------------------------------------------------------------------------------------------------

/** The pictures of a GIF taken at [fps] (the GIF's own times are not kept), at most [maxW] wide. */
@Suppress("DEPRECATION")
private fun readGif(ctx: android.content.Context, u: Uri, fps: Int, maxW: Int): Anim {
    val bytes = ctx.contentResolver.openInputStream(u)!!.use { it.readBytes() }
    val m = android.graphics.Movie.decodeByteArray(bytes, 0, bytes.size) ?: throw java.io.IOException("not a GIF")
    val sw = m.width().coerceAtLeast(1); val sh = m.height().coerceAtLeast(1)
    val k = minOf(1f, maxW.toFloat() / sw)
    val w = (sw * k).roundToInt().coerceAtLeast(2); val h = (sh * k).roundToInt().coerceAtLeast(2)
    val dur = m.duration().coerceAtLeast(100)
    val step = 1000 / fps.coerceAtLeast(1)
    val n = minOf(maxFrames(w, h), (dur / step).coerceAtLeast(1))
    val frames = ArrayList<Bitmap>(); val delays = ArrayList<Int>()
    for (i in 0 until n) {
        m.setTime((i.toLong() * dur / n).toInt().coerceAtMost(dur - 1))
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b); c.drawColor(Color.WHITE); c.scale(k, k); m.draw(c, 0f, 0f)
        frames += b; delays += (dur / n).coerceAtLeast(20)
    }
    return Anim(frames, delays)
}

/** [fps] pictures a second of the part [fromS]..[toS] of a video. */
private fun fromVideo(ctx: android.content.Context, u: Uri, fromS: Float, toS: Float, fps: Int, maxW: Int): Anim {
    val r = MediaMetadataRetriever()
    try {
        r.setDataSource(ctx, u)
        val vw = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: maxW
        val vh = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: maxW
        val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        val sw = if (rot == 90 || rot == 270) vh else vw; val shh = if (rot == 90 || rot == 270) vw else vh
        val k = minOf(1f, maxW.toFloat() / sw)
        val w = (sw * k).roundToInt().coerceAtLeast(2); val h = (shh * k).roundToInt().coerceAtLeast(2)
        val total = ((toS - fromS) * fps).roundToInt().coerceAtLeast(1)
        val n = minOf(total, maxFrames(w, h))
        val frames = ArrayList<Bitmap>(); val delays = ArrayList<Int>()
        for (i in 0 until n) {
            val us = ((fromS + (toS - fromS) * i / n) * 1_000_000f).toLong()
            val f = r.getFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST) ?: continue
            frames += if (f.width == w && f.height == h) f else Bitmap.createScaledBitmap(f, w, h, true)
            delays += ((toS - fromS) * 1000f / n).roundToInt().coerceAtLeast(20)
        }
        if (frames.isEmpty()) throw java.io.IOException("no pictures")
        return Anim(frames, delays)
    } finally { r.release() }
}

/** Pictures one after another, each shown for a second, all fitted into the size of the first. */
private fun fromPictures(ctx: android.content.Context, list: List<Uri>, maxW: Int): Anim {
    val first = decodeUpright(ctx, list.first(), maxW)
    val k = minOf(1f, maxW.toFloat() / first.width)
    val w = (first.width * k).roundToInt().coerceAtLeast(2); val h = (first.height * k).roundToInt().coerceAtLeast(2)
    val frames = list.take(maxFrames(w, h)).map { u ->
        val src = decodeUpright(ctx, u, maxW)
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b); c.drawColor(Color.WHITE)
        val s = minOf(w.toFloat() / src.width, h.toFloat() / src.height)
        val dw = (src.width * s).roundToInt(); val dh = (src.height * s).roundToInt()
        c.drawBitmap(src, null, Rect((w - dw) / 2, (h - dh) / 2, (w + dw) / 2, (h + dh) / 2), Paint(Paint.FILTER_BITMAP_FLAG))
        b
    }
    return Anim(frames, List(frames.size) { 1000 })
}
