package com.umutk.pulse.extras.keyboard

import android.content.Context
import androidx.compose.ui.text.font.FontFamily

/** The person's own font for the key labels (a .ttf or .otf picked in Settings > Keyboard, kept as kb_font in the keyboard's files). */
object KbFont {
    var family: FontFamily? = null; private set
    fun load(c: Context) {
        val f = java.io.File(c.filesDir, "kb_font")
        family = if (f.isFile) runCatching { FontFamily(android.graphics.Typeface.createFromFile(f)) }.getOrNull() else null
    }
    /** Copies [uri] in as the font (true when it reads as one). */
    fun pick(c: Context, uri: android.net.Uri): Boolean = runCatching {
        val f = java.io.File(c.filesDir, "kb_font")
        c.contentResolver.openInputStream(uri)!!.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
        android.graphics.Typeface.createFromFile(f); load(c); true
    }.getOrElse { java.io.File(c.filesDir, "kb_font").delete(); load(c); false }
    fun clear(c: Context) { java.io.File(c.filesDir, "kb_font").delete(); load(c) }
}
