package com.drdisagree.glasslayer.xposed.systemui

import android.R as AndroidR
import android.content.res.ColorStateList
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableContainer
import android.graphics.drawable.DrawableWrapper
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.Button
import android.widget.CompoundButton
import android.widget.ImageButton
import com.drdisagree.glasslayer.xposed.glass.ComposeLiquidGlass
import com.drdisagree.glasslayer.xposed.glass.GlassJoin
import com.drdisagree.glasslayer.xposed.glass.PressHighlight
import com.drdisagree.glasslayer.xposed.glass.ViewGlassDrawable
import com.drdisagree.glasslayer.xposed.glass.ViewGlassParams
import com.drdisagree.glasslayer.xposed.hook.Guard
import com.drdisagree.glasslayer.xposed.hook.call
import com.drdisagree.glasslayer.xposed.hook.field
import com.drdisagree.glasslayer.xposed.hook.intercept
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import kotlin.math.roundToInt

object ViewGlassUnderlay {

    private val targets = WeakHashMap<View, UnderlayTarget>()
    private val presses = WeakHashMap<View, ViewPress>()

    @Volatile
    var discoverer: ((View) -> Unit)? = null
    private val pressRect = RectF()
    private val pressPath = Path()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val clipPath = Path()

    fun install(): Int {
        val draw = try {
            View::class.java.getDeclaredMethod("draw", Canvas::class.java)
        } catch (_: Throwable) {
            null
        } ?: return 0
        val handle = draw.intercept { chain ->
            val view = chain.thisObject as? View
            val canvas = chain.args.firstOrNull() as? Canvas
            val target = if (targets.isEmpty()) null else view?.let { targets[it] }
            val press = if (presses.isEmpty()) null else view?.let { presses[it] }
            if (view != null && (view is Button || view is ImageButton) && view !is CompoundButton) discover(view)
            if (view == null || canvas == null || (target == null && press == null)) {
                chain.proceed()
            } else if (press == null) {
                drawTarget(view, canvas, target!!) { chain.proceed() }
            } else {
                drawPressed(view, canvas, press) {
                    if (target == null) chain.proceed() else drawTarget(view, canvas, target) { chain.proceed() }
                }
            }
        }
        val hotspot = try {
            View::class.java.getDeclaredMethod("drawableHotspotChanged", Float::class.javaPrimitiveType, Float::class.javaPrimitiveType)
        } catch (_: Throwable) {
            null
        }
        val hotspotHandle = hotspot?.intercept { chain ->
            val view = chain.thisObject as? View
            val press = if (presses.isEmpty()) null else view?.let { presses[it] }
            if (press != null) {
                press.x = chain.args.getOrNull(0) as? Float ?: press.x
                press.y = chain.args.getOrNull(1) as? Float ?: press.y
            }
            chain.proceed()
        }
        return (if (handle != null) 1 else 0) + (if (hotspotHandle != null) 1 else 0)
    }

    private fun discover(view: View) {
        val discover = discoverer ?: return
        Guard.run("ViewGlassUnderlay.discover", Unit) { discover(view) }
    }

    private inline fun drawTarget(view: View, canvas: Canvas, target: UnderlayTarget, stock: () -> Any?): Any? {
        resolveJoin(view, target)
        target.scrim?.let { color ->
            if (target.active()) {
                ComposeLiquidGlass.ensureExcluded(view.rootView)
                canvas.drawColor(color())
            }
        }
        var count = -1
        try {
            val isolate = drawUnder(view, canvas, target)
            if (target.joinActive && target.clipJoin) count = clipJoin(canvas, target, 0f)
            if (isolate) {
                val layer = canvas.saveLayer(
                    view.scrollX.toFloat(),
                    view.scrollY.toFloat(),
                    (view.scrollX + view.width).toFloat(),
                    (view.scrollY + view.height).toFloat(),
                    null
                )
                if (count < 0) count = layer
            }
            val result = stock()
            if (target.rimOnTop && target.tinted != null && target.stockAlpha > 0) {
                val strength = if (GlassConfig.glassAvailable) target.appliedAlpha.coerceAtLeast(0) / target.stockAlpha.toFloat() else 1f
                target.glass?.drawRim(canvas, strength, target.sheenOnTop)
            }
            return result
        } finally {
            if (count >= 0) canvas.restoreToCount(count)
        }
    }

    private inline fun drawPressed(view: View, canvas: Canvas, press: ViewPress, content: () -> Any?): Any? {
        val active = press.active() && view.isEnabled
        updateRipple(view, press, active)
        press.spring.update(active && view.isPressed, SystemClock.uptimeMillis())
        if (press.spring.animating) view.postInvalidateOnAnimation()
        val value = press.spring.value
        if (!active || value == 0f || view.width <= 0 || view.height <= 0) return content()
        val width = view.width.toFloat()
        val height = view.height.toFloat()
        val shapeHeight = pressShape(view, pressRect)
        val scale = if (GlassConfig.buttonPress) 1f + PRESS_GROW_DP * view.resources.displayMetrics.density / shapeHeight * value.coerceIn(0f, 1f) else 1f
        val count = canvas.save()
        try {
            canvas.scale(scale, scale, view.scrollX + width / 2f, view.scrollY + height / 2f)
            val result = content()
            canvas.translate(view.scrollX.toFloat(), view.scrollY.toFloat())
            PressHighlight.draw(canvas, pressPath, width, height, press.x, press.y, value)
            return result
        } finally {
            canvas.restoreToCount(count)
        }
    }

