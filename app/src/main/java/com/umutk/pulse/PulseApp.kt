package com.umutk.pulse

import android.app.Application
import android.content.ComponentCallbacks2
import com.umutk.pulse.extras.lyrics.LyricsMigrate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Frees the picture caches when no Pulse screen is in front. Services (Motion cues, overlays, alerts) are not touched. */
class PulseApp : Application(), androidx.work.Configuration.Provider {
    // WorkManager starts itself, in every process of the app, from this configuration: the launcher, Files and the other windows run in their own processes, where the
    // start-up provider did not start it, and the first use of it (the music player's lyrics work, for one) crashed with "WorkManager is not initialized properly"
    override val workManagerConfiguration: androidx.work.Configuration
        get() = androidx.work.Configuration.Builder().build()

    override fun onCreate() {
        super.onCreate()
        // a crash is written to Download/Pulse/crash.txt, so it can be read on the phone
        CrashSaver.install(this)
        // the lyrics kept in the app's data and cache move beside their songs, once, in the background
        CoroutineScope(Dispatchers.IO).launch { LyricsMigrate.run(this@PulseApp) }
        // the ads start (play build) and Premium is checked with Google Play, so a reinstall gets it back
        com.umutk.pulse.ads.PulseAds.init(this)
        com.umutk.pulse.ads.PulseAds.restorePremium(this)
        // the main process only: the list of the App manager is read in the background, so that page opens already filled
        if (runCatching { Application.getProcessName() == packageName }.getOrDefault(false)) CoroutineScope(Dispatchers.IO).launch {
            kotlinx.coroutines.delay(2500)
            runCatching { com.umutk.pulse.features.prewarmAppManager(this@PulseApp) }
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            com.umutk.pulse.extras.music.trimArtCache()
            com.umutk.pulse.extras.video.trimVideoThumbs()
        }
    }
}
