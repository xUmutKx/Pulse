package com.umutk.pulse.extras.localsend

import android.content.Context
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Environment
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** A device seen on the network. */
class Peer(val alias: String, val ip: String, val port: Int, val https: Boolean, val fingerprint: String, val model: String, val type: String)

/** A transfer offered to this phone, waiting for Accept / Decline. */
class Incoming(val sessionId: String, val from: String, val files: List<OfferedFile>, val decision: CompletableFuture<Boolean>)
class OfferedFile(val id: String, val name: String, val size: Long, val mime: String)

/**
 * LocalSend (localsend.org) protocol v2 on the local network: this phone shows up for other LocalSend devices, they show up here, and files go both ways.
 * Discovery is the UDP multicast announcement (224.0.0.167:53317) plus a scan of the subnet, for networks that drop multicast. The phone's own server
 * speaks plain HTTP (the protocol's non-encrypted mode, which LocalSend apps accept); devices that answer with HTTPS are reached too, their self-signed
 * certificate accepted because the transfer stays on the local network.
 */
object LocalSendEngine {
    const val PORT = 53317
    private const val GROUP = "224.0.0.167"
    private const val V = "2.1"

    var alias by mutableStateOf("")
    val peers = mutableStateMapOf<String, Peer>()
    var incoming by mutableStateOf<Incoming?>(null)
    val received = mutableStateListOf<String>()
    var busy by mutableStateOf("")
    var progress by mutableFloatStateOf(0f)
    var running by mutableStateOf(false)
    var note by mutableStateOf("")

    private var fingerprint = ""
    private var server: ServerSocket? = null
    private var mcast: MulticastSocket? = null
    private var lock: WifiManager.MulticastLock? = null
    private val pool = Executors.newCachedThreadPool()
    private val sessions = ConcurrentHashMap<String, Session>()

    private class Session(val dir: File, val files: Map<String, OfferedFile>, val tokens: Map<String, String>)

    private fun prefs(c: Context) = c.getSharedPreferences("localsend", Context.MODE_PRIVATE)

