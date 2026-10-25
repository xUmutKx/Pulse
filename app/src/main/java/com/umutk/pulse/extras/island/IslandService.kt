package com.umutk.pulse.extras.island

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.view.Gravity
import android.view.WindowManager
import androidx.core.graphics.drawable.toBitmap
import com.umutk.pulse.Compat
import com.umutk.pulse.QuietChannel
import com.umutk.pulse.R
import com.umutk.pulse.extras.lyrics.LyricsListenerService

/** Gives the island the notifications other apps post, and a component the media sessions can be read with. Declared in the manifest. */
class IslandListenerService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) { IslandService.live?.onNotification(sbn) }
    override fun onNotificationRemoved(sbn: StatusBarNotification) { IslandService.live?.remove("n:" + sbn.key) }
}

/** Starts the island again after a restart, when it was on. */
class IslandBoot : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action != Intent.ACTION_BOOT_COMPLETED) return
        IslandPrefs.load(c)
        if (IslandPrefs.on && Compat.canOverlay(c)) IslandService.start(c)
    }
}

/**
 * The island: one small window at the top of the screen that shows what is going on (the song, a notification, a charger, a headset, the volume, the torch, an app
 * that was removed, a screenshot) and opens on a tap. It runs as a foreground service so the system leaves it alone.
 */
