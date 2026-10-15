package com.umutk.pulse.widgets

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import kotlin.random.Random

/**
 * Windows 11 dark Mica / Acrylic for home-screen widgets, after the WinUI dark recipes:
 * acrylic = blurred backdrop + #2C2C2C tint + 2% grain, mica = heavier blur + #202020 tint, no grain.
 * A launcher never hands a widget what is behind it, so the backdrop is the phone wallpaper, blurred by downscaling,
 * and the whole tile is drawn semi-transparent so the real wallpaper still shows through it.
 */
object Win11Material {
    private var tiny: Bitmap? = null
    private var tinyId = Int.MIN_VALUE
    private val noise: Bitmap by lazy {
        val r = Random(11)
        Bitmap.createBitmap(IntArray(64 * 64) { val g = r.nextInt(256); Color.argb(14, g, g, g) }, 64, 64, Bitmap.Config.ARGB_8888)
    }

    /** The appWidgetId being drawn (0 = the global look); set by [Skins.draw] and the text widgets around their draw. */
    val forId = ThreadLocal.withInitial { 0 }

    /** The accent for text, gauges and icons: the widget's own pick, else the widget/app accent as [WidgetAccent] resolves it (theme swatch, Material You), dark ones lightened ~45% toward white as Windows does. */
    fun accent(ctx: Context): Int {
        val id = forId.get()
        // WidgetAccent knows the theme's swatch list and the wallpaper accent; reading WinAccents[accent2] here gave the wrong colour on non-Windows themes
        val base = if (id > 0) WidgetAccent.of(ctx, id) else WidgetAccent.app(ctx)
        if (androidx.core.graphics.ColorUtils.calculateLuminance(base) > .4) return base or 0xFF000000.toInt()
        fun up(c: Int) = c + ((255 - c) * .45f).toInt()
        return Color.rgb(up(Color.red(base)), up(Color.green(base)), up(Color.blue(base)))
    }

    /** A w x h tile bitmap, for the text widgets' background ImageView. */
    fun tile(ctx: Context, w: Int, h: Int, radius: Float, material: String): Bitmap =
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { draw(ctx, Canvas(it), w.toFloat(), h.toFloat(), radius, material) }

