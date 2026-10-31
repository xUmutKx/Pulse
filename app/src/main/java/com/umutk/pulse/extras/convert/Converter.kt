package com.umutk.pulse.extras.convert

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.math.ceil
import kotlin.math.max

/**
 * File conversion on the phone, with no network. Each source format lists the formats it can become ([targets]); [convert]
 * writes the result next to the source file. Pictures use the phone's own codecs, text documents are built from their paragraphs,
 * CSV and JSON are read as tables.
 */
object Converter {
    /** What each source extension can become. */
    val targets: Map<String, List<String>> = mapOf(
        "png" to listOf("jpg", "webp", "bmp"),
        "jpg" to listOf("png", "webp", "bmp"),
        "jpeg" to listOf("png", "webp", "bmp"),
        "webp" to listOf("png", "jpg", "bmp"),
        "bmp" to listOf("png", "jpg", "webp"),
        "txt" to listOf("pdf", "epub", "docx"),
        "docx" to listOf("txt", "epub"),
        "epub" to listOf("txt"),
        "pdf" to listOf("txt"),
        "csv" to listOf("json"),
        "json" to listOf("csv"),
    )

    /** The file types the converter can read. */
    val sources: Set<String> get() = targets.keys

    fun targetsFor(file: File): List<String> = targets[file.extension.lowercase()].orEmpty()

    /** Converts [src] into [target] next to it, and returns the new file. Throws with a short message when it cannot. */
    fun convert(c: Context, src: File, target: String): File {
        val from = src.extension.lowercase()
        if (target !in targets[from].orEmpty()) throw IllegalArgumentException("unsupported")
        val out = freeName(src, target)
        when (from) {
            "png", "jpg", "jpeg", "webp", "bmp" -> picture(src, out, target)
            "txt" -> {
                val paras = paragraphs(src.readText())
                when (target) {
                    "pdf" -> textToPdf(paras, out)
                    "epub" -> writeEpub(src.nameWithoutExtension, paras, out)
                    "docx" -> writeDocx(paras, out)
                }
            }
            "docx" -> {
                val paras = docxParagraphs(src)
                if (target == "txt") out.writeText(paras.joinToString("\n\n")) else writeEpub(src.nameWithoutExtension, paras, out)
            }
            "epub" -> out.writeText(epubText(src).joinToString("\n\n"))
            "pdf" -> out.writeText(pdfText(c, src).joinToString("\n\n"))
            "csv" -> out.writeText(JSONArray(csvRows(src.readText())).toString(2))
            "json" -> out.writeText(jsonToCsv(src.readText()))
        }
        if (!out.exists() || out.length() == 0L) throw IllegalStateException("empty")
        return out
    }

    private fun freeName(src: File, target: String): File {
        val base = src.nameWithoutExtension
        var f = File(src.parentFile, "$base.$target")
        var n = 1
        while (f.exists()) { f = File(src.parentFile, "${base}_$n.$target"); n++ }
        return f
    }

    // ---- pictures ----

    private fun picture(src: File, out: File, target: String) {
        val bmp = decode(src) ?: throw IllegalStateException("picture")
        try {
            if (target == "bmp") writeBmp(bmp, out) else {
                val format = when (target) {
                    "png" -> Bitmap.CompressFormat.PNG
                    "webp" -> if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
                    else -> Bitmap.CompressFormat.JPEG
                }
                // a JPEG has no transparency: the picture goes on white
                val flat = if (format == Bitmap.CompressFormat.JPEG) onWhite(bmp) else bmp
                out.outputStream().use { flat.compress(format, if (format == Bitmap.CompressFormat.PNG) 100 else 92, it) }
                if (flat !== bmp) flat.recycle()
            }
        } finally {
            bmp.recycle()
        }
    }

