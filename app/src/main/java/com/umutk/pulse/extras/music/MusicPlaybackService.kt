package com.umutk.pulse.extras.music

import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Keeps playback alive when the UI is gone: Media3 runs the foreground service and the media notification
 * while the player plays. Needs the manifest entry (foregroundServiceType="mediaPlayback") in AndroidManifest.xml.
 */
class MusicPlaybackService : MediaSessionService() {

    private var session: MediaSession? = null

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        session ?: MediaSession.Builder(this, MusicPlayerEngine.get(this).player).build().also { session = it }

    override fun onDestroy() {
        session?.release()
        session = null
        super.onDestroy()
    }
}
