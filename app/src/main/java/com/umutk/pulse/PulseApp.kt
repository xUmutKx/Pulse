package com.umutk.pulse

import android.app.Application
import android.content.ComponentCallbacks2
import com.umutk.pulse.extras.lyrics.LyricsMigrate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Frees the picture caches when no Pulse screen is in front. Services (Motion cues, overlays, alerts) are not touched. */
class PulseApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // the lyrics kept in the app's data and cache move beside their songs, once, in the background
        CoroutineScope(Dispatchers.IO).launch { LyricsMigrate.run(this@PulseApp) }
        // the ads start (play build) and Premium is checked with Google Play, so a reinstall gets it back
        com.umutk.pulse.ads.PulseAds.init(this)
        com.umutk.pulse.ads.PulseAds.restorePremium(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            com.umutk.pulse.extras.music.trimArtCache()
            com.umutk.pulse.extras.video.trimVideoThumbs()
        }
    }
}
