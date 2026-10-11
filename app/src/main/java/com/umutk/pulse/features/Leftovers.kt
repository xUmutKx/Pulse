package com.umutk.pulse.features

import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val thumbFolders = listOf(".thumbnails", "DCIM/.thumbnails", "Pictures/.thumbnails", ".tmp")

/** Thumbnail and temp folders that exist on the shared storage, with their total size. */
private fun thumbAndTemp(): List<Junk> {
    val root = Environment.getExternalStorageDirectory()
    return thumbFolders.map { File(root, it) }.filter { it.isDirectory }
        .map { d -> Junk(d, d.walkTopDown().filter { it.isFile }.sumOf { it.length() }, "thumbnail/temp folder") }
}

// Title to scanner. APKs and empty folders reuse Cleaner; only thumbnails are new.
private val groups: List<Pair<String, () -> List<Junk>>> = listOf(
    "APK files" to { Cleaner.apks() },
    "Empty folders" to { Cleaner.emptyDirs() },
    "Thumbnail and temp folders" to { thumbAndTemp() },
)

/** Lists leftover files per group. Nothing is deleted until the user confirms. */
@Composable
fun LeftoversScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var lists by remember { mutableStateOf<Map<String, List<Junk>>>(emptyMap()) }
    var chosen by remember { mutableStateOf<Map<String, Set<File>>>(emptyMap()) }
    var scanning by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<String?>(null) }
    var msg by remember { mutableStateOf("") }

    fun scan() {
        scanning = true
        scope.launch {
            lists = withContext(Dispatchers.IO) {
                groups.associate { (title, f) -> title to runCatching { f() }.getOrDefault(emptyList()) }
            }
            chosen = emptyMap()
            scanning = false
        }
    }
    LaunchedEffect(Unit) { scan() }
    BackHandler { onBack() }

    fun toggle(title: String, file: File) {
        val cur = chosen[title].orEmpty()
        chosen = chosen + (title to if (file in cur) cur - file else cur + file)
    }
    fun toggleAll(title: String) {
        val all = lists[title].orEmpty().map { it.file }.toSet()
        val cur = chosen[title].orEmpty()
        chosen = chosen + (title to if (cur.size == all.size) emptySet() else all)
    }
    fun removeChosen(title: String) {
        val picked = chosen[title].orEmpty()
        val items = lists[title].orEmpty().filter { it.file in picked }
        scope.launch {
            val done = withContext(Dispatchers.IO) { items.filter { runCatching { it.file.deleteRecursively() }.getOrDefault(false) } }
            lists = lists + (title to lists[title].orEmpty().filter { it !in done })
            chosen = chosen + (title to emptySet())
            msg = "Removed ${done.size} of ${items.size} from $title"
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PButton("Back", onClick = onBack)
            Spacer(Modifier.width(8.dp))
            Text("Leftovers", color = Look.accent, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            PButton("Rescan", onClick = { if (!scanning) scan() })
        }
        if (scanning) Text("Scanning…", color = T.sub, fontSize = 12.sp)
        if (msg.isNotEmpty()) Text(msg, color = Look.accent, fontSize = 12.sp)

        groups.forEach { (title, _) ->
            val items = lists[title].orEmpty()
            val picked = chosen[title].orEmpty()
            PCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = Look.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text("${items.size} · ${Guard.gb(items.sumOf { it.size })}", color = T.sub, fontSize = 13.sp)
                }
                if (items.isEmpty()) {
                    Center("Nothing found")
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        PButton("Select all", onClick = { toggleAll(title) })
                        if (picked.isNotEmpty()) PButton("Delete ${picked.size}", danger = true, onClick = { pending = title })
                    }
                    items.forEach { j ->
                        Row(Modifier.fillMaxWidth().clickable { toggle(title, j.file) }, verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = j.file in picked, onCheckedChange = null)
                            Column(Modifier.weight(1f)) {
                                Text(j.file.path.removePrefix(Environment.getExternalStorageDirectory().path), color = T.text, fontSize = 12.sp, maxLines = 1)
                                Text(Guard.gb(j.size), color = T.sub, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    pending?.let { title ->
        val picked = chosen[title].orEmpty()
        val size = lists[title].orEmpty().filter { it.file in picked }.sumOf { it.size }
        AlertDialog(onDismissRequest = { pending = null },
            title = { Text("Remove from $title?") },
            text = { Text("${picked.size} items, ${Guard.gb(size)}. This cannot be undone.") },
            confirmButton = { TextButton({ pending = null; removeChosen(title) }) { Text("Remove") } },
            dismissButton = { TextButton({ pending = null }) { Text("Cancel") } })
    }
}
