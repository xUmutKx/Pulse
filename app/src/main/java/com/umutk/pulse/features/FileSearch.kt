package com.umutk.pulse.features

import android.content.Context
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Searching the names of the whole phone for the page of its own in Files. The Rust library does the walk when it is in the APK; without it the same walk is done by Kotlin threads
 * (slower, still several folders at a time). The searches typed are kept as a history (the last 30).
 */
object FileSearch {
    /** The files and folders under [root] whose name contains [q]; folders first, then by name. */
    fun find(root: File, q: String, hidden: Boolean, limit: Int = 400): List<File> {
        val needle = q.trim()
        if (needle.length < 2) return emptyList()
        val raw: List<File> = if (FileSearchNative.AVAILABLE) runCatching { FileSearchNative.search(root.path, needle, hidden, limit).map { File(it) } }.getOrNull() ?: walk(root, needle, hidden, limit)
        else walk(root, needle, hidden, limit)
        return raw.sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() }))
    }

    /** A folder's entry with what the sort needs, read once. */
    class Entry(val file: File, val dir: Boolean, val size: Long, val modified: Long)

    /** The entries of [dir] read in one native pass (no stat for each file from Kotlin); null when the library is not in the APK, then the caller reads the folder itself. */
    fun list(dir: File, hidden: Boolean): List<Entry>? {
        if (!FileSearchNative.AVAILABLE) return null
        return runCatching {
            FileSearchNative.list(dir.path, hidden).mapNotNull { row ->
                val p = row.split('\u0001')
                if (p.size < 4) null else Entry(File(dir, p[0]), p[1] == "1", p[2].toLongOrNull() ?: 0L, p[3].toLongOrNull() ?: 0L)
            }
        }.getOrNull()
    }

    private fun fold(s: String): String = buildString { for (c in s) append(when (c) { 'İ', 'ı', 'I' -> 'i'; else -> c.lowercaseChar() }) }

    /** The Kotlin walk: four threads take folders from a shared queue. */
    private fun walk(root: File, q: String, hidden: Boolean, limit: Int): List<File> {
        val needle = fold(q)
        val queue = ConcurrentLinkedQueue<File>().apply { add(root) }
        val pending = AtomicInteger(1)
        val stop = AtomicBoolean(false)
        val hits = java.util.Collections.synchronizedList(ArrayList<File>())
        val ts = (1..4).map {
            Thread {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
                while (!stop.get()) {
                    val d = queue.poll()
                    if (d == null) { if (pending.get() == 0) break; Thread.sleep(2); continue }
                    d.listFiles()?.forEach { f ->
                        if (hidden || !f.name.startsWith(".")) {
                            if (fold(f.name).contains(needle)) { hits += f; if (hits.size >= limit) stop.set(true) }
                            if (f.isDirectory && !(d == root && f.name == "Android") && !java.nio.file.Files.isSymbolicLink(f.toPath())) { pending.incrementAndGet(); queue.add(f) }
                        }
                    }
                    pending.decrementAndGet()
                }
            }.also { it.start() }
        }
        ts.forEach { it.join() }
        return hits.take(limit)
    }

    private fun sp(c: Context) = c.getSharedPreferences("files_search", Context.MODE_PRIVATE)

    /** The searches typed before, newest first. */
    fun history(c: Context): List<String> = sp(c).getString("h", "").orEmpty().split('\n').filter { it.isNotBlank() }

    fun remember(c: Context, q: String) {
        val t = q.trim(); if (t.length < 2) return
        sp(c).edit().putString("h", (listOf(t) + history(c).filter { !it.equals(t, true) }).take(30).joinToString("\n")).apply()
    }

    fun forget(c: Context, q: String? = null) {
        sp(c).edit().putString("h", if (q == null) "" else history(c).filter { it != q }.joinToString("\n")).apply()
    }
}
