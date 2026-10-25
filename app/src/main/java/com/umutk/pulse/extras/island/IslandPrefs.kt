package com.umutk.pulse.extras.island

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** What the island is set to. Geometry is in dp, measured from the top centre of the screen, so it can be set against the phone's own camera hole. */
object IslandPrefs {
    private fun sp(c: Context) = c.getSharedPreferences("pulse_island", Context.MODE_PRIVATE)

    var on by mutableStateOf(false)
    var top by mutableFloatStateOf(6f)          // distance of the pill's top edge from the top of the screen
    var x by mutableFloatStateOf(0f)            // sideways shift from the centre
    var w by mutableFloatStateOf(120f)          // resting width
    var h by mutableFloatStateOf(34f)           // resting height
    var rest by mutableStateOf(true)            // draw the resting pill (it hides behind the camera); off = nothing until there is something to show
    var music by mutableStateOf(true)
    var notif by mutableStateOf(true)
    var charge by mutableStateOf(true)
    var headset by mutableStateOf(true)
    var volume by mutableStateOf(true)
    var torch by mutableStateOf(true)
    var uninstall by mutableStateOf(true)
    var shot by mutableStateOf(true)
    var style by mutableStateOf("black")        // black / accent / art (the colour of the cover)
    var collapse by mutableIntStateOf(6)        // seconds before an opened island closes by itself; 0 = never
    var haptic by mutableStateOf(true)

    fun load(c: Context) {
        val p = sp(c)
        on = p.getBoolean("on", false); top = p.getFloat("top", 6f); x = p.getFloat("x", 0f); w = p.getFloat("w", 120f); h = p.getFloat("h", 34f); rest = p.getBoolean("rest", true)
        music = p.getBoolean("music", true); notif = p.getBoolean("notif", true); charge = p.getBoolean("charge", true); headset = p.getBoolean("headset", true)
        volume = p.getBoolean("volume", true); torch = p.getBoolean("torch", true); uninstall = p.getBoolean("uninstall", true); shot = p.getBoolean("shot", true)
        style = p.getString("style", "black") ?: "black"; collapse = p.getInt("collapse", 6); haptic = p.getBoolean("haptic", true)
    }

    fun save(c: Context) {
        sp(c).edit().putBoolean("on", on).putFloat("top", top).putFloat("x", x).putFloat("w", w).putFloat("h", h).putBoolean("rest", rest)
            .putBoolean("music", music).putBoolean("notif", notif).putBoolean("charge", charge).putBoolean("headset", headset).putBoolean("volume", volume)
            .putBoolean("torch", torch).putBoolean("uninstall", uninstall).putBoolean("shot", shot).putString("style", style).putInt("collapse", collapse).putBoolean("haptic", haptic).apply()
    }

    fun enabled(kind: String) = when (kind) {
        "music" -> music; "notif" -> notif; "charge" -> charge; "headset", "bt" -> headset; "volume" -> volume; "torch" -> torch; "uninstall" -> uninstall; "shot" -> shot; else -> true
    }
}
