package com.umutk.pulse.features

import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.umutk.pulse.PButton
import com.umutk.pulse.tr
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt
import com.umutk.pulse.R

/** The picture behind the Windows 11 windows: the phone wallpaper, the opening picture or your own, decoded once per choice (not animated). Off = plain colours. */
object Backdrop {
    /** Default: off, plain colours. The wallpapers are picked in Settings when wanted. */
    var mode by mutableStateOf("off")
    var image by mutableStateOf<ImageBitmap?>(null)
    /** Blur of the picture behind the whole app, in dp (0 = sharp). */
    var blur by mutableStateOf(8f)
    /** Which part of the picture shows when it is cropped to the screen: "top", "center" or "bottom". */
    /** The blurred copy of [image], rendered once per choice/blur/crop change off the main thread; null = draw [image] sharp. */
    var blurred by mutableStateOf<ImageBitmap?>(null)
    /** The Windows 11 stock wallpaper, behind the glass when no picture is chosen (decoded once in [load]). */
    var stock by mutableStateOf<ImageBitmap?>(null)
    var align by mutableStateOf("center")
    /** The crop chosen in the editor: where the screen sits in the free room (0..1 each way) and the zoom (1 = just fills). */
    var fx by mutableStateOf(.5f)
    var fy by mutableStateOf(.5f)
    var zoom by mutableStateOf(1f)
    private var custom: String? = null
    private const val PREFS = "p"
    /** The 16 bundled Windows 11 wallpapers (from the uploaded pack), named pack1..pack16 in the settings. */
    private val PACK = intArrayOf(R.drawable.win11_wall_1, R.drawable.win11_wall_2, R.drawable.win11_wall_3, R.drawable.win11_wall_4, R.drawable.win11_wall_5, R.drawable.win11_wall_6, R.drawable.win11_wall_7, R.drawable.win11_wall_8, R.drawable.win11_wall_9, R.drawable.win11_wall_10, R.drawable.win11_wall_11, R.drawable.win11_wall_12, R.drawable.win11_wall_13, R.drawable.win11_wall_14, R.drawable.win11_wall_15, R.drawable.win11_wall_16,
        R.drawable.mw11_1, R.drawable.mw11_2, R.drawable.mw11_3, R.drawable.mw11_4, R.drawable.mw11_5, R.drawable.mw11_6, R.drawable.mw11_7, R.drawable.mw11_8, R.drawable.mw11_9, R.drawable.mw11_10, R.drawable.mw11_11, R.drawable.mw11_12, R.drawable.mw11_13, R.drawable.mw11_14, R.drawable.mw11_15, R.drawable.mw11_16, R.drawable.mw11_17, R.drawable.mw11_18)

    fun load(c: Context) {
        val p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        mode = p.getString("backdrop", "off") ?: "off"
        custom = p.getString("backdrop_uri", null)
        blur = p.getFloat("backdrop_blur2", 8f)
        align = p.getString("backdrop_align", "center") ?: "center"
        fx = p.getFloat("backdrop_fx", .5f)
        // older choices only had Top/Middle/Bottom: they become the vertical focus
        fy = p.getFloat("backdrop_fy", alignY())
        zoom = p.getFloat("backdrop_zoom", 1f)
        image = render(c)
        stock = decodeRes(c, R.drawable.splash_win11_wallpaper)?.asImageBitmap()
        rebuildBlur()
    }

    /** Saves the choice and renders the blurred picture; call off the main thread. */
    fun set(c: Context, m: String, uri: Uri? = null) {
        if (uri != null) runCatching { c.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        custom = uri?.toString() ?: custom
        mode = m
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("backdrop", m).putString("backdrop_uri", custom).apply()
        image = render(c)
        rebuildBlur()
    }

    private const val MAX = 1280

    private fun decodeRes(c: Context, id: Int): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(c.resources, id, o)
        o.inSampleSize = sample(o.outWidth, o.outHeight); o.inJustDecodeBounds = false
        return BitmapFactory.decodeResource(c.resources, id, o)
    }

    private fun sample(w: Int, h: Int) = Integer.highestOneBit((maxOf(w, h) / MAX).coerceAtLeast(1))

    private fun source(c: Context): Bitmap? = when (mode) {
        "phone" -> runCatching { (WallpaperManager.getInstance(c).drawable as? BitmapDrawable)?.bitmap }.getOrNull()
        "opening" -> decodeRes(c, R.drawable.splash_win11_wallpaper)
        "custom" -> custom?.let { u -> runCatching {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            c.contentResolver.openInputStream(Uri.parse(u))?.use { BitmapFactory.decodeStream(it, null, o) }
            o.inSampleSize = sample(o.outWidth, o.outHeight); o.inJustDecodeBounds = false
            c.contentResolver.openInputStream(Uri.parse(u))?.use { BitmapFactory.decodeStream(it, null, o) }
        }.getOrNull() }
        else -> if (mode.startsWith("pack")) mode.removePrefix("pack").toIntOrNull()?.let { i -> decodeRes(c, PACK[(i - 1).coerceIn(0, PACK.size - 1)]) } else null
    }

