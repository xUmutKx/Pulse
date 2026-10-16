package com.umutk.pulse

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.provider.Settings
import android.view.Surface
import android.view.View
import android.view.WindowManager
import androidx.core.content.ContextCompat

/** Car-sickness helper: dots along the screen edges that slide with the vehicle's acceleration, so the eyes see the motion the inner ear feels. */
class MotionCueService : Service() {
    private lateinit var wm: WindowManager
    private var view: CueView? = null
    private lateinit var sm: SensorManager
    private val g = FloatArray(3); private val gs = FloatArray(3); private var seeded = false

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            if (!seeded) { for (i in 0..2) { g[i] = e.values[i]; gs[i] = e.values[i] }; seeded = true } // start at rest, not at 0
            for (i in 0..2) { g[i] += .05f * (e.values[i] - g[i]); gs[i] += .004f * (e.values[i] - gs[i]) }
            // acceleration = raw minus gravity estimate; tilt = gravity drifting from its slow baseline, decays back to rest
            val ax = e.values[0] - g[0] + (g[0] - gs[0]) * .8f; val ay = e.values[1] - g[1] + (g[1] - gs[1]) * .8f
            // device axes to screen axes for the current rotation
            val (sx, sy) = when (wm.defaultDisplay.rotation) {
                Surface.ROTATION_90 -> -ay to ax
                Surface.ROTATION_180 -> -ax to -ay
                Surface.ROTATION_270 -> ay to -ax
                else -> ax to ay
            }
            view?.push(-sx, sy)
        }
        override fun onAccuracyChanged(s: Sensor?, a: Int) {}
    }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("cues", "Motion cues", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "cues").setSmallIcon(R.drawable.ic_qs_touch).setContentTitle("Motion cues on").setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(7, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(7, n)
        running = true
        loadPrefs(this)
        wm = getSystemService(WindowManager::class.java)
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return }
        val v = CueView(this).also { view = it }
        wm.addView(v, WindowManager.LayoutParams(-1, -1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT))
        sm = getSystemService(SensorManager::class.java)
        sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME, android.os.Handler(android.os.Looper.getMainLooper())) }
    }

    override fun onStartCommand(i: Intent?, f: Int, id: Int) = START_STICKY
    override fun onBind(i: Intent?) = null

    override fun onDestroy() {
        running = false
        runCatching { sm.unregisterListener(listener) }
        view?.let { runCatching { wm.removeView(it) } }
        super.onDestroy()
    }

    private class CueView(c: Context) : View(c) {
        private val d = c.resources.displayMetrics.density
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF000000.toInt() }
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); style = Paint.Style.STROKE; strokeWidth = 1f } // 1 px
        private val waveP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x40000000 }
        private val waveL = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); style = Paint.Style.STROKE; strokeWidth = 1f }
        private val path = android.graphics.Path()
        private var ox = 0f; private var oy = 0f   // target offset from the sensor
        private var sx = 0f; private var sy = 0f   // eased offset that is drawn
        private var phase = 0f; private var rise = 0f; private var last = 0L
        fun push(ax: Float, ay: Float) { // a spring pulls the dots back to the middle, acceleration pushes them away
            ox = (ox * .9f + ax * 14f * d).coerceIn(-48f * d, 48f * d)
            oy = (oy * .9f + ay * 14f * d).coerceIn(-48f * d, 48f * d)
        }
        override fun onDraw(cv: Canvas) {
            val w = width.toFloat(); val h = height.toFloat()
            val now = System.nanoTime(); val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(.1f); last = now
            // ease towards the sensor value every frame so sensor bursts never show as jitter
            val k = 1f - Math.exp(-12.0 * dt).toFloat()
            sx += (ox - sx) * k; sy += (oy - sy) * k
            // fewer, bigger dots on one even grid, centred in their cells
            val cell = 100f * d
            val cols = Math.max(3, Math.round(w / cell)); val rows = Math.max(4, Math.round(h / cell))
            val cw = w / cols; val ch = h / rows
            for (r in 0 until rows) for (c in 0 until cols) {
                val x = cw * (c + .5f) + sx; val y = ch * (r + .5f) + sy
                cv.drawCircle(x, y, 6f * d, fill); cv.drawCircle(x, y, 6f * d, ring)
            }
            if (wave) drawWave(cv, w, h, dt)
            postInvalidateOnAnimation()
        }
        /** A soft wave that rises from the bottom every few seconds and fades out; sideways motion slides it along, forward/back motion lifts it. */
        private fun drawWave(cv: Canvas, w: Float, h: Float, dt: Float) {
            rise = (rise + dt / 7f) % 1f
            phase = (phase + dt * 1.6f) % 6.2831855f
            val t = rise
            val base = h - h * .55f * t + sy * 1.5f
            val a = (1f - t) * (if (t < .1f) t / .1f else 1f)
            waveP.alpha = (a * 90).toInt(); waveL.alpha = (a * 255).toInt()
            val shift = sx / w * 6.2831855f * 2f // device moves right, the wave slides the same way
            path.rewind()
            var px = 0f; var first = true
            while (true) {
                val y = base + Math.sin(px / (w / 2f) * 6.2831855 + phase + shift).toFloat() * 16f * d
                if (first) { path.moveTo(px, y); first = false } else path.lineTo(px, y)
                if (px >= w) break
                px = Math.min(w, px + 8f * d)
            }
            cv.drawPath(path, waveL)
            path.lineTo(w, h); path.lineTo(0f, h); path.close()
            cv.drawPath(path, waveP)
        }
    }

    companion object {
        @Volatile var running = false
        @Volatile var wave = false
        fun loadPrefs(c: Context) { wave = c.getSharedPreferences("motion_cues", Context.MODE_PRIVATE).getBoolean("wave", false) }
        fun setWave(c: Context, on: Boolean) { wave = on; c.getSharedPreferences("motion_cues", Context.MODE_PRIVATE).edit().putBoolean("wave", on).apply() }
        fun toggle(c: Context): Boolean {
            if (!Settings.canDrawOverlays(c)) return false
            if (running) c.stopService(Intent(c, MotionCueService::class.java))
            else ContextCompat.startForegroundService(c, Intent(c, MotionCueService::class.java))
            return true
        }
    }
}
