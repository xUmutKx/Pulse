package com.umutk.pulse.features

import android.content.Context
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.libtorrent4j.Priority
import org.libtorrent4j.SessionManager
import org.libtorrent4j.TorrentInfo
import java.io.File
import java.io.IOException

/**
 * The torrent engine (libtorrent through libtorrent4j, 64-bit phones only), used by the download manager for magnet links: it fetches the torrent's file list from
 * the peers, then downloads only the one file wanted (the one named by "pulse_idx" in the link, else the biggest) and stops as soon as it is whole (nothing is seeded).
 */
object TorrentEngine {
    val available: Boolean get() = Build.SUPPORTED_ABIS.contains("arm64-v8a")
    private var session: SessionManager? = null

    @Synchronized
    private fun sm(): SessionManager = session ?: SessionManager().also { it.start(); session = it }

    /**
     * Downloads the wanted file of [magnet] into [dir] and returns it; null when [stop] said so first (the partial data stays for a later go).
     * [onName] gets the file's name once the file list is known; [onProgress] the bytes of the wanted file so far, its size, and the peers.
     */
    suspend fun download(ctx: Context, magnet: String, dir: File, stop: () -> Boolean, onName: (String) -> Unit, onProgress: (done: Long, total: Long, peers: Int) -> Unit): File? = withContext(Dispatchers.IO) {
        val s = try { sm() } catch (e: Throwable) { throw IOException("the torrent engine could not start: ${e.message ?: e.javaClass.simpleName}") }
        val idx = magnetParam(magnet, "pulse_idx")?.toIntOrNull() ?: -1
        dir.mkdirs()
        // the file list comes from the peers: it can take a while when there are few of them
        var data: ByteArray? = null
        var tries = 0
        while (data == null && tries < 3) {
            if (stop()) return@withContext null
            data = s.fetchMagnet(magnet, 60, dir)
            tries++
        }
        if (data == null) throw IOException("no peers answered for this torrent")
        val ti = TorrentInfo(data)
        val fs = ti.files()
        val n = ti.numFiles()
        val pick = if (idx in 0 until n) idx else (0 until n).maxByOrNull { fs.fileSize(it) } ?: 0
        val want = fs.fileSize(pick)
        onName(File(fs.filePath(pick)).name)
        val prios = Priority.array(Priority.IGNORE, n)
        prios[pick] = Priority.DEFAULT
        s.download(ti, dir, null, prios, null, null)
        val h = s.find(ti.infoHash()) ?: throw IOException("the torrent could not be added")
        try {
            while (true) {
                if (stop()) return@withContext null
                val st = h.status()
                val got = st.totalWantedDone()
                onProgress(got, want, st.numPeers())
                if (want > 0 && got >= want) break
                if (st.isFinished || st.isSeeding) break
                delay(700)
            }
        } finally {
            // nothing is seeded: the torrent leaves the session, the files stay
            runCatching { s.remove(h) }
        }
        File(dir, fs.filePath(pick))
    }
}
