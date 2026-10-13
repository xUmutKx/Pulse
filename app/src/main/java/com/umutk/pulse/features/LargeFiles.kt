package com.umutk.pulse.features

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import com.umutk.pulse.WinAlert
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.text.DateFormat
import java.util.Date
import java.util.Locale

data class LargeFile(val file: File, val size: Long, val modified: Long)

private const val MAX_RESULTS = 500

// Android/data and Android/obb are app-private trees; deleting from them breaks apps, so they are never walked.
internal fun isSkippedDir(dir: File): Boolean {
    val parent = dir.parentFile ?: return false
    return parent.name == "Android" && (dir.name == "data" || dir.name == "obb")
}

/** Walks [root] iteratively and returns files at least [minBytes] big, biggest first, capped at [MAX_RESULTS]. */
suspend fun scanLargeFiles(root: File, minBytes: Long): List<LargeFile> {
    val found = ArrayList<LargeFile>()
    val stack = ArrayDeque<File>()
    stack.addLast(root)
    while (stack.isNotEmpty() && currentCoroutineContext().isActive) {
        val dir = stack.removeLast()
        // listFiles returns null for unreadable dirs; symlinks are skipped to avoid loops
        val children = dir.listFiles() ?: continue
        for (child in children) {
            if (Files.isSymbolicLink(child.toPath())) continue
            if (child.isDirectory) {
                if (!isSkippedDir(child)) stack.addLast(child)
            } else if (child.isFile && child.length() >= minBytes) {
                found.add(LargeFile(child, child.length(), child.lastModified()))
            }
        }
    }
    return found.sortedByDescending { it.size }.take(MAX_RESULTS)
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> String.format(Locale.US, "%.1f GB", bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> String.format(Locale.US, "%.1f MB", bytes / (1L shl 20).toDouble())
    else -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
}

private val dateFormat: DateFormat by lazy {
    DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault())
}

@Composable
fun LargeFilesScreen(root: File, onBack: () -> Unit) {
    var folder by remember { mutableStateOf(root) }
    var minBytes by remember { mutableStateOf(50L shl 20) }
    var results by remember { mutableStateOf<List<LargeFile>?>(null) }
    var subfolders by remember { mutableStateOf<List<File>>(emptyList()) }
    var selected by remember { mutableStateOf(setOf<File>()) }
    var reloadTick by remember { mutableStateOf(0) }
    var confirmDelete by remember { mutableStateOf(false) }
    var deleteFailed by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(folder, minBytes, reloadTick) {
        results = null
        selected = emptySet()
        deleteFailed = 0
        subfolders = withContext(Dispatchers.IO) {
            folder.listFiles()?.filter { it.isDirectory && !Files.isSymbolicLink(it.toPath()) && !isSkippedDir(it) }
                ?.sortedBy { it.name.lowercase() } ?: emptyList()
        }
        results = withContext(Dispatchers.IO) { scanLargeFiles(folder, minBytes) }
    }

    val chosen = results?.filter { it.file in selected } ?: emptyList()
    val chosenBytes = chosen.sumOf { it.size }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            }
            Text("Large files", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = { reloadTick++ }) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
            }
        }

        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(50L shl 20 to "50 MB", 200L shl 20 to "200 MB", 1L shl 30 to "1 GB").forEach { (bytes, label) ->
                FilterChip(
                    selected = minBytes == bytes,
                    onClick = { minBytes = bytes },
                    label = { Text(label) }
                )
            }
        }

        Text(
            folder.absolutePath,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
        )
        if (folder != root) {
            Text(
                "..  (up)",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { folder = folder.parentFile ?: root }.padding(vertical = 4.dp)
            )
        }

        LazyColumn(Modifier.weight(1f)) {
            items(subfolders, key = { it.path }) { dir ->
                Row(
                    Modifier.fillMaxWidth().clickable { folder = dir }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Folder, contentDescription = null, modifier = Modifier.padding(end = 12.dp))
                    Text(dir.name)
                }
            }
            item { HorizontalDivider() }
            when (val list = results) {
                null -> item { Text("Scanning...", modifier = Modifier.padding(12.dp)) }
                else -> {
                    if (list.isEmpty()) {
                        item { Text("No files over this size here (or no access).", modifier = Modifier.padding(12.dp)) }
                    }
                    items(list, key = { it.file.path }) { entry ->
                        val isOn = entry.file in selected
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { selected = if (isOn) selected - entry.file else selected + entry.file }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = isOn, onCheckedChange = null)
                            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                                Text(entry.file.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "${entry.file.parentFile?.absolutePath ?: ""} | ${dateFormat.format(Date(entry.modified))}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Text(formatSize(entry.size), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }

        if (deleteFailed > 0) {
            Text("$deleteFailed could not be deleted.", color = MaterialTheme.colorScheme.error)
        }
        if (selected.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    "${selected.size} selected (${formatSize(chosenBytes)})",
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (confirmDelete) {
        WinAlert(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${selected.size} file(s)?") },
            text = { Text("${formatSize(chosenBytes)} will be permanently deleted. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    val toDelete = chosen.map { it.file }
                    scope.launch {
                        val deleted = withContext(Dispatchers.IO) { toDelete.filter { it.delete() }.toSet() }
                        results = results?.filter { it.file !in deleted }
                        selected = selected - deleted
                        deleteFailed = toDelete.size - deleted.size
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}
