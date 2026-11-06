package com.umutk.pulse.extras.ebook

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

/*
 * The e-book reader's files: EPUB and TXT are read into chapters of paragraphs (the screen lays them out in pages),
 * PDF is drawn page by page by the system's own renderer. Nothing is bundled: no fonts, covers or images are stored.
 */

/** One paragraph of a chapter; a heading is drawn bold and larger. */
class Para(val text: String, val heading: Boolean)

class Chapter(val title: String, val paras: List<Para>)

/** A book on the shelf: [uri] is what is opened again, [pdf] says which kind of reader it needs. */
data class Book(val uri: String, val title: String, val author: String, val pdf: Boolean, val chapters: List<Chapter>)

object Ebook {
    val EXTS = listOf("epub", "txt", "pdf")

    fun isBook(name: String) = EXTS.any { name.endsWith(".$it", ignoreCase = true) }

    /** Reads a book from a content or file uri. Text books are read whole (their text only); a PDF gives just its name. */
    fun open(c: Context, uri: Uri): Book? {
        val name = nameOf(c, uri)
        val lower = name.lowercase()
        val fallback = name.substringBeforeLast('.')
        return when {
            // a PDF with text in it is read as a text book, reflowed to the reader's own pages (no page layout kept);
            // a scanned PDF (no text) stays a page-by-page PDF
            lower.endsWith(".pdf") -> pdfText(c, uri)?.let { paras -> Book(uri.toString(), fallback, "", false, listOf(Chapter(fallback, paras.map { Para(it, false) }))) }
                ?: Book(uri.toString(), fallback, "", true, emptyList())
            lower.endsWith(".epub") -> c.contentResolver.openInputStream(uri)?.use { epub(it, fallback, uri.toString()) }
            lower.endsWith(".txt") -> c.contentResolver.openInputStream(uri)?.use { txt(it, fallback, uri.toString()) }
            else -> null
        }
    }

    /** The paragraphs of a PDF's text (line ends joined, hyphens at a line end mended), or null when there is little or no text. Reads the file: call off the main thread. */
    private fun pdfText(c: Context, uri: Uri): List<String>? = runCatching {
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(c.applicationContext)
        c.contentResolver.openInputStream(uri)?.use { input ->
            com.tom_roush.pdfbox.pdmodel.PDDocument.load(input).use { doc ->
                val text = com.tom_roush.pdfbox.text.PDFTextStripper().getText(doc)
                text.replace(Regex("-\\n(?=\\p{Ll})"), "").split(Regex("\\n\\s*\\n"))
                    .map { it.replace(Regex("\\s+"), " ").trim() }.filter { it.length > 1 }
            }
        }
    }.getOrNull()?.takeIf { p -> p.sumOf { it.length } > 200 }

