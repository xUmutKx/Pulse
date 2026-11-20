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

    /**
     * Public trackers added to every link, so a bare "xt=urn:btih:..." link also finds peers when the DHT is slow to start. The last ones speak HTTP(S): they answer on
     * networks that block UDP (some mobile and public networks do). A fresh list of working trackers is fetched once a day ([refreshTrackers]) and added to these.
     */
    private val TRACKERS = listOf(
        "udp://tracker.opentrackr.org:1337/announce",
        "udp://open.stealth.si:80/announce",
        "udp://tracker.torrent.eu.org:451/announce",
        "udp://exodus.desync.com:6969/announce",
        "udp://tracker.tiny-vps.com:6969/announce",
        "udp://tracker.dler.org:6969/announce",
        "udp://open.demonii.com:1337/announce",
        "udp://explodie.org:6969/announce",
        "udp://tracker.moeking.me:6969/announce",
        "udp://tracker1.bt.moack.co.kr:80/announce",
        "udp://tracker.bitsearch.to:1337/announce",
        "udp://p4p.arenabg.com:1337/announce",
        "udp://tracker.cyberia.is:6969/announce",
        "udp://opentracker.i2p.rocks:6969/announce",
        "udp://tracker.skyts.net:6969/announce",
        "udp://retracker.lanta-net.ru:2710/announce",
        "https://tracker.tamersunion.org:443/announce",
        "https://tracker.gbitt.info:443/announce",
        "https://tracker1.520.jp:443/announce",
        "http://tracker.openbittorrent.com:80/announce",
        "http://tracker.bt4g.com:2095/announce",
    )

    private const val TRACKER_LIST = "https://raw.githubusercontent.com/ngosang/trackerslist/master/trackers_best.txt"
    private fun trackerFile(ctx: Context) = File(ctx.filesDir, "torrent/trackers.txt")

    /** The list of working trackers, asked for in the background when the saved one is a day old (or none): the next torrent uses it. */
    private fun refreshTrackers(ctx: Context) {
        val f = trackerFile(ctx)
        if (f.isFile && System.currentTimeMillis() - f.lastModified() < 24L * 3_600_000L) return
        val app = ctx.applicationContext
        Thread {
            runCatching {
                val c = java.net.URL(TRACKER_LIST).openConnection() as java.net.HttpURLConnection
                c.connectTimeout = 6_000; c.readTimeout = 8_000
                try {
                    if (c.responseCode in 200..299) {
                        val lines = c.inputStream.bufferedReader().use { it.readText() }.lines().map { it.trim() }.filter { Regex("^(udp|https?)://\\S+").containsMatchIn(it) }.take(30)
                        if (lines.size >= 5) { val t = trackerFile(app); t.parentFile?.mkdirs(); t.writeText(lines.joinToString("\n")) }
                    }
                } finally { c.disconnect() }
            }
        }.apply { isDaemon = true; name = "tracker-list" }.start()
    }

    private fun liveTrackers(ctx: Context): List<String> = runCatching { trackerFile(ctx).takeIf { it.isFile }?.readLines().orEmpty().filter { it.isNotBlank() } }.getOrDefault(emptyList())

    private var session: SessionManager? = null

    // the engine's native part (libtorrent, 16 MB on the phone) is not inside the app: it is fetched when the person asks for it ([fetch]), from Maven Central
    // (the same file the app was built with, checked by its SHA-256), and kept in the app's own folder
    private const val LIB_URL = "https://repo1.maven.org/maven2/org/libtorrent4j/libtorrent4j-android-arm64/2.1.0-38/libtorrent4j-android-arm64-2.1.0-38.jar"
    private const val LIB_ENTRY = "lib/arm64-v8a/libtorrent4j.so"
    private const val LIB_SHA = "9141ae3d8e3af134eef6098bf83dc8d818e7bb45b4e1cb6b493503e7a83c3c63"

    private fun sha256(f: File): String = java.security.MessageDigest.getInstance("SHA-256").let { md ->
        f.inputStream().use { ins -> val b = ByteArray(65536); while (true) { val n = ins.read(b); if (n < 0) break; md.update(b, 0, n) } }
        md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun libFile(ctx: Context) = File(ctx.filesDir, "torrent/libtorrent4j.so")
    @Volatile private var verified = false

    /** The engine is on the phone (checked once per run by its SHA-256). */
    fun ready(ctx: Context): Boolean {
        if (verified) return true
        val f = libFile(ctx)
        verified = f.isFile && f.length() > 1_000_000 && sha256(f) == LIB_SHA
        return verified
    }

    /** The engine is being fetched. */
    val fetching = kotlinx.coroutines.flow.MutableStateFlow(false)

    /** Fetches the engine (6 MB), only when the person asks for it; false when it could not be had. */
    suspend fun fetch(ctx: Context): Boolean = withContext(Dispatchers.IO) {
        fetching.value = true
        try { ensureLib(ctx); true } catch (e: Throwable) { false } finally { fetching.value = false }
    }

    @Synchronized
    private fun ensureLib(ctx: Context) {
        val f = libFile(ctx)
        if (!ready(ctx)) {
            f.parentFile?.mkdirs()
            val tmp = File(f.parentFile, "lib.part")
            val c = java.net.URL(LIB_URL).openConnection() as java.net.HttpURLConnection
            c.connectTimeout = 15_000; c.readTimeout = 30_000
            try {
                if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode}")
                java.util.zip.ZipInputStream(c.inputStream.buffered()).use { z ->
                    var e = z.nextEntry
                    while (e != null && e.name != LIB_ENTRY) e = z.nextEntry
                    if (e == null) throw IOException("no library in the download")
                    tmp.outputStream().use { o -> z.copyTo(o) }
                }
            } finally { c.disconnect() }
            if (sha256(tmp) != LIB_SHA) { tmp.delete(); throw IOException("the downloaded library is not the expected one") }
            f.delete(); if (!tmp.renameTo(f)) { tmp.copyTo(f, overwrite = true); tmp.delete() }
            verified = true
        }
        System.setProperty("libtorrent4j.jni.path", f.absolutePath)
    }

    @Synchronized
    /** How many DHT nodes the session knows now, and how long it has been running (a young session has not found its peers yet): for the patience of a download and its status line. */
    fun dhtNodes(): Long = runCatching { session?.dhtNodes() ?: 0L }.getOrDefault(0L)
    @Volatile private var startedAt = 0L
    fun sessionAgeMs(): Long = if (startedAt == 0L) 0L else System.currentTimeMillis() - startedAt

    /** Lets the session go (every torrent in it with it): the next torrent starts a new one, on whatever network the app is bound to by then (mobile data when Wi-Fi gave no peers). */
    fun resetSession() {
        synchronized(lock) { uses.clear() }
        runCatching { session?.stop() }
        session = null
        startedAt = 0L
    }

    private fun sm(): SessionManager = session ?: SessionManager().also {
        val sp = SettingsPack()
        sp.setString(settings_pack.string_types.dht_bootstrap_nodes.swigValue(),
            "dht.libtorrent.org:25401,router.bittorrent.com:6881,router.utorrent.com:6881,dht.transmissionbt.com:6881,dht.aelitis.com:6881")
        sp.setBoolean(settings_pack.bool_types.enable_dht.swigValue(), true)
        // every torrent of a season downloads at the same time: libtorrent lets three run and queues the rest by default (those sat at "finding peers (0)")
        sp.setInteger(settings_pack.int_types.active_downloads.swigValue(), -1)
        sp.setInteger(settings_pack.int_types.active_limit.swigValue(), -1)
        sp.setInteger(settings_pack.int_types.active_seeds.swigValue(), -1)
        sp.setBoolean(settings_pack.bool_types.dont_count_slow_torrents.swigValue(), true)
        // more peers, found sooner: a bigger peer list, more connections, more of them opened at the start, every tracker asked
        sp.setInteger(settings_pack.int_types.connections_limit.swigValue(), 500)
        sp.setInteger(settings_pack.int_types.max_peerlist_size.swigValue(), 4000)
        sp.setInteger(settings_pack.int_types.torrent_connect_boost.swigValue(), 30)
        sp.setInteger(settings_pack.int_types.connection_speed.swigValue(), 60)
        sp.setBoolean(settings_pack.bool_types.announce_to_all_trackers.swigValue(), true)
        sp.setBoolean(settings_pack.bool_types.announce_to_all_tiers.swigValue(), true)
        sp.setBoolean(settings_pack.bool_types.enable_lsd.swigValue(), true)
        val p = SessionParams(sp)
        p.setPosixDiskIO() // plain file writes: memory-mapped files misbehave on the phone's shared storage
        it.start(p)
        startedAt = System.currentTimeMillis()
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

    private fun withTrackers(ctx: Context, magnet: String): String =
        magnet + (TRACKERS + liveTrackers(ctx)).distinct().filter { !magnet.contains(java.net.URLEncoder.encode(it, "UTF-8")) && !magnet.contains(it) }
            .joinToString("") { "&tr=" + java.net.URLEncoder.encode(it, "UTF-8") }


    /**
     * Downloads the wanted file of [magnet] (or of the .torrent file [torrent], when there is one) into a folder of the torrent inside [base] and returns it;
     * null when [stop] said so first (the partial data stays for a later go). [onName] gets the file's name once the file list is known;
     * [onProgress] the bytes of the wanted file so far, its size, and the peers.
     */
    suspend fun download(ctx: Context, magnet: String, torrent: ByteArray?, base: File, stop: () -> Boolean, onName: (String) -> Unit, onProgress: (done: Long, total: Long, peers: Int) -> Unit): File? = withContext(Dispatchers.IO) {
        if (!ready(ctx)) throw IOException(NEEDS_ENGINE)
        System.setProperty("libtorrent4j.jni.path", libFile(ctx).absolutePath)
        refreshTrackers(ctx)
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
                        else AddTorrentParams.parseMagnetUri(withTrackers(ctx, magnet))
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
                // every 15 seconds without the file list the trackers and the DHT are asked again (a first ask made before the DHT was up finds nobody)
                if (waited > 0 && waited % 15_000 < 300) runCatching { h.forceReannounce(); h.forceDHTAnnounce() }
                if (waited > 180_000) throw IOException("no peers answered for this torrent")
                delay(300); waited += 300
            }
            val ti = h.torrentFile() ?: throw IOException("the torrent has no file list")
            val fs = ti.files()
            val n = ti.numFiles()
            pick = pickFile(ti, early)
            val want = fs.fileSize(pick)
            // a pack of several files gives the path inside the torrent (its own folder first), a single file only its name
            onName(if ((0 until n).count { !fs.padFileAt(it) } > 1) fs.filePath(pick) else File(fs.filePath(pick)).name)
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

    // ---- watching while it downloads -------------------------------------------------------------------------------------------------------------------

    /** The torrent being watched: its handle, and the small web server on this phone that gives the player the file's bytes as the pieces arrive. */
    private class Live(val hash: String, val use: Use, val server: java.net.ServerSocket, val pick: Int) { @Volatile var closed = false }
    @Volatile private var live: Live? = null

    /**
     * Starts the wanted file of [magnet] and returns an address on this phone (http://127.0.0.1:…) that Pulse's video player can open at once: it answers the player's
     * range requests from the torrent, asking for the pieces it needs next first (so it plays while the rest still comes, and a jump forward fetches that part). It returns when the
     * first pieces and the last one (a video's index may be there) are in. [onStatus] gets what is going on (engine, peers, buffering). Null when nothing could be started.
     * Only one torrent is watched at a time: the one before it is let go.
     */
    suspend fun stream(ctx: Context, magnet: String, onStatus: (String) -> Unit): String? = withContext(Dispatchers.IO) {
        stopStream()
        if (!ready(ctx)) { onStatus(com.umutk.pulse.tr("Getting the torrent engine (6 MB)…", "Torrent motoru indiriliyor (6 MB)…")); if (!fetch(ctx)) throw IOException("the torrent engine could not be downloaded") }
        System.setProperty("libtorrent4j.jni.path", libFile(ctx).absolutePath)
        refreshTrackers(ctx)
        val s = try { sm() } catch (e: Throwable) { throw IOException("the torrent engine could not start: ${e.message ?: e.javaClass.simpleName}") }
        val idx = magnetParam(magnet, "pulse_idx")?.toIntOrNull() ?: -1
        val hash = hashOf(magnet) ?: throw IOException("this magnet link could not be read")
        val dir = File(Downloads.torrentWork(ctx), ".torrent-$hash").also { it.mkdirs() }
        val sha = AddTorrentParams.parseMagnetUri(magnet).infoHashes.best
        val use = synchronized(lock) {
            (uses[hash]?.takeIf { it.h.isValid } ?: run {
                for (i in 0 until 50) { if (s.find(sha)?.isValid != true) break; Thread.sleep(100) }
                val p = AddTorrentParams.parseMagnetUri(withTrackers(ctx, magnet))
                p.savePath = dir.absolutePath
                val err = org.libtorrent4j.swig.error_code()
                val th = s.swig().add_torrent(p.swig(), err)
                if (err.value() != 0 || th == null || !th.is_valid) throw IOException("the torrent could not be added" + (if (err.value() != 0) ": " + err.message() else ""))
                Use(TorrentHandle(th), dir).also { uses[hash] = it }
            }).also { it.users++ }
        }
        val h = use.h
        var pick = -1
        var server: java.net.ServerSocket? = null
        try {
            var waited = 0
            while (true) {
                if (!h.isValid) throw IOException("the torrent left the session")
                val st = h.status(true)
                if (st.hasMetadata()) break
                onStatus(com.umutk.pulse.tr("Finding peers… (${st.numPeers()})", "Kaynak aranıyor… (${st.numPeers()})"))
                if (waited > 45_000) throw IOException("no peers answered for this torrent")
                delay(400); waited += 400
            }
            val ti = h.torrentFile() ?: throw IOException("the torrent has no file list")
            val fs = ti.files()
            pick = pickFile(ti, idx)
            val size = fs.fileSize(pick); val off = fs.fileOffset(pick); val pl = ti.pieceLength()
            synchronized(lock) { use.wanted.add(pick); use.apply(ti.numFiles()) }
            h.resume()
            val firstP = (off / pl).toInt(); val lastP = ((off + size - 1) / pl).toInt()
            for (k in 0 until 6) if (firstP + k <= lastP) h.setPieceDeadline(firstP + k, 800 + k * 600)
            h.setPieceDeadline(lastP, 1500)
            waited = 0
            while (true) {
                if (!h.isValid) throw IOException("the torrent left the session")
                val need = (firstP..minOf(firstP + 1, lastP)).toList() + lastP
                if (need.all { h.havePiece(it) }) break
                val st = h.status(true)
                onStatus(com.umutk.pulse.tr("Buffering… ${need.count { h.havePiece(it) }}/${need.size}  ·  ${st.numPeers()} peers  ·  ${st.downloadPayloadRate() / 1024} KB/s", "Hazırlanıyor… ${need.count { h.havePiece(it) }}/${need.size}  ·  ${st.numPeers()} kaynak  ·  ${st.downloadPayloadRate() / 1024} KB/s"))
                // a start that crawls (a few percent after half a minute) is dropped so the next, better shared torrent is tried
                if (waited > 30_000 && st.downloadPayloadRate() < 30 * 1024 && need.none { h.havePiece(it) }) throw IOException("too slow to start")
                if (waited > 90_000) throw IOException("the peers sent no data")
                delay(500); waited += 500
            }
            val path = File(dir, fs.filePath(pick))
            val ss = java.net.ServerSocket(0, 8, java.net.InetAddress.getByName("127.0.0.1")); server = ss
            val me = Live(hash, use, ss, pick)
            live = me
            val ext = path.extension.lowercase().ifEmpty { "mp4" }
            val mime = when (ext) { "mkv" -> "video/x-matroska"; "webm" -> "video/webm"; "avi" -> "video/x-msvideo"; "mov" -> "video/quicktime"; else -> "video/mp4" }
            kotlin.concurrent.thread(isDaemon = true, name = "torrent-stream") {
                while (!me.closed) {
                    val c = try { ss.accept() } catch (e: Throwable) { break }
                    kotlin.concurrent.thread(isDaemon = true, name = "torrent-stream-conn") { runCatching { serveRange(c, me, h, path, off, size, pl, lastP, mime) } }
                }
            }
            "http://127.0.0.1:${ss.localPort}/stream.$ext"
        } catch (e: Throwable) {
            runCatching { server?.close() }
            synchronized(lock) {
                use.users--; use.wanted.remove(if (pick >= 0) pick else idx)
                if (use.users <= 0) { uses.remove(hash); runCatching { s.remove(h) } }
            }
            throw e
        }
    }

    /** Ends the watched torrent: its server closes, and the torrent leaves the session when no download wants it either (what was fetched stays for a later go). */
    fun stopStream() {
        val me = live ?: return
        live = null
        me.closed = true
        runCatching { me.server.close() }
        synchronized(lock) {
            me.use.users--; me.use.wanted.remove(me.pick)
            if (me.use.users <= 0) { uses.remove(me.hash); runCatching { session?.remove(me.use.h) } }
            else if (me.use.h.isValid && me.use.h.status(true).hasMetadata()) runCatching { me.use.apply(me.use.h.torrentFile().numFiles()) }
        }
    }

    /** One request of the player: the bytes of the range asked for, each piece waited for (and asked for first) before it is read from the torrent's folder. */
    private fun serveRange(c: java.net.Socket, me: Live, h: TorrentHandle, path: File, off: Long, size: Long, pl: Int, lastP: Int, mime: String) {
        c.use { sock ->
            val rd = sock.getInputStream().bufferedReader(Charsets.ISO_8859_1)
            val req = rd.readLine() ?: return
            var from = 0L; var to = size - 1; var ranged = false
            while (true) {
                val l = rd.readLine() ?: break
                if (l.isEmpty()) break
                if (l.startsWith("range:", true)) {
                    val m = Regex("bytes=(\\d*)-(\\d*)").find(l) ?: continue
                    val a = m.groupValues[1]; val b = m.groupValues[2]
                    ranged = true
                    if (a.isEmpty()) { from = (size - (b.toLongOrNull() ?: 0L)).coerceAtLeast(0L) } else { from = a.toLong(); if (b.isNotEmpty()) to = b.toLong().coerceAtMost(size - 1) }
                }
            }
            if (from >= size || from > to) { sock.getOutputStream().write("HTTP/1.1 416 Range Not Satisfiable\r\nContent-Range: bytes */$size\r\nConnection: close\r\n\r\n".toByteArray()); return }
            val out = sock.getOutputStream()
            val head = StringBuilder()
            head.append(if (ranged) "HTTP/1.1 206 Partial Content\r\n" else "HTTP/1.1 200 OK\r\n")
            head.append("Content-Type: $mime\r\nAccept-Ranges: bytes\r\nContent-Length: ${to - from + 1}\r\nConnection: close\r\n")
            if (ranged) head.append("Content-Range: bytes $from-$to/$size\r\n")
            head.append("\r\n")
            out.write(head.toString().toByteArray())
            if (req.startsWith("HEAD")) return
            val buf = ByteArray(128 * 1024)
            var pos = from
            var askedFrom = -1
            java.io.RandomAccessFile(path, "r").use { raf ->
                while (pos <= to && !me.closed && h.isValid) {
                    val len = minOf(buf.size.toLong(), to - pos + 1).toInt()
                    val p0 = ((off + pos) / pl).toInt(); val p1 = ((off + pos + len - 1) / pl).toInt()
                    // the pieces from here on are asked for first (the ones right ahead most urgently)
                    if (p0 != askedFrom) { askedFrom = p0; for (k in 0 until 6) if (p0 + k <= lastP) runCatching { h.setPieceDeadline(p0 + k, 400 + k * 500) } }
                    while ((p0..p1).any { !h.havePiece(it) }) { if (me.closed || !h.isValid) return; Thread.sleep(120) }
                    raf.seek(pos)
                    val n = raf.read(buf, 0, len)
                    if (n <= 0) { Thread.sleep(120); continue }
                    out.write(buf, 0, n); pos += n
                }
            }
        }
    }
}
