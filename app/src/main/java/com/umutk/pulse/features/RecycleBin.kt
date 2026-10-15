package com.umutk.pulse.features

import android.os.Environment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PButton
import com.umutk.pulse.PCard
import com.umutk.pulse.PHead
import com.umutk.pulse.PIcon
import com.umutk.pulse.T
import java.io.File

/**
 * The Recycle Bin: deleted files are moved into .pulse_trash at the storage root (when the option is on),
 * and each one's original place is kept in .origins so it can be put back.
 */
object RecycleBin {
    fun dir(): File = File(Environment.getExternalStorageDirectory(), ".pulse_trash")
    private fun origins() = File(dir(), ".origins")

    /** Original path of every trashed file: trash name to where it came from. */
    fun map(): Map<String, String> = runCatching {
        origins().readLines().mapNotNull { line -> line.split('\t', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
    }.getOrDefault(emptyMap())

    /** Records where a trashed file came from. */
    fun remember(trashed: File, original: File) {
        runCatching { dir().mkdirs(); origins().appendText("${trashed.name}\t${original.absolutePath}\n") }
    }

    fun items(): List<File> = dir().listFiles()?.filter { it.name != ".origins" }?.sortedByDescending { it.lastModified() }.orEmpty()

    /** Puts a trashed file back where it was; falls back to Downloads/Pulse/restored when that place is taken or gone. */
    fun restore(f: File): String {
        val was = map()[f.name]?.let { File(it) }
        val target = if (was != null && !was.exists()) was else File(Environment.getDownloadCacheDirectory().parentFile ?: dir(), "restored/${f.name.substringAfter('-').substringAfter('-')}")
        target.parentFile?.mkdirs()
        val ok = f.renameTo(target)
        if (ok) forget(f.name)
        return if (ok) "Restored ${target.name}" else "Could not restore ${f.name}"
    }

    /** Deletes a trashed file for good and drops its record. */
    fun deleteForever(f: File) { f.deleteRecursively(); forget(f.name) }

    fun empty() { items().forEach { it.deleteRecursively() }; origins().delete() }

    private fun forget(name: String) {
        val left = map().filterKeys { it != name }
        runCatching { origins().writeText(left.entries.joinToString("") { "${it.key}\t${it.value}\n" }) }
    }
}

/** The Recycle Bin page: what was deleted, with Restore, Delete for good and Empty. */
@Composable
fun RecycleBinPage() {
    var refresh by remember { mutableStateOf(0) }
    var note by remember { mutableStateOf("") }
    val files = remember(refresh) { RecycleBin.items() }
    val origin = remember(refresh) { RecycleBin.map() }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PHead("Recycle bin", "${files.size} items · deleted files wait here when the option is on", guide = "recycle")
        Row(verticalAlignment = Alignment.CenterVertically) {
            PButton("Empty bin", danger = true) { RecycleBin.empty(); note = "Bin emptied"; refresh++ }
        }
        if (note.isNotEmpty()) Text(note, color = T.sub, fontSize = 12.sp)
        if (files.isEmpty()) Text("The bin is empty.", color = T.sub, fontSize = 13.sp)
        files.forEach { f ->
            PCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PIcon("delete", T.sub, 20.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(f.name.substringAfter('-').substringAfter('-'), color = T.text, fontSize = 14.sp, maxLines = 1)
                        Text(origin[f.name] ?: "unknown place", color = T.sub, fontSize = 11.sp, maxLines = 1)
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    PButton("Restore") { note = RecycleBin.restore(f); refresh++ }
                    Spacer(Modifier.width(8.dp))
                    PButton("Delete", danger = true) { RecycleBin.deleteForever(f); refresh++ }
                }
            }
        }
    }
}
