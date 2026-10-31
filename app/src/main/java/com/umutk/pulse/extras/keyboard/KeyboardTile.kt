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
 * Quick settings tile: opens the Pulse keyboard as the small floating keyboard. A keyboard only shows for a text field, so the
 * tile starts [KeyboardFloatActivity], a transparent window with a focused field; that field brings the floating keyboard up.
 */
@androidx.annotation.RequiresApi(24)
class KeyboardTile : TileService() {
    override fun onStartListening() {
        qsTile?.apply { state = Tile.STATE_INACTIVE; updateTile() }
    }

    override fun onClick() {
        KeyboardPrefs.load(this)
        KeyboardPrefs.floating = true
        KeyboardPrefs.save(this)
        val i = Intent(this, KeyboardFloatActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(android.app.PendingIntent.getActivity(this, 0, i, android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(i)
        }
    }
}

/** A transparent window with one focused field: the keyboard comes up over it (as the floating keyboard) and the window closes when it is put away. */
class KeyboardFloatActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setLayout(1, 1)
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
