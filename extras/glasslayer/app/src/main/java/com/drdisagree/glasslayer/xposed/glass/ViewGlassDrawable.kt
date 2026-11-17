package com.drdisagree.glasslayer.xposed.glass

import android.R as AndroidR
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableContainer
import android.graphics.drawable.DrawableWrapper
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.TransitionDrawable
import android.view.View
import android.widget.ImageView
import com.drdisagree.glasslayer.xposed.hook.Guard
import com.drdisagree.glasslayer.xposed.hook.call
import com.drdisagree.glasslayer.xposed.hook.field

class ViewGlassDrawable(
    private val host: View,
    private val shapeSource: Drawable?,
    private val params: () -> ViewGlassParams
) : Drawable() {

    private val radii = FloatArray(RADII)
    private val endRadii = FloatArray(RADII)
    private val glassBounds = Rect()
    private val mappedBounds = RectF()
    private val localJoin = GlassJoin()
    private var alphaFraction = 1f
    private val transition: TransitionDrawable? by lazy { shapeSource?.findTransition() }
    private val shape: GradientDrawable?
        get() = (transition?.getDrawable(0) ?: shapeSource)?.findShape()
    private val endShape: GradientDrawable? by lazy { transition?.takeIf { it.numberOfLayers > 1 }?.getDrawable(1)?.findShape() }

    override fun draw(canvas: Canvas) {
        Guard.run(TAG, Unit) { drawGlass(canvas) }
    }

    fun drawRim(canvas: Canvas, strength: Float, sheen: Boolean = false) {
        Guard.run(RIM_TAG, Unit) {
            val current = params()
            if (resolveGeometry(current)) {
                GlassRim.draw(
                    canvas,
                    glassBounds,
                    radii,
                    host.resources.displayMetrics.density,
                    alphaFraction * strength,
                    sheen,
                    current.style.rim,
                    current.style.reflection
                )
            }
        }
    }

    private fun drawGlass(canvas: Canvas) {
        val current = params()
        if (!resolveGeometry(current)) return
        if (current.rimOnly) {
            val density = host.resources.displayMetrics.density
            if (current.segmented) {
                GlassRim.drawSegment(canvas, glassBounds, radii, density, alphaFraction, current.style.rim, current.style.reflection)
            } else if (!current.rimOnTop) {
                GlassRim.draw(canvas, glassBounds, radii, density, alphaFraction, rim = current.style.rim, reflection = current.style.reflection)
            }
            return
        }
        ComposeLiquidGlass.drawView(
            host,
            canvas,
            glassBounds.left.toFloat(),
            glassBounds.top.toFloat(),
            glassBounds.width().toFloat(),
            glassBounds.height().toFloat(),
            radii,
            current.blurRadius,
            current.dim,
            current.style,
            alphaFraction,
            openSmallEdges = current.openEdges,
            join = current.join?.let {
                localJoin.set(it, -glassBounds.left.toFloat(), -glassBounds.top.toFloat())
                localJoin
            }
        )
    }

    private fun resolveGeometry(current: ViewGlassParams): Boolean {
        val shape = shape
        glassBounds.set(
            shape?.bounds?.takeUnless { it.isEmpty }
                ?: shapeSource?.innermost()?.bounds?.takeUnless { it.isEmpty }
                ?: shapeSource?.bounds?.takeUnless { it.isEmpty }
                ?: bounds
        )
        if (glassBounds.isEmpty) return false
        val imageScale = if (current.imageContent) mapImageBounds() else 1f
        if (glassBounds.isEmpty) return false
        val width = glassBounds.width().toFloat()
        val height = glassBounds.height().toFloat()
        val maxRadius = minOf(width, height) / 2f
        val resolved = if (shape == null) {
            shapeSource?.innermost()?.let { it.call(RESOLVED_CORNER) as? Float ?: OutlineRadius.of(it) }
        } else {
            null
        }
        if (resolved != null) radii.fill(resolved.coerceAtMost(maxRadius)) else shape.radiiInto(radii, maxRadius / imageScale)
        if (imageScale != 1f) for (index in radii.indices) radii[index] = (radii[index] * imageScale).coerceAtMost(maxRadius)
        val end = endShape
        val progress = transitionProgress()
        if (end != null && progress > 0f) {
            end.radiiInto(endRadii, maxRadius)
            for (index in radii.indices) radii[index] += (endRadii[index] - radii[index]) * progress
        }
        return true
    }

    private fun mapImageBounds(): Float {
        val image = host as? ImageView ?: return 1f
        mappedBounds.set(glassBounds)
        image.imageMatrix.mapRect(mappedBounds)
        mappedBounds.offset(image.paddingLeft.toFloat(), image.paddingTop.toFloat())
        val scale = if (glassBounds.width() > 0) mappedBounds.width() / glassBounds.width() else 1f
        mappedBounds.roundOut(glassBounds)
        return scale
    }

    override fun getAlpha(): Int = (alphaFraction * 255).toInt()

    override fun setAlpha(alpha: Int) {
        alphaFraction = alpha / 255f
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {}

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    private fun transitionProgress(): Float {
        val alpha = transition?.field(TRANSITION_ALPHA) as? Int ?: return 0f
        return (alpha / OPAQUE).coerceIn(0f, 1f)
    }

    private fun GradientDrawable?.radiiInto(out: FloatArray, maxRadius: Float) {
        val shapeRadii = this?.cornerRadii
        if (this?.shape == GradientDrawable.OVAL) {
            out.fill(maxRadius)
        } else if (shapeRadii != null && shapeRadii.size == RADII) {
            shapeRadii.forEachIndexed { index, value -> out[index] = value.coerceAtMost(maxRadius) }
        } else {
            out.fill((this?.cornerRadius ?: maxRadius).coerceAtMost(maxRadius))
        }
    }

    private fun Drawable.innermost(): Drawable = when (this) {
        is DrawableWrapper -> drawable?.innermost() ?: this
        is DrawableContainer -> current?.innermost() ?: this
        is LayerDrawable -> (0 until numberOfLayers)
            .firstOrNull { getId(it) != AndroidR.id.mask }
            ?.let { getDrawable(it)?.innermost() }
            ?: this
        else -> this
    }

    private fun Drawable.findTransition(): TransitionDrawable? = when (this) {
        is TransitionDrawable -> this
        is DrawableWrapper -> drawable?.findTransition()
        else -> null
    }

    private fun Drawable.findShape(): GradientDrawable? {
        if (this is GradientDrawable) return this
        if (this is DrawableWrapper) return drawable?.findShape()
        if (this is DrawableContainer) return current?.findShape()
        if (this is LayerDrawable) {
            for (index in 0 until numberOfLayers) {
                if (getId(index) == AndroidR.id.mask) continue
                getDrawable(index)?.findShape()?.let { return it }
            }
        }
        return null
    }
}

private const val TAG = "ViewGlassDrawable"
private const val RIM_TAG = "ViewGlassDrawable.rim"
private const val RADII = 8
private const val OPAQUE = 255f
private const val TRANSITION_ALPHA = "mAlpha"
private const val RESOLVED_CORNER = "getTopLeftCornerResolvedSize"
