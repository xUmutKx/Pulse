package com.umutk.pulse.widgets.glass

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import com.umutk.pulse.features.BlurBackdrop

/**
 * Liquid glass tile for home-screen widgets: the wallpaper behind the tile, blurred, its picture bent toward the
 * centre along the edges (a lens) and a light rim on the side facing the light. A widget is a bitmap the launcher
 * shows, so the backdrop is built once per wallpaper and size and cached; only the widget's text is drawn per update.
 * The lens is an AGSL RuntimeShader (Android 13+); older phones get the blurred backdrop and the rim only.
 */
object LiquidGlass {
    /** True while a liquid tile is drawn on this thread: the skin's own tile fill is then left out. */
    val active = ThreadLocal.withInitial { false }

    /** The lens: samples the backdrop nearer the centre toward the rim, and lights the rim on the upper-left. */
    private const val LENS = """
        uniform shader content;
        uniform float2 size;
        uniform float band;
        uniform float strength;
        half4 main(float2 p) {
            float2 c = size * 0.5;
            float e = min(min(p.x, p.y), min(size.x - p.x, size.y - p.y));
            float k = 1.0 - smoothstep(0.0, band, e);
            half4 col = content.eval(p - (p - c) * (k * strength));
            float2 v = p - c;
            float2 n = v / max(length(v), 0.001);
            float lit = max(0.0, -(n.x + n.y) * 0.7071);
            half a = half(k * (0.35 + 0.65 * lit) * 0.5);
            col.rgb = col.rgb * (1.0 - a) + half3(a);
            return col;
        }
    """

    private var key = ""
    private var backdrop: Bitmap? = null
    private var wall: Bitmap? = null
    private var wallId = Int.MIN_VALUE

    /** Draws the liquid tile into [c] at logical size [w] x [h] with corner [radius] (the caller's scale applies). */
    fun draw(ctx: Context, c: Canvas, w: Float, h: Float, radius: Float) {
        val bw = (w * 2).toInt().coerceAtLeast(2); val bh = (h * 2).toInt().coerceAtLeast(2)
        val box = RectF(0f, 0f, w, h)
        c.save(); c.clipPath(Path().apply { addRoundRect(box, radius, radius, Path.Direction.CW) })
        c.drawBitmap(backdrop(ctx, bw, bh), null, box, Paint(Paint.FILTER_BITMAP_FLAG))
        c.restore()
        c.drawRoundRect(RectF(.75f, .75f, w - .75f, h - .75f), radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 1.5f
            shader = LinearGradient(0f, 0f, 0f, h, Color.argb(120, 255, 255, 255), Color.argb(30, 255, 255, 255), Shader.TileMode.CLAMP)
        })
    }

    /** The blurred, lensed wallpaper centre-cropped to [bw] x [bh], rebuilt only when the wallpaper or the size changes. */
    @Synchronized
    private fun backdrop(ctx: Context, bw: Int, bh: Int): Bitmap {
        val src = wallpaper(ctx)
        val k = "$wallId/$bw/$bh/${src != null}"
        backdrop?.let { if (k == key) return it }
        val cut = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        if (src == null) Canvas(cut).drawColor(Color.argb(200, 20, 20, 20)) else {
            val aspect = bw.toFloat() / bh
            var sw = src.width; var sh = (sw / aspect).toInt()
            if (sh > src.height) { sh = src.height; sw = (sh * aspect).toInt() }
            val x = (src.width - sw) / 2; val y = (src.height - sh) / 2
            Canvas(cut).drawBitmap(src, Rect(x, y, x + sw, y + sh), Rect(0, 0, bw, bh), Paint(Paint.FILTER_BITMAP_FLAG))
        }
        val soft = BlurBackdrop.blur(cut, 18f, 0) // tint 0 leaves the picture as it is
        val out = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val band = minOf(bw, bh) * .12f
        if (Build.VERSION.SDK_INT >= 33) {
            val sh = RuntimeShader(LENS)
            sh.setInputShader("content", BitmapShader(soft, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP))
            sh.setFloatUniform("size", bw.toFloat(), bh.toFloat())
            sh.setFloatUniform("band", band)
            sh.setFloatUniform("strength", .35f)
            Canvas(out).drawRect(0f, 0f, bw.toFloat(), bh.toFloat(), Paint().apply { shader = sh })
        } else Canvas(out).drawBitmap(soft, 0f, 0f, null)
        backdrop = out; key = k
        return out
    }

    /** The wallpaper shrunk to 512 px on its long side (the backdrop is blurred anyway); null when the launcher cannot read it. */
    private fun wallpaper(ctx: Context): Bitmap? {
        val wm = WallpaperManager.getInstance(ctx)
        val id = runCatching { wm.getWallpaperId(WallpaperManager.FLAG_SYSTEM) }.getOrDefault(-1)
        if (id == wallId && wall != null) return wall
        val b = runCatching {
            val d = wm.drawable ?: return@runCatching null
            val s = 512f / maxOf(d.intrinsicWidth, d.intrinsicHeight, 1)
            val out = Bitmap.createBitmap((d.intrinsicWidth * s).toInt().coerceAtLeast(1), (d.intrinsicHeight * s).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            d.setBounds(0, 0, out.width, out.height); d.draw(Canvas(out))
            out
        }.getOrNull()
        wall = b; wallId = id
        return b
    }
}
