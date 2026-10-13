package com.umutk.pulse

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*

/** Keeps sampling when the app is closed: floating numbers over other apps, a live notification and threshold alerts. */
class MonitorService : Service() {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var job: Job? = null
    private var wm: WindowManager? = null
    private var tv: TextView? = null
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

    private suspend fun loop() {
        while (true) {
            if (System.currentTimeMillis() - Sampler.lastSample > 2500) Sampler.sample(this)
            val text = line()
            withContext(Dispatchers.Main) { showOverlay(text) }
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

    private fun showOverlay(text: String) {
        if (!Opt.ovOn || !Settings.canDrawOverlays(this)) { hideOverlay(); return }
        if (tv == null) {
            wm = getSystemService(WINDOW_SERVICE) as WindowManager
            val v = TextView(this).apply { setTextColor(Color.WHITE); typeface = Typeface.MONOSPACE; setPadding(20, 10, 20, 10) }
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
        tv?.apply {
            this.text = text; textSize = Opt.ovSize
            setBackgroundColor(Color.argb((Opt.ovAlpha * 255).toInt(), 0, 0, 0))
        }
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
    private fun paint() { qsTile?.apply { state = if (Opt.ovOn || Opt.notifOn) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE; label = "Pulse"; updateTile() } }
    override fun onClick() {
        Opt.load(this)
        val on = !(Opt.ovOn || Opt.notifOn)
        if (on) { if (Settings.canDrawOverlays(this)) Opt.ovOn = true else Opt.notifOn = true } else { Opt.ovOn = false; Opt.notifOn = false }
        Opt.save(this); MonitorService.sync(this); paint()
    }
}

/** Quick Settings tile that opens the app on the Toolkit page; [start] is the "start" extra MainActivity reads. */
open class ToolkitTile : TileService() {
    open val start: String get() = "toolkit"
    override fun onClick() {
        val i = Intent(this, MainActivity::class.java).putExtra("start", start).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        else startActivityAndCollapse(i)
    }
}

class CompassTile : ToolkitTile() { override val start: String get() = "toolkit:compass" }
class DiceTile : ToolkitTile() { override val start: String get() = "toolkit:dice" }
class CoinTile : ToolkitTile() { override val start: String get() = "toolkit:coin" }
class ScreenshotTile : ToolkitTile() { override val start: String get() = "toolkit:screenshot" }
class SoundModeTile : ToolkitTile() { override val start: String get() = "toolkit:vibrate" }
class LevelTile : ToolkitTile() { override val start: String get() = "toolkit:level" }
class LuxTile : ToolkitTile() { override val start: String get() = "toolkit:light" }
class ClipboardTile : ToolkitTile() { override val start: String get() = "toolkit:clipboard" }
class SensorsOffTile : ToolkitTile() { override val start: String get() = "toolkit:sensors" }
class BatteryTile : ToolkitTile() { override val start: String get() = "toolkit:battery" }
class MemoryTile : ToolkitTile() { override val start: String get() = "toolkit:memory" }
class TemperatureTile : ToolkitTile() { override val start: String get() = "toolkit:thermal" }
class VolumeTile : ToolkitTile() { override val start: String get() = "toolkit:volume" }
class StopwatchTile : ToolkitTile() { override val start: String get() = "toolkit:stopwatch" }
class TimerTile : ToolkitTile() { override val start: String get() = "toolkit:timer" }
class CalculatorTile : ToolkitTile() { override val start: String get() = "toolkit:calc" }
class ScaleTile : ToolkitTile() { override val start: String get() = "toolkit:scale" }
class FinderTile : ToolkitTile() { override val start: String get() = "toolkit:search" }
class PowerMenuTile : ToolkitTile() { override val start: String get() = "toolkit:power" }

/** Quick Settings tile that toggles the torch; the tile follows the torch, also when another app or the system changes it. */
class FlashlightTile : TileService() {
    private val cm by lazy { getSystemService(CameraManager::class.java) }
    private val camId: String? by lazy {
        try { cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true } }
        catch (e: CameraAccessException) { null }
    }
    private var torchOn = false
    private val torch = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) { if (cameraId == camId) { torchOn = enabled; paint() } }
    }
    override fun onStartListening() { cm.registerTorchCallback(torch, null); paint() }
    override fun onStopListening() { cm.unregisterTorchCallback(torch) }
    private fun paint() { qsTile?.apply { state = if (torchOn) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE; updateTile() } }
    override fun onClick() {
        val id = camId ?: return
        try { cm.setTorchMode(id, !torchOn) } catch (e: CameraAccessException) { }
    }
}
