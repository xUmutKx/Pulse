package com.umutk.pulse

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** More saved choices: general look, which pages show, the floating monitor and the alerts. */
object Opt {
    var startPage by mutableStateOf("processes")
    var hidden by mutableStateOf(setOf<String>())
    var tempF by mutableStateOf(false)
    var keepOn by mutableStateOf(false)
    var fontScale by mutableFloatStateOf(1f)
    var compact by mutableStateOf(false)

    var ovOn by mutableStateOf(false)         // floating monitor over other apps
    var notifOn by mutableStateOf(false)      // live numbers in a notification
    var ovAlpha by mutableFloatStateOf(.75f)
    var ovSize by mutableFloatStateOf(11f)
    var ovItems by mutableStateOf(setOf("cpu", "ram", "temp", "net"))
    var alertTemp by mutableIntStateOf(0)     // °C, 0 = off
    var alertBat by mutableIntStateOf(0)      // % at or below, 0 = off
    var alertRam by mutableIntStateOf(0)      // % at or above, 0 = off

    private fun sp(c: Context) = c.getSharedPreferences("p", Context.MODE_PRIVATE)

    fun load(c: Context) {
        val p = sp(c)
        startPage = p.getString("start", "processes") ?: "processes"
        hidden = p.getStringSet("hidden", emptySet()) ?: emptySet()
        tempF = p.getBoolean("tempF", false); keepOn = p.getBoolean("keepOn", false)
        fontScale = p.getFloat("fontScale", 1f); compact = p.getBoolean("compact", false)
        ovOn = p.getBoolean("ovOn", false); notifOn = p.getBoolean("notifOn", false)
        ovAlpha = p.getFloat("ovAlpha", .75f); ovSize = p.getFloat("ovSize", 11f)
        ovItems = p.getStringSet("ovItems", setOf("cpu", "ram", "temp", "net")) ?: emptySet()
        alertTemp = p.getInt("alertTemp", 0); alertBat = p.getInt("alertBat", 0); alertRam = p.getInt("alertRam", 0)
    }

    fun save(c: Context) {
        sp(c).edit().putString("start", startPage).putStringSet("hidden", hidden).putBoolean("tempF", tempF).putBoolean("keepOn", keepOn)
            .putFloat("fontScale", fontScale).putBoolean("compact", compact).putBoolean("ovOn", ovOn).putBoolean("notifOn", notifOn)
            .putFloat("ovAlpha", ovAlpha).putFloat("ovSize", ovSize).putStringSet("ovItems", ovItems)
            .putInt("alertTemp", alertTemp).putInt("alertBat", alertBat).putInt("alertRam", alertRam).apply()
    }

    /** A temperature in the chosen unit. */
    fun temp(c: Float) = if (tempF) "%.1f°F".format(c * 9f / 5f + 32f) else "%.1f°C".format(c)
}
