package com.umutk.pulse.extras.ai

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.umutk.pulse.QuietChannel
import com.umutk.pulse.R
import com.umutk.pulse.extras.island.IslandItem
import com.umutk.pulse.extras.island.IslandService
import com.umutk.pulse.tr

/*
 * Pulse AI's mascot outside the app. Two parts, both of them showing the one original orange mascot:
 *  - the pill: Pulse's own Island (extras/island) shows "Pulse AI - Working..." while the model answers and "Done" for a moment after (size, place and
 *    gestures are the Island's own settings, their defaults);
 *  - the floating mascot: a small mascot over every app that dances while the model works; drag it, it settles at the nearest edge; a tap opens Pulse AI.
 * The idea (the mascot on top of every app, the pill) is from Claude Chat (xUmutKx/ClaudeChat); the code here is Pulse's own.
 */
object AiLive {
    @Volatile var working = false; private set

    fun openChat(c: Context) { com.umutk.pulse.launchExtra(c, "com.umutk.pulse.ExtraToolActivity", "ai") }

    /** The model starts or stops answering: the floating mascot dances, the Island shows it. */
    fun set(c: Context, w: Boolean) {
        val app = c.applicationContext
        working = w
        AiBuddyService.live?.setWorking(w)
        val island = IslandService.live ?: return
        val orange = 0xFFD97757.toInt()
        island.post(IslandItem("ai", "ai", "Pulse AI", if (w) tr("Working…", "Çalışıyor…") else tr("Done", "Bitti"), icon = mascotBitmap(app), color = orange, prio = 3,
            ttl = if (w) 0L else 3000L, onTap = { openChat(app) }))
    }

    fun mascotBitmap(c: Context, closed: Boolean = false): Bitmap {
        val d = ContextCompat.getDrawable(c, if (closed) R.drawable.cc_mascot_sleep18 else R.drawable.cc_mascot18)!!.mutate()
        val b = Bitmap.createBitmap(120, 108, Bitmap.Config.ARGB_8888); d.setBounds(0, 0, 120, 108); d.draw(Canvas(b)); return b
    }
}

