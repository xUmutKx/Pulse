package com.umutk.pulse.features

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.umutk.pulse.BarMeter
import com.umutk.pulse.tr
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
/** Folder the user was last in. `remember` is dropped when the screen leaves composition, so this outlives it. */
private var lastDir: File? = null

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun FolderTreeContent(root: File, onBack: () -> Unit) {
    var dir by remember(root) {
        mutableStateOf(lastDir?.takeIf { it.isDirectory && it.path.startsWith(root.path) } ?: root)
    }
    // the one shared walk of the storage (see StorageScan): the folder on screen, its children sorted by size
    val snap by StorageScan.snap.collectAsState()
    val scanned by StorageScan.scanned.collectAsState()
    LaunchedEffect(root) { StorageScan.ensure(root) }
    LaunchedEffect(dir) { lastDir = dir }
    val loading = snap?.root != root.path
    val rows = remember(snap, dir) {
        val s = snap?.takeIf { it.root == root.path }
        s?.childrenOf(dir)?.map { TreeNode(it, s.sizeOf(it)) }?.sortedByDescending { it.size }.orEmpty()
    }

    val atRoot = dir == root
    fun up() {
        if (atRoot) onBack() else dir = dir.parentFile ?: root
    }
    BackHandler { up() }

    val total = rows.sumOf { it.size }
    // long press picks files and folders; a picked set can be deleted from here
    var sel by remember(root) { mutableStateOf(setOf<String>()) }
    var askDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun toggleSel(p: String) { sel = if (p in sel) sel - p else sel + p }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { up() }) {
                Icon(Icons.Filled.ArrowBack, contentDescription = if (atRoot) "Back" else "Up")
            }
            Text("Folder tree", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { StorageScan.rescan(root) }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
            }
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
            com.umutk.pulse.Busy(modifier = Modifier.padding(top = 8.dp))
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

        if (sel.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { sel = emptySet() }) { Icon(Icons.Filled.Close, contentDescription = "Clear") }
            Text("${sel.size}", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { askDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
        }
        if (askDelete) AlertDialog(
            onDismissRequest = { askDelete = false },
            title = { Text("Delete ${sel.size} item(s)?") },
            text = { Text("Folders are deleted with everything in them.") },
            confirmButton = { TextButton(onClick = {
                val paths = sel; askDelete = false; sel = emptySet()
                scope.launch(Dispatchers.IO) { paths.forEach { File(it).deleteRecursively() }; StorageScan.rescan(root) }
            }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { askDelete = false }) { Text("Cancel") } },
        )

        LazyColumn(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(rows, key = { it.file.path }) { node ->
                val isDir = node.file.isDirectory
                val share = if (total > 0) node.size.toFloat() / total else 0f
                val picked = node.file.path in sel
                val thumb = if (isDir) null else exThumb(node.file)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(if (picked) MaterialTheme.colorScheme.primary.copy(alpha = .12f) else Color.Transparent)
                        .combinedClickable(
                            onClick = { if (sel.isNotEmpty()) toggleSel(node.file.path) else if (isDir) dir = node.file },
                            onLongClick = { toggleSel(node.file.path) },
                        )
                        .padding(vertical = 6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (thumb != null) Image(thumb, null, Modifier.size(36.dp).clip(RoundedCornerShape(6.dp)), contentScale = ContentScale.Crop, filterQuality = androidx.compose.ui.graphics.FilterQuality.High)
                        else Icon(
                            if (picked) Icons.Filled.CheckCircle else if (isDir) Icons.Filled.Folder else Icons.Filled.InsertDriveFile,
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

/** Storage screens sit on the transparent page, so their text is white rather than the theme's dark text. */
@Composable
fun FolderTreeScreen(root: File, onBack: () -> Unit) {
    // with root (or Shizuku) the tree can also start at the system root "/"; the chip switches between the two
    var base by remember(root) { mutableStateOf(root) }
    val canRoot = com.umutk.pulse.Shell.root == true || com.umutk.pulse.Shell.shizuku == 2
    CompositionLocalProvider(LocalContentColor provides com.umutk.pulse.T.text) {
        Column(Modifier.fillMaxSize()) {
            if (canRoot) Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                androidx.compose.material3.FilterChip(base.path == "/", { base = if (base.path == "/") root else File("/") },
                    { Text(if (base.path == "/") tr("Storage", "Dahili depolama") else tr("Root (/)", "Kök (/)")) })
            }
            Box(Modifier.weight(1f).fillMaxWidth()) { key(base) { FolderTreeContent(base, onBack) } }
        }
    }
}