    /** The sharp picture: at most 1280 px on its long side, never decoded full size. */
    private fun render(c: Context): ImageBitmap? {
        val b = source(c) ?: return null
        val k = minOf(1f, MAX.toFloat() / maxOf(b.width, b.height))
        val out = if (k < 1f) Bitmap.createScaledBitmap(b, (b.width * k).toInt().coerceAtLeast(1), (b.height * k).toInt().coerceAtLeast(1), true) else b.copy(Bitmap.Config.ARGB_8888, false)
        if (out !== b && mode != "phone") b.recycle()   // the phone wallpaper bitmap belongs to the system cache
        return out.asImageBitmap()
    }

    /** Pre-renders the blur: the picture is shrunk (1280 / (1 + blur/2) px wide) and drawn back up with bilinear filtering, which is the blur. Call off the main thread. */
    @Synchronized
    fun rebuildBlur() {
        val img = image
        if (img == null || blur < 1f) { blurred = null; return }
        var b = img.asAndroidBitmap()
        val target = (img.width / (1f + blur / 2f)).toInt().coerceAtLeast(16)
        var made = false
        while (b.width > target * 2) { val n = Bitmap.createScaledBitmap(b, b.width / 2, (b.height / 2).coerceAtLeast(1), true); if (made) b.recycle(); b = n; made = true }
        val h = (b.height * target.toFloat() / b.width).toInt().coerceAtLeast(1)
        val fin = Bitmap.createScaledBitmap(b, target, h, true)
        if (made && fin !== b) b.recycle()
        val small = fin.copy(Bitmap.Config.RGB_565, false)
        if (small !== fin) fin.recycle()
        blurred = small.asImageBitmap()
        // the composables still drawing the old one finish their frame first; it is dropped to the GC, not recycled
    }

    /** Saves the blur and renders it again (slider released). */
    fun saveBlur(c: Context) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putFloat("backdrop_blur2", blur).apply()
        Thread { rebuildBlur() }.start()
    }

    private fun alignY() = when (align) { "top" -> 0f; "bottom" -> 1f; else -> .5f }

    /** Top/Middle/Bottom: a shortcut that moves the vertical focus. */
    fun setAlign(c: Context, v: String) { align = v; setCrop(c, fx, alignY(), zoom); c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("backdrop_align", v).apply() }

    fun setCrop(c: Context, x: Float, y: Float, z: Float) {
        fx = x.coerceIn(0f, 1f); fy = y.coerceIn(0f, 1f); zoom = z.coerceIn(1f, MAX_ZOOM)
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putFloat("backdrop_fx", fx).putFloat("backdrop_fy", fy).putFloat("backdrop_zoom", zoom).apply()
    }

    /** For a plain Image with ContentScale.Crop: follows the focus, not the zoom ([drawBackdrop] does both). */
    fun alignment(): androidx.compose.ui.Alignment = androidx.compose.ui.BiasAlignment(fx * 2f - 1f, fy * 2f - 1f)

    const val MAX_ZOOM = 5f

    /** The part of [img] that fills a [w] x [h] screen: left, top, width, height in picture pixels. */
    fun crop(img: ImageBitmap, w: Float, h: Float, x: Float = fx, y: Float = fy, z: Float = zoom): FloatArray {
        val s = maxOf(w / img.width, h / img.height) * z
        val cw = minOf(img.width.toFloat(), w / s); val ch = minOf(img.height.toFloat(), h / s)
        return floatArrayOf((img.width - cw) * x, (img.height - ch) * y, cw, ch)
    }

    /** Draws the picture filling the area, cropped at the chosen focus and zoom. */
    fun DrawScope.drawBackdrop(img: ImageBitmap, x: Float = fx, y: Float = fy, z: Float = zoom) {
        val r = crop(img, size.width, size.height, x, y, z)
        val w = r[2].roundToInt(); val h = r[3].roundToInt()
        drawImage(img, srcOffset = IntOffset(r[0].roundToInt().coerceIn(0, img.width - w), r[1].roundToInt().coerceIn(0, img.height - h)), srcSize = IntSize(w, h), dstSize = IntSize(size.width.toInt(), size.height.toInt()))
    }
}

