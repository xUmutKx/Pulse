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
    /** "off", "device" (the phone's Material You colour) or "palette" (a colour of the look made in Palette). */
    fun mode(c: Context): String = prefs(c).getString("mode", if (prefs(c).getBoolean("on", false)) "device" else "off") ?: "off"
    fun setMode(c: Context, m: String) { prefs(c).edit().putString("mode", m).apply(); rev++ }
    fun on(c: Context): Boolean = mode(c) != "off"
    fun set(c: Context, v: Boolean) { setMode(c, if (v) "device" else "off") }

    /** The phone's accent colour (light tone on a dark launcher, deep tone on a light one), or null when it is off or the phone has none. */
    fun color(c: Context, dark: Boolean): Color? {
        val m = mode(c)
        if (m == "off") return null
        // a colour of the look saved in Palette: its middle tone (a light one on a dark launcher)
        if (m == "palette") return runCatching { Color(com.umutk.pulse.extras.palette.PaletteStudio.spec(c).palette.colors.let { l -> l[if (dark) (l.size * 3 / 4).coerceAtMost(l.lastIndex) else l.size / 2] }) }.getOrNull()
        if (!available) return null
        return runCatching { Color(c.getColor(if (dark) android.R.color.system_accent1_200 else android.R.color.system_accent1_600)) }.getOrNull()
    }
}
