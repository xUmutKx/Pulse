package com.umutk.pulse

import android.content.Context
import android.graphics.*
import android.graphics.drawable.*
import android.util.TypedValue
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.PathParser

/** Floating monitor look: each theme draws its own box and icons (Fluent paths), built with plain Views because the service runs without the UI. */
object OverlayUi {
    private class S(val bg: Drawable, val text: Int, val icon: Int, val shadow: Boolean = false)
    private val paths = HashMap<String, List<Path>>()
    private val bmps = HashMap<String, Bitmap>()
    private val FLUENT = mapOf("cpu" to "cpu", "ram" to "memory", "gpu" to "gpu", "temp" to "thermal", "bat" to "battery", "down" to "down", "up" to "up")

    private fun icon(k: String, px: Int, color: Int): Bitmap = bmps.getOrPut("$k$px$color") {
        val ps = paths.getOrPut(k) { (FluentPaths.map[FLUENT[k] ?: "info"] ?: emptyList()).map { PathParser.createPathFromPathData(it) } }
        val b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(b); c.scale(px / 24f, px / 24f)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        ps.forEach { c.drawPath(it, p) }
        b
    }

    private fun box(fill: Int, r: Float, stroke: Int = 0, sw: Int = 0) = GradientDrawable().apply { setColor(fill); cornerRadius = r; if (sw > 0) setStroke(sw, stroke) }

    private fun bevel(d: Float): Drawable {
        fun l(c: Int) = ColorDrawable(c)
        val i = d.toInt().coerceAtLeast(1)
        return LayerDrawable(arrayOf(l(Color.BLACK), l(Color.WHITE), l(0xFF808080.toInt()), l(0xFFC0C0C0.toInt()))).apply {
            setLayerInset(1, 0, 0, i, i); setLayerInset(2, i, i, i, i); setLayerInset(3, i, i, 2 * i, 2 * i)
        }
    }

    private fun style(d: Float): S {
        val t = Look.theme; val a = Look.accent.toArgb()
        return when (t.id) {
            "win11" -> S(box(0xE62B2B2B.toInt(), 14 * d, 0x33FFFFFF, d.toInt()), Color.WHITE, 0xFF60CDFF.toInt())
            "win10" -> S(box(0xF0202020.toInt(), 0f, a, (2 * d).toInt()), Color.WHITE, a)
            "win7" -> S(GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xCC9CC8F0.toInt(), 0xCC3C74B4.toInt())).apply { cornerRadius = 8 * d; setStroke((1.5f * d).toInt(), 0xF0FFFFFF.toInt()) }, Color.WHITE, Color.WHITE, true)
            "xp" -> S(box(0xFFECE9D8.toInt(), 6 * d, 0xFF0054E3.toInt(), (2 * d).toInt()), Color.BLACK, 0xFF0054E3.toInt())
            "win95" -> S(bevel(d), Color.BLACK, 0xFF000080.toInt())
            "material" -> S(box(0xFFF3EDF7.toInt(), Look.cornerDp.value * d, 0xFFCAC4D0.toInt(), d.toInt()), 0xFF1D1B20.toInt(), 0xFF6750A4.toInt())
            "amoled" -> S(box(Color.BLACK, 16 * d, 0x99FFFFFF.toInt(), d.toInt()), Color.WHITE, Color.WHITE)
            else -> S(box(t.panel.toArgb(), t.radius.value * d, t.line.toArgb(), d.toInt()), t.text.toArgb(), a)
        }
    }

    /** Re-reads the saved look and refills [root] with icon + value pairs, three per row. */
    fun fill(root: LinearLayout, items: List<Pair<String, String>>) {
        val ctx = root.context
        Cfg.load(ctx)
        val d = ctx.resources.displayMetrics.density
        val s = style(d)
        s.bg.alpha = (Opt.ovAlpha * 255).toInt()
        root.background = s.bg
        root.orientation = LinearLayout.VERTICAL
        root.setPadding((10 * d).toInt(), (6 * d).toInt(), (10 * d).toInt(), (6 * d).toInt())
        root.removeAllViews()
        val ip = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, Opt.ovSize * 1.3f, ctx.resources.displayMetrics).toInt()
        items.chunked(3).forEach { row ->
            val r = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.forEach { (k, v) ->
                r.addView(ImageView(ctx).apply { setImageBitmap(icon(k, ip, s.icon)) }, LinearLayout.LayoutParams(ip, ip))
                r.addView(TextView(ctx).apply {
                    text = v; textSize = Opt.ovSize; setTextColor(s.text); typeface = Typeface.DEFAULT_BOLD
                    if (s.shadow) setShadowLayer(3f, 0f, 1f, 0xFF000000.toInt())
                    setPadding((4 * d).toInt(), 0, (10 * d).toInt(), 0)
                })
            }
            root.addView(r)
        }
    }
}
