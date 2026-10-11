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

    /** Throws the result away and walks [root] again. */
    fun rescan(root: File) {
        snapFlow.value = null
        ensure(root)
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
                    if (!isSkippedDir(child)) total += dir(child)
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
