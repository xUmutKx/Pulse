package com.umutk.pulse

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.os.BatteryManager
import android.content.IntentFilter
import java.util.Date
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.umutk.pulse.features.TileActions
import kotlinx.coroutines.*

/** Keeps sampling when the app is closed: floating numbers over other apps, a live notification and threshold alerts. */
class MonitorService : Service() {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var job: Job? = null
    private var wm: WindowManager? = null
    private var tv: android.widget.LinearLayout? = null
    private var lp: WindowManager.LayoutParams? = null
    private val lastAlert = HashMap<String, Long>()

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Opt.load(this)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH, "Monitor", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_ALERT, "Alerts", NotificationManager.IMPORTANCE_HIGH))
        val n = build("Pulse monitor is running")
        if (Build.VERSION.SDK_INT >= 34) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(ID, n)
        if (job == null) job = scope.launch { loop() }
        return START_STICKY
    }

    private fun build(text: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CH).setSmallIcon(android.R.drawable.stat_notify_more).setContentTitle("Pulse").setContentText(text)
            .setOngoing(true).setOnlyAlertOnce(true).setPriority(NotificationCompat.PRIORITY_LOW).setContentIntent(open).build()
    }

    private fun line(): String {
        val s = Sampler.snap.value
        val parts = ArrayList<String>()
        if ("cpu" in Opt.ovItems) parts += "CPU %${s.cpu}"
        if ("ram" in Opt.ovItems) parts += "RAM %${if (s.ramTotalMb == 0) 0 else 100 * s.ramUsedMb / s.ramTotalMb}"
        if ("gpu" in Opt.ovItems && s.gpu >= 0) parts += "GPU %${s.gpu}"
        if ("temp" in Opt.ovItems) parts += Opt.temp(s.cpuTemp)
        if ("bat" in Opt.ovItems) parts += "Bat ${s.batPct}% ${s.batMa}mA"
        if ("net" in Opt.ovItems) parts += "↓${rate(s.down.toFloat())} ↑${rate(s.up.toFloat())}"
        return parts.joinToString("  ·  ")
    }

    private fun items(): List<Pair<String, String>> {
        val s = Sampler.snap.value
        val l = ArrayList<Pair<String, String>>()
        if ("cpu" in Opt.ovItems) l += "cpu" to "%${s.cpu}"
        if ("ram" in Opt.ovItems) l += "ram" to "%${if (s.ramTotalMb == 0) 0 else 100 * s.ramUsedMb / s.ramTotalMb}"
        if ("gpu" in Opt.ovItems && s.gpu >= 0) l += "gpu" to "%${s.gpu}"
        if ("temp" in Opt.ovItems) l += "temp" to Opt.temp(s.cpuTemp)
        if ("bat" in Opt.ovItems) l += "bat" to "${s.batPct}% ${s.batMa}mA"
        if ("net" in Opt.ovItems) { l += "down" to rate(s.down.toFloat()); l += "up" to rate(s.up.toFloat()) }
        return l
    }

    private suspend fun loop() {
        while (true) {
            if (System.currentTimeMillis() - Sampler.lastSample > 2500) Sampler.sample(this)
            val text = line()
            val its = items()
            withContext(Dispatchers.Main) { showOverlay(its) }
            if (Opt.notifOn) getSystemService(NotificationManager::class.java).notify(ID, build(text))
            alerts()
            if (!Opt.ovOn && !Opt.notifOn && Opt.alertTemp == 0 && Opt.alertBat == 0 && Opt.alertRam == 0) { stopSelf(); return }
            delay(maxOf(1000L, Sampler.interval))
        }
    }

    private fun alerts() {
        val s = Sampler.snap.value
        fun fire(key: String, msg: String) {
            val now = System.currentTimeMillis()
            if (now - (lastAlert[key] ?: 0L) < 300_000) return
            lastAlert[key] = now
            val n = NotificationCompat.Builder(this, CH_ALERT).setSmallIcon(android.R.drawable.stat_sys_warning).setContentTitle("Pulse alert").setContentText(msg).setAutoCancel(true).build()
            getSystemService(NotificationManager::class.java).notify(key.hashCode(), n)
        }
        if (Opt.alertTemp > 0 && s.cpuTemp >= Opt.alertTemp) fire("temp", "Temperature ${Opt.temp(s.cpuTemp)} (limit ${Opt.alertTemp}°C)")
        if (Opt.alertBat > 0 && s.batPct <= Opt.alertBat && !s.charging) fire("bat", "Battery ${s.batPct}% (limit ${Opt.alertBat}%)")
        val ram = if (s.ramTotalMb == 0) 0 else 100 * s.ramUsedMb / s.ramTotalMb
        if (Opt.alertRam > 0 && ram >= Opt.alertRam) fire("ram", "Memory $ram% (limit ${Opt.alertRam}%)")
    }

    private fun showOverlay(its: List<Pair<String, String>>) {
        if (!Opt.ovOn || !Settings.canDrawOverlays(this)) { hideOverlay(); return }
        if (tv == null) {
            wm = getSystemService(WINDOW_SERVICE) as WindowManager
            val v = android.widget.LinearLayout(this)
            val p = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT)
            p.gravity = Gravity.TOP or Gravity.START; p.x = 16; p.y = 80
            var dx = 0f; var dy = 0f; var sx = 0; var sy = 0
            v.setOnTouchListener { _, e ->
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> { dx = e.rawX; dy = e.rawY; sx = p.x; sy = p.y }
                    MotionEvent.ACTION_MOVE -> { p.x = sx + (e.rawX - dx).toInt(); p.y = sy + (e.rawY - dy).toInt(); wm?.updateViewLayout(v, p) }
                }
                true
            }
            try { wm?.addView(v, p); tv = v; lp = p } catch (e: Exception) { return }
        }
        tv?.let { OverlayUi.fill(it, its) }
    }

    private fun hideOverlay() { tv?.let { try { wm?.removeView(it) } catch (e: Exception) { } }; tv = null }

    override fun onDestroy() {
        job?.cancel(); scope.cancel()
        hideOverlay()
        super.onDestroy()
    }

    companion object {
        const val CH = "pulse_monitor"; const val CH_ALERT = "pulse_alert"; const val ID = 7
        fun start(c: Context) { try { ContextCompat.startForegroundService(c, Intent(c, MonitorService::class.java)) } catch (e: Exception) { } }
        fun stop(c: Context) { c.stopService(Intent(c, MonitorService::class.java)) }
        /** Starts or stops the service to match what is switched on. */
        fun sync(c: Context) { if (Opt.ovOn || Opt.notifOn || Opt.alertTemp > 0 || Opt.alertBat > 0 || Opt.alertRam > 0) start(c) else stop(c) }
    }
}