    private fun pressShape(view: View, out: RectF): Float {
        val background = view.background
        val shape = SolidShapes.of(background)
        val inner = shape?.bounds?.takeUnless { it.isEmpty } ?: innermost(background)?.bounds?.takeUnless { it.isEmpty }
        if (inner != null) out.set(inner) else out.set(0f, 0f, view.width.toFloat(), view.height.toFloat())
        val half = minOf(out.width(), out.height()) / 2f
        val resolved = if (shape == null) innermost(background)?.call(RESOLVED_CORNER) as? Float else null
        val radius = when {
            resolved != null -> resolved.coerceAtMost(half)
            shape == null || shape.shape == GradientDrawable.OVAL -> half
            else -> (shape.cornerRadii?.firstOrNull() ?: shape.cornerRadius).coerceAtMost(minOf(out.width(), out.height()) / 2f)
        }
        pressPath.rewind()
        pressPath.addRoundRect(out, radius, radius, Path.Direction.CW)
        return out.height().coerceAtLeast(1f)
    }

    private fun innermost(drawable: Drawable?): Drawable? = when (drawable) {
        null -> null
        is DrawableWrapper -> drawable.drawable?.let { innermost(it) } ?: drawable
        is DrawableContainer -> drawable.current?.let { innermost(it) } ?: drawable
        is LayerDrawable -> (0 until drawable.numberOfLayers)
            .firstOrNull { drawable.getId(it) != AndroidR.id.mask }
            ?.let { innermost(drawable.getDrawable(it)) }
            ?: drawable
        else -> drawable
    }

    private fun updateRipple(view: View, press: ViewPress, active: Boolean) {
        val ripple = view.background as? RippleDrawable ?: return
        if (active && press.stockRipple == null) {
            (ripple.field(RIPPLE_STATE)?.field(RIPPLE_COLOR) as? ColorStateList)?.let {
                press.stockRipple = it
                ripple.setColor(CLEAR_RIPPLE)
            }
        } else if (!active) {
            press.stockRipple?.let {
                ripple.setColor(it)
                press.stockRipple = null
            }
        }
    }

    fun attachPress(view: View, active: () -> Boolean) {
        if (!presses.containsKey(view)) presses[view] = ViewPress(active)
        view.invalidate()
    }

    fun attach(
        view: View,
        surfaceKey: String,
        active: () -> Boolean,
        tint: () -> Float,
        glass: Boolean = true,
        openEdges: Boolean = false,
        joinWith: ((View, GlassJoin) -> Boolean)? = null,
        clipJoin: Boolean = false,
        tintInGlass: Boolean = false,
        source: ((View) -> Drawable?)? = null,
        behind: ((View) -> Int)? = null,
        imageContent: Boolean = false,
        rimOnTop: Boolean = false,
        segmented: Boolean = false,
        sheenOnTop: Boolean = false,
        backdrop: ((View) -> Drawable?)? = null
    ) {
        val existing = targets[view]
        if (existing == null) {
            targets[view] = UnderlayTarget(surfaceKey, active, tint, glass, openEdges, joinWith, clipJoin, tintInGlass, source, behind)
                .also {
                    it.imageContent = imageContent
                    it.rimOnTop = rimOnTop
                    it.segmented = segmented
                    it.sheenOnTop = sheenOnTop
                    it.backdrop = backdrop
                }
        } else {
            existing.tintInGlass = tintInGlass
            existing.source = source
            existing.behind = behind
            existing.glassEnabled = glass
            existing.openEdges = openEdges
            existing.joinWith = joinWith
            existing.clipJoin = clipJoin
            existing.baseResolved = false
            existing.backdrop = backdrop
        }
        view.invalidate()
    }

    fun isAttached(view: View): Boolean = targets.containsKey(view)

    fun attachScrim(view: View, active: () -> Boolean, color: () -> Int) {
        val target = targets[view] ?: UnderlayTarget("", active, { 1f }, false, false, null, false, false, null, null)
            .also { targets[view] = it }
        target.scrim = color
        view.setWillNotDraw(false)
        view.invalidate()
    }

    fun detach(view: View) {
        if (targets.remove(view) != null) view.invalidate()
    }

    fun refresh() {
        mainHandler.post { targets.keys.toList().forEach { it.invalidate() } }
    }

    private fun resolveJoin(view: View, target: UnderlayTarget) {
        val joinWith = target.joinWith
        target.joinActive = joinWith != null && target.active() && joinWith(view, target.join)
    }

