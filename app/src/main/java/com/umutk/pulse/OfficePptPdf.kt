package com.umutk.pulse

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import java.io.File
import java.io.OutputStream

/**
 * A presentation (.pptx) written out as a PDF: one page per slide, drawn from the same slide model the Office viewer uses (shapes, pictures, tables and text,
 * as vectors and real text). Animations and notes are not kept.
 */
object PptPdf {
    /** Writes [src] as a PDF into [out] and returns the number of slides. */
    fun write(src: File, out: OutputStream): Int {
        val m = PptModel(src)
        val pageW = 960
        val pageH = (pageW * m.cy / m.cx).toInt().coerceAtLeast(100)
        val k = pageW / m.cx
        val doc = PdfDocument()
        try {
            for (i in m.slides.indices) {
                val r = m.render(i)
                val page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, i + 1).create())
                val c = page.canvas
                c.drawColor(r.bg)
                for (s in r.shapes) runCatching { drawShape(m, c, s, k) }
                doc.finishPage(page)
            }
            doc.writeTo(out)
        } finally { doc.close() }
        return m.slides.size
    }

    private fun drawShape(m: PptModel, c: Canvas, s: PShape, k: Float) {
        val w = (s.w * k).coerceAtLeast(1f); val h = (s.h * k).coerceAtLeast(1f)
        c.save()
        c.translate(s.x * k, s.y * k)
        if (s.rot != 0f) c.rotate(s.rot, w / 2, h / 2)
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = maxOf(.75f, s.lineW * k) }
        if (s.geom == "line" || s.geom.contains("onnector")) {
            line.color = s.line ?: s.fill ?: 0xFF000000.toInt()
            c.drawLine(0f, 0f, w, h, line); c.restore(); return
        }
        if (s.kind == "sp") {
            val p = Path().apply { when (s.geom) {
                "ellipse" -> addOval(RectF(0f, 0f, w, h), Path.Direction.CW)
                "roundRect" -> addRoundRect(RectF(0f, 0f, w, h), minOf(w, h) * .17f, minOf(w, h) * .17f, Path.Direction.CW)
                "triangle" -> { moveTo(w / 2, 0f); lineTo(w, h); lineTo(0f, h); close() }
                "diamond" -> { moveTo(w / 2, 0f); lineTo(w, h / 2); lineTo(w / 2, h); lineTo(0f, h / 2); close() }
                else -> addRect(0f, 0f, w, h, Path.Direction.CW)
            } }
            s.fill?.let { c.drawPath(p, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = it }) }
            s.line?.let { line.color = it; c.drawPath(p, line) }
        }
        when (s.kind) {
            "pic" -> s.media?.let { path ->
                m.bytes(path)?.let { b -> android.graphics.BitmapFactory.decodeByteArray(b, 0, b.size) }?.let { bmp ->
                    c.drawBitmap(bmp, null, RectF(0f, 0f, w, h), Paint(Paint.FILTER_BITMAP_FLAG)); bmp.recycle()
                }
            }
            "tbl" -> {
                var y = 0f
                s.table?.forEachIndexed { ri, row ->
                    var x = 0f
                    val rh = (s.rowH.getOrNull(ri) ?: 0f) * k
                    row.forEachIndexed { ci, cell ->
                        val cw = (s.colW.getOrNull(ci) ?: 0f) * k
                        cell.fill?.let { c.drawRect(x, y, x + cw, y + rh, Paint().apply { color = it }) }
                        c.drawRect(x, y, x + cw, y + rh, Paint().apply { style = Paint.Style.STROKE; strokeWidth = .5f; color = 0xFF999999.toInt() })
                        val pad = 45720f * k
                        c.save(); c.translate(x + pad, y + pad)
                        text(c, cell.paras, k, (cw - 2 * pad).toInt().coerceAtLeast(1), 0)
                        c.restore()
                        x += cw
                    }
                    y += rh
                }
            }
            else -> if (s.paras.any { p -> p.runs.any { it.text.isNotEmpty() } }) {
                val l = s.ins[0] * k; val t = s.ins[1] * k; val r = s.ins[2] * k; val b = s.ins[3] * k
                c.save(); c.clipRect(0f, 0f, w, h); c.translate(l, t)
                text(c, s.paras, k, (w - l - r).toInt().coerceAtLeast(1), s.anchor, (h - t - b).coerceAtLeast(1f))
                c.restore()
            }
        }
        c.restore()
    }

    /** The paragraphs under each other from the top (anchor 0), the middle (1) or the bottom (2) of a box [boxH] tall. */
    private fun text(c: Canvas, paras: List<PPara>, k: Float, width: Int, anchor: Int, boxH: Float = 0f) {
        val layouts = paras.map { p ->
            val sb = SpannableStringBuilder()
            if (p.bullet) sb.append("•  ")
            if (p.runs.isEmpty()) sb.append(" ")
            for (r in p.runs) {
                val a = sb.length; sb.append(r.text); val e = sb.length
                if (e == a) continue
                sb.setSpan(AbsoluteSizeSpan(maxOf(4f, r.sizePt * 12700f * k).toInt(), false), a, e, 0)
                sb.setSpan(ForegroundColorSpan(r.color), a, e, 0)
                if (r.bold || r.italic) sb.setSpan(StyleSpan(if (r.bold && r.italic) android.graphics.Typeface.BOLD_ITALIC else if (r.bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.ITALIC), a, e, 0)
                if (r.under) sb.setSpan(UnderlineSpan(), a, e, 0)
                r.hl?.let { sb.setSpan(BackgroundColorSpan(it), a, e, 0) }
            }
            val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = maxOf(4f, (p.runs.firstOrNull()?.sizePt ?: 18f) * 12700f * k) }
            StaticLayout.Builder.obtain(sb, 0, sb.length, tp, width)
                .setAlignment(when (p.align) { 1 -> Layout.Alignment.ALIGN_CENTER; 2 -> Layout.Alignment.ALIGN_OPPOSITE; else -> Layout.Alignment.ALIGN_NORMAL }).build()
        }
        val total = layouts.sumOf { it.height }.toFloat()
        var y = when (anchor) { 1 -> ((boxH - total) / 2f).coerceAtLeast(0f); 2 -> (boxH - total).coerceAtLeast(0f); else -> 0f }
        for (l in layouts) { c.save(); c.translate(0f, y); l.draw(c); c.restore(); y += l.height }
    }
}