/** Quick Settings tile: switches the floating monitor (or, without the overlay permission, the notification monitor) on and off. */
class PulseTile : TileService() {
    override fun onStartListening() { Opt.load(this); paint() }
    private fun paint() { qsTile?.apply { state = if (Opt.ovOn || Opt.notifOn) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE; label = "Pulse"; subtitle = if (Opt.ovOn) tr("Overlay", "Ekran üstü") else if (Opt.notifOn) tr("Notification", "Bildirim") else tr("Off", "Kapalı"); updateTile() } }
    override fun onClick() {
        Opt.load(this)
        val on = !(Opt.ovOn || Opt.notifOn)
        if (on) { if (Settings.canDrawOverlays(this)) Opt.ovOn = true else Opt.notifOn = true } else { Opt.ovOn = false; Opt.notifOn = false }
        Opt.save(this); MonitorService.sync(this); paint()
    }
}

private val DIRS = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")

/** Long-pressing a tile opens Pulse on that tile's page (MainActivity handles ACTION_QS_TILE_PREFERENCES); keyed by the tile's class name. */
fun tilePage(cls: String): String = when (cls.substringAfterLast('.')) {
    "PulseTile" -> "settings"
    "DnsTile" -> "toolkit:wifi"   // the DNS tool of the Toolkit
    "RotationTile" -> "toolkit:rotate"
    "CoverTile" -> "cover"
    "FlashlightTile" -> "toolkit:flash"
    "StopwatchTile" -> "toolkit:stopwatch"
    "ToolkitTile" -> "toolkit"
    else -> runCatching { (Class.forName(cls).getDeclaredConstructor().newInstance() as ToolkitTile).start }.getOrDefault("toolkit")
}

