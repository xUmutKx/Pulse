package com.umutk.pulse.features

import org.libtorrent4j.Sha1Hash
import org.libtorrent4j.SessionHandle
import org.libtorrent4j.SessionManager
import org.libtorrent4j.TorrentInfo
import org.libtorrent4j.swig.add_torrent_params
import java.io.File

/**
 * The BitTorrent engine of the download manager: libtorrent4j, the library LibreTorrent is built on. One session for the whole app;
 * each torrent is one download in the list, found again by its info hash.
 */
object TorrentEngine {
    private var session: SessionManager? = null

    @Synchronized
    private fun session(): SessionManager = session ?: SessionManager().also { it.start(); session = it }

    /** The info hash (40 hex digits) of a magnet link, or null when the link has none. */
    fun hashOf(magnet: String): String? = Regex("urn:btih:([0-9a-fA-F]{40})").find(magnet)?.groupValues?.get(1)?.lowercase()

    /**
     * Puts the torrent into the engine and returns its info hash: a magnet link is added by its link (the engine gets its metadata
     * from the swarm), a .torrent [file] by its content. A torrent the engine already has is resumed, not added twice.
     */
    fun start(url: String, file: File?, saveDir: File): String {
        val s = session()
        if (file != null) {
            val ti = TorrentInfo(file)
            val hash = ti.infoHash()
            val h = s.find(hash)
            if (h != null && h.isValid) { h.resume(); return hash.toHex() }
            val p = add_torrent_params()
            p.set_ti(ti.swig())
            p.setSave_path(saveDir.absolutePath)
            s.swig().async_add_torrent(p)
            return hash.toHex()
        }
        val hex = hashOf(url) ?: throw IllegalArgumentException("the magnet link has no torrent hash")
        val hash = Sha1Hash.parseHex(hex)
        val h = s.find(hash)
        if (h != null && h.isValid) { h.resume(); return hex }
        // the magnet link is read by libtorrent4j's own parser (the same one LibreTorrent's tests use)
        val p = org.libtorrent4j.AddTorrentParams.parseMagnetUri(url).swig()
        p.setSave_path(saveDir.absolutePath)
        s.swig().async_add_torrent(p)
        return hex
    }

    /** Bytes got, bytes wanted and whether the whole torrent is there; null while the engine does not have it yet. */
    fun progress(hash: String): Triple<Long, Long, Boolean>? {
        val h = session?.find(Sha1Hash.parseHex(hash)) ?: return null
        if (!h.isValid) return null
        val st = h.status()
        return Triple(st.totalDone(), st.totalWanted(), st.isFinished)
    }

    fun pause(hash: String) { session?.find(Sha1Hash.parseHex(hash))?.pause() }

    /** Takes the torrent out of the engine; the files already downloaded stay unless [deleteFiles]. */
    fun remove(hash: String, deleteFiles: Boolean) {
        val s = session ?: return
        val h = s.find(Sha1Hash.parseHex(hash)) ?: return
        if (deleteFiles) s.remove(h, SessionHandle.DELETE_FILES) else s.swig().remove_torrent(h.swig())
    }
}
