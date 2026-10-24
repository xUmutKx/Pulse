package com.umutk.pulse.features

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material.icons.filled.GridView
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.List
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import android.graphics.BitmapFactory
import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Refresh
import com.umutk.pulse.Icon
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
private class TreeNode(val file: File, val size: Long, val isDir: Boolean = file.isDirectory)

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

/** The next tree screen opens in [d] (when it lies inside the storage it shows). */
fun openTreeAt(d: File) { lastDir = d; directStart = d }

/** Set by [openTreeAt]: the screen that opens next leaves in one Back from its start folder, not by climbing every parent up to the storage root. */
private var directStart: File? = null

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun FolderTreeContent(root: File, onBack: () -> Unit) {
    var dir by remember(root) {
        // the system root always opens at "/" itself: every path starts with it, so the last storage folder (Android...) must not be taken for it
        mutableStateOf(lastDir?.takeIf { root.path != "/" && it.isDirectory && it.path.startsWith(root.path) } ?: root)
    }
    // the one shared walk of the storage (see StorageScan): the folder on screen, its children sorted by size
    val snap by StorageScan.snap.collectAsState()
    val scanned by StorageScan.scanned.collectAsState()
    val taken by StorageScan.taken.collectAsState()
    // the system root "/" is never walked whole (it never ends: /proc, /sys): each folder is measured on its own through root with du, once, and kept
    val sysRoot = root.path == "/"
    var rootRev by remember { mutableStateOf(0) }
    LaunchedEffect(root) { if (!sysRoot) StorageScan.ensure(root) }
    LaunchedEffect(dir) { if (root.path != "/") lastDir = dir }
    // the other apps' folders (Android/data, Android/obb) are not in the storage walk below their first level: with root or Shizuku each one is measured when opened
    val rootOk = com.umutk.pulse.Shell.root == true || com.umutk.pulse.Shell.shizuku == 2
    val viaRoot = sysRoot || (rootOk && (dir.path.contains("/Android/data") || dir.path.contains("/Android/obb")))
    val rootRows by produceState<List<TreeNode>?>(if (viaRoot) RootDu.cached(dir) else null, dir, rootRev, viaRoot) {
        if (viaRoot) value = RootDu.cached(dir) ?: withContext(Dispatchers.IO) { RootDu.measure(dir) }
    }
    val snapRows = remember(snap, dir, rootRows, viaRoot) {
        if (viaRoot) return@remember rootRows.orEmpty()
        val s = snap?.takeIf { it.root == root.path }
        s?.childrenOf(dir)?.map { TreeNode(it, s.sizeOf(it)) }?.sortedByDescending { it.size }.orEmpty()
    }
    // a folder made after the saved scan is not in it: its children are read and measured now, so it never shows as empty while it holds files
    val needLive = !viaRoot && snap?.root == root.path && snapRows.isEmpty() && dir.list()?.isNotEmpty() == true
    val liveRows by produceState<List<TreeNode>?>(null, dir, needLive) {
        value = if (!needLive) null else withContext(Dispatchers.IO) {
            dir.listFiles().orEmpty().map { f -> TreeNode(f, if (f.isFile) f.length() else f.walkTopDown().take(100_000).filter { it.isFile }.sumOf { it.length() }) }.sortedByDescending { it.size }
        }
    }
    val loading = if (viaRoot) rootRows == null else if (needLive) liveRows == null else snap?.root != root.path
    val rows = if (needLive) liveRows.orEmpty() else snapRows

    val atRoot = dir == root
    val direct = remember { directStart.also { directStart = null } }
    fun up() {
        when {
            atRoot -> onBack()
            direct != null && (dir == direct || !dir.path.startsWith(direct.path)) -> onBack()
            else -> dir = dir.parentFile ?: root
        }
    }
    BackHandler { up() }

    val total = rows.sumOf { it.size }
    // long press picks files and folders; a picked set can be deleted from here
    var sel by remember(root) { mutableStateOf(setOf<String>()) }
    var askDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun toggleSel(p: String) { sel = if (p in sel) sel - p else sel + p }
    // tap: a folder opens, a picture opens big, a file does nothing; while something is picked a tap toggles it
    var preview by remember { mutableStateOf<File?>(null) }
    fun tap(node: TreeNode) {
        when {
            sel.isNotEmpty() -> toggleSel(node.file.path)
            node.isDir -> dir = node.file
            isPicture(node.file) -> preview = node.file
        }
    }
    // list or square tiles, remembered for the next visit
    val prefs = androidx.compose.ui.platform.LocalContext.current.getSharedPreferences("p", Context.MODE_PRIVATE)
    // 0 list, 1 square tiles, 2 the disk map (blocks as big as the folders); the button cycles them
    var mode by remember { mutableStateOf(prefs.getInt("tree_mode", if (prefs.getBoolean("tree_grid", false)) 1 else 0).coerceIn(0, 2)) }
    val grid = mode == 1
    preview?.let { f -> PictureView(f) { preview = null } }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { up() }) {
                Icon(Icons.Filled.ArrowBack, contentDescription = if (atRoot) "Back" else "Up")
            }
            Text("Folder tree", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = {
                mode = (mode + 1) % 3
                prefs.edit().putInt("tree_mode", mode).apply()
            }) {
                Icon(when (mode) { 0 -> Icons.Filled.Apps; 1 -> Icons.Filled.GridView; else -> Icons.Filled.List }, contentDescription = if (mode == 0) "Tiles" else if (mode == 1) "Disk map" else "List")
            }
            IconButton(onClick = { if (viaRoot) { RootDu.forget(dir); rootRev++ } else StorageScan.rescan(root) }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
            }
        }
        Text(
            dir.path + if (!viaRoot && taken > 0L) "  ·  " + tr("measured ${((System.currentTimeMillis() - taken) / 60000).coerceAtLeast(0)} min ago", "${((System.currentTimeMillis() - taken) / 60000).coerceAtLeast(0)} dk önce ölçüldü") else "",
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
                if (scanned < 0) tr("Reading the saved scan…", "Kayıtlı tarama okunuyor…") else "Measuring… ${scanned} files",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
        } else if (rows.isEmpty()) {
            Text(
                if (dir.isDirectory || viaRoot) "Empty folder" else "This folder cannot be read",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 16.dp),
            )
        }

        if (sel.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { sel = emptySet() }) { Icon(Icons.Filled.Close, contentDescription = "Clear") }
            Text("${sel.size}", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { askDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
        }
        if (askDelete) com.umutk.pulse.WinAlert(
            onDismissRequest = { askDelete = false },
            title = { Text("Delete ${sel.size} item(s)?") },
            text = { Text("Folders are deleted with everything in them.") },
            confirmButton = { TextButton(onClick = {
                val paths = sel; askDelete = false; sel = emptySet()
                scope.launch(Dispatchers.IO) { paths.forEach { File(it).deleteRecursively() }; if (viaRoot) { RootDu.forget(dir); rootRev++ } else StorageScan.rescan(root) }
            }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { askDelete = false }) { Text("Cancel") } },
        )

        if (mode == 2) TreeMap(rows, sel, ::tap, ::toggleSel) else if (grid) TreeTiles(rows, sel, ::tap, ::toggleSel) else LazyColumn(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(rows, key = { it.file.path }) { node ->
                val isDir = node.isDir
                val share = if (total > 0) node.size.toFloat() / total else 0f
                val picked = node.file.path in sel
                val thumb = if (isDir) null else exThumb(node.file)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(if (picked) MaterialTheme.colorScheme.primary.copy(alpha = .12f) else Color.Transparent)
                        .combinedClickable(
                            onClick = { tap(node) },
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
    // this screen can open before the app checked for root, so the check runs here too (it sets Shell.root)
    LaunchedEffect(Unit) { if (com.umutk.pulse.Shell.root != true) withContext(Dispatchers.IO) { com.umutk.pulse.Shell.detectRoot() } }
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

private val pictureExt = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic")

private fun isPicture(f: File): Boolean = f.isFile && f.extension.lowercase() in pictureExt

/** Full-screen picture, decoded at half size; tap anywhere to close. */
@Composable
private fun PictureView(file: File, onClose: () -> Unit) {
    val bmp by produceState<ImageBitmap?>(null, file) {
        value = withContext(Dispatchers.IO) {
            runCatching { BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = 2 })?.asImageBitmap() }.getOrNull()
        }
    }
    Dialog(onDismissRequest = onClose) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .92f)).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
            val b = bmp
            if (b != null) Image(b, file.name, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            else com.umutk.pulse.Busy()
        }
    }
}

