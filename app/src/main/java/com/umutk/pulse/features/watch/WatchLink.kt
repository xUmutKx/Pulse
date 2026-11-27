package com.umutk.pulse.features.watch

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.os.StatFs
import android.util.Base64
import com.umutk.pulse.QuietChannel
import com.umutk.pulse.tr
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.RandomAccessFile
import java.util.UUID

/**
 * The phone side of Pulse Watch (wear/PLAN.md, part 0): a Bluetooth RFCOMM server the watch connects to (no Google Play services). The watch must be paired
 * with the phone (bonded); anything else is turned away. One line of JSON each way: {"t": type, "id": n, "b": body}; the answer has the same id.
 * It answers: hello, status, media (now playing of any app, through the media sessions Pulse's notification access sees), mcmd (play, pause, next, previous,
 * volume, seek), find (ring the phone even when silent, the torch blinks), torch, ringer, list (a folder of the shared storage), get (a 48 KB piece of a file).
 */
object WatchLink {
    const val VERSION = 1
    val UUID_LINK: UUID = UUID.fromString("6f1d2b52-8a3e-4c35-9c7e-50756c736557")
    private const val CHUNK = 48 * 1024

    fun enabled(c: Context) = c.getSharedPreferences("watch_link", Context.MODE_PRIVATE).getBoolean("on", false)
    fun setEnabled(c: Context, on: Boolean) {
        c.getSharedPreferences("watch_link", Context.MODE_PRIVATE).edit().putBoolean("on", on).apply()
        val i = Intent(c, WatchLinkService::class.java)
        if (on) runCatching { if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i) else c.startService(i) } else c.stopService(i)
    }

    /** Answers one request of the watch. */
    fun answer(c: Context, t: String, b: JSONObject): JSONObject = when (t) {
        "hello" -> JSONObject().put("v", VERSION).put("name", Build.MODEL)
        "status" -> status(c)
        "media" -> media(c, b.optBoolean("art", false))
        "mcmd" -> { mediaCmd(c, b.optString("c"), b.optLong("ms")); JSONObject().put("ok", true) }
        "find" -> { Finder.set(c, b.optBoolean("on")); JSONObject().put("on", Finder.on) }
        "torch" -> { torch(c, b.optBoolean("on")); JSONObject().put("on", torchOn) }
        "ringer" -> { runCatching { (c.getSystemService(Context.AUDIO_SERVICE) as AudioManager).ringerMode = b.optInt("m", AudioManager.RINGER_MODE_NORMAL) }; status(c) }
        "list" -> list(b.optString("p"))
        "get" -> piece(b.optString("p"), b.optLong("off"))
        else -> JSONObject().put("err", "unknown")
    }

    private fun status(c: Context): JSONObject {
        val bat = c.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val lvl = bat?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = bat?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val plugged = (bat?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        val temp = (bat?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10.0
        val st = runCatching { StatFs(Environment.getExternalStorageDirectory().path) }.getOrNull()
        val am = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return JSONObject().put("bat", if (lvl >= 0) lvl * 100 / scale.coerceAtLeast(1) else -1).put("chg", plugged).put("temp", temp)
            .put("free", st?.availableBytes ?: 0L).put("total", st?.totalBytes ?: 0L).put("ringer", am.ringerMode).put("torch", torchOn).put("find", Finder.on)
            .put("name", Build.MODEL)
    }

    // ---- media: the sessions of every app (Pulse's notification access lets it see them) ----
    private val listeners = listOf(
        "com.umutk.pulse.extras.notifhist.NotifHistListener", "com.umutk.pulse.extras.island.IslandListenerService", "com.umutk.pulse.extras.lyrics.LyricsListenerService",
    )

    private fun controller(c: Context): MediaController? {
        val msm = c.getSystemService(MediaSessionManager::class.java) ?: return null
        for (cls in listeners) {
            val list = runCatching { msm.getActiveSessions(ComponentName(c, cls)) }.getOrNull() ?: continue
            return list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list.firstOrNull()
        }
        return null
    }

    private fun media(c: Context, art: Boolean): JSONObject {
        val am = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val o = JSONObject().put("vol", am.getStreamVolume(AudioManager.STREAM_MUSIC)).put("max", am.getStreamMaxVolume(AudioManager.STREAM_MUSIC))
        val mc = controller(c) ?: return o.put("none", true)
        val md = mc.metadata
        val ps = mc.playbackState
        o.put("title", md?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()).put("artist", md?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty())
            .put("app", runCatching { c.packageManager.getApplicationLabel(c.packageManager.getApplicationInfo(mc.packageName, 0)).toString() }.getOrDefault(mc.packageName))
            .put("playing", ps?.state == PlaybackState.STATE_PLAYING).put("pos", ps?.position ?: 0L).put("dur", md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L)
        if (art) {
            val bmp = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            if (bmp != null) runCatching {
                val s = Bitmap.createScaledBitmap(bmp, 200, (200f * bmp.height / bmp.width.coerceAtLeast(1)).toInt().coerceIn(1, 400), true)
                val out = ByteArrayOutputStream(); s.compress(Bitmap.CompressFormat.JPEG, 80, out)
                o.put("art", Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP))
            }
        }
        return o
    }

    private fun mediaCmd(c: Context, cmd: String, ms: Long) {
        val am = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        when (cmd) {
            "volup" -> { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0); return }
            "voldn" -> { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0); return }
        }
        val t = controller(c)?.transportControls ?: return
        when (cmd) {
            "play" -> t.play(); "pause" -> t.pause(); "next" -> t.skipToNext(); "prev" -> t.skipToPrevious(); "seek" -> t.seekTo(ms)
            "toggle" -> if (controller(c)?.playbackState?.state == PlaybackState.STATE_PLAYING) t.pause() else t.play()
        }
    }

    // ---- torch ----
    @Volatile var torchOn = false
    fun torch(c: Context, on: Boolean) {
        val cm = c.getSystemService(CameraManager::class.java) ?: return
        val id = runCatching { cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true } }.getOrNull() ?: return
        if (runCatching { cm.setTorchMode(id, on) }.isSuccess) torchOn = on
    }

    /** Find my phone: the alarm sound at full volume (the alarm stream rings when the phone is silent), the torch blinks; until the watch or the phone stops it. */
    object Finder {
        @Volatile var on = false
        private var mp: MediaPlayer? = null
        private var oldVol = -1
        private var blink: Thread? = null
        fun set(c: Context, v: Boolean) { if (v) start(c) else stop(c) }
        @Synchronized private fun start(c: Context) {
            if (on) return
            on = true
            val am = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            oldVol = am.getStreamVolume(AudioManager.STREAM_ALARM)
            runCatching { am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0) }
            mp = runCatching {
                MediaPlayer().apply {
                    setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    setDataSource(c, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
                    isLooping = true; prepare(); start()
                }
            }.getOrNull()
            blink = Thread {
                var k = 0
                // the torch blinks for two minutes at most
                while (on && k < 240) { torch(c, k % 2 == 0); k++; runCatching { Thread.sleep(500L) } }
                torch(c, false)
                if (on) stop(c)
            }.apply { isDaemon = true; start() }
        }
        @Synchronized fun stop(c: Context) {
            on = false
            runCatching { mp?.stop(); mp?.release() }; mp = null
            if (oldVol >= 0) runCatching { (c.getSystemService(Context.AUDIO_SERVICE) as AudioManager).setStreamVolume(AudioManager.STREAM_ALARM, oldVol, 0) }
            oldVol = -1
        }
    }

    // ---- files: the shared storage only (never the private folders of apps) ----
    private val root get() = Environment.getExternalStorageDirectory()
    private fun safe(p: String): File? {
        val f = if (p.isBlank()) root else File(p)
        val cp = runCatching { f.canonicalPath }.getOrNull() ?: return null
        if (!cp.startsWith(root.canonicalPath)) return null
        if (cp.startsWith(File(root, "Android/data").canonicalPath) || cp.startsWith(File(root, "Android/obb").canonicalPath)) return null
        return f
    }

    private fun list(p: String): JSONObject {
        val d = safe(p) ?: return JSONObject().put("err", "denied")
        val arr = JSONArray()
        (d.listFiles() ?: emptyArray()).filter { !it.name.startsWith(".") }
            .sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() })).take(400)
            .forEach { f -> arr.put(JSONObject().put("n", f.name).put("d", f.isDirectory).put("s", if (f.isFile) f.length() else 0L).put("m", f.lastModified())) }
        return JSONObject().put("p", d.path).put("root", root.path).put("items", arr)
    }

    private fun piece(p: String, off: Long): JSONObject {
        val f = safe(p)?.takeIf { it.isFile } ?: return JSONObject().put("err", "denied")
        return RandomAccessFile(f, "r").use { r ->
            r.seek(off.coerceAtLeast(0L))
            val buf = ByteArray(CHUNK); val n = r.read(buf).coerceAtLeast(0)
            JSONObject().put("size", f.length()).put("off", off).put("n", n).put("data", Base64.encodeToString(buf, 0, n, Base64.NO_WRAP))
        }
    }
}

