package com.umutk.pulse.features

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.libtorrent4j.AddTorrentParams
import org.libtorrent4j.Priority
import org.libtorrent4j.SessionManager
import org.libtorrent4j.SessionParams
import org.libtorrent4j.SettingsPack
import org.libtorrent4j.TorrentFlags
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.TorrentInfo
import org.libtorrent4j.swig.settings_pack
import java.io.File
import java.io.IOException

/**
 * The torrent engine (libtorrent through libtorrent4j, 64-bit phones only), used by the download manager for magnet links and .torrent files.
 * Each download wants one file of a torrent (the one named by "pulse_idx" in the link, else the biggest). Several downloads of the same torrent
 * (the episodes of one season pack) share the torrent in the session and its folder, each one asking only for its own file; the torrent leaves the
 * session when nobody wants anything from it any more (nothing is seeded).
 */
object TorrentEngine {
    val available: Boolean get() = Build.SUPPORTED_ABIS.contains("arm64-v8a")

    /** Public trackers added to every link, so a bare "xt=urn:btih:..." link also finds peers when the DHT is slow to start. */
    private val TRACKERS = listOf(
        "udp://tracker.opentrackr.org:1337/announce",
        "udp://open.stealth.si:80/announce",
        "udp://tracker.torrent.eu.org:451/announce",
        "udp://exodus.desync.com:6969/announce",
        "udp://open.demonii.com:1337/announce",
        "udp://explodie.org:6969/announce",
    )

    private var session: SessionManager? = null

    @Synchronized
    private fun sm(): SessionManager = session ?: SessionManager().also {
        val sp = SettingsPack()
        sp.setString(settings_pack.string_types.dht_bootstrap_nodes.swigValue(),
            "dht.libtorrent.org:25401,router.bittorrent.com:6881,router.utorrent.com:6881,dht.transmissionbt.com:6881,dht.aelitis.com:6881")
        sp.setBoolean(settings_pack.bool_types.enable_dht.swigValue(), true)
        val p = SessionParams(sp)
        p.setPosixDiskIO() // plain file writes: memory-mapped files misbehave on the phone's shared storage
        it.start(p)
        session = it
    }

    /** One torrent in the session and the downloads using it. */
    private class Use(val h: TorrentHandle, val dir: File) {
        var users = 0
        /** The files the downloads of this torrent still want. */
        val wanted = HashSet<Int>()
        /** Asks for exactly the wanted files (once the file list is known). */
        fun apply(n: Int) = h.prioritizeFiles(Array(n) { if (it in wanted) Priority.DEFAULT else Priority.IGNORE })
    }
    private val uses = HashMap<String, Use>()
    private val lock = Any()

    /** The file wanted: [idx] when it is a real file of the torrent (not a padding file), else the biggest one. */
    private fun pickFile(ti: TorrentInfo, idx: Int): Int {
        val fs = ti.files()
        val n = ti.numFiles()
        return if (idx in 0 until n && !fs.padFileAt(idx)) idx else (0 until n).filter { !fs.padFileAt(it) }.maxByOrNull { fs.fileSize(it) } ?: 0
    }

    /** The torrent's info hash (hex) of a magnet link, or null when it is not one libtorrent understands. */
    private fun hashOf(magnet: String): String? = runCatching { AddTorrentParams.parseMagnetUri(magnet).infoHashes.best.toHex() }.getOrNull()

    private fun withTrackers(magnet: String): String =
        magnet + TRACKERS.filter { !magnet.contains(java.net.URLEncoder.encode(it, "UTF-8")) && !magnet.contains(it) }
            .joinToString("") { "&tr=" + java.net.URLEncoder.encode(it, "UTF-8") }


