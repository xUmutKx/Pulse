package com.umutk.pulse.extras.speedtest

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

/** The numbers of one measurement. */
class SpeedResult(val pingMs: Float, val jitterMs: Float, val down: Float, val up: Float, val isp: String, val place: String, val ip: String, val time: Long) {
    fun save(c: Context) = c.getSharedPreferences("speedtest", Context.MODE_PRIVATE).edit().putString("last", JSONObject().put("p", pingMs.toDouble()).put("j", jitterMs.toDouble()).put("d", down.toDouble()).put("u", up.toDouble())
        .put("i", isp).put("l", place).put("a", ip).put("t", time).toString()).apply()
    companion object {
        fun last(c: Context): SpeedResult? = runCatching {
            val o = JSONObject(c.getSharedPreferences("speedtest", Context.MODE_PRIVATE).getString("last", null) ?: return null)
            SpeedResult(o.getDouble("p").toFloat(), o.getDouble("j").toFloat(), o.getDouble("d").toFloat(), o.getDouble("u").toFloat(), o.optString("i"), o.optString("l"), o.optString("a"), o.optLong("t"))
        }.getOrNull()
    }
}

/** Where the test is: [phase] is idle, ping, down, up, done or error; [value] is the number shown on the dial (ms for ping, Mbit/s for the others). */
class SpeedLive(val phase: String, val value: Float, val ping: Float = 0f, val jitter: Float = 0f, val down: Float = 0f, val up: Float = 0f, val isp: String = "", val place: String = "", val ip: String = "")

/**
 * An internet speed test against Cloudflare's public test servers (no account, no key): the round trip time, the download over four streams and the upload over
 * three, each for a few seconds, plus who the provider is. It runs on its own scope so a quick tile and the page can both watch it.
 */
object SpeedTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private val live = MutableStateFlow(SpeedLive("idle", 0f))
    val state: StateFlow<SpeedLive> = live.asStateFlow()
    val running get() = job?.isActive == true

    fun start(c: Context) {
        if (running) return
        val app = c.applicationContext
        job = scope.launch {
            live.value = SpeedLive("ping", 0f)
            val r = runCatching { measure(app) }.getOrNull()
            if (r != null) { r.save(app); live.value = SpeedLive("done", r.down, r.pingMs, r.jitterMs, r.down, r.up, r.isp, r.place, r.ip) }
            else if (isActive) live.value = SpeedLive("error", 0f)
        }
    }

    fun cancel() { job?.cancel(); live.value = SpeedLive("idle", 0f) }

    private const val HOST = "https://speed.cloudflare.com"

    private fun open(path: String, post: Boolean = false): HttpURLConnection = (URL(HOST + path).openConnection() as HttpURLConnection).apply {
        connectTimeout = 8000; readTimeout = 12000; useCaches = false
        setRequestProperty("User-Agent", "Pulse speed test")
        if (post) { requestMethod = "POST"; doOutput = true }
    }

    private suspend fun measure(c: Context): SpeedResult = coroutineScope {
        // who and where
        var isp = ""; var place = ""; var ip = ""
        withContext(Dispatchers.IO) {
            runCatching {
                val con = open("/meta")
                val o = JSONObject(con.inputStream.bufferedReader().readText())
                isp = o.optString("asOrganization"); ip = o.optString("clientIp")
                place = listOf(o.optString("city"), o.optString("country")).filter { it.isNotBlank() }.joinToString(", ")
                con.disconnect()
            }
        }
        live.value = SpeedLive("ping", 0f, isp = isp, place = place, ip = ip)
        // the round trip: small requests one after the other on one connection; the first (it sets the connection up) does not count
        val times = ArrayList<Float>()
        withContext(Dispatchers.IO) {
            for (i in 0 until 9) {
                val t0 = System.nanoTime()
                val con = open("/__down?bytes=0")
                con.inputStream.use { it.readBytes() }
                val ms = (System.nanoTime() - t0) / 1_000_000f
                if (i > 0) { times += ms; live.value = SpeedLive("ping", times.min(), isp = isp, place = place, ip = ip) }
                delay(60)
            }
        }
        val ping = times.minOrNull() ?: 0f
        val jitter = if (times.size > 1) times.zipWithNext { a, b -> abs(a - b) }.average().toFloat() else 0f
        // the download
        val down = transfer(false, 8000, 4) { v -> live.value = SpeedLive("down", v, ping, jitter, isp = isp, place = place, ip = ip) }
        // the upload
        val up = transfer(true, 6000, 3) { v -> live.value = SpeedLive("up", v, ping, jitter, down, isp = isp, place = place, ip = ip) }
        SpeedResult(ping, jitter, down, up, isp, place, ip, System.currentTimeMillis())
    }

    /** Moves data on [streams] connections for [ms] and reports the speed in Mbit/s as it goes; the result leaves out the first moments, while the connections speed up. */
    private suspend fun transfer(upload: Boolean, ms: Long, streams: Int, onSpeed: (Float) -> Unit): Float = coroutineScope {
        val bytes = AtomicLong()
        val t0 = System.nanoTime()
        val end = t0 + ms * 1_000_000L
        val workers = List(streams) {
            async(Dispatchers.IO) {
                val buf = ByteArray(64 * 1024)
                while (isActive && System.nanoTime() < end) {
                    val con = if (upload) open("/__up", true) else open("/__down?bytes=40000000")
                    try {
                        if (upload) {
                            con.setFixedLengthStreamingMode(8_000_000L)
                            con.outputStream.use { o ->
                                var left = 8_000_000
                                while (left > 0 && isActive && System.nanoTime() < end) { val n = minOf(left, buf.size); o.write(buf, 0, n); bytes.addAndGet(n.toLong()); left -= n }
                            }
                            runCatching { con.responseCode }
                        } else con.inputStream.use { i ->
                            while (isActive && System.nanoTime() < end) { val n = i.read(buf); if (n < 0) break; bytes.addAndGet(n.toLong()) }
                        }
                    } catch (_: Exception) {
                    } finally { con.disconnect() }
                }
            }
        }
        // the speed every 200 ms over the last second, smoothed
        var shown = 0f
        var marked = -1L; var markT = 0L
        val ring = ArrayDeque<Pair<Long, Long>>()
        while (isActive && System.nanoTime() < end) {
            delay(200)
            val now = System.nanoTime(); val b = bytes.get()
            ring.addLast(now to b); while (ring.size > 6) ring.removeFirst()
            if (ring.size > 1) {
                val (t1, b1) = ring.first()
                val v = (b - b1) * 8f / ((now - t1) / 1e9f) / 1e6f
                shown += (v - shown) * .5f
                onSpeed(shown)
            }
            // after the first 1.5 s the count for the result starts
            if (marked < 0 && now - t0 > 1_500_000_000L) { marked = b; markT = now }
        }
        workers.awaitAll()
        val now = System.nanoTime()
        if (marked < 0) (bytes.get() * 8f / ((now - t0) / 1e9f) / 1e6f) else ((bytes.get() - marked) * 8f / ((now - markT) / 1e9f) / 1e6f)
    }
}