    /** A picture decoded at most 4096 pixels on its longest side, so a large photo cannot run the phone out of memory. */
    private fun decode(src: File): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(src.path, o)
        if (o.outWidth <= 0 || o.outHeight <= 0) return null
        var s = 1
        while (max(o.outWidth, o.outHeight) / s > 4096) s *= 2
        return BitmapFactory.decodeFile(src.path, BitmapFactory.Options().apply { inSampleSize = s })
    }

    private fun onWhite(bmp: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(bmp, 0f, 0f, null)
        return out
    }

    /** A 24-bit Windows bitmap: the header, then the rows from the bottom up, each padded to 4 bytes. */
    private fun writeBmp(bmp: Bitmap, out: File) {
        val w = bmp.width
        val h = bmp.height
        val row = ((w * 3 + 3) / 4) * 4
        val size = 54 + row * h
        val head = java.nio.ByteBuffer.allocate(54).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        head.put('B'.code.toByte()).put('M'.code.toByte()).putInt(size).putInt(0).putInt(54)
        head.putInt(40).putInt(w).putInt(h).putShort(1).putShort(24).putInt(0).putInt(row * h).putInt(2835).putInt(2835).putInt(0).putInt(0)
        out.outputStream().buffered().use { o ->
            o.write(head.array())
            val pixels = IntArray(w)
            val line = ByteArray(row)
            for (y in h - 1 downTo 0) {
                bmp.getPixels(pixels, 0, w, 0, y, w, 1)
                for (x in 0 until w) {
                    val p = pixels[x]
                    line[x * 3] = (p and 0xFF).toByte()
                    line[x * 3 + 1] = ((p shr 8) and 0xFF).toByte()
                    line[x * 3 + 2] = ((p shr 16) and 0xFF).toByte()
                }
                o.write(line)
            }
        }
    }

    // ---- text ----

    /** Paragraphs are the blocks between blank lines; the lines inside one are joined. */
    private fun paragraphs(text: String): List<String> =
        text.split(Regex("\\n\\s*\\n")).map { it.replace('\n', ' ').replace(Regex("\\s+"), " ").trim() }.filter { it.isNotEmpty() }

    private fun textToPdf(paras: List<String>, out: File) {
        val pageW = 595
        val pageH = 842
        val margin = 48
        val w = pageW - 2 * margin
        val contentH = pageH - 2 * margin
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12f; color = Color.BLACK }
        @Suppress("DEPRECATION")
        val layout = StaticLayout(paras.joinToString("\n\n"), paint, w, Layout.Alignment.ALIGN_NORMAL, 1.3f, 0f, false)
        val pages = max(1, ceil(layout.height / contentH.toDouble()).toInt())
        val doc = PdfDocument()
        try {
            for (p in 0 until pages) {
                val page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, p + 1).create())
                val cv = page.canvas
                cv.save()
                cv.translate(margin.toFloat(), margin - p * contentH.toFloat())
                cv.clipRect(0f, p * contentH.toFloat(), w.toFloat(), (p + 1) * contentH.toFloat())
                layout.draw(cv)
                cv.restore()
                doc.finishPage(page)
            }
            out.outputStream().use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
    }

    private fun esc(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    /** An EPUB 2: the stored mimetype first, the container, the package, a table of contents and the chapters (about 60 paragraphs each). */
    private fun writeEpub(title: String, paras: List<String>, out: File) {
        val chapters = paras.chunked(60).ifEmpty { listOf(emptyList()) }
        val id = "pulse-" + System.currentTimeMillis()
        ZipOutputStream(out.outputStream().buffered()).use { z ->
            stored(z, "mimetype", "application/epub+zip")
            deflated(z, "META-INF/container.xml", """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""")
            chapters.forEachIndexed { i, ps ->
                val body = ps.joinToString("\n") { "<p>${esc(it)}</p>" }
                deflated(z, "OEBPS/ch$i.xhtml", """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html><html xmlns="http://www.w3.org/1999/xhtml"><head><title>${esc(title)}</title><meta charset="utf-8"/></head><body>
$body
</body></html>""")
            }
            val manifest = chapters.indices.joinToString("\n") { """<item id="c$it" href="ch$it.xhtml" media-type="application/xhtml+xml"/>""" }
            val spine = chapters.indices.joinToString("\n") { """<itemref idref="c$it"/>""" }
            deflated(z, "OEBPS/content.opf", """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="bookid">
<metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>${esc(title)}</dc:title><dc:language>tr</dc:language><dc:identifier id="bookid">$id</dc:identifier></metadata>
<manifest><item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
$manifest</manifest>
<spine toc="ncx">
$spine</spine>
</package>""")
            val nav = chapters.indices.joinToString("\n") { """<navPoint id="n$it" playOrder="${it + 1}"><navLabel><text>${esc(title)} ${it + 1}</text></navLabel><content src="ch$it.xhtml"/></navPoint>""" }
            deflated(z, "OEBPS/toc.ncx", """<?xml version="1.0" encoding="UTF-8"?>
<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1"><head><meta name="dtb:uid" content="$id"/></head>
<docTitle><text>${esc(title)}</text></docTitle><navMap>
$nav
</navMap></ncx>""")
        }
    }

    private fun stored(z: ZipOutputStream, name: String, text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        val e = ZipEntry(name).apply {
            method = ZipEntry.STORED
            size = bytes.size.toLong()
            compressedSize = bytes.size.toLong()
            crc = CRC32().apply { update(bytes) }.value
        }
        z.putNextEntry(e)
        z.write(bytes)
        z.closeEntry()
    }

    private fun deflated(z: ZipOutputStream, name: String, text: String) {
        z.putNextEntry(ZipEntry(name))
        z.write(text.toByteArray(Charsets.UTF_8))
        z.closeEntry()
    }

    /** A minimal Word document: one paragraph per block of text. */
    private fun writeDocx(paras: List<String>, out: File) {
        val body = paras.joinToString("") { "<w:p><w:r><w:t xml:space=\"preserve\">${esc(it)}</w:t></w:r></w:p>" }
        ZipOutputStream(out.outputStream().buffered()).use { z ->
            deflated(z, "[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>""")
            deflated(z, "_rels/.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>""")
            deflated(z, "word/document.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>$body<w:sectPr/></w:body></w:document>""")
        }
    }

    /** The paragraphs of a Word document: the text runs, tabs and line breaks inside each paragraph. */
    private fun docxParagraphs(src: File): List<String> {
        ZipFile(src).use { zip ->
            val entry = zip.getEntry("word/document.xml") ?: throw IllegalStateException("docx")
            val out = ArrayList<String>()
            val cur = StringBuilder()
            val p = android.util.Xml.newPullParser().apply { setInput(zip.getInputStream(entry), "UTF-8") }
            var inText = false
            var ev = p.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                when (ev) {
                    XmlPullParser.START_TAG -> when (p.name) {
                        "w:t" -> inText = true
                        "w:tab" -> cur.append('\t')
                        "w:br" -> cur.append('\n')
                    }
                    XmlPullParser.TEXT -> if (inText) cur.append(p.text)
                    XmlPullParser.END_TAG -> when (p.name) {
                        "w:t" -> inText = false
                        "w:p" -> { out += cur.toString().trim(); cur.setLength(0) }
                    }
                }
                ev = p.next()
            }
            return out.filter { it.isNotEmpty() }
        }
    }

    /** The text of an EPUB, chapter by chapter in reading order: the package's spine, tags taken out, paragraphs kept. */
    private fun epubText(src: File): List<String> {
        ZipFile(src).use { zip ->
            val container = read(zip, "META-INF/container.xml")
            val opfPath = Regex("full-path=\"([^\"]+)\"").find(container)?.groupValues?.get(1) ?: throw IllegalStateException("epub")
            val opf = read(zip, opfPath)
            val dir = opfPath.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
            val manifest = Regex("<item\\b[^>]*>").findAll(opf).mapNotNull { m ->
                val id = Regex("id=\"([^\"]+)\"").find(m.value)?.groupValues?.get(1)
                val href = Regex("href=\"([^\"]+)\"").find(m.value)?.groupValues?.get(1)
                if (id != null && href != null) id to href else null
            }.toMap()
            val order = Regex("<itemref\\b[^>]*idref=\"([^\"]+)\"").findAll(opf).map { it.groupValues[1] }.toList()
            return order.mapNotNull { id ->
                val href = manifest[id] ?: return@mapNotNull null
                val entry = zip.getEntry(dir + href) ?: return@mapNotNull null
                val html = zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
                htmlParagraphs(html)
            }.flatten()
        }
    }

    private fun read(zip: ZipFile, name: String): String =
        zip.getEntry(name)?.let { zip.getInputStream(it).bufferedReader(Charsets.UTF_8).use { r -> r.readText() } } ?: throw IllegalStateException(name)

    private fun htmlParagraphs(html: String): List<String> {
        val body = html.replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"), "")
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</(p|div|h[1-6]|li|tr)>"), "\n\n")
            .replace(Regex("<[^>]+>"), "")
        return decodeEntities(body).split(Regex("\\n\\s*\\n")).map { it.replace(Regex("\\s+"), " ").trim() }.filter { it.isNotEmpty() }
    }

    private fun decodeEntities(s: String): String = s
        .replace("&nbsp;", " ").replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
        .replace(Regex("&#(\\d+);")) { m -> m.groupValues[1].toIntOrNull()?.let { String(Character.toChars(it)) } ?: "" }
        .replace(Regex("&#x([0-9a-fA-F]+);")) { m -> m.groupValues[1].toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: "" }

    /** The text of a PDF that has text in it (scanned pages give nothing here). */
    private fun pdfText(c: Context, src: File): List<String> {
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(c.applicationContext)
        val text = src.inputStream().use { input ->
            com.tom_roush.pdfbox.pdmodel.PDDocument.load(input).use { doc -> com.tom_roush.pdfbox.text.PDFTextStripper().getText(doc) }
        }
        return paragraphs(text.replace(Regex("-\\n(?=\\p{Ll})"), ""))
    }

    // ---- tables ----

    /** CSV rows as objects, the first line gives the names. Quoted fields may hold commas and line breaks. */
    private fun csvRows(text: String): List<JSONObject> {
        val rows = parseCsv(text)
        if (rows.isEmpty()) return emptyList()
        val head = rows.first()
        return rows.drop(1).filter { it.any { cell -> cell.isNotEmpty() } }.map { cells ->
            JSONObject().also { o -> head.forEachIndexed { i, name -> o.put(name.ifBlank { "column${i + 1}" }, cells.getOrElse(i) { "" }) } }
        }
    }

    private fun parseCsv(text: String): List<List<String>> {
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            when {
                quoted && ch == '"' && i + 1 < text.length && text[i + 1] == '"' -> { cell.append('"'); i++ }
                ch == '"' -> quoted = !quoted
                !quoted && ch == ',' -> { row += cell.toString(); cell.setLength(0) }
                !quoted && (ch == '\n' || ch == '\r') -> {
                    if (ch == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                    row += cell.toString(); cell.setLength(0)
                    rows += row; row = ArrayList()
                }
                else -> cell.append(ch)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) { row += cell.toString(); rows += row }
        return rows
    }

    /** A JSON array of objects as CSV: the names of all the objects, in the order they first appear. */
    private fun jsonToCsv(text: String): String {
        val arr = JSONArray(text.trim())
        val names = LinkedHashSet<String>()
        for (i in 0 until arr.length()) arr.optJSONObject(i)?.keys()?.forEach { names += it }
        fun cell(s: String): String = if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
        val sb = StringBuilder()
        sb.append(names.joinToString(",") { cell(it) }).append('\n')
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            sb.append(names.joinToString(",") { cell(o.optString(it, "")) }).append('\n')
        }
        return sb.toString()
    }
}
