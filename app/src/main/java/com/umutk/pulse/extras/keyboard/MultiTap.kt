package com.umutk.pulse.extras.keyboard

/**
 * The phone keypad (multi-tap): the digits 1-9 and 0 each carry a few letters, as on an old phone; pressing the same key again within a moment
 * replaces the letter just typed with the next one on that key. Off by default (Settings > Swipes and keys).
 */
object MultiTap {
    private const val WINDOW_MS = 900L

    /** The keys in rows: what each key cycles through (its digit last), for [lang]. */
    fun rows(lang: String): List<List<String>> {
        val tr = lang.startsWith("tr")
        return listOf(
            listOf(".,?!'1", if (tr) "abcç2" else "abc2", "def3"),
            listOf(if (tr) "ghğıi4" else "ghi4", "jkl5", if (tr) "mnoö6" else "mno6"),
            listOf(if (tr) "pqrsş7" else "pqrs7", if (tr) "tuüv8" else "tuv8", "wxyz9"),
        )
    }

    private var lastKey: String? = null
    private var lastAt = 0L
    private var idx = 0

    /** A press of the key cycling through [chars]: what to type, and whether the letter typed before is to be replaced by it. */
    fun press(chars: String): Pair<String, Boolean> {
        val now = android.os.SystemClock.uptimeMillis()
        val again = chars == lastKey && now - lastAt < WINDOW_MS
        idx = if (again) (idx + 1) % chars.length else 0
        lastKey = chars; lastAt = now
        return chars[idx].toString() to again
    }

    /** Any other key ends the cycle: the next press of the same key starts a new letter. */
    fun reset() { lastKey = null }
}