/** Opens Pulse on [page] and closes the panel. */
fun TileService.openPulse(page: String) {
    val i = Intent(this, MainActivity::class.java).putExtra("start", page).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(PendingIntent.getActivity(this, page.hashCode(), i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
    else @Suppress("DEPRECATION") startActivityAndCollapse(i)
}

/** Quick Settings tile for one Toolkit tool; [start] is the "start" extra MainActivity reads. The plain Toolkit tile opens the Toolkit page.
 *  A tap does the tool's own job in the panel ([act]); a tile without one opens its tool. Sensor tiles switch their live reading on and off. */
open class ToolkitTile : TileService() {
    open val start: String get() = "toolkit"
    /** Turkish name; the English one is the manifest label. */
    open val trName: String get() = "Araç kutusu"
    /** Sensor that feeds [subtitleFor]. Registered only while the tile is listening and switched on. */
    open val sensor: Int? get() = null
    /** Line under the tile name. [values] is the latest sensor reading, null otherwise. Null hides the line. */
    open fun subtitleFor(values: FloatArray?): String? = null
    /** Highlighted (ACTIVE) look, e.g. while charging or a timer runs. */
    open fun active(): Boolean = false
    /** The tile's own picture (the dice face). Null keeps the manifest icon. */
    open val picture: Int? get() = null
    /** Repaint period while the panel is open, for readings that change on their own; 0 = only on open and tap. */
    open val refreshMs: Long get() = 0L
    /** The tile's own job, done in the panel. Returns true to repaint; a tile without one opens its tool. */
    open fun act(): Boolean = if (sensor != null) { live = !live; listen(); true } else { openPulse(start); false }

    protected val prefs get() = getSharedPreferences("p", Context.MODE_PRIVATE)
    protected val main = Handler(Looper.getMainLooper())
    protected fun batteryIntent(): Intent? = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    private val name by lazy { runCatching { packageManager.getServiceInfo(ComponentName(this, javaClass), 0).loadLabel(packageManager).toString() }.getOrDefault("Toolkit") }
    private var live: Boolean
        get() = prefs.getBoolean("qs_live_$start", true)
        set(v) { prefs.edit().putBoolean("qs_live_$start", v).apply() }
    private val sensorMgr get() = getSystemService(SensorManager::class.java)
    private var listening = false
    private var registered = false
    private val listener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) { qsTile?.apply { val s = subtitleFor(e.values); if (subtitle != s) { subtitle = s; updateTile() } } }
        override fun onAccuracyChanged(s: Sensor?, a: Int) {}
    }
    private val tick = object : Runnable { override fun run() { paint(); if (listening) main.postDelayed(this, refreshMs) } }

    private fun listen() {
        if (registered) { sensorMgr.unregisterListener(listener); registered = false }
        val s = sensor?.let { sensorMgr.getDefaultSensor(it) } ?: return
        if (listening && live) registered = sensorMgr.registerListener(listener, s, SensorManager.SENSOR_DELAY_UI)
    }

    protected fun paint() {
        val noSensor = sensor?.let { sensorMgr.getDefaultSensor(it) == null } == true
        qsTile?.apply {
            label = tr(name, trName)
            state = when { noSensor -> Tile.STATE_UNAVAILABLE; (if (sensor != null) live else active()) -> Tile.STATE_ACTIVE; else -> Tile.STATE_INACTIVE }
            subtitle = when { noSensor -> tr("No sensor", "Sensör yok"); sensor != null && !live -> tr("Off", "Kapalı"); else -> subtitleFor(null) }
            picture?.let { icon = Icon.createWithResource(this@ToolkitTile, it) }
            updateTile()
        }
    }

    override fun onStartListening() {
        Opt.load(this) // the language for tr()
        listening = true; paint(); listen()
        main.removeCallbacks(tick); if (refreshMs > 0) main.postDelayed(tick, refreshMs)
    }
    override fun onStopListening() { listening = false; listen(); main.removeCallbacks(tick) }
    override fun onClick() { if (act()) paint() }
}

