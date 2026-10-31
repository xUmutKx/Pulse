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
            lower.endsWith(".pdf") -> Book(uri.toString(), fallback, "", true, emptyList())
            lower.endsWith(".epub") -> c.contentResolver.openInputStream(uri)?.use { epub(it, fallback, uri.toString()) }
            lower.endsWith(".txt") -> c.contentResolver.openInputStream(uri)?.use { txt(it, fallback, uri.toString()) }
            else -> null
        }
    }

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
    fun cover(c: Context, book: Book): File? {
        val key = java.security.MessageDigest.getInstance("MD5").digest(book.uri.toByteArray()).joinToString("") { "%02x".format(it) }
        val dir = File(c.filesDir, "covers").apply { mkdirs() }
        val png = File(dir, "$key.png")
        val none = File(dir, "$key.none")
        if (png.exists()) return png
        if (none.exists()) return null
        val ok = runCatching {
            if (book.pdf) pdfFirstPage(c, Uri.parse(book.uri), png) else epubCover(c, Uri.parse(book.uri), png)
        }.getOrDefault(false)
        if (!ok) none.createNewFile()
        return if (ok) png else null
    }

    /** The first page of a PDF, drawn at the width of a cover. */
    private fun pdfFirstPage(c: Context, uri: Uri, out: File): Boolean {
        val fd = c.contentResolver.openFileDescriptor(uri, "r") ?: return false
        try {
            val r = PdfRenderer(fd)
            try {
                if (r.pageCount == 0) return false
                val pg = r.openPage(0)
                try {
                    val w = 480
                    val h = (w * pg.height / pg.width.toFloat()).toInt().coerceAtLeast(1)
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(android.graphics.Color.WHITE)
                    pg.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    out.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
                    bmp.recycle()
                } finally { pg.close() }
            } finally { r.close() }
        } finally { fd.close() }
        return true
    }

    /** The cover picture of an EPUB: the manifest item the package marks as the cover (EPUB 3 or EPUB 2 style). */
    private fun epubCover(c: Context, uri: Uri, out: File): Boolean {
        val container = zipEntry(c, uri) { it.equals("META-INF/container.xml", true) }?.toString(Charsets.UTF_8) ?: return false
        val opfPath = Regex("full-path=\"([^\"]+)\"").find(container)?.groupValues?.get(1) ?: return false
        val opf = zipEntry(c, uri) { it == opfPath }?.toString(Charsets.UTF_8) ?: return false
        val dir = if (opfPath.contains('/')) opfPath.substringBeforeLast('/') + "/" else ""
        val items = Regex("<item\\b[^>]*>").findAll(opf).map { it.value }.toList()
        val coverId = Regex("<meta\\b[^>]*name=\"cover\"[^>]*>").find(opf)?.value?.let { attr(it, "content") }
        val href = items.firstOrNull { it.contains("cover-image") }?.let { attr(it, "href") }
            ?: coverId?.let { id -> items.firstOrNull { attr(it, "id") == id }?.let { attr(it, "href") } }
            ?: return false
        val path = dir + java.net.URLDecoder.decode(href, "UTF-8")
        val bytes = zipEntry(c, uri) { it == path } ?: return false
        val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return false
        out.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
        bmp.recycle()
        return true
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