    private fun clipJoin(canvas: Canvas, target: UnderlayTarget, inset: Float): Int {
        clipPath.rewind()
        target.join.addTo(clipPath, inset)
        val count = canvas.save()
        canvas.clipOutPath(clipPath)
        return count
    }

    private fun drawUnder(view: View, canvas: Canvas, target: UnderlayTarget): Boolean {
        val background = target.source?.invoke(view) ?: view.background
        if (!target.active() || background == null) {
            restore(target)
            return false
        }
        target.backdrop?.invoke(view)?.draw(canvas)
        if (target.tinted !== background) {
            restore(target)
            if (background is ColorDrawable) background.mutate()
            target.tinted = background
            target.appliedAlpha = -1
            target.isolate = replacesBelow(background)
        }
        val current = background.alpha
        if (current != target.appliedAlpha) target.stockAlpha = current
        val tint = target.tint()
        val alpha = if (target.tintInGlass) 0 else (target.stockAlpha * tint).roundToInt().coerceIn(0, UnderlayTarget.OPAQUE)
        if (current != alpha) background.alpha = alpha
        target.appliedAlpha = alpha
        if (target.stockAlpha <= 0 || !target.glassEnabled) return false
        target.dim = if (target.tintInGlass) tinted(background, tint) else Color.TRANSPARENT
        target.glassDim = ColorBlend.over(target.dim, baseDim(view, target))

        val glass = target.glass?.takeIf { target.glassSource === background } ?: ViewGlassDrawable(view, background) {
            val surface = GlassConfig.surface(target.surfaceKey)
            ViewGlassParams(
                surface.blurRadiusPx,
                surface.style,
                target.openEdges,
                target.join.takeIf { target.joinActive },
                target.glassDim,
                !GlassConfig.glassAvailable,
                target.imageContent,
                target.rimOnTop,
                target.segmented
            )
        }.also {
            target.glass = it
            target.glassSource = background
        }
        glass.setBounds(view.scrollX, view.scrollY, view.scrollX + view.width, view.scrollY + view.height)
        if (glass.alpha != target.stockAlpha) glass.alpha = target.stockAlpha
        val count = if (target.joinActive && target.clipJoin) clipJoin(canvas, target, SEAM_OVERLAP) else -1
        try {
            glass.draw(canvas)
        } finally {
            if (count >= 0) canvas.restoreToCount(count)
        }
        return target.isolate
    }

    private fun tinted(background: Drawable?, fraction: Float): Int {
        val color = SolidShapes.of(background)?.color?.defaultColor ?: return Color.TRANSPARENT
        val alpha = (Color.alpha(color) * fraction).roundToInt().coerceIn(0, UnderlayTarget.OPAQUE)
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun baseDim(view: View, target: UnderlayTarget): Int {
        if (!target.baseResolved) {
            target.baseResolved = true
            target.base = findBase(view)?.let { WeakReference(it) }
        }
        val baseView = target.base?.get() ?: return target.behind?.invoke(view) ?: Color.TRANSPARENT
        return effectiveTint(baseView)
    }

    fun glassBehind(view: View): Int {
        var current: Any? = view
        while (current is View) {
            if (targets[current]?.glassEnabled == true) return effectiveTint(current)
            current = current.parent
        }
        return Color.TRANSPARENT
    }

    private fun effectiveTint(baseView: View): Int {
        val base = targets[baseView] ?: return Color.TRANSPARENT
        if (!base.active() || !base.glassEnabled) return Color.TRANSPARENT
        if (base.tintInGlass) return base.dim
        val stock = if (base.stockAlpha > 0) base.stockAlpha / UnderlayTarget.OPAQUE.toFloat() else 1f
        return tinted(baseView.background, stock * base.tint())
    }

    private fun findBase(view: View): View? {
        var parent = view.parent
        while (parent is View) {
            if (targets[parent]?.glassEnabled == true) return parent
            parent = parent.parent
        }
        return null
    }

    private fun replacesBelow(drawable: Drawable): Boolean = when (drawable) {
        is GradientDrawable -> (drawable.field(FILL_PAINT) as? Paint)?.blendMode.let { it != null && it != BlendMode.SRC_OVER }
        is DrawableWrapper -> drawable.drawable?.let { replacesBelow(it) } == true
        is LayerDrawable -> (0 until drawable.numberOfLayers).any { index -> drawable.getDrawable(index)?.let { replacesBelow(it) } == true }
        else -> false
    }

    private fun restore(target: UnderlayTarget) {
        target.tinted?.let { if (it.alpha != target.stockAlpha) it.alpha = target.stockAlpha }
        target.tinted = null
        target.appliedAlpha = -1
    }

    private const val FILL_PAINT = "mFillPaint"
    private const val RIPPLE_STATE = "mState"
    private const val RESOLVED_CORNER = "getTopLeftCornerResolvedSize"
    private const val RIPPLE_COLOR = "mColor"
    private const val PRESS_GROW_DP = 2f
    private val CLEAR_RIPPLE = ColorStateList.valueOf(Color.TRANSPARENT)
    private const val SEAM_OVERLAP = 1f
}
