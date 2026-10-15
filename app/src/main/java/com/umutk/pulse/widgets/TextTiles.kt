package com.umutk.pulse.widgets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.graphics.ColorUtils
import com.umutk.pulse.R

/**
 * What a text widget shows: the big value, secondary lines, a progress bar ([frac] 0..1), a sparkline ([series], [series2] fainter),
 * mini bars (per-core) and an optional [tone] that recolours the bar (0 = the design's accent).
 */
class Tile(
    val big: String,
    val sub: List<String> = emptyList(),
    val frac: Float? = null,
    val series: List<Float> = emptyList(),
    val series2: List<Float> = emptyList(),
    val seriesMax: Float = 0f,
    val bars: List<Float> = emptyList(),
    val tone: Int = 0,
    /** With [bars]: how many bars from the left are lit, the rest dimmed (signal bars); -1 = all lit. */
    val lit: Int = -1,
)

/** Draws a whole text widget (background, icon, title, value, lines, bar, graph) into one bitmap, in the style of its design. */
object TextTile {
    fun draw(ctx: Context, s: WidgetStyle, icon: Int, title: String, t: Tile, w: Int, h: Int, id: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val d = ctx.resources.displayMetrics.density
        val W = w.toFloat(); val H = h.toFloat()
        val win11 = s == WidgetStyle.WIN11 || s == WidgetStyle.WIN11_DARK
        val xp = s == WidgetStyle.XP; val w95 = s == WidgetStyle.WIN95
        val strip = xp || w95
        val w10 = s == WidgetStyle.WIN10
        WidgetLook.load(ctx)
        // Windows 10: a flat tile in the accent (its own, the global one, else the classic blue); dark text when the accent is light
        val w10bg = WidgetAccent.own(ctx, id).takeIf { it != 0 } ?: WidgetLook.accentColor.takeIf { it != 0 } ?: 0xFF0078D7.toInt()
        val w10light = w10 && ColorUtils.calculateLuminance(w10bg) > .55
        val box = RectF(0f, 0f, W, H)
        if (win11) Win11Material.draw(ctx, c, W, H, 16f * d, if (s == WidgetStyle.WIN11_DARK) "mica" else WidgetOver.material(ctx, id))
        else {
            // every other look is opaque art: the opacity fades the whole tile with its content
            c.saveLayerAlpha(box, (WidgetOver.opacity(ctx, id, s.design).coerceIn(0f, 1f) * 255).toInt())
            if (w10) {
                c.drawColor(w10bg)
                c.drawRect(0f, 0f, W, 14f * d + 24f * d + 6f * d, Paint().apply { color = 0x24000000 })   // the darker header band
                c.drawRect(0f, 0f, W, H, Paint().apply { style = Paint.Style.STROKE; strokeWidth = 2f * d; color = 0x33FFFFFF })
            } else ctx.getDrawable(s.bg)?.mutate()?.let { it.setBounds(0, 0, w, h); it.draw(c) }
        }
        val dark = { col: Int -> Color.rgb((Color.red(col) * .55f).toInt(), (Color.green(col) * .55f).toInt(), (Color.blue(col) * .55f).toInt()) }
        val acc = when {
            win11 -> Win11Material.accent(ctx)
            xp -> 0xFF2DB52D.toInt()
            w95 -> 0xFF000080.toInt()
            w10 -> if (w10light) Color.BLACK else Color.WHITE
            s == WidgetStyle.WIN7 -> 0xFF5CC8FF.toInt()
            s == WidgetStyle.MATERIAL -> dark(WidgetAccent.of(ctx, id))
            else -> WidgetAccent.of(ctx, id)
        }
        val light = s == WidgetStyle.MATERIAL || xp || w95 || w10light
        val fg = if (s == WidgetStyle.MATERIAL) 0xFF1D1B20.toInt() else if (light) Color.BLACK else Color.WHITE
        val fg2 = when { xp -> 0xFF202020.toInt(); w95 -> Color.BLACK; s == WidgetStyle.MATERIAL -> 0xFF3B383E.toInt(); w10 -> if (w10light) 0xFF202020.toInt() else 0xFFF0F7FD.toInt(); else -> 0xFFEAEAEA.toInt() }
        val fill = if (t.tone != 0) t.tone else acc
        fun text(p: Paint, str: String, x: Float, y: Float, maxW: Float, minSize: Float = 9f * d) {
            while (p.measureText(str) > maxW && p.textSize > minSize) p.textSize -= d * .5f
            val out = if (p.measureText(str) > maxW) android.text.TextUtils.ellipsize(str, android.text.TextPaint(p), maxW, android.text.TextUtils.TruncateAt.END).toString() else str
            c.drawText(out, x, y, p)
        }
        fun paint(color: Int, size: Float, bold: Boolean) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color; textSize = size; typeface = if (w10 && size > 20f * d) Typeface.create("sans-serif-light", Typeface.NORMAL) else if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            if (!light) setShadowLayer(d * 1.5f, 0f, d * .8f, 0x99000000.toInt())
        }
        val pad = (if (w95) 7f else if (xp) 9f else 14f) * d
        val hh = (if (w95) 20f else 24f) * d
        val bodyTop: Float; val headMid: Float
        if (xp) {
            val r = RectF(2f * d, 2f * d, W - 2f * d, 2f * d + hh)
            c.drawRoundRect(r, 4f * d, 4f * d, Paint().apply { shader = LinearGradient(0f, r.top, 0f, r.bottom, 0xFF3A93FF.toInt(), 0xFF0058EE.toInt(), Shader.TileMode.CLAMP) })
            bodyTop = r.bottom + 8f * d; headMid = r.centerY()
        } else if (w95) {
            val r = RectF(4f * d, 4f * d, W - 4f * d, 4f * d + hh)
            c.drawRect(r, Paint().apply { shader = LinearGradient(r.left, 0f, r.right, 0f, 0xFF000080.toInt(), 0xFF1084D0.toInt(), Shader.TileMode.CLAMP) })
            bodyTop = r.bottom + 7f * d; headMid = r.centerY()
        } else { bodyTop = pad + hh + 4f * d; headMid = pad + hh / 2f }
        // header: icon and title
        val isz = if (strip) hh * .66f else 20f * d
        val ix = if (strip) pad else pad
        if (icon != 0) ctx.getDrawable(icon)?.mutate()?.let {
            it.setTint(if (strip) Color.WHITE else if (win11 || s == WidgetStyle.MATERIAL) acc else fg)
            it.setBounds(ix.toInt(), (headMid - isz / 2).toInt(), (ix + isz).toInt(), (headMid + isz / 2).toInt()); it.draw(c)
        }
        val tx = ix + isz + 7f * d
        paint(if (strip) Color.WHITE else fg2, (if (strip) 12f else 13f) * d, true).let { text(it, title, tx, headMid + it.textSize * .35f, W - pad - tx) }
        val L = pad; val R = W - pad; val bottom = H - pad
        val bh = (if (xp) 12f else if (w95) 14f else if (w10) 5f else 9f) * d
        val hasBar = t.frac != null
        val barTop = if (hasBar) bottom - bh else bottom
        val availBottom = barTop - if (hasBar) 7f * d else 0f
        val bodyH = (availBottom - bodyTop).coerceAtLeast(10f * d)
        val hasGraph = t.series.size >= 2 || t.bars.isNotEmpty()
        val wide = W > H * 1.45f
        val leftR = if (wide && hasGraph) L + (R - L) * .55f else R
        // the big value (a sunken field on Windows 95)
        val vs = minOf(bodyH * (if (t.sub.isEmpty()) .8f else .55f), 42f * d)
        val vp = paint(fg, vs, true)
        var base = bodyTop + vs * .92f
        if (w95) {
            val fr = RectF(L, bodyTop - 2f * d, leftR, bodyTop + vs * 1.2f)
            ctx.getDrawable(com.umutk.pulse.R.drawable.widget_win95_field)?.let { it.setBounds(fr.left.toInt(), fr.top.toInt(), fr.right.toInt(), fr.bottom.toInt()); it.draw(c) }
            base = fr.bottom - vs * .22f
            text(vp, t.big, L + 5f * d, base, leftR - L - 10f * d)
            base = fr.bottom
        } else text(vp, t.big, L, base, leftR - L)
        // secondary lines
        var y = base
        for (line in t.sub) {
            if (y + 15f * d > availBottom) break
            y += 15f * d
            text(paint(fg2, 12f * d, false), line, L, y, leftR - L)
        }
        // sparkline or mini bars
        val g = if (!hasGraph) null else if (wide) RectF(leftR + 10f * d, bodyTop, R, availBottom) else RectF(L, y + 7f * d, R, availBottom).takeIf { it.height() >= 18f * d }
        if (g != null) {
            val track = when { xp -> Color.WHITE; w95 -> Color.BLACK; else -> ColorUtils.setAlphaComponent(fg, 34) }
            c.drawRoundRect(g, 4f * d, 4f * d, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = track })
            if (xp) c.drawRoundRect(g, 4f * d, 4f * d, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = d; color = 0xFF7F9DB9.toInt() })
            val line = if (w95) 0xFF00FF00.toInt() else fill
            if (t.bars.isNotEmpty()) {
                val n = t.bars.size; val gap = 3f * d; val bw = (g.width() - gap * (n + 1)) / n
                t.bars.forEachIndexed { i, v ->
                    val x = g.left + gap + i * (bw + gap)
                    val top = g.bottom - gap - (g.height() - 2 * gap) * v.coerceIn(0f, 1f).coerceAtLeast(.04f)
                    c.drawRoundRect(RectF(x, top, x + bw, g.bottom - gap), 2f * d, 2f * d, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (t.lit < 0 || i < t.lit) line else ColorUtils.setAlphaComponent(line, 70) })
                }
            } else {
                val mx = if (t.seriesMax > 0f) t.seriesMax else maxOf(1f, t.series.maxOrNull() ?: 1f, t.series2.maxOrNull() ?: 1f)
                fun curve(v: List<Float>, col: Int, fillArea: Boolean) {
                    if (v.size < 2) return
                    val p = Path()
                    v.forEachIndexed { i, y0 ->
                        val x = g.left + g.width() * i / (v.size - 1)
                        val yy = g.bottom - 3f * d - (g.height() - 6f * d) * (y0 / mx).coerceIn(0f, 1f)
                        if (i == 0) p.moveTo(x, yy) else p.lineTo(x, yy)
                    }
                    if (fillArea) {
                        val a = Path(p).apply { lineTo(g.right, g.bottom); lineTo(g.left, g.bottom); close() }
                        c.drawPath(a, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, g.top, 0f, g.bottom, ColorUtils.setAlphaComponent(col, 110), ColorUtils.setAlphaComponent(col, 10), Shader.TileMode.CLAMP) })
                    }
                    c.drawPath(p, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f * d; strokeJoin = Paint.Join.ROUND; color = col })
                }
                c.save(); c.clipRect(g)
                curve(t.series2, if (light && !xp) 0xFF606060.toInt() else ColorUtils.setAlphaComponent(fg2, 190), false)
                curve(t.series, line, true)
                c.restore()
            }
        }
        // progress bar
        t.frac?.let { f ->
            val r = RectF(L, barTop, R, bottom); val v = f.coerceIn(0f, 1f)
            if (xp || w95) {
                val boxP = if (xp) Color.WHITE else Color.WHITE
                c.drawRect(r, Paint().apply { color = boxP })
                c.drawRect(r, Paint().apply { style = Paint.Style.STROKE; strokeWidth = d; color = if (xp) 0xFF7F9DB9.toInt() else 0xFF808080.toInt() })
                val bw = (if (xp) 7f else 9f) * d; val gap = 2f * d
                val inner = RectF(r.left + 2f * d, r.top + 2f * d, r.right - 2f * d, r.bottom - 2f * d)
                val n = ((inner.width() * v) / (bw + gap)).toInt()
                for (i in 0 until n) c.drawRect(inner.left + i * (bw + gap), inner.top, inner.left + i * (bw + gap) + bw, inner.bottom, Paint().apply { color = fill })
            } else {
                val rr = if (w10) 0f else bh / 2
                c.drawRoundRect(r, rr, rr, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ColorUtils.setAlphaComponent(fg, if (light) 40 else 70) })
                if (v > 0f) c.drawRoundRect(RectF(r.left, r.top, maxOf(r.left + bh, r.left + r.width() * v), r.bottom), rr, rr, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill })
            }
        }
        if (!win11) c.restore()
        return bmp
    }
}
