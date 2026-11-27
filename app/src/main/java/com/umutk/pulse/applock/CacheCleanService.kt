package com.umutk.pulse.applock

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.umutk.pulse.tr

/**
 * The cache cleaner for a phone without root, the way SD Maid's app cleaner does it: for each app in the list the settings page of the app is opened, Storage is pressed and then Clear cache,
 * and the phone goes back. It works only while the cleaner runs ([running]); at any other time the service does nothing, and it listens to the settings apps only (see
 * accessibility_cachecleaner.xml). The labels are looked for in several languages and in the strings of the phone's own Settings app, so the buttons are found on the phone's language.
 */
class CacheCleanService : AccessibilityService() {
    companion object {
        @Volatile var instance: CacheCleanService? = null
        var running by mutableStateOf(false)
        var status by mutableStateOf("")
        var done by mutableIntStateOf(0)
        var total by mutableIntStateOf(0)
        var cleaned by mutableIntStateOf(0)

        fun enabled(): Boolean = instance != null

        /** Starts cleaning the caches of [pkgs] one after the other; false when the service is not switched on in the Accessibility settings. */
        fun start(pkgs: List<String>): Boolean { val s = instance ?: return false; s.begin(pkgs); return true }
        fun stop() { instance?.finish() }

        private val clearWords = listOf("clear cache", "önbelleği temizle", "önbelleği sil", "önbellek temizle", "cache leeren", "cache löschen", "vider le cache", "effacer le cache", "borrar caché", "borrar cache",
            "svuota cache", "limpar cache", "limpar a cache", "очистить кэш", "wis cache", "wyczyść pamięć podręczną", "ล้างแคช", "清除缓存", "清除快取", "キャッシュを削除", "캐시 삭제", "مسح ذاكرة التخزين المؤقت", "xóa bộ nhớ đệm", "hapus cache")
        private val storageWords = listOf("storage & cache", "storage", "depolama alanı", "depolama", "speicher", "stockage", "almacenamiento", "memoria", "armazenamento", "хранилище", "opslag", "pamięć", "存储", "儲存", "ストレージ", "저장공간", "التخزين", "bộ nhớ", "penyimpanan")
    }

    private val main = Handler(Looper.getMainLooper())
    private var queue = ArrayDeque<String>()
    private var current: String? = null
    private var step = 0
    private var tries = 0
    private var dynamicClear = emptyList<String>()
    private var dynamicStorage = emptyList<String>()
    private val tickR = Runnable { runCatching { tick() } }
    private val timeoutR = Runnable { skip(tr("no answer", "cevap yok")) }

    override fun onServiceConnected() { super.onServiceConnected(); instance = this }
    override fun onUnbind(intent: Intent?): Boolean { if (instance === this) instance = null; running = false; return super.onUnbind(intent) }
    override fun onInterrupt() {}

    /** Strings of the phone's own Settings app (in the phone's language): the real labels of the buttons. */
    private fun settingsStrings(vararg names: String): List<String> = runCatching {
        val r = packageManager.getResourcesForApplication("com.android.settings")
        names.mapNotNull { n -> r.getIdentifier(n, "string", "com.android.settings").takeIf { it != 0 }?.let { r.getString(it).trim() } }.filter { it.length >= 3 }
    }.getOrDefault(emptyList())

    private fun begin(pkgs: List<String>) {
        if (running) return
        queue = ArrayDeque(pkgs.distinct()); total = queue.size; done = 0; cleaned = 0; running = true
        dynamicClear = settingsStrings("clear_cache_btn_text", "clear_cache", "storage_clear_cache_btn_text").map { it.lowercase() }
        dynamicStorage = settingsStrings("storage_settings", "storage_settings_for_app", "storage_and_cache_title", "app_storage_settings").map { it.lowercase() }
        status = tr("Cleaning…", "Temizleniyor…")
        next()
    }