class CompassTile : ToolkitTile() {
    override val start: String get() = "toolkit:compass"
    override val trName: String get() = "Pusula"
    override val sensor: Int? get() = Sensor.TYPE_ROTATION_VECTOR
    override fun subtitleFor(values: FloatArray?): String? {
        if (values == null) return null
        val m = FloatArray(9); val o = FloatArray(3)
        SensorManager.getRotationMatrixFromVector(m, values); SensorManager.getOrientation(m, o)
        val deg = (Math.toDegrees(o[0].toDouble()) + 360) % 360
        return "%.0f° %s".format(deg, DIRS[((deg + 22.5) / 45).toInt() % 8])
    }
}
class LevelTile : ToolkitTile() {
    override val start: String get() = "toolkit:level"
    override val trName: String get() = "Su terazisi"
    override val sensor: Int? get() = Sensor.TYPE_ACCELEROMETER
    override fun subtitleFor(values: FloatArray?): String? = values?.let {
        tr("Tilt %.0f°", "Eğim %.0f°").format(Math.toDegrees(Math.atan2(Math.hypot(it[0].toDouble(), it[1].toDouble()), it[2].toDouble())))
    }
}
class LuxTile : ToolkitTile() {
    override val start: String get() = "toolkit:light"
    override val trName: String get() = "Işık ölçer"
    override val sensor: Int? get() = Sensor.TYPE_LIGHT
    override fun subtitleFor(values: FloatArray?): String? = values?.let { "%.0f lx".format(it[0]) }
}
class DiceTile : ToolkitTile() {
    override val start: String get() = "toolkit:dice"
    override val trName: String get() = "Zar"
    override fun subtitleFor(values: FloatArray?): String? = prefs.getInt("dice_last", 0).takeIf { it > 0 }?.toString()
    // a tap rolls in the panel; the tile shows the face that came up
    override fun act(): Boolean { prefs.edit().putInt("dice_last", (1..6).random()).apply(); return true }
    override val picture: Int? get() = prefs.getInt("dice_last", 0).takeIf { it in 1..6 }?.let { DICE_FACES[it - 1] }
}
private val DICE_FACES = intArrayOf(R.drawable.ic_dice_1, R.drawable.ic_dice_2, R.drawable.ic_dice_3, R.drawable.ic_dice_4, R.drawable.ic_dice_5, R.drawable.ic_dice_6)
class CoinTile : ToolkitTile() {
    override val start: String get() = "toolkit:coin"
    override val trName: String get() = "Yazı tura"
    // stored in English (shared with the Toolkit page): heads is "tura" in Turkish, tails "yazı"
    override fun subtitleFor(values: FloatArray?): String? = prefs.getString("coin_last", null)?.let { tr(it, if (it == "Heads") "Tura" else "Yazı") }
    override fun act(): Boolean { prefs.edit().putString("coin_last", if ((0..1).random() == 0) "Heads" else "Tails").apply(); return true }
}
class ScreenshotTile : ToolkitTile() {
    override val start: String get() = "toolkit:screenshot"
    override val trName: String get() = "Ekran görüntüsü"
    private var failed = false
    // with root/Shizuku the panel closes and the screen is captured; without them the tool opens
    override fun act(): Boolean {
        if (!TileActions.screenshot(this) { ok -> failed = !ok; paint(); if (!ok) openPulse(start) }) { openPulse(start); return false }
        return false
    }
    override fun subtitleFor(values: FloatArray?): String? = if (failed) tr("Failed", "Olmadı") else prefs.getLong("shot_last", 0).takeIf { it > 0 }
        ?.let { android.text.format.DateFormat.getTimeFormat(this).format(Date(it)) }
}
class SoundModeTile : ToolkitTile() {
    override val start: String get() = "toolkit:vibrate"
    override val trName: String get() = "Ses modu"
    override val refreshMs: Long get() = 1000L // follows the volume keys
    override fun active(): Boolean = getSystemService(AudioManager::class.java).ringerMode == AudioManager.RINGER_MODE_NORMAL
    override fun subtitleFor(values: FloatArray?): String? = when (getSystemService(AudioManager::class.java).ringerMode) {
        AudioManager.RINGER_MODE_NORMAL -> tr("Normal", "Normal"); AudioManager.RINGER_MODE_VIBRATE -> tr("Vibrate", "Titreşim"); else -> tr("Silent", "Sessiz")
    }
    // Normal, Vibrate, Silent in turn; Silent needs Do Not Disturb access, without it the cycle skips Silent, and a refused change opens the tool (it asks for that access)
    override fun act(): Boolean {
        val am = getSystemService(AudioManager::class.java)
        val dnd = getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted
        val next = when (am.ringerMode) {
            AudioManager.RINGER_MODE_NORMAL -> AudioManager.RINGER_MODE_VIBRATE
            AudioManager.RINGER_MODE_VIBRATE -> if (dnd) AudioManager.RINGER_MODE_SILENT else AudioManager.RINGER_MODE_NORMAL
            else -> AudioManager.RINGER_MODE_NORMAL
        }
        return try { am.ringerMode = next; true } catch (e: SecurityException) { openPulse(start); false }
    }
}
class ClipboardTile : ToolkitTile() {
    override val start: String get() = "toolkit:clipboard"
    override val trName: String get() = "Pano"
    private var text: String? = null
    override fun subtitleFor(values: FloatArray?): String? = text?.replace('\n', ' ')?.take(40)
    // Android hands the clipboard only to the foreground app, so this is mostly null: the tap then opens the tool
    override fun act(): Boolean { text = TileActions.clipboardText(this)?.takeIf { it.isNotBlank() } ?: run { openPulse(start); return false }; return true }
}
class SensorsOffTile : ToolkitTile() {
    override val start: String get() = "toolkit:sensors"
    override val trName: String get() = "Sensörler kapalı"
    private var busy = false
    private var off: Boolean? = null // read in the background (dumpsys), so kept here instead of asking on every paint
    override fun active(): Boolean = off == true
    override fun subtitleFor(values: FloatArray?): String? = if (busy) "…" else when (off) { true -> tr("Mic + camera off", "Mikrofon + kamera kapalı"); false -> tr("On", "Açık"); null -> null }
    override fun onStartListening() { super.onStartListening(); TileActions.refreshSensors(this) { off = it ?: off; paint() } }
    override fun act(): Boolean {
        if (busy) return false
        if (!TileActions.toggleSensorsOff(this) { now -> busy = false; off = now ?: off; paint(); if (now == null) openPulse(start) }) { openPulse(start); return false }
        busy = true; return true
    }
}
class BatteryTile : ToolkitTile() {
    override val start: String get() = "toolkit:battery"
    override val trName: String get() = "Pil"
    override val refreshMs: Long get() = 3000L
    override fun act(): Boolean = true // the reading is the job; a tap refreshes it
    override fun active(): Boolean = batteryIntent()?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)?.let { it != 0 } == true
    override fun subtitleFor(values: FloatArray?): String? = batteryIntent()?.let { i ->
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        "${i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) * 100 / scale}%" + if (active()) tr(" · charging", " · şarjda") else ""
    }
}
class MemoryTile : ToolkitTile() {
    override val start: String get() = "toolkit:memory"
    override val trName: String get() = "Bellek"
    override val refreshMs: Long get() = 3000L
    override fun act(): Boolean = true // the reading is the job; a tap refreshes it
    override fun subtitleFor(values: FloatArray?): String? {
        val m = ActivityManager.MemoryInfo().also { getSystemService(ActivityManager::class.java).getMemoryInfo(it) }
        return tr("%d%% used", "%%%d dolu").format((m.totalMem - m.availMem) * 100 / m.totalMem)
    }
}
class TemperatureTile : ToolkitTile() {
    override val start: String get() = "toolkit:thermal"
    override val trName: String get() = "Sıcaklık"
    override val refreshMs: Long get() = 3000L
    override fun act(): Boolean = true // the reading is the job; a tap refreshes it
    override fun subtitleFor(values: FloatArray?): String? = batteryIntent()?.let { Opt.temp(it.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f) }
}
class VolumeTile : ToolkitTile() {
    override val start: String get() = "toolkit:volume"
    override val trName: String get() = "Ses düzeyi"
    override val refreshMs: Long get() = 1000L // follows the volume panel and keys
    override fun act(): Boolean { TileActions.volumePanel(this); return true }
    override fun subtitleFor(values: FloatArray?): String? {
        val am = getSystemService(AudioManager::class.java)
        return tr("Media", "Medya") + " ${am.getStreamVolume(AudioManager.STREAM_MUSIC)}/${am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)}"
    }
}
/** Timer tile: while a timer runs, its remaining time is the subtitle and the icon turns to an hourglass. The Toolkit timer writes its end time (ms) to prefs "timer", key "end". A tap opens the timer. */
class TimerTile : ToolkitTile() {
    override val start: String get() = "toolkit:timer"
    override val trName: String get() = "Zamanlayıcı"
    override val refreshMs: Long get() = 1000L
    private fun remainingMs(): Long = getSharedPreferences("timer", Context.MODE_PRIVATE).getLong("end", 0L) - System.currentTimeMillis()
    override fun active(): Boolean = remainingMs() > 0
    override val picture: Int? get() = if (active()) R.drawable.qs_hourglass else R.drawable.ic_qs_timer
    override fun subtitleFor(values: FloatArray?): String? = remainingMs().takeIf { it > 0 }?.let { ms ->
        val s = (ms + 999) / 1000 // round up, so the last second shows 00:01
        "%02d:%02d".format(s / 60, s % 60)
    }
}
class CalculatorTile : ToolkitTile() { override val start: String get() = "calculator"; override val trName: String get() = "Hesap makinesi" }
/** Network type tile (5G only, LTE only ...): a tap switches to the next mode picked in the Toolkit, as root; without root it opens the screen. */
class NetModeTile : ToolkitTile() {
    override val start: String get() = "toolkit:globe"
    override val trName: String get() = "Ağ türü"
    override val refreshMs: Long get() = 3000L
    private var busy = false
    override fun subtitleFor(values: FloatArray?): String? = if (busy) "…" else com.umutk.pulse.features.NetMode.current(this)?.let { com.umutk.pulse.features.NetMode.label(it) }
    override fun active(): Boolean = com.umutk.pulse.features.NetMode.current(this) in setOf(23, 24)
    override fun act(): Boolean {
        if (busy) return false
        if (Shell.root != true) { openPulse(start); return false }
        busy = true
        com.umutk.pulse.features.NetMode.apply(this, com.umutk.pulse.features.NetMode.next(this)) { ok -> busy = false; paint(); if (!ok) openPulse(start) }
        return true
    }
}
class FinderTile : ToolkitTile() { override val start: String get() = "toolkit:search"; override val trName: String get() = "Bulucu" }
class DinkTile : ToolkitTile() { override val start: String get() = "toolkit:dink"; override val trName: String get() = "Dink" }
class ScaleTile : ToolkitTile() {
    override val start: String get() = "toolkit:scale"
    override val trName: String get() = "Ekran ölçeği"
    override fun subtitleFor(values: FloatArray?): String? = tr("Text ", "Yazı ") + TileActions.scaleLabel(this)
    // the next font size step (as TileActions.scaleNext), but with a callback: through the shell the answer comes later
    override fun act(): Boolean {
        val cur = TileActions.fontScale(this)
        val next = TileActions.scales.firstOrNull { it > cur + .01f } ?: TileActions.scales[0]
        if (!TileActions.setFontScale(this, next) { ok -> paint(); if (!ok) openPulse(start) }) { openPulse(start); return false }
        return true
    }
}
class RotationTile : ToolkitTile() {
    override val start: String get() = "toolkit:rotate"
    override val trName: String get() = "Ekran yönü"
    private var steps = 0
    private var base = 0
    private val apply = Runnable {
        val n = steps; steps = 0
        if (n % 4 != 0) TileActions.setRotation(this, (base + n) % 4) { paint() } else paint()
    }
    override fun subtitleFor(values: FloatArray?): String? =
        if (steps > 0) "${((base + steps) % 4) * 90}°" else TileActions.rotationLabel(TileActions.rotation(this))
    override fun active(): Boolean = (base + steps) % 4 != 0 && steps > 0 || steps == 0 && TileActions.rotation(this) > 0
    override val picture: Int? get() = null
    override fun act(): Boolean {
        if (steps == 0) base = TileActions.rotation(this).coerceAtLeast(0)
        steps++
        prefs.edit().putLong("rot_tap", System.currentTimeMillis()).apply()
        main.removeCallbacks(apply); main.postDelayed(apply, 500)
        qsTile?.icon = android.graphics.drawable.Icon.createWithBitmap(turned(((base + steps) % 4) * 90f))
        return true
    }
    /** The tile's own picture turned to [deg], so each tap visibly turns the phone. */
    private fun turned(deg: Float): android.graphics.Bitmap {
        val d = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.ic_qs_rotate)!!
        val sz = 96
        val b = android.graphics.Bitmap.createBitmap(sz, sz, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(b)
        c.rotate(deg, sz / 2f, sz / 2f); d.setBounds(0, 0, sz, sz); d.draw(c)
        return b
    }
}
class TouchesTile : ToolkitTile() {
    override val start: String get() = "toolkit:touch"
    override val trName: String get() = "Dokunma noktaları"
    override fun active(): Boolean = TileActions.flagOn(this, "show_touches")
    override fun act(): Boolean {
        if (!TileActions.toggleFlag(this, "show_touches") { ok -> paint(); if (!ok) openPulse(start) }) { openPulse(start); return false }
        return true
    }
}
class PointerTile : ToolkitTile() {
    override val start: String get() = "toolkit:pointer"
    override val trName: String get() = "Dokunma çizgileri"
    override fun active(): Boolean = TileActions.flagOn(this, "pointer_location")
    override fun act(): Boolean {
        if (!TileActions.toggleFlag(this, "pointer_location") { ok -> paint(); if (!ok) openPulse(start) }) { openPulse(start); return false }
        return true
    }
}
class MotionCueTile : ToolkitTile() {
    override val start: String get() = "toolkit:motion"
    override val trName: String get() = "Hareket noktaları"
    override fun active(): Boolean = MotionCueService.running
    override fun act(): Boolean { if (!MotionCueService.toggle(this)) { openPulse(start); return false }; main.postDelayed({ paint() }, 300); return true }
}
class PowerMenuTile : ToolkitTile() {
    override val start: String get() = "toolkit:power"
    override val trName: String get() = "Güç menüsü"
    override fun act(): Boolean { if (!TileActions.powerMenu()) openPulse(start); return false }
}
/** Quick Settings tile "last played": shows the last track or mix ([QuickTileContract]); a tap resumes it through MainActivity. */
class LastPlayedTile : TileService() {
    override fun onStartListening() { Opt.load(this); paint() }
    private fun paint() {
        val last = com.umutk.pulse.extras.watchdog.QuickTileContract.load(this)
        qsTile?.apply {
            label = com.umutk.pulse.extras.watchdog.QuickTileContract.label(this@LastPlayedTile)
            state = if (last != null) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            subtitle = last?.let { if (it.kind == com.umutk.pulse.extras.watchdog.QuickTileContract.Kind.MIX) tr("Mix", "Karışım") else tr("Track", "Parça") }
            updateTile()
        }
    }
    override fun onClick() {
        val i = com.umutk.pulse.extras.watchdog.QuickTileContract.resumeIntent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        else @Suppress("DEPRECATION") startActivityAndCollapse(i)
    }
}

