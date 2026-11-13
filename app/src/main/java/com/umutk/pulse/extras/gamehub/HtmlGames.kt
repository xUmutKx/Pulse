package com.umutk.pulse.extras.gamehub

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipInputStream

/*
 * Games of your own that are web pages: a single .html file, or a .zip with an index.html (and its pictures, scripts, sounds). They are copied into Pulse's
 * own folder, get a cover drawn from their name, sit on the shelf like any game and open full screen in their own window, every game under an address of its own
 * (so one game's saves never meet another's).
 */
internal object HtmlGames {
    const val PREFIX = "html:"
    class Item(val id: String, val name: String, val desc: String)

    private fun root(c: Context) = File(c.filesDir, "htmlgames").apply { mkdirs() }
    fun dir(c: Context, id: String) = File(root(c), id)
    fun coverFile(c: Context, id: String) = File(dir(c, id), "cover.jpg")
    fun isHtml(pkg: String) = pkg.startsWith(PREFIX)
    fun idOf(pkg: String) = pkg.removePrefix(PREFIX)

    fun list(c: Context): List<Item> = runCatching {
        val a = JSONArray(prefs(c).getString("htmlGames", "[]"))
        List(a.length()) { i -> a.getJSONObject(i).let { Item(it.getString("id"), it.getString("name"), it.optString("desc")) } }
    }.getOrDefault(emptyList()).filter { File(dir(c, it.id), "index.html").isFile }

    fun get(c: Context, pkg: String): Item? = list(c).firstOrNull { it.id == idOf(pkg) }

    private fun save(c: Context, l: List<Item>) {
        val a = JSONArray(); l.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name).put("desc", it.desc)) }
        prefs(c).edit().putString("htmlGames", a.toString()).apply()
    }

    private fun displayName(c: Context, uri: Uri): String? = runCatching {
        c.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
    }.getOrNull()

    /** Takes a chosen .html or .zip into the app; true when a game was made of it. */
    fun import(c: Context, uri: Uri): Boolean = runCatching {
        val fileName = displayName(c, uri).orEmpty()
        val id = "h" + java.lang.Long.toString(System.currentTimeMillis(), 36)
        val d = dir(c, id).apply { mkdirs() }
        val bytes = c.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return false
        if (bytes.size > 80_000_000) { d.deleteRecursively(); return false }
        val zip = bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
        if (zip) {
            var total = 0L
            ZipInputStream(bytes.inputStream()).use { z ->
                while (true) {
                    val e = z.nextEntry ?: break
                    val out = File(d, e.name)
                    // a name that leaves the game's own folder is skipped
                    if (!out.canonicalPath.startsWith(d.canonicalPath + File.separator)) continue
                    if (e.isDirectory) { out.mkdirs(); continue }
                    out.parentFile?.mkdirs()
                    out.outputStream().use { o -> total += z.copyTo(o) }
                    if (total > 200_000_000L) { d.deleteRecursively(); return false }
                }
            }
            // the page to start: index.html, at the top or inside the single folder the archive was packed in
            if (!File(d, "index.html").isFile) {
                val inner = d.walkTopDown().maxDepth(3).firstOrNull { it.isFile && it.name.equals("index.html", true) }
                if (inner == null) { d.deleteRecursively(); return false }
                val base = inner.parentFile!!
                if (base != d) { base.listFiles()?.forEach { it.renameTo(File(d, it.name)) } }
                if (!File(d, "index.html").isFile) { d.deleteRecursively(); return false }
            }
        } else File(d, "index.html").writeBytes(bytes)
        val text = runCatching { File(d, "index.html").readText().take(200_000) }.getOrDefault("")
        val title = Regex("<title[^>]*>([^<]{1,80})</title>", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
        val name = (title ?: fileName.substringBeforeLast('.').replace('_', ' ').replace('-', ' ').trim().takeIf { it.isNotEmpty() } ?: "HTML game").take(60)
        val desc = Regex("<meta[^>]+name=[\"']description[\"'][^>]+content=[\"']([^\"']{1,300})", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)?.trim().orEmpty()
        makeCover(c, id, name)
        save(c, list(c) + Item(id, name, desc))
        true
    }.getOrDefault(false)

    fun remove(c: Context, pkg: String) {
        val id = idOf(pkg)
        dir(c, id).deleteRecursively()
        save(c, list(c).filter { it.id != id })
    }

    /** A cover drawn from the name: a colour from it, its first letters big, the whole name under them. */
    private fun makeCover(c: Context, id: String, name: String) {
        val w = 600; val h = 900
        val hue = ((name.hashCode() and 0x7fffffff) % 360).toFloat()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = android.graphics.LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), Color.HSVToColor(floatArrayOf(hue, .55f, .55f)), Color.HSVToColor(floatArrayOf((hue + 40f) % 360f, .7f, .25f)), android.graphics.Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        val big = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 260f; isFakeBoldText = true; textAlign = Paint.Align.CENTER; alpha = 230 }
        val letters = name.split(' ').filter { it.isNotEmpty() }.take(2).joinToString("") { it.take(1).uppercase() }.ifEmpty { "H" }
        cv.drawText(letters, w / 2f, 420f, big)
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 54f; isFakeBoldText = true }
        val lay = StaticLayout.Builder.obtain(name, 0, name.length, tp, w - 100).setAlignment(Layout.Alignment.ALIGN_CENTER).setMaxLines(3).setEllipsize(android.text.TextUtils.TruncateAt.END).build()
        cv.save(); cv.translate(50f, 540f); lay.draw(cv); cv.restore()
        File(dir(c, id), "cover.jpg").outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        bmp.recycle()
    }

    fun open(c: Context, pkg: String) {
        val id = idOf(pkg)
        if (!File(dir(c, id), "index.html").isFile) return
        runCatching { c.startActivity(Intent(c, HtmlGameActivity::class.java).putExtra("id", id).apply { if (c !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }) }
    }
}