class IslandService : Service() {
    companion object {
        @Volatile var live: IslandService? = null
        fun start(c: Context) { val i = Intent(c, IslandService::class.java); if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i) else c.startService(i) }
        fun stop(c: Context) { c.stopService(Intent(c, IslandService::class.java)) }
        /** The pill outlined and always there, to place it over the camera while the sliders are moved. */
        fun calibrate(on: Boolean) { live?.let { s -> s.main.post { s.view?.calibrating = on; s.refresh() } } }
        fun demo(c: Context) { live?.demo() }
    }

    internal val main = Handler(Looper.getMainLooper())
    private lateinit var wm: WindowManager
    internal var view: IslandView? = null
    private lateinit var lp: WindowManager.LayoutParams
    private val items = LinkedHashMap<String, IslandItem>()
    private val expire = HashMap<String, Runnable>()
    private val labels = HashMap<String, String>()
    private var controller: MediaController? = null
    private var pausedSince = 0L
    private var lastArtKey = ""
    private var lastArt: Bitmap? = null
    private var lastColor = 0
    private var plugged = false
    private var torchOn = false
    private var torchId: String? = null
    private val poll = object : Runnable { override fun run() { pollMusic(); main.postDelayed(this, 700) } }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        IslandPrefs.load(this)
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(QuietChannel("island", "Island", NotificationManager.IMPORTANCE_LOW))
        val n = androidx.core.app.NotificationCompat.Builder(this, "island").setSmallIcon(R.drawable.ic_ad_island_fg).setContentTitle("Island on").setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(9, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(9, n)
        if (!Compat.canOverlay(this)) { stopSelf(); return }
        live = this
        wm = getSystemService(WindowManager::class.java)
        val d = resources.displayMetrics.density
        val v = IslandView(this)
        lp = WindowManager.LayoutParams(1, 1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            if (Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            title = "Pulse island"
        }
        v.onSize = { w, h -> lp.width = w; lp.height = h; lp.x = (IslandPrefs.x * d).toInt(); lp.y = (IslandPrefs.top * d).toInt(); runCatching { wm.updateViewLayout(v, lp) } }
        v.onTransport = { a -> transport(a) }
        v.onLong = { openSettings() }
        view = v
        v.visibility = android.view.View.GONE
        if (runCatching { wm.addView(v, lp) }.isFailure) { stopSelf(); return }
        cacheLabels()
        registerSources()
        main.post(poll)
        refresh()
    }

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int = START_STICKY

    override fun onDestroy() {
        live = null
        main.removeCallbacksAndMessages(null)
        runCatching { unregisterReceiver(receiver) }
        runCatching { contentResolver.unregisterContentObserver(shots) }
        torchCb?.let { runCatching { getSystemService(CameraManager::class.java).unregisterTorchCallback(it) } }
        view?.let { v -> runCatching { wm.removeView(v) } }
        view = null
        super.onDestroy()
    }

    // ---- what is shown ----------------------------------------------------------------------------------------------------------------------
    private fun refresh() { view?.setItems(items.values.filter { IslandPrefs.enabled(it.kind) }) }

    fun post(item: IslandItem) {
        if (!IslandPrefs.enabled(item.kind)) return
        main.post {
            items[item.id] = item
            expire.remove(item.id)?.let { main.removeCallbacks(it) }
            if (item.ttl > 0) Runnable { remove(item.id) }.also { expire[item.id] = it; main.postDelayed(it, item.ttl) }
            refresh()
        }
    }

    fun remove(id: String) { main.post { items.remove(id); expire.remove(id)?.let { main.removeCallbacks(it) }; refresh() } }

    private fun openSettings() { runCatching { startActivity(Intent(this, com.umutk.pulse.ExtraToolActivity::class.java).putExtra("route", "island").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }

    fun demo() {
        post(IslandItem("demo", "uninstall", "Island", "This is how an alert looks", ttl = 5000, prio = 3, acts = listOf(IslandAct("Settings") { openSettings() })))
    }

    // ---- music: the playing session, read about once a second ------------------------------------------------------------------------------
    private fun listener(): ComponentName? {
        val en = Settings.Secure.getString(contentResolver, "enabled_notification_listeners").orEmpty()
        return listOf(ComponentName(this, IslandListenerService::class.java), ComponentName(this, LyricsListenerService::class.java)).firstOrNull { en.contains(it.flattenToString()) || en.contains(it.className) }
    }

    private fun pollMusic() {
        if (!IslandPrefs.music) { remove("music"); return }
        val comp = listener() ?: return
        val list = try { getSystemService(MediaSessionManager::class.java).getActiveSessions(comp) } catch (e: SecurityException) { return }
        val playingC = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
        val now = SystemClock.elapsedRealtime()
        // a song that was paused stays on the island for a while, so it can be started again from there
        val c = playingC ?: list.firstOrNull { it.metadata != null && it.playbackState?.state == PlaybackState.STATE_PAUSED && controller?.sessionToken == it.sessionToken }?.let {
            if (pausedSince == 0L) pausedSince = now
            if (now - pausedSince < 45_000) it else null
        }
        if (playingC != null) pausedSince = 0L
        if (c == null) { controller = null; pausedSince = 0L; remove("music"); return }
        val m = c.metadata
        if (m == null) { remove("music"); return }
        val playing = playingC != null
        controller = c
        val title = m.getString(MediaMetadata.METADATA_KEY_TITLE) ?: m.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: return
        val artist = m.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: m.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: m.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE).orEmpty()
        val key = c.packageName + "|" + title + "|" + artist
        if (key != lastArtKey) {
            lastArtKey = key
            lastArt = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: m.getBitmap(MediaMetadata.METADATA_KEY_ART) ?: m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            lastColor = lastArt?.let { b -> runCatching { androidx.palette.graphics.Palette.from(b).generate().let { p -> p.getVibrantColor(p.getMutedColor(p.getDominantColor(0))) } }.getOrDefault(0) } ?: 0
        }
        val dur = m.getLong(MediaMetadata.METADATA_KEY_DURATION)
        val ps = c.playbackState!!
        val pos = ps.position + if (playing) ((SystemClock.elapsedRealtime() - ps.lastPositionUpdateTime) * ps.playbackSpeed).toLong() else 0L
        val pkg = c.packageName
        val old = items["music"]
        main.post {
            items["music"] = IslandItem("music", "music", title, artist, lastArt, lastColor, if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else -1f, playing = playing, prio = 2, at = old?.at ?: System.currentTimeMillis(),
                onTap = { packageManager.getLaunchIntentForPackage(pkg)?.let { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } })
            refresh()
        }
    }

    private fun transport(a: String) {
        val t = controller?.transportControls ?: return
        when (a) {
            "prev" -> t.skipToPrevious()
            "next" -> t.skipToNext()
            else -> if (controller?.playbackState?.state == PlaybackState.STATE_PLAYING) t.pause() else t.play()
        }
        main.postDelayed({ pollMusic() }, 250)
    }

    // ---- notifications of other apps ------------------------------------------------------------------------------------------------------------
    fun onNotification(sbn: StatusBarNotification) {
        if (!IslandPrefs.notif || sbn.packageName == packageName) return
        val n = sbn.notification
        if (n.flags and Notification.FLAG_ONGOING_EVENT != 0 || n.flags and Notification.FLAG_GROUP_SUMMARY != 0 || n.category == Notification.CATEGORY_TRANSPORT || n.category == Notification.CATEGORY_PROGRESS || n.category == Notification.CATEGORY_SERVICE) return
        val ex = n.extras
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        if (title.isEmpty() && text.isEmpty()) return
        val icon = runCatching { packageManager.getApplicationIcon(sbn.packageName).toBitmap(96, 96) }.getOrNull()
        val pi = n.contentIntent
        post(IslandItem("n:" + sbn.key, "notif", title.ifEmpty { labels[sbn.packageName] ?: sbn.packageName }, text, icon, ttl = 5500, prio = 3,
            onTap = { runCatching { pi?.send() ?: packageManager.getLaunchIntentForPackage(sbn.packageName)?.let { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } } }))
    }

    // ---- the system's own events -----------------------------------------------------------------------------------------------------------------
    private fun cacheLabels() { Thread { runCatching { packageManager.getInstalledApplications(0).forEach { labels[it.packageName] = packageManager.getApplicationLabel(it).toString() } } }.start() }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                Intent.ACTION_BATTERY_CHANGED -> {
                    val now = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
                    val pct = (100f * i.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) / i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)).toInt()
                    if (now && !plugged || (now && items.containsKey("charge"))) post(IslandItem("charge", "charge", "Charging", "$pct%", level = pct / 100f, ttl = 4500, prio = 3))
                    if (!now && plugged) remove("charge")
                    plugged = now
                }
                Intent.ACTION_HEADSET_PLUG -> if (i.getIntExtra("state", 0) == 1) post(IslandItem("headset", "headset", "Headphones", "Connected", ttl = 3500, prio = 3))
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    val dev = if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) else @Suppress("DEPRECATION") i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    val name = try { dev?.name } catch (e: SecurityException) { null }
                    post(IslandItem("bt", "bt", name ?: "Bluetooth", "Connected", ttl = 3500, prio = 3))
                }
                "android.media.VOLUME_CHANGED_ACTION" -> {
                    val s = i.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1); val v = i.getIntExtra("android.media.EXTRA_VOLUME_STREAM_VALUE", -1)
                    if (s < 0 || v < 0) return
                    val max = (getSystemService(AudioManager::class.java)).getStreamMaxVolume(s).coerceAtLeast(1)
                    val name = when (s) { AudioManager.STREAM_MUSIC -> "Media"; AudioManager.STREAM_RING -> "Ring"; AudioManager.STREAM_ALARM -> "Alarm"; AudioManager.STREAM_VOICE_CALL -> "Call"; AudioManager.STREAM_NOTIFICATION -> "Notifications"; else -> "Volume" }
                    post(IslandItem("volume", "volume", name, "${(100 * v / max)}%", level = v.toFloat() / max, ttl = 1800, prio = 4))
                }
                Intent.ACTION_PACKAGE_ADDED -> i.data?.schemeSpecificPart?.let { p -> Thread { runCatching { labels[p] = packageManager.getApplicationLabel(packageManager.getApplicationInfo(p, 0)).toString() } }.start() }
                Intent.ACTION_PACKAGE_FULLY_REMOVED -> {
                    val p = i.data?.schemeSpecificPart ?: return
                    if (i.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return
                    post(IslandItem("uninstall", "uninstall", labels[p] ?: p, "Uninstalled", ttl = 5000, prio = 3))
                }
            }
        }
    }

    private var torchCb: CameraManager.TorchCallback? = null

    private val shots = object : ContentObserver(main) {
        override fun onChange(selfChange: Boolean) { main.removeCallbacks(shotCheck); main.postDelayed(shotCheck, 700) }
    }
    private val shotCheck = Runnable { checkShot() }

    private fun checkShot() {
        if (!IslandPrefs.shot) return
        if (checkSelfPermission(if (Build.VERSION.SDK_INT >= 33) android.Manifest.permission.READ_MEDIA_IMAGES else android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) return
        runCatching {
            val cols = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.DATE_ADDED, MediaStore.Images.Media.DATA)
            contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cols, null, null, MediaStore.Images.Media.DATE_ADDED + " DESC LIMIT 1")?.use { c ->
                if (!c.moveToFirst()) return
                val name = c.getString(1).orEmpty(); val path = c.getString(3).orEmpty(); val added = c.getLong(2)
                if (System.currentTimeMillis() / 1000 - added > 8 || !(name.contains("screenshot", true) || path.contains("screenshot", true))) return
                val id = c.getLong(0)
                val uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                val thumb = runCatching { if (Build.VERSION.SDK_INT >= 29) contentResolver.loadThumbnail(uri, android.util.Size(256, 256), null) else null }.getOrNull()
                post(IslandItem("shot:$id", "shot", "Screenshot", "Saved", thumb, ttl = 7000, prio = 3, acts = listOf(
                    IslandAct("Share") { startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("image/*").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
                    IslandAct("Copy") { (getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newUri(contentResolver, "screenshot", uri)) },
                ), onTap = { startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "image/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)) }))
            }
        }
    }

    private fun registerSources() {
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED); addAction(Intent.ACTION_HEADSET_PLUG); addAction(BluetoothDevice.ACTION_ACL_CONNECTED); addAction("android.media.VOLUME_CHANGED_ACTION")
        }
        val pf = IntentFilter().apply { addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED); addDataScheme("package") }
        if (Build.VERSION.SDK_INT >= 33) { registerReceiver(receiver, f, Context.RECEIVER_EXPORTED); registerReceiver(receiver, pf, Context.RECEIVER_EXPORTED) } else { registerReceiver(receiver, f); registerReceiver(receiver, pf) }
        runCatching { contentResolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, shots) }
        runCatching {
            val cm = getSystemService(CameraManager::class.java)
            val cb = object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                    torchOn = enabled; torchId = cameraId
                    if (enabled) post(IslandItem("torch", "torch", "Torch", "On", prio = 2, acts = listOf(IslandAct("Turn off") { runCatching { cm.setTorchMode(cameraId, false) } })))
                    else remove("torch")
                }
            }
            torchCb = cb
            cm.registerTorchCallback(cb, main)
        }
    }
}
