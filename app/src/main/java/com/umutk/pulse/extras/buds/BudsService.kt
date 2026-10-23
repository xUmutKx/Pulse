package com.umutk.pulse.extras.buds

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.IBinder
import android.os.SystemClock
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import com.umutk.pulse.R
import com.umutk.pulse.extras.music.MusicPlayerEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.UUID

/**
 * Holds the Bluetooth serial link to the chosen Galaxy Buds. It reads their status (battery, which bud is in the ear, the noise control mode) and acts on it: when both come
 * out the music stops and the noise control goes off, when one goes back in the music carries on and the noise control returns to what it was.
 */
class BudsService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var socket: BluetoothSocket? = null
    private var pausedByUs = false
    private var noiseBefore = -1
    private var lastWorn: Boolean? = null
    private var gotStatus = false

    private val acl = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val d = i.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
            if (d.address == BudsPrefs.address(c) && i.action == BluetoothDevice.ACTION_ACL_CONNECTED) wake = true
        }
    }
    @Volatile private var wake = false

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        if (android.os.Build.VERSION.SDK_INT >= 29) startForeground(ID, note(), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE) else startForeground(ID, note())
        if (job == null) {
            ContextCompat.registerReceiver(this, acl, IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED), ContextCompat.RECEIVER_EXPORTED)
            job = scope.launch { loop() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(acl) }
        runCatching { socket?.close() }
        BudsLink.sender = null
        BudsLink.set(BudsState())
        scope.cancel()
        super.onDestroy()
    }

    private fun note(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (android.os.Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("buds", "Galaxy Buds", NotificationManager.IMPORTANCE_MIN))
        val s = BudsLink.state.value
        val text = if (s.connected) listOfNotNull(s.name.ifBlank { null }, s.batteryL.takeIf { it >= 0 }?.let { "L $it%" }, s.batteryR.takeIf { it >= 0 }?.let { "R $it%" }).joinToString("  ") else "…"
        val stop = android.app.PendingIntent.getService(this, 0, Intent(this, BudsService::class.java).setAction(ACTION_STOP), android.app.PendingIntent.FLAG_IMMUTABLE)
        return androidx.core.app.NotificationCompat.Builder(this, "buds").setSmallIcon(R.drawable.ic_notif_music).setContentTitle("Galaxy Buds").setContentText(text).setOngoing(true)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_MIN).addAction(0, "×", stop).build()
    }

    private fun refreshNote() { getSystemService(NotificationManager::class.java).notify(ID, note()) }

    private suspend fun loop() {
        while (scope.isActive) {
            val addr = BudsPrefs.address(this)
            val adapter = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
            if (addr == null || adapter == null || !adapter.isEnabled) { delay(10_000); continue }
            try { session(adapter, adapter.getRemoteDevice(addr)) } catch (_: Exception) { }
            BudsLink.sender = null
            BudsLink.set(BudsState(message = "—"))
            runCatching { socket?.close() }
            // a new attempt after a while, at once when the earbuds connect
            var waited = 0
            while (waited < 20_000 && !wake) { delay(500); waited += 500 }
            wake = false
        }
    }

    private fun uuidFor(name: String): UUID =
        if (name.contains("Buds Live") || name.contains("Buds+") || name.contains("Buds Pro") && !name.contains("Buds2") && !name.contains("Buds3") && !name.contains("Buds4")) UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        else UUID.fromString("2e73a4ad-332d-41fc-90e2-16bef06523f2")

    private suspend fun session(adapter: BluetoothAdapter, dev: BluetoothDevice) {
        val name = runCatching { dev.name }.getOrNull().orEmpty()
        adapter.cancelDiscovery()
        val s = dev.createRfcommSocketToServiceRecord(uuidFor(name))
        socket = s
        s.connect()
        val out = s.outputStream
        BudsLink.sender = { b -> runCatching { synchronized(out) { out.write(b); out.flush() } } }
        BudsLink.set(BudsState(connected = true, name = name))
        gotStatus = false; lastWorn = null
        refreshNote()
        val input = s.inputStream
        val buf = ByteArray(2048)
        val acc = ByteArrayOutputStream()
        while (scope.isActive) {
            val n = input.read(buf)
            if (n < 0) break
            acc.write(buf, 0, n)
            for (m in BudsProto.parse(acc)) handle(m, name)
        }
    }

    private fun handle(m: BudsProto.Msg, name: String) {
        val p = m.payload
        fun u(i: Int) = p.getOrElse(i) { 0 }.toInt() and 0xFF
        when (m.id) {
            BudsProto.STATUS_UPDATED, BudsProto.EXTENDED_STATUS_UPDATED -> {
                val ext = m.id == BudsProto.EXTENDED_STATUS_UPDATED
                val placement = if (ext) u(6) else u(5)
                val l = (placement and 0xF0) shr 4; val r = placement and 0x0F
                val bl = if (ext) u(2) else u(1); val br = if (ext) u(3) else u(2)
                val cs = if (ext) u(7) else -1
                // Pro and newer: the noise control mode is in the long status
                val canNoise = !name.contains("Buds Live") && !name.contains("Buds+")
                val mode = if (ext && canNoise && p.size > 12) u(12).takeIf { it in 0..3 } ?: -1 else -1
                BudsLink.update { it.copy(connected = true, batteryL = bl, batteryR = br, batteryCase = if (cs in 0..100) cs else it.batteryCase, wornL = l == 1, wornR = r == 1, canNoise = canNoise, noise = if (mode >= 0) mode else it.noise) }
                // the long status also carries the settings of the earbuds (layout of the GalaxyBudsClient notes, Buds Live and newer)
                if (ext && !name.contains("Buds+") && p.size > 24) {
                    val pro = name.contains("Buds2") || name.contains("Buds3") || name.contains("Buds Pro")
                    val eqm = u(9)
                    val bits = u(21)
                    BudsLink.update {
                        it.copy(
                            revision = u(0), eq = eqm, lock = (u(10) and 0x80) == 0, touchL = (u(11) shr 4) and 0x0F, touchR = u(11) and 0x0F,
                            seamless = u(19) == 0, ambientVolume = u(23), ancHigh = u(24) == 1,
                            // 0 ANC and off, 1 ambient and off, 2 ANC and ambient (the bits of the touch-and-hold cycle)
                            cycle = when { bits and 1 != 0 && bits and 4 != 0 && bits and 2 == 0 -> 0; bits and 1 != 0 && bits and 2 != 0 && bits and 4 == 0 -> 1; bits and 2 != 0 && bits and 4 != 0 -> 2; else -> it.cycle },
                            voiceDetect = if (name.contains("Buds2 Pro") || name.contains("Buds Pro") || name.contains("Buds3")) u(26) == 1 else it.voiceDetect,
                            oneBud = if (pro && p.size > 28) u(28) == 1 else it.oneBud,
                            sidetone = if (pro && p.size > 33) u(33) == 1 else it.sidetone,
                            spatial = if (name.contains("Buds2") && p.size > 35) u(35) == 1 else it.spatial,
                        )
                    }
                }
                // the earbuds expect the status to be answered, and then to hear who is talking
                BudsLink.sender?.invoke(BudsProto.encode(m.id, p, response = true))
                if (!gotStatus) {
                    gotStatus = true
                    BudsLink.sender?.invoke(BudsProto.encode(BudsProto.MANAGER_INFO, byteArrayOf(1, 1, android.os.Build.VERSION.SDK_INT.toByte())))
                    val t = java.nio.ByteBuffer.allocate(12).order(java.nio.ByteOrder.LITTLE_ENDIAN).putLong(System.currentTimeMillis()).putInt(java.util.TimeZone.getDefault().rawOffset).array()
                    BudsLink.sender?.invoke(BudsProto.encode(BudsProto.UPDATE_TIME, t))
                }
                // the music player may only be touched on the main thread
                val worn = l == 1 || r == 1
                android.os.Handler(mainLooper).post { wearChanged(worn) }
                refreshNote()
            }
            BudsProto.NOISE_CONTROLS_UPDATE -> BudsLink.update { it.copy(noise = u(0).takeIf { v -> v in 0..3 } ?: it.noise) }
        }
    }

    /** At least one earbud in the ear, or none: acts when that changes. */
    private fun wearChanged(worn: Boolean) {
        val before = lastWorn
        lastWorn = worn
        if (before == null || before == worn) return
        val c = this
        if (!worn) {
            if (BudsPrefs.pauseOnRemove(c)) pausedByUs = pauseMusic()
            if (BudsPrefs.noiseOffOnRemove(c)) {
                val cur = BudsLink.state.value
                if (cur.canNoise && cur.noise > 0) { noiseBefore = cur.noise; BudsLink.setNoise(0) }
            }
        } else if (BudsPrefs.resumeOnWear(c)) {
            if (noiseBefore > 0) { BudsLink.setNoise(noiseBefore); noiseBefore = -1 }
            if (pausedByUs) { pausedByUs = false; playMusic() }
        }
    }

    private fun key(code: Int) {
        val am = getSystemService(AudioManager::class.java)
        val t = SystemClock.uptimeMillis()
        am.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0))
        am.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0))
    }

    /** True when something was playing and is stopped now: Pulse's own player, or any other app through the media key. */
    private fun pauseMusic(): Boolean {
        val e = runCatching { MusicPlayerEngine.get(this) }.getOrNull()
        if (e != null && e.player.playWhenReady) { e.pause(); return true }
        val am = getSystemService(AudioManager::class.java)
        if (am.isMusicActive) { key(KeyEvent.KEYCODE_MEDIA_PAUSE); return true }
        return false
    }

    private fun playMusic() {
        val e = runCatching { MusicPlayerEngine.get(this) }.getOrNull()
        if (e != null && e.player.mediaItemCount > 0 && !e.player.playWhenReady) e.play()
        else key(KeyEvent.KEYCODE_MEDIA_PLAY)
    }

    companion object {
        const val ID = 5150
        const val ACTION_STOP = "com.umutk.pulse.buds.STOP"
        fun start(c: Context) { ContextCompat.startForegroundService(c, Intent(c, BudsService::class.java)) }
        fun stop(c: Context) { c.stopService(Intent(c, BudsService::class.java)) }
        val running get() = BudsLink.state.value.connected
    }
}
