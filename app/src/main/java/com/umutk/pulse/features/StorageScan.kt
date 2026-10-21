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
        internal fun parts() = sizes to kids
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
    /** When the walk on screen was made (ms); the screens show its age, so a saved copy from an earlier run is recognised as one. */
    private val takenFlow = MutableStateFlow(0L)
    val taken: StateFlow<Long> = takenFlow.asStateFlow()
    private val running = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Where the last walk is kept between runs (set once from MainActivity: filesDir); null keeps it in memory only. */
    @Volatile var dir: File? = null
    private fun store() = dir?.let { File(it, "storage_scan.bin") }

    /** Walks [root] unless it is already walked, or being walked now. A walk saved by an earlier run is used as it is: only Refresh walks again. */
    fun ensure(root: File, fresh: Boolean = false) {
        if (snapFlow.value?.root == root.path) return
        if (!running.compareAndSet(false, true)) return
        scope.launch {
            try {
                // -1 = reading the saved copy, not measuring
                scannedFlow.value = -1L
                val saved = if (fresh) null else runCatching { load(root) }.getOrNull()
                if (saved != null) { takenFlow.value = store()?.lastModified() ?: System.currentTimeMillis(); snapFlow.value = saved; return@launch }
                scannedFlow.value = 0L
                val s = walk(root)
                takenFlow.value = System.currentTimeMillis()
                snapFlow.value = s
                runCatching { save(s) }
            } finally {
                running.set(false)
            }
        }
    }

    /** Takes a deleted file out of the walk (call it after the delete, with the size it had). */
    fun forget(f: File, len: Long) {
        val s = snapFlow.value ?: return
        val n = s.without(f, len)
        snapFlow.value = n
        scope.launch { runCatching { save(n) } }
    }

    /** Throws the result away and walks [root] again. */
    fun rescan(root: File) {
        snapFlow.value = null
        ensure(root, fresh = true)
    }

    // ---- the saved walk: gzip of root, kinds, folder sizes and folder children (names only) ----
    private fun save(s: Snapshot) {
        val f = store() ?: return
        val tmp = File(f.path + ".tmp")
        java.io.DataOutputStream(java.io.BufferedOutputStream(java.util.zip.GZIPOutputStream(tmp.outputStream()), 1 shl 16)).use { o ->
            o.writeInt(1); o.writeUTF(s.root)
            o.writeInt(s.kinds.size)
            s.kinds.forEach { k -> o.writeUTF(k.kind.name); o.writeLong(k.bytes); o.writeInt(k.count); o.writeInt(k.largest.size); k.largest.forEach { l -> o.writeUTF(l.file.path); o.writeLong(l.size); o.writeLong(l.modified) } }
            val (sizes, kids) = s.parts()
            o.writeInt(sizes.size); sizes.forEach { (p, v) -> o.writeUTF(p); o.writeLong(v) }
            o.writeInt(kids.size); kids.forEach { (p, l) -> o.writeUTF(p); o.writeInt(l.size); l.forEach { c -> o.writeUTF(c.name) } }
        }
        tmp.renameTo(f)
    }

    private fun load(root: File): Snapshot? {
        val f = store()?.takeIf { it.isFile } ?: return null
        java.io.DataInputStream(java.io.BufferedInputStream(java.util.zip.GZIPInputStream(f.inputStream()), 1 shl 16)).use { i ->
            if (i.readInt() != 1) return null
            val r = i.readUTF(); if (r != root.path) return null
            val kinds = List(i.readInt()) {
                val k = FileKind.valueOf(i.readUTF()); val b = i.readLong(); val c = i.readInt()
                KindSummary(k, b, c, List(i.readInt()) { LargeFile(File(i.readUTF()), i.readLong(), i.readLong()) })
            }
            val sizes = HashMap<String, Long>(); repeat(i.readInt()) { sizes[i.readUTF()] = i.readLong() }
            val kids = HashMap<String, List<File>>(); repeat(i.readInt()) { val p = i.readUTF(); val d = File(p); kids[p] = List(i.readInt()) { File(d, i.readUTF()) } }
            return Snapshot(r, kinds, sizes, kids)
        }
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