    /** Paints the tile into [c]; the caller draws its content on top at full opacity. */
    fun draw(ctx: Context, c: Canvas, w: Float, h: Float, radius: Float, material: String? = null) {
        WidgetLook.load(ctx)
        val id = forId.get()
        val mat = material ?: WidgetOver.material(ctx, id)
        val box = RectF(0f, 0f, w, h)
        val alpha = (WidgetOver.opacity(ctx, id).coerceIn(0f, 1f) * 255).toInt()
        val clip = Path().apply { addRoundRect(box, radius, radius, Path.Direction.CW) }
        c.save(); c.clipPath(clip)
        c.saveLayerAlpha(box, alpha)
        if (mat == "blur") { // the lock screen's blurred wallpaper (BlurBackdrop), centre-cropped to the tile
            val tintA = (WidgetOver.tint(ctx, id).coerceIn(0f, 1f) * 255).toInt() shl 24
            val b = blurSource(ctx, WidgetOver.blur(ctx, id).coerceIn(0f, 1f) * 40f, tintA)
            if (b == null) { // "none": only the dark tint, no picture
                c.drawRect(box, Paint().apply { color = Color.argb(205 + (tintA ushr 24) / 5, 20, 20, 20) })
                c.restore(); c.restore()
                c.drawRoundRect(RectF(.75f, .75f, w - .75f, h - .75f), radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f; color = Color.argb(40, 255, 255, 255) })
                return
            }
            val s = maxOf(w / b.width, h / b.height); val sw = (w / s).toInt().coerceAtMost(b.width); val sh = (h / s).toInt().coerceAtMost(b.height)
            val x = (b.width - sw) / 2; val y = (b.height - sh) / 2
            c.drawBitmap(b, Rect(x, y, x + sw, y + sh), box, Paint(Paint.FILTER_BITMAP_FLAG))
            c.restore(); c.restore()
            c.drawRoundRect(RectF(.75f, .75f, w - .75f, h - .75f), radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f; color = Color.argb(40, 255, 255, 255) })
            return
        }
        val wall = if (mat == "solid") null else blurred(ctx, w / h)
        if (wall != null) {
            // saturation up a little like the Windows blur, then the dark luminosity/tint layer over it
            val sat = ColorMatrix().apply { setSaturation(1.25f) }
            c.drawBitmap(wall, null, box, Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(sat) })
            // the Tint slider sets how dark the wash is (default .25 = the old 235), so the wallpaper can really show through
            val a = (150 + WidgetOver.tint(ctx, id).coerceIn(0f, 1f) * 340).toInt().coerceAtMost(255)
            c.drawColor(when (mat) { "mica" -> Color.argb(a, 10, 10, 10); "acrylic" -> Color.argb(a - 12, 12, 12, 12); else -> Color.argb(a, 6, 6, 6) })
        } else c.drawColor(when (mat) { "mica" -> Color.rgb(10, 10, 10); "acrylic" -> Color.rgb(12, 12, 12); else -> Color.rgb(6, 6, 6) })
        if (mat == "acrylic") c.drawRect(box, Paint().apply { shader = BitmapShader(noise, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT) })
        c.drawRect(box, Paint().apply { shader = LinearGradient(0f, 0f, 0f, h * .45f, Color.argb(7, 255, 255, 255), 0, Shader.TileMode.CLAMP) })
        c.restore()
        c.restore()
        // 1px light edge, brighter on top like the Windows flyout border
        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 1.5f
            shader = LinearGradient(0f, 0f, 0f, h, Color.argb(70, 255, 255, 255), Color.argb(22, 255, 255, 255), Shader.TileMode.CLAMP)
        }
        c.drawRoundRect(RectF(.75f, .75f, w - .75f, h - .75f), radius, radius, edge)
    }

    private var bgKey = ""
    private var bgBmp: Bitmap? = null
    /** The picture behind the blur material, blurred and tinted: the stock wallpaper, the phone's, an own picture, or null for none. */
    @Synchronized
    private fun blurSource(ctx: Context, radiusDp: Float, tint: Int): Bitmap? {
        val mode = WidgetLook.blurBg
        val file = java.io.File(ctx.filesDir, "widget_blur_bg.jpg")
        if (mode == "none") return null
        if (mode == "stock" || (mode == "file" && !file.exists())) return com.umutk.pulse.features.BlurBackdrop.blurredWallpaper(ctx, radiusDp, tint)
        val k = "$mode/${radiusDp.toInt()}/$tint/${if (mode == "file") file.lastModified() else 0}"
        bgBmp?.let { if (k == bgKey) return it }
        val src: Bitmap = (if (mode == "file") {
            val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(file.path, o)
            o.inSampleSize = Integer.highestOneBit((o.outWidth / 540).coerceAtLeast(1)); o.inJustDecodeBounds = false
            android.graphics.BitmapFactory.decodeFile(file.path, o)
        } else wallpaper(ctx)) ?: return com.umutk.pulse.features.BlurBackdrop.blurredWallpaper(ctx, radiusDp, tint)
        return com.umutk.pulse.features.BlurBackdrop.blur(src, radiusDp, tint).also { bgBmp = it; bgKey = k }
    }

    /** The wallpaper centre-cropped to [aspect] and shrunk by WidgetLook.blur; scaling it back up is the blur. */
    private fun blurred(ctx: Context, aspect: Float): Bitmap? {
        val src = wallpaper(ctx) ?: return null
        var sw = src.width; var sh = (sw / aspect).toInt()
        if (sh > src.height) { sh = src.height; sw = (sh * aspect).toInt() }
        val n = (4 + (1f - WidgetOver.blur(ctx, forId.get()).coerceIn(0f, 1f)) * 92).toInt()   // 4..96 px wide (96 = nearly sharp)
        val out = Bitmap.createBitmap(n, (n / aspect).toInt().coerceAtLeast(2), Bitmap.Config.ARGB_8888)
        val x = (src.width - sw) / 2; val y = (src.height - sh) / 2
        Canvas(out).drawBitmap(src, Rect(x, y, x + sw, y + sh), Rect(0, 0, out.width, out.height), Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }

    /** A 96 px wide copy of the wallpaper, cached by wallpaper id; falls back to the wallpaper colours. */
    private fun wallpaper(ctx: Context): Bitmap? {
        val wm = WallpaperManager.getInstance(ctx)
        val id = try { wm.getWallpaperId(WallpaperManager.FLAG_SYSTEM) } catch (e: Exception) { -1 }
        if (id == tinyId && tiny != null) return tiny
        val bmp = try {
            // shrink in halves so the 96 px copy averages the picture instead of sampling it
            wm.drawable?.let { d ->
                val dw = d.intrinsicWidth.coerceAtLeast(1); val dh = d.intrinsicHeight.coerceAtLeast(1)
                var b = Bitmap.createBitmap(384, (384f * dh / dw).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                Canvas(b).also { d.setBounds(0, 0, b.width, b.height); d.draw(it) }
                while (b.width > 96) b = Bitmap.createScaledBitmap(b, b.width / 2, (b.height / 2).coerceAtLeast(1), true)
                b
            }
        } catch (e: Exception) { null } ?: try {
            wm.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)?.let { wc ->
                val a = wc.primaryColor.toArgb(); val b = (wc.secondaryColor ?: wc.primaryColor).toArgb()
                Bitmap.createBitmap(48, 96, Bitmap.Config.ARGB_8888).also {
                    Canvas(it).drawPaint(Paint().apply { shader = LinearGradient(0f, 0f, 48f, 96f, a, b, Shader.TileMode.CLAMP) })
                }
            }
        } catch (e: Exception) { null }
        tiny = bmp; tinyId = id
        return bmp
    }
}
