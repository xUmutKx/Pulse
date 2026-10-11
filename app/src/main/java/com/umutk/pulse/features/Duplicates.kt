package com.umutk.pulse.features

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest

private const val PARTIAL_BYTES = 64L * 1024

/** Files with identical [size] and identical content. [wasted] is what deleting the extras frees. */
data class DupGroup(val size: Long, val files: List<File>) {
    val wasted: Long get() = size * (files.size - 1)
}

data class ScanProgress(val stage: String, val done: Int, val total: Int)

/**
 * Stages: size (free, from file length) -> MD5 of first 64 KB -> full MD5.
 * Each stage only sees files the previous one could not rule out.
 * Runs on Dispatchers.IO and stops when the calling coroutine is cancelled.
 */
suspend fun findDuplicates(root: File, onProgress: (ScanProgress) -> Unit): List<DupGroup> =
    withContext(Dispatchers.IO) {
        onProgress(ScanProgress("Listing files", 0, 0))
        val bySize = root.walkTopDown()
            .filter { it.isFile && it.length() > 0 && !Files.isSymbolicLink(it.toPath()) }
            .groupBy { it.length() }
            .filterValues { it.size > 1 }
        val sizeCandidates = bySize.values.flatten()

        val partialGroups = refine(sizeCandidates, PARTIAL_BYTES, "Hashing first 64 KB", onProgress)
        val fullCandidates = partialGroups.flatMap { it.files }

        refine(fullCandidates, Long.MAX_VALUE, "Hashing full files", onProgress)
            .sortedByDescending { it.wasted }
    }

/** Groups [files] by (size, MD5 of the first [limit] bytes) and keeps groups with 2+ members. */
private suspend fun refine(
    files: List<File>,
    limit: Long,
    stage: String,
    onProgress: (ScanProgress) -> Unit,
): List<DupGroup> {
    val byKey = HashMap<Pair<Long, String>, MutableList<File>>()
    files.forEachIndexed { i, f ->
        currentCoroutineContext().ensureActive()
        onProgress(ScanProgress(stage, i, files.size))
        val digest = md5(f, limit) ?: return@forEachIndexed
        byKey.getOrPut(f.length() to digest) { mutableListOf() }.add(f)
    }
    return byKey.filterValues { it.size > 1 }
        .map { (key, list) -> DupGroup(key.first, list.sortedBy { it.path }) }
}

private fun md5(file: File, limit: Long): String? = try {
    val md = MessageDigest.getInstance("MD5")
    val buf = ByteArray(64 * 1024)
    file.inputStream().use { input ->
        var left = limit
        while (left > 0) {
            val n = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
            if (n < 0) break
            md.update(buf, 0, n)
            left -= n
        }
    }
    md.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
} catch (e: IOException) {
    null // unreadable file: leave it out of the groups
}

/** Deletes [files] and returns the ones that were actually removed. */
private suspend fun deleteFiles(files: List<File>): List<File> =
    withContext(Dispatchers.IO) { files.filter { it.delete() } }

private fun formatSize(b: Long): String = when {
    b >= 1L shl 30 -> "%.1f GB".format(b / 1073741824.0)
    b >= 1L shl 20 -> "%.1f MB".format(b / 1048576.0)
    b >= 1024 -> "%.1f KB".format(b / 1024.0)
    else -> "$b B"
}

@Composable
fun DuplicatesScreen(root: File, onBack: () -> Unit) {
    var groups by remember(root) { mutableStateOf<List<DupGroup>?>(null) }
    var progress by remember(root) { mutableStateOf(ScanProgress("Starting", 0, 0)) }
    val keep = remember(root) { mutableStateMapOf<DupGroup, File>() }
    var pending by remember(root) { mutableStateOf<DupGroup?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(root) {
        groups = findDuplicates(root) { progress = it }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Duplicates", style = MaterialTheme.typography.titleLarge)
        }
        Text(
            root.path,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        val current = groups
        when {
            current == null -> {
                Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    if (progress.total > 0) {
                        LinearProgressIndicator(
                            progress = { progress.done.toFloat() / progress.total },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Text(
                        "${progress.stage}${if (progress.total > 0) " ${progress.done}/${progress.total}" else ""}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            current.isEmpty() -> Text(
                "No duplicates found",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 16.dp),
            )
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(current, key = { g -> g.files.joinToString("|") { it.path } }) { g ->
                    val keepFile = keep[g] ?: g.files.first()
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                "${formatSize(g.size)} each, ${formatSize(g.wasted)} wasted",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            g.files.forEach { f ->
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(
                                        selected = f == keepFile,
                                        onClick = { keep[g] = f },
                                    )
                                    Column(Modifier.weight(1f)) {
                                        Text(f.name, style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            f.parent ?: "",
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                IconButton(onClick = { pending = g }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete the other copies")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pending?.let { g ->
        val keepFile = keep[g] ?: g.files.first()
        val victims = g.files.filter { it != keepFile }
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Delete ${victims.size} file(s)?") },
            text = { Text("Keeping ${keepFile.path}. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    scope.launch {
                        val deleted = deleteFiles(victims)
                        val left = g.files.filter { it !in deleted }
                        groups = groups?.mapNotNull { old ->
                            when {
                                old != g -> old
                                left.size < 2 -> null
                                else -> DupGroup(g.size, left).also { keep[it] = keepFile }
                            }
                        }
                        keep.remove(g)
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } },
        )
    }
}
