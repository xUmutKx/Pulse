package com.umutk.pulse.extras.lucid.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The look of the alarm app. One UI is the default (the app's own look); Material is the flat dark surface; AMOLED is pure black.
 */
enum class LucidLook(val label: String) {
    ONE_UI("One UI"),
    MATERIAL("Material"),
    AMOLED("AMOLED black");
}

/** The chosen look, as Compose state: every screen that reads it redraws when it changes. Kept in the app's own preferences. */
object LucidLookState {
    private const val PREFS = "lucid_look"

    var current by mutableStateOf(LucidLook.ONE_UI)
        private set

    /** Reads the saved look; call once when the alarm app starts. */
    fun load(c: Context) {
        current = runCatching {
            LucidLook.valueOf(c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("look", LucidLook.ONE_UI.name) ?: LucidLook.ONE_UI.name)
        }.getOrDefault(LucidLook.ONE_UI)
    }

    fun set(c: Context, look: LucidLook) {
        current = look
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("look", look.name).apply()
    }
}
