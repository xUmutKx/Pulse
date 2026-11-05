package com.umutk.pulse.features

import android.content.Context
import java.io.File
import java.util.zip.ZipFile

/**
 * The text inside documents, for the Files search: Word (docx), Excel (xlsx), PowerPoint (pptx), PDF, and plain text files.
 * Read on the search's own background thread only; a file over [MAX_BYTES] or a document with no readable text is skipped.
 */
object DocText {
    private const val MAX_BYTES = 40L * 1024 * 1024
    private const val MAX_TEXT = 4 * 1024 * 1024
    private val OFFICE = setOf("docx", "xlsx", "pptx")
    private val PLAIN = setOf("txt", "md", "csv", "log", "json", "xml", "html", "htm", "rtf")

    /** True for the kinds the content search reads. */
    fun readable(f: File): Boolean {
        val ext = f.extension.lowercase()
        return f.isFile && f.length() in 1..MAX_BYTES && (ext in OFFICE || ext == "pdf" || ext in PLAIN)
    }

    /** The text of [f], or "" when it has none or cannot be read. */
    fun text(c: Context, f: File): String = runCatching {
        when (val ext = f.extension.lowercase()) {
            "docx" -> zipXml(f) { it.startsWith("word/") && it.endsWith(".xml") && (it == "word/document.xml" || it.startsWith("word/header") || it.startsWith("word/footer")) }
            "xlsx" -> zipXml(f) { it == "xl/sharedStrings.xml" || (it.startsWith("xl/worksheets/") && it.endsWith(".xml")) }
            "pptx" -> zipXml(f) { it.startsWith("ppt/slides/slide") && it.endsWith(".xml") }
            "pdf" -> pdf(c, f)
            else -> if (ext in PLAIN) f.readText(Charsets.UTF_8).take(MAX_TEXT) else ""
        }
    }.getOrDefault("")

    /** Every XML part whose name [want] accepts, with its tags turned into spaces. */
    private fun zipXml(f: File, want: (String) -> Boolean): String {
        val out = StringBuilder()
        ZipFile(f).use { zip ->
            zip.entries().asSequence().filter { !it.isDirectory && want(it.name) }.forEach { e ->
                zip.getInputStream(e).bufferedReader(Charsets.UTF_8).use { r ->
                    val xml = r.readText()
                    out.append(xml.replace(Regex("<[^>]+>"), " ")).append('\n')
                }
                if (out.length > MAX_TEXT) return unescape(out.toString())
            }
        }
        return unescape(out.toString())
    }

    private fun pdf(c: Context, f: File): String {
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(c.applicationContext)
        return f.inputStream().use { input ->
            com.tom_roush.pdfbox.pdmodel.PDDocument.load(input).use { doc ->
                com.tom_roush.pdfbox.text.PDFTextStripper().getText(doc).take(MAX_TEXT)
            }
        }
    }

    private fun unescape(s: String): String = s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'")
        .replace(Regex("\\s+"), " ")

    /**
     * Files under [root] whose text contains [q] (ignoring case). Stops after [budgetMs] or [limit] hits, so a big folder never holds the search.
     * The name matches are the caller's; this only adds the files whose inside matches.
     */
    fun contentHits(c: Context, root: File, q: String, hidden: Boolean, limit: Int = 200, budgetMs: Long = 12_000): List<File> {
        val needle = q.trim()
        if (needle.length < 2) return emptyList()
        val until = System.currentTimeMillis() + budgetMs
        val hits = ArrayList<File>()
        for (f in root.walkTopDown().take(50_000)) {
            if (System.currentTimeMillis() > until || hits.size >= limit) break
            if (f == root || (!hidden && f.name.startsWith("."))) continue
            if (!readable(f)) continue
            if (text(c, f).contains(needle, ignoreCase = true)) hits += f
        }
        return hits
    }
}
