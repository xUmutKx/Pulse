package com.umutk.pulse.features

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Finds the download links of a web page that is not a file itself: the page is fetched once as plain text (no WebView, nothing heavy) and its
 * links, media sources and download buttons are read for files. Pages that build their links with scripts show nothing here.
 */
object LinkFinder {
    sealed class Result {
        /** The address is a file already. */
        object Direct : Result()
        /** Download links found on the page, the likeliest first. */
        class Found(val links: List<String>) : Result()
        object None : Result()
        class Failed(val why: String) : Result()
    }

    private val FILE_EXT = setOf("apk", "apks", "xapk", "apkm", "zip", "7z", "rar", "tar", "gz", "tgz", "bz2", "xz", "exe", "msi", "dmg", "iso", "img", "deb", "pdf", "epub", "mobi",
        "doc", "docx", "xls", "xlsx", "ppt", "pptx", "csv", "mp3", "flac", "wav", "ogg", "m4a", "aac", "opus", "mp4", "mkv", "avi", "webm", "mov", "m4v", "3gp", "m3u8", "torrent", "ttf", "otf", "wsz")
    private val ATTR = Regex("""(?i)\b(?:href|src|data-url|data-href|data-src|content)\s*=\s*["']([^"']+)["']""")
    private val RAW = Regex("""(?i)https?://[^\s"'<>\\)]+""")

    private fun ext(u: String) = u.substringBefore('#').substringBefore('?').substringAfterLast('/').substringAfterLast('.', "").lowercase()

    suspend fun find(url: String): Result = withContext(Dispatchers.IO) {
        try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.instanceFollowRedirects = true; c.connectTimeout = 8000; c.readTimeout = 10000
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
            c.setRequestProperty("Accept", "text/html,application/xhtml+xml,*/*;q=0.8")
            try {
                if (c.responseCode !in 200..299) return@withContext Result.Failed("HTTP ${c.responseCode}")
                val type = c.contentType.orEmpty().lowercase()
                val disp = c.getHeaderField("Content-Disposition").orEmpty().lowercase()
                // a file, an attachment or any non-page answer is downloaded as it is
                if (!type.contains("text/html") && !type.contains("xhtml") || disp.contains("attachment") || ext(c.url.toString()) in FILE_EXT) return@withContext Result.Direct
                val base = c.url
                val html = c.inputStream.use { it.readNBytes(1_500_000).toString(Charsets.UTF_8) }
                val seen = LinkedHashSet<String>()
                fun take(raw: String) {
                    val r = raw.trim().replace("&amp;", "&").replace("\\/", "/")
                    if (r.isEmpty() || r.startsWith("javascript:") || r.startsWith("mailto:") || r.startsWith("data:") || r.startsWith("#")) return
                    val abs = runCatching { URL(base, r).toString() }.getOrNull() ?: return
                    if (abs.startsWith("http")) seen += abs
                }
                ATTR.findAll(html).forEach { take(it.groupValues[1]) }
                RAW.findAll(html).forEach { take(it.value) }
                val files = seen.filter { ext(it) in FILE_EXT }
                // buttons that go through a download script: address says download, but no extension
                val maybe = seen.filter { ext(it) !in FILE_EXT && Regex("(?i)(/download|download=|[?&]dl=|/get/|/file/|/dl/)").containsMatchIn(it) }
                val all = (files + maybe).distinct().take(40)
                if (all.isEmpty()) Result.None else Result.Found(all)
            } finally { c.disconnect() }
        } catch (e: Exception) { Result.Failed(e.message ?: e.javaClass.simpleName) }
    }
}
