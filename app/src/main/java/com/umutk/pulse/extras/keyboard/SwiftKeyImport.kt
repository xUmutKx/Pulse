package com.umutk.pulse.extras.keyboard

import android.content.Context
import com.umutk.pulse.Shell
import java.io.File

/**
 * Takes the words SwiftKey learned from you: with root, its personal language model files (dynamic.lm and friends in its data folder) are copied and read.
 * SwiftKey's model is a binary format that is not documented, so the words are picked out of it as text (every run of letters that looks like a word),
 * without their counts: each becomes a known word of Pulse's keyboard.
 */
object SwiftKeyImport {
    var lastError = ""
    class Result(val words: Int, val files: Int)

    private val LATIN = "çğıöşüÇĞİÖŞÜâîûêôéèàáíóúñäëïü'"
    private fun latin(c: Char) = c in 'a'..'z' || c in 'A'..'Z' || c in LATIN

    /** Null with [lastError] set when there is no root or no SwiftKey data; else how many words were taken. Blocking: call off the main thread. */
    fun run(ctx: Context): Result? {
        lastError = ""
        val pkgs = listOf("com.touchtype.swiftkey", "com.touchtype.swiftkey.beta")
        val why = StringBuilder()
        val seen = Shell.rootSee(pkgs.flatMap { listOf("/data/data/$it", "/data/user/0/$it") }, why)
        if (seen == null) { lastError = why.toString().ifBlank { "root refused or SwiftKey is not installed" }; return null }
        val (mode, root) = seen
        val found = Shell.runRoot("find $root -type f \\( -name '*dynamic*' -o -name 'merge.lm' -o -name 'user*.lm' -o -name '*.lm' \\) -size +1k 2>/dev/null", mode)
            .lines().map { it.trim() }.filter { it.startsWith("/") && !it.contains("/cache/") }
        if (found.isEmpty()) { lastError = "no personal language model in $root"; return null }
        val work = File(ctx.cacheDir, "sk_import").apply { deleteRecursively(); mkdirs() }
        Shell.runRoot("chmod 777 '${work.path}'", mode)
        val counts = HashMap<String, Int>()
        var files = 0
        found.forEachIndexed { i, p ->
            // the bundled language packs are big and are not yours: only the files of the user's own model are read (dynamic ones, and merge copies)
            val own = p.contains("dynamic", true) || p.endsWith("merge.lm") || p.contains("/user", true)
            if (!own) return@forEachIndexed
            val out = File(work, "m$i.lm")
            Shell.runRoot("cp '$p' '${out.path}' && chmod 666 '${out.path}'", mode)
            if (!out.exists() || out.length() == 0L) return@forEachIndexed
            files++
            val bytes = out.readBytes()
            tokens(String(bytes, Charsets.UTF_8)).forEach { counts[it] = (counts[it] ?: 0) + 1 }
            tokens(String(bytes, Charsets.UTF_16LE)).forEach { counts[it] = (counts[it] ?: 0) + 1 }
            out.delete()
        }
        work.deleteRecursively()
        if (files == 0) { lastError = "found ${found.size} model files, none readable"; return null }
        val words = counts.keys.filter { good(it) }
        if (words.isEmpty()) { lastError = "read $files files, no words in them"; return null }
        KeyboardDictionary.importUser(ctx, words)
        return Result(words.size, files)
    }

    private fun tokens(s: String): List<String> {
        val out = ArrayList<String>(); val sb = StringBuilder()
        for (c in s) { if (latin(c)) sb.append(c) else { if (sb.length in 2..24) out += sb.toString(); sb.setLength(0) } }
        if (sb.length in 2..24) out += sb.toString()
        return out
    }

    /** A token that reads like a word: it has a vowel, no letter four times in a row, and is not made of capitals only. */
    private fun good(w: String): Boolean {
        val l = w.lowercase()
        if (l.none { it in "aeıioöuüâîûéèàáíóúyäëï" }) return false
        if (Regex("(.)\\1{3}").containsMatchIn(l)) return false
        if (w.length > 3 && w == w.uppercase()) return false
        return l.trim('\'').length >= 2
    }
}
