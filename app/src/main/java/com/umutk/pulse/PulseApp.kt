package com.umutk.pulse

import android.app.Application
import android.content.ComponentCallbacks2
import com.umutk.pulse.extras.lyrics.LyricsMigrate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Frees the picture caches when no Pulse screen is in front. Services (Motion cues, overlays, alerts) are not touched. */
class PulseApp : Application(), androidx.work.Configuration.Provider, coil.ImageLoaderFactory {
    /**
     * Every picture of the app (covers, artist photos, thumbnails of YTXTube and YouTube Music) is fetched once and kept on the disk until the cache is cleared,
     * whatever the server says about keeping it (its headers asked for a new download each time, so an artist page loaded its pictures again on every visit).
     * Each process keeps its own folder (the disk cache cannot be shared between processes).
     */
    override fun newImageLoader(): coil.ImageLoader {
        val proc = runCatching { if (android.os.Build.VERSION.SDK_INT >= 28) getProcessName() else packageName }.getOrDefault(packageName).substringAfter(':', "main")
        return coil.ImageLoader.Builder(this)
            .respectCacheHeaders(false)
            .memoryCache { coil.memory.MemoryCache.Builder(this).maxSizePercent(0.15).build() }
            .diskCache { coil.disk.DiskCache.Builder().directory(cacheDir.resolve("images_$proc")).maxSizeBytes(300L * 1024 * 1024).build() }
            .crossfade(true)
            .build()
    }

    // WorkManager starts itself, in every process of the app, from this configuration: the launcher, Files and the other windows run in their own processes, where the
    // start-up provider did not start it, and the first use of it (the music player's lyrics work, for one) crashed with "WorkManager is not initialized properly"
    override val workManagerConfiguration: androidx.work.Configuration
        get() = androidx.work.Configuration.Builder().build()

    override fun onCreate() {
        super.onCreate()
        // before the first unlock only the keyboard runs (its data comes from the device storage, see PulseKeyboardService): the rest waits for the unlock
        if (android.os.Build.VERSION.SDK_INT >= 24 && runCatching { !(getSystemService(USER_SERVICE) as android.os.UserManager).isUserUnlocked }.getOrDefault(false)) return
        // Android lets only one process use a WebView data folder: a second process that opens a WebView while another holds it crashes ("Using WebView from more than one
        // process at once", seen when Game Hub opened while YTXTube ran). Every extra process gets a data folder of its own; the main process keeps the default one.
        if (android.os.Build.VERSION.SDK_INT >= 28) runCatching {
            val pn = Application.getProcessName()
            if (pn != packageName) android.webkit.WebView.setDataDirectorySuffix(pn.substringAfter(':').ifBlank { "x" })
        }
        // a crash is written to Download/Pulse/crash.txt, so it can be read on the phone
        CrashSaver.install(this)
        // once for each version, in the main process: the notification channels an older version made with the icon badge on are removed, and made again (without it) by the code that posts to them:
        // Android does not let an app turn the badge off of a channel that exists, and every extra app of Pulse wore the dot. (Alarm channels and a channel that holds a running
        // foreground service are left alone.)
        if (android.os.Build.VERSION.SDK_INT >= 26 && Application.getProcessName() == packageName) runCatching {
            val sp = getSharedPreferences("p", MODE_PRIVATE)
            if (sp.getInt("badge_sweep", 0) < BuildConfig.VERSION_CODE) {
                val nm = getSystemService(android.app.NotificationManager::class.java)
                nm.notificationChannels.filter { it.canShowBadge() && listOf("alarm", "lucid", "awake", "snooze", "timer", "ring", "nowbar", "miscellaneous").none { k -> it.id.contains(k, true) } }
                    .forEach { runCatching { nm.deleteNotificationChannel(it.id) } }
                sp.edit().putInt("badge_sweep", BuildConfig.VERSION_CODE).apply()
            }
        }
        // the performance record (a setting, see PerfLog): in every process
        PerfLog.init(this)
        // an own Private DNS for Wi-Fi and for mobile data (a setting): watched in the main process only
        if (runCatching { Application.getProcessName() == packageName }.getOrDefault(true)) runCatching { com.umutk.pulse.features.DnsPerNet.start(this) }
        // accessibility services Android marked "Not working" are switched off and on again (with root, Shizuku or the secure-settings grant), and are on by default
        if (runCatching { Application.getProcessName() == packageName }.getOrDefault(true)) runCatching { com.umutk.pulse.features.A11yRepair.schedule(this) }
        // the lyrics kept in the app's data and cache move beside their songs, once, in the background
        CoroutineScope(Dispatchers.IO).launch { LyricsMigrate.run(this@PulseApp) }
        // the folders Pulse made in Downloads before are moved under Download/Pulse (once the storage permission is there; nothing happens without it)
        if (android.os.Build.VERSION.SDK_INT < 28 || getProcessName() == packageName) CoroutineScope(Dispatchers.IO).launch { runCatching { PulseDirs.migrateAll() } }
        // the small packs (icons of the older looks, more wallpapers, the Office Assistants: one download of about 2 MB) come once by themselves, from the main process only
        if (android.os.Build.VERSION.SDK_INT < 28 || getProcessName() == packageName) runCatching { com.umutk.pulse.extras.store.PackAuto.firstRun(this) }
        // the ads start (play build) and Premium is checked with Google Play, so a reinstall gets it back
        com.umutk.pulse.ads.PulseAds.init(this)
        com.umutk.pulse.ads.PulseAds.restorePremium(this)
        // nearby sharing stays visible while Pulse runs (the keyboard keeps this process alive), so a phone whose LocalSend page is closed is still found
        if (runCatching { Application.getProcessName() == packageName }.getOrDefault(false)) runCatching { com.umutk.pulse.extras.localsend.LocalSendEngine.keepAlive(this) }
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