/** The system power menu: through the App lock accessibility service when it runs, otherwise a long power-key press with root/Shizuku. */
object PowerMenu {
    fun show(): Boolean {
        com.umutk.pulse.applock.LockService.instance?.let { if (it.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)) return true }
        if (Shell.root != true && !Shell.shizukuOk()) return false
        Thread { Shell.run("cmd statusbar collapse; input keyevent --longpress 26") }.start()
        return true
    }
}

/** Quick Settings tile that toggles the torch; the tile follows the torch, also when another app or the system changes it. */
class FlashlightTile : TileService() {
    private val cm by lazy { getSystemService(CameraManager::class.java) }
    private val camId: String? by lazy {
        try { cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true } }
        catch (e: CameraAccessException) { null }
    }
    private var torchOn = false
    private var busy = false // the camera is in use by another app: the torch cannot be switched now
    private val torch = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) { if (cameraId == camId) { torchOn = enabled; busy = false; paint() } }
        override fun onTorchModeUnavailable(cameraId: String) { if (cameraId == camId) { busy = true; paint() } }
    }
    override fun onStartListening() { Opt.load(this); cm.registerTorchCallback(torch, null); paint() }
    override fun onStopListening() { cm.unregisterTorchCallback(torch) }
    private fun paint() {
        qsTile?.apply {
            label = tr("Flashlight", "El feneri")
            state = if (camId == null) Tile.STATE_UNAVAILABLE else if (torchOn) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            subtitle = when { camId == null -> tr("No flash", "Flaş yok"); busy -> tr("Camera in use", "Kamera kullanımda"); torchOn -> tr("On", "Açık"); else -> tr("Off", "Kapalı") }
            icon = Icon.createWithResource(this@FlashlightTile, if (torchOn) R.drawable.ic_qs_flashlight else R.drawable.qs_flashlight_off)
            updateTile()
        }
    }
    override fun onClick() {
        val id = camId ?: return
        try { cm.setTorchMode(id, !torchOn) } catch (e: Exception) { busy = true; paint() }
    }
}
