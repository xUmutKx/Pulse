package com.umutk.pulse.extras.relay

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.OpenableColumns
import androidx.core.app.NotificationCompat
import com.vythera.relay.node.NodeConfig
import com.vythera.relay.node.NodeEvent
import com.vythera.relay.node.JsonFileTrustStore
import com.vythera.relay.node.RelayNode
import com.vythera.relay.protocol.DeviceType
import com.vythera.relay.protocol.Platform
import com.vythera.relay.security.IdentityCodec
import com.vythera.relay.security.IdentityStore
import com.vythera.relay.security.RelayIdentity
import com.vythera.relay.transfer.DirectoryStorage
import com.vythera.relay.transfer.OutgoingFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * Relay (extras/relay): files, text and links between this phone and other devices running Relay (the desktop app on Windows, Linux and macOS, or another
 * phone) over the local Wi-Fi, no account and no cloud. The engine is the Relay project's own (com.vythera.relay.*, Apache-2.0); this file is the Android side:
 * the saved identity and pairings, where received files go, and the one node the screen and the background service share.
 */
object RelayHost {
    /** The engine speaks TLS 1.3 and needs the platform's extended trust manager classes. */
    fun supported() = Build.VERSION.SDK_INT >= 29

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var node: RelayNode? = null
    private var users = 0
    private var lock: WifiManager.MulticastLock? = null

    private fun prefs(c: Context) = c.getSharedPreferences("relay", Context.MODE_PRIVATE)
    fun background(c: Context) = prefs(c).getBoolean("background", false)
    fun setBackground(c: Context, on: Boolean) {
        prefs(c).edit().putBoolean("background", on).apply()
        if (on) androidx.core.content.ContextCompat.startForegroundService(c, Intent(c, RelayService::class.java)) else c.stopService(Intent(c, RelayService::class.java))
    }
    fun clipboardSync(c: Context) = prefs(c).getBoolean("clip", false)
    fun setClipboardSync(c: Context, on: Boolean) { prefs(c).edit().putBoolean("clip", on).apply(); node?.setClipboardSync(on) }
    fun deviceName(c: Context) = prefs(c).getString("name", null)?.takeIf { it.isNotBlank() } ?: (Build.MODEL ?: "Android")
    fun setDeviceName(c: Context, n: String) { prefs(c).edit().putString("name", n.trim().take(64)).apply(); node?.rename(deviceName(c)) }

    /** Where received files land: Download/Pulse/Relay when the app may write there, else the app's own folder. */
    fun inbox(c: Context): File {
        val pub = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Pulse/Relay")
        val allowed = Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager()
        if (allowed && (pub.isDirectory || pub.mkdirs()) && pub.canWrite()) return pub
        return File(c.getExternalFilesDir(null) ?: c.filesDir, "Relay").apply { mkdirs() }
    }

    private class FileIdentityStore(val file: File) : IdentityStore {
        override fun load(): RelayIdentity? = if (file.isFile) runCatching { IdentityCodec.decode(file.readBytes()) }.getOrNull() else null
        override fun save(identity: RelayIdentity) { file.parentFile?.mkdirs(); file.writeBytes(IdentityCodec.encode(identity)) }
    }

    @Synchronized
    fun node(c: Context): RelayNode {
        node?.let { return it }
        val app = c.applicationContext
        val dir = File(app.filesDir, "relay").apply { mkdirs() }
        val identity = FileIdentityStore(File(dir, "identity.json")).loadOrCreate()
        val trust = JsonFileTrustStore(File(dir, "trusted.json"))
        val tablet = app.resources.configuration.smallestScreenWidthDp >= 600
        val cfg = NodeConfig(deviceName = deviceName(app), deviceType = if (tablet) DeviceType.TABLET else DeviceType.PHONE, platform = Platform.ANDROID, appVersion = "1.0")
        return RelayNode(cfg, identity, trust, DirectoryStorage(inbox(app)), scope).also { it.setClipboardSync(clipboardSync(app)); node = it }
    }

    /** The screen or the service needs the node running; it stops when the last one lets go and the background switch is off. */
    @Synchronized
    fun open(c: Context) {
        if (!supported()) return
        users++
        val app = c.applicationContext
        if (lock == null) runCatching {
            lock = (app.getSystemService(Context.WIFI_SERVICE) as WifiManager).createMulticastLock("pulse-relay").apply { setReferenceCounted(false); acquire() }
        }
        node(app).start()
        node(app).refresh()
    }

    @Synchronized
    fun close(c: Context) {
        users = (users - 1).coerceAtLeast(0)
        if (users == 0 && !background(c)) {
            node?.stop()
            runCatching { lock?.release() }; lock = null
        }
    }

    /** A picked file, read through the content resolver when it is sent. */
    class UriFile(private val c: Context, private val uri: Uri, override val name: String, override val sizeBytes: Long, override val mimeType: String) : OutgoingFile {
        override fun open(): InputStream = c.contentResolver.openInputStream(uri) ?: throw IOException("The file is gone")
    }

    fun filesOf(c: Context, uris: List<Uri>): List<OutgoingFile> = uris.mapNotNull { u ->
        runCatching {
            var name = u.lastPathSegment?.substringAfterLast('/') ?: "file"; var size = -1L
            c.contentResolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { q ->
                if (q.moveToFirst()) { q.getString(0)?.let { name = it }; if (!q.isNull(1)) size = q.getLong(1) }
            }
            if (size < 0) size = c.contentResolver.openAssetFileDescriptor(u, "r")?.use { it.length } ?: 0L
            UriFile(c, u, name, size, c.contentResolver.getType(u) ?: "application/octet-stream")
        }.getOrNull()
    }
}

/** Keeps Relay listening while the app is closed (Settings of Relay > Stay available), and tells about offers and received text. */
class RelayService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var opened = false
    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(com.umutk.pulse.QuietChannel("relay", "Relay", NotificationManager.IMPORTANCE_LOW))
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(android.app.NotificationChannel("relay_offer", "Relay offers", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(this, 5201, Intent().setClassName(this, "com.umutk.pulse.ToolOpenActivity").putExtra("route", "relay").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n: Notification = NotificationCompat.Builder(this, "relay").setSmallIcon(android.R.drawable.stat_sys_data_bluetooth).setContentTitle("Relay").setContentText(com.umutk.pulse.tr("Available to nearby devices", "Yakındaki cihazlara açık"))
            .setOngoing(true).setContentIntent(open).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(5200, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(5200, n)
        if (RelayHost.supported() && !opened) {
            opened = true
            RelayHost.open(this)
            val node = RelayHost.node(this)
            scope.launch {
                node.events.collect { e ->
                    val text = when (e) {
                        is NodeEvent.TransferOffered -> com.umutk.pulse.tr("A device wants to send you ${e.transfer.items.size} file(s)", "Bir cihaz sana ${e.transfer.items.size} dosya göndermek istiyor")
                        is NodeEvent.TextReceived -> e.text.take(120)
                        is NodeEvent.PairingRequested -> com.umutk.pulse.tr("${e.session.peerName} wants to pair", "${e.session.peerName} eşleşmek istiyor")
                        else -> null
                    } ?: return@collect
                    nm.notify(5202, NotificationCompat.Builder(this@RelayService, "relay_offer").setSmallIcon(android.R.drawable.stat_sys_data_bluetooth).setContentTitle("Relay").setContentText(text).setAutoCancel(true).setContentIntent(open).build())
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() { scope.cancel(); if (opened) RelayHost.close(this); super.onDestroy() }
}
