package com.drdisagree.glasslayer.xposed.systemui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import androidx.core.graphics.ColorUtils
import com.drdisagree.glasslayer.data.source.PrefKeys
import com.drdisagree.glasslayer.xposed.compose.ComposeToolkit
import com.drdisagree.glasslayer.xposed.glass.ComposeLiquidGlass
import com.drdisagree.glasslayer.xposed.hook.Guard
import com.drdisagree.glasslayer.xposed.hook.HookEnv
import com.drdisagree.glasslayer.xposed.hook.field
import com.drdisagree.glasslayer.xposed.hook.hookAll
import com.drdisagree.glasslayer.xposed.hook.hookAllAfter
import com.drdisagree.glasslayer.xposed.hook.hookMatching
import com.drdisagree.glasslayer.xposed.hook.proceedWithArgs
import com.drdisagree.glasslayer.xposed.hook.setField
import java.util.WeakHashMap
import kotlin.math.roundToInt

object TransitionGlass {

    private const val EXPANDABLE_KT = "com.android.compose.animation.ExpandableKt"
    private const val WRAPPED_DRAWABLE = "${SystemUiModule.PACKAGE}.animation.GhostedViewTransitionAnimatorController\$WrappedDrawable"
    private const val TRANSITION_ANIMATOR = "${SystemUiModule.PACKAGE}.animation.TransitionAnimator"
    private const val WINDOW_BACKGROUND = "applyStateToWindowBackgroundLayer"
    private const val CURRENT_ALPHA = "currentAlpha"
    private const val CORNER_RADII = "cornerRadii"
    private const val WRAPPED = "wrapped"
    private const val RADII_COUNT = 8
    private const val OVERLAY_NODE = "com.android.compose.animation.DrawExpandableInOverlayNode"
    private const val TOP_RADIUS = "topCornerRadius"
    private const val BOTTOM_RADIUS = "bottomCornerRadius"
    private const val STATE_LEFT = "left"
    private const val STATE_TOP = "top"
    private const val VIEW_LOCATION = "composeViewLocationOnScreen"
    private const val DRAW_NODE = "drawNode"
    private const val COLOR_INDEX = 2
    private const val SIZE_INDEX = 4
    private const val TRANSITION_KEY = 0
    private const val OPAQUE = 255
    private const val LOW_BITS = 0xFFFFFFFFL

    private val ghostBounds = Rect()
    private val appliedAlpha = WeakHashMap<Drawable, Int>()

    fun install(): Int = installCompose() + installGhost() + installWindowBackground()

    private fun installWindowBackground(): Int = HookEnv.findClass(TRANSITION_ANIMATOR).hookAllAfter(WINDOW_BACKGROUND) { chain, _ ->
        if (!GlassConfig.transitionsActive) return@hookAllAfter
        val layer = chain.args.getOrNull(0) as? Drawable ?: return@hookAllAfter
        val stock = layer.alpha
        if (appliedAlpha[layer] == stock) return@hookAllAfter
        val alpha = (stock * GlassConfig.transitionTint).roundToInt().coerceIn(0, OPAQUE)
        appliedAlpha[layer] = alpha
        layer.alpha = alpha
    }

    private fun installCompose(): Int = HookEnv.findClass(EXPANDABLE_KT).hookMatching(Regex("drawBackground-.*")) { chain ->
        val args = chain.args
        val color = args.getOrNull(COLOR_INDEX) as? Long
        if (!GlassConfig.transitionsActive || color == null) return@hookMatching chain.proceed()
        val drawScope = args[0] ?: return@hookMatching chain.proceed()
        val state = args[1] ?: return@hookMatching chain.proceed()
        val size = args.getOrNull(SIZE_INDEX) as? Long ?: ComposeToolkit.drawSize(drawScope)
            ?: return@hookMatching chain.proceed()
        Guard.run("TransitionGlass.glass", Unit) { drawGlass(drawScope, state, size) }
        chain.proceedWithArgs { values -> values[COLOR_INDEX] = tinted(color) }
    }

    private fun installGhost(): Int = HookEnv.findClass(WRAPPED_DRAWABLE).hookAll("draw") { chain ->
        val drawable = chain.thisObject as? Drawable
        val canvas = chain.args.getOrNull(0) as? Canvas
        val alpha = drawable?.field(CURRENT_ALPHA) as? Int
        if (!GlassConfig.transitionsActive || drawable == null || canvas == null || alpha == null || alpha <= 0 || drawable.bounds.isEmpty) {
            return@hookAll chain.proceed()
        }
        Guard.run("TransitionGlass.ghost", Unit) { drawGhostGlass(drawable, canvas, alpha) }
        drawable.setField(CURRENT_ALPHA, (alpha * GlassConfig.transitionTint).roundToInt().coerceIn(0, OPAQUE))
        try {
            chain.proceed()
        } finally {
            drawable.setField(CURRENT_ALPHA, alpha)
        }
    }

