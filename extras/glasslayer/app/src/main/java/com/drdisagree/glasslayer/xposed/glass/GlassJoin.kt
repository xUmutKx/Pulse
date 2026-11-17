package com.drdisagree.glasslayer.xposed.glass

import android.graphics.Path
import android.graphics.RectF

class GlassJoin {
    val rect = RectF()
    val radii = FloatArray(RADII)
    private val insetRect = RectF()
    private val insetRadii = FloatArray(RADII)

    fun set(source: GlassJoin, dx: Float, dy: Float) {
        rect.set(source.rect)
        rect.offset(dx, dy)
        source.radii.copyInto(radii)
    }

    fun addTo(path: Path, inset: Float = 0f) {
        if (inset == 0f) {
            path.addRoundRect(rect, radii, Path.Direction.CW)
            return
        }
        insetRect.set(rect)
        insetRect.inset(inset, inset)
        if (insetRect.isEmpty) return
        radii.forEachIndexed { index, value -> insetRadii[index] = (value - inset).coerceAtLeast(0f) }
        path.addRoundRect(insetRect, insetRadii, Path.Direction.CW)
    }

    companion object {
        const val RADII = 8
    }
}
