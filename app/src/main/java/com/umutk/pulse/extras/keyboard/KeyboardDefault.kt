package com.umutk.pulse.extras.keyboard

import android.content.Context
import android.provider.Settings
import com.umutk.pulse.extras.blackout.Privilege

/**
 * Keeps Pulse Keyboard the default keyboard. When Pulse is force-stopped, Android falls back to another keyboard (Samsung's); when Pulse opens
 * again, it is set back through the root or Shizuku shell (`ime enable` and `ime set`). Without root or Shizuku nothing can be set, and the
 * keyboard is chosen again in the system picker. Blocking shell calls: run it off the main thread.
 */
object KeyboardDefault {
    private const val PREFS = "keyboard_default"

    fun keep(c: Context): Boolean = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("keep", true)
    fun setKeep(c: Context, v: Boolean) { c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("keep", v).apply() }

    private fun id(c: Context): String = c.packageName + "/com.umutk.pulse.extras.keyboard.PulseKeyboardService"

    /** Sets Pulse Keyboard back as the default, when it is kept, root or Shizuku is there, and another keyboard is the default now. */
    fun restore(c: Context) {
        if (!keep(c)) return
        if (Privilege.detect() == Privilege.Mode.None) return
        val current = Settings.Secure.getString(c.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        if (current == id(c)) return
        Privilege.run("ime enable ${id(c)}; ime set ${id(c)}")
    }
}
