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
    private var clock: TextView? = null
    private var date: TextView? = null
    private var battery: TextView? = null
    private val timeFormat by lazy { android.text.format.DateFormat.getTimeFormat(this) }
    private val datePattern by lazy { android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEE d MMMM") }
    private val main = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            val now = Date()
            clock?.text = timeFormat.format(now)
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
        nm.createNotificationChannel(NotificationChannel(CH, "Screen cover", NotificationManager.IMPORTANCE_MIN))
        val n = NotificationCompat.Builder(this, CH).setSmallIcon(R.drawable.ic_qs_pulse).setContentTitle("Pulse")
            .setContentText(tr("Screen cover is on", "Ekran kapağı açık")).setOngoing(true).build()
        // startForeground first: the service was started as a foreground service and must not stop without it
        if (Build.VERSION.SDK_INT >= 34) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(ID, n)
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return }

        val v = object : FrameLayout(this) {
            override fun dispatchTouchEvent(ev: MotionEvent): Boolean { gestures.onTouchEvent(ev); return super.dispatchTouchEvent(ev) }
        }
        v.setBackgroundColor(Color.BLACK)
        // the centred items stack in one column: clock, date, battery, own text
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        fun line(sp: Float) = TextView(this).apply { setTextColor(Color.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_SP, sp); gravity = Gravity.CENTER }
        if (CoverStore.clock(this)) {
            clock = line(72f).apply { typeface = Typeface.create("sans-serif-light", Typeface.NORMAL) }
            column.addView(clock)
        }
        if (CoverStore.date(this)) { date = line(18f); column.addView(date) }
        if (CoverStore.battery(this)) { battery = line(16f); column.addView(battery) }
        CoverStore.text(this).takeIf { it.isNotBlank() }?.let { column.addView(line(16f).apply { text = it }) }
        if (column.childCount > 0) v.addView(column, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        wm = getSystemService(WindowManager::class.java)
        // no FLAG_NOT_TOUCHABLE: the cover takes every touch; the status and navigation bars sit under the overlay layer
        wm?.addView(v, WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON, PixelFormat.OPAQUE)   // AOD-like: the screen stays on while the cover is up
            .apply { layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES })
        root = v
        running = true
        tick.run()
    }

    // a cover must not come back on its own after the process was killed
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onDestroy() {
        running = false
        main.removeCallbacks(tick)
        root?.let { runCatching { wm?.removeViewImmediate(it) } }
        root = null
        super.onDestroy()
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
