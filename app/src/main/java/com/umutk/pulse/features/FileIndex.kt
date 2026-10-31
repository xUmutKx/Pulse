package com.umutk.pulse.features

import java.io.File

/**
 * Every file and folder name on the phone, kept in memory, so a search over the whole phone answers at once.
 * It is read again when it is older than five minutes; the first search reads it.
 */
object FileIndex {
    @Volatile private var files: List<File> = emptyList()
    @Volatile private var builtAt = 0L
    private const val MAX_AGE = 5 * 60_000L
    private const val MAX_FILES = 400_000

    fun isStale(): Boolean = System.currentTimeMillis() - builtAt > MAX_AGE

    /** Walks [root] and keeps what it finds. Android's own folder (app data, mostly unreadable) is left out. */
    fun build(root: File) {
        val out = ArrayList<File>()
        val stack = ArrayDeque<File>()
        stack.add(root)
        while (stack.isNotEmpty() && out.size < MAX_FILES) {
            val dir = stack.removeLast()
            val kids = dir.listFiles() ?: continue
            for (f in kids) {
                out += f
                if (f.isDirectory && !(dir == root && f.name == "Android")) stack.add(f)
            }
        }
        files = out
        builtAt = System.currentTimeMillis()
    }

    /** The first [limit] names that contain [q] (case-insensitive); hidden names only when [hidden] is on. */
    fun search(q: String, hidden: Boolean, limit: Int = 300): List<File> {
        val needle = q.trim()
        if (needle.isEmpty()) return emptyList()
        return files.asSequence()
            .filter { hidden || !it.name.startsWith(".") }
            .filter { it.name.contains(needle, ignoreCase = true) }
            .take(limit).toList()
    }
}
