package com.umutk.pulse.extras.keyboard

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * The person's own letter layouts: a name, the language it types (its word list, corrections and capitals stay that language's) and the rows of letters.
 * One of them can be switched on per language; "Standard" is the built-in layout. They are written to, and read from, a small text file:
 *
 *     # pulse-layout
 *     name: AZERTY
 *     base: en
 *     rows:
 *     azertyuiop
 *     qsdfghjklm
 *     wxcvbn
 *
 * (a key is one character; spaces in a row are ignored; a file with only the rows works too).
 */
object KbCustomLayouts {
    class Layout(val id: String, val name: String, val base: String, val rows: List<String>)

    private const val FILE = "pulse_kb_layouts"
    private var cache: List<Layout>? = null
    private var active: Map<String, String> = emptyMap()

    private fun p(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun all(c: Context): List<Layout> {
        cache?.let { return it }
        val out = ArrayList<Layout>()
        runCatching {
            val a = JSONArray(p(c).getString("list", "[]"))
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                out += Layout(o.getString("id"), o.getString("name"), o.getString("base"), o.getJSONArray("rows").let { r -> List(r.length()) { r.getString(it) } })
            }
        }
        val act = HashMap<String, String>()
        runCatching { val o = JSONObject(p(c).getString("active", "{}")); o.keys().forEach { act[it] = o.getString(it) } }
        active = act
        cache = out
        return out
    }

    private fun write(c: Context, list: List<Layout>, act: Map<String, String>) {
        val a = JSONArray(); list.forEach { l -> a.put(JSONObject().put("id", l.id).put("name", l.name).put("base", l.base).put("rows", JSONArray(l.rows))) }
        p(c).edit().putString("list", a.toString()).putString("active", JSONObject(act).toString()).apply()
        cache = list; active = act
    }

    /** The rows now in use for a language (a layout of the person's switched on for it), or null for the built-in ones. */
    fun rowsFor(code: String): List<String>? {
        val id = active[code] ?: return null
        return cache?.firstOrNull { it.id == id && it.base == code }?.rows
    }

    fun activeId(c: Context, base: String): String? { all(c); return active[base] }

    fun use(c: Context, base: String, id: String?) {
        val act = HashMap(active.also { all(c) }.let { active })
        if (id == null) act.remove(base) else act[base] = id
        write(c, all(c), act)
        KeyboardPrefs.layoutRev++
    }

    fun save(c: Context, id: String?, name: String, base: String, rows: List<String>, switchOn: Boolean = true): Layout {
        val list = all(c).toMutableList()
        val clean = rows.map { it.trim() }.filter { it.isNotEmpty() }.take(5)
        val l = Layout(id ?: "l" + System.currentTimeMillis(), name.trim().ifEmpty { "Layout" }, base, clean)
        val i = list.indexOfFirst { it.id == l.id }
        if (i >= 0) list[i] = l else list += l
        val act = HashMap(active)
        if (switchOn) act[base] = l.id
        write(c, list, act)
        KeyboardPrefs.layoutRev++
        return l
    }

    fun delete(c: Context, id: String) {
        val list = all(c).filter { it.id != id }
        write(c, list, active.filterValues { it != id })
        KeyboardPrefs.layoutRev++
    }

    /** The keys of a row as written: each character that is not a space. */
    fun keysOf(row: String): List<String> = row.filter { !it.isWhitespace() }.map { it.toString() }

    fun export(l: Layout): String = "# pulse-layout\nname: ${l.name}\nbase: ${l.base}\nrows:\n" + l.rows.joinToString("\n")

    /** Reads a layout file (see above). Returns null when there is no letter row in it. */
    fun parse(text: String, fallbackName: String): Layout? {
        var name = fallbackName; var base = "en"; var inRows = false
        val rows = ArrayList<String>()
        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            if (!inRows && line.startsWith("name:", true)) { name = line.substringAfter(':').trim().ifEmpty { name }; continue }
            if (!inRows && line.startsWith("base:", true)) { base = line.substringAfter(':').trim().lowercase().takeIf { it in KeyboardLayouts.languages } ?: "en"; continue }
            if (!inRows && line.equals("rows:", true)) { inRows = true; continue }
            rows += line
        }
        val clean = rows.take(5)
        if (clean.isEmpty() || clean.any { keysOf(it).size > 14 }) return null
        return Layout("l" + System.currentTimeMillis(), name, base, clean)
    }
}