    fun finish() {
        main.removeCallbacks(tickR); main.removeCallbacks(timeoutR)
        val msg = tr("Caches cleared in $cleaned of $total apps", "$total uygulamanın $cleaned tanesinde önbellek temizlendi")
        running = false; current = null; queue.clear(); status = msg
        // back to Pulse
        runCatching { startActivity(Intent(this, com.umutk.pulse.FilesActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)) }
    }

    private fun next() {
        main.removeCallbacks(timeoutR)
        val pkg = queue.removeFirstOrNull() ?: return finish()
        current = pkg; step = 0; tries = 0
        status = tr("Cleaning ${done + 1} of $total: $pkg", "Temizleniyor ${done + 1} / $total: $pkg")
        val ok = runCatching { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)) }.isSuccess
        if (!ok) return skip(tr("cannot open", "açılamadı"))
        main.postDelayed(timeoutR, 10_000L)
        main.postDelayed(tickR, 900L)
    }

    private fun skip(why: String) { current?.let { android.util.Log.d("CacheClean", "skipped $it: $why") }; done++; leave() }

    /** Back out of the settings pages, then the next app. */
    private fun leave() {
        main.removeCallbacks(tickR); main.removeCallbacks(timeoutR)
        performGlobalAction(GLOBAL_ACTION_BACK)
        main.postDelayed({ performGlobalAction(GLOBAL_ACTION_BACK); main.postDelayed({ next() }, 450L) }, 450L)
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        if (!running || current == null) return
        main.removeCallbacks(tickR); main.postDelayed(tickR, 280L)
    }

    /** A node whose text or description holds one of [words]; for the dynamic labels an exact match is needed (they are short). */
    private fun find(root: AccessibilityNodeInfo, words: List<String>): AccessibilityNodeInfo? {
        if (words.isEmpty()) return null
        var hit: AccessibilityNodeInfo? = null
        fun walk(n: AccessibilityNodeInfo?, depth: Int) {
            if (n == null || hit != null || depth > 24) return
            val t = (n.text?.toString() ?: n.contentDescription?.toString() ?: "").trim().lowercase()
            if (t.isNotEmpty() && t.length <= 60 && words.any { w -> t == w || t.startsWith(w) || t.contains(w) }) { hit = n; return }
            for (i in 0 until n.childCount) walk(n.getChild(i), depth + 1)
        }
        walk(root, 0)
        return hit
    }

    private fun clickable(n: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var c = n; var up = 0
        while (c != null && up < 6) { if (c.isClickable) return c; c = c.parent; up++ }
        return null
    }

    private fun tick() {
        val pkg = current ?: return
        val root = rootInActiveWindow ?: return
        val clearBtn = find(root, dynamicClear + clearWords)
        if (clearBtn != null) {
            // the Clear cache button: pressed unless it is off (nothing to clear)
            val b = clickable(clearBtn)
            val usable = b != null && b.isEnabled && clearBtn.isEnabled
            if (usable) { b!!.performAction(AccessibilityNodeInfo.ACTION_CLICK); cleaned++ }
            done++
            main.postDelayed({ leave() }, 500L)
            main.removeCallbacks(timeoutR)
            return
        }
        if (step == 0) {
            val row = clickable(find(root, dynamicStorage + storageWords))
            if (row != null) { row.performAction(AccessibilityNodeInfo.ACTION_CLICK); step = 1; tries = 0; return }
        }
        // not there yet: the page may still be drawing, or the row is below the fold
        tries++
        if (tries in 3..5) root.findScrollable()?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        if (tries > 8) { skip(tr("button not found for $pkg", "$pkg için düğme bulunamadı")); return }
        main.postDelayed(tickR, 600L)
    }

    private fun AccessibilityNodeInfo.findScrollable(): AccessibilityNodeInfo? {
        if (isScrollable) return this
        for (i in 0 until childCount) getChild(i)?.findScrollable()?.let { return it }
        return null
    }
}
