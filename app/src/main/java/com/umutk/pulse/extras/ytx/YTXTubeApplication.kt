package com.umutk.pulse.extras.ytx

import android.content.Context
import com.umutk.pulse.extras.ytx.potoken.PoTokenGenerator
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor

/** Version of the YTXTube code this extra app is built from (GPL-3.0, github.com/Koustubh12345/YTXTube). */
const val YTX_VERSION = "2.2.0"

/**
 * The start-up of the YouTube extra app (was the Application class of YTXTube): Pulse has one Application, so the activity and the playback service call [init],
 * which runs once per process.
 */
object YTXTubeApplication {
    lateinit var appContext: Context
        private set
    @Volatile private var done = false

    @Synchronized
    fun init(ctx: Context) {
        if (done) return
        done = true
        val app = ctx.applicationContext
        appContext = app
        onCreate(app)
    }

    private fun onCreate(app: Context) {
        val self = app
        TvMode.init(self)
        LanguageSettings.init(self)
        GlassQuality.init(self)
        GlassSettings.init(self)
        NetworkSpeed.init(self)
        PlaybackBandwidth.init(self)
        NewPipe.init(NewPipeDownloader.instance)


        val poTokenGenerator = PoTokenGenerator()
        poTokenGenerator.initialize()
        YoutubeStreamExtractor.setPoTokenProvider(YtxPoTokenProvider(poTokenGenerator))


        YoutubeStreamExtractor.setFetchIosClient(true)
        HdrItags.install()
        ThemeSettings.init(self)
        HdrSupport.init(self)
        QualityPreference.init(self)
        PlaybackSpeed.init(self)
        AudioOnly.init(self)
        AudioTrackPreference.init(self)
        CaptionPreference.init(self)
        VideoGestureSettings.init(self)
        PlayerControlSettings.init(self)
        PipSettings.init(self)
        MiniPlayerSettings.init(self)
        ResumeStore.init(self)
        Account.init(self)
        SponsorBlock.init(self)
        SearchHistory.init(self)
        Downloads.init(self)

        self.getSystemService(android.net.ConnectivityManager::class.java)?.registerDefaultNetworkCallback(
            object : android.net.ConnectivityManager.NetworkCallback() {
                private var last: android.net.Network? = null
                override fun onAvailable(network: android.net.Network) {
                    if (last != null && last != network) StreamCache.invalidateAll()
                    last = network
                }
            },
        )
    }
}
