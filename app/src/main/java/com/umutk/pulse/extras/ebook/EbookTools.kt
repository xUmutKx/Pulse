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
    Triple("rotate", tr("Screen Orientation", "Ekran yönü"), "orient"),
    Triple("light", tr("Day/Night Mode", "Gündüz/Gece"), "daynight"),
    Triple("speak", tr("Speak", "Sesli oku"), "speak"),
    Triple("sort", tr("Autoscroll", "Otomatik kaydır"), "auto"),
    Triple("details", tr("Chapters", "Bölümler"), "chapters"),
    Triple("flash", tr("Brightness", "Parlaklık"), "brightness"),
    Triple("text", tr("Font Size", "Yazı boyutu"), "text"),
    Triple("search", tr("Search", "Ara"), "search"),
    Triple("view", tr("Visual Options", "Görünüm seçenekleri"), "visual"),
    Triple("level", tr("Control Options", "Denetim seçenekleri"), "control"),
    Triple("palette", tr("Themes", "Temalar"), "theme"),
    Triple("tune", tr("Options", "Seçenekler"), "options"),
    Triple("more", tr("Customize reader bar buttons", "Okuma çubuğu düğmeleri"), "custom"),
    Triple("info", tr("Book information", "Kitap bilgisi"), "info"),
)

/** The bottom bar at first: the buttons Moon Reader shows checked. */
private const val DEFAULT_BAR = "orient,daynight,speak,auto,chapters,text,custom"

/** The button that opens the bar customisation; it stays last in the bar. */
private const val PINNED = "custom"

object EbookPrefs {
    private fun p(c: Context) = c.getSharedPreferences("ebook_reader", Context.MODE_PRIVATE)

    /** The buttons of the bottom bar, as they were chosen; version 2 brings the default of Moon Reader's list. */
    fun bar(c: Context): List<String> {
        val pr = p(c)
        if (pr.getInt("barVer", 1) < 2) pr.edit().putString("bar", DEFAULT_BAR).putInt("barVer", 2).apply()
        // version 3 puts the font size button on the bar (it was only in the ⋮ menu)
        if (pr.getInt("barVer", 1) < 3) {
            val ids = (pr.getString("bar", DEFAULT_BAR) ?: "").split(',').filter { it.isNotBlank() }
            pr.edit().putString("bar", (ids + "text").distinct().joinToString(",")).putInt("barVer", 3).apply()
        }
        // ids of buttons that were removed from the list are dropped
        return pinned((pr.getString("bar", DEFAULT_BAR) ?: "").split(',').filter { id -> BAR_TOOLS.any { it.third == id } })
    }
    fun setBar(c: Context, ids: List<String>) { p(c).edit().putString("bar", pinned(ids).joinToString(",")).apply() }

    /** The customise button is always in the bar and always the last one. */
    fun pinned(ids: List<String>): List<String> = ids.filter { it != PINNED } + PINNED

    /** 0 serif, 1 sans-serif, 2 monospace. */
    fun font(c: Context): Int = p(c).getInt("font", 0)
    fun setFont(c: Context, v: Int) { p(c).edit().putInt("font", v).apply() }

    /** The e-ink look: black on white, no page animation. */
    fun eink(c: Context): Boolean = p(c).getBoolean("eink", false)
    fun setEink(c: Context, on: Boolean) { p(c).edit().putBoolean("eink", on).apply() }

    /** The shelf stands on a wooden background. */
    fun wood(c: Context): Boolean = p(c).getBoolean("wood", true)
    fun setWood(c: Context, on: Boolean) { p(c).edit().putBoolean("wood", on).apply() }

    /** The dark themes use pure black. */
    fun amoled(c: Context): Boolean = p(c).getBoolean("amoled", false)
    fun setAmoled(c: Context, on: Boolean) { p(c).edit().putBoolean("amoled", on).apply() }

    /** The reader picks the day or the night theme by the time of day when the book opens. */
    fun autoTheme(c: Context): Boolean = p(c).getBoolean("autoTheme", false)
    fun setAutoTheme(c: Context, on: Boolean) { p(c).edit().putBoolean("autoTheme", on).apply() }

