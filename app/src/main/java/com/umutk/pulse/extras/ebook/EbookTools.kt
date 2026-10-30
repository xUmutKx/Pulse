package com.umutk.pulse.extras.ebook

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.umutk.pulse.tr
import java.util.Locale

/*
 * The reader's tools: the choice of the bottom bar, the font, the e-ink look, the autoscroll speed, reading aloud,
 * the screen brightness and the search in the book. Plain Android, nothing bundled.
 */

/** Every tool the bottom bar can hold, with its icon and its name. */
val BAR_TOOLS = listOf(
    Triple("details", tr("Chapters", "Bölümler"), "chapters"),
    Triple("text", tr("Text size", "Yazı boyutu"), "text"),
    Triple("palette", tr("Theme", "Tema"), "theme"),
    Triple("pin", tr("Bookmarks", "Yer imleri"), "bookmarks"),
    Triple("search", tr("Search", "Ara"), "search"),
    Triple("speak", tr("Read aloud", "Sesli oku"), "speak"),
    Triple("sort", tr("Autoscroll", "Otomatik kaydır"), "auto"),
    Triple("tune", tr("Options", "Seçenekler"), "options"),
    Triple("info", tr("Book information", "Kitap bilgisi"), "info"),
)

object EbookPrefs {
    private fun p(c: Context) = c.getSharedPreferences("ebook_reader", Context.MODE_PRIVATE)

    fun bar(c: Context): List<String> = (p(c).getString("bar", "chapters,text,theme,bookmarks,info") ?: "").split(',').filter { it.isNotBlank() }
    fun setBar(c: Context, ids: List<String>) { p(c).edit().putString("bar", ids.joinToString(",")).apply() }

    /** 0 serif, 1 sans-serif, 2 monospace. */
    fun font(c: Context): Int = p(c).getInt("font", 0)
    fun setFont(c: Context, v: Int) { p(c).edit().putInt("font", v).apply() }

    /** The e-ink look: black on white, no page animation. */
    fun eink(c: Context): Boolean = p(c).getBoolean("eink", false)
    fun setEink(c: Context, on: Boolean) { p(c).edit().putBoolean("eink", on).apply() }

    /** The shelf stands on a wooden background. */
    fun wood(c: Context): Boolean = p(c).getBoolean("wood", true)
    fun setWood(c: Context, on: Boolean) { p(c).edit().putBoolean("wood", on).apply() }

    /** Two rows for the reader's bottom bar. */
    fun double(c: Context): Boolean = p(c).getBoolean("double", false)
    fun setDouble(c: Context, on: Boolean) { p(c).edit().putBoolean("double", on).apply() }

    /** The cover width of the shelf, in dp (the height is 1.4 times it). */
    fun cover(c: Context): Int = p(c).getInt("cover", 84)
    fun setCover(c: Context, v: Int) { p(c).edit().putInt("cover", v.coerceIn(60, 130)).apply() }

    /** The reading progress shown on the covers of the shelf. */
    fun showProgress(c: Context): Boolean = p(c).getBoolean("showProgress", true)
    fun setShowProgress(c: Context, on: Boolean) { p(c).edit().putBoolean("showProgress", on).apply() }

    /** Whether the page turns with its slide and fade. */
    fun anim(c: Context): Boolean = p(c).getBoolean("anim", true)
    fun setAnim(c: Context, on: Boolean) { p(c).edit().putBoolean("anim", on).apply() }

    /** Seconds per page while the autoscroll runs. */
    fun autoSeconds(c: Context): Int = p(c).getInt("auto", 10)
    fun setAutoSeconds(c: Context, v: Int) { p(c).edit().putInt("auto", v.coerceIn(3, 120)).apply() }
}

/** Reads a page aloud with the system's speech engine; [onDone] runs on the main thread when the page is read. */
class Speaker(c: Context, private val onDone: () -> Unit) {
    private var ready = false
    private var tts: TextToSpeech? = null

    init {
        tts = TextToSpeech(c.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) tts?.language = Locale.getDefault()
        }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { Handler(Looper.getMainLooper()).post(onDone) }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {}
        })
    }

    fun speak(text: String) {
        if (!ready || text.isBlank()) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "page")
    }

    fun stop() { tts?.stop() }

    fun shutdown() { tts?.stop(); tts?.shutdown() }
}

/** Sets the brightness of the window the reader is in (0..1); a negative value gives the system's own brightness back. */
fun setBrightness(c: Context, v: Float) {
    var ctx: Context? = c
    while (ctx != null && ctx !is Activity) ctx = (ctx as? ContextWrapper)?.baseContext
    val w = (ctx as? Activity)?.window ?: return
    val lp = w.attributes
    lp.screenBrightness = if (v < 0f) android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE else v.coerceIn(0.01f, 1f)
    w.attributes = lp
}

/** The chapters whose text holds [q] (case-insensitive), with the first paragraph that does and a short piece of it around the match. */
fun searchBook(chapters: List<Chapter>, q: String): List<Pair<Int, String>> {
    if (q.isBlank()) return emptyList()
    val out = ArrayList<Pair<Int, String>>()
    chapters.forEachIndexed { i, ch ->
        val p = ch.paras.firstOrNull { it.text.contains(q, ignoreCase = true) } ?: return@forEachIndexed
        val at = p.text.indexOf(q, ignoreCase = true)
        val from = (at - 30).coerceAtLeast(0)
        val to = (at + q.length + 50).coerceAtMost(p.text.length)
        out += i to (if (from > 0) "…" else "") + p.text.substring(from, to) + (if (to < p.text.length) "…" else "")
        if (out.size >= 60) return out
    }
    return out
}
