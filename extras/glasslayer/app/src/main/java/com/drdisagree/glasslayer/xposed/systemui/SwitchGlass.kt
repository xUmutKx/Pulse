package com.drdisagree.glasslayer.xposed.systemui

import android.R as AndroidR
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.RippleDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Choreographer
import android.view.View
import android.widget.Checkable
import android.widget.Switch
import androidx.core.graphics.ColorUtils
import com.drdisagree.glasslayer.data.source.PrefKeys
import com.drdisagree.glasslayer.xposed.compose.ComposeModifiers
import com.drdisagree.glasslayer.xposed.compose.ComposeToolkit
import com.drdisagree.glasslayer.xposed.glass.ComposeLiquidGlass
import com.drdisagree.glasslayer.xposed.glass.GlassRim
import com.drdisagree.glasslayer.xposed.hook.Guard
import com.drdisagree.glasslayer.xposed.hook.HookEnv
import com.drdisagree.glasslayer.xposed.hook.call
import com.drdisagree.glasslayer.xposed.hook.field
import com.drdisagree.glasslayer.xposed.hook.hookAll
import com.drdisagree.glasslayer.xposed.hook.hookAllBefore
import com.drdisagree.glasslayer.xposed.hook.hookMatching
import com.drdisagree.glasslayer.xposed.hook.proceedWithArgs
import com.drdisagree.glasslayer.xposed.hook.setField
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap

object SwitchGlass {

    private const val SWITCH_KT = "androidx.compose.material3.SwitchKt"
    private const val SWITCH_COLORS = "androidx.compose.material3.SwitchColors"
    private const val SWITCH_COMPAT = "androidx.appcompat.widget.SwitchCompat"
    private const val DISABLED_ALPHA = 0.38f
    private const val RTL = "Rtl"
    private const val TOUCH_MODE_DRAGGING = 2
    private const val OPAQUE = 255

    private val pendingRedraws = Collections.newSetFromMap(WeakHashMap<Any, Boolean>())
    private val composeMotions = WeakHashMap<Any, SwitchMotion>()
    private val viewMotions = WeakHashMap<View, ViewSwitchMotion>()
    private val hiddenViews = Collections.newSetFromMap(WeakHashMap<View, Boolean>())
    private val stockMinWidths = WeakHashMap<View, Int>()
    private val stockRipples = WeakHashMap<View, ColorStateList>()
    private val recomposeScopes = Collections.newSetFromMap(WeakHashMap<Any, Boolean>())
    private val modifierToOriginal = WeakHashMap<Any, Any>()
    private val plainPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimRect = RectF()
    private val rimRadii = FloatArray(CORNERS)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val checkedState = intArrayOf(AndroidR.attr.state_checked, AndroidR.attr.state_enabled)
    private val uncheckedState = intArrayOf(AndroidR.attr.state_enabled)
    private val disabledCheckedState = intArrayOf(AndroidR.attr.state_checked)
    private val disabledUncheckedState = intArrayOf()

    fun install(): Int {
        var hooks = 0
        hooks += HookEnv.findClass(SWITCH_KT).hookMatching(Regex("SwitchImpl(-.*)?")) { chain ->
            val method = chain.executable as Method
            val composerIndex = method.parameterTypes.indexOfFirst { it.name == ComposeToolkit.COMPOSER_CLASS }
            chain.args.getOrNull(composerIndex)?.call("getRecomposeScope")?.let { recomposeScopes.add(it) }
            if (!GlassConfig.switchesActive) return@hookMatching chain.proceed()
            chain.proceedWithArgs { args -> wrap(method, args) }
        }
        hooks += Switch::class.java.hookAllBefore("onMeasure") { chain -> (chain.thisObject as? View)?.let { widen(it) } }
        hooks += HookEnv.findClass(SWITCH_COMPAT).hookAllBefore("onMeasure") { chain -> (chain.thisObject as? View)?.let { widen(it) } }
        hooks += Switch::class.java.hookAll("onDraw") { chain -> drawView(chain.thisObject as? View, chain.args.firstOrNull() as? Canvas) { chain.proceed() } }
        hooks += HookEnv.findClass(SWITCH_COMPAT).hookAll("onDraw") { chain ->
            drawView(chain.thisObject as? View, chain.args.firstOrNull() as? Canvas) { chain.proceed() }
        }
        return hooks
    }

