package com.drdisagree.glasslayer.xposed.systemui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RecordingCanvas
import android.graphics.RenderNode
import androidx.core.graphics.ColorUtils
import com.drdisagree.glasslayer.xposed.glass.LiquidGlassRenderer
import com.drdisagree.glasslayer.xposed.glass.LiquidGlassStyle
import kotlin.math.abs

internal class KyantThumb {

    private val source = RenderNode(SOURCE_NODE)
    private val lens = LiquidGlassRenderer()
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val lensPath = Path()
    private val lensMatrix = Matrix()
    private val radii = FloatArray(RADII)
    private var sourceLeft = 0f
    private var sourceTop = 0f

    fun drawToggle(
        canvas: Canvas,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        position: Float,
        press: Float,
        velocity: Float,
        density: Float,
        trackColor: Int,
        surfaceColor: Int,
        alpha: Float,
        boundsStart: Float = Float.NEGATIVE_INFINITY,
        boundsEnd: Float = Float.POSITIVE_INFINITY,
        boundsTop: Float = Float.NEGATIVE_INFINITY,
        boundsBottom: Float = Float.POSITIVE_INFINITY
    ) {
        if (width <= 0f || height <= 0f) return
        val margin = height
        record(-margin, -margin, width + margin * 2f, height + margin * 2f) { recording ->
            fillPaint.color = ColorUtils.setAlphaComponent(trackColor, OPAQUE)
            recording.drawRoundRect(margin, margin, margin + width, margin + height, height / 2f, height / 2f, fillPaint)
        }
        val padding = PADDING_DP * density
        val thumbHeight = height - padding * 2f
        val thumbWidth = minOf(thumbHeight * RATIO, width - padding * 2f - thumbHeight * MIN_TRAVEL)
        val travel = width - padding * 2f - thumbWidth
        val centerX = padding + position.coerceIn(0f, 1f) * travel + thumbWidth / 2f
        drawCapsule(
            canvas, left, top, centerX, height / 2f, thumbWidth, thumbHeight, press, velocity, true, alpha,
            lensScaleX = TOGGLE_LENS_X_START + (TOGGLE_LENS_X_END - TOGGLE_LENS_X_START) * press,
            lensScaleY = TOGGLE_LENS_Y_END * press,
            boundsStart = boundsStart - left, boundsEnd = boundsEnd - left,
            boundsTop = boundsTop - top, boundsBottom = boundsBottom - top
        )
    }

