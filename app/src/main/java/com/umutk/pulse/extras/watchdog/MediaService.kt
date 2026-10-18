package com.umutk.pulse.extras.watchdog

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.umutk.pulse.MainActivity
import com.umutk.pulse.tr

/**
 * Base for the music, video and ambient services. Runs as a mediaPlayback foreground service,
 * beats [ServiceHealth] every minute, and answers a null or ACTION_RESUME start with [onResume]
 * so the last track or mix comes back after Android killed the process.
 * Start and stop through [start] and [stop]: they record the user's intent for [ServiceWatchdog].
 */
abstract class MediaService : Service() {

    /** Notification title, shown while the service runs. */
    abstract val title: String

    /** Unique per subclass. */
    abstract val notifId: Int

    /** Called on a restart without intent, or with ACTION_RESUME. Override to reload the player. */
    open fun onResume(last: QuickTileContract.LastPlayed?) {}

    private val handler = Handler(Looper.getMainLooper())
    private val beatTask = object : Runnable {
        override fun run() {
            ServiceHealth.beat(this@MediaService, this@MediaService.javaClass)
            handler.postDelayed(this, BEAT_MS)
        }
    }

    private var wake: PowerManager.WakeLock? = null

    override fun onBind(i: Intent?): IBinder? = null

    /** The service ends itself (queue finished): the watchdog must not bring it back. */
    protected fun finish() {
        ServiceWatchdog.setWanted(this, javaClass, false)
        stopSelf()
    }

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CH, tr("Playback", "Oynatma"), NotificationManager.IMPORTANCE_LOW))
        // Keeps the CPU awake while the service runs; released in onDestroy. Needs WAKE_LOCK (already in the manifest).
        wake = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "pulse:${javaClass.simpleName}")
            .apply { setReferenceCounted(false); acquire() }
        handler.post(beatTask)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must be called within 5 seconds of a foreground start, so it comes first.
        try {
            startForeground(notifId, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } catch (e: RuntimeException) {
            // Android refused the foreground start (background restriction); the watchdog retries at its next check.
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent == null || intent.action == QuickTileContract.ACTION_RESUME) {
            onResume(QuickTileContract.load(this))
        }
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(beatTask)
        wake?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CH)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Pulse")
            .setContentText(title)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(open)
            .build()
    }

    companion object {
        private const val CH = "pulse_playback"
        private const val BEAT_MS = 60_000L

        /** Starts [cls] in the foreground and keeps it alive. False when Android refused the start; the watchdog retries. */
        fun start(c: Context, cls: Class<out MediaService>): Boolean {
            ServiceWatchdog.setWanted(c, cls, true)
            return try {
                ContextCompat.startForegroundService(c, Intent(c, cls))
                true
            } catch (e: IllegalStateException) {
                false
            } catch (e: SecurityException) {
                false
            }
        }

        /** The user stopped it: the watchdog stops watching it and it stays down. */
        fun stop(c: Context, cls: Class<out MediaService>) {
            ServiceWatchdog.setWanted(c, cls, false)
            c.stopService(Intent(c, cls))
        }
    }
}

/**
 * Keep-alive anchor for music. The real player is extras.music.MusicPlaybackService (Media3 session); this class only
 * brings it back: after a kill, [onResume] reloads the last track into the shared MusicPlayerEngine.
 */
class MusicPlaybackService : MediaService() {
    override val title: String get() = tr("Playing music", "Müzik çalıyor")
    override val notifId = 9301
    override fun onResume(last: QuickTileContract.LastPlayed?) {
        if (last?.kind != QuickTileContract.Kind.TRACK) return
        val e = com.umutk.pulse.extras.music.MusicPlayerEngine.get(this)
        if (e.player.mediaItemCount > 0) e.play()
        else if (last.ref.isNotEmpty()) e.playQueue(listOf(com.umutk.pulse.extras.music.Track(last.ref, last.name)))
    }
}

class VideoPlaybackService : MediaService() {
    override val title: String get() = tr("Playing video", "Video oynatılıyor")
    override val notifId = 9302
}

class AmbientPlaybackService : MediaService() {
    override val title: String get() = tr("Ambient sound playing", "Ortam sesi çalıyor")
    override val notifId = 9303
    override fun onResume(last: QuickTileContract.LastPlayed?) {
        val a = com.umutk.pulse.extras.ambient.AmbientLast
        if (a.hasLast(this) && !a.isPlaying()) a.toggle(this)
    }
}
