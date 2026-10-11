package com.umutk.pulse.features

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.umutk.pulse.BarMeter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicLong

/** One child of the folder on screen, with the size of everything under it. */
private class TreeNode(val file: File, val size: Long)

private fun isLink(f: File): Boolean = Files.isSymbolicLink(f.toPath())

/**
 * Total size of [f]. Symlinks are never followed, so the walk stays inside the folder
 * the user picked. [counter] counts files as they are measured, for the progress line.
 */
private suspend fun sizeOf(f: File, counter: AtomicLong): Long {
    currentCoroutineContext().ensureActive()
    if (isLink(f)) return 0L
    if (f.isFile) {
        counter.incrementAndGet()
        return f.length()
    }
    if (!f.isDirectory) return 0L
    var total = 0L
    for (child in f.listFiles().orEmpty()) total += sizeOf(child, counter)
    return total
}

private fun formatSize(b: Long): String = when {
    b >= 1L shl 30 -> "%.1f GB".format(b / 1073741824.0)
    b >= 1L shl 20 -> "%.1f MB".format(b / 1048576.0)
    b >= 1024 -> "%.1f KB".format(b / 1024.0)
    else -> "$b B"
}

/**
 * TreeSize-style browser: shows the children of [root] (or the folder the user went into)
 * sorted by size, each with a bar showing its share of the folder total.
 * Tap a folder to go in; the back button goes up, and leaves the screen at [root].
 */
@Composable
fun FolderTreeScreen(root: File, onBack: () -> Unit) {
    var dir by remember(root) { mutableStateOf(root) }
    var rows by remember(root) { mutableStateOf<List<TreeNode>>(emptyList()) }
    var loading by remember(root) { mutableStateOf(true) }
    var scanned by remember(root) { mutableStateOf(0L) }

    LaunchedEffect(dir) {
        val target = dir
        loading = true
        rows = emptyList()
        scanned = 0L
        val counter = AtomicLong(0L)
        val ticker = launch {
            while (isActive) {
                scanned = counter.get()
                delay(150)
            }
        }
        val result = withContext(Dispatchers.IO) {
            target.listFiles().orEmpty()
                .filter { !isLink(it) }
                .map { TreeNode(it, sizeOf(it, counter)) }
                .sortedByDescending { it.size }
        }
        ticker.cancel()
        rows = result
        scanned = counter.get()
        loading = false
    }

    val atRoot = dir == root
    fun up() {
        if (atRoot) onBack() else dir = dir.parentFile ?: root
    }
    BackHandler { up() }

    val total = rows.sumOf { it.size }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { up() }) {
                Icon(Icons.Filled.ArrowBack, contentDescription = if (atRoot) "Back" else "Up")
            }
            Text("Folder tree", style = MaterialTheme.typography.titleLarge)
        }
        Text(
            dir.path,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Text(
            "Total ${formatSize(total)}",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 12.dp),
        )

        if (loading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            Text(
                "Measuring… ${scanned} files",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
        } else if (rows.isEmpty()) {
            Text(
                if (dir.isDirectory) "Empty folder" else "This folder cannot be read",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 16.dp),
            )
        }

        LazyColumn(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(rows, key = { it.file.path }) { node ->
                val isDir = node.file.isDirectory
                val share = if (total > 0) node.size.toFloat() / total else 0f
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = isDir) { dir = node.file }
                        .padding(vertical = 6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (isDir) Icons.Filled.Folder else Icons.Filled.InsertDriveFile,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            node.file.name,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(start = 8.dp),
                        )
                        Text(
                            "${formatSize(node.size)}  ${"%.1f".format(share * 100)}%",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    BarMeter(
                        frac = share,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp),
                        h = 6.dp,
                    )
                }
            }
        }
    }
}