    fun nameOf(c: Context, uri: Uri): String {
        runCatching {
            c.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { q ->
                if (q.moveToFirst()) return q.getString(0) ?: ""
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: ""
    }

    private fun txt(input: InputStream, title: String, uri: String): Book {
        val text = input.bufferedReader().readText()
        val paras = text.split(Regex("\\n\\s*\\n")).map { it.replace('\n', ' ').trim() }.filter { it.isNotEmpty() }.map { Para(it, false) }
        return Book(uri, title, "", false, listOf(Chapter(title, paras)))
    }

    /** An EPUB is a zip: the package file (OPF) names the chapters in reading order (the spine). Pictures are skipped. */
    private fun epub(input: InputStream, fallback: String, uri: String): Book {
        val files = HashMap<String, String>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                val n = e.name.lowercase()
                if (!e.isDirectory && (n.endsWith(".opf") || n.endsWith(".xhtml") || n.endsWith(".html") || n.endsWith(".htm") || n.endsWith(".xml") || n == "mimetype")) {
                    files[e.name] = zip.bufferedReader().readText()
                }
            }
        }
        val container = files.entries.firstOrNull { it.key.equals("META-INF/container.xml", true) }?.value.orEmpty()
        val opfPath = Regex("full-path=\"([^\"]+)\"").find(container)?.groupValues?.get(1)
            ?: files.keys.firstOrNull { it.endsWith(".opf", true) } ?: return Book(uri, fallback, "", false, emptyList())
        val opf = files[opfPath] ?: return Book(uri, fallback, "", false, emptyList())
        val dir = if (opfPath.contains('/')) opfPath.substringBeforeLast('/') + "/" else ""
        val title = tag(opf, "dc:title") ?: fallback
        val author = tag(opf, "dc:creator").orEmpty()
        val manifest = HashMap<String, String>()
        Regex("<item\\b[^>]*>").findAll(opf).forEach { m ->
            val id = attr(m.value, "id"); val href = attr(m.value, "href")
            if (id != null && href != null) manifest[id] = dir + java.net.URLDecoder.decode(href, "UTF-8")
        }
        val chapters = ArrayList<Chapter>()
        Regex("<itemref\\b[^>]*>").findAll(opf).forEach { m ->
            val href = attr(m.value, "idref")?.let { manifest[it] } ?: return@forEach
            val html = files[href] ?: files.entries.firstOrNull { it.key.equals(href, true) }?.value ?: return@forEach
            val paras = htmlParas(html)
            if (paras.isEmpty()) return@forEach
            val heading = paras.firstOrNull { it.heading }?.text ?: "${chapters.size + 1}"
            chapters += Chapter(heading, paras)
        }
        return Book(uri, title, author, false, chapters)
    }

    private fun tag(xml: String, name: String): String? =
        Regex("<$name[^>]*>([^<]*)</$name>").find(xml)?.groupValues?.get(1)?.let { unescape(it).trim() }?.takeIf { it.isNotEmpty() }

    private fun attr(tag: String, name: String): String? =
        Regex("\\b$name\\s*=\\s*\"([^\"]*)\"").find(tag)?.groupValues?.get(1)

