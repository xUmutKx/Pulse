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

/**
 * Draws a whole text widget into one bitmap at its real cell size, in its design's own look.
 * Layout: an icon + title header (XP and 95 in their title bar, Windows 10 with the name at the bottom like a live tile), the big value,
 * one or two secondary lines, then a bar; wide cells put the sparkline / bars, or a ring for a plain percentage, in a right-hand panel.
 */
object TextTile {
    fun draw(ctx: Context, s: WidgetStyle, icon: Int, title: String, t: Tile, w: Int, h: Int, id: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val d = ctx.resources.displayMetrics.density
        val W = w.toFloat(); val H = h.toFloat()
        val win11 = s == WidgetStyle.WIN11 || s == WidgetStyle.WIN11_DARK
        val xp = s == WidgetStyle.XP; val w95 = s == WidgetStyle.WIN95
        val strip = xp || w95
        val w10 = s == WidgetStyle.WIN10; val w7 = s == WidgetStyle.WIN7
        val mat = s == WidgetStyle.MATERIAL; val amo = s == WidgetStyle.AMOLED
        WidgetLook.load(ctx)
        val app = WidgetAccent.of(ctx, id)
        // Windows 10: a flat tile in the accent (its own, the global one, else the classic blue); dark text when the accent is light
        val w10bg = WidgetAccent.own(ctx, id).takeIf { it != 0 } ?: WidgetLook.accentColor.takeIf { it != 0 } ?: 0xFF0078D7.toInt()
        val w10light = w10 && ColorUtils.calculateLuminance(w10bg) > .55
        // Material You tones mixed from the accent: a pale container, a mid chip and a deep on-colour
        val matCard = ColorUtils.blendARGB(app, Color.WHITE, .84f)
        val matChip = ColorUtils.blendARGB(app, Color.WHITE, .5f)
        val matOn = ColorUtils.blendARGB(app, Color.BLACK, .68f)
        val box = RectF(0f, 0f, W, H)

        // ---- background
        if (win11) Win11Material.draw(ctx, c, W, H, 12f * d, if (s == WidgetStyle.WIN11_DARK) "mica" else WidgetOver.material(ctx, id))
        else {
            // every other look is opaque art: the opacity fades the whole tile with its content
            c.saveLayerAlpha(box, (WidgetOver.opacity(ctx, id, s.design).coerceIn(0f, 1f) * 255).toInt())
            when {
                w10 -> c.drawColor(w10bg)
                mat -> c.drawRoundRect(box, 24f * d, 24f * d, fillP(matCard))
                amo -> {
                    c.drawRoundRect(box, 24f * d, 24f * d, fillP(Color.BLACK))
                    c.drawRoundRect(RectF(d * .5f, d * .5f, W - d * .5f, H - d * .5f), 24f * d, 24f * d, fillP(ColorUtils.setAlphaComponent(app, 70)).apply { style = Paint.Style.STROKE; strokeWidth = d })
                }
                else -> ctx.getDrawable(s.bg)?.mutate()?.let { it.setBounds(0, 0, w, h); it.draw(c) }
            }
        }

        // ---- colours
        val fg = when { mat -> matOn; xp || w95 -> Color.BLACK; w10light -> Color.BLACK; else -> Color.WHITE }
        val fg2 = when {
            mat -> ColorUtils.blendARGB(matOn, matCard, .3f); xp -> 0xFF3C3C3C.toInt(); w95 -> 0xFF202020.toInt()
            w10 -> ColorUtils.setAlphaComponent(fg, 210); win11 -> 0xFFC8C8C8.toInt(); w7 -> 0xFFCFE8FF.toInt(); else -> 0xFF9E9E9E.toInt()
        }
        val acc = when {
            win11 -> Win11Material.accent(ctx); xp -> 0xFF2DB52D.toInt(); w95 -> 0xFF000080.toInt(); w10 -> fg
            w7 -> 0xFF5CC8FF.toInt(); mat -> ColorUtils.blendARGB(app, Color.BLACK, .25f); else -> Skins.readable(app)
        }
        val fill = if (t.tone != 0) t.tone else acc
        val track = when { mat -> ColorUtils.setAlphaComponent(matChip, 150); w95 || xp -> Color.WHITE; w10 -> ColorUtils.setAlphaComponent(fg, 60); else -> Color.argb(56, 255, 255, 255) }
        val shadow = w7 || win11
        val valueFace = when {
            w10 || amo -> Typeface.create("sans-serif-light", Typeface.NORMAL)
            win11 || mat -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
            else -> Typeface.DEFAULT_BOLD
        }

        fun paint(color: Int, size: Float, face: Typeface) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color; textSize = size; typeface = face
            if (shadow) setShadowLayer(d * 1.2f, 0f, d * .6f, 0x80000000.toInt())
        }
        // shrinks to fit [maxW], then ellipsizes below [minSize]
        fun text(p: Paint, str: String, x: Float, y: Float, maxW: Float, minSize: Float = 9f * d) {
            while (p.measureText(str) > maxW && p.textSize > minSize) p.textSize -= d * .5f
            val out = if (p.measureText(str) > maxW) android.text.TextUtils.ellipsize(str, android.text.TextPaint(p), maxW, android.text.TextUtils.TruncateAt.END).toString() else str
            c.drawText(out, x, y, p)
        }
        fun drawIcon(r: RectF, tint: Int) { if (icon != 0) ctx.getDrawable(icon)?.mutate()?.let { it.setTint(tint); it.setBounds(r.left.toInt(), r.top.toInt(), r.right.toInt(), r.bottom.toInt()); it.draw(c) } }

