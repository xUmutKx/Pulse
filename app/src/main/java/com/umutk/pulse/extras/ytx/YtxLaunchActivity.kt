package com.umutk.pulse.extras.ytx

import android.app.Activity
import android.os.Bundle

/** The home-screen icon of YTXTube inside Pulse: it installs the client once, opens it after that, and then takes its own icon away (the client has an icon of its own). */
class YtxLaunchActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BundledApks.launchFromIcon(this, BundledApks.ytx, YtxLaunchActivity::class.java)
    }
}

/** The same for Athenaeum (tarot, astrology, spirits and magic, offline). */
class AthenaLaunchActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BundledApks.launchFromIcon(this, BundledApks.athenaeum, AthenaLaunchActivity::class.java)
    }
}
