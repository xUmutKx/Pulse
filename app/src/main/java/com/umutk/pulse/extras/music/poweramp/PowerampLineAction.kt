package com.umutk.pulse.extras.music.poweramp

import android.content.Context
import com.umutk.pulse.tr

/** What a long press on the artist / album line of the Poweramp now-playing page does. */
enum class LineChoice(val key: String, val en: String, val tur: String) {
    NONE("none", "Nothing", "Hiçbiri"),
    LYRICS("lyrics", "Lyrics", "Şarkı sözleri"),
    EQUALIZER("equalizer", "Equalizer", "Ekolayzır"),
    SLEEP("sleep", "Sleep timer", "Uyku zamanlayıcı");

    fun label(): String = tr(en, tur)
}

/** Saved choice for the long press on the artist / album line. Default: nothing, so no action is taken unless picked. */
object PowerampLineAction {
    private fun sp(c: Context) = c.getSharedPreferences("poweramp_line", Context.MODE_PRIVATE)
    fun longPress(c: Context): LineChoice {
        val key = sp(c).getString("longPress", LineChoice.NONE.key)
        return LineChoice.entries.firstOrNull { it.key == key } ?: LineChoice.NONE
    }
    fun setLongPress(c: Context, choice: LineChoice) = sp(c).edit().putString("longPress", choice.key).apply()
}