/** The foreground service that keeps the Bluetooth server open while "Watch link" is on. */
class WatchLinkService : Service() {
    @Volatile private var server: BluetoothServerSocket? = null
    @Volatile private var alive = true
    private val clients = java.util.concurrent.CopyOnWriteArrayList<BluetoothSocket>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(QuietChannel("watch_link", tr("Watch link", "Saat bağlantısı"), NotificationManager.IMPORTANCE_MIN))
        val n = (if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, "watch_link") else @Suppress("DEPRECATION") Notification.Builder(this))
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth).setContentTitle(tr("Pulse Watch link", "Pulse Watch bağlantısı"))
            .setContentText(tr("Waiting for the watch", "Saat bekleniyor")).setOngoing(true).build()
        runCatching {
            if (Build.VERSION.SDK_INT >= 34) startForeground(7351, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(7351, n)
        }
        Thread({ serve() }, "watch-link").apply { isDaemon = true; start() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    @SuppressLint("MissingPermission")
    private fun serve() {
        val bt = getSystemService(BluetoothManager::class.java)?.adapter ?: return stopSelf()
        while (alive) {
            val s = runCatching { server ?: bt.listenUsingInsecureRfcommWithServiceRecord("Pulse Watch", WatchLink.UUID_LINK).also { server = it } }.getOrNull()
            if (s == null) { runCatching { Thread.sleep(15_000L) }; continue }
            val sock = runCatching { s.accept() }.getOrNull()
            if (sock == null) { runCatching { server?.close() }; server = null; runCatching { Thread.sleep(3_000L) }; continue }
            // only a watch paired with this phone
            if (sock.remoteDevice?.bondState != BluetoothDevice.BOND_BONDED) { runCatching { sock.close() }; continue }
            clients.add(sock)
            Thread({ client(sock) }, "watch-client").apply { isDaemon = true; start() }
        }
    }

    private fun client(sock: BluetoothSocket) {
        val out: OutputStream = sock.outputStream
        runCatching {
            BufferedReader(InputStreamReader(sock.inputStream, Charsets.UTF_8)).use { r ->
                while (alive) {
                    val line = r.readLine() ?: break
                    val q = runCatching { JSONObject(line) }.getOrNull() ?: continue
                    val id = q.optInt("id")
                    val body = runCatching { WatchLink.answer(this, q.optString("t"), q.optJSONObject("b") ?: JSONObject()) }.getOrElse { JSONObject().put("err", it.message ?: "error") }
                    val reply = JSONObject().put("t", "ok").put("id", id).put("b", body).toString() + "\n"
                    synchronized(out) { out.write(reply.toByteArray(Charsets.UTF_8)); out.flush() }
                }
            }
        }
        clients.remove(sock); runCatching { sock.close() }
    }

    override fun onDestroy() {
        alive = false
        runCatching { server?.close() }
        clients.forEach { runCatching { it.close() } }
        WatchLink.Finder.stop(this)
        super.onDestroy()
    }
}
