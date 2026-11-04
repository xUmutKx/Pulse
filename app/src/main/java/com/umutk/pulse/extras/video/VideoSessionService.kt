package com.umutk.pulse.extras.video

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Hands the video player to the system's media controls (notification, lock screen, the Quick settings media card, headphone
 * buttons). Without it those controls showed the music player's session, so Play/Pause moved the song instead of the video.
 * The video screen sets [player] and starts this service while it is open, and stops it on leave.
 */
class VideoSessionService : MediaSessionService() {

    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val p: Player = player ?: run { stopSelf(); return }
        val open = PendingIntent.getActivity(
            this, 0, packageManager.getLaunchIntentForPackage(packageName) ?: Intent(),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, p).setId("pulse_video").setSessionActivity(open).build()
        current = session
    }

    // Pulse's own video notification (VideoPlaybackService: cover, previous, play or pause, next) is the only one; the default one would repeat it
    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) { }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        current = null
        session?.release()
        session = null
        super.onDestroy()
    }

    companion object {
        /** The player of the video screen that is open now; null when no video is on screen. */
        @Volatile var player: Player? = null

        /** The media session of that player, for the notification's media card. */
        @Volatile var current: MediaSession? = null
        val session: MediaSession? get() = current
    }
}