    fun drawSlider(
        canvas: Canvas,
        boundsStart: Float,
        boundsEnd: Float,
        centerY: Float,
        fraction: Float,
        rtl: Boolean,
        press: Float,
        velocity: Float,
        density: Float,
        activeColor: Int,
        inactiveColor: Int,
        surfaceColor: Int,
        alpha: Float,
        ticks: Int = 0,
        tickActiveColor: Int = inactiveColor,
        tickInactiveColor: Int = activeColor,
        boundsTop: Float = Float.NEGATIVE_INFINITY,
        boundsBottom: Float = Float.POSITIVE_INFINITY
    ) {
        val capsuleWidth = SLIDER_LONG_DP * density
        val capsuleHeight = SLIDER_SHORT_DP * density
        val reach = capsuleWidth / 2f * (1f + SLIDER_PRESS_SCALE) * (1f + SLIDER_MAX_STRETCH)
        val centerMin = boundsStart + reach
        val centerMax = boundsEnd - reach
        if (centerMax <= centerMin) return
        val trackStart = centerMin - capsuleWidth / 4f
        val trackEnd = centerMax + capsuleWidth / 4f
        val progress = fraction.coerceIn(0f, 1f)
        val centerX = centerMin + (if (rtl) 1f - progress else progress) * (centerMax - centerMin)
        val thickness = TRACK_DP * density
        val radius = thickness / 2f
        val top = centerY - radius
        fillPaint.color = inactiveColor
        fillPaint.alpha = (Color.alpha(inactiveColor) * alpha).toInt()
        canvas.drawRoundRect(trackStart, top, trackEnd, top + thickness, radius, radius, fillPaint)
        val fill = (trackEnd - trackStart) * progress
        val fillStart = if (rtl) trackEnd - fill else trackStart
        val fillEnd = if (rtl) trackEnd else trackStart + fill
        if (fill > 0f) {
            fillPaint.color = activeColor
            fillPaint.alpha = (Color.alpha(activeColor) * alpha).toInt()
            canvas.drawRoundRect(fillStart, top, fillEnd, top + thickness, radius, radius, fillPaint)
        }
        if (ticks in 1..MAX_TICKS) {
            val dot = TICK_RADIUS_DP * density
            for (index in 0..ticks) {
                val tickX = centerMin + index * (centerMax - centerMin) / ticks
                val activeSide = tickX in fillStart..fillEnd
                val color = if (activeSide) tickActiveColor else tickInactiveColor
                fillPaint.color = color
                fillPaint.alpha = (Color.alpha(color) * alpha).toInt()
                canvas.drawCircle(tickX, centerY, dot, fillPaint)
            }
        }

        val sourceWidth = capsuleWidth * SOURCE_SPAN
        val sourceHeight = capsuleHeight * SOURCE_SPAN
        val sourceLeft = centerX - sourceWidth / 2f
        val sourceTop = centerY - sourceHeight / 2f
        record(sourceLeft, sourceTop, sourceWidth, sourceHeight) { recording ->
            recording.translate(-sourceLeft, -sourceTop)
            fillPaint.alpha = OPAQUE
            fillPaint.color = inactiveColor
            recording.drawRoundRect(trackStart, top, trackEnd, top + thickness, radius, radius, fillPaint)
            if (fill > 0f) {
                fillPaint.color = activeColor
                recording.drawRoundRect(fillStart, top, fillEnd, top + thickness, radius, radius, fillPaint)
            }
        }
        drawCapsule(
            canvas, 0f, 0f, centerX, centerY, capsuleWidth, capsuleHeight, press,
            velocity, true, alpha, SLIDER_PRESS_SCALE, SLIDER_MAX_STRETCH,
            boundsTop = boundsTop, boundsBottom = boundsBottom
        )
    }

    fun drawSliderThumb(
        canvas: Canvas,
        width: Float,
        height: Float,
        horizontal: Boolean,
        startIsActive: Boolean,
        press: Float,
        density: Float,
        activeColor: Int,
        inactiveColor: Int,
        surfaceColor: Int,
        alpha: Float
    ) {
        val long = SLIDER_LONG_DP * density
        val short = SLIDER_SHORT_DP * density
        val capsuleWidth = if (horizontal) long else short
        val capsuleHeight = if (horizontal) short else long
        val centerX = width / 2f
        val centerY = height / 2f
        val sourceWidth = capsuleWidth * SOURCE_SPAN
        val sourceHeight = capsuleHeight * SOURCE_SPAN
        val half = TRACK_DP * density / 2f
        record(centerX - sourceWidth / 2f, centerY - sourceHeight / 2f, sourceWidth, sourceHeight) { recording ->
            val first = ColorBlend.over(if (startIsActive) activeColor else inactiveColor, surfaceColor.opaque())
            val second = ColorBlend.over(if (startIsActive) inactiveColor else activeColor, surfaceColor.opaque())
            fillPaint.alpha = OPAQUE
            if (horizontal) {
                val bandTop = sourceHeight / 2f - half
                fillPaint.color = first
                recording.drawRect(0f, bandTop, sourceWidth / 2f, bandTop + half * 2f, fillPaint)
                fillPaint.color = second
                recording.drawRect(sourceWidth / 2f, bandTop, sourceWidth, bandTop + half * 2f, fillPaint)
            } else {
                val bandLeft = sourceWidth / 2f - half
                fillPaint.color = first
                recording.drawRect(bandLeft, 0f, bandLeft + half * 2f, sourceHeight / 2f, fillPaint)
                fillPaint.color = second
                recording.drawRect(bandLeft, sourceHeight / 2f, bandLeft + half * 2f, sourceHeight, fillPaint)
            }
        }
        drawCapsule(canvas, 0f, 0f, centerX, centerY, capsuleWidth, capsuleHeight, press, 0f, horizontal, alpha, SLIDER_PRESS_SCALE, SLIDER_MAX_STRETCH)
    }

    private fun Int.opaque(): Int = if (Color.alpha(this) == 0) Color.BLACK else ColorUtils.setAlphaComponent(this, OPAQUE)

