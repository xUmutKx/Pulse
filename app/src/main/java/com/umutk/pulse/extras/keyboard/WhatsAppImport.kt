package com.umutk.pulse.extras.keyboard

import android.content.Context
import android.net.Uri
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * "Learn from a WhatsApp chat": the exported chat (the .txt, or the .zip WhatsApp makes) is read on the phone, one person's lines are picked, and the words and
 * the word pairs of those lines are learned like typed text. Nothing leaves the phone and the chat itself is not kept.
 */
object WhatsAppImport {
    // "9/7/24, 16:22 - Name: text" (Android) and "[9/7/24, 16:22:10] Name: text" (iPhone)
    private val head = Regex("""^[‎‏]?\[?\d{1,4}[./]\d{1,2}[./]\d{1,4},?\s+\d{1,2}[:.]\d{2}(?:[:.]\d{2})?(?:\s?[APap][Mm])?\]?\s*(?:[-–]\s*)?([^:]{1,60}?):\s(.*)$""")
    private val skip = Regex("""(?i)^(<media omitted>|<medya dahil edilmedi>|null|this message was deleted|bu mesaj silindi|you deleted this message|<attached:.*>)$""")

    /** Sender -> its messages, in order. */
    fun read(c: Context, uri: Uri): Map<String, List<String>> {
        val out = LinkedHashMap<String, MutableList<String>>()
        c.contentResolver.openInputStream(uri)?.buffered()?.use { raw ->
            raw.mark(4)
            val a = ByteArray(2); val n = raw.read(a); raw.reset()
            val ins: InputStream = if (n == 2 && a[0] == 'P'.code.toByte() && a[1] == 'K'.code.toByte()) {
                val z = ZipInputStream(raw)
                var e = z.nextEntry
                while (e != null && !e.name.endsWith(".txt")) e = z.nextEntry
                if (e == null) return emptyMap()
                z
            } else raw
            var who: String? = null; val cur = StringBuilder()
            fun flush() { val w = who; val t = cur.toString().trim(); if (w != null && t.isNotEmpty() && !skip.matches(t)) out.getOrPut(w) { ArrayList() } += t; cur.setLength(0) }
            ins.bufferedReader(Charsets.UTF_8).forEachLine { line ->
                val m = head.find(line)
                if (m != null) { flush(); who = m.groupValues[1].trim(); cur.append(m.groupValues[2]) }
                else if (who != null) { if (cur.isNotEmpty()) cur.append('\n'); cur.append(line) }   // a message of several lines
            }
            flush()
        }
        return out
    }

    /** Learns the lines (links and numbers are left out); returns how many lines were used. */
    fun learn(c: Context, lines: List<String>): Int {
        var n = 0
        for (l in lines) {
            val t = l.replace(Regex("https?://\\S+"), " ").replace(Regex("\\S+@\\S+"), " ")
            if (t.length < 3) continue
            KeyboardDictionary.learnText(c, t); n++
        }
        KeyboardDictionary.saveUser(c, true)
        return n
    }
}
