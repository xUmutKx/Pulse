package com.umutk.pulse.extras.keyboard

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** One thing the clipboard held: a text, or a picture (kept as a file of the app); pinned ones stay until unpinned. */
class ClipItem(val id: Long, val text: String, val image: String?, val time: Long, val pinned: Boolean)

/**
 * Everything copied while the keyboard is the input method, kept on the phone: texts and pictures, newest first, pinned ones on top. Unpinned ones are
 * dropped after the time set in the keyboard settings (one hour to three days).
 */
object ClipStore {
    private const val MAX = 300
    private fun file(c: Context) = File(c.filesDir, "kbd_clips.json")
    private fun dir(c: Context) = File(c.filesDir, "clips")
    private var items = listOf<ClipItem>()
    private var loaded = false

    private fun read(c: Context) {
        if (loaded) return
        loaded = true
        runCatching {
            val a = JSONArray(file(c).readText())
            items = (0 until a.length()).map { i -> a.getJSONObject(i).let { o -> ClipItem(o.getLong("id"), o.optString("t"), o.optString("i").ifEmpty { null }, o.getLong("time"), o.optBoolean("pin")) } }
        }
    }

    private fun write(c: Context) {
        runCatching { file(c).writeText(JSONArray(items.map { JSONObject().put("id", it.id).put("t", it.text).put("i", it.image ?: "").put("time", it.time).put("pin", it.pinned) }).toString()) }
    }

    private fun sorted(l: List<ClipItem>) = l.sortedWith(compareByDescending<ClipItem> { it.pinned }.thenByDescending { it.time })

    /** The items now (old unpinned ones dropped first). */
    fun load(c: Context): List<ClipItem> {
        read(c)
        val limit = System.currentTimeMillis() - KeyboardPrefs.clipHours.coerceIn(1, 72) * 3_600_000L
        val keep = items.filter { it.pinned || it.time >= limit }
        if (keep.size != items.size) { drop(c, items - keep.toSet()); items = keep; write(c) }
        return sorted(items)
    }

    private fun drop(c: Context, gone: List<ClipItem>) { gone.forEach { g -> g.image?.let { File(it).delete() } } }

    fun addText(c: Context, t: String): List<ClipItem> {
        read(c)
        val text = t.trim()
        if (text.isEmpty() || text.length > 20000) return load(c)
        val same = items.firstOrNull { it.image == null && it.text == text }
        items = items.filter { it !== same } + ClipItem(System.currentTimeMillis(), text, null, System.currentTimeMillis(), same?.pinned == true)
        trim(c); write(c)
        return load(c)
    }

    /** A picture copied: its bytes are kept in the app's files (the source may go away). Blocking: call off the main thread. */
    fun addImage(c: Context, uri: Uri): List<ClipItem> {
        read(c)
        runCatching {
            dir(c).mkdirs()
            val now = System.currentTimeMillis()
            val f = File(dir(c), "$now.img")
            c.contentResolver.openInputStream(uri)?.use { i -> f.outputStream().use { o -> i.copyTo(o) } } ?: return load(c)
            if (f.length() == 0L || f.length() > 12_000_000L) { f.delete(); return load(c) }
            items = items + ClipItem(now, "", f.path, now, false)
            trim(c); write(c)
        }
        return load(c)
    }

    private fun trim(c: Context) {
        val un = items.filter { !it.pinned }.sortedByDescending { it.time }
        if (un.size > MAX) { val gone = un.drop(MAX); drop(c, gone); items = items - gone.toSet() }
    }

    fun togglePin(c: Context, id: Long): List<ClipItem> {
        read(c); items = items.map { if (it.id == id) ClipItem(it.id, it.text, it.image, it.time, !it.pinned) else it }; write(c); return load(c)
    }

    fun delete(c: Context, ids: Set<Long>): List<ClipItem> {
        read(c); val gone = items.filter { it.id in ids }; drop(c, gone); items = items - gone.toSet(); write(c); return load(c)
    }

    /** Everything that is not pinned. */
    fun clear(c: Context): List<ClipItem> {
        read(c); val gone = items.filter { !it.pinned }; drop(c, gone); items = items - gone.toSet(); write(c); return load(c)
    }
}
