// The motion estimator and the dot field are adapted from "Motion cues" by David Ventura (https://github.com/davidv/motion-sickness-app),
// SPDX-License-Identifier: GPL-3.0-only (Pulse is GPL-3.0 too): sensors fused into a world-fixed dot field that drifts with the vehicle.
package com.umutk.pulse

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
import android.view.View
import android.view.WindowManager
import androidx.core.content.ContextCompat
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Horizontal-world acceleration (m/s²) in the screen plane, the part out of the screen, the roll of the phone and the turning rates around the world. */
class MotionVector(val x: Float, val y: Float, val outOfPlane: Float, val roll: Float, val yawRate: Float, val pitchRate: Float) {
    companion object { val ZERO = MotionVector(0f, 0f, 0f, 0f, 0f, 0f) }
}

/** Fuses linear acceleration, the rotation vector and the gyroscope, with the gyroscope's own drift tracked while the phone lies still. */
class MotionEstimator(context: Context, private val out: (MotionVector) -> Unit) {
    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val lin = sm.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private val rot = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val gyro = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val rm = FloatArray(9)
    private var hasRot = false
    private var fx = 0f; private var fy = 0f; private var fz = 0f
    private var roll = 0f; private var fyaw = 0f; private var fpitch = 0f
    private var lastA = 0L; private var lastG = 0L
    private var bx = 0f; private var by = 0f; private var bz = 0f
    private var still = 0f; private var accMagSq = 0f
    @Volatile var events = 0

    /** False when the phone has no linear acceleration or rotation sensor: the overlay then falls back to the plain accelerometer. */
    val usable get() = lin != null && rot != null

    private val l = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            events++
            when (e.sensor.type) {
                Sensor.TYPE_ROTATION_VECTOR -> { SensorManager.getRotationMatrixFromVector(rm, e.values); hasRot = true }
                Sensor.TYPE_LINEAR_ACCELERATION -> if (hasRot) accel(e.values[0], e.values[1], e.values[2], e.timestamp)
                Sensor.TYPE_GYROSCOPE -> if (hasRot) gyro(e.values[0], e.values[1], e.values[2], e.timestamp)
            }
        }
        override fun onAccuracyChanged(s: Sensor?, a: Int) {}
    }

    private fun accel(ax: Float, ay: Float, az: Float, ts: Long) {
        val ux = rm[6]; val uy = rm[7]; val uz = rm[8]
        val dot = ax * ux + ay * uy + az * uz
        val hx = ax - dot * ux; val hy = ay - dot * uy; val hz = az - dot * uz
        val dt = if (lastA == 0L) .02f else ((ts - lastA) / 1e9f).coerceIn(.001f, .2f)
        lastA = ts
        val a = dt / (.08f + dt)
        fx += a * (hx - fx); fy += a * (hy - fy); fz += a * (hz - fz)
        accMagSq = hx * hx + hy * hy + hz * hz
        if (ux * ux + uy * uy > .04f) roll = atan2(ux, uy)
        publish()
    }

    private fun gyro(gx: Float, gy: Float, gz: Float, ts: Long) {
        val dt = if (lastG == 0L) .01f else ((ts - lastG) / 1e9f).coerceIn(.001f, .2f)
        lastG = ts
        val isStill = accMagSq < .09f && gx * gx + gy * gy + gz * gz < .0025f
        if (isStill) {
            still += dt
            if (still > .8f) { val ba = dt / (1.5f + dt); bx += ba * (gx - bx); by += ba * (gy - by); bz += ba * (gz - bz) }
        } else still = 0f
        val gxd = gx - bx; val gyd = gy - by; val gzd = gz - bz
        val owx = rm[0] * gxd + rm[1] * gyd + rm[2] * gzd
        val owy = rm[3] * gxd + rm[4] * gyd + rm[5] * gzd
        val owz = rm[6] * gxd + rm[7] * gyd + rm[8] * gzd
        val fwx = -rm[2]; val fwy = -rm[5]; val fl = fwx * fwx + fwy * fwy
        val pitch = if (fl > .04f) { val inv = 1f / sqrt(fl); owx * (-fwy * inv) + owy * (fwx * inv) } else 0f
        val a = dt / (.03f + dt)
        fyaw += a * (owz - fyaw); fpitch += a * (pitch - fpitch)
        publish()
    }

    private fun dead(v: Float, t: Float) = if (v > t) v - t else if (v < -t) v + t else 0f
    private fun publish() = out(MotionVector(fx, fy, fz, roll, dead(fyaw, .01f), dead(fpitch, .01f)))

    fun start(handler: android.os.Handler) {
        sm.registerListener(l, lin, SensorManager.SENSOR_DELAY_GAME, handler)
        sm.registerListener(l, rot, SensorManager.SENSOR_DELAY_GAME, handler)
        sm.registerListener(l, gyro, SensorManager.SENSOR_DELAY_GAME, handler)
    }

    fun stop() { sm.unregisterListener(l); hasRot = false; lastA = 0L; lastG = 0L; fx = 0f; fy = 0f; fz = 0f; roll = 0f; fyaw = 0f; fpitch = 0f; still = 0f; accMagSq = 0f; out(MotionVector.ZERO) }
}