    fun refresh() {
        mainHandler.post {
            recomposeScopes.toList().forEach { it.call("invalidateForResult", null) }
            hiddenViews.toList().forEach { it.invalidate() }
        }
    }

    private fun drawView(view: View?, canvas: Canvas?, stock: () -> Any?): Any? {
        if (view == null || canvas == null) return stock()
        val track = view.field("mTrackDrawable") as? Drawable
        val thumb = view.field("mThumbDrawable") as? Drawable
        if (!GlassConfig.switchesActive) {
            if (hiddenViews.remove(view)) {
                track?.alpha = OPAQUE
                thumb?.alpha = OPAQUE
                view.background?.let { if (it.alpha != OPAQUE) it.alpha = OPAQUE }
                (view.background as? RippleDrawable)?.let { ripple -> stockRipples.remove(view)?.let { ripple.setColor(it) } }
                stockMinWidths.remove(view)?.let {
                    view.setField(MIN_WIDTH_FIELD, it)
                    view.requestLayout()
                }
            }
            return stock()
        }
        if (track?.alpha != 0) track?.alpha = 0
        if (thumb?.alpha != 0) thumb?.alpha = 0
        view.background?.let { if (it.alpha != 0) it.alpha = 0 }
        (view.background as? RippleDrawable)?.let { ripple ->
            if (!stockRipples.containsKey(view)) {
                (ripple.field(RIPPLE_STATE)?.field(RIPPLE_COLOR) as? ColorStateList)?.let {
                    stockRipples[view] = it
                    ripple.setColor(CLEAR_RIPPLE)
                }
            }
        }
        hiddenViews.add(view)
        val result = stock()
        Guard.run("SwitchGlass.view", Unit) { drawViewToggle(view, canvas) }
        return result
    }

    private fun widen(view: View) {
        if (!GlassConfig.switchesActive) return
        val current = (view.field(MIN_WIDTH_FIELD) as? Number)?.toInt() ?: return
        val target = (KYANT_WIDTH_DP * view.resources.displayMetrics.density).toInt()
        if (current >= target) return
        stockMinWidths.getOrPut(view) { current }
        view.setField(MIN_WIDTH_FIELD, target)
    }

