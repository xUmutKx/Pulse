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
    /** The last answer (the speech bubble of the floating mascot shows it when the model is done). */
    @Volatile var lastAnswer = ""

    fun openChat(c: Context) { com.umutk.pulse.launchExtra(c, "com.umutk.pulse.ExtraToolActivity", "ai") }

    /** The model starts or stops answering: the floating mascot dances, the Island shows it. */
    fun set(c: Context, w: Boolean) {
        val app = c.applicationContext
        working = w
        AiBuddyService.live?.setWorking(w, lastAnswer)
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

/** Hosts the floating mascot: the walking, draggable, talking buddy copied from Claude Chat (cc/Buddy.kt). */
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
    private var buddy: com.umutk.pulse.extras.ai.cc.Buddy? = null
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
        if (!com.umutk.pulse.Compat.canOverlay(this)) { applicationContext.getSharedPreferences("ai_overlay", Context.MODE_PRIVATE).edit().putBoolean("buddy", false).apply(); stopSelf(); return }
        com.umutk.pulse.extras.ai.cc.Prefs.init(this)
        buddy = com.umutk.pulse.extras.ai.cc.Buddy(this).also { it.render(com.umutk.pulse.extras.ai.cc.Status.Idle, "", "") }
        if (AiLive.working) setWorking(true, "")
    }

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        if (i?.action == "stop") { applicationContext.getSharedPreferences("ai_overlay", Context.MODE_PRIVATE).edit().putBoolean("buddy", false).apply(); stopSelf() }
        return START_STICKY
    }

    /** The model starts answering (the mascot works and says so) or is done (it is happy and shows the answer in a speech bubble). */
    fun setWorking(w: Boolean, last: String) {
        main.post {
            val b = buddy ?: return@post
            if (w) b.render(com.umutk.pulse.extras.ai.cc.Status.Working, tr("Working…", "Çalışıyorum…"), "")
            else b.render(com.umutk.pulse.extras.ai.cc.Status.Done, "", last)
        }
    }

    override fun onDestroy() {
        buddy?.hide(); buddy = null; live = null
        super.onDestroy()
    }
}