/** Car-sickness helper: a field of dots that stays fixed in the world while the phone moves, so the eyes see the motion the inner ear feels. */
class MotionCueService : Service() {
    private lateinit var wm: WindowManager
    private var view: CueView? = null
    private var est: MotionEstimator? = null
    private var strip: View? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(QuietChannel("cues", "Motion cues", NotificationManager.IMPORTANCE_LOW))
        val n = androidx.core.app.NotificationCompat.Builder(this, "cues").setSmallIcon(R.drawable.ic_qs_touch).setContentTitle("Motion cues on").setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(7, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(7, n)
        running = true
        loadPrefs(this)
        wm = getSystemService(WindowManager::class.java)
        if (!Compat.canOverlay(this)) { stopSelf(); return }
        val v = CueView(this).also { view = it }
        val lp = WindowManager.LayoutParams(-1, -1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT)
        // Android 12 treats touches through a window that is more than about 80% opaque as "obscured" and many apps refuse them; just under that, taps reach the app below
        if (Build.VERSION.SDK_INT >= 31) lp.alpha = .79f
        wm.addView(v, lp)
        addTopStrip()
        est = MotionEstimator(this) { m -> view?.setMotion(m) }.also { it.start(android.os.Handler(android.os.Looper.getMainLooper())) }
    }

