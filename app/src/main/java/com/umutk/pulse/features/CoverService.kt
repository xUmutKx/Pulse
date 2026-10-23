package com.umutk.pulse.features

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.PathParser
import com.umutk.pulse.FluentPaths
import com.umutk.pulse.R
import com.umutk.pulse.tr
import java.util.Date
import java.util.Locale

/** Screen cover: a black overlay over the whole screen, status and navigation bars included. Double-tap closes it.
 *  The clock, date, battery and own text stay visible when CoverStore allows; a tap on the bell closes the cover and opens notification history;
 *  a swipe down from the top edge closes it and pulls the quick settings panel down. */
class CoverService : Service() {
    private var wm: WindowManager? = null
    private var root: FrameLayout? = null
    private val bands = ArrayList<View>()
    private var clock: TextView? = null
    private var date: TextView? = null
    private var battery: TextView? = null
    private var style = "light"
    private val timeFormat by lazy { android.text.format.DateFormat.getTimeFormat(this) }
    private val datePattern by lazy { android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEE d MMMM") }
    private val main = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            val now = Date()
            clock?.text = if (style == "stacked") android.text.format.DateFormat.format(if (android.text.format.DateFormat.is24HourFormat(this@CoverService)) "HH\nmm" else "hh\nmm", now) else timeFormat.format(now)
            date?.text = android.text.format.DateFormat.format(datePattern, now)
            battery?.text = batteryText()
            main.postDelayed(this, 60_000L - System.currentTimeMillis() % 60_000L) // wakes at the next minute
        }
    }
    // Touches reach the detector before the views, so a double tap anywhere closes the cover.
    private val gestures by lazy {
        GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean { stopSelf(); return true }
            // a downward fling that starts on the top edge is the way back to the status bar and quick settings
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (e1 == null || vy <= 0f || e1.y > topZone() || e2.y <= e1.y) return false
                openQuickPanel()
                return true
            }
        })
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(com.umutk.pulse.QuietChannel(CH, "Screen cover", NotificationManager.IMPORTANCE_MIN))
        val n = NotificationCompat.Builder(this, CH).setSmallIcon(R.drawable.ic_qs_pulse).setContentTitle("Pulse")
            .setContentText(tr("Screen cover is on", "Ekran kapağı açık")).setOngoing(true).build()
        // startForeground first: the service was started as a foreground service and must not stop without it
        if (Build.VERSION.SDK_INT >= 34) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(ID, n)
        // the accessibility service, when it is on, draws the cover above everything (bars, keyboard, other overlays); otherwise the overlay permission is needed
        val acc = CoverAccessibilityService.instance
        if (acc == null && !Settings.canDrawOverlays(this)) { stopSelf(); return }

        val v = object : FrameLayout(this) {
            override fun dispatchTouchEvent(ev: MotionEvent): Boolean { gestures.onTouchEvent(ev); return super.dispatchTouchEvent(ev) }
        }
        v.setBackgroundColor(Color.BLACK)
        // the centred items stack in one column: clock, date, battery, own text
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        fun line(sp: Float) = TextView(this).apply { setTextColor(Color.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_SP, sp); gravity = Gravity.CENTER }
        if (CoverStore.clock(this)) {
            // the design picked in the settings (the previews there use the same faces)
            style = CoverStore.style(this)
            clock = when (style) {
                "bold" -> line(80f).apply { typeface = Typeface.create("sans-serif-black", Typeface.NORMAL) }
                "digital" -> line(64f).apply { typeface = Typeface.MONOSPACE }
                "serif" -> line(72f).apply { typeface = Typeface.SERIF }
                "stacked" -> line(96f).apply { typeface = Typeface.create("sans-serif-light", Typeface.NORMAL); setLineSpacing(0f, .9f) }
                "small" -> line(40f)
                else -> line(72f).apply { typeface = Typeface.create("sans-serif-light", Typeface.NORMAL) }
            }
            // a 12-hour time with AM/PM must not wrap into two lines: one line, shrinking to fit the screen width
            if (style != "stacked") clock?.let { c ->
                c.maxLines = 1; c.setHorizontallyScrolling(false)
                val sw = resources.displayMetrics.widthPixels - 2 * (40 * resources.displayMetrics.density).toInt()
                c.layoutParams = LinearLayout.LayoutParams(sw, LinearLayout.LayoutParams.WRAP_CONTENT)
                androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(c, 20, when (style) { "bold" -> 80; "digital" -> 64; "small" -> 40; else -> 72 }, 2, TypedValue.COMPLEX_UNIT_SP)
            }
            column.addView(clock)
        }
        if (CoverStore.date(this)) { date = line(18f); column.addView(date) }
        if (CoverStore.battery(this)) { battery = line(16f); column.addView(battery) }
        CoverStore.text(this).takeIf { it.isNotBlank() }?.let { column.addView(line(16f).apply { text = it }) }
        // the block sits where the settings put it: 3 x 3 spots, rows top / middle / bottom, columns left / centre / right
        val pos = CoverStore.pos(this)
        val gv = (when (pos / 3) { 0 -> Gravity.TOP; 2 -> Gravity.BOTTOM; else -> Gravity.CENTER_VERTICAL }) or (when (pos % 3) { 0 -> Gravity.START; 2 -> Gravity.END; else -> Gravity.CENTER_HORIZONTAL })
        column.gravity = when (pos % 3) { 0 -> Gravity.START; 2 -> Gravity.END; else -> Gravity.CENTER_HORIZONTAL }
        val m = (40 * resources.displayMetrics.density).toInt()
        if (column.childCount > 0) v.addView(column, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, gv).apply { setMargins(m, m * 2, m, m * 2) })
        wm = (acc ?: this).getSystemService(WindowManager::class.java)
        val layerType = if (acc != null) WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY else WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        val hole = CoverStore.hole(this)
        val br = CoverStore.bright(this)
        // no FLAG_NOT_TOUCHABLE: the cover takes every touch; the status and navigation bars sit under the overlay layer
        val flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON   // AOD-like: the screen stays on while the cover is up
        fun params(w: Int, h: Int, f: Int, fmt: Int) = WindowManager.LayoutParams(w, h, layerType, f, fmt).apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            if (br > 0) screenBrightness = br / 100f
        }
        if (hole == null) wm?.addView(v, params(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, flags, PixelFormat.OPAQUE))
        else {
            // an open window: four black bands around it, so touches inside the hole reach the apps below; the clock floats in a layer of its own
            val b = wm!!.currentWindowMetrics.bounds
            val sw = b.width(); val sh = b.height()
            val l = hole[0] * sw / 100; val t = hole[1] * sh / 100; val r = hole[2] * sw / 100; val bt = hole[3] * sh / 100
            v.removeView(column)
            v.setBackgroundColor(Color.TRANSPARENT)
            fun band(x: Int, y: Int, w: Int, h: Int) {
                if (w <= 0 || h <= 0) return
                val band = object : FrameLayout(this) { override fun dispatchTouchEvent(ev: MotionEvent): Boolean { gestures.onTouchEvent(ev); return super.dispatchTouchEvent(ev) } }
                band.setBackgroundColor(Color.BLACK)
                wm?.addView(band, params(w, h, flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.OPAQUE).apply { gravity = Gravity.TOP or Gravity.START; this.x = x; this.y = y })
                bands.add(band)
            }
            band(0, 0, sw, t); band(0, bt, sw, sh - bt); band(0, t, l, bt - t); band(r, t, sw - r, bt - t)
            if (column.childCount > 0) v.addView(column, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, gv).apply { setMargins(m, m * 2, m, m * 2) })
            wm?.addView(v, params(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, PixelFormat.TRANSLUCENT))
        }
        root = v
        running = true
        refreshTile()
        tick.run()
    }

    // a cover must not come back on its own after the process was killed
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onDestroy() {
        running = false
        refreshTile()
        main.removeCallbacks(tick)
        root?.let { runCatching { wm?.removeViewImmediate(it) } }
        root = null
        bands.forEach { b -> runCatching { wm?.removeViewImmediate(b) } }; bands.clear()
        super.onDestroy()
    }

    /** Closed from the cover itself (double tap): the quick settings tile re-reads the state instead of staying lit. */
    private fun refreshTile() {
        runCatching { android.service.quicksettings.TileService.requestListeningState(this, ComponentName(this, CoverTile::class.java)) }
    }

    private fun statusBarHeight(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else 0
    }

    /** The strip at the top where a swipe down counts as "pull the panel": the status bar, at least 24dp. */
    private fun topZone() = maxOf(statusBarHeight().toFloat(), 24 * resources.displayMetrics.density)

    private fun batteryText(): String {
        val i = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return ""
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1); val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        return if (level < 0 || scale <= 0) "" else "${level * 100 / scale}%"
    }

    /** Closes the cover, then pulls the quick settings panel down. The pull runs as root (`cmd statusbar`); without root the cover only closes. */
    private fun openQuickPanel() {
        stopSelf()
        // shortcut: root shell call, no check for the panel actually opening; the cover closes either way, upgrade to StatusBarManager when the platform grants it
        main.postDelayed({ Thread { runCatching { Runtime.getRuntime().exec(arrayOf("su", "-c", "cmd statusbar expand-settings")).waitFor() } }.start() }, 300L)
    }

    /** Closes the cover, then opens notification history through its launcher alias (ExtraToolActivity.routeOf maps it to "notifhist"). */
    private fun openNotifHist() {
        startActivity(Intent().setComponent(ComponentName(this, "com.umutk.pulse.NotifHistLauncher")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        stopSelf()
    }

    companion object {
        const val CH = "pulse_cover"; const val ID = 2010
        /** True while the cover is on screen; the tile reads it. */
        @Volatile var running = false
        fun start(c: Context) = ContextCompat.startForegroundService(c, Intent(c, CoverService::class.java))
        fun stop(c: Context) { c.stopService(Intent(c, CoverService::class.java)) }
    }
}

/** The notification bell, drawn from the same Fluent outline the Notifications page uses. */
private class BellView(c: Context) : View(c) {
    private val path = Path().apply { FluentPaths.map.getValue("notifications").forEach { d -> PathParser.createPathFromPathData(d)?.let { addPath(it) } } }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    override fun onDraw(canvas: Canvas) { canvas.scale(width / 24f, height / 24f); canvas.drawPath(path, paint) }
}


/**
 * Lets the screen cover draw as an accessibility overlay, the topmost layer there is: above the status and navigation bars, the keyboard and other overlays.
 * It reads nothing: no events, no window content. The cover uses it while this service is switched on in Accessibility settings.
 */
class CoverAccessibilityService : android.accessibilityservice.AccessibilityService() {
    // the automatic trigger of the advanced cover: this service stays alive, so it can watch the charger and the screen's direction
    private val power = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            // the launcher (another process) asks for a system action: lock the screen, open the notification shade
            if (i.action == "com.umutk.pulse.action.GLOBAL") { performGlobalAction(i.getIntExtra("g", 0)); return }
            if (i.action == Intent.ACTION_SCREEN_ON) lastTouch = android.os.SystemClock.uptimeMillis()
            autoCheck()
        }
    }

    // the idle trigger: a touch anywhere (the system tells this service when a touch starts and ends) restarts the count
    @Volatile private var lastTouch = android.os.SystemClock.uptimeMillis()
    private val tick = android.os.Handler(android.os.Looper.getMainLooper())
    private val idleCheck = object : Runnable {
        override fun run() {
            val now = android.os.SystemClock.uptimeMillis()
            val sec = StandByPrefs.idleSec(this@CoverAccessibilityService)
            if (StandBy.showing) lastTouch = now
            else if (sec > 0 && now - lastTouch >= sec * 1000L) {
                val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                val km = getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
                if (pm.isInteractive && !km.isKeyguardLocked) StandBy.show(this@CoverAccessibilityService)
                lastTouch = now
            }
            tick.postDelayed(this, 3000)
        }
    }

    /** Starts the advanced cover when the phone charges (and, if asked, lies sideways); closes it again once the trigger no longer holds. */
    private fun autoCheck() {
        if (!StandByPrefs.autoCharge(this)) return
        val charging = registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0)?.let { it != 0 } == true
        val sideways = !StandByPrefs.sideways(this) || resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        if (charging && sideways) { if (!StandBy.showing) StandBy.show(this, auto = true) }
        else if (StandBy.showing && StandBy.autoStarted) StandBy.hide(this)
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) { super.onConfigurationChanged(newConfig); autoCheck() }

    override fun onServiceConnected() {
        instance = this
        lastTouch = android.os.SystemClock.uptimeMillis(); tick.removeCallbacks(idleCheck); tick.postDelayed(idleCheck, 3000)
        runCatching {
            registerReceiver(power, android.content.IntentFilter().apply { addAction(Intent.ACTION_POWER_CONNECTED); addAction(Intent.ACTION_POWER_DISCONNECTED); addAction(Intent.ACTION_SCREEN_ON); addAction("com.umutk.pulse.action.GLOBAL") }, Context.RECEIVER_NOT_EXPORTED)
        }
    }
    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) { lastTouch = android.os.SystemClock.uptimeMillis() }
    override fun onInterrupt() {}
    override fun onUnbind(intent: Intent?): Boolean { tick.removeCallbacks(idleCheck); if (instance === this) instance = null; runCatching { unregisterReceiver(power) }; return super.onUnbind(intent) }
    override fun onDestroy() { if (instance === this) instance = null; super.onDestroy() }
    /** Closes the quick panel (a tile was tapped there): asked twice, a moment apart, so a panel that was still opening closes too. */
    fun collapseShade() {
        val go = Runnable { performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE) }
        tick.postDelayed(go, 200); tick.postDelayed(go, 600)
    }

    companion object { @Volatile var instance: CoverAccessibilityService? = null }
}
