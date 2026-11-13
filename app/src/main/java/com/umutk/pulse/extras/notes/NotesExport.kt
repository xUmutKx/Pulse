package com.umutk.pulse.extras.notes

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color as AColor
import android.graphics.Paint
import android.graphics.Path
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import java.io.File
import kotlin.math.ceil

/** A note as a PDF (the text, then the pen drawing) or as one picture, shared like Samsung Notes' "Save as". */
object NotesExport {
    private const val PW = 595; private const val PH = 842; private const val M = 40

    /** The body without its markdown marks, with bullets and boxes drawn as signs. */
    fun plain(body: String): String = body.split("\n").joinToString("\n") { l ->
        var s = l
        s = s.replace(Regex("^\\s*(?:[-*] )?\\[( )] "), "☐ ").replace(Regex("^\\s*(?:[-*] )?\\[[xX]] "), "☑ ")
        s = s.replace(Regex("^#{1,2} "), "").replace(Regex("^[-*] "), "•  ").replace(Regex("^> "), "│ ")
        s.replace("**", "").replace("~~", "").replace("==", "").replace("__", "")
    }

    private fun strokePaint(w: Float, col: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; strokeWidth = w; color = col
    }

    private fun drawInk(cv: Canvas, strokes: List<InkStroke>, dx: Float, dy: Float, s: Float) {
        for (st in strokes) {
            if (st.pts.isEmpty()) continue
            val p = strokePaint((st.width * s).coerceAtLeast(.6f), st.color)
            if (st.kind == 1) p.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.MULTIPLY)
            if (st.pts.size == 1) { cv.drawCircle(st.pts[0].x * s + dx, st.pts[0].y * s + dy, p.strokeWidth / 2, p.apply { style = Paint.Style.FILL }); continue }
            val path = Path().apply {
                moveTo(st.pts[0].x * s + dx, st.pts[0].y * s + dy)
                for (i in 1 until st.pts.size) lineTo(st.pts[i].x * s + dx, st.pts[i].y * s + dy)
            }
            cv.drawPath(path, p)
        }
    }

    fun pdf(c: Context, n: Note): File? = runCatching {
        val doc = PdfDocument()
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = AColor.BLACK; textSize = 12f }
        val tt = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = AColor.BLACK; textSize = 22f; isFakeBoldText = true }
        val cw = PW - 2 * M
        val title = StaticLayout.Builder.obtain(NotesStore.titleOf(n), 0, NotesStore.titleOf(n).length, tt, cw).build()
        val body = plain(n.body)
        val bl = StaticLayout.Builder.obtain(body, 0, body.length, tp, cw).setLineSpacing(3f, 1f).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
        // the text is cut at line ends into pages
        var line = 0; var page = 1; var first = true
        do {
            val info = PdfDocument.PageInfo.Builder(PW, PH, page++).create()
            val pg = doc.startPage(info); val cv = pg.canvas
            cv.drawColor(AColor.WHITE)
            var y = M.toFloat()
            if (first) { cv.save(); cv.translate(M.toFloat(), y); title.draw(cv); cv.restore(); y += title.height + 14f; first = false }
            val top = if (line < bl.lineCount) bl.getLineTop(line) else 0
            var end = line
            while (end < bl.lineCount && bl.getLineBottom(end) - top <= PH - M - y) end++
            if (end == line && line < bl.lineCount) end = line + 1
            if (bl.lineCount > 0) {
                cv.save(); cv.translate(M.toFloat(), y - top); cv.clipRect(0f, top.toFloat(), cw.toFloat(), (if (end < bl.lineCount) bl.getLineTop(end) else bl.height).toFloat()); bl.draw(cv); cv.restore()
            }
            line = end
            doc.finishPage(pg)
        } while (line < bl.lineCount)
        // the pen drawing: scaled to the page width, cut into pages
        val strokes = NotesInk.decode(n.ink)
        val b = NotesInk.bounds(strokes)
        if (b != null) {
            val s = (cw / maxOf(b.right, 1f)).coerceAtMost(1f).coerceAtLeast(.2f)
            val pages = ceil(((b.bottom + 24f) * s) / (PH - 2 * M)).toInt().coerceAtLeast(1)
            for (k in 0 until pages) {
                val pg = doc.startPage(PdfDocument.PageInfo.Builder(PW, PH, page++).create()); val cv = pg.canvas
                cv.drawColor(AColor.WHITE); cv.save(); cv.clipRect(M.toFloat(), M.toFloat(), (PW - M).toFloat(), (PH - M).toFloat())
                drawInk(cv, strokes, M.toFloat(), M - k * (PH - 2f * M), s); cv.restore(); doc.finishPage(pg)
            }
        }
        val dir = File(c.cacheDir, "notes_export").apply { mkdirs() }
        val f = File(dir, (NotesStore.titleOf(n).ifBlank { "note" }.replace(Regex("[^\\p{L}\\p{N} _-]"), "_").take(40)) + ".pdf")
        f.outputStream().use { doc.writeTo(it) }; doc.close(); f
    }.getOrNull()

    fun image(c: Context, n: Note): File? = runCatching {
        val strokes = NotesInk.decode(n.ink)
        val b = NotesInk.bounds(strokes)
        val w = 1080; val cw = w - 2 * 60
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = AColor.BLACK; textSize = 38f }
        val tt = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = AColor.BLACK; textSize = 56f; isFakeBoldText = true }
        val title = NotesStore.titleOf(n); val body = plain(n.body)
        val tl = StaticLayout.Builder.obtain(title, 0, title.length, tt, cw).build()
        val bl = StaticLayout.Builder.obtain(body, 0, body.length, tp, cw).setLineSpacing(8f, 1f).build()
        val s = if (b != null) (cw / maxOf(b.right, 1f)).coerceAtMost(1.5f) else 1f
        val inkH = if (b != null) ((b.bottom + 24f) * s).toInt() else 0
        val h = (60 + tl.height + 30 + bl.height + 30 + inkH + 60).coerceAtMost(16000)
        val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp); cv.drawColor(AColor.WHITE)
        cv.save(); cv.translate(60f, 60f); tl.draw(cv); cv.translate(0f, tl.height + 30f); bl.draw(cv); cv.restore()
        if (b != null) drawInk(cv, strokes, 60f, (60 + tl.height + 30 + bl.height + 30).toFloat(), s)
        val dir = File(c.cacheDir, "notes_export").apply { mkdirs() }
        val f = File(dir, "note.png"); f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; bmp.recycle(); f
    }.getOrNull()

    fun share(c: Context, f: File?, mime: String) {
        if (f == null) { noteToast(c, com.umutk.pulse.tr("Could not export", "Dışa aktarılamadı")); return }
        val u = FileProvider.getUriForFile(c, "${c.packageName}.files", f)
        c.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, u).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
