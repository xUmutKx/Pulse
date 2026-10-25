package com.umutk.pulse.extras.keyboard

import android.content.Context
import android.graphics.Paint

/** Skin tones of the emoji that have them: a long press in the emoji pages shows the five tones, the chosen one is kept for that emoji. */
object EmojiTone {
    val mods = listOf("🏻", "🏼", "🏽", "🏾", "🏿")
    private val paint = Paint()
    private val ok = HashMap<String, Boolean>()

    /** [e] with tone [i] (0..4) put right after its first character. */
    fun toned(e: String, i: Int): String {
        if (e.isEmpty()) return e
        val first = String(Character.toChars(e.codePointAt(0)))
        return first + mods[i.coerceIn(0, 4)] + e.substring(first.length).removePrefix("️")
    }

    /** Whether the phone's emoji font has tone variants of [e]. */
    fun supports(e: String): Boolean = synchronized(ok) {
        ok.getOrPut(e) {
            android.os.Build.VERSION.SDK_INT >= 23 && e.isNotEmpty() && e.codePointAt(0) > 0x2000 && paint.hasGlyph(toned(e, 2)) && toned(e, 2) != e
        }
    }

    private fun sp(c: Context) = c.getSharedPreferences("pulse_kb_tone", Context.MODE_PRIVATE)

    /** The emoji as it is to be shown and sent: with the tone chosen for it before, if any. */
    fun shown(c: Context, e: String): String {
        if (!supports(e)) return e
        val i = sp(c).getInt(e, -1)
        return if (i in 0..4) toned(e, i) else e
    }

    fun choose(c: Context, base: String, i: Int) { sp(c).edit().apply { if (i in 0..4) putInt(base, i) else remove(base) }.apply() }
}