/** Square tiles: a thumbnail or icon, the name and the size. Same tap and pick rules as the list. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun TreeTiles(rows: List<TreeNode>, sel: Set<String>, onTap: (TreeNode) -> Unit, onToggle: (String) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(104.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        gridItems(rows, key = { it.file.path }) { node ->
            val isDir = node.isDir
            val picked = node.file.path in sel
            val thumb = if (isDir) null else exThumb(node.file)
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(if (picked) MaterialTheme.colorScheme.primary.copy(alpha = .12f) else Color.Transparent)
                    .combinedClickable(onClick = { onTap(node) }, onLongClick = { onToggle(node.file.path) })
                    .padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (thumb != null) Image(thumb, null, Modifier.size(72.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                else Icon(
                    if (picked) Icons.Filled.CheckCircle else if (isDir) Icons.Filled.Folder else Icons.Filled.InsertDriveFile,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                )
                Text(node.file.name, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                Text(formatSize(node.size), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** Folder sizes under the system root, measured one folder at a time through root (`du` on one file system) and kept for the run. */
private object RootDu {
    private val cache = java.util.concurrent.ConcurrentHashMap<String, List<TreeNode>>()
    fun cached(d: File): List<TreeNode>? = cache[d.path]
    fun forget(d: File) { cache.remove(d.path) }
    fun measure(d: File): List<TreeNode> {
        val q = d.path.replace("'", "'\\''")
        val p = if (d.path == "/") "" else d.path
        // the folders (ls -p marks them with /), then the size of every entry on this file system (du -x: /proc, /sys and other mounts stay 0)
        val out = com.umutk.pulse.Shell.run("ls -1Ap -- '$q' 2>/dev/null; echo __DU; du -xakd 1 -- '$q' 2>/dev/null")
        val (ls, du) = out.split("__DU").let { (it.getOrNull(0) ?: "") to (it.getOrNull(1) ?: "") }
        val dirs = ls.lines().filter { it.endsWith("/") }.map { it.dropLast(1) }.toSet()
        val sizes = HashMap<String, Long>()
        du.lines().forEach { l -> val t = l.indexOf('\t'); if (t > 0) l.substring(0, t).trim().toLongOrNull()?.let { sizes[l.substring(t + 1).trim()] = it * 1024 } }
        val names = ls.lines().filter { it.isNotBlank() }.map { it.removeSuffix("/") }
        val list = names.map { n -> val f = File("$p/$n"); TreeNode(f, sizes[f.path] ?: 0L, n in dirs) }.sortedByDescending { it.size }
        cache[d.path] = list
        return list
    }
}