/** One game of your own, full screen. Its files are served from its folder under an address of its own; Back leaves it. */
class HtmlGameActivity : Activity() {
    private var web: WebView? = null
    private val suffix = ".hgame.pulse.local"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val id = intent.getStringExtra("id")?.takeIf { it.all { ch -> ch.isLetterOrDigit() } } ?: run { finish(); return }
        val base = HtmlGames.dir(this, id)
        val w = WebView(this)
        web = w
        w.setBackgroundColor(Color.BLACK)
        w.settings.javaScriptEnabled = true
        w.settings.domStorageEnabled = true
        w.settings.mediaPlaybackRequiresUserGesture = false
        w.settings.textZoom = 100
        w.settings.allowFileAccess = false
        w.isLongClickable = false
        w.setOnLongClickListener { true }
        w.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val u = request.url
                val host = u.host ?: return null
                if (!host.endsWith(suffix)) return null
                if (host != id + suffix) return WebResourceResponse("text/plain", "utf-8", 404, "Not found", emptyMap(), java.io.ByteArrayInputStream(ByteArray(0)))
                val rel = (u.path ?: "/").trimStart('/').ifEmpty { "index.html" }
                val f = File(base, rel)
                if (!f.canonicalPath.startsWith(base.canonicalPath + File.separator) || !f.isFile) return WebResourceResponse("text/plain", "utf-8", 404, "Not found", emptyMap(), java.io.ByteArrayInputStream(ByteArray(0)))
                val ext = f.extension.lowercase()
                val mime = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: when (ext) { "js", "mjs" -> "application/javascript"; "wasm" -> "application/wasm"; "json" -> "application/json"; else -> "application/octet-stream" }
                return WebResourceResponse(mime, if (mime.startsWith("text/") || mime.contains("javascript") || mime.contains("json")) "utf-8" else null, f.inputStream())
            }
        }
        setContentView(w, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        immersive()
        w.loadUrl("https://$id$suffix/index.html")
    }

    private fun immersive() {
        runCatching {
            val c = androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
            c.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            c.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) { super.onWindowFocusChanged(hasFocus); if (hasFocus) immersive() }
    override fun onPause() { super.onPause(); web?.onPause() }
    override fun onResume() { super.onResume(); web?.onResume() }
    override fun onDestroy() { web?.let { (it.parent as? ViewGroup)?.removeView(it); it.destroy() }; web = null; super.onDestroy() }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { finish() }
}
