package com.umutk.pulse.extras.launcher

import android.content.Context
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/** The Material launcher looks can take their accent from the phone's own colours (Material You, Android 12 and later). Off by default: the accent stays as it is. */
object DeviceAccent {
    var rev by mutableIntStateOf(0)
    private fun prefs(c: Context) = c.getSharedPreferences("pulse_device_accent", Context.MODE_PRIVATE)
    val available: Boolean get() = Build.VERSION.SDK_INT >= 31
    fun on(c: Context): Boolean = available && prefs(c).getBoolean("on", false)
    fun set(c: Context, v: Boolean) { prefs(c).edit().putBoolean("on", v).apply(); rev++ }

    /** The phone's accent colour (light tone on a dark launcher, deep tone on a light one), or null when it is off or the phone has none. */
    fun color(c: Context, dark: Boolean): Color? {
        if (!on(c)) return null
        return runCatching { Color(c.getColor(if (dark) android.R.color.system_accent1_200 else android.R.color.system_accent1_600)) }.getOrNull()
    }
}
