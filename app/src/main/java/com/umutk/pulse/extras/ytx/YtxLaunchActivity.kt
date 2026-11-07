package com.umutk.pulse.extras.ytx

import android.app.Activity
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Bundle

/** The home-screen icon of YTXTube inside Pulse: it installs the client once, opens it after that, and then takes its own icon away (the client has an icon of its own). */
class YtxLaunchActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val was = YtxApp.installed(this)
        YtxApp.openOrInstall(this)
        // the client is there and was opened: this entry is not needed any more
        if (was) runCatching { packageManager.setComponentEnabledSetting(ComponentName(this, YtxLaunchActivity::class.java), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP) }
        finish()
    }
}