    private fun drawViewToggle(view: View, canvas: Canvas) {
        val switchLeft = (view.field("mSwitchLeft") as? Number)?.toFloat() ?: return
        val switchTop = (view.field("mSwitchTop") as? Number)?.toFloat() ?: return
        val switchRight = (view.field("mSwitchRight") as? Number)?.toFloat() ?: return
        val switchBottom = (view.field("mSwitchBottom") as? Number)?.toFloat() ?: return
        val density = view.resources.displayMetrics.density
        val rtl = view.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val start = maxOf(switchLeft, view.paddingLeft.toFloat())
        val end = minOf(switchRight, (view.width - view.paddingRight).toFloat())
        val width = minOf(end - start, KYANT_WIDTH_DP * density)
        val height = minOf(switchBottom - switchTop, KYANT_HEIGHT_DP * density, width * KYANT_HEIGHT_DP / KYANT_WIDTH_DP)
        if (width <= 0f || height <= 0f) return
        val left = if (rtl) start else end - width
        val top = (switchTop + switchBottom - height) / 2f
        val raw = (view.field("mThumbPosition") as? Number)?.toFloat() ?: if ((view as? Checkable)?.isChecked == true) 1f else 0f
        val dragging = (view.field("mTouchMode") as? Number)?.toInt() == TOUCH_MODE_DRAGGING
        val motion = viewMotions.getOrPut(view) { ViewSwitchMotion() }
        motion.update(raw, view.isPressed || dragging, SystemClock.uptimeMillis())
        if (motion.animating) view.postInvalidateOnAnimation()

        val enabled = view.isEnabled
        val tints = view.field("trackTintList") as? ColorStateList ?: view.field("mTrackTintList") as? ColorStateList
        val fallback = SolidShapes.of(view.field("mTrackDrawable") as? Drawable)?.color
        val list = tints ?: fallback
        val on = list?.getColorForState(if (enabled) checkedState else disabledCheckedState, list.defaultColor) ?: Color.GRAY
        val off = KyantTrackColors.off(view.context, enabled)
        val trackColor = ColorUtils.blendARGB(off, on, raw)
        val behind = SurfaceBehind.color(view)
        val surface = GlassConfig.surface(PrefKeys.SWITCHES)
        val radii = FloatArray(8) { height / 2f }
        if (GlassConfig.glassAvailable && onGlass(view.rootView)) {
            ComposeLiquidGlass.drawView(
                view,
                canvas,
                left,
                top,
                width,
                height,
                radii,
                surface.blurRadiusPx,
                ColorBlend.over(ColorBlend.scaleAlpha(trackColor, GlassConfig.switchTint), behind),
                surface.style,
                key = TRACK_KEY
            )
        } else {
            drawPlainTrack(canvas, left, top, width, height, trackColor, density, surface)
        }
        motion.thumb.drawToggle(
            canvas,
            left,
            top,
            width,
            height,
            if (rtl) 1f - raw else raw,
            if (GlassConfig.switchPress) motion.press else 0f,
            motion.velocity,
            density,
            ColorBlend.over(trackColor, behind.withOpaque()),
            behind.withOpaque(),
            if (enabled) 1f else DISABLED_ALPHA,
            0f,
            view.width.toFloat(),
            0f,
            view.height.toFloat()
        )
    }

    private fun wrap(method: Method, args: Array<Any?>) {
        val types = method.parameterTypes
        val modifierIndex = types.indexOfFirst { it.name == ComposeToolkit.MODIFIER_CLASS }
        if (modifierIndex == -1) return
        val checked = args.getOrNull(modifierIndex + 1) as? Boolean ?: return
        val enabled = args.getOrNull(modifierIndex + 2) as? Boolean ?: true
        val colors = args.getOrNull(types.indexOfFirst { it.name == SWITCH_COLORS })
        val passed = args[modifierIndex] ?: return
        val original = modifierToOriginal[passed] ?: passed
        val drawer = ComposeModifiers.contentProxy("SwitchGlass.draw") { drawScope ->
            drawComposeToggle(drawScope, checked, enabled, colors)
        } ?: return
        ComposeModifiers.drawWithContent(original, drawer)?.let {
            modifierToOriginal[it] = original
            args[modifierIndex] = it
        }
    }

