package com.umutk.pulse.extras.notes

import android.content.Context
import java.io.File
import java.util.UUID

/**
 * Folders of the phone linked to Notes: every .md / .txt file in them (and in the folders below, three deep) is a note of the same name, kept in that file.
 * Editing the note writes the file; a file changed outside Notes updates its note the next time the list opens. Picked with Pulse's own file picker.
 */
object NotesLink {
    private val EXT = setOf("md", "txt", "markdown")
    private fun sp(c: Context) = c.getSharedPreferences("notes_prefs", Context.MODE_PRIVATE)

    fun linked(c: Context): Set<String> = sp(c).getStringSet("linked_folders", emptySet()).orEmpty()
    fun add(c: Context, dir: String) { sp(c).edit().putStringSet("linked_folders", linked(c) + dir).apply() }
    fun remove(c: Context, dir: String) { sp(c).edit().putStringSet("linked_folders", linked(c) - dir).apply() }

    private fun walk(root: File, depth: Int = 3): List<File> {
        if (depth < 0) return emptyList()
        val kids = root.listFiles().orEmpty().filter { !it.name.startsWith(".") }
        return kids.filter { it.isFile && it.extension.lowercase() in EXT } + kids.filter { it.isDirectory }.flatMap { walk(it, depth - 1) }
    }

    /** [notes] with the linked folders read in: new files become notes, changed files update theirs. Returns the same list when nothing changed. */
    fun sync(c: Context, notes: List<Note>): List<Note> {
        val dirs = linked(c).map { File(it) }.filter { it.isDirectory }
        if (dirs.isEmpty()) return notes
        val out = notes.toMutableList()
        var changed = false
        for (dir in dirs) for (f in walk(dir)) {
            val i = out.indexOfFirst { it.path == f.path }
            val text = runCatching { f.readText() }.getOrNull() ?: continue
            if (i >= 0) {
                val n = out[i]
                if (f.lastModified() > n.updated + 1500 && text != n.body) { out[i] = n.copy(body = text, updated = f.lastModified()); changed = true }
            } else {
                val rel = f.parentFile?.relativeTo(dir)?.path.orEmpty()
                val folder = if (rel.isBlank()) dir.name else dir.name + "/" + rel
                out += Note(UUID.randomUUID().toString(), f.nameWithoutExtension, text, f.lastModified(), f.lastModified(), folder = folder, path = f.path)
                changed = true
            }
        }
        return if (changed) out else notes
    }

    /** Writes the note's text back to its file (a note that is not from a linked folder is left alone). */
    fun write(n: Note) { if (n.path.isNotBlank()) runCatching { File(n.path).writeText(n.body) } }
}
