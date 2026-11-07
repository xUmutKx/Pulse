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
        // the keep-alive notification is the least important one: no icon in the status bar, no sound, no pop-up. A channel's
        // importance cannot change once made, so the new channel has a new id and the old one is removed.
        if (android.os.Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager::class.java).apply {
            deleteNotificationChannel("pulse_playback")
            createNotificationChannel(com.umutk.pulse.QuietChannel(CH, tr("Playback", "Oynatma"), NotificationManager.IMPORTANCE_MIN))
        }
        // Keeps the CPU awake while the service runs; released in onDestroy. Needs WAKE_LOCK (already in the manifest).
        wake = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "pulse:${javaClass.simpleName}")
            .apply { setReferenceCounted(false); acquire() }
        handler.post(beatTask)
    }

    /** What a tap on the notification opens (the screen of this service's own player, not the Pulse start page). */
    protected open fun openIntent(): Intent = Intent(this, MainActivity::class.java)

    /** True: the notification carries a Stop button; [onStop] runs when it is pressed. */
    protected open val hasStop: Boolean = false
    /** The picture of the notification (a drawn one for the sounds that have no cover); none by default. */
    protected open val art: android.graphics.Bitmap? get() = null
    protected open fun onStop() {}

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { onStop(); finish(); return START_NOT_STICKY }
        // Must be called within 5 seconds of a foreground start, so it comes first.
        try {
            if (android.os.Build.VERSION.SDK_INT >= 29) startForeground(notifId, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK) else startForeground(notifId, notification())
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

    /** Shows the current [notification] again (the title, the cover or the play state changed). */
    protected fun refreshNotification() {
        runCatching { getSystemService(NotificationManager::class.java).notify(notifId, notification()) }
    }

    override fun onDestroy() {
        handler.removeCallbacks(beatTask)
        wake?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    /** Builds the foreground notification; a service with a richer one (the video player: cover, previous, play or pause, next) overrides it. */
    protected open fun notification(): Notification {
        val open = PendingIntent.getActivity(this, 0, openIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE)
        val b = NotificationCompat.Builder(this, CH)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Pulse")
            .setContentText(title)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(open)
        art?.let { b.setLargeIcon(it) }
        if (hasStop) b.addAction(android.R.drawable.ic_media_pause, tr("Stop", "Durdur"),
            PendingIntent.getService(this, 1, Intent(this, javaClass).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        return b.build()
    }

    companion object {
        const val CH = "pulse_playback2"
        const val ACTION_STOP = "com.umutk.pulse.action.MEDIA_STOP"
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
    override fun openIntent(): Intent = Intent().setClassName(packageName, "$packageName.ExtraToolActivity").putExtra("route", "music").putExtra(com.umutk.pulse.extras.music.MusicPlaybackService.EXTRA_OPEN, "player")
    override fun onResume(last: QuickTileContract.LastPlayed?) {
        if (last?.kind != QuickTileContract.Kind.TRACK) return
        // Only a restart after music was playing at its last state change; a paused or closed player stays silent.
        if (!com.umutk.pulse.extras.music.Playlists.wasPlaying(this)) return
        val e = com.umutk.pulse.extras.music.MusicPlayerEngine.get(this)
        if (e.player.mediaItemCount > 0) e.play()
        else if (last.ref.isNotEmpty()) e.playQueue(listOf(com.umutk.pulse.extras.music.Track(last.ref, last.name)))
    }
}

class VideoPlaybackService : MediaService() {
    override val title: String get() = tr("Playing video", "Video oynatılıyor")
    override val notifId = 9302

    private var bound: androidx.media3.common.Player? = null
    private var thumb: android.graphics.Bitmap? = null
    private var thumbKey: String? = null

    // the notification follows the video: another video, play or pause, new title
    private val listener = object : androidx.media3.common.Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = refreshNotification()
        override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) = refreshNotification()
        override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) = refreshNotification()
    }

    private fun attach() {
        val p = com.umutk.pulse.extras.video.VideoSessionService.player ?: return
        if (bound === p) return
        bound?.removeListener(listener)
        p.addListener(listener); bound = p
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val p = com.umutk.pulse.extras.video.VideoSessionService.player
        when (intent?.action) {
            ACT_PREV -> { p?.seekToPreviousMediaItem(); return START_STICKY }
            ACT_NEXT -> { p?.seekToNextMediaItem(); return START_STICKY }
            ACT_TOGGLE -> { if (p?.isPlaying == true) p.pause() else p?.play(); refreshNotification(); return START_STICKY }
        }
        attach()
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        bound?.removeListener(listener); bound = null
        super.onDestroy()
    }

    /** The cover of the video that is playing: the picture the system keeps for it, loaded once per video. */
    private fun loadThumb(key: String) {
        if (key == thumbKey) return
        thumbKey = key; thumb = null
        Thread {
            val bmp = runCatching {
                val uri = android.net.Uri.parse(key)
                if (android.os.Build.VERSION.SDK_INT >= 29) contentResolver.loadThumbnail(uri, android.util.Size(512, 512), null)
                else android.media.MediaMetadataRetriever().let { r -> try { r.setDataSource(this, uri); r.getFrameAtTime(1_000_000) } finally { r.release() } }
            }.getOrNull()
            if (bmp != null && key == thumbKey) { thumb = bmp; android.os.Handler(Looper.getMainLooper()).post { refreshNotification() } }
        }.start()
    }

    override fun notification(): Notification {
        val p = com.umutk.pulse.extras.video.VideoSessionService.player
        val item = p?.currentMediaItem
        val name = item?.mediaMetadata?.title?.toString() ?: p?.mediaMetadata?.title?.toString() ?: title
        item?.mediaId?.takeIf { it.isNotBlank() }?.let { loadThumb(it) }
        val open = PendingIntent.getActivity(this, 0, openIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE)
        fun act(code: Int, action: String) = PendingIntent.getService(this, code, Intent(this, VideoPlaybackService::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val playing = p?.isPlaying == true
        val b = NotificationCompat.Builder(this, CH)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(name)
            .setContentText(if (playing) tr("Playing", "Oynatılıyor") else tr("Paused", "Duraklatıldı"))
            .setOngoing(playing)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_media_previous, tr("Previous", "Önceki"), act(11, ACT_PREV))
            .addAction(if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play, if (playing) tr("Pause", "Duraklat") else tr("Play", "Oynat"), act(12, ACT_TOGGLE))
            .addAction(android.R.drawable.ic_media_next, tr("Next", "Sonraki"), act(13, ACT_NEXT))
        thumb?.let { b.setLargeIcon(it) }
        // the media card: the system draws the cover, the title and the three buttons (also on the lock screen)
        com.umutk.pulse.extras.video.VideoSessionService.session?.let {
            b.setStyle(androidx.media3.session.MediaStyleNotificationHelper.MediaStyle(it).setShowActionsInCompactView(0, 1, 2))
        }
        return b.build()
    }

    companion object {
        const val ACT_PREV = "com.umutk.pulse.action.VIDEO_PREV"
        const val ACT_NEXT = "com.umutk.pulse.action.VIDEO_NEXT"
        const val ACT_TOGGLE = "com.umutk.pulse.action.VIDEO_TOGGLE"
    }
}

class AmbientPlaybackService : MediaService() {
    override val title: String get() = tr("Ambient sound playing", "Ortam sesi çalıyor")
    override val notifId = 9303
    // a tap opens the music player's own sound page; the Stop button ends the mix and the notification
    override fun openIntent(): Intent = Intent().setClassName(packageName, "$packageName.ExtraToolActivity").putExtra("route", "music").putExtra(com.umutk.pulse.extras.music.MusicPlaybackService.EXTRA_OPEN, "noise")
    override val hasStop = true
    override val art: android.graphics.Bitmap? get() = noiseArt
    private val noiseArt by lazy { com.umutk.pulse.extras.music.notif.GeneratedArt.make(com.umutk.pulse.extras.music.notif.GeneratedArt.Kind.NOISE) }
    override fun onStop() { com.umutk.pulse.extras.ambient.AmbientMixer.stopAll() }
    // No onResume: a restart after Android killed the process must never start the last mix by itself.
    // The mix starts only from the page, the Quick panel tile or the widget (AmbientLast.toggle / AmbientMixer.load).
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val r = super.onStartCommand(intent, flags, startId)
        // a restart with no mix playing (the watchdog, a new process) ends here, so no "playing" notification sits over silence
        if (!com.umutk.pulse.extras.ambient.AmbientMixer.isPlaying()) finish()
        return r
    }
}