        // ---- header
        val compact = W < 190f * d || W < H * 1.5f
        val pad = (if (w95) 6f else if (xp) 8f else if (compact) 12f else 14f) * d
        val L = pad; val R = W - pad
        val top: Float; var bottom = H - pad
        val titleFace = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        if (strip) {
            val hh = (if (w95) 18f else 22f) * d
            val r = if (xp) RectF(2f * d, 2f * d, W - 2f * d, 2f * d + hh) else RectF(3f * d, 3f * d, W - 3f * d, 3f * d + hh)
            if (xp) c.drawRoundRect(r, 4f * d, 4f * d, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, r.top, 0f, r.bottom, 0xFF3A93FF.toInt(), 0xFF0058EE.toInt(), Shader.TileMode.CLAMP) })
            else c.drawRect(r, Paint().apply { shader = LinearGradient(r.left, 0f, r.right, 0f, 0xFF000080.toInt(), 0xFF1084D0.toInt(), Shader.TileMode.CLAMP) })
            val isz = hh * .7f
            drawIcon(RectF(r.left + 4f * d, r.centerY() - isz / 2, r.left + 4f * d + isz, r.centerY() + isz / 2), Color.WHITE)
            val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = hh * .56f; typeface = Typeface.DEFAULT_BOLD; if (xp) setShadowLayer(d, d * .7f, d * .7f, 0xFF0A246A.toInt()) }
            text(tp, title, r.left + 8f * d + isz, r.centerY() + tp.textSize * .36f, r.right - r.left - 12f * d - isz)
            top = r.bottom + (if (w95) 6f else 8f) * d
        } else if (w10) {
            // live tile: a small glyph top left, the name at the bottom left
            drawIcon(RectF(L, pad, L + 18f * d, pad + 18f * d), fg)
            val tp = paint(fg, 12f * d, Typeface.DEFAULT)
            text(tp, title, L, H - pad, R - L)
            top = pad + 26f * d; bottom = H - pad - 20f * d
        } else {
            val hs = (if (compact) 22f else 24f) * d
            if (mat) {
                c.drawCircle(L + hs / 2, pad + hs / 2, hs / 2, fillP(matChip))
                drawIcon(RectF(L + hs * .2f, pad + hs * .2f, L + hs * .8f, pad + hs * .8f), matOn)
            } else drawIcon(RectF(L, pad + hs * .1f, L + hs * .8f, pad + hs * .9f), acc)
            val tx = L + hs + 6f * d
            val tp = paint(fg2, 12.5f * d, titleFace)
            text(tp, title, tx, pad + hs / 2 + tp.textSize * .36f, R - tx)
            top = pad + hs + (if (compact) 4f else 6f) * d
        }

        // ---- body: decide the right-hand panel and the bar
        val hasGraph = t.series.size >= 2 || t.bars.isNotEmpty()
        val ringOk = !strip && t.frac != null && !hasGraph
        val ring = ringOk && !compact
        // square cells with room: the ring sits at the bottom under the text instead of a bar
        val sqRing = ringOk && compact && bottom - top > 120f * d
        val ringBox: RectF? = when {
            ring -> RectF(L + (R - L) * .6f, top, R, bottom)
            sqRing -> minOf(R - L, (bottom - top) * .5f).let { z -> RectF((L + R) / 2 - z / 2, bottom - z, (L + R) / 2 + z / 2, bottom) }
            else -> null
        }
        val panel = if (!compact && (hasGraph || ring)) RectF(L + (R - L) * .6f, top, R, bottom) else null
        val leftR = if (panel != null) panel.left - 10f * d else R
        val bh = when { xp -> 12f; w95 -> 14f; mat -> 8f; w7 -> 6f; amo -> 3f; else -> 4f } * d
        val showBar = t.frac != null && !ringOk
        val barR = if (showBar) RectF(L, bottom - bh, leftR, bottom) else null
        // square cells keep a graph above the bar (or at the bottom), as tall as the cell allows
        val gBottom = (barR?.top?.minus(6f * d)) ?: bottom
        val miniGraph = if (compact && hasGraph) RectF(L, gBottom - minOf(44f * d, (bottom - top) * .4f), R, gBottom) else null
        val textBottom = (ringBox?.takeIf { sqRing }?.top ?: miniGraph?.top ?: barR?.top ?: bottom) - 6f * d
        val avail = (textBottom - top).coerceAtLeast(12f * d)

        // ---- big value and secondary lines
        val maxSub = if (compact) (if (avail > 80f * d) 3 else 1) else 2
        val lineH = 15f * d
        val subs = t.sub.take(maxSub).take(((avail * .45f) / lineH).toInt().coerceAtLeast(0))
        val vs = minOf((avail - subs.size * lineH) * .9f, if (compact) 34f * d else 42f * d).coerceAtLeast(12f * d)
        val vp = paint(fg, vs, valueFace)
        var y: Float
        if (w95) {
            val fr = RectF(L, top, leftR, top + vs * 1.15f)
            ctx.getDrawable(com.umutk.pulse.R.drawable.widget_win95_field)?.let { it.setBounds(fr.left.toInt(), fr.top.toInt(), fr.right.toInt(), fr.bottom.toInt()); it.draw(c) }
            vp.textSize = vs * .8f
            text(vp, t.big, L + 5f * d, fr.bottom - vs * .28f, leftR - L - 10f * d)
            y = fr.bottom
        } else {
            y = top + vs * .82f
            text(vp, t.big, L - (if (w10 || amo) d else 0f), y, leftR - L)
            y += vs * .12f
        }
        val sp = paint(fg2, 12f * d, Typeface.DEFAULT)
        subs.forEach { line -> y += lineH; text(Paint(sp), line, L, y - 3f * d, leftR - L) }

        // ---- graph (sparkline or bars) or ring
        val g = panel?.takeIf { hasGraph } ?: miniGraph
        if (g != null) graph(c, d, t, g, strip, fill, fg2, track)
        if (ringBox != null) {
            val f = t.frac!!.coerceIn(0f, 1f)
            val sz = minOf(ringBox.width(), ringBox.height())
            val sw = sz * .1f
            val rr = RectF(ringBox.centerX() - sz / 2 + sw / 2, ringBox.centerY() - sz / 2 + sw / 2, ringBox.centerX() + sz / 2 - sw / 2, ringBox.centerY() + sz / 2 - sw / 2)
            val cap = if (w10) Paint.Cap.BUTT else Paint.Cap.ROUND
            c.drawArc(rr, 0f, 360f, false, fillP(track).apply { style = Paint.Style.STROKE; strokeWidth = sw })
            if (f > 0f) c.drawArc(rr, -90f, 360f * f, false, fillP(fill).apply { style = Paint.Style.STROKE; strokeWidth = sw; strokeCap = cap })
            val pp = paint(fg, sz * .24f, valueFace).apply { textAlign = Paint.Align.CENTER }
            c.drawText("${(f * 100).toInt()}%", rr.centerX(), rr.centerY() + pp.textSize * .35f, pp)
        }

        // ---- bar
        if (barR != null) bar(c, d, barR, t.frac!!.coerceIn(0f, 1f), s, fill, track)
        if (!win11) c.restore()
        return bmp
    }

    private fun fillP(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

    /** A progress bar in the design's own way: XP's green and 95's navy blocks in a sunken box, square on Windows 10, round elsewhere. */
    private fun bar(c: Canvas, d: Float, r: RectF, v: Float, s: WidgetStyle, fill: Int, track: Int) {
        when (s) {
            WidgetStyle.XP, WidgetStyle.WIN95 -> {
                val xp = s == WidgetStyle.XP
                if (xp) {
                    c.drawRoundRect(r, 3f * d, 3f * d, fillP(Color.WHITE))
                    c.drawRoundRect(r, 3f * d, 3f * d, fillP(0xFF7F9DB9.toInt()).apply { style = Paint.Style.STROKE; strokeWidth = d })
                } else {
                    // sunken: grey on the top and left edges
                    c.drawRect(r, Paint().apply { color = Color.WHITE })
                    val e = Paint().apply { color = 0xFF808080.toInt(); strokeWidth = d }
                    c.drawLine(r.left, r.top, r.right, r.top, e); c.drawLine(r.left, r.top, r.left, r.bottom, e)
                }
                val bw = (if (xp) 7f else 9f) * d; val gap = 2f * d
                val inner = RectF(r.left + 2f * d, r.top + 2f * d, r.right - 2f * d, r.bottom - 2f * d)
                val n = ((inner.width() * v) / (bw + gap)).toInt()
                val p = Paint().apply {
                    color = fill
                    if (xp) shader = LinearGradient(0f, inner.top, 0f, inner.bottom, intArrayOf(ColorUtils.blendARGB(fill, Color.WHITE, .55f), fill, ColorUtils.blendARGB(fill, Color.BLACK, .1f)), floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
                }
                for (i in 0 until n) c.drawRect(inner.left + i * (bw + gap), inner.top, inner.left + i * (bw + gap) + bw, inner.bottom, p)
            }
            WidgetStyle.WIN7 -> {
                // the glossy Aero meter: a dark well, the fill with a light upper half
                val rr = r.height() / 2
                c.drawRoundRect(r, rr, rr, fillP(0x66000000))
                if (v > 0f) {
                    val f = RectF(r.left, r.top, maxOf(r.left + r.height(), r.left + r.width() * v), r.bottom)
                    c.drawRoundRect(f, rr, rr, fillP(fill).apply { shader = LinearGradient(0f, f.top, 0f, f.bottom, ColorUtils.blendARGB(fill, Color.WHITE, .5f), fill, Shader.TileMode.CLAMP) })
                }
                c.drawRoundRect(r, rr, rr, fillP(0x55FFFFFF).apply { style = Paint.Style.STROKE; strokeWidth = d * .8f })
            }
            else -> {
                val rr = if (s == WidgetStyle.WIN10) 0f else r.height() / 2
                c.drawRoundRect(r, rr, rr, fillP(track))
                if (v > 0f) c.drawRoundRect(RectF(r.left, r.top, maxOf(r.left + r.height(), r.left + r.width() * v), r.bottom), rr, rr, fillP(fill))
            }
        }
    }

    /** The sparkline ([Tile.series], [Tile.series2] fainter) or the bars ([Tile.bars]); XP and 95 draw it as Task Manager's green-on-black graph. */
    private fun graph(c: Canvas, d: Float, t: Tile, g: RectF, strip: Boolean, fill: Int, fg2: Int, track: Int) {
        val line = if (strip) 0xFF00FF00.toInt() else fill
        if (strip) {
            c.drawRect(g, Paint().apply { color = Color.BLACK })
            val grid = Paint().apply { color = 0xFF008040.toInt(); strokeWidth = 1f }
            val step = 10f * d
            var x = g.left; while (x < g.right) { c.drawLine(x, g.top, x, g.bottom, grid); x += step }
            var yy = g.top; while (yy < g.bottom) { c.drawLine(g.left, yy, g.right, yy, grid); yy += step }
        }
        if (t.bars.isNotEmpty()) {
            val n = t.bars.size; val gap = (if (n > 6) 2f else 4f) * d
            val bw = (g.width() - gap * (n - 1)) / n
            val signal = t.lit >= 0
            t.bars.forEachIndexed { i, v ->
                val x = g.left + i * (bw + gap)
                val r = minOf(bw / 2, 3f * d)
                val full = RectF(x, if (signal) g.bottom - g.height() * v.coerceIn(0f, 1f) else g.top, x + bw, g.bottom)
                if (!strip) c.drawRoundRect(full, r, r, fillP(track))
                val lit = !signal || i < t.lit
                if (signal) { if (lit) c.drawRoundRect(full, r, r, fillP(line)) }
                else {
                    val top = g.bottom - g.height() * v.coerceIn(0f, 1f).coerceAtLeast(.04f)
                    c.drawRoundRect(RectF(x, top, x + bw, g.bottom), r, r, fillP(line))
                }
            }
            return
        }
        val mx = if (t.seriesMax > 0f) t.seriesMax else maxOf(1f, t.series.maxOrNull() ?: 1f, t.series2.maxOrNull() ?: 1f)
        fun curve(v: List<Float>, col: Int, area: Boolean) {
            if (v.size < 2) return
            val p = Path()
            v.forEachIndexed { i, y0 ->
                val x = g.left + g.width() * i / (v.size - 1)
                val yy = g.bottom - 2f * d - (g.height() - 4f * d) * (y0 / mx).coerceIn(0f, 1f)
                if (i == 0) p.moveTo(x, yy) else p.lineTo(x, yy)
            }
            if (area) {
                val a = Path(p).apply { lineTo(g.right, g.bottom); lineTo(g.left, g.bottom); close() }
                c.drawPath(a, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, g.top, 0f, g.bottom, ColorUtils.setAlphaComponent(col, if (strip) 70 else 120), ColorUtils.setAlphaComponent(col, 0), Shader.TileMode.CLAMP) })
            }
            c.drawPath(p, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = (if (strip) 1.5f else 2f) * d; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND; color = col })
        }
        c.save(); c.clipRect(g)
        if (!strip) c.drawLine(g.left, g.bottom - d / 2, g.right, g.bottom - d / 2, fillP(track).apply { strokeWidth = d })
        curve(t.series2, if (strip) 0xFFFFFF00.toInt() else ColorUtils.setAlphaComponent(fg2, 170), false)
        curve(t.series, line, true)
        c.restore()
    }
}