    /** Two rows for the reader's bottom bar. */
    fun double(c: Context): Boolean = p(c).getBoolean("double", false)
    fun setDouble(c: Context, on: Boolean) { p(c).edit().putBoolean("double", on).apply() }

    /** The cover width of the shelf, in dp (the height is 1.4 times it). */
    fun cover(c: Context): Int = p(c).getInt("cover", 84)
    fun setCover(c: Context, v: Int) { p(c).edit().putInt("cover", v.coerceIn(60, 130)).apply() }

    /** Books in a row on the shelf: 0 fits as many as the width allows (more in landscape), else the number chosen. */
    fun perRow(c: Context): Int = p(c).getInt("perRow", 0)
    fun setPerRow(c: Context, v: Int) { p(c).edit().putInt("perRow", v).apply() }

    /** The reading progress shown on the covers of the shelf. */
    fun showProgress(c: Context): Boolean = p(c).getBoolean("showProgress", true)
    fun setShowProgress(c: Context, on: Boolean) { p(c).edit().putBoolean("showProgress", on).apply() }

    /** The page turn: 0 instant (the default), 1 slide, 2 scroll down like a printed page feeding, 3 flip (a small grow and fade). */
    fun animStyle(c: Context): Int = p(c).getInt("animStyle", 0)
    fun setAnimStyle(c: Context, v: Int) { p(c).edit().putInt("animStyle", v.coerceIn(0, 3)).apply() }

    /** Seconds per page while the autoscroll runs. */
    fun autoSeconds(c: Context): Int = p(c).getInt("auto", 10)
    fun setAutoSeconds(c: Context, v: Int) { p(c).edit().putInt("auto", v.coerceIn(3, 120)).apply() }

    /** The reading speed of the voice: 1 is the engine's normal speed; the default is a little slower than that. */
    fun speechRate(c: Context): Float = p(c).getFloat("speechRate", 0.85f)
    fun setSpeechRate(c: Context, v: Float) { p(c).edit().putFloat("speechRate", v.coerceIn(0.5f, 1.6f)).apply() }
}

/** Reads a page aloud with the system's speech engine; [onDone] runs on the main thread when the page is read. */
class Speaker(c: Context, private val onDone: () -> Unit) {
    private val app = c.applicationContext
    private var ready = false
    private var tts: TextToSpeech? = null
    /** The part of the text being read now: the start and the end, as characters of the text given to [speak]. */
    var onRange: ((Int, Int) -> Unit)? = null

    init {
        // the Samsung speech engine when the phone has it, else the phone's own
        val listener = TextToSpeech.OnInitListener { status -> ready = status == TextToSpeech.SUCCESS }
        val samsung = listOf("com.samsung.SMT").firstOrNull { pkg -> runCatching { c.packageManager.getPackageInfo(pkg, 0) }.isSuccess }
        tts = if (samsung != null) TextToSpeech(c.applicationContext, listener, samsung) else TextToSpeech(c.applicationContext, listener)
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { Handler(Looper.getMainLooper()).post(onDone) }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {}
            @Suppress("OVERRIDE_DEPRECATION")
            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                Handler(Looper.getMainLooper()).post { onRange?.invoke(start, end) }
            }
        })
    }

    /** Turkish when the text has Turkish letters, else the phone's language. */
    private fun localeOf(text: String): Locale =
        if (text.any { it in "ğĞşŞıİ" }) Locale("tr", "TR") else Locale.getDefault()

    /** The best voice the engine has for [loc] that works without the net: the highest quality one. */
    private fun pickVoice(t: TextToSpeech, loc: Locale) {
        val best = runCatching { t.voices }.getOrNull()
            ?.filter { it.locale.language == loc.language && !it.isNetworkConnectionRequired }
            ?.maxByOrNull { it.quality }
        if (best != null) runCatching { t.voice = best }
    }

    fun speak(text: String) {
        if (!ready || text.isBlank()) return
        val t = tts ?: return
        val loc = localeOf(text)
        t.language = loc
        pickVoice(t, loc)
        // the speed chosen in the reader (the default is a little slower than the engine's own)
        t.setSpeechRate(EbookPrefs.speechRate(app))
        t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "page")
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
