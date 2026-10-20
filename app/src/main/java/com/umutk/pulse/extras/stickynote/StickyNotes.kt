package com.umutk.pulse.extras.stickynote

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** One sticky note: its text and an index into [StickyStore.colors]. */
data class StickyNote(val id: String, val text: String, val color: Int)

/**
 * Sticky notes in their own prefs file ("sticky_notes"), kept as one JSON array. The widget keeps one note per widget id.
 * A list that cannot be read is kept under "notes_bad" before the next save replaces it.
 */
object StickyStore {
    /** Windows 7 sticky note colours: yellow, blue, green, pink, grey (ARGB). */
    val colors = longArrayOf(0xFFFFF59DL, 0xFF9ED8FFL, 0xFFB9F6A7L, 0xFFFFB3D9L, 0xFFD9D9D9L)

    fun argb(color: Int): Int = colors[color.coerceIn(0, colors.lastIndex)].toInt()

    private fun prefs(c: Context) = c.getSharedPreferences("sticky_notes", Context.MODE_PRIVATE)

    fun all(c: Context): List<StickyNote> {
        val raw = prefs(c).getString("notes", "[]").orEmpty()
        return runCatching {
            val a = JSONArray(raw)
            List(a.length()) { i -> a.getJSONObject(i).let { StickyNote(it.getString("id"), it.getString("text"), it.getInt("color")) } }
        }.getOrElse {
            prefs(c).edit().putString("notes_bad", raw).putString("notes", "[]").apply()
            emptyList()
        }
    }

    private fun save(c: Context, list: List<StickyNote>) {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("id", it.id).put("text", it.text).put("color", it.color)) }
        prefs(c).edit().putString("notes", a.toString()).apply()
    }

    fun put(c: Context, n: StickyNote) {
        val list = all(c)
        save(c, if (list.any { it.id == n.id }) list.map { if (it.id == n.id) n else it } else list + n)
    }

    fun create(c: Context, color: Int = 0): StickyNote = StickyNote(UUID.randomUUID().toString(), "", color).also { put(c, it) }
    fun get(c: Context, id: String?): StickyNote? = all(c).firstOrNull { it.id == id }
    fun latest(c: Context): StickyNote? = all(c).lastOrNull()

    fun noteFor(c: Context, widgetId: Int): StickyNote? = get(c, prefs(c).getString("w$widgetId", null))
    fun bind(c: Context, widgetId: Int, noteId: String) { prefs(c).edit().putString("w$widgetId", noteId).apply() }
    fun unbind(c: Context, widgetId: Int) { prefs(c).edit().remove("w$widgetId").apply() }
}
