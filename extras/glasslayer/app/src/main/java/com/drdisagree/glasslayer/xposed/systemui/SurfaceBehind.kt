package com.drdisagree.glasslayer.xposed.systemui

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.SystemClock
import android.view.View
import java.util.WeakHashMap
import kotlin.math.roundToInt

internal object SurfaceBehind {

    private const val CACHE_MS = 1000L
    private const val MIN_ALPHA = 32
    private const val OPAQUE = 255

    private val cache = WeakHashMap<View, Long>()

    fun color(view: View?): Int {
        view ?: return Color.TRANSPARENT
        val now = SystemClock.uptimeMillis()
        val cached = cache[view]
        if (cached != null && now - (cached ushr 32) < CACHE_MS) return (cached and LOW_BITS).toInt()
        val color = resolve(view)
        cache[view] = (now shl 32) or (color.toLong() and LOW_BITS)
        return color
    }

    private fun resolve(view: View): Int {
        var current: Any? = view.parent
        while (current is View) {
            val background = current.background
            val color = (background as? ColorDrawable)?.color ?: SolidShapes.of(background)?.color?.defaultColor
            if (color != null && background != null) {
                val alpha = (Color.alpha(color) * background.alpha / OPAQUE.toFloat() * current.alpha).roundToInt().coerceIn(0, OPAQUE)
                if (alpha >= MIN_ALPHA) return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
            }
            current = current.parent
        }
        return Color.TRANSPARENT
    }

    private const val LOW_BITS = 0xFFFFFFFFL
}
