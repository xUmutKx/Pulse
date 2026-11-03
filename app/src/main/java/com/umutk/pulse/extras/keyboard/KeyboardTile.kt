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
 * Quick settings tile: opens the Pulse keyboard as the floating keyboard, straight away. A keyboard only shows for a text field, so the
 * tile starts [KeyboardFloatActivity], a transparent window with a focused field; that field brings the floating keyboard up.
 */
@androidx.annotation.RequiresApi(24)
class KeyboardTile : TileService() {
    override fun onStartListening() {
        qsTile?.apply { state = if (tileActive(this@KeyboardTile)) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE; updateTile() }
    }

    override fun onClick() {
        KeyboardPrefs.load(this)
        // a second tap puts the keyboard away
        if (tileActive(this)) {
            KeyboardFloatActivity.current?.finish() ?: tileOff(this)
            qsTile?.apply { state = Tile.STATE_INACTIVE; updateTile() }
            return
        }
        // one step: the tile opens the floating keyboard at once (no button on the screen's side first). It lasts while its window is open and is never saved
        // as the keyboard's mode, so the normal keyboard stays the normal keyboard
        KeyboardPrefs.floatSession = true
        tilePrefs(this).edit().putBoolean("active", true).apply()
        qsTile?.apply { state = Tile.STATE_ACTIVE; updateTile() }
        val i = com.umutk.pulse.ads.TileAds.wrap(this, Intent(this, KeyboardFloatActivity::class.java)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(android.app.PendingIntent.getActivity(this, 0, i, android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(i)
        }
    }
}

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
        window.decorView.post {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(field, InputMethodManager.SHOW_FORCED)
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }
}
