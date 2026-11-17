package com.umutk.pulse.watch.apps

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.umutk.pulse.watch.ui.W

/** One app of the watch: where it is opened, its name and icon, a line about it, and its page. */
class WatchApp(val route: String, val name: String, val icon: String, val about: String, val screen: @Composable () -> Unit)

object Apps {
    val all = listOf(
        WatchApp("stopwatch", "Stopwatch", "⏱", "Laps, to the hundredth") { StopwatchScreen() },
        WatchApp("timer", "Timer", "⏲", "Turn the crown to set it") { TimerScreen() },
        WatchApp("world", "World clock", "🌍", "Cities and their time") { WorldClockScreen() },
        WatchApp("pomodoro", "Pomodoro", "🍅", "Work 25, rest 5") { PomodoroScreen() },
        WatchApp("heart", "Heart rate", "❤", "From the watch's sensor") { HeartScreen() },
        WatchApp("steps", "Steps", "👣", "Since the watch started") { StepsScreen() },
        WatchApp("breathe", "Breathe", "🌬", "In, hold, out") { BreatheScreen() },
        WatchApp("compass", "Compass", "🧭", "North and the heading") { CompassScreen() },
        WatchApp("battery", "Battery & device", "🔋", "Charge, heat, memory") { BatteryScreen() },
        WatchApp("calc", "Calculator", "🧮", "Round keys") { CalculatorScreen() },
        WatchApp("notes", "Notes", "📝", "Speak or type") { NotesScreen() },
        WatchApp("counter", "Counter", "➕", "Count with the crown") { CounterScreen() },
        WatchApp("dice", "Dice & coin", "🎲", "Roll or flip") { DiceScreen() },
        WatchApp("metronome", "Metronome", "🎵", "Beats on the wrist") { MetronomeScreen() },
        WatchApp("light", "Flashlight", "🔦", "The whole screen white") { FlashlightScreen() },
        WatchApp("settings", "Settings", "⚙", "Accent, crown ticks, clock") { SettingsScreen() },
    )
}

/** What the watch keeps: the settings, the notes, the counter, the cities. */
object Prefs {
    private var p: SharedPreferences? = null
    var crownTicks = true
    val ACCENTS = listOf(0xFF4FC3F7, 0xFF5BD68A, 0xFFFFB74D, 0xFFFF6E91, 0xFFB388FF, 0xFFFFFFFF)

    fun load(c: Context) {
        val s = c.getSharedPreferences("watch", Context.MODE_PRIVATE); p = s
        crownTicks = s.getBoolean("crownTicks", true)
        W.accent = Color(s.getLong("accent", ACCENTS[0]))
    }
    fun setAccent(v: Long) { W.accent = Color(v); p?.edit()?.putLong("accent", v)?.apply() }
    fun setCrownTicks(v: Boolean) { crownTicks = v; p?.edit()?.putBoolean("crownTicks", v)?.apply() }
    fun string(key: String, def: String = ""): String = p?.getString(key, def) ?: def
    fun putString(key: String, v: String) { p?.edit()?.putString(key, v)?.apply() }
    fun int(key: String, def: Int = 0): Int = p?.getInt(key, def) ?: def
    fun putInt(key: String, v: Int) { p?.edit()?.putInt(key, v)?.apply() }
}
