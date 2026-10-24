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

    private val wordRe = Regex("[\\p{L}]+(?:['’][\\p{L}]+)?")

    /** The spellings within one slip (a letter more, less or other) of [w]: the word itself and each way of leaving one letter out. */
    private fun variants(w: String): List<String> = ArrayList<String>(w.length + 1).also { l -> l += w; for (i in w.indices) l += w.removeRange(i, i + 1) }

    /**
     * Learns the lines (links and e-mail addresses left out); returns how many lines were used. A chat holds the person's slips as well as their words, and a slip
     * repeated a hundred times would count as a word: a word that is not in a word list and has a spelling one slip away that was written many times more often is taken
     * for a typo of that one. It is learned as a typo (so it is corrected next time) and counted as the right word in the pairs.
     */
    fun learn(c: Context, lines: List<String>): Int {
        val clean = lines.map { it.replace(Regex("https?://\\S+"), " ").replace(Regex("\\S+@\\S+"), " ") }.filter { it.length >= 3 }
        val count = HashMap<String, Int>()
        for (l in clean) for (m in wordRe.findAll(l.lowercase(java.util.Locale.ROOT))) count.merge(m.value.trim('\'', '’'), 1, Int::plus)
        val near = HashMap<String, MutableList<String>>()
        for (w in count.keys) for (v in variants(w)) near.getOrPut(v) { ArrayList(3) } += w
        val typo = HashMap<String, String>()
        for ((w, n) in count) {
            if (w.length < 4 || n > 40 || KeyboardDictionary.isKnown(w)) continue
            val best = variants(w).flatMap { near[it].orEmpty() }.filter { it != w && (it.length >= 4) && (count[it] ?: 0) >= 8 * n }.maxByOrNull { count[it] ?: 0 } ?: continue
            if (best != w) typo[w] = best
        }
        typo.forEach { (w, v) -> KeyboardDictionary.learnTypo(c, w, v) }
        val fix = Regex("[\\p{L}]+(?:['’][\\p{L}]+)?")
        var n = 0
        for (l in clean) {
            val t = if (typo.isEmpty()) l else fix.replace(l) { m -> typo[m.value.lowercase(java.util.Locale.ROOT)] ?: m.value }
            KeyboardDictionary.learnText(c, t); n++
        }
        KeyboardDictionary.saveUser(c, true)
        return n
    }
}