/** Full-screen crop editor: the whole picture with a phone-shaped frame; drag moves it, pinch zooms, Done saves. */
@Composable
fun BackdropEditor(onClose: () -> Unit) {
    val img = Backdrop.image ?: return
    val ctx = LocalContext.current
    CropEditor(img, Backdrop.fx, Backdrop.fy, Backdrop.zoom, Backdrop.blur, onClose) { x, y, z -> Backdrop.setCrop(ctx, x, y, z) }
}

/** The editor itself, shared by the Windows 11 background and the Windows 7 / XP wallpapers: the start crop in, the chosen crop out on Done. */
@Composable
fun CropEditor(img: ImageBitmap, x0: Float, y0: Float, z0: Float, blurDp: Float, onClose: () -> Unit, onDone: (Float, Float, Float) -> Unit) {
    val conf = LocalConfiguration.current
    val screenW = conf.screenWidthDp.toFloat(); val screenH = conf.screenHeightDp.toFloat()
    var x by remember { mutableStateOf(x0) }
    var y by remember { mutableStateOf(y0) }
    var z by remember { mutableStateOf(z0) }
    Dialog(onClose, DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            BoxWithConstraints(Modifier.fillMaxSize().padding(16.dp).padding(bottom = 64.dp)) {
                val bw = constraints.maxWidth.toFloat(); val bh = constraints.maxHeight.toFloat()
                val k = minOf(bw / img.width, bh / img.height)
                val ox = (bw - img.width * k) / 2; val oy = (bh - img.height * k) / 2
                val gestures = Modifier.pointerInput(img, k) {
                    detectTransformGestures { _, pan, gz, _ ->
                        z = (z * gz).coerceIn(1f, Backdrop.MAX_ZOOM)
                        val r = Backdrop.crop(img, screenW, screenH, x, y, z)
                        // the picture follows the finger (the frame moves the other way); no free room on an axis = nothing to move
                        val freeX = (img.width - r[2]) * k; val freeY = (img.height - r[3]) * k
                        if (freeX > 0f) x = (x - pan.x / freeX).coerceIn(0f, 1f)
                        if (freeY > 0f) y = (y - pan.y / freeY).coerceIn(0f, 1f)
                    }
                }
                // the picture, blurred like the real background, scaled down with the frame
                val shrink = Backdrop.crop(img, screenW, screenH, x, y, z)[2] * k / (screenW * LocalDensity.current.density)
                Canvas(Modifier.fillMaxSize().blur((blurDp * shrink).dp)) {
                    drawImage(img, dstOffset = IntOffset(ox.roundToInt(), oy.roundToInt()), dstSize = IntSize((img.width * k).roundToInt(), (img.height * k).roundToInt()))
                }
                // dim outside the frame, then the phone outline
                Canvas(Modifier.fillMaxSize().then(gestures)) {
                    val r = Backdrop.crop(img, screenW, screenH, x, y, z)
                    val fl = ox + r[0] * k; val ft = oy + r[1] * k; val fw = r[2] * k; val fh = r[3] * k
                    val corner = CornerRadius(minOf(fw, fh) * .08f)
                    val hole = Path().apply {
                        fillType = PathFillType.EvenOdd
                        addRect(Rect(0f, 0f, size.width, size.height))
                        addRoundRect(RoundRect(fl, ft, fl + fw, ft + fh, corner))
                    }
                    drawPath(hole, Color.Black.copy(alpha = .6f))
                    // a miniature of the app on top, so the result is visible: status bar, title bar, two cards
                    val card = Color.White.copy(alpha = .22f)
                    drawRect(Color.Black.copy(alpha = .25f), Offset(fl, ft), Size(fw, fh * .04f))
                    drawRect(card, Offset(fl, ft + fh * .04f), Size(fw, fh * .07f))
                    drawRoundRect(card, Offset(fl + fw * .05f, ft + fh * .15f), Size(fw * .9f, fh * .25f), CornerRadius(fw * .03f))
                    drawRoundRect(card, Offset(fl + fw * .05f, ft + fh * .44f), Size(fw * .9f, fh * .25f), CornerRadius(fw * .03f))
                    drawRect(card, Offset(fl, ft + fh * .93f), Size(fw, fh * .07f))
                    drawRoundRect(Color.White, Offset(fl, ft), Size(fw, fh), corner, style = Stroke(2.dp.toPx()))
                }
            }
            Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                PButton(tr("Cancel", "İptal"), icon = Icons.Filled.Close) { onClose() }
                PButton(tr("Reset", "Sıfırla"), icon = Icons.Filled.Refresh) { x = .5f; y = .5f; z = 1f }
                PButton(tr("Done", "Tamam"), icon = Icons.Filled.Check) { onDone(x, y, z); onClose() }
            }
        }
    }
}
