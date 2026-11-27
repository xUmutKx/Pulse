package com.umutk.pulse.extras.keyboard

import android.content.Context
import android.net.Uri

/**
 * A personal dictionary from another keyboard (as WM Keyboard imports them, MIT): Gboard's export (a zip with dictionary.txt: word, shortcut, language), a word list
 * (one word on a line, from HeliBoard, FUTO, SwiftKey or anywhere), or a CSV (word,shortcut). The words join the learned words; a shortcut becomes a Pulse shortcut
 * (typing it offers its word or phrase).
 */
object KbDictImport {
    class Result(val words: Int, val shortcuts: Int)

    fun import(c: Context, uri: Uri): Result? = runCatching {
        val name = c.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { k -> if (k.moveToFirst()) k.getString(0) else null }.orEmpty()
        val bytes = c.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        val texts = if (bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) {
            val out = ArrayList<String>()
            java.util.zip.ZipInputStream(bytes.inputStream()).use { z ->
                var e = z.nextEntry
                while (e != null) {
                    if (!e.isDirectory && e.name.substringAfterLast('.').lowercase() in setOf("txt", "csv", "tsv", "dic")) out += String(z.readBytes(), Charsets.UTF_8)
                    e = z.nextEntry
                }
            }
            out
        } else listOf(String(bytes, Charsets.UTF_8))
        var words = 0; var shortcuts = 0
        val list = ArrayList<String>()
        for (t in texts) for (raw in t.lines()) {
            val line = raw.trim().removePrefix("\uFEFF")
            if (line.isEmpty() || line.startsWith("#")) continue
            val parts = if ('\t' in line) line.split('\t') else if (name.endsWith(".csv", true) || line.count { it == ',' } == 1) line.split(',') else listOf(line)
            val word = parts[0].trim().trim('"')
            val short = parts.getOrNull(1)?.trim()?.trim('"').orEmpty()
            if (word.isEmpty()) continue
            if (short.isNotEmpty() && short.none { it.isWhitespace() }) { KeyShortcuts.put(c, short, word); shortcuts++ }
            if (word.length in 2..40 && word.none { it.isWhitespace() || it.isDigit() }) { list += word; words++ }
        }
        KeyboardDictionary.importUser(c, list)
        Result(words, shortcuts)
    }.getOrNull()
}
