package com.umutk.pulse.watch.link

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * The watch side of the link to Pulse on the phone (wear/PLAN.md part 0): a Bluetooth RFCOMM socket to the paired phone, where Pulse keeps a server open while
 * Device manager > Watch > Pulse Watch link is on. One line of JSON each way, {"t", "id", "b"}; [ask] sends a request and waits for the answer with the same id.
 */
object PhoneLink {
    private val UUID_LINK: UUID = UUID.fromString("6f1d2b52-8a3e-4c35-9c7e-50756c736557")
    enum class State { OFF, CONNECTING, ON, FAILED }
    var state by mutableStateOf(State.OFF)
    var phoneName by mutableStateOf("")
    var error by mutableStateOf("")

    private var sock: BluetoothSocket? = null
    private val ids = AtomicInteger(1)
    private val waiting = ConcurrentHashMap<Int, CompletableDeferred<JSONObject>>()
    private val lock = Any()

    /** Opens the link if it is not open: tries the phone that worked last, then every paired device. */
    @SuppressLint("MissingPermission")
    suspend fun connect(c: Context): Boolean = withContext(Dispatchers.IO) {
        synchronized(lock) { if (sock?.isConnected == true) return@withContext true }
        state = State.CONNECTING; error = ""
        val ad = c.getSystemService(BluetoothManager::class.java)?.adapter
        if (ad == null || !ad.isEnabled) { state = State.FAILED; error = "Bluetooth off"; return@withContext false }
        val prefs = c.getSharedPreferences("watch", Context.MODE_PRIVATE)
        val last = prefs.getString("phone", null)
        val devices = runCatching { ad.bondedDevices.toList() }.getOrElse { state = State.FAILED; error = "No Bluetooth permission"; return@withContext false }
            .sortedBy { if (it.address == last) 0 else 1 }
        for (d in devices) {
            val s = runCatching { d.createInsecureRfcommSocketToServiceRecord(UUID_LINK).also { it.connect() } }.getOrNull() ?: continue
            synchronized(lock) { sock = s }
            Thread({ read(s) }, "phone-link").apply { isDaemon = true; start() }
            val hello = runCatching { ask("hello") }.getOrNull()
            if (hello != null && hello.has("v")) {
                prefs.edit().putString("phone", d.address).apply()
                phoneName = hello.optString("name", d.name ?: "Phone"); state = State.ON
                return@withContext true
            }
            close()
        }
        state = State.FAILED; error = "Pulse on the phone did not answer"
        false
    }

    private fun read(s: BluetoothSocket) {
        runCatching {
            BufferedReader(InputStreamReader(s.inputStream, Charsets.UTF_8)).use { r ->
                while (true) {
                    val line = r.readLine() ?: break
                    val o = runCatching { JSONObject(line) }.getOrNull() ?: continue
                    waiting.remove(o.optInt("id"))?.complete(o.optJSONObject("b") ?: JSONObject())
                }
            }
        }
        synchronized(lock) { if (sock === s) { sock = null; if (state == State.ON) state = State.OFF } }
        waiting.values.forEach { it.completeExceptionally(IllegalStateException("link closed")) }; waiting.clear()
    }

    /** Sends one request and waits for its answer (15 s at most). */
    suspend fun ask(t: String, b: JSONObject = JSONObject(), timeoutMs: Long = 15_000L): JSONObject = withContext(Dispatchers.IO) {
        val s = synchronized(lock) { sock } ?: throw IllegalStateException("not connected")
        val id = ids.getAndIncrement()
        val d = CompletableDeferred<JSONObject>(); waiting[id] = d
        val line = JSONObject().put("t", t).put("id", id).put("b", b).toString() + "\n"
        try {
            synchronized(s) { s.outputStream.write(line.toByteArray(Charsets.UTF_8)); s.outputStream.flush() }
            withTimeout(timeoutMs) { d.await() }
        } finally { waiting.remove(id) }
    }

    /** [ask], connecting first when needed; null when the phone cannot be reached. */
    suspend fun tryAsk(c: Context, t: String, b: JSONObject = JSONObject()): JSONObject? {
        if (state != State.ON && !connect(c)) return null
        return runCatching { ask(t, b) }.getOrElse { close(); if (connect(c)) runCatching { ask(t, b) }.getOrNull() else null }
    }

    fun close() {
        synchronized(lock) { runCatching { sock?.close() }; sock = null }
        if (state == State.ON) state = State.OFF
    }
}
