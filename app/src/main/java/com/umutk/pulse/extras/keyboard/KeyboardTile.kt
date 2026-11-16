package com.umutk.pulse.extras.keyboard

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Bundle
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText

/**
 * Quick settings tile: the floating keyboard on or off. It opens no window of its own any more: a hidden window over the app took the app's place in front
 * (the app stood still behind it and what was typed went into the hidden window). Now the tile only switches the floating look; the keyboard floats over the
 * app's own text box, the app keeps running and takes the typing, and everything outside the keys is the app's. The keyboard follows at once when it is up.
 */
@androidx.annotation.RequiresApi(24)
class KeyboardTile : TileService() {
    override fun onStartListening() {
        qsTile?.apply { state = if (tileActive(this@KeyboardTile)) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE; updateTile() }
    }

    override fun onClick() {
        KeyboardPrefs.load(this)
        val on = !tileActive(this)
        tilePrefs(this).edit().putBoolean("active", on).apply()
        KeyboardPrefs.floatSession = on
        qsTile?.apply { state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE; updateTile() }
        // a keyboard that is up changes its look now
        PulseKeyboardService.live?.floatChanged()
    }
}

/** The tile's floating keyboard is on (kept until the tile is tapped again). */
internal fun floatTileOn(c: Context) = tileActive(c)

private fun tilePrefs(c: Context) = c.getSharedPreferences("pulse_kb_tile", Context.MODE_PRIVATE)
private fun tileActive(c: Context) = tilePrefs(c).getBoolean("active", false)

/** The tile is off again (the window closed). */
private fun tileOff(c: Context) = tilePrefs(c).edit().putBoolean("active", false).apply()

/** A transparent window with one focused field: the keyboard comes up over it (as the floating keyboard) and the window closes when it is put away. */
class KeyboardFloatActivity : Activity() {
    companion object { @Volatile var current: KeyboardFloatActivity? = null }

    override fun onDestroy() {
        super.onDestroy()
        if (current === this) current = null
        // however the window closes (tile, back, the field left), the tile is off again and the floating keyboard is put away
        tileOff(this)
        KeyboardPrefs.floatSession = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        current = this
        // only the keyboard shows: the window is 1 pixel, transparent, with no dimming behind it, and touches outside it reach the app
        window.setLayout(1, 1)
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
        val field = EditText(this).apply { alpha = 0f; isFocusableInTouchMode = true }
        setContentView(field)
        field.requestFocus()
        // the keyboard comes up once this window has focus (asked before that, the system ignores it and the window stays empty)
        shownFor = field
    }

    private var shownFor: EditText? = null
    private var asked = false

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        val field = shownFor ?: return
        if (!hasFocus || asked) return
        asked = true
        field.post {
            field.requestFocus()
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(field, InputMethodManager.SHOW_FORCED)
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }
}