    fun saveDir(c: Context): File {
        val d = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "LocalSend")
        return if (d.isDirectory || d.mkdirs()) d else File(c.getExternalFilesDir(null) ?: c.filesDir, "LocalSend").apply { mkdirs() }
    }

    fun setAlias(c: Context, a: String) { alias = a.trim().ifEmpty { Build.MODEL }; prefs(c).edit().putString("alias", alias).apply() }

    private fun me(announce: Boolean? = null): JSONObject = JSONObject().put("alias", alias).put("version", V).put("deviceModel", Build.MODEL).put("deviceType", "mobile")
        .put("fingerprint", fingerprint).put("port", PORT).put("protocol", "http").put("download", false).also { if (announce != null) it.put("announce", announce) }

    fun start(c: Context) {
        if (running) return
        val app = c.applicationContext
        val p = prefs(app)
        fingerprint = p.getString("fp", null) ?: UUID.randomUUID().toString().replace("-", "").also { p.edit().putString("fp", it).apply() }
        alias = p.getString("alias", null) ?: Build.MODEL
        running = true; note = ""
        lock = (app.getSystemService(Context.WIFI_SERVICE) as WifiManager).createMulticastLock("pulse-localsend").apply { setReferenceCounted(false); runCatching { acquire() } }
        pool.execute { runCatching { serve(app) }.onFailure { note = "Server: ${it.message}" } }
        pool.execute { runCatching { listen() } }
        pool.execute { repeat(3) { announce(); Thread.sleep(2000) } }
        scan()
    }

    fun stop() {
        running = false
        runCatching { server?.close() }; server = null
        runCatching { mcast?.close() }; mcast = null
        runCatching { lock?.release() }; lock = null
        incoming?.decision?.complete(false); incoming = null
    }

    // ---------------------------------------------------------------- discovery

    fun announce() {
        runCatching {
            val data = me(true).toString().toByteArray()
            MulticastSocket().use { s -> s.send(DatagramPacket(data, data.size, InetAddress.getByName(GROUP), PORT)) }
        }
    }

    private fun listen() {
        val s = MulticastSocket(null).apply { reuseAddress = true; bind(InetSocketAddress(PORT)); joinGroup(InetAddress.getByName(GROUP)) }
        mcast = s
        val buf = ByteArray(4096)
        while (running) {
            val pk = DatagramPacket(buf, buf.size)
            try { s.receive(pk) } catch (e: Exception) { break }
            val ip = pk.address.hostAddress ?: continue
            val o = runCatching { JSONObject(String(pk.data, 0, pk.length)) }.getOrNull() ?: continue
            val peer = peerOf(o, ip) ?: continue
            peers[ip] = peer
            // an announcement is answered by registering with the sender, so it lists this phone at once
            if (o.optBoolean("announce", false)) pool.execute { runCatching { post(peer, "register", me(false).toString().toByteArray()) } }
        }
    }

    private fun peerOf(o: JSONObject, ip: String): Peer? {
        val fp = o.optString("fingerprint"); if (fp.isEmpty() || fp == fingerprint) return null
        return Peer(o.optString("alias", ip), ip, o.optInt("port", PORT), o.optString("protocol", "https") == "https", fp, o.optString("deviceModel", ""), o.optString("deviceType", "mobile"))
    }

    /** Asks every address of the local /24 for its LocalSend info; the ones that answer are listed. */
    fun scan() {
        pool.execute {
            val base = localAddress() ?: return@execute
            val prefix = base.substringBeforeLast('.')
            for (i in 1..254) {
                val ip = "$prefix.$i"
                if (ip == base) continue
                pool.execute { if (running) probe(ip) }
            }
        }
    }

    /** Looks one address up by hand (the "add by IP" field). */
    fun probe(ip: String): Boolean {
        for (https in listOf(false, true)) {
            val r = runCatching {
                val c = open(URL("${if (https) "https" else "http"}://$ip:$PORT/api/localsend/v2/info"), https)
                c.connectTimeout = 700; c.readTimeout = 1500
                try { if (c.responseCode == 200) JSONObject(c.inputStream.bufferedReader().readText()) else null } finally { c.disconnect() }
            }.getOrNull() ?: continue
            val peer = peerOf(r.put("protocol", if (https) "https" else "http").put("port", PORT), ip) ?: return false
            peers[ip] = peer
            return true
        }
        return false
    }

    private fun localAddress(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }.firstOrNull { it is java.net.Inet4Address && it.isSiteLocalAddress }?.hostAddress
    }.getOrNull()

    // ---------------------------------------------------------------- client

    private fun open(u: URL, https: Boolean): HttpURLConnection {
        val c = u.openConnection() as HttpURLConnection
        if (https && c is javax.net.ssl.HttpsURLConnection) {
            // the other LocalSend devices use self-signed certificates; the connection is on the local network
            val ctx = javax.net.ssl.SSLContext.getInstance("TLS")
            ctx.init(null, arrayOf<javax.net.ssl.TrustManager>(object : javax.net.ssl.X509TrustManager {
                override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = emptyArray()
            }), null)
            c.sslSocketFactory = ctx.socketFactory
            c.hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
        }
        return c
    }

    private fun post(p: Peer, path: String, body: ByteArray, query: String = "", timeoutMs: Int = 5000): Pair<Int, String> {
        val c = open(URL("${if (p.https) "https" else "http"}://${p.ip}:${p.port}/api/localsend/v2/$path$query"), p.https)
        try {
            c.requestMethod = "POST"; c.connectTimeout = 4000; c.readTimeout = timeoutMs; c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(body) }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.readText().orEmpty()
            return code to text
        } finally { c.disconnect() }
    }

    /** Sends [uris] to [peer]: offers them, waits for the other side to accept, then uploads each file. Returns a short result line. */
    fun send(ctx: Context, peer: Peer, uris: List<Uri>): String {
        if (uris.isEmpty()) return ""
        val cr = ctx.contentResolver
        class F(val id: String, val uri: Uri, val name: String, val size: Long, val mime: String)
        val list = uris.mapNotNull { u ->
            runCatching {
                var name = "file"; var size = -1L
                cr.query(u, null, null, null, null)?.use { q -> if (q.moveToFirst()) { q.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { name = q.getString(it) }; q.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 }?.let { size = q.getLong(it) } } }
                if (size < 0) size = cr.openAssetFileDescriptor(u, "r")?.use { it.length } ?: 0L
                F(UUID.randomUUID().toString(), u, name, size, cr.getType(u) ?: "application/octet-stream")
            }.getOrNull()
        }
        if (list.isEmpty()) return "Nothing to send"
        busy = "→ ${peer.alias}"; progress = 0f
        try {
            val files = JSONObject(); list.forEach { f -> files.put(f.id, JSONObject().put("id", f.id).put("fileName", f.name).put("size", f.size).put("fileType", f.mime)) }
            val (code, text) = post(peer, "prepare-upload", JSONObject().put("info", me()).put("files", files).toString().toByteArray(), timeoutMs = 120_000)
            when (code) {
                204 -> return "Nothing to transfer"
                403 -> return "${peer.alias} declined"
                409 -> return "${peer.alias} is busy"
                200 -> Unit
                else -> return "Error $code"
            }
            val j = JSONObject(text); val session = j.getString("sessionId"); val tokens = j.getJSONObject("files")
            val total = list.sumOf { it.size }.coerceAtLeast(1L); var sent = 0L
            for (f in list) {
                val token = tokens.optString(f.id).ifEmpty { continue }
                val c = open(URL("${if (peer.https) "https" else "http"}://${peer.ip}:${peer.port}/api/localsend/v2/upload?sessionId=$session&fileId=${f.id}&token=$token"), peer.https)
                try {
                    c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 5000; c.readTimeout = 60_000
                    c.setRequestProperty("Content-Type", f.mime)
                    if (f.size >= 0) c.setFixedLengthStreamingMode(f.size)
                    cr.openInputStream(f.uri)?.use { ins ->
                        c.outputStream.use { out ->
                            val buf = ByteArray(64 * 1024)
                            while (true) { val n = ins.read(buf); if (n < 0) break; out.write(buf, 0, n); sent += n; progress = (sent.toFloat() / total).coerceIn(0f, 1f) }
                        }
                    }
                    if (c.responseCode !in 200..299) return "Upload failed (${c.responseCode})"
                } finally { c.disconnect() }
            }
            return "Sent ${list.size} to ${peer.alias}"
        } catch (e: Exception) {
            return "Failed: ${e.message}"
        } finally { busy = ""; progress = 0f }
    }

    // ---------------------------------------------------------------- server

    private fun serve(app: Context) {
        val s = ServerSocket()
        s.reuseAddress = true
        s.bind(InetSocketAddress(PORT))
        server = s
        while (running) {
            val sock = try { s.accept() } catch (e: Exception) { break }
            pool.execute { runCatching { handle(app, sock) }; runCatching { sock.close() } }
        }
    }

    private class Req(val method: String, val path: String, val query: Map<String, String>, val headers: Map<String, String>, val body: InputStream)

    private fun readReq(sock: Socket): Req? {
        val ins = sock.getInputStream().buffered()
        val head = ByteArrayOutputStream()
        var last4 = 0
        while (true) {
            val b = ins.read(); if (b < 0) return null
            head.write(b); last4 = (last4 shl 8) or b
            if (last4 == 0x0D0A0D0A) break
            if (head.size() > 32 * 1024) return null
        }
        val lines = head.toString("ISO-8859-1").split("\r\n")
        val (method, target) = lines[0].split(" ").let { it[0] to it.getOrElse(1) { "/" } }
        val path = target.substringBefore('?')
        val query = target.substringAfter('?', "").split('&').filter { it.contains('=') }.associate { it.substringBefore('=') to java.net.URLDecoder.decode(it.substringAfter('='), "UTF-8") }
        val headers = lines.drop(1).filter { it.contains(':') }.associate { it.substringBefore(':').trim().lowercase() to it.substringAfter(':').trim() }
        val body: InputStream = if (headers["transfer-encoding"]?.contains("chunked", true) == true) ChunkedStream(ins) else LimitedStream(ins, headers["content-length"]?.toLongOrNull() ?: 0L)
        return Req(method, path, query, headers, body)
    }

    private class LimitedStream(private val i: InputStream, private var left: Long) : InputStream() {
        override fun read(): Int { if (left <= 0) return -1; val b = i.read(); if (b >= 0) left--; return b }
        override fun read(b: ByteArray, off: Int, len: Int): Int { if (left <= 0) return -1; val n = i.read(b, off, minOf(len.toLong(), left).toInt()); if (n > 0) left -= n; return n }
    }

    private class ChunkedStream(private val i: InputStream) : InputStream() {
        private var left = 0L; private var done = false
        private fun line(): String { val sb = StringBuilder(); while (true) { val c = i.read(); if (c < 0 || c == '\n'.code) break; if (c != '\r'.code) sb.append(c.toChar()) }; return sb.toString() }
        private fun next(): Boolean { if (done) return false; if (left == 0L) { val l = line().substringBefore(';').trim(); left = l.toLongOrNull(16) ?: 0L; if (left == 0L) { done = true; return false } }; return true }
        override fun read(): Int { if (!next()) return -1; val b = i.read(); left--; if (left == 0L) line(); return b }
        override fun read(b: ByteArray, off: Int, len: Int): Int { if (!next()) return -1; val n = i.read(b, off, minOf(len.toLong(), left).toInt()); if (n > 0) { left -= n; if (left == 0L) line() }; return n }
    }

    private fun reply(sock: Socket, code: Int, body: String = "", type: String = "application/json") {
        val bytes = body.toByteArray()
        val msg = mapOf(200 to "OK", 204 to "No Content", 400 to "Bad Request", 403 to "Forbidden", 404 to "Not Found", 409 to "Conflict", 500 to "Server Error")[code] ?: "OK"
        val out: OutputStream = sock.getOutputStream()
        out.write("HTTP/1.1 $code $msg\r\nContent-Type: $type\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
        out.write(bytes); out.flush()
    }

    private fun handle(app: Context, sock: Socket) {
        sock.soTimeout = 120_000
        val r = readReq(sock) ?: return
        val ip = sock.inetAddress.hostAddress.orEmpty()
        val route = r.path.substringAfter("/api/localsend/v2/", "").ifEmpty { r.path.substringAfter("/api/localsend/v1/", "").let { if (it == "send-request") "prepare-upload" else if (it == "send") "upload" else it } }
        when {
            r.method == "GET" && route == "info" -> reply(sock, 200, me().toString())
            r.method == "POST" && route == "register" -> {
                val o = runCatching { JSONObject(r.body.readBytes().toString(Charsets.UTF_8)) }.getOrNull()
                if (o != null) peerOf(o, ip)?.let { peers[ip] = it }
                reply(sock, 200, me().toString())
            }
            r.method == "POST" && route == "prepare-upload" -> prepare(app, sock, r, ip)
            r.method == "POST" && route == "upload" -> upload(app, sock, r)
            r.method == "POST" && route == "cancel" -> { r.query["sessionId"]?.let { sessions.remove(it) }; reply(sock, 200) }
            else -> reply(sock, 404)
        }
    }

    private fun prepare(app: Context, sock: Socket, r: Req, ip: String) {
        val o = runCatching { JSONObject(r.body.readBytes().toString(Charsets.UTF_8)) }.getOrNull() ?: return reply(sock, 400)
        val from = o.optJSONObject("info")?.optString("alias").orEmpty().ifEmpty { ip }
        val fj = o.optJSONObject("files") ?: return reply(sock, 400)
        val offered = fj.keys().asSequence().map { k -> fj.getJSONObject(k).let { OfferedFile(it.optString("id", k), it.optString("fileName", "file"), it.optLong("size", 0), it.optString("fileType", "")) } }.toList()
        if (offered.isEmpty()) return reply(sock, 204)
        if (incoming != null) return reply(sock, 409)
        val session = UUID.randomUUID().toString()
        val d = CompletableFuture<Boolean>()
        incoming = Incoming(session, from, offered, d)
        val ok = runCatching { d.get(60, TimeUnit.SECONDS) }.getOrDefault(false)
        incoming = null
        if (!ok) return reply(sock, 403)
        val tokens = offered.associate { it.id to UUID.randomUUID().toString() }
        sessions[session] = Session(saveDir(app), offered.associateBy { it.id }, tokens)
        reply(sock, 200, JSONObject().put("sessionId", session).put("files", JSONObject(tokens)).toString())
    }

    private fun upload(app: Context, sock: Socket, r: Req) {
        val s = sessions[r.query["sessionId"]] ?: return reply(sock, 403)
        val id = r.query["fileId"] ?: return reply(sock, 400)
        if (s.tokens[id] != r.query["token"]) return reply(sock, 403)
        val f = s.files[id] ?: return reply(sock, 404)
        s.dir.mkdirs()
        // the file name comes from the sender: only its last part is used, so it can never leave the folder
        val clean = f.name.substringAfterLast('/').substringAfterLast('\\').ifBlank { "file" }
        var out = File(s.dir, clean); var n = 1
        while (out.exists()) { out = File(s.dir, clean.substringBeforeLast('.', clean) + " ($n)" + (clean.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" })); n++ }
        busy = "← ${f.name}"; progress = 0f
        try {
            var got = 0L; val total = f.size.coerceAtLeast(1L)
            out.outputStream().use { o -> val buf = ByteArray(64 * 1024); while (true) { val k = r.body.read(buf); if (k < 0) break; o.write(buf, 0, k); got += k; progress = (got.toFloat() / total).coerceIn(0f, 1f) } }
            received.add(0, out.path)
            runCatching { android.media.MediaScannerConnection.scanFile(app, arrayOf(out.path), null, null) }
            reply(sock, 200)
        } catch (e: Exception) { out.delete(); reply(sock, 500) } finally { busy = ""; progress = 0f }
    }
}