class AiBuddyService : Service() {
    companion object {
        @Volatile var live: AiBuddyService? = null
        private fun sp(c: Context) = c.applicationContext.getSharedPreferences("ai_overlay", Context.MODE_PRIVATE)
        fun enabled(c: Context) = sp(c).getBoolean("buddy", false)
        fun setEnabled(c: Context, on: Boolean) {
            sp(c).edit().putBoolean("buddy", on).apply()
            if (on) { val i = Intent(c, AiBuddyService::class.java); if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i) else c.startService(i) }
            else c.stopService(Intent(c, AiBuddyService::class.java))
        }
    }

    private val main = Handler(Looper.getMainLooper())
    private lateinit var wm: WindowManager
    private var view: BuddyView? = null
    private lateinit var lp: WindowManager.LayoutParams
    private val ch = "ai_buddy"

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        live = this
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(QuietChannel(ch, tr("Pulse AI mascot", "Pulse AI maskotu"), NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, 1, Intent(this, AiBuddyService::class.java).setAction("stop"), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n: Notification = NotificationCompat.Builder(this, ch).setSmallIcon(android.R.drawable.stat_notify_chat).setOngoing(true).setSilent(true)
            .setContentTitle(tr("Pulse AI mascot", "Pulse AI maskotu")).addAction(0, tr("Stop", "Durdur"), stop).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(7461, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else startForeground(7461, n)
        if (!com.umutk.pulse.Compat.canOverlay(this)) { sp().edit().putBoolean("buddy", false).apply(); stopSelf(); return }
        wm = getSystemService(WindowManager::class.java)
        add()
        view?.dance(AiLive.working)
    }

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        if (i?.action == "stop") { sp().edit().putBoolean("buddy", false).apply(); stopSelf() }
        return START_STICKY
    }

    private fun sp() = applicationContext.getSharedPreferences("ai_overlay", Context.MODE_PRIVATE)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun screenW() = resources.displayMetrics.widthPixels
    private fun screenH() = resources.displayMetrics.heightPixels

    private fun add() {
        val w = dp(54); val h = dp(49)
        val v = BuddyView(this)
        view = v
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        lp = WindowManager.LayoutParams(w, h, type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT)
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = sp().getInt("x", screenW() - w); lp.y = sp().getInt("y", (screenH() * 0.4f).toInt())
        var dx = 0f; var dy = 0f; var sx = 0; var sy = 0; var moved = false
        val slop = dp(8)
        v.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { dx = e.rawX; dy = e.rawY; sx = lp.x; sy = lp.y; moved = false }
                MotionEvent.ACTION_MOVE -> {
                    if (!moved && (Math.abs(e.rawX - dx) > slop || Math.abs(e.rawY - dy) > slop)) moved = true
                    if (moved) { lp.x = (sx + (e.rawX - dx)).toInt().coerceIn(0, screenW() - w); lp.y = (sy + (e.rawY - dy)).toInt().coerceIn(0, screenH() - h); runCatching { wm.updateViewLayout(v, lp) } }
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) AiLive.openChat(this)
                    else {
                        // it settles at the nearest side
                        val to = if (lp.x + w / 2 < screenW() / 2) 0 else screenW() - w
                        android.animation.ValueAnimator.ofInt(lp.x, to).apply {
                            duration = 180
                            addUpdateListener { lp.x = it.animatedValue as Int; runCatching { wm.updateViewLayout(v, lp) } }
                            addListener(object : android.animation.AnimatorListenerAdapter() { override fun onAnimationEnd(a: android.animation.Animator) { sp().edit().putInt("x", lp.x).putInt("y", lp.y).apply() } })
                        }.start()
                    }
                }
            }
            true
        }
        runCatching { wm.addView(v, lp) }.onFailure { stopSelf() }
    }

    fun setWorking(w: Boolean) { main.post { view?.dance(w) } }

    override fun onDestroy() {
        view?.let { v -> v.stop(); runCatching { wm.removeView(v) } }
        view = null; live = null
        super.onDestroy()
    }
}

/** The original orange mascot: it blinks now and then; while the model works it dances (centre, tilt left, centre, tilt right). */
private class BuddyView(c: Context) : View(c) {
    private val awake = ContextCompat.getDrawable(c, R.drawable.cc_mascot18)!!.mutate()
    private val asleep = ContextCompat.getDrawable(c, R.drawable.cc_mascot_sleep18)!!.mutate()
    private val main = Handler(Looper.getMainLooper())
    private var closed = false
    private var working = false
    private var frame = 0
    private val rot = floatArrayOf(0f, -12f, 0f, 12f)
    private val up = floatArrayOf(1f, 0f, 1f, 0f)
    private val tick = object : Runnable {
        override fun run() { frame = (frame + 1) % 4; invalidate(); main.postDelayed(this, 280) }
    }
    private val blink = object : Runnable {
        override fun run() {
            if (!working) { closed = true; invalidate(); main.postDelayed({ closed = false; invalidate() }, 140) }
            main.postDelayed(this, 2200L + (0..3800).random())
        }
    }
    init { main.postDelayed(blink, 2200) }

    fun dance(on: Boolean) { if (on == working) return; working = on; main.removeCallbacks(tick); frame = 0; if (on) main.post(tick) else invalidate() }
    fun stop() { main.removeCallbacksAndMessages(null) }

    override fun onDraw(cv: Canvas) {
        val d = if (closed && !working) asleep else awake
        d.setBounds(0, 0, width, height)
        cv.save()
        if (working) { cv.rotate(rot[frame], width / 2f, height.toFloat()); cv.translate(0f, -up[frame] * 4f * resources.displayMetrics.density) }
        d.draw(cv)
        cv.restore()
    }
}