    private fun drawComposeToggle(drawScope: Any, checked: Boolean, enabled: Boolean, colors: Any?) {
        ComposeLiquidGlass.observeFrames()
        val canvas = ComposeToolkit.nativeCanvas(drawScope) ?: return drawScope.drawStock()
        val size = ComposeToolkit.drawSize(drawScope) ?: return drawScope.drawStock()
        val node = drawScope.field("drawNode") ?: return drawScope.drawStock()
        val width = Float.fromBits((size ushr 32).toInt())
        val height = Float.fromBits((size and 0xFFFFFFFFL).toInt())
        if (width <= 0f || height <= 0f) return

        val motion = composeMotions.getOrPut(node) { SwitchMotion(checked) }
        motion.update(checked, SystemClock.uptimeMillis())
        if (motion.animating) {
            ComposeLiquidGlass.keepFrames(SWITCH_FRAMES_MS)
            if (pendingRedraws.add(node)) {
                Choreographer.getInstance().postFrameCallback {
                    pendingRedraws.remove(node)
                    Guard.run(REDRAW_TAG, Unit) { ComposeToolkit.invalidateDraw(node) }
                }
            }
        }

        val on = colors.color(if (enabled) "checkedTrackColor" else "disabledCheckedTrackColor")
        val off = KyantTrackColors.off(ComposeToolkit.ownerView(drawScope)?.context, enabled)
        val trackColor = ColorUtils.blendARGB(off, on, motion.fraction)
        val surface = GlassConfig.surface(PrefKeys.SWITCHES)
        val behind = SurfaceBehind.color(ComposeToolkit.ownerView(drawScope))
        val density = drawScope.call("getDensity") as? Float ?: 1f
        val rtl = drawScope.call("getLayoutDirection")?.toString() == RTL
        val trackWidth = maxOf(width, KYANT_WIDTH_DP * density)
        val trackHeight = minOf(height, KYANT_HEIGHT_DP * density)
        val left = if (rtl) 0f else width - trackWidth
        val top = (height - trackHeight) / 2f
        if (GlassConfig.glassAvailable && onGlass(ComposeToolkit.ownerView(drawScope)?.rootView)) {
            ComposeLiquidGlass.drawSegment(
                drawScope,
                TRACK_KEY,
                left,
                top,
                trackWidth,
                trackHeight,
                FloatArray(CORNERS) { trackHeight / 2f },
                surface.blurRadiusPx,
                ColorBlend.over(ColorBlend.scaleAlpha(trackColor, GlassConfig.switchTint), behind),
                surface.style
            )
        } else {
            drawPlainTrack(canvas, left, top, trackWidth, trackHeight, trackColor, density, surface)
        }
        motion.thumb.drawToggle(
            canvas,
            left,
            top,
            trackWidth,
            trackHeight,
            if (rtl) 1f - motion.fraction else motion.fraction,
            if (GlassConfig.switchPress) motion.press else 0f,
            motion.velocity,
            density,
            ColorBlend.over(trackColor, behind.withOpaque()),
            behind.withOpaque(),
            if (enabled) 1f else DISABLED_ALPHA
        )
    }

    private fun drawPlainTrack(
        canvas: Canvas,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        color: Int,
        density: Float,
        surface: SurfaceStyle
    ) {
        val radius = height / 2f
        plainPaint.color = ColorBlend.scaleAlpha(color, GlassConfig.switchTint)
        canvas.drawRoundRect(left, top, left + width, top + height, radius, radius, plainPaint)
        rimRect.set(left, top, left + width, top + height)
        rimRadii.fill(radius)
        GlassRim.draw(canvas, rimRect, rimRadii, density, 1f, rim = surface.style.rim, reflection = surface.style.reflection)
    }

    private fun onGlass(root: View?): Boolean = ComposeLiquidGlass.rootHasGlass(root) { key ->
        composeMotions.containsKey(key) || (key is View && viewMotions.containsKey(key)) || ButtonGlass.ownsNode(key)
    }

    private fun Any.drawStock() {
        call("drawContent")
    }

    private fun Int.withOpaque(): Int = if (Color.alpha(this) == 0) Color.BLACK else ColorUtils.setAlphaComponent(this, OPAQUE)

    private fun Any?.color(name: String): Int {
        val packed = this?.field(name) as? Long ?: return Color.TRANSPARENT
        return ComposeToolkit.toArgb(packed) ?: Color.TRANSPARENT
    }

    private const val TRACK_KEY = 11
    private const val MIN_WIDTH_FIELD = "mSwitchMinWidth"
    private const val RIPPLE_STATE = "mState"
    private const val RIPPLE_COLOR = "mColor"
    private val CLEAR_RIPPLE = ColorStateList.valueOf(Color.TRANSPARENT)
    private const val KYANT_WIDTH_DP = 64f
    private const val CORNERS = 8
    private const val SWITCH_FRAMES_MS = 560L
    private const val REDRAW_TAG = "SwitchGlass.redraw"
    private const val KYANT_HEIGHT_DP = 28f
}