/** The disk map: every item is a block whose area is its share of the folder, packed so the blocks stay close to squares (squarified treemap). */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ColumnScope.TreeMap(rows: List<TreeNode>, sel: Set<String>, onTap: (TreeNode) -> Unit, onToggle: (String) -> Unit) {
    val items = remember(rows) { rows.filter { it.size > 0 }.sortedByDescending { it.size }.take(80) }
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp)) {
        val w = maxWidth.value; val h = maxHeight.value
        val cells = remember(items, w, h) { squarify(items, w, h) }
        cells.forEach { (node, r) ->
            val picked = node.file.path in sel
            val hue = ((node.file.name.hashCode() and 0x7fffffff) % 360).toFloat()
            val base = Color.hsv(hue, if (node.isDir) .55f else .3f, if (node.isDir) .62f else .78f)
            Box(
                Modifier.offset(r[0].dp, r[1].dp).size(r[2].dp, r[3].dp).padding(1.dp)
                    .background(if (picked) MaterialTheme.colorScheme.primary else base)
                    .combinedClickable(onClick = { onTap(node) }, onLongClick = { onToggle(node.file.path) }),
            ) {
                if (r[2] > 54f && r[3] > 30f) Column(Modifier.padding(4.dp)) {
                    Text(node.file.name, color = Color.White, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (r[3] > 46f) Text(formatSize(node.size), color = Color.White.copy(alpha = .8f), fontSize = 10.sp)
                }
            }
        }
    }
}

/** Squarified treemap layout of [items] (sorted, biggest first) in a w x h box: each result is (item, [x, y, width, height]). */
private fun squarify(items: List<TreeNode>, w: Float, h: Float): List<Pair<TreeNode, FloatArray>> {
    val total = items.sumOf { it.size }.toDouble().coerceAtLeast(1.0)
    val area = items.map { (it.size / total * w * h).toFloat() }
    val out = ArrayList<Pair<TreeNode, FloatArray>>()
    var x = 0f; var y = 0f; var rw = w; var rh = h
    var i = 0
    fun worst(row: List<Float>, side: Float): Float {
        val s = row.sum(); if (s <= 0f || side <= 0f) return Float.MAX_VALUE
        return maxOf(side * side * row.max() / (s * s), s * s / (side * side * row.min()))
    }
    while (i < items.size) {
        val side = minOf(rw, rh)
        val row = ArrayList<Float>(); var j = i
        while (j < items.size) {
            val next = row + area[j]
            if (row.isNotEmpty() && worst(next, side) > worst(row, side)) break
            row.add(area[j]); j++
        }
        val s = row.sum()
        val thick = if (side > 0f) s / side else 0f
        var off = 0f
        for (k in i until j) {
            val len = if (thick > 0f) area[k] / thick else 0f
            if (rw >= rh) out += items[k] to floatArrayOf(x, y + off, thick, len) else out += items[k] to floatArrayOf(x + off, y, len, thick)
            off += len
        }
        if (rw >= rh) { x += thick; rw -= thick } else { y += thick; rh -= thick }
        i = j
    }
    return out
}