    /** The text of one XHTML page: block tags become paragraph breaks, headings are marked, the rest of the tags go. */
    fun htmlParas(html: String): List<Para> {
        var s = html.replace(Regex("(?is)<head\\b.*?</head>"), " ")
            .replace(Regex("(?is)<(style|script)\\b.*?</\\1>"), " ")
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)<h[1-6]\\b[^>]*>"), "\n\u0001")
            .replace(Regex("(?i)</h[1-6]>"), "\u0002\n\n")
            .replace(Regex("(?i)</?(p|div|li|tr|section|blockquote|article)\\b[^>]*>"), "\n\n")
        s = s.replace(Regex("<[^>]+>"), "")
        return unescape(s).split(Regex("\\n\\s*\\n")).mapNotNull { raw ->
            val heading = raw.contains('\u0001')
            val t = raw.replace("\u0001", "").replace("\u0002", "").replace(Regex("\\s+"), " ").trim()
            if (t.isEmpty()) null else Para(t, heading)
        }
    }

    private fun unescape(s: String): String = s
        .replace(Regex("&#x([0-9a-fA-F]+);")) { String(Character.toChars(it.groupValues[1].toInt(16))) }
        .replace(Regex("&#([0-9]+);")) { String(Character.toChars(it.groupValues[1].toInt())) }
        .replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")

    /**
     * The picture of a book on the shelf: the first page of a PDF, the cover picture of an EPUB. It is drawn once and kept as a PNG
     * in the app's files, named by the book's uri. Null when the book has no picture (the shelf then draws the letter).
     */
    private fun coverKey(uri: String): String = java.security.MessageDigest.getInstance("MD5").digest((uri + "#v4").toByteArray()).joinToString("") { "%02x".format(it) }

    /** A book leaves the shelf: its picture, favourite, progress and last-opened time go with it. The book file itself stays on the phone. */
    fun forget(c: Context, uri: String) {
        val key = coverKey(uri)
        val dir = File(c.filesDir, "covers")
        File(dir, "$key.jpg").delete()
        File(dir, "$key.none").delete()
        val favs = HashSet(p(c).getStringSet("favs", emptySet()).orEmpty()).apply { remove(uri) }
        p(c).edit().putStringSet("favs", favs).remove("open_$uri").remove("pct_$uri").apply()
    }

    // the covers of the older builds were PNG files of 480 x 720 (a big shelf took 25 MB): they are deleted once, the new ones are small JPEGs
    @Volatile private var purged = false
    private fun purgeOld(dir: File) {
        if (purged) return
        purged = true
        dir.listFiles()?.forEach { if (it.name.endsWith(".png")) it.delete() }
    }

    /** A cover is kept as a JPEG of at most 100 KB: 240 px wide first, then lower quality and smaller until it fits. */
    private fun saveCover(src: Bitmap, out: File) {
        var width = 240
        while (true) {
            val h = (width * src.height / src.width.toFloat()).toInt().coerceAtLeast(1)
            val small = Bitmap.createBitmap(width, h, Bitmap.Config.ARGB_8888)
            val cv = android.graphics.Canvas(small)
            cv.drawColor(android.graphics.Color.BLACK)   // a picture with see-through parts lies on black
            cv.drawBitmap(src, null, android.graphics.Rect(0, 0, width, h), android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
            var q = 75
            do {
                out.outputStream().use { small.compress(Bitmap.CompressFormat.JPEG, q, it) }
                q -= 12
            } while (out.length() > 100_000L && q >= 30)
            small.recycle()
            if (out.length() <= 100_000L || width <= 120) return
            width -= 60
        }
    }

    fun cover(c: Context, book: Book): File? {
        val key = coverKey(book.uri)
        val dir = File(c.filesDir, "covers").apply { mkdirs() }
        purgeOld(dir)
        val png = File(dir, "$key.jpg")
        val none = File(dir, "$key.none")
        if (png.exists()) return png
        if (none.exists()) return null
        val ok = runCatching {
            if (book.pdf) pdfFirstPage(c, Uri.parse(book.uri), png)
            // no picture inside an EPUB (or a TXT): a cover drawn from the title, white letters on black
            else epubCover(c, Uri.parse(book.uri), png) || Ebook.open(c, Uri.parse(book.uri))?.let { textPage(it, png) } == true
        }.getOrDefault(false)
        if (!ok) none.createNewFile()
        return if (ok) png else null
    }

    /** A cover drawn from the book's name: black, the title in big white letters, the author under it, the first line of the book small at the foot. */
    private fun textPage(book: Book, out: File): Boolean {
        if (book.title.isBlank()) return false
        val w = 480
        val h = 720
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        canvas.drawColor(android.graphics.Color.BLACK)
        val titlePaint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE; typeface = android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD); textSize = 54f
        }
        @Suppress("DEPRECATION")
        var layout = android.text.StaticLayout(book.title, titlePaint, w - 80, android.text.Layout.Alignment.ALIGN_CENTER, 1.1f, 0f, false)
        // a long title takes smaller letters so it stays inside the cover
        while (layout.height > h * 0.55f && titlePaint.textSize > 26f) {
            titlePaint.textSize -= 4f
            @Suppress("DEPRECATION")
            layout = android.text.StaticLayout(book.title, titlePaint, w - 80, android.text.Layout.Alignment.ALIGN_CENTER, 1.1f, 0f, false)
        }
        canvas.save(); canvas.translate(40f, (h - layout.height) / 2f - 30f); layout.draw(canvas); canvas.restore()
        // a thin white line, then the author
        val line = android.graphics.Paint().apply { color = android.graphics.Color.WHITE; alpha = 160; strokeWidth = 2f }
        val lineY = (h + layout.height) / 2f + 4f
        canvas.drawLine(w / 2f - 40f, lineY, w / 2f + 40f, lineY, line)
        val author = book.author.trim()
        if (author.isNotEmpty()) {
            val ap = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; alpha = 200; textSize = 28f }
            @Suppress("DEPRECATION")
            val al = android.text.StaticLayout(author, ap, w - 80, android.text.Layout.Alignment.ALIGN_CENTER, 1.1f, 0f, false)
            canvas.save(); canvas.translate(40f, lineY + 22f); al.draw(canvas); canvas.restore()
        }
        saveCover(bmp, out)
        bmp.recycle()
        return true
    }

    /** The first page of a PDF, drawn at the width of a cover. A mostly white page is turned to white letters on black. */
    private fun pdfFirstPage(c: Context, uri: Uri, out: File): Boolean {
        val fd = c.contentResolver.openFileDescriptor(uri, "r") ?: return false
        try {
            val r = PdfRenderer(fd)
            try {
                if (r.pageCount == 0) return false
                val pg = r.openPage(0)
                try {
                    val w = 320
                    val h = (w * pg.height / pg.width.toFloat()).toInt().coerceAtLeast(1)
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(android.graphics.Color.WHITE)
                    pg.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    saveCover(invertIfLight(bmp), out)
                    bmp.recycle()
                } finally { pg.close() }
            } finally { r.close() }
        } finally { fd.close() }
        return true
    }

    /** The picture with its colours turned over when it is mostly light (a white page): black ground, light letters. Returned as it is otherwise. */
    private fun invertIfLight(bmp: Bitmap): Bitmap {
        var sum = 0L; var n = 0
        for (x in 0 until bmp.width step 8) for (y in 0 until bmp.height step 8) {
            val p = bmp.getPixel(x, y)
            sum += (android.graphics.Color.red(p) * 3 + android.graphics.Color.green(p) * 6 + android.graphics.Color.blue(p)) / 10; n++
        }
        if (n == 0 || sum / n < 170) return bmp
        val inv = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        val paint = android.graphics.Paint().apply {
            colorFilter = android.graphics.ColorMatrixColorFilter(android.graphics.ColorMatrix(floatArrayOf(-1f, 0f, 0f, 0f, 255f, 0f, -1f, 0f, 0f, 255f, 0f, 0f, -1f, 0f, 255f, 0f, 0f, 0f, 1f, 0f)))
        }
        android.graphics.Canvas(inv).drawBitmap(bmp, 0f, 0f, paint)
        return inv
    }

    /**
     * The cover picture of an EPUB. The package names it (EPUB 3 "cover-image", EPUB 2 meta "cover"); when it does not, the cover page
     * is read for its picture, and failing that a picture whose name says cover is taken.
     */
    private fun epubCover(c: Context, uri: Uri, out: File): Boolean {
        val container = zipEntry(c, uri) { it.equals("META-INF/container.xml", true) }?.toString(Charsets.UTF_8) ?: return false
        val opfPath = Regex("full-path=\"([^\"]+)\"").find(container)?.groupValues?.get(1) ?: return false
        val opf = zipEntry(c, uri) { it.equals(opfPath, true) }?.toString(Charsets.UTF_8) ?: return false
        val items = Regex("<item\\b[^>]*>").findAll(opf).map { it.value }.toList()
        fun full(href: String) = resolvePath(opfPath, java.net.URLDecoder.decode(href, "UTF-8"))
        val candidates = ArrayList<String>()
        val coverId = Regex("<meta\\b[^>]*name=\"cover\"[^>]*>").find(opf)?.value?.let { attr(it, "content") }
        items.firstOrNull { it.contains("cover-image") }?.let { attr(it, "href") }?.let { candidates += full(it) }
        coverId?.let { id -> items.firstOrNull { attr(it, "id") == id }?.let { attr(it, "href") }?.let { candidates += full(it) } }
        // the cover page: its first picture
        for (item in items.filter { (attr(it, "id") ?: "").contains("cover", true) || (attr(it, "href") ?: "").contains("cover", true) }) {
            val href = attr(item, "href") ?: continue
            if (href.endsWith(".xhtml", true) || href.endsWith(".html", true) || href.endsWith(".htm", true)) {
                val page = resolvePath(opfPath, java.net.URLDecoder.decode(href, "UTF-8"))
                val text = zipEntry(c, uri) { it.equals(page, true) }?.toString(Charsets.UTF_8) ?: continue
                Regex("(?:src|href)=\"([^\"]+\\.(?:jpe?g|png|gif|webp))\"", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)
                    ?.let { candidates += resolvePath(page, java.net.URLDecoder.decode(it, "UTF-8")) }
            } else candidates += full(href)
        }
        // any picture whose name says cover, front or title
        items.filter { ((attr(it, "href") ?: "") + (attr(it, "id") ?: "")).let { n -> n.contains("cover", true) || n.contains("front", true) || n.contains("title", true) } && (attr(it, "media-type") ?: "").startsWith("image") }
            .mapNotNullTo(candidates) { attr(it, "href")?.let { h -> full(h) } }
        for (path in candidates) {
            val bytes = zipEntry(c, uri) { it.equals(path, true) } ?: continue
            val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: continue
            saveCover(bmp, out)
            bmp.recycle()
            return true
        }
        return false
    }

    /** The path of [rel] as seen from the file [base] inside the zip (the ".." and "." parts are taken out). */
    private fun resolvePath(base: String, rel: String): String {
        val out = ArrayList<String>()
        for (part in (base.substringBeforeLast('/', "") + "/" + rel).split('/')) {
            when (part) {
                "", "." -> {}
                ".." -> if (out.isNotEmpty()) out.removeAt(out.size - 1)
                else -> out += part
            }
        }
        return out.joinToString("/")
    }

    /** The bytes of the first entry of the zip that [pick] takes, read by streaming (the book is not held in memory). */
    private fun zipEntry(c: Context, uri: Uri, pick: (String) -> Boolean): ByteArray? {
        c.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    if (!e.isDirectory && pick(e.name)) return zip.readBytes()
                }
            }
        }
        return null
    }

    // ---- what the reader remembers per book (the place, the bookmarks, the reading time) -----------------------------------

    private fun p(c: Context) = c.getSharedPreferences("ebook_state", Context.MODE_PRIVATE)

    /** Where the book was left: the chapter and the page in it (text books) or the PDF page. */
    fun place(c: Context, uri: String): Pair<Int, Int> {
        val v = p(c).getString("at_$uri", null) ?: return 0 to 0
        val parts = v.split(',')
        return (parts.getOrNull(0)?.toIntOrNull() ?: 0) to (parts.getOrNull(1)?.toIntOrNull() ?: 0)
    }

    fun savePlace(c: Context, uri: String, chapter: Int, page: Int) {
        p(c).edit().putString("at_$uri", "$chapter,$page").apply()
    }

    /** When the book was last opened (0 = never), for the Recent list. */
    fun opened(c: Context, uri: String): Long = p(c).getLong("open_$uri", 0L)
    fun touch(c: Context, uri: String) { p(c).edit().putLong("open_$uri", System.currentTimeMillis()).apply() }

    /** The favourites: a set of the book uris. */
    fun isFavorite(c: Context, uri: String): Boolean = p(c).getStringSet("favs", emptySet())?.contains(uri) == true
    fun toggleFavorite(c: Context, uri: String): Boolean {
        val set = HashSet(p(c).getStringSet("favs", emptySet()).orEmpty())
        val on = if (set.contains(uri)) { set.remove(uri); false } else { set.add(uri); true }
        p(c).edit().putStringSet("favs", set).apply()
        return on
    }

    /** How far the book was read, in per cent (the chapter reached out of all of them), for the shelf. */
    fun pct(c: Context, uri: String): Int = p(c).getInt("pct_$uri", 0)

    fun savePct(c: Context, uri: String, v: Int) { p(c).edit().putInt("pct_$uri", v.coerceIn(0, 100)).apply() }

    fun bookmarks(c: Context, uri: String): List<String> =
        p(c).getString("bm_$uri", "").orEmpty().split('|').filter { it.isNotBlank() }

    fun toggleBookmark(c: Context, uri: String, chapter: Int, page: Int): Boolean {
        val key = "$chapter,$page"
        val list = bookmarks(c, uri).toMutableList()
        val on = if (key in list) { list.remove(key); false } else { list.add(key); true }
        p(c).edit().putString("bm_$uri", list.joinToString("|")).apply()
        return on
    }
}
