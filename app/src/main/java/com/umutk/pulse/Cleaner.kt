package com.umutk.pulse

import android.os.Environment
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.Storage
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** One thing the cleaner found: a file or folder that can be removed, and why it was listed. */
class Junk(val file: File, val size: Long, val why: String)

/** SD Maid style checks, read-only: nothing is deleted until the user confirms a group. */
object Cleaner {
    private val root get() = Environment.getExternalStorageDirectory()

    fun apks(): List<Junk> = listOf(File(root, "Download"), root).distinct()
        .flatMap { d -> d.walkTopDown().maxDepth(4).filter { it.isFile && it.extension.equals("apk", true) }.toList() }
        .map { Junk(it, it.length(), "APK file") }

    fun emptyDirs(): List<Junk> = root.walkTopDown().maxDepth(5).filter { it.isDirectory && !it.name.startsWith(".") && (it.list()?.isEmpty() == true) }
        .map { Junk(it, 0L, "empty folder") }.toList()

    /** Root or Shizuku: the shell can read other apps' cache folders, the file API cannot (Android 11+ blocks Android/data). */
    private val viaShell get() = Shell.root == true || Shell.shizukuOk()

    /** App caches: Android/data/<package>/cache, or with root every app's cache and code_cache in every user's data. */
    fun caches(): List<Junk> = if (viaShell) shellCaches() else File(root, "Android/data").listFiles().orEmpty().filter { it.isDirectory }
        .mapNotNull { pkg -> File(pkg, "cache").takeIf { it.isDirectory } }
        .map { c -> Junk(c, c.walkTopDown().filter { it.isFile }.sumOf { it.length() }, "app cache") }
        .filter { it.size > 0 }.sortedByDescending { it.size }

    /** One shell pass lists the caches and sizes them with du. /data/user needs root; Shizuku's shell reads only the shared storage folders. */
    private fun shellCaches(): List<Junk> {
        val r = root.path
        val deep = if (Shell.root == true) "/data/user/*/*/cache /data/user/*/*/code_cache " else ""
        val out = Shell.run("for d in ${deep}$r/Android/data/*/cache $r/Android/data/*/code_cache; do [ -d \"\$d\" ] && du -sk \"\$d\" 2>/dev/null; done")
        return out.lineSequence().mapNotNull { line ->
            val kb = line.substringBefore('\t').trim().toLongOrNull() ?: return@mapNotNull null
            Junk(File(line.substringAfter('\t').trim()), kb * 1024, "app cache")
        }.filter { it.size > 0 }.sortedByDescending { it.size }.toList()
    }

    /** A cache folder of one app where cleaning is allowed: /data/user/<id>/<pkg>/ or Android/data/<pkg>/. */
    private fun isCacheDir(dir: File): Boolean {
        if (dir.name != "cache" && dir.name != "code_cache") return false
        val base = dir.parentFile?.parentFile ?: return false
        return base.parentFile?.path == "/data/user" || (base.name == "data" && base.parentFile?.name == "Android")
    }

    /** Empties one app cache folder and keeps the folder. Returns the bytes freed; root paths go through the shell. */
    fun clearCache(j: Junk): Long {
        val dir = j.file
        if (!isCacheDir(dir)) return 0L
        if (viaShell) {
            Shell.run("rm -rf '${dir.path}'/* '${dir.path}'/.[!.]* 2>/dev/null")
            return (j.size - duBytes(dir.path)).coerceAtLeast(0L)
        }
        dir.listFiles()?.forEach { runCatching { it.deleteRecursively() } }
        return (j.size - dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }).coerceAtLeast(0L)
    }

    /** Removes one junk item. Cache folders are emptied, everything else is deleted. */
    fun remove(j: Junk): Boolean {
        if (isCacheDir(j.file)) { clearCache(j); return true }
        return runCatching { j.file.deleteRecursively() }.getOrDefault(false)
    }

    private fun duBytes(path: String): Long =
        Shell.run("du -sk '$path' 2>/dev/null").substringBefore('\t').trim().toLongOrNull()?.times(1024) ?: 0L

    /** Big files that are easy to forget, biggest first. */
    fun large(minBytes: Long = 100L shl 20): List<Junk> = root.walkTopDown().maxDepth(6)
        .filter { it.isFile && it.length() >= minBytes && !it.path.contains("/Android/") }
        .map { Junk(it, it.length(), "large file") }.sortedByDescending { it.size }.take(20).toList()
}

/** The cleaner card: each group is scanned on demand and can be removed after a confirmation. */
@Composable
fun CleanerCard() {
    val scope = rememberCoroutineScope()
    var groups by remember { mutableStateOf<Map<String, List<Junk>>>(emptyMap()) }
    var busy by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<Pair<String, List<Junk>>?>(null) }
    var msg by remember { mutableStateOf("") }
    fun scan(name: String, f: () -> List<Junk>) {
        busy = name
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { f() }.getOrDefault(emptyList()) }
            groups = groups + (name to r); busy = ""
        }
    }
    PCard {
        Text("Cleaner", color = Look.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconAction(Icons.Filled.Android, "APK files") { scan("APK files") { Cleaner.apks() } }
            IconAction(Icons.Filled.FolderOff, "Empty") { scan("Empty folders") { Cleaner.emptyDirs() } }
            IconAction(Icons.Filled.CleaningServices, "Caches") { scan("App caches") { Cleaner.caches() } }
            IconAction(Icons.Filled.Storage, "Large") { scan("Large files") { Cleaner.large() } }
        }
        if (busy.isNotEmpty()) Text("Scanning ${busy}…", color = T.sub, fontSize = 12.sp)
        groups.forEach { (name, items) ->
            val size = items.sumOf { it.size }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("$name: ${items.size} · ${Guard.gb(size)}", color = T.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                if (items.isNotEmpty() && name != "Large files") IconButton(onClick = { pending = name to items }) { Icon(Icons.Filled.Delete, "Remove", tint = Color(0xFFC42B1C)) }
            }
            items.take(6).forEach { Text(it.file.path.removePrefix(Environment.getExternalStorageDirectory().path), color = T.sub, fontSize = 11.sp, maxLines = 1) }
        }
        if (msg.isNotEmpty()) Text(msg, color = Look.accent, fontSize = 12.sp)
    }
    pending?.let { (name, items) ->
        WinAlert(onDismissRequest = { pending = null },
            title = { Text("Remove $name?") },
            text = { Text("${items.size} items, ${Guard.gb(items.sumOf { it.size })}. This cannot be undone.") },
            confirmButton = { TextButton({
                val p = pending; pending = null
                scope.launch {
                    val n = withContext(Dispatchers.IO) { items.count { j -> Cleaner.remove(j) } }
                    msg = "Removed $n of ${items.size} from $name"
                    groups = groups + (name to emptyList())
                }
            }) { Text("Remove") } },
            dismissButton = { TextButton({ pending = null }) { Text("Cancel") } })
    }
}

/** Icon-only action with a one-word caption: the storage tools read as a row of pictures, not text buttons. */
@Composable
fun IconAction(icon: ImageVector, label: String, danger: Boolean = false, onClick: () -> Unit) {
    val tint = if (danger) Color(0xFFC42B1C) else Look.accent
    Column(Modifier.width(64.dp).clickable(onClick = onClick).padding(vertical = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(44.dp).background(tint.copy(alpha = .14f), RoundedCornerShape(T.radius.coerceAtMost(14.dp))), contentAlignment = Alignment.Center) {
            Icon(icon, label, Modifier.size(22.dp), tint = tint)
        }
        Text(label, color = T.sub, fontSize = 11.sp, maxLines = 1)
    }
}