    /**
     * The way out, like the screen cover: a thin strip along the top edge takes touches (the dots themselves never do). A double tap on it switches the cues off;
     * a swipe down from it switches them off and pulls the quick settings panel down, so they can be closed from there too.
     */
    private fun addTopStrip() {
        val d = resources.displayMetrics.density
        val sbId = resources.getIdentifier("status_bar_height", "dimen", "android")
        val h = maxOf(if (sbId > 0) resources.getDimensionPixelSize(sbId) else 0, (24 * d).toInt())
        var lastTap = 0L; var downAt = 0L; var downX = 0f; var downY = 0f
        val gestures = android.view.GestureDetector(this, object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: android.view.MotionEvent?, e2: android.view.MotionEvent, vx: Float, vy: Float): Boolean {
                if (e1 == null || vy <= 0f || e2.y <= e1.y) return false
                stopSelf()
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    com.umutk.pulse.extras.launcher.globalAction(applicationContext, android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
                    Thread { runCatching { Runtime.getRuntime().exec(arrayOf("su", "-c", "cmd statusbar expand-settings")).waitFor() } }.start()
                }, 250L)
                return true
            }
        })
        val sv = object : View(this) {
            override fun onTouchEvent(ev: android.view.MotionEvent): Boolean {
                gestures.onTouchEvent(ev)
                when (ev.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> { downAt = ev.eventTime; downX = ev.x; downY = ev.y }
                    android.view.MotionEvent.ACTION_UP -> {
                        val slop = 48f * d
                        if (ev.eventTime - downAt < 350 && Math.hypot((ev.x - downX).toDouble(), (ev.y - downY).toDouble()) < slop) {
                            // a tap closes the quick settings / notification panel that is open under it
                            // with root the shade is collapsed by the system itself (no accessibility permission needed); without it the accessibility action does it
                            Thread { runCatching { Runtime.getRuntime().exec(arrayOf("su", "-c", "cmd statusbar collapse")).waitFor() } }.start()
                            com.umutk.pulse.extras.launcher.globalAction(applicationContext, android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
                            if (ev.eventTime - lastTap < 600) { lastTap = 0L; stopSelf() } else lastTap = ev.eventTime
                        } else lastTap = 0L
                    }
                }
                return true
            }
        }
        val lp = WindowManager.LayoutParams(-1, h, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT)
        lp.gravity = android.view.Gravity.TOP
        runCatching { wm.addView(sv, lp); strip = sv }
    }

    override fun onStartCommand(i: Intent?, f: Int, id: Int) = START_STICKY
    override fun onBind(i: Intent?) = null

    override fun onDestroy() {
        running = false
        // the quick settings tile reads the state again, so it does not stay lit after a close from the top edge
        if (Build.VERSION.SDK_INT >= 24) runCatching { android.service.quicksettings.TileService.requestListeningState(this, android.content.ComponentName(this, MotionCueTile::class.java)) }
        est?.stop()
        view?.let { runCatching { wm.removeView(it) } }
        strip?.let { runCatching { wm.removeView(it) } }
        super.onDestroy()
    }

    private class CueView(c: Context) : View(c) {
        private val d = c.resources.displayMetrics.density
        private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); alpha = 217; style = Paint.Style.FILL }
        private val waveP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x40000000 }
        private val waveL = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); style = Paint.Style.STROKE; strokeWidth = 1f }
        private val path = android.graphics.Path()
        @Volatile private var m = MotionVector.ZERO
        private val px = FloatArray(PARTICLES); private val py = FloatArray(PARTICLES); private val ps = FloatArray(PARTICLES)
        private var halfShort = 1f; private var halfDiag = 1f; private var cx = 0f; private var cy = 0f
        private var gvx = 0f; private var gvy = 0f; private var gox = 0f; private var goy = 0f; private var boost = 0f
        private var last = 0L; private var rise = 0f; private var wx = 0f; private var wy = 0f

        fun setMotion(v: MotionVector) { m = v }

        override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
            halfShort = min(w, h) / 2f; halfDiag = sqrt(w.toFloat() * w + h.toFloat() * h) / 2f; cx = w / 2f; cy = h / 2f
            val rng = Random(42); val sp = 2f * EXT / DIM; var i = 0
            for (row in 0 until DIM) { val off = if (row % 2 == 1) sp * .5f else 0f
                for (col in 0 until DIM) { px[i] = (col + .5f) * sp - EXT + off + (rng.nextFloat() - .5f) * .04f; py[i] = (row + .5f) * sp - EXT + (rng.nextFloat() - .5f) * .04f; ps[i] = 8f * (.85f + rng.nextFloat() * .3f); i++ } }
        }

        override fun onDraw(cv: Canvas) {
            val now = System.nanoTime(); val dt = if (last == 0L) 1f / 60f else ((now - last) / 1e9f).coerceIn(.001f, .1f); last = now
            val mv = m
            // the grid is pushed against the acceleration, damped, and turned by the turning rates
            val cr = cos(mv.roll); val sr = sin(mv.roll)
            gvx += -(mv.x * cr - mv.y * sr) * .6f * dt; gvy += -(mv.x * sr + mv.y * cr) * .6f * dt
            val damp = exp(-1.2f * dt); gvx *= damp; gvy *= damp
            gox += gvx * dt + mv.yawRate * 1.5f * dt; goy += gvy * dt + mv.pitchRate * 1.5f * dt
            val span = 2f * EXT
            if (gox > EXT) gox -= span else if (gox < -EXT) gox += span
            if (goy > EXT) goy -= span else if (goy < -EXT) goy += span
            val target = max(0f, mv.outOfPlane) * 1.2f
            if (target > boost) boost = target
            boost *= exp(-dt / .9f)
            val sizeBoost = (1f + boost).coerceIn(1f, 8f)
            for (i in 0 until PARTICLES) {
                val lx = wrap(px[i] + gox, EXT, span); val ly = wrap(py[i] + goy, EXT, span)
                val sx = lx * cr + ly * sr; val sy = -lx * sr + ly * cr
                val x = cx + sx * halfShort; val y = cy - sy * halfShort
                var r = ps[i] * d * sizeBoost * .5f
                if (focus) {
                    val dist = sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy)) / halfDiag
                    val f = smooth(.25f, .9f, dist)
                    if (f <= .01f) continue
                    r *= f
                }
                cv.drawCircle(x, y, r, dot)
            }
            if (wave) drawWave(cv, width.toFloat(), height.toFloat(), dt, mv)
            postInvalidateOnAnimation()
        }

        /** A flat surface, like liquid in a glass: it rises from the bottom every few seconds and fades out. Sideways motion tilts it, forward/back motion lifts it. */
        private fun drawWave(cv: Canvas, w: Float, h: Float, dt: Float, mv: MotionVector) {
            val k = 1f - exp(-8f * dt)
            wx += ((-mv.x * 14f * d).coerceIn(-48f * d, 48f * d) - wx) * k; wy += ((mv.y * 14f * d).coerceIn(-48f * d, 48f * d) - wy) * k
            rise = (rise + dt / 7f) % 1f
            val t = rise
            val level = h - h * .55f * t + wy * 1.5f
            val a = (1f - t) * (if (t < .1f) t / .1f else 1f)
            waveP.alpha = (a * 90).toInt(); waveL.alpha = (a * 255).toInt()
            val tilt = wx * .6f
            val yl = level + tilt; val yr = level - tilt
            path.rewind(); path.moveTo(0f, yl); path.lineTo(w, yr)
            cv.drawPath(path, waveL)
            path.lineTo(w, h); path.lineTo(0f, h); path.close()
            cv.drawPath(path, waveP)
        }

        private fun wrap(v: Float, ext: Float, span: Float): Float { var r = (v + ext) % span; if (r < 0f) r += span; return r - ext }
        private fun smooth(e0: Float, e1: Float, x: Float): Float { val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f); return t * t * (3f - 2f * t) }

        private companion object { const val DIM = 12; const val PARTICLES = DIM * DIM; const val EXT = 3.5f }
    }

    companion object {
        @Volatile var running = false
        @Volatile var wave = false
        /** true: the middle of the screen stays clear and the dots sit near the edges; false: dots cover the whole screen. */
        @Volatile var focus = true
        fun loadPrefs(c: Context) { val p = c.getSharedPreferences("motion_cues", Context.MODE_PRIVATE); wave = p.getBoolean("wave", false); focus = p.getBoolean("focus", true) }
        fun setWave(c: Context, on: Boolean) { wave = on; c.getSharedPreferences("motion_cues", Context.MODE_PRIVATE).edit().putBoolean("wave", on).apply() }
        fun setFocus(c: Context, on: Boolean) { focus = on; c.getSharedPreferences("motion_cues", Context.MODE_PRIVATE).edit().putBoolean("focus", on).apply() }
        fun toggle(c: Context): Boolean {
            if (!Compat.canOverlay(c)) return false
            if (running) c.stopService(Intent(c, MotionCueService::class.java))
            else ContextCompat.startForegroundService(c, Intent(c, MotionCueService::class.java))
            return true
        }
    }
}
