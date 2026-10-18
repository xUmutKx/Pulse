package com.umutk.pulse.features

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean
import com.umutk.pulse.Shell

/**
 * One walk of the shared storage, used by the folder tree and the file-type breakdown alike. It runs once and the
 * result stays for the whole run of the app, so opening either screen again shows the same numbers at once.
 * Refresh on either screen walks again.
 */
object StorageScan {
    class Snapshot(
        val root: String,
        val kinds: List<KindSummary>,
        private val sizes: Map<String, Long>,
        private val kids: Map<String, List<File>>,
    ) {
        /** Bytes under a folder, or the size of a file. Folders the walk skips (app-private data) count as 0. */
        fun sizeOf(f: File): Long = if (f.isDirectory) sizes[f.path] ?: 0L else f.length()

        /** Direct children of a folder, as the walk saw them (links left out). */
        fun childrenOf(dir: File): List<File> = kids[dir.path].orEmpty()

        /** The same numbers with one deleted file taken out, so the folder tree and the type list match the disk. */
        fun without(f: File, len: Long): Snapshot {
            val sz = HashMap(sizes)
            var p = f.parentFile
            while (p != null && p.path.startsWith(root)) {
                val path = p.path
                sz[path]?.let { sz[path] = it - len }
                p = p.parentFile
            }
            val kd = HashMap(kids)
            f.parentFile?.path?.let { pp -> kd[pp]?.let { kd[pp] = it.filterNot { c -> c.path == f.path } } }
            val kind = KindByExt[f.extension.lowercase()] ?: FileKind.OTHER
            val ks = kinds.map { k ->
                if (k.kind != kind) k else KindSummary(k.kind, k.bytes - len, k.count - 1, k.largest.filterNot { it.file.path == f.path })
            }
            return Snapshot(root, ks, sz, kd)
        }
    }

    private val snapFlow = MutableStateFlow<Snapshot?>(null)
    val snap: StateFlow<Snapshot?> = snapFlow.asStateFlow()
    private val scannedFlow = MutableStateFlow(0L)
    /** Files measured so far in the walk that is running, for the progress line. */
    val scanned: StateFlow<Long> = scannedFlow.asStateFlow()
    private val running = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Walks [root] unless it is already walked, or being walked now. */
    fun ensure(root: File) {
        if (snapFlow.value?.root == root.path) return
        if (!running.compareAndSet(false, true)) return
        scope.launch {
            try {
                scannedFlow.value = 0L
                snapFlow.value = walk(root)
            } finally {
                running.set(false)
            }
        }
    }

    /** Takes a deleted file out of the walk (call it after the delete, with the size it had). */
    fun forget(f: File, len: Long) {
        val s = snapFlow.value ?: return
        snapFlow.value = s.without(f, len)
    }

    /** Throws the result away and walks [root] again. */
    fun rescan(root: File) {
        snapFlow.value = null
        ensure(root)
    }

    /** Sizes the children of Android/data or Android/obb with one `du` through root or Shizuku; 0 when neither is granted. */
    private fun androidSizes(d: File, sizes: HashMap<String, Long>, kids: HashMap<String, List<File>>): Long {
        if (Shell.root != true && Shell.shizuku != 2) return 0L
        val q = d.path.replace("'", "'\\''")
        val list = ArrayList<File>()
        var total = 0L
        for (line in Shell.run("du -sk '$q'/* 2>/dev/null").lines()) {
            val t = line.indexOf('\t')
            if (t <= 0) continue
            val bytes = (line.substring(0, t).trim().toLongOrNull() ?: continue) * 1024
            val f = File(line.substring(t + 1).trim())
            list.add(f); sizes[f.path] = bytes; total += bytes
        }
        kids[d.path] = list
        sizes[d.path] = total
        return total
    }

    private fun walk(root: File): Snapshot {
        val acc = FileKind.entries.associateWith { KindAcc() }
        val sizes = HashMap<String, Long>()
        val kids = HashMap<String, List<File>>()
        var files = 0L

        fun dir(d: File): Long {
            val list = d.listFiles()
            if (list == null) {
                kids[d.path] = emptyList()
                return 0L
            }
            var total = 0L
            val mine = ArrayList<File>(list.size)
            for (child in list) {
                if (Files.isSymbolicLink(child.toPath())) continue
                mine.add(child)
                if (child.isDirectory) {
                    // Android/data and Android/obb: the other apps' folders only size through root or Shizuku
                    if (isSkippedDir(child)) total += androidSizes(child, sizes, kids) else total += dir(child)
                } else if (child.isFile) {
                    val len = child.length()
                    total += len
                    files++
                    if (files % 2000 == 0L) scannedFlow.value = files
                    val a = acc[KindByExt[child.extension.lowercase()] ?: FileKind.OTHER]!!
                    a.bytes += len
                    a.count++
                    a.top.add(LargeFile(child, len, child.lastModified()))
                    if (a.top.size > TOP_PER_KIND) a.top.poll()
                }
            }
            kids[d.path] = mine
            sizes[d.path] = total
            return total
        }

        dir(root)
        scannedFlow.value = files
        val kinds = FileKind.entries
            .map { k -> acc[k]!!.let { KindSummary(k, it.bytes, it.count, it.top.sortedByDescending { f -> f.size }) } }
            .sortedByDescending { it.bytes }
        return Snapshot(root.path, kinds, sizes, kids)
    }
}
