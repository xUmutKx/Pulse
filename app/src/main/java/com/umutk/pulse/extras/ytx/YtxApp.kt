package com.umutk.pulse.extras.ytx

import android.content.Context

/** YTXTube, the YouTube and YouTube Music client (GPL v3, github.com/Koustubh12345/YTXTube; its source is in extras/ytxtube, without Shorts and without its own updater). See [BundledApks]. */
object YtxApp {
    fun bundled(c: Context) = BundledApks.bundled(c, BundledApks.ytx)
    fun installed(c: Context) = BundledApks.installed(c, BundledApks.ytx)
    fun openOrInstall(c: Context) = BundledApks.openOrInstall(c, BundledApks.ytx)
}
