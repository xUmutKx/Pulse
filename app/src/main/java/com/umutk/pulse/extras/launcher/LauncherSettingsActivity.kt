package com.umutk.pulse.extras.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.umutk.pulse.AppLookGuard
import com.umutk.pulse.Cfg
import com.umutk.pulse.LookHost
import com.umutk.pulse.Opt

/** The launcher's settings opened from its own home screen; runs in the launcher process and wears the launcher's look. */
class LauncherSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Cfg.load(this); Opt.load(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            LookHost(lookId = LauncherPrefs.look) { LauncherSettings(back = { finish() }) }
        }
    }

    override fun onStart() { LauncherPrefs.load(this); AppLookGuard.enter({ LauncherPrefs.apply() }, { }); super.onStart() }
    override fun onResume() { LauncherPrefs.load(this); AppLookGuard.enter({ LauncherPrefs.apply() }, { }); super.onResume() }
    override fun onStop() { super.onStop(); AppLookGuard.leave(this) }
}