    /**
     * Downloads the wanted file of [magnet] (or of the .torrent file [torrent], when there is one) into a folder of the torrent inside [base] and returns it;
     * null when [stop] said so first (the partial data stays for a later go). [onName] gets the file's name once the file list is known;
     * [onProgress] the bytes of the wanted file so far, its size, and the peers.
     */
    suspend fun download(ctx: Context, magnet: String, torrent: ByteArray?, base: File, stop: () -> Boolean, onName: (String) -> Unit, onProgress: (done: Long, total: Long, peers: Int) -> Unit): File? = withContext(Dispatchers.IO) {
        val s = try { sm() } catch (e: Throwable) { throw IOException("the torrent engine could not start: ${e.message ?: e.javaClass.simpleName}") }
        val idx = magnetParam(magnet, "pulse_idx")?.toIntOrNull() ?: -1
        val tInfo = torrent?.let { runCatching { TorrentInfo(it) }.getOrElse { throw IOException("this .torrent file could not be read") } }
        val hash = tInfo?.infoHash()?.toHex() ?: hashOf(magnet) ?: throw IOException("this magnet link could not be read")
        val dir = File(base, ".torrent-$hash")
        dir.mkdirs()
        // every download of this torrent shares one handle; files nobody asked for are never downloaded
        // the file wanted is known before the file list when the link names it (or the .torrent file is here)
        val early = when {
            tInfo != null -> pickFile(tInfo, idx)
            else -> idx
        }
        val sha = tInfo?.infoHash() ?: AddTorrentParams.parseMagnetUri(magnet).infoHashes.best
        val use = synchronized(lock) {
            (uses[hash]?.takeIf { it.h.isValid } ?: run {
                // the same torrent just left the session (a pause, then a continue): its removal finishes first
                for (i in 0 until 50) { if (s.find(sha)?.isValid != true) break; Thread.sleep(100) }
                // added with only the wanted file asked for: a torrent with nothing wanted counts as finished and drops its peers
                val p = if (tInfo != null) AddTorrentParams(org.libtorrent4j.swig.add_torrent_params()).also { it.swig().set_ti(tInfo.swig()) }
                        else AddTorrentParams.parseMagnetUri(withTrackers(magnet))
                p.savePath = dir.absolutePath
                // (a link that does not name its file asks for all of them until the file list shows the biggest: a few moments of the others)
                if (early >= 0) {
                    p.filePriorities(Array(if (tInfo != null) tInfo.numFiles() else early + 1) { if (it == early) Priority.DEFAULT else Priority.IGNORE })
                    p.flags = p.flags.or_(TorrentFlags.DEFAULT_DONT_DOWNLOAD)
                }
                val err = org.libtorrent4j.swig.error_code()
                val th = s.swig().add_torrent(p.swig(), err)
                if (err.value() != 0 || th == null || !th.is_valid) throw IOException("the torrent could not be added" + (if (err.value() != 0) ": " + err.message() else ""))
                Use(TorrentHandle(th), dir).also { uses[hash] = it }
            }).also {
                it.users++
                // a later download of a torrent still waiting for its file list: its file is asked for now, applied when the list comes
                if (early >= 0 && it.wanted.add(early)) runCatching { it.apply(maxOf(it.wanted.max() + 1, it.h.torrentFile()?.numFiles() ?: 0)) }
            }
        }
        val h = use.h
        var pick = -1
        try {
            // a magnet link: the file list comes from the peers, which can take a while when there are few of them
            var waited = 0
            while (true) {
                if (!h.isValid) throw IOException("the torrent left the session")
                val st = h.status(true)
                if (st.hasMetadata()) break
                if (stop()) return@withContext null
                onProgress(0, 0, st.numPeers())
                if (waited > 180_000) throw IOException("no peers answered for this torrent")
                delay(300); waited += 300
            }
            val ti = h.torrentFile() ?: throw IOException("the torrent has no file list")
            val fs = ti.files()
            val n = ti.numFiles()
            pick = pickFile(ti, early)
            val want = fs.fileSize(pick)
            onName(File(fs.filePath(pick)).name)
            synchronized(lock) { use.wanted.add(pick); use.apply(n) }
            h.resume()
            // a torrent that had nothing left to fetch (another file of it just finished) let its peers go: ask the trackers and the DHT again
            if (h.status(true).numPeers() == 0) runCatching { h.forceReannounce(); h.forceDHTAnnounce() }
            while (true) {
                if (stop()) return@withContext null
                val got = h.fileProgress().getOrElse(pick) { 0L }
                if (!h.isValid) throw IOException("the torrent left the session")
                val st = h.status(true)
                st.errorCode()?.takeIf { it.isError }?.let { throw IOException(it.message) }
                onProgress(got, want, st.numPeers())
                if (got >= want) break
                delay(700)
            }
            File(dir, fs.filePath(pick))
        } finally {
            synchronized(lock) {
                use.users--
                // this download is over (finished or paused): its file is not wanted any more by this torrent
                use.wanted.remove(if (pick >= 0) pick else early)
                if (h.isValid && use.users > 0 && h.status(true).hasMetadata()) runCatching { use.apply(h.torrentFile().numFiles()) }
                if (use.users <= 0) {
                    uses.remove(hash)
                    runCatching { s.remove(h) }
                }
            }
        }
    }

    /** Called with the path [download] returned, after the finished file was moved away: the torrent's working folder goes once no download uses it (with the pieces of neighbouring files it holds). */
    fun done(file: File) {
        val dir = generateSequence(file.parentFile) { it.parentFile }.firstOrNull { it.name.startsWith(".torrent-") } ?: return
        val busy = synchronized(lock) { uses.values.any { it.dir == dir } }
        if (!busy) runCatching { dir.deleteRecursively() }
    }
}
