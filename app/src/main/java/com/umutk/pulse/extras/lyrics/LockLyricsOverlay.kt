package com.umutk.pulse.extras.lyrics

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.umutk.pulse.Compat
import com.umutk.pulse.extras.music.MusicSettings
import com.umutk.pulse.features.CoverAccessibilityService

/**
 * The lyrics on the lock screen: the line being sung and the next one in a small window of plain views, with a soft dark
 * backdrop so white letters stay readable over a bright wallpaper. It is a window, not an activity: the lock screen keeps
 * its clock, its notifications and the unlock swipe (every touch goes through), and nothing freezes when the screen turns
 * off, because the line is set from the lyrics ticker and not from a screen that has to be drawn first.
 *
 * With the accessibility layer on (the Cover service) the window is drawn above the lock screen itself; without it the
 * normal overlay window is used, which Android may hide behind the lock screen on some phones.
 */
object LockLyricsOverlay {
    private var box: LinearLayout? = null
    private var now: TextView? = null
    private var next: TextView? = null
    private var host: Context? = null

    val showing get() = box != null

    /** Shows the window (or updates its two lines). Returns false when no window can be drawn (no accessibility layer, no overlay permission). */
    fun show(ctx: Context, current: String, upcoming: String?): Boolean {
        val app = ctx.applicationContext
        if (box == null && !add(app)) return false
        now?.text = current
        val n = upcoming?.takeIf { it.isNotBlank() }
        next?.text = n.orEmpty()
        next?.visibility = if (n != null) View.VISIBLE else View.GONE
        return true
    }

    private fun add(app: Context): Boolean {
        val acc = CoverAccessibilityService.instance
        if (acc == null && !Compat.canOverlay(app)) return false
        val base: Context = acc ?: app
        val wm = base.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val d = base.resources.displayMetrics
        val type = if (acc != null) WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY else WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        val lp = WindowManager.LayoutParams(
            (d.widthPixels * 0.86f).toInt(), WindowManager.LayoutParams.WRAP_CONTENT, type,
            // never takes the keys or a touch: the lock screen under it works as if it was not there
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            // its lower edge sits at the wallpaper's peak: a little over a quarter of the screen up from the bottom
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = (d.heightPixels * 0.27f).toInt()
        }
        // the size is the lyrics size of the music player (its setting), a little under what the player draws
        val size = MusicSettings.lyricsFont(app).coerceIn(12, 30).toFloat()
        val pad = (12 * d.density).toInt()
        val l = LinearLayout(base).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply { setColor(0x73000000); cornerRadius = 18 * d.density }
        }
        val a = TextView(base).apply {
            setTextColor(Color.WHITE); textSize = size; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; maxLines = 3
            setShadowLayer(6f, 0f, 2f, 0xE6000000.toInt())
        }
        val b = TextView(base).apply {
            setTextColor(0x88FFFFFF.toInt()); textSize = size - 4f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; maxLines = 2
            setShadowLayer(6f, 0f, 2f, 0xE6000000.toInt())
            setPadding(0, (4 * d.density).toInt(), 0, 0)
        }
        l.addView(a, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        l.addView(b, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        if (runCatching { wm.addView(l, lp) }.isFailure) return false
        box = l; now = a; next = b; host = base
        return true
    }

    fun hide(ctx: Context) {
        val v = box ?: return
        runCatching { ((host ?: ctx.applicationContext).getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(v) }
        box = null; now = null; next = null; host = null
    }
}