    private inline fun record(left: Float, top: Float, width: Float, height: Float, block: (RecordingCanvas) -> Unit) {
        sourceLeft = left
        sourceTop = top
        source.setPosition(0, 0, width.toInt().coerceAtLeast(1), height.toInt().coerceAtLeast(1))
        val recording = source.beginRecording()
        try {
            block(recording)
        } finally {
            source.endRecording()
        }
    }

    private fun drawCapsule(
        canvas: Canvas,
        left: Float,
        top: Float,
        centerX: Float,
        centerY: Float,
        width: Float,
        height: Float,
        press: Float,
        velocity: Float,
        horizontal: Boolean,
        alpha: Float,
        pressScale: Float = PRESS_SCALE,
        maxStretch: Float = MAX_STRETCH,
        boundsStart: Float = Float.NEGATIVE_INFINITY,
        boundsEnd: Float = Float.POSITIVE_INFINITY,
        boundsTop: Float = Float.NEGATIVE_INFINITY,
        boundsBottom: Float = Float.POSITIVE_INFINITY,
        lensScaleX: Float = 1f,
        lensScaleY: Float = 1f
    ) {
        val stretch = (abs(velocity) * STRETCH).coerceAtMost(maxStretch)
        val scale = 1f + pressScale * press
        val roomX = 2f * minOf(centerX - boundsStart, boundsEnd - centerX) / width
        val roomY = 2f * minOf(centerY - boundsTop, boundsBottom - centerY) / height
        val scaleX = minOf(scale * if (horizontal) 1f + stretch else 1f - stretch / 2f, maxOf(1f, roomX))
        val scaleY = minOf(scale * if (horizontal) 1f - stretch / 2f else 1f + stretch, maxOf(1f, roomY))
        val radius = minOf(width, height) / 2f

        val count = canvas.save()
        try {
            canvas.translate(left + centerX, top + centerY)
            canvas.scale(scaleX, scaleY)
            canvas.translate(-width / 2f, -height / 2f)
            if (press > VISIBLE_PRESS) {
                lensPath.rewind()
                lensPath.addRoundRect(0f, 0f, width, height, radius, radius, Path.Direction.CW)
                radii.fill(radius)
                lensMatrix.setTranslate(sourceLeft - centerX, sourceTop - centerY)
                lensMatrix.postScale(1f / scaleX, 1f / scaleY)
                lensMatrix.postScale(lensScaleX, lensScaleY)
                lensMatrix.postTranslate(width / 2f, height / 2f)
                lens.drawSource(canvas, lensPath, source, lensMatrix, width, height, radii, Color.TRANSPARENT, STYLE, press * alpha)
            }
            thumbPaint.color = Color.WHITE
            thumbPaint.alpha = (OPAQUE * (1f - press) * alpha).toInt().coerceIn(0, OPAQUE)
            canvas.drawRoundRect(0f, 0f, width, height, radius, radius, thumbPaint)
        } finally {
            canvas.restoreToCount(count)
        }
    }

    private companion object {
        const val SOURCE_NODE = "GlassLayerKyantSource"
        const val RADII = 4
        const val OPAQUE = 255
        const val PADDING_DP = 2f
        const val RATIO = 5f / 3f
        const val MIN_TRAVEL = 0.45f
        const val PRESS_SCALE = 0.5f
        const val STRETCH = 0.6f
        const val MAX_STRETCH = 0.2f
        const val VISIBLE_PRESS = 0.01f
        const val TOGGLE_LENS_X_START = 2f / 3f
        const val TOGGLE_LENS_X_END = 0.75f
        const val TOGGLE_LENS_Y_END = 0.75f
        const val SLIDER_LONG_DP = 40f
        const val SLIDER_SHORT_DP = 24f
        const val SOURCE_SPAN = 3f
        const val TRACK_DP = 6f
        const val SLIDER_PRESS_SCALE = 0.3f
        const val TICK_RADIUS_DP = 1.25f
        const val MAX_TICKS = 50
        const val SLIDER_MAX_STRETCH = 0.1f
        val STYLE = LiquidGlassStyle(refraction = 2f, reflection = 1.2f, rim = 0.9f, shadow = 1.2f)
    }
}