    private fun drawGhostGlass(drawable: Drawable, canvas: Canvas, alpha: Int) {
        val view = drawable.callback as? View ?: return
        val wrapped = drawable.field(WRAPPED) as? Drawable
        val shape = SolidShapes.of(wrapped)
        val outer = wrapped?.bounds
        val inner = shape?.bounds
        ghostBounds.set(drawable.bounds)
        if (outer != null && inner != null && !outer.isEmpty && !inner.isEmpty && shape !== wrapped) {
            ghostBounds.left += inner.left - outer.left
            ghostBounds.top += inner.top - outer.top
            ghostBounds.right -= outer.right - inner.right
            ghostBounds.bottom -= outer.bottom - inner.bottom
        }
        if (ghostBounds.isEmpty) return
        val width = ghostBounds.width().toFloat()
        val height = ghostBounds.height().toFloat()
        val radii = ghostRadii(drawable, shape, minOf(width, height) / 2f)
        val surface = GlassConfig.surface(PrefKeys.TRANSITIONS)
        ComposeLiquidGlass.drawView(
            view,
            canvas,
            ghostBounds.left.toFloat(),
            ghostBounds.top.toFloat(),
            width,
            height,
            radii,
            surface.blurRadiusPx,
            Color.TRANSPARENT,
            surface.style,
            alpha / OPAQUE.toFloat()
        )
    }

    private fun ghostRadii(drawable: Drawable, shape: GradientDrawable?, limit: Float): FloatArray {
        val animated = drawable.field(CORNER_RADII) as? FloatArray
        val source = when {
            animated != null && animated.size >= RADII_COUNT && animated[0] >= 0f -> animated
            shape?.shape == GradientDrawable.OVAL -> null
            else -> shape?.cornerRadii
        }
        if (source != null && source.size >= RADII_COUNT) return FloatArray(RADII_COUNT) { minOf(source[it], limit) }
        val radius = if (shape == null || shape.shape == GradientDrawable.OVAL) limit else minOf(shape.cornerRadius, limit)
        return FloatArray(RADII_COUNT) { radius }
    }

    private fun drawGlass(drawScope: Any, state: Any, size: Long) {
        val width = Float.fromBits((size ushr 32).toInt())
        val height = Float.fromBits((size and LOW_BITS).toInt())
        if (width <= 0f || height <= 0f) return
        val top = state.field(TOP_RADIUS) as? Float ?: 0f
        val bottom = state.field(BOTTOM_RADIUS) as? Float ?: 0f
        val radii = floatArrayOf(top, top, top, top, bottom, bottom, bottom, bottom)
        val surface = GlassConfig.surface(PrefKeys.TRANSITIONS)
        val node = drawScope.field(DRAW_NODE)
        val location = if (node?.javaClass?.name == OVERLAY_NODE) node.field(VIEW_LOCATION) as? IntArray else null
        if (location == null) {
            ComposeLiquidGlass.drawSegment(
                drawScope, TRANSITION_KEY, 0f, 0f, width, height, radii, surface.blurRadiusPx, Color.TRANSPARENT, surface.style
            )
            return
        }
        val canvas = ComposeToolkit.nativeCanvas(drawScope) ?: return
        val offsetX = ((state.field(STATE_LEFT) as? Int ?: return) - location[0]).toFloat()
        val offsetY = ((state.field(STATE_TOP) as? Int ?: return) - location[1]).toFloat()
        val count = canvas.save()
        try {
            canvas.translate(-offsetX, -offsetY)
            ComposeLiquidGlass.drawSegment(
                drawScope, TRANSITION_KEY, offsetX, offsetY, width, height, radii, surface.blurRadiusPx, Color.TRANSPARENT, surface.style
            )
        } finally {
            canvas.restoreToCount(count)
        }
    }

    private fun tinted(color: Long): Long {
        val argb = ComposeToolkit.toArgb(color) ?: return color
        val alpha = (Color.alpha(argb) * GlassConfig.transitionTint).roundToInt().coerceIn(0, OPAQUE)
        return ComposeToolkit.colorOf(ColorUtils.setAlphaComponent(argb, alpha))
    }
}
