package com.umutk.pulse.extras.notes

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/**
 * One note. [trashedAt] > 0 means it sits in the trash; [files] are attachment names in the attachment folder
 * (`im_` image, `sk_` sketch, `au_` audio); [remindAt] > 0 is a pending reminder.
 */
data class Note(
    val id: String, val title: String, val body: String, val updated: Long,
    val created: Long = updated, val pinned: Boolean = false, val color: Int = 0, val folder: String = "",
    val trashedAt: Long = 0L, val locked: Boolean = false, val remindAt: Long = 0L, val files: List<String> = emptyList(),
)

/** Notes in one JSON file in the app's files folder. A write goes to a temp file first, so a crash keeps the old list. */
object NotesStore {
    /** Trashed notes are removed for good after this many days. */
    const val TRASH_DAYS = 30L

    private fun file(c: Context) = File(c.filesDir, "notes/notes.json")
    /** Folder of the attachments (images, sketches, audio). */
    fun attachments(c: Context) = File(c.filesDir, "notes/att").apply { mkdirs() }

    fun load(c: Context): List<Note> {
        val f = file(c)
        if (!f.exists()) return emptyList()
        return runCatching {
            val raw = f.readBytes()
            val text = if (NotesPrefs.encrypted(c)) String(NotesCrypto.open(raw), Charsets.UTF_8) else String(raw, Charsets.UTF_8)
            parse(JSONArray(text))
        }.getOrElse {
            // unreadable file: keep it aside instead of letting the next save overwrite it
            f.renameTo(File(f.path + ".bad"))
            emptyList()
        }
    }

    fun save(c: Context, notes: List<Note>) {
        val f = file(c)
        f.parentFile?.mkdirs()
        val bytes = JSONArray().also { arr -> notes.forEach { arr.put(toJson(it)) } }.toString().toByteArray(Charsets.UTF_8)
        val tmp = File(f.path + ".tmp")
        tmp.writeBytes(if (NotesPrefs.encrypted(c)) NotesCrypto.seal(bytes) else bytes)
        tmp.renameTo(f)
        NotesWidget.refresh(c)
    }

    private fun toJson(n: Note): JSONObject = JSONObject()
        .put("id", n.id).put("title", n.title).put("body", n.body).put("updated", n.updated).put("created", n.created)
        .put("pinned", n.pinned).put("color", n.color).put("folder", n.folder).put("trashedAt", n.trashedAt)
        .put("locked", n.locked).put("remindAt", n.remindAt).put("files", JSONArray(n.files))

    private fun parse(arr: JSONArray): List<Note> = (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        val files = o.optJSONArray("files")?.let { fa -> (0 until fa.length()).map { fa.getString(it) } }.orEmpty()
        Note(
            o.getString("id"), o.optString("title"), o.optString("body"), o.optLong("updated"), o.optLong("created", o.optLong("updated")),
            o.optBoolean("pinned"), o.optInt("color"), o.optString("folder"), o.optLong("trashedAt"), o.optBoolean("locked"),
            o.optLong("remindAt"), files,
        )
    }

    /** Export of the text of every note (attachments stay on the phone). */
    fun exportJson(notes: List<Note>): String = JSONObject().put("app", "pulse-notes").put("version", 2)
        .put("notes", JSONArray().also { arr -> notes.forEach { arr.put(toJson(it).put("files", JSONArray())) } }).toString(2)

    /** Notes from an export; every note gets a new id, so importing twice adds copies instead of replacing. */
    fun importJson(text: String): List<Note> {
        val arr = JSONObject(text).getJSONArray("notes")
        return parse(arr).map { it.copy(id = UUID.randomUUID().toString(), files = emptyList()) }
    }

    /** Removes trashed notes older than [TRASH_DAYS] and their attachment files. */
    fun purge(c: Context, notes: List<Note>): List<Note> {
        val limit = System.currentTimeMillis() - TRASH_DAYS * 86_400_000L
        val (gone, keep) = notes.partition { it.trashedAt in 1 until limit }
        gone.forEach { n -> n.files.forEach { File(attachments(c), it).delete() } }
        return keep
    }

    /** Search over title, body, folder and tags, ignoring case; a blank query matches everything. */
    fun matches(n: Note, q: String) = q.isBlank() || n.title.contains(q, true) || n.body.contains(q, true) || n.folder.contains(q, true)

    /** The #tags in a body, lower case, without the #. */
    fun tagsOf(body: String): Set<String> = TAG.findAll(body).map { it.value.substring(1).lowercase() }.toSet()

    /** The [[note title]] links in a body. */
    fun linksOf(body: String): List<String> = LINK.findAll(body).map { it.groupValues[1].trim() }.filter { it.isNotEmpty() }.toList()

    /** The text shown for a note with no title: its first non-blank line. */
    fun titleOf(n: Note): String = n.title.ifBlank { n.body.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty() }

    private val TAG = Regex("#[\\p{L}\\p{N}_]+")
    private val LINK = Regex("\\[\\[([^\\]]+)]]")
}
