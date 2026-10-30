package com.umutk.pulse

import android.app.Application
import android.content.ComponentCallbacks2

/** Frees the picture caches when no Pulse screen is in front. Services (Motion cues, overlays, alerts) are not touched. */
class PulseApp : Application() {
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            com.umutk.pulse.extras.music.trimArtCache()
            com.umutk.pulse.extras.video.trimVideoThumbs()
        }
    }
}
