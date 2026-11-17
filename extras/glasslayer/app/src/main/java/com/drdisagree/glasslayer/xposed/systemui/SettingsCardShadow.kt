package com.drdisagree.glasslayer.xposed.systemui

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import kotlin.math.roundToInt

internal object SettingsCardShadow {

    private const val BLUR_DP = 10f
    private const val OFFSET_DP = 3f
    private const val SEAM_RADIUS_DP = 8f
    private const val MAX_GAP_DP = 12f
    private const val BASE_ALPHA = 0.2f
    private const val RADII = 8

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPath = Path()
    private val clipPath = Path()
    private val groupRect = RectF()
    private val cardRect = RectF()
    private val groupRadii = FloatArray(RADII)
    private val cardRadii = FloatArray(RADII)
    private var blurFor = -1f

    fun draw(parent: ViewGroup, canvas: Canvas, isCard: (View) -> Boolean, strength: Float) {
        if (strength <= 0f || parent.childCount == 0) return
        val density = parent.resources.displayMetrics.density
        val blur = BLUR_DP * density
        if (blurFor != blur) {
            paint.maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
            blurFor = blur
        }
        paint.color = Color.argb((BASE_ALPHA * strength).coerceIn(0f, 1f).times(255).roundToInt(), 0, 0, 0)
        val seam = SEAM_RADIUS_DP * density
        val maxGap = MAX_GAP_DP * density
        val offset = OFFSET_DP * density
        shadowPath.rewind()
        clipPath.rewind()
        var open = false
        val children = (0 until parent.childCount).map { parent.getChildAt(it) }
            .filter { it.visibility == View.VISIBLE && isCard(it) }
            .sortedBy { it.top + it.translationY }
        for (child in children) {
            val shape = SolidShapes.of(child.background) ?: continue
            if (!cardBounds(child, shape)) continue
            val openTop = maxOf(cardRadii[0], cardRadii[2]) <= seam
            val openBottom = maxOf(cardRadii[4], cardRadii[6]) <= seam
            if (open && openTop && cardRect.top - groupRect.bottom <= maxGap) {
                groupRect.bottom = cardRect.bottom
                for (index in 4 until RADII) groupRadii[index] = cardRadii[index]
            } else {
                if (open) closeGroup()
                groupRect.set(cardRect)
                cardRadii.copyInto(groupRadii)
                open = true
            }
            if (!openBottom) {
                closeGroup()
                open = false
            }
        }
        if (open) closeGroup()
        if (shadowPath.isEmpty) return
        val count = canvas.save()
        try {
            canvas.clipOutPath(clipPath)
            canvas.translate(0f, offset)
            canvas.drawPath(shadowPath, paint)
        } finally {
            canvas.restoreToCount(count)
        }
    }

    private fun closeGroup() {
        shadowPath.addRoundRect(groupRect, groupRadii, Path.Direction.CW)
        clipPath.addRoundRect(groupRect, groupRadii, Path.Direction.CW)
    }

    private fun cardBounds(child: View, shape: GradientDrawable): Boolean {
        val bounds = shape.bounds
        if (bounds.isEmpty) return false
        val left = child.left + child.translationX
        val top = child.top + child.translationY
        cardRect.set(left + bounds.left, top + bounds.top, left + bounds.right, top + bounds.bottom)
        val max = minOf(cardRect.width(), cardRect.height()) / 2f
        val radii = shape.cornerRadii
        if (radii != null && radii.size == RADII) {
            for (index in 0 until RADII) cardRadii[index] = radii[index].coerceAtMost(max)
        } else {
            cardRadii.fill(shape.cornerRadius.coerceAtMost(max))
        }
        return true
    }
}
